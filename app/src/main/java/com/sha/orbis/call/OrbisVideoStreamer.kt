package com.sha.orbis.call

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * High-performance, End-to-End Encrypted (E2EE) P2P Video Streamer.
 * Captures camera frames using Android Camera2 API, encrypts them with AES-256,
 * and transmits them via low-latency UDP datagrams.
 */
object OrbisVideoStreamer {

    private const val TAG = "OrbisVideoStreamer"
    const val VIDEO_PORT = 42426
    private const val MAX_PACKET_SIZE = 65507
    private const val FRAME_WIDTH = 320
    private const val FRAME_HEIGHT = 240

    private val _remoteVideoFrame = MutableStateFlow<Bitmap?>(null)
    val remoteVideoFrame: StateFlow<Bitmap?> = _remoteVideoFrame.asStateFlow()

    private val _localVideoFrame = MutableStateFlow<Bitmap?>(null)
    val localVideoFrame: StateFlow<Bitmap?> = _localVideoFrame.asStateFlow()

    private val _isLocalCameraEnabled = MutableStateFlow(true)
    val isLocalCameraEnabled: StateFlow<Boolean> = _isLocalCameraEnabled.asStateFlow()

    private val _isRemoteCameraActive = MutableStateFlow(false)
    val isRemoteCameraActive: StateFlow<Boolean> = _isRemoteCameraActive.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private var socket: DatagramSocket? = null
    private var receiverThread: Thread? = null
    var isStreaming = false
        private set
    private var secretKey: SecretKeySpec? = null

    private var peerAddress: InetAddress? = null
    private var peerPort: Int = VIDEO_PORT
    // Dual-candidate ICE traversal: separate local LAN candidate
    private var peerLocalAddress: InetAddress? = null
    private var peerLocalPort: Int = VIDEO_PORT
    @Volatile private var mediaPacketsReceived: Int = 0

    // STUN-discovered public endpoint for this device's video socket
    var videoPublicIp: String? = null
        private set
    var videoPublicPort: Int = VIDEO_PORT
        private set

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var watchdogJob: Job? = null
    private var lastPacketReceivedTime = 0L
    private var currentContext: Context? = null
    // Hardware sensor orientation — read from CameraCharacteristics, overrides hardcoded rotation
    private var sensorOrientation: Int = 90

