package com.sha.orbis.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Network
import androidx.core.content.ContextCompat
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

object OrbisAudioStreamer {

    private const val TAG = "OrbisAudioStreamer"
    const val AUDIO_PORT = 16002
    private const val SAMPLE_RATE = 16000
    private const val FRAME_SIZE_BYTES = 640 // 20ms of 16kHz 16-bit mono PCM (16000 * 0.02 * 2 = 640)
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus changed: $focusChange")
    }

    private var socket: DatagramSocket? = null
    private var receiverThread: Thread? = null
    private var senderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
    private var isStreaming = false

    @Volatile
    var isCallConnected = false

    @Volatile
    var onPeerConnected: ((isGsmFallback: Boolean) -> Unit)? = null

    @Volatile
    var onPeerHandshakeReceived: ((isGsmFallback: Boolean) -> Unit)? = null

    @Volatile
    var onPeerHangup: (() -> Unit)? = null

    const val HANDSHAKE_MAGIC_E2EE = "ORBIS_VOICE_HANDSHAKE_E2EE"
    const val HANDSHAKE_MAGIC_GSM = "ORBIS_VOICE_HANDSHAKE_GSM"
    const val MAGIC_HANGUP = "ORBIS_VOICE_HANGUP"

    @Volatile
    private var isMuted = false

    @Volatile
    private var isSpeaker = false

    @Volatile
    private var lastPacketReceivedTime: Long = 0L
    private var watchdogJob: Job? = null

    private var secretKey: SecretKeySpec? = null
    private val secureRandom = SecureRandom()
    private var targetPeerAddress: InetAddress? = null

    @Volatile
    private var targetPeerPort: Int = AUDIO_PORT

    private var targetPeerLocalAddress: InetAddress? = null

    @Volatile
    private var targetPeerLocalPort: Int = AUDIO_PORT

    private var currentContext: Context? = null

    @Volatile
    private var mediaPacketsReceived: Int = 0

    @Volatile
    private var cellularNetwork: Network? = null

    @Volatile
    var resolvedPublicIp: String? = null

    @Volatile
    var resolvedPublicPort: Int = AUDIO_PORT

    private var stunResponseDeferred: CompletableDeferred<Pair<String, Int>?>? = null

    private val STUN_SERVERS = listOf(
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

    fun applyAudioRouting(context: Context?, isSpeaker: Boolean) {
        val ctx = context ?: currentContext ?: return
        if (currentContext == null && context != null) {
            currentContext = context.applicationContext
        }
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            requestCommunicationAudioFocus(audioManager)
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val communicationDevices = audioManager.availableCommunicationDevices
                val targetDevice = if (isSpeaker) {
                    communicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    val preferredTypes = listOf(
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    )
                    preferredTypes.firstNotNullOfOrNull { type ->
                        communicationDevices.firstOrNull { it.type == type }
                    }
                }

                if (targetDevice != null) {
                    val res = audioManager.setCommunicationDevice(targetDevice)
                    Log.d(TAG, "Audio routing setCommunicationDevice(type=${targetDevice.type}) -> $res")
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = isSpeaker
                    Log.w(TAG, "No communication device found; fallback speakerphone=$isSpeaker")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = isSpeaker
            }

            // Direct AudioTrack preferredDevice routing (API 23+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val targetDevice = if (isSpeaker) {
                        outputDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    } else {
                        outputDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    }
                    audioTrack?.preferredDevice = targetDevice
                    Log.d(TAG, "AudioTrack preferredDevice set to: ${targetDevice?.type}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set audioTrack preferredDevice: ${e.message}")
                }
            }

            val communicationDeviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice?.type
            } else {
                null
            }
            Log.d(
                TAG,
                "Audio routing applied: isSpeaker=$isSpeaker, isSpeakerphoneOn=${audioManager.isSpeakerphoneOn}, communicationDevice=$communicationDeviceType"
            )
        } catch (e: Exception) {
            Log.w(TAG, "applyAudioRouting error: ${e.message}")
        }
    }

    fun resetAudioRouting(context: Context? = null) {
        val ctx = context ?: currentContext ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.isMicrophoneMute = false
            audioManager.mode = AudioManager.MODE_NORMAL
            abandonCommunicationAudioFocus(audioManager)
            Log.d(TAG, "Audio routing reset to MODE_NORMAL")
        } catch (e: Exception) {
            Log.w(TAG, "resetAudioRouting error: ${e.message}")
        }
    }

    private fun requestCommunicationAudioFocus(audioManager: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .setWillPauseWhenDucked(false)
                    .build()
                    .also { audioFocusRequest = it }
                val result = audioManager.requestAudioFocus(request)
                Log.d(TAG, "Audio focus request result=$result")
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                Log.d(TAG, "Legacy audio focus request result=$result")
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestCommunicationAudioFocus error: ${e.message}")
        }
    }

    private fun abandonCommunicationAudioFocus(audioManager: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "abandonCommunicationAudioFocus error: ${e.message}")
        }
    }

    @Synchronized
    fun initAudioRecord(): Boolean {
        val ctx = currentContext ?: return false
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted yet - delaying AudioRecord creation.")
            return false
        }
        if (audioRecord != null && audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            return true
        }

        return try {
            val minRecordBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_SIZE_BYTES * 4)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minRecordBuf
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed creating VOICE_COMMUNICATION AudioRecord: ${e.message}")
            }

            if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "VOICE_COMMUNICATION AudioRecord init failed, falling back to AudioSource.MIC")
                try { audioRecord?.release() } catch (_: Exception) {}
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minRecordBuf
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed creating MIC AudioRecord: ${e.message}")
                }
            }

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                val sessionId = audioRecord?.audioSessionId ?: 0
                if (sessionId != 0) {
                    try {
                        if (AcousticEchoCanceler.isAvailable()) {
                            aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                        }
                        if (NoiseSuppressor.isAvailable()) {
                            ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                        }
                        if (AutomaticGainControl.isAvailable()) {
                            agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Hardware audio effects setup error: ${e.message}")
                    }
                }
                if (isStreaming) {
                    try {
                        audioRecord?.startRecording()
                        Log.i(TAG, "AudioRecord started recording successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to startRecording: ${e.message}", e)
                    }
                }
                true
            } else {
                Log.w(TAG, "AudioRecord could not be initialized")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing AudioRecord: ${e.message}", e)
            false
        }
    }

    fun onRecordAudioPermissionGranted(context: Context) {
        currentContext = context.applicationContext
        Log.i(TAG, "RECORD_AUDIO permission granted dynamically. Initializing AudioRecord.")
        initAudioRecord()
    }

    /** Start full-duplex E2EE audio stream (P2P UDP with dual-candidate ICE traversal). */
    @SuppressLint("MissingPermission")
    fun start(
        context: Context,
        peerPhone: String,
        callId: String,
        safetyNumber: String,
        peerIp: String? = null,
        peerPort: Int = AUDIO_PORT,
        peerLocalIp: String? = null,
        peerLocalPort: Int = AUDIO_PORT,
        isCaller: Boolean = false,
        isGsmFallback: Boolean = false,
        initialSpeaker: Boolean = false
    ) {
        currentContext = context.applicationContext
        isSpeaker = initialSpeaker
        isMuted = false

        if (isStreaming) {
            Log.d(TAG, "Audio stream already running, updating parameters.")
            resolvePeerEndpoint(peerIp, peerPort, peerLocalIp, peerLocalPort)
            if (!isCaller) {
                isCallConnected = true
            }
            return
        }

        try {
            Log.i(TAG, "Starting Orbis Audio Streamer (callId=$callId, peerIp=$peerIp, peerPort=$peerPort, peerLocalIp=$peerLocalIp, peerLocalPort=$peerLocalPort, isCaller=$isCaller, isGsmFallback=$isGsmFallback)")
            isCallConnected = !isCaller // Receiver is connected on answer; Caller connects when handshake/first packet received

            // 1. Derive 256-bit AES Key from CallID and Safety Number
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest("$callId:$safetyNumber:orbis_voice_stream_key".toByteArray(Charsets.UTF_8))
            secretKey = SecretKeySpec(keyBytes, "AES")

            // 2. Configure Android AudioManager and route audio
            applyAudioRouting(context, isSpeaker)

            // 3. Setup Low-Latency AudioRecord with Hardware AEC and MIC fallback
            initAudioRecord()

            val sessionId = audioRecord?.audioSessionId ?: 0

            // 4. Setup AudioTrack for Voice Playback with max volume
            val minTrackBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_SIZE_BYTES * 4)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                minTrackBuf,
                AudioTrack.MODE_STREAM,
                sessionId
            ).apply {
                try {
                    setVolume(AudioTrack.getMaxVolume())
                } catch (_: Exception) {}
            }
            // Ensure newly instantiated AudioTrack is immediately assigned preferredDevice (earpiece vs speaker)
            applyAudioRouting(context, isSpeaker)

            // 5. Open UDP datagram socket
            socket = openAudioSocket(context)

            resolvePeerEndpoint(peerIp, peerPort, peerLocalIp, peerLocalPort)
            isStreaming = true
            mediaPacketsReceived = 0
            lastPacketReceivedTime = System.currentTimeMillis()

            // 6. Start Audio Hardware safely
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                try {
                    audioRecord?.startRecording()
                    Log.i(TAG, "AudioRecord started recording")
                } catch (e: Exception) {
                    Log.e(TAG, "AudioRecord startRecording error: ${e.message}")
                }
            } else {
                Log.w(TAG, "AudioRecord not initialized yet; inbound audio track will proceed, mic pending permission")
            }

            try {
                audioTrack?.play()
                Log.i(TAG, "AudioTrack started playback")
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack play error: ${e.message}")
            }

            // 7. Launch Receiver Loop (Thread)
            receiverThread = thread(name = "OrbisAudioReceiver", priority = Thread.MAX_PRIORITY) {
                runReceiverLoop()
            }

            // 8. Launch Sender Loop (Coroutine)
            senderJob = scope.launch {
                runSenderLoop()
            }

            // 8b. Launch Inactivity Watchdog (Dead-Peer Detection)
            watchdogJob = scope.launch {
                runWatchdogLoop()
            }

            // 9. If Receiver: Send instant UDP handshake burst
            if (!isCaller) {
                sendHandshake(isGsmFallback)
            }

            Log.i(TAG, "Orbis Audio Streamer successfully initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OrbisAudioStreamer: ${e.message}", e)
            stop()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openAudioSocket(context: Context): DatagramSocket {
        Log.d(TAG, "Opening UDP audio datagram socket on port $AUDIO_PORT")
        return try {
            DatagramSocket(AUDIO_PORT).apply {
                receiveBufferSize = 64 * 1024
                sendBufferSize = 64 * 1024
            }
        } catch (e: Exception) {
            Log.w(TAG, "Port $AUDIO_PORT in use, opening ephemeral socket: ${e.message}")
            DatagramSocket().apply {
                receiveBufferSize = 64 * 1024
                sendBufferSize = 64 * 1024
            }
        }
    }

    private suspend fun runWatchdogLoop() {
        val callStartTime = System.currentTimeMillis()
        while (isStreaming && scope.isActive) {
            kotlinx.coroutines.delay(1000)
            if (isCallConnected && isStreaming) {
                val now = System.currentTimeMillis()
                val lastRecv = lastPacketReceivedTime

                // Initial connection grace period: 25 seconds for Nostr signaling transit and UDP hole punching
                if (now - callStartTime < 25_000L && mediaPacketsReceived == 0) {
                    continue
                }

                // Inactivity threshold: 20 seconds of silence once packets were flowing
                if (lastRecv > 0 && (now - lastRecv) > 20_000L) {
                    Log.w(TAG, "Dead-Peer Detection: Stream silence for ${(now - lastRecv)}ms. Peer closed call or disconnected.")
                    onPeerHangup?.invoke()
                    break
                }
            }
        }
    }

    /**
     * Broadcasts UDP handshake burst directly to peer for E2EE audio line establishment.
     */
    fun sendHandshake(isGsmFallback: Boolean = false) {
        scope.launch {
            val magic = HANDSHAKE_MAGIC_E2EE
            val payload = magic.toByteArray(Charsets.UTF_8)
            val encrypted = encryptFrame(payload)
            if (encrypted == null) return@launch
            for (i in 0 until 5) {
                sendEncryptedDatagram(encrypted)
                kotlinx.coroutines.delay(35)
            }
        }
    }

    /** Enables outbound audio once SMS signaling confirms the call (required for caller NAT punch). */
    fun activateMediaPath() {
        isCallConnected = true
    }

    fun hasReceivedMediaPackets(): Boolean = mediaPacketsReceived > 0

    fun getBoundLocalPort(): Int = socket?.localPort?.takeIf { it > 0 } ?: AUDIO_PORT

    /**
     * Broadcasts UDP hangup burst directly to peer so call terminates immediately in <30ms without SMS delay.
     * Uses a dedicated background thread with an ephemeral DatagramSocket transmitting 6 spaced packets.
     */
    fun sendHangupBurst() {
        val dest = targetPeerAddress
        val key = secretKey
        if (key == null) return
        val payload = MAGIC_HANGUP.toByteArray(Charsets.UTF_8)
        val encrypted = encryptFrame(payload) ?: return

        val appContext = cellularNetwork
        thread(name = "OrbisHangupSender") {
            var tempSocket: DatagramSocket? = null
            try {
                tempSocket = DatagramSocket()
                try {
                    appContext?.bindSocket(tempSocket)
                } catch (_: Exception) {
                }
                if (dest != null) {
                    for (i in 0 until 6) {
                        try {
                            val packet = DatagramPacket(encrypted, encrypted.size, dest, targetPeerPort)
                            tempSocket.send(packet)
                        } catch (_: Exception) {
                        }
                        try {
                            Thread.sleep(25)
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    tempSocket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun resolvePeerEndpoint(
        peerIp: String?,
        peerPort: Int = AUDIO_PORT,
        peerLocalIp: String? = null,
        peerLocalPort: Int = AUDIO_PORT
    ) {
        try {
            val myLocal = enumerateFirstIpv4()
            val myPublic = resolvedPublicIp

            // 1. Resolve local candidate
            if (!peerLocalIp.isNullOrBlank() && peerLocalIp != "0.0.0.0" && peerLocalIp != "127.0.0.1") {
                targetPeerLocalAddress = InetAddress.getByName(peerLocalIp)
                targetPeerLocalPort = peerLocalPort.takeIf { it in 1..65535 } ?: AUDIO_PORT
            } else {
                targetPeerLocalAddress = null
            }

            // 2. Resolve public candidate
            val publicAddr = if (!peerIp.isNullOrBlank() && peerIp != "0.0.0.0" && peerIp != "127.0.0.1") {
                InetAddress.getByName(peerIp)
            } else null
            val publicPortNum = peerPort.takeIf { it in 1..65535 } ?: AUDIO_PORT

            // 3. Same Wi-Fi / LAN detection (Loopback prevention on consumer routers)
            val samePublicIp = !myPublic.isNullOrBlank() && !peerIp.isNullOrBlank() && myPublic == peerIp
            val sameSubnet = myLocal.substringBeforeLast('.', "") == peerLocalIp?.substringBeforeLast('.', "") && myLocal.isNotBlank()

            if ((samePublicIp || sameSubnet) && targetPeerLocalAddress != null) {
                targetPeerAddress = targetPeerLocalAddress
                targetPeerPort = targetPeerLocalPort
                Log.i(TAG, "Wi-Fi LAN loopback detected (same router WAN $myPublic or subnet $sameSubnet). Prioritizing local endpoint: $targetPeerAddress:$targetPeerPort")
            } else {
                targetPeerAddress = publicAddr ?: targetPeerLocalAddress
                targetPeerPort = if (publicAddr != null) publicPortNum else targetPeerLocalPort
                Log.d(TAG, "Resolved target peer audio endpoint: public=$publicAddr:$publicPortNum, local=$targetPeerLocalAddress:$targetPeerLocalPort, selected=$targetPeerAddress:$targetPeerPort")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve target peer endpoint: ${e.message}")
        }
    }

    private fun sendEncryptedDatagram(encrypted: ByteArray) {
        val sock = socket ?: return
        val dest = targetPeerAddress ?: return
        try {
            sock.send(DatagramPacket(encrypted, encrypted.size, dest, targetPeerPort))
        } catch (_: Exception) {
        }

        // ICE Dual-candidate transmission: before bidirectional media packets confirm the path,
        // send also to the local LAN candidate so Wi-Fi peers connect without NAT hairpinning
        val localDest = targetPeerLocalAddress
        if (mediaPacketsReceived == 0 && localDest != null && (localDest != dest || targetPeerLocalPort != targetPeerPort)) {
            try {
                sock.send(DatagramPacket(encrypted, encrypted.size, localDest, targetPeerLocalPort))
            } catch (_: Exception) {}
        }
    }

    private suspend fun runSenderLoop() {
        val pcmBuffer = ByteArray(FRAME_SIZE_BYTES)
        val silenceBuffer = ByteArray(FRAME_SIZE_BYTES) { 0 }

        while (isStreaming && scope.isActive) {
            if (!isCallConnected) {
                try {
                    Thread.sleep(40)
                } catch (_: Exception) {}
                continue
            }

            var record = audioRecord
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                val currentCtx = currentContext
                if (currentCtx != null && ContextCompat.checkSelfPermission(currentCtx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    initAudioRecord()
                    record = audioRecord
                }
            }

            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    try {
                        record.startRecording()
                    } catch (_: Exception) {}
                }

                val read = try {
                    record.read(pcmBuffer, 0, FRAME_SIZE_BYTES)
                } catch (e: Exception) {
                    -1
                }

                if (read > 0) {
                    val payloadToSend = if (isMuted) silenceBuffer else pcmBuffer
                    val encryptedPacket = encryptFrame(payloadToSend)
                    if (encryptedPacket != null) {
                        sendEncryptedDatagram(encryptedPacket)
                    }
                    continue
                }
            }

            // Keep UDP NAT pinhole and remote peer connection alive with encrypted silence packets
            val encryptedSilence = encryptFrame(silenceBuffer)
            if (encryptedSilence != null) {
                sendEncryptedDatagram(encryptedSilence)
            }
            try {
                Thread.sleep(40)
            } catch (_: Exception) {}
        }
    }

    private fun runReceiverLoop() {
        val recvBuffer = ByteArray(1024)
        val packet = DatagramPacket(recvBuffer, recvBuffer.size)

        while (isStreaming) {
            try {
                val currentSocket = socket ?: break
                packet.data = recvBuffer
                packet.length = recvBuffer.size
                currentSocket.receive(packet)

                // Intercept STUN reflexive discovery packets
                if (isStunMessage(packet.data, packet.length)) {
                    val stunEndpoint = parseStunResponse(packet.data, packet.length)
                    if (stunEndpoint != null) {
                        Log.i(TAG, "STUN response received: ${stunEndpoint.first}:${stunEndpoint.second}")
                        resolvedPublicIp = stunEndpoint.first
                        resolvedPublicPort = stunEndpoint.second
                        stunResponseDeferred?.complete(stunEndpoint)
                    }
                    continue
                }

                // Dynamically learn the remote peer's IP address from incoming packets
                val isTargetBroadcast = targetPeerAddress?.hostAddress?.endsWith(".255") == true || targetPeerAddress?.hostAddress == "255.255.255.255"
                if (targetPeerAddress == null || isTargetBroadcast || mediaPacketsReceived == 0) {
                    targetPeerAddress = packet.address
                    targetPeerPort = packet.port
                    Log.d(TAG, "Learned remote peer audio endpoint: ${packet.address.hostAddress}:${packet.port}")
                } else if (packet.port > 0 && packet.port != targetPeerPort && packet.address == targetPeerAddress) {
                    targetPeerPort = packet.port
                }

                val decryptedPcm = decryptFrame(packet.data, packet.offset, packet.length) ?: continue
                lastPacketReceivedTime = System.currentTimeMillis()

                if (decryptedPcm.size < FRAME_SIZE_BYTES / 2) {
                    val text = try { String(decryptedPcm, Charsets.UTF_8) } catch (_: Exception) { "" }
                    if (text == MAGIC_HANGUP || text.startsWith(MAGIC_HANGUP)) {
                        Log.i(TAG, "Received instant remote hangup packet from peer via UDP!")
                        onPeerHangup?.invoke()
                        continue
                    }
                    if (text.startsWith("ORBIS_VOICE_HANDSHAKE")) {
                        val isGsm = (text == HANDSHAKE_MAGIC_GSM)
                        Log.i(TAG, "Received handshake packet from peer: text=$text, isGsm=$isGsm")
                        onPeerHandshakeReceived?.invoke(isGsm)
                        if (!isCallConnected) {
                            isCallConnected = true
                            onPeerConnected?.invoke(isGsm)
                        }
                        continue
                    }
                }

                if (!isCallConnected) {
                    isCallConnected = true
                    onPeerConnected?.invoke(false)
                }

                mediaPacketsReceived++
                audioTrack?.write(decryptedPcm, 0, decryptedPcm.size)
            } catch (e: Exception) {
                if (!isStreaming) break
            }
        }
    }

    private fun encryptFrame(pcmData: ByteArray): ByteArray? {
        val key = secretKey ?: return null
        return try {
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val ciphertext = cipher.doFinal(pcmData)

            // Format: [12 bytes IV] + [Ciphertext + 16 bytes GCM Tag]
            val packet = ByteArray(GCM_IV_LENGTH + ciphertext.size)
            System.arraycopy(iv, 0, packet, 0, GCM_IV_LENGTH)
            System.arraycopy(ciphertext, 0, packet, GCM_IV_LENGTH, ciphertext.size)
            packet
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptFrame(data: ByteArray, offset: Int, length: Int): ByteArray? {
        val key = secretKey ?: return null
        if (length < GCM_IV_LENGTH + 16) return null

        return try {
            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(data, offset, iv, 0, GCM_IV_LENGTH)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            cipher.doFinal(data, offset + GCM_IV_LENGTH, length - GCM_IV_LENGTH)
        } catch (_: Exception) {
            null
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun setSpeakerOn(speakerOn: Boolean, context: Context? = null) {
        if (context != null) {
            currentContext = context.applicationContext
        }
        isSpeaker = speakerOn
        applyAudioRouting(currentContext, speakerOn)
    }

    /**
     * Returns the confirmed peer IP address learned from the audio UDP connection.
     * Used by OrbisCallManager to synchronize the video streamer's target after hole-punch.
     */
    fun getLastConfirmedPeerIp(): String? = targetPeerAddress?.hostAddress

    fun stop() {
        Log.i(TAG, "Stopping Orbis Audio Streamer")
        isStreaming = false
        isMuted = false
        isSpeaker = false

        sendHangupBurst()

        watchdogJob?.cancel()
        watchdogJob = null
        lastPacketReceivedTime = 0L

        senderJob?.cancel()
        senderJob = null

        resetAudioRouting(currentContext)

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        try {
            receiverThread?.interrupt()
        } catch (_: Exception) {}
        receiverThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        try {
            aec?.release()
            ns?.release()
            agc?.release()
        } catch (_: Exception) {}
        aec = null
        ns = null
        agc = null

        secretKey = null
        targetPeerAddress = null
        targetPeerPort = AUDIO_PORT
        targetPeerLocalAddress = null
        targetPeerLocalPort = AUDIO_PORT
        currentContext = null
        mediaPacketsReceived = 0
        cellularNetwork = null
        isCallConnected = false
        onPeerConnected = null
        onPeerHandshakeReceived = null
        onPeerHangup = null

        resolvedPublicIp = null
        resolvedPublicPort = AUDIO_PORT
        try {
            stunResponseDeferred?.cancel()
        } catch (_: Exception) {}
        stunResponseDeferred = null
    }

    /** Local IPv4 for P2P audio streaming across all active network interfaces (Wi-Fi, 4G, 5G). */
    fun getLocalIpAddress(context: Context? = null): String {
        return enumerateFirstIpv4()
    }

    /**
     * Discovers public reflexive IPv4 and port via STUN (RFC 5389) using the active audio socket.
     * Falls back to local IPv4 if offline or if STUN resolution times out.
     */
    suspend fun resolvePublicEndpoint(context: Context? = null): Pair<String, Int> {
        val cached = resolvedPublicIp
        if (!cached.isNullOrBlank()) {
            return Pair(cached, resolvedPublicPort)
        }

        val currentSocket = socket
        if (currentSocket == null || currentSocket.isClosed) {
            val localIp = getLocalIpAddress(context)
            val localPort = getBoundLocalPort()
            return Pair(localIp, localPort)
        }

        val deferred = CompletableDeferred<Pair<String, Int>?>()
        stunResponseDeferred = deferred

        withContext(Dispatchers.IO) {
            try {
                val txId = ByteArray(12)
                secureRandom.nextBytes(txId)

                val request = ByteArray(20).apply {
                    this[0] = 0x00.toByte()
                    this[1] = 0x01.toByte() // Binding Request
                    this[2] = 0x00.toByte()
                    this[3] = 0x00.toByte() // Length 0
                    this[4] = 0x21.toByte()
                    this[5] = 0x12.toByte()
                    this[6] = 0xA4.toByte()
                    this[7] = 0x42.toByte() // Magic Cookie: 0x2112A442
                    System.arraycopy(txId, 0, this, 8, 12)
                }

                for ((host, port) in STUN_SERVERS) {
                    try {
                        val addresses = InetAddress.getAllByName(host)
                        for (addr in addresses) {
                            if (addr is Inet4Address) {
                                val packet = DatagramPacket(request, request.size, addr, port)
                                currentSocket.send(packet)
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "STUN send failed for $host: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error initiating STUN discovery: ${e.message}")
            }
        }

        val result = withTimeoutOrNull(4000L) {
            deferred.await()
        }

        stunResponseDeferred = null

        return if (result != null && result.first.isNotBlank()) {
            Log.i(TAG, "STUN reflexive endpoint successfully resolved: ${result.first}:${result.second}")
            resolvedPublicIp = result.first
            resolvedPublicPort = result.second
            result
        } else {
            // IMPORTANT: do NOT store local IP as resolvedPublicIp — it would be sent as
            // "public" IP to the remote peer who cannot reach a private address across networks.
            // Return local IP only as a fallback pair for same-LAN calls.
            val fallbackIp = getLocalIpAddress(context)
            val fallbackPort = getBoundLocalPort()
            Log.w(TAG, "STUN resolution timed out on all servers — call may fail on symmetric NAT. Fallback local=$fallbackIp:$fallbackPort")
            Pair(fallbackIp, fallbackPort)
        }
    }

    private fun isStunMessage(data: ByteArray, length: Int): Boolean {
        if (length < 20) return false
        return data[4] == 0x21.toByte() &&
               data[5] == 0x12.toByte() &&
               data[6] == 0xA4.toByte() &&
               data[7] == 0x42.toByte()
    }

    private fun parseStunResponse(data: ByteArray, length: Int): Pair<String, Int>? {
        if (length < 20) return null
        val msgType = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (msgType != 0x0101) return null

        val msgLength = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val maxOffset = minOf(length, 20 + msgLength)
        var offset = 20

        while (offset + 4 <= maxOffset) {
            val attrType = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            val attrLength = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
            val valOffset = offset + 4
            if (valOffset + attrLength > length) break

            if (attrType == 0x0020 && attrLength >= 8) {
                // XOR-MAPPED-ADDRESS
                val family = data[valOffset + 1].toInt() and 0xFF
                if (family == 0x01) {
                    val rawPort = ((data[valOffset + 2].toInt() and 0xFF) shl 8) or (data[valOffset + 3].toInt() and 0xFF)
                    val port = rawPort xor 0x2112
                    val ip0 = (data[valOffset + 4].toInt() and 0xFF) xor 0x21
                    val ip1 = (data[valOffset + 5].toInt() and 0xFF) xor 0x12
                    val ip2 = (data[valOffset + 6].toInt() and 0xFF) xor 0xA4
                    val ip3 = (data[valOffset + 7].toInt() and 0xFF) xor 0x42
                    return Pair("$ip0.$ip1.$ip2.$ip3", port)
                }
            } else if (attrType == 0x0001 && attrLength >= 8) {
                // MAPPED-ADDRESS
                val family = data[valOffset + 1].toInt() and 0xFF
                if (family == 0x01) {
                    val port = ((data[valOffset + 2].toInt() and 0xFF) shl 8) or (data[valOffset + 3].toInt() and 0xFF)
                    val ip0 = data[valOffset + 4].toInt() and 0xFF
                    val ip1 = data[valOffset + 5].toInt() and 0xFF
                    val ip2 = data[valOffset + 6].toInt() and 0xFF
                    val ip3 = data[valOffset + 7].toInt() and 0xFF
                    return Pair("$ip0.$ip1.$ip2.$ip3", port)
                }
            }

            val paddedLength = (attrLength + 3) and 3.inv()
            offset = valOffset + paddedLength
        }
        return null
    }

    private fun enumerateFirstIpv4(): String {
        try {
            for (intf in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank() && !host.startsWith("127.")) return host
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ""
    }
}