    fun start(
        context: Context,
        callId: String,
        safetyNumber: String,
        peerIp: String?,
        peerPortParam: Int = VIDEO_PORT,
        peerLocalIp: String? = null,
        peerLocalPortParam: Int = VIDEO_PORT
    ) {
        currentContext = context.applicationContext

        if (isStreaming) {
            Log.d(TAG, "Already streaming video, updating peer endpoint and ensuring camera")
            resolvePeerEndpoint(peerIp, peerPortParam, peerLocalIp, peerLocalPortParam)
            ensureCameraRunning(context)
            sendVideoHandshake()
            return
        }

        try {
            // 1. Derive AES-256 key from callId and safetyNumber
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest("$callId:$safetyNumber:orbis_video_stream_key".toByteArray(Charsets.UTF_8))
            secretKey = SecretKeySpec(keyBytes, "AES")

            // 2. Open UDP datagram socket
            videoPublicIp = null
            videoPublicPort = VIDEO_PORT
            socket = try {
                DatagramSocket(VIDEO_PORT).apply {
                    reuseAddress = true
                    receiveBufferSize = 512 * 1024
                    sendBufferSize = 512 * 1024
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not bind to port $VIDEO_PORT, letting system assign: ${e.message}")
                DatagramSocket()
            }

            // 2b. Discover public video endpoint via STUN (parallel, non-blocking)
            scope.launch { resolveVideoPublicEndpoint() }

            // 3. Resolve destination endpoint
            resolvePeerEndpoint(peerIp, peerPortParam, peerLocalIp, peerLocalPortParam)

            isStreaming = true
            _isLocalCameraEnabled.value = true
            _isRemoteCameraActive.value = false

            // 4. Start Receiver Thread
            startReceiverThread()

            // 5. Start Watchdog Job
            startWatchdog()

            // 6. Start Camera Capture
            startCameraThread()
            openCamera(context)

            Log.i(TAG, "Orbis Video Streamer successfully started on port ${socket?.localPort}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Orbis Video Streamer: ${e.message}", e)
            stop()
        }
    }

    private fun resolvePeerEndpoint(peerIp: String?, peerPortParam: Int, peerLocalIp: String?, peerLocalPortParam: Int) {
        try {
            val myLocalIp = OrbisAudioStreamer.getLocalIpAddress(currentContext)
            val myPublicIp = videoPublicIp

            // 1. Resolve local candidate
            if (!peerLocalIp.isNullOrBlank() && peerLocalIp != "0.0.0.0" && peerLocalIp != "127.0.0.1") {
                peerLocalAddress = InetAddress.getByName(peerLocalIp)
                peerLocalPort = peerLocalPortParam.takeIf { it in 1..65535 } ?: VIDEO_PORT
            } else {
                peerLocalAddress = null
            }

            // 2. Resolve public candidate
            val publicAddr = if (!peerIp.isNullOrBlank() && peerIp != "0.0.0.0" && peerIp != "127.0.0.1") {
                InetAddress.getByName(peerIp)
            } else null
            val publicPortNum = peerPortParam.takeIf { it in 1..65535 } ?: VIDEO_PORT

            // 3. Same Wi-Fi / LAN detection — only prefer local if truly on the same network
            val samePublicIp = !myPublicIp.isNullOrBlank() && !peerIp.isNullOrBlank() && myPublicIp == peerIp
            val sameSubnet = myLocalIp.substringBeforeLast('.', "").isNotBlank() &&
                myLocalIp.substringBeforeLast('.', "") == peerLocalIp?.substringBeforeLast('.', "")

            if ((samePublicIp || sameSubnet) && peerLocalAddress != null) {
                peerAddress = peerLocalAddress
                peerPort = peerLocalPort
                Log.i(TAG, "Video LAN loopback detected (samePublic=$samePublicIp, sameSubnet=$sameSubnet). Using local: $peerAddress:$peerPort")
            } else {
                peerAddress = publicAddr ?: peerLocalAddress
                peerPort = if (publicAddr != null) publicPortNum else peerLocalPort
                Log.i(TAG, "Video WAN endpoint selected: public=$publicAddr:$publicPortNum local=$peerLocalAddress:$peerLocalPort → $peerAddress:$peerPort")
            }
            sendVideoHandshake()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve video peer endpoint: ${e.message}")
        }
    }

    /**
     * Sends an encrypted datagram to the peer using dual-candidate strategy:
     * before any media packet is confirmed, send simultaneously to both WAN and LAN
     * endpoints to punch through NAT on all device brands (Vivo, Honor, Huawei, etc.).
     */
    private fun sendVideoDatagram(encrypted: ByteArray) {
        val sock = socket ?: return
        val dest = peerAddress ?: return
        try {
            sock.send(DatagramPacket(encrypted, encrypted.size, dest, peerPort))
        } catch (_: Exception) {}

        // Dual-candidate: until media path is confirmed, also send to local LAN candidate
        val localDest = peerLocalAddress
        if (mediaPacketsReceived == 0 && localDest != null && (localDest != dest || peerLocalPort != peerPort)) {
            try {
                sock.send(DatagramPacket(encrypted, encrypted.size, localDest, peerLocalPort))
            } catch (_: Exception) {}
        }
    }

    fun sendVideoHandshake() {
        if (peerAddress == null) return
        val key = secretKey ?: return
        scope.launch {
            try {
                val pingPayload = "ORBIS_VIDEO_PING".toByteArray(Charsets.UTF_8)
                val encrypted = encryptFrame(pingPayload, key) ?: return@launch
                for (i in 0 until 5) {
                    sendVideoDatagram(encrypted) // sends to both WAN + LAN candidates
                    delay(40)
                }
                Log.i(TAG, "Sent video handshake burst (dual-candidate) to $peerAddress:$peerPort + local=$peerLocalAddress:$peerLocalPort")
            } catch (e: Exception) {
                Log.w(TAG, "Error sending video handshake: ${e.message}")
            }
        }
    }

    fun updatePeerEndpoint(ip: String, port: Int) {
        try {
            peerAddress = InetAddress.getByName(ip)
            peerPort = port
            Log.i(TAG, "Updated peer video endpoint to: $peerAddress:$peerPort")
        } catch (e: Exception) {
            Log.w(TAG, "Error updating peer video endpoint: ${e.message}")
        }
    }

    private fun startReceiverThread() {
        receiverThread = Thread {
            val buffer = ByteArray(MAX_PACKET_SIZE)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isStreaming && !Thread.currentThread().isInterrupted) {
                try {
                    socket?.receive(packet)
                    if (packet.length <= 16) continue // Must contain at least IV

                    // Dynamically learn / lock the peer address from incoming packets (same as audio streamer)
                    if (peerAddress == null || mediaPacketsReceived == 0) {
                        peerAddress = packet.address
                        peerPort = packet.port
                        Log.d(TAG, "Locked video peer address from incoming: ${packet.address.hostAddress}:${packet.port}")
                        sendVideoHandshake()
                    }

                    lastPacketReceivedTime = System.currentTimeMillis()
                    _isRemoteCameraActive.value = true

                    val encryptedBytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    val decrypted = decryptFrame(encryptedBytes)
                    if (decrypted != null && decrypted.isNotEmpty()) {
                        // Video handshake ping — reply with our own handshake to keep the hole open
                        if (decrypted.size >= 14 && String(decrypted.take(16).toByteArray(), Charsets.UTF_8).startsWith("ORBIS_VIDEO_PING")) {
                            Log.d(TAG, "Received video handshake ping from ${packet.address}:${packet.port}")
                            sendVideoHandshake()
                            continue
                        }

                        // JPEG image frame
                        if (decrypted.size > 20 && decrypted[0] == 0xFF.toByte() && decrypted[1] == 0xD8.toByte()) {
                            mediaPacketsReceived++
                            val bitmap = BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
                            if (bitmap != null) {
                                _remoteVideoFrame.value = bitmap
                            }
                        }
                    }

                } catch (e: Exception) {
                    if (!isStreaming) break
                }
            }
        }.apply {
            name = "OrbisVideoReceiver"
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive && isStreaming) {
                delay(1500)
                if (System.currentTimeMillis() - lastPacketReceivedTime > 6000) {
                    _isRemoteCameraActive.value = false
                }
                // Watchdog: auto-revive camera if it should be active but was closed during transition
                if (_isLocalCameraEnabled.value && cameraDevice == null) {
                    currentContext?.let { ctx ->
                        Log.i(TAG, "Watchdog: Camera device is null while streaming, auto-opening camera...")
                        ensureCameraRunning(ctx)
                    }
                }
            }
        }
    }

    // Camera2 Pipeline
    private fun startCameraThread() {
        cameraThread = HandlerThread("OrbisCameraBackground").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
        } catch (_: Exception) {}
        cameraThread = null
        cameraHandler = null
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(context: Context) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        try {
            val desiredFacing = if (_isFrontCamera.value) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val targetId = manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == desiredFacing
            } ?: manager.cameraIdList.firstOrNull() ?: return

            val chars = manager.getCameraCharacteristics(targetId)
            // Read hardware sensor orientation for correct rotation on all OEM devices
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val supportedSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
            val chosenSize = supportedSizes.filter { it.width <= 640 && it.height <= 480 }
                .maxByOrNull { it.width * it.height }
                ?: supportedSizes.minByOrNull { it.width * it.height }
                ?: android.util.Size(FRAME_WIDTH, FRAME_HEIGHT)

            imageReader = ImageReader.newInstance(chosenSize.width, chosenSize.height, ImageFormat.JPEG, 3).apply {
                setOnImageAvailableListener({ reader ->
                    val image = try {
                        reader.acquireLatestImage()
                    } catch (_: Exception) {
                        null
                    } ?: return@setOnImageAvailableListener

                    try {
                        val planes = image.planes
                        if (planes.isNotEmpty()) {
                            val buffer = planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)

                            if (_isLocalCameraEnabled.value) {
                                // Decode local preview bitmap with rotation
                                val rawBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (rawBmp != null) {
                                    // SENSOR_ORIENTATION = degrees CW to rotate image to be upright.
                                    // Both front and back cameras use sensorOrientation directly.
                                    // (360 - sensorOrientation) is for TextureView view-space transforms
                                    // and is WRONG for raw Bitmap pixel rotation.
                                    val rotationDeg = sensorOrientation
                                    val matrix = Matrix().apply {
                                        postRotate(rotationDeg.toFloat())
                                        if (_isFrontCamera.value) postScale(-1f, 1f) // Mirror front camera (selfie convention)
                                    }
                                    val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                                    _localVideoFrame.value = rotated

                                    // Compress frame for smooth, low-latency UDP transmission
                                    val out = java.io.ByteArrayOutputStream()
                                    rotated.compress(Bitmap.CompressFormat.JPEG, 60, out)
                                    sendVideoFrame(out.toByteArray())
                                }
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            image.close()
                        } catch (_: Exception) {}
                    }
                }, cameraHandler)
            }

            manager.openCamera(targetId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    try { camera.close() } catch (_: Exception) {}
                    cameraDevice = null
                    scheduleCameraRetry()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.w(TAG, "Camera error: $error")
                    try { camera.close() } catch (_: Exception) {}
                    cameraDevice = null
                    scheduleCameraRetry()
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Error opening camera: ${e.message}")
            scheduleCameraRetry()
        }
    }

    private fun scheduleCameraRetry() {
        if (!isStreaming || !_isLocalCameraEnabled.value) return
        scope.launch {
            delay(500)
            currentContext?.let { ctx ->
                if (cameraDevice == null && isStreaming && _isLocalCameraEnabled.value) {
                    Log.i(TAG, "Retrying openCamera after delay...")
                    ensureCameraRunning(ctx)
                }
            }
        }
    }

    private fun startCaptureSession() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        try {
            val surface = reader.surface
            @Suppress("DEPRECATION")
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    try {
                        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }
                        session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error starting repeating request: ${e.message}")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.w(TAG, "Capture session configuration failed")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Error creating capture session: ${e.message}")
        }
    }

    private fun sendVideoFrame(rawJpegBytes: ByteArray) {
        if (peerAddress == null) return
        val key = secretKey ?: return
        if (rawJpegBytes.isEmpty()) return


        try {
            val encrypted = encryptFrame(rawJpegBytes, key) ?: return
            if (encrypted.size <= MAX_PACKET_SIZE) {
                sendVideoDatagram(encrypted) // dual-candidate: WAN + LAN until path confirmed
            }
        } catch (_: Exception) {}
    }

    fun ensureCameraRunning(context: Context) {
        currentContext = context.applicationContext
        if (cameraDevice == null && _isLocalCameraEnabled.value) {
            if (cameraThread == null || cameraHandler == null) startCameraThread()
            openCamera(context)
        }
    }

    fun toggleCamera(enabled: Boolean) {
        _isLocalCameraEnabled.value = enabled
        if (!enabled) {
            _localVideoFrame.value = null
        } else {
            currentContext?.let { ctx ->
                if (cameraDevice == null && isStreaming) {
                    ensureCameraRunning(ctx)
                }
            }
        }
    }

    fun switchCamera() {
        val ctx = currentContext ?: return
        _isFrontCamera.value = !_isFrontCamera.value
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            openCamera(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "Error switching camera: ${e.message}")
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping Orbis Video Streamer")
        isStreaming = false
        _isRemoteCameraActive.value = false

        watchdogJob?.cancel()
        watchdogJob = null

        try {
            captureSession?.close()
        } catch (_: Exception) {}
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        cameraDevice = null

        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null

        stopCameraThread()

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        try {
            receiverThread?.interrupt()
        } catch (_: Exception) {}
        receiverThread = null

        _remoteVideoFrame.value = null
        _localVideoFrame.value = null
        secretKey = null
        peerAddress = null
        peerLocalAddress = null
        peerLocalPort = VIDEO_PORT
        mediaPacketsReceived = 0
        videoPublicIp = null
        videoPublicPort = VIDEO_PORT
    }

    /**
     * Discovers the public IP:port of this device's video UDP socket via STUN.
     * Called after the socket is created. Stores result in videoPublicIp / videoPublicPort.
     */
    private suspend fun resolveVideoPublicEndpoint() {
        val sock = socket ?: return
        val stunServers = listOf(
            // Google STUN (primary)
            "stun.l.google.com" to 19302,
            "stun1.l.google.com" to 19302,
            "stun2.l.google.com" to 19302,
            // Cloudflare STUN (works on most carriers including African/Asian operators)
            "stun.cloudflare.com" to 3478,
            // Twilio STUN
            "global.stun.twilio.com" to 3478,
            // Open Relay (free, reliable globally)
            "stun.openrelay.metered.ca" to 80
        )
        for ((host, port) in stunServers) {
            try {
                val transactionId = ByteArray(12).also { java.util.Random().nextBytes(it) }
                val stunRequest = ByteArray(20)
                stunRequest[0] = 0x00; stunRequest[1] = 0x01   // Binding Request
                stunRequest[2] = 0x00; stunRequest[3] = 0x00   // Length = 0
                stunRequest[4] = 0x21.toByte(); stunRequest[5] = 0x12
                stunRequest[6] = 0xA4.toByte(); stunRequest[7] = 0x42  // Magic cookie
                System.arraycopy(transactionId, 0, stunRequest, 8, 12)

                val serverAddr = java.net.InetAddress.getByName(host)
                val sendPkt = java.net.DatagramPacket(stunRequest, stunRequest.size, serverAddr, port)

                val origTimeout = try { sock.soTimeout } catch (_: Exception) { 0 }
                sock.soTimeout = 2500
                sock.send(sendPkt)

                val buf = ByteArray(512)
                val recvPkt = java.net.DatagramPacket(buf, buf.size)
                sock.receive(recvPkt)
                sock.soTimeout = origTimeout

                val resp = recvPkt.data
                var offset = 20
                while (offset + 4 <= recvPkt.length) {
                    val attrType = ((resp[offset].toInt() and 0xFF) shl 8) or (resp[offset + 1].toInt() and 0xFF)
                    val attrLen  = ((resp[offset + 2].toInt() and 0xFF) shl 8) or (resp[offset + 3].toInt() and 0xFF)
                    if ((attrType == 0x0020 || attrType == 0x0001) && attrLen >= 8) {
                        val rawPort = ((resp[offset + 6].toInt() and 0xFF) shl 8) or (resp[offset + 7].toInt() and 0xFF)
                        val xPort  = if (attrType == 0x0020) rawPort xor 0x2112 else rawPort
                        val ipBytes = ByteArray(4) { i ->
                            if (attrType == 0x0020)
                                (resp[offset + 8 + i].toInt() xor stunRequest[4 + i].toInt()).toByte()
                            else
                                resp[offset + 8 + i]
                        }
                        val mappedIp = ipBytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
                        videoPublicIp   = mappedIp
                        videoPublicPort = xPort
                        Log.i(TAG, "Video STUN OK: public endpoint = $mappedIp:$xPort via $host")
                        return
                    }
                    val padded = attrLen + if (attrLen % 4 != 0) 4 - (attrLen % 4) else 0
                    offset += 4 + padded
                }
            } catch (e: Exception) {
                Log.w(TAG, "Video STUN [$host]: ${e.message}")
            }
        }
        // Fallback: use local socket port (works on same LAN or open NAT)
        videoPublicPort = sock.localPort
        Log.w(TAG, "Video STUN failed on all servers — using local port $videoPublicPort as fallback")
    }

    // AES-256 CBC with random IV prepended
    private fun encryptFrame(data: ByteArray, key: SecretKeySpec): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(data)
            val result = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, result, 0, iv.size)
            System.arraycopy(cipherText, 0, result, iv.size, cipherText.size)
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptFrame(data: ByteArray): ByteArray? {
        val key = secretKey ?: return null
        if (data.size < 16) return null
        return try {
            val iv = ByteArray(16)
            System.arraycopy(data, 0, iv, 0, 16)
            val cipherText = ByteArray(data.size - 16)
            System.arraycopy(data, 16, cipherText, 0, cipherText.size)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            cipher.doFinal(cipherText)
        } catch (e: Exception) {
            null
        }
    }
}
