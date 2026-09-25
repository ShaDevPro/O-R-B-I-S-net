package com.sha.orbis.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * OrbisWebRTCManager — Moteur d'appel WebRTC universel pour Orbis.
 *
 * Inspiré de web_rtc_handler.dart du projet référence noscall :
 *   - RTCPeerConnection avec STUN (Google, Cloudflare) + TURN relay (0xchat public)
 *   - SDP Offer/Answer échangé via Nostr (chiffré E2EE Nostr)
 *   - ICE trickle : chaque candidat envoyé séparément via Nostr
 *   - DTLS-SRTP : chiffrement bout-en-bout du média (obligatoire en WebRTC)
 *   - H.264 Baseline (profile-level-id=42e032) forcé pour compatibilité universelle
 *   - sdpSemantics: unified-plan, bundlePolicy: max-bundle (NAT-friendly)
 *   - iceCandidatePoolSize: 4 (pré-fetch rapide de candidats)
 *
 * Remplace OrbisAudioStreamer + OrbisVideoStreamer.
 */
object OrbisWebRTCManager {

    private const val TAG = "OrbisWebRTCManager"
    private const val MAX_ICE_RESTART_ATTEMPTS = 3
    private const val ICE_RESTART_RETRY_DELAY_MS = 10_000L
    private const val ICE_DISCONNECTED_GRACE_MS = 30_000L
    private const val ICE_RECEIVING_TIMEOUT_MS = 90_000
    private const val MEDIA_WATCHDOG_INITIAL_DELAY_MS = 8_000L
    private const val MEDIA_WATCHDOG_INTERVAL_MS = 6_000L
    private const val MEDIA_WATCHDOG_MAX_PROBES = 5

    // ── State ────────────────────────────────────────────────────────────────
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localStream: MediaStream? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var eglBase: EglBase? = null

    val localRenderer = object : VideoSink {
        override fun onFrame(frame: VideoFrame) { _localFrame.value = frame }
    }
    val remoteRenderer = object : VideoSink {
        override fun onFrame(frame: VideoFrame) { _remoteFrame.value = frame }
    }

    private val _localFrame = MutableStateFlow<VideoFrame?>(null)
    val localFrame: StateFlow<VideoFrame?> = _localFrame.asStateFlow()

    private val _remoteFrame = MutableStateFlow<VideoFrame?>(null)
    val remoteFrame: StateFlow<VideoFrame?> = _remoteFrame.asStateFlow()

    private val _isRemoteCameraActive = MutableStateFlow(false)
    val isRemoteCameraActive: StateFlow<Boolean> = _isRemoteCameraActive.asStateFlow()

    private val _isLocalCameraEnabled = MutableStateFlow(true)
    val isLocalCameraEnabled: StateFlow<Boolean> = _isLocalCameraEnabled.asStateFlow()

    private val _iceConnectionState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val iceConnectionState: StateFlow<PeerConnection.IceConnectionState> = _iceConnectionState.asStateFlow()

    private var appContext: Context? = null
    private var cameraEnumerator: CameraEnumerator? = null
    private var frontCameraName: String? = null
    private var backCameraName: String? = null

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    /** Pending ICE candidates received before remote description was set */
    private val pendingCandidates = mutableListOf<IceCandidate>()

    // Callbacks vers OrbisCallManager
    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var onIceRestartNeeded: ((sdp: String, type: String) -> Unit)? = null

    private var iceRestartAttempts = 0
    private var disconnectGraceJob: Job? = null
    private var iceRestartRetryJob: Job? = null
    private var iceRestartInProgress = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioRouteKeeperJob: Job? = null
    private var mediaWatchdogJob: Job? = null
    private var mediaRepairRestartTriggered = false
    private var zeroInboundAudioProbeCount = 0

    @Volatile
    private var desiredSpeakerOn: Boolean = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.i(TAG, "Audio focus change during call: $focusChange")
        val ctx = appContext
        if (ctx != null && focusChange != AudioManager.AUDIOFOCUS_LOSS) {
            scope.launch {
                reapplyCallAudioMode(ctx, desiredSpeakerOn, "focus_change_$focusChange")
            }
        }
    }

    @Volatile var isConnected: Boolean = false
        private set

    var onConnected: (() -> Unit)? = null
        set(value) {
            field = value
            if (value != null && isConnected) {
                Log.i(TAG, "onConnected listener registered while already connected -> invoking immediately")
                scope.launch { value.invoke() }
            }
        }
    var onDisconnected: (() -> Unit)? = null

    fun notifyConnected() {
        clearIceRestartRecovery(resetAttempts = true)
        if (!isConnected) {
            isConnected = true
            Log.i(TAG, "WebRTC media session CONNECTED (triggering onConnected)")
            appContext?.let { ctx ->
                val currentCall = OrbisCallManager.callState.value
                val isSpeaker = currentCall?.isSpeakerOn == true || currentCall?.isVideoCall == true
                setSpeaker(ctx, isSpeaker)
            }
            startMediaWatchdog()
            scope.launch { onConnected?.invoke() }
        }
    }

    /** EglBase.Context exposé pour SurfaceViewRenderer dans Compose */
    val eglBaseContext: EglBase.Context
        get() = eglBase?.eglBaseContext ?: EglBase.create().also { eglBase = it }.eglBaseContext

    // Ensembles de renderers attachés (multi-sink) pour local et remote
    private val localRenderers  = mutableSetOf<VideoSink>()
    private val remoteRenderers = mutableSetOf<VideoSink>()

    fun attachLocalRenderer(sink: VideoSink) {
        localVideoTrack?.addSink(sink)
        synchronized(localRenderers) {
            localRenderers.add(sink)
        }
    }
    fun attachRemoteRenderer(sink: VideoSink) {
        remoteVideoTrack?.addSink(sink)
        synchronized(remoteRenderers) {
            remoteRenderers.add(sink)
        }
    }
    fun detachRenderer(sink: VideoSink) {
        localVideoTrack?.removeSink(sink)
        remoteVideoTrack?.removeSink(sink)
        synchronized(localRenderers) {
            localRenderers.remove(sink)
        }
        synchronized(remoteRenderers) {
            remoteRenderers.remove(sink)
        }
    }


    // ── Initialisation ───────────────────────────────────────────────────────

    fun init(context: Context) {
        appContext = context.applicationContext
        if (factory != null) return
        eglBase = EglBase.create()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        // JavaAudioDeviceModule — CRITIQUE pour Android.
        // JavaAudioDeviceModule — CRITIQUE pour Android.
        // Désactivation HW AEC/NS sur Huawei / Xiaomi / Vivo / Unisoc / Android <= 10 :
        // Les HALs audio Huawei Kirin, Xiaomi MediaTek et Unisoc (ums9230 du Vivo)
        // crashent ou provoquent un silence audio au micro.
        // Le logiciel WebRTC APM AEC3 natif prend le relais avec une clarté et stabilité absolue.
        val isProblematicChipset = android.os.Build.MANUFACTURER.contains("huawei", ignoreCase = true) ||
                                  android.os.Build.MANUFACTURER.contains("honor", ignoreCase = true) ||
                                  android.os.Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ||
                                  android.os.Build.MANUFACTURER.contains("redmi", ignoreCase = true) ||
                                  android.os.Build.MANUFACTURER.contains("vivo", ignoreCase = true) ||
                                  android.os.Build.HARDWARE.contains("ums", ignoreCase = true) ||
                                  android.os.Build.HARDWARE.contains("sprd", ignoreCase = true) ||
                                  android.os.Build.BOARD.contains("ums", ignoreCase = true) ||
                                  android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.Q
        val useHwAec = !isProblematicChipset && JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()
        val useHwNs = !isProblematicChipset && JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()

        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(useHwAec)
            .setUseHardwareNoiseSuppressor(useHwNs)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(msg: String) {
                    Log.e(TAG, "AudioRecord init error: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.onAudioError(true, "Init: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO_ERR", "AudioRecord init error: $msg")
                }
                override fun onWebRtcAudioRecordStartError(error: JavaAudioDeviceModule.AudioRecordStartErrorCode, msg: String) {
                    Log.e(TAG, "AudioRecord start error [$error]: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.onAudioError(true, "Start [$error]: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO_ERR", "AudioRecord start error [$error]: $msg")
                }
                override fun onWebRtcAudioRecordError(msg: String) {
                    Log.e(TAG, "AudioRecord error: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.onAudioError(true, msg)
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO_ERR", "AudioRecord error: $msg")
                }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(msg: String) {
                    Log.e(TAG, "AudioTrack init error: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.onAudioError(false, "Init: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO_ERR", "AudioTrack init error: $msg")
                }
                override fun onWebRtcAudioTrackStartError(error: JavaAudioDeviceModule.AudioTrackStartErrorCode, msg: String) {
                    Log.e(TAG, "AudioTrack start error [$error]: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.onAudioError(false, "Start [$error]: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO_ERR", "AudioTrack start error [$error]: $msg")
                }
                override fun onWebRtcAudioTrackError(msg: String) {
                    Log.e(TAG, "AudioTrack error: $msg")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.onAudioError(false, msg)
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO_ERR", "AudioTrack error: $msg")
                }
            })
            .createAudioDeviceModule()

        val useSoftwareCodecs = com.sha.orbis.call.diagnostic.CallDiagnosticSettings.isForceSoftwareCodecs(context)
        val decoderFactory = if (useSoftwareCodecs) {
            SoftwareVideoDecoderFactory()
        } else {
            DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)
        }
        val encoderFactory = if (useSoftwareCodecs) {
            SoftwareVideoEncoderFactory()
        } else {
            DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, false)
        }

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoDecoderFactory(decoderFactory)
            .setVideoEncoderFactory(encoderFactory)
            .createPeerConnectionFactory()

        // Libérer la référence locale — factory a sa propre référence interne
        adm.release()
        Log.i(TAG, "PeerConnectionFactory initialized with JavaAudioDeviceModule (HW AEC=${JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()}, HW NS=${JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()}, swCodecs=$useSoftwareCodecs)")
    }

    /**
     * Retourne true si la PeerConnection est créée ET initialisée.
     * Utilisé par OrbisCallManager pour décider de bufferiser ou livrer
     * directement les candidats ICE entrants.
     */
    fun isPeerConnectionReady(): Boolean = peerConnection != null

    // ── Création de la PeerConnection ────────────────────────────────────────

    /**
     * Crée la RTCPeerConnection avec STUN + TURN 0xchat.
     * Miroir de WebRTCHelper.createConnection() dans noscall web_rtc_handler.dart.
     */
    fun createPeerConnection(context: Context, isVideo: Boolean) {
        init(context)
        isConnected = false
        if (peerConnection != null) {
            stop()
        }
        val isForcedTurn = com.sha.orbis.call.diagnostic.CallDiagnosticSettings.isForceTurnRelay(context)
        val rtcConfig = PeerConnection.RTCConfiguration(OrbisIceServers.build()).apply {
            // Toujours tester P2P direct ET serveurs TURN en parallèle (ou forcer TURN si configuré)
            iceTransportsType = if (isForcedTurn) {
                Log.w(TAG, "Mode diagnostic actif : Forçage du relais TURN (PeerConnection.IceTransportsType.RELAY)")
                PeerConnection.IceTransportsType.RELAY
            } else {
                PeerConnection.IceTransportsType.ALL
            }
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            iceBackupCandidatePairPingInterval = 2000
            iceConnectionReceivingTimeout = ICE_RECEIVING_TIMEOUT_MS
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE        // audio+vidéo sur 1 port (NAT-friendly)
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN     // standard moderne
            iceCandidatePoolSize = 4                                     // pool optimal 4G
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = buildPeerConnectionObserver()
        peerConnection = factory!!.createPeerConnection(rtcConfig, observer)
            ?: throw IllegalStateException("Failed to create PeerConnection")

        // Ajouter les tracks locaux
        addLocalTracks(context, isVideo)
        Log.i(TAG, "PeerConnection created (video=$isVideo)")
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "PeerConnection créée : isVideo=$isVideo, STUN/TURN initialisés")
    }

    // ── Tracks locaux ────────────────────────────────────────────────────────

    private fun addLocalTracks(context: Context, isVideo: Boolean) {
        val pc = peerConnection ?: return
        val f = factory ?: return

        // Audio track (toujours présent)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        val audioSource = f.createAudioSource(audioConstraints)
        localAudioTrack = f.createAudioTrack("audio0", audioSource)
        pc.addTrack(localAudioTrack!!, listOf("stream0"))
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "Piste audio locale ajoutée (audio0)")

        if (isVideo) {
            // Camera capturer — front camera par défaut
            eglBase?.let { egl ->
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", egl.eglBaseContext)
            }
            videoCapturer = createCameraCapturer(context)
            if (videoCapturer != null) {
                val cap = videoCapturer!!
                val videoSource = f.createVideoSource(cap.isScreencast)
                cap.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
                cap.startCapture(640, 480, 30)
                localVideoTrack = f.createVideoTrack("video0", videoSource)
                localVideoTrack!!.addSink(localRenderer)
                synchronized(localRenderers) {
                    localRenderers.forEach { sink ->
                        try {
                            localVideoTrack?.addSink(sink)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error attaching registered local sink: ${e.message}")
                        }
                    }
                }
                pc.addTrack(localVideoTrack!!, listOf("stream0"))
                Log.i(TAG, "Camera capturer started")
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.onLocalVideoStarted(true)
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "Piste vidéo locale ajoutée (video0, capture démarrée)")
            } else {
                Log.e(TAG, "Échec création camera capturer")
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.onLocalVideoStarted(false)
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ERR", "Échec création camera capturer")
            }
        }
    }

    /**
     * Crée le capturer caméra en préférant Camera2 (meilleures performances, obligatoire
     * sur Samsung/Xiaomi/Honor récents avec foregroundServiceType="camera").
     * Fallback sur Camera1 si Camera2 non supporté par la ROM.
     *
     * Fix définitif Bug 6 — GPT 5.5 audit.
     */
    private fun createCameraCapturer(context: Context): CameraVideoCapturer? {
        val forceCamera1 = com.sha.orbis.call.diagnostic.CallDiagnosticSettings.isForceCamera1(context)
        // ── 1. Essai Camera2 en priorité si non contourné par le diagnostic ──
        if (!forceCamera1) {
            try {
                val camera2 = Camera2Enumerator(context)
                val front = camera2.deviceNames.firstOrNull { camera2.isFrontFacing(it) }
                val back = camera2.deviceNames.firstOrNull { camera2.isBackFacing(it) }

            if (front != null) {
                val capturer = camera2.createCapturer(front, null)
                if (capturer != null) {
                    cameraEnumerator = camera2
                    frontCameraName = front
                    backCameraName = back
                    _isFrontCamera.value = true
                    Log.i(TAG, "Camera2 FRONT capturer créé par défaut: $front (back=$back)")
                    return capturer
                }
            } else if (back != null) {
                val capturer = camera2.createCapturer(back, null)
                if (capturer != null) {
                    cameraEnumerator = camera2
                    frontCameraName = null
                    backCameraName = back
                    _isFrontCamera.value = false
                    Log.i(TAG, "Camera2 BACK capturer créé (pas de front disponible): $back")
                    return capturer
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Camera2 capturer creation failed: ${e.message} — fallback Camera1")
        }
        }

        // ── 2. Fallback Camera1 avec captureToTexture = TRUE (OBLIGATOIRE pour SurfaceTextureHelper) ──
        try {
            val camera1 = Camera1Enumerator(true)
            val front = camera1.deviceNames.firstOrNull { camera1.isFrontFacing(it) }
            val back = camera1.deviceNames.firstOrNull { camera1.isBackFacing(it) }

            if (front != null) {
                val capturer = camera1.createCapturer(front, null)
                if (capturer != null) {
                    cameraEnumerator = camera1
                    frontCameraName = front
                    backCameraName = back
                    _isFrontCamera.value = true
                    Log.i(TAG, "Camera1 FRONT capturer créé par défaut: $front (back=$back)")
                    return capturer
                }
            } else if (back != null) {
                val capturer = camera1.createCapturer(back, null)
                if (capturer != null) {
                    cameraEnumerator = camera1
                    frontCameraName = null
                    backCameraName = back
                    _isFrontCamera.value = false
                    Log.i(TAG, "Camera1 BACK capturer créé (pas de front): $back")
                    return capturer
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Camera1 capturer creation failed: ${e.message}")
        }
        Log.e(TAG, "Aucune caméra utilisable trouvée sur l'appareil")
        return null
    }

    // ── SDP Offer / Answer ──────────────────────────────────────────────────

    /**
     * Caller : crée l'offre SDP et la retourne (à envoyer via Nostr).
     * Miroir de WebRTCHelper.createOffer() dans noscall web_rtc_handler.dart.
     */
    fun createOffer(isVideo: Boolean, callback: (sdp: String?, type: String?) -> Unit) {
        val pc = peerConnection ?: run { callback(null, null); return }
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
        }
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                // Force H.264 Baseline profile pour compatibilité universelle
                // Miroir exact de noscall web_rtc_handler.dart ligne 440 :
                //   sdp?.replaceAll('profile-level-id=640c1f', 'profile-level-id=42e032')
                val patchedSdp = forceBaselineH264(desc.description)
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.onSdpOffer(patchedSdp)
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "createOffer réussi (taille=${patchedSdp.length})")
                val patched = SessionDescription(desc.type, patchedSdp)
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.i(TAG, "createOffer: setLocalDescription OK (sdp length=${patchedSdp.length})")
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "setLocalDescription(offer) OK")
                        callback(patchedSdp, "offer")
                    }
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "createOffer: setLocalDescription failed: $error")
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ERR", "setLocalDescription(offer) échec: $error")
                        callback(null, null)
                    }
                }, patched)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failed: $error")
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ERR", "createOffer échec: $error")
                callback(null, null)
            }
        }, constraints)
    }

    /**
     * Callee : applique l'offre reçue puis crée la réponse SDP.
     * Miroir de setRemoteDescription + createAnswer dans noscall web_rtc_handler.dart.
     */
    fun setRemoteOfferAndCreateAnswer(
        remoteSdp: String,
        isVideo: Boolean,
        callback: (sdp: String?, type: String?) -> Unit
    ) {
        val pc = peerConnection ?: run { callback(null, null); return }
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.onSdpOffer(remoteSdp)
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "Application SDP Offer distante (${remoteSdp.length} octets)...")
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.i(TAG, "setRemoteOfferAndCreateAnswer: remote offer set OK")
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "setRemoteDescription(offer) OK, création Answer...")
                // Appliquer les candidats en attente (pending candidates queue — noscall web_rtc_handler.dart L110-122)
                flushPendingCandidates()
                // Créer la réponse
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
                }
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        val patchedSdp = forceBaselineH264(desc.description)
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.onSdpAnswer(patchedSdp)
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "createAnswer réussi (taille=${patchedSdp.length})")
                        val patched = SessionDescription(desc.type, patchedSdp)
                        pc.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                Log.i(TAG, "createAnswer: setLocalDescription OK")
                                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "setLocalDescription(answer) OK")
                                callback(patchedSdp, "answer")
                            }
                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "createAnswer: setLocalDescription failed: $error")
                                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ERR", "setLocalDescription(answer) échec: $error")
                                callback(null, null)
                            }
                        }, patched)
                    }
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "createAnswer failed: $error")
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ERR", "createAnswer échec: $error")
                        callback(null, null)
                    }
                }, constraints)
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteOffer failed: $error")
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ERR", "setRemoteDescription(offer) échec: $error")
                callback(null, null)
            }
        }, remoteDesc)
    }

    /**
     * Caller : applique la réponse SDP reçue du callee.
     */
    fun setRemoteAnswer(remoteSdp: String) {
        val pc = peerConnection ?: return
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.onSdpAnswer(remoteSdp)
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "Application SDP Answer distante (${remoteSdp.length} octets)...")
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, remoteSdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.i(TAG, "setRemoteAnswer: remote answer set OK")
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "setRemoteDescription(answer) OK")
                flushPendingCandidates()
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteAnswer failed: $error")
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ERR", "setRemoteDescription(answer) échec: $error")
            }
        }, remoteDesc)
    }

    /**
     * ICE Restart (RFC 5245 / 8445) — Re-négocie la session WebRTC avec de nouveaux identifiants ICE
     * pour traverser les changements de NAT 4G/CGNAT et réactiver les flux après instabilité cellulaire.
     */
    fun restartIce(isVideo: Boolean, callback: (sdp: String?, type: String?) -> Unit) {
        val pc = peerConnection ?: run { callback(null, null); return }
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
        }
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                val patchedSdp = forceBaselineH264(desc.description)
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ICE", "restartIce createOffer réussi (taille=${patchedSdp.length})")
                val patched = SessionDescription(desc.type, patchedSdp)
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.i(TAG, "restartIce: setLocalDescription OK (sdp length=${patchedSdp.length})")
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ICE", "restartIce setLocalDescription OK")
                        callback(patchedSdp, "offer")
                    }
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "restartIce: setLocalDescription failed: $error")
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ERR", "restartIce setLocalDescription échec: $error")
                        callback(null, null)
                    }
                }, patched)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "restartIce failed: $error")
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ERR", "restartIce createOffer échec: $error")
                callback(null, null)
            }
        }, constraints)
    }

    /**
     * Déclenche automatiquement un ICE Restart en cas de déconnexion ou d'échec 4G.
     */
    fun triggerIceRestart() {
        if (iceRestartInProgress) {
            Log.d(TAG, "triggerIceRestart: tentative déjà en cours, aucun doublon envoyé")
            return
        }
        val pc = peerConnection
        if (pc != null && pc.signalingState() != PeerConnection.SignalingState.STABLE) {
            Log.w(TAG, "triggerIceRestart ignoré : signalingState=${pc.signalingState()} (évite une renégociation concurrente)")
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent(
                "WEBRTC_ICE",
                "ICE Restart ignoré car signalingState=${pc.signalingState()}"
            )
            return
        }
        if (iceRestartAttempts >= MAX_ICE_RESTART_ATTEMPTS) {
            Log.w(TAG, "triggerIceRestart: limite de 3 tentatives atteinte -> fin d'appel")
            scope.launch { onDisconnected?.invoke() }
            return
        }
        iceRestartInProgress = true
        iceRestartAttempts++
        Log.w(TAG, "triggerIceRestart: tentative #$iceRestartAttempts de relance ICE...")
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ICE", "Tentative ICE Restart #$iceRestartAttempts déclenchée")
        val currentCall = OrbisCallManager.callState.value
        val isVideo = currentCall?.isVideoCall == true
        restartIce(isVideo) { sdp, type ->
            if (sdp != null && type != null) {
                Log.i(TAG, "triggerIceRestart: Offer SDP générée (${sdp.length} octets) -> transmission à OrbisCallManager")
                scope.launch { onIceRestartNeeded?.invoke(sdp, type) }
                scheduleIceRestartRetryIfNeeded()
            } else {
                Log.e(TAG, "triggerIceRestart: échec génération offre ICE Restart")
                iceRestartInProgress = false
                scheduleIceRestartRetryIfNeeded()
            }
        }
    }

    private fun scheduleIceRestartRetryIfNeeded() {
        iceRestartRetryJob?.cancel()
        iceRestartRetryJob = scope.launch {
            delay(ICE_RESTART_RETRY_DELAY_MS)
            iceRestartInProgress = false
            val state = _iceConnectionState.value
            if (shouldContinueIceRestartRecovery(state)) {
                if (iceRestartAttempts < MAX_ICE_RESTART_ATTEMPTS) {
                    Log.w(TAG, "ICE restart sans reconnexion après ${ICE_RESTART_RETRY_DELAY_MS}ms -> nouvelle tentative")
                    triggerIceRestart()
                } else {
                    Log.e(TAG, "ICE restart épuisé sans reconnexion -> fin d'appel")
                    onDisconnected?.invoke()
                }
            }
        }
    }

    private fun shouldContinueIceRestartRecovery(state: PeerConnection.IceConnectionState): Boolean =
        state != PeerConnection.IceConnectionState.CONNECTED &&
            state != PeerConnection.IceConnectionState.COMPLETED &&
            state != PeerConnection.IceConnectionState.CLOSED

    private fun clearIceRestartRecovery(resetAttempts: Boolean) {
        disconnectGraceJob?.cancel()
        disconnectGraceJob = null
        iceRestartRetryJob?.cancel()
        iceRestartRetryJob = null
        iceRestartInProgress = false
        if (resetAttempts) {
            iceRestartAttempts = 0
        }
    }

    private fun forceBaselineH264(sdp: String): String =
        sdp.replace(Regex("profile-level-id=64[0-9a-fA-F]{4}"), "profile-level-id=42e032")

    // ── ICE candidates ───────────────────────────────────────────────────────

    /**
     * Ajoute un candidat ICE reçu via Nostr.
     * Queue si remote description pas encore set (miroir noscall web_rtc_handler.dart L110-122).
     */
    fun addIceCandidate(sdp: String, sdpMid: String?, sdpMLineIndex: Int) {
        val pc = peerConnection ?: return
        val candType = extractCandidateType(sdp)
        val candTransport = extractCandidateTransport(sdp)
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.onRemoteIceCandidate(candType)
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent(
            "WEBRTC_ICE",
            "Candidat ICE distant reçu : type=$candType $candTransport (mid=$sdpMid, line=$sdpMLineIndex)"
        )
        val candidate = IceCandidate(sdpMid ?: "0", sdpMLineIndex, sdp)
        if (pc.remoteDescription == null) {
            // Remote desc pas encore set — mettre en file d'attente
            synchronized(pendingCandidates) { pendingCandidates.add(candidate) }
            Log.d(TAG, "ICE candidate queued (no remote desc yet): type=$candType $sdp")
        } else {
            pc.addIceCandidate(candidate)
            Log.d(TAG, "ICE candidate added: type=$candType $sdp")
        }
    }

    private fun flushPendingCandidates() {
        val pc = peerConnection ?: return
        synchronized(pendingCandidates) {
            pendingCandidates.forEach { candidate ->
                pc.addIceCandidate(candidate)
                Log.d(TAG, "Flushed pending ICE candidate: ${candidate.sdp}")
            }
            pendingCandidates.clear()
        }
    }

    // ── Contrôles utilisateur ────────────────────────────────────────────────

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        _isLocalCameraEnabled.value = enabled
    }

    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun switchCamera(onComplete: ((Boolean) -> Unit)? = null) {
        val capturer = videoCapturer as? CameraVideoCapturer ?: run {
            Log.w(TAG, "switchCamera: videoCapturer is not CameraVideoCapturer")
            onComplete?.invoke(_isFrontCamera.value)
            return
        }
        val targetFacingFront = !_isFrontCamera.value
        val targetName = if (targetFacingFront) frontCameraName else backCameraName

        val handler = object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                Log.i(TAG, "switchCamera DONE: isFrontCamera=$isFront (target was $targetFacingFront)")
                _isFrontCamera.value = isFront
                scope.launch { onComplete?.invoke(isFront) }
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.w(TAG, "switchCamera targeted ERROR ($targetName): $errorDescription — tentative switch générique")
                capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFront: Boolean) {
                        Log.i(TAG, "switchCamera generic DONE: isFrontCamera=$isFront")
                        _isFrontCamera.value = isFront
                        scope.launch { onComplete?.invoke(isFront) }
                    }

                    override fun onCameraSwitchError(err: String?) {
                        Log.e(TAG, "switchCamera generic ERROR: $err")
                        scope.launch { onComplete?.invoke(_isFrontCamera.value) }
                    }
                })
            }
        }

        if (targetName != null) {
            capturer.switchCamera(handler, targetName)
        } else {
            capturer.switchCamera(handler)
        }
    }

    /**
     * Contrôle haut-parleur/écouteur pendant un appel WebRTC.
     * Applique à la fois AudioManager (pour WebRTC) et Android Telecom (Core-Telecom).
     */
    fun setSpeaker(context: Context, on: Boolean) {
        appContext = context.applicationContext
        desiredSpeakerOn = on
        reapplyCallAudioMode(context.applicationContext, on, "setSpeaker")
        try {
            com.sha.orbis.call.telecom.OrbisTelecomHelper.setSpeaker(on)
        } catch (_: Throwable) {}
    }

    private fun requestCallAudioFocus(audioManager: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setWillPauseWhenDucked(false)
                    .build()
                    .also { audioFocusRequest = it }
                val result = audioManager.requestAudioFocus(request)
                Log.d(TAG, "Call audio focus request result=$result")
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                Log.d(TAG, "Legacy call audio focus request result=$result")
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestCallAudioFocus error: ${e.message}")
        }
    }

    private fun abandonCallAudioFocus(audioManager: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "abandonCallAudioFocus error: ${e.message}")
        }
    }

    private fun reapplyCallAudioMode(context: Context, speakerOn: Boolean, reason: String) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            requestCallAudioFocus(audioManager)
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false

            // Toujours définir isSpeakerphoneOn : certains HAL Samsung/Vivo/Honor ignorent
            // setCommunicationDevice après un passage veille si ce flag n'est pas cohérent.
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speakerOn

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (speakerOn) {
                    val speakerDevice = audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speakerDevice != null) {
                        val ok = audioManager.setCommunicationDevice(speakerDevice)
                        Log.i(TAG, "setCommunicationDevice(BUILTIN_SPEAKER) result: $ok")
                    }
                } else {
                    val preferredTypes = listOf(
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    )
                    val targetDevice = preferredTypes.firstNotNullOfOrNull { type ->
                        audioManager.availableCommunicationDevices.firstOrNull { it.type == type }
                    }
                    if (targetDevice != null) {
                        val ok = audioManager.setCommunicationDevice(targetDevice)
                        Log.i(TAG, "setCommunicationDevice(type=${targetDevice.type}) result: $ok")
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                }
            }

            localAudioTrack?.setEnabled(OrbisCallManager.callState.value?.isMuted != true)
            remoteAudioTrack?.setEnabled(true)

            Log.d(TAG, "Audio mode reapplied ($reason): speaker=$speakerOn, mode=${audioManager.mode}, isSpeakerphoneOn=${audioManager.isSpeakerphoneOn}")
        } catch (e: Exception) {
            Log.w(TAG, "reapplyCallAudioMode error: ${e.message}")
        }
    }

    private fun startAudioRouteKeeper(context: Context, speakerOn: Boolean) {
        val ctx = context.applicationContext
        appContext = ctx
        desiredSpeakerOn = speakerOn
        reapplyCallAudioMode(ctx, speakerOn, "start")
        audioRouteKeeperJob?.cancel()
        audioRouteKeeperJob = scope.launch {
            while (isActive) {
                delay(10_000L)
                val state = OrbisCallManager.callState.value
                if (state == null || state.status == CallStatus.ENDED) break
                reapplyCallAudioMode(ctx, desiredSpeakerOn, "keeper")
            }
        }
    }

    private fun stopAudioRouteKeeper(context: Context?) {
        audioRouteKeeperJob?.cancel()
        audioRouteKeeperJob = null
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            abandonCallAudioFocus(audioManager)
        }
    }

    /**
     * Configure AudioManager pour les appels WebRTC.
     * Appelé UNE SEULE FOIS au début de l'appel, AVANT createPeerConnection.
     * WebRTC JavaAudioDeviceModule prend le relais ensuite.
     */
    fun setupCallAudioMode(context: Context, isSpeaker: Boolean) {
        try {
            startAudioRouteKeeper(context, isSpeaker)
            Log.i(TAG, "setupCallAudioMode: MODE_IN_COMMUNICATION, speaker=$isSpeaker")
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO", "setupCallAudioMode: MODE_IN_COMMUNICATION, speaker=$isSpeaker")
        } catch (e: Exception) {
            Log.w(TAG, "setupCallAudioMode error: ${e.message}")
        }
    }

    /**
     * Réinitialise AudioManager après un appel WebRTC terminé.
     * Restaure MODE_NORMAL pour ne pas interférer avec le reste de l'app.
     */
    fun resetCallAudioMode(context: Context?) {
        try {
            val ctx = context ?: return
            stopAudioRouteKeeper(ctx)
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.isMicrophoneMute = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            Log.i(TAG, "resetCallAudioMode: MODE_NORMAL restored")
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO", "resetCallAudioMode: MODE_NORMAL restauré")
        } catch (e: Exception) {
            Log.w(TAG, "resetCallAudioMode error: ${e.message}")
        }
    }

    private fun startMediaWatchdog() {
        mediaWatchdogJob?.cancel()
        mediaRepairRestartTriggered = false
        zeroInboundAudioProbeCount = 0
        mediaWatchdogJob = scope.launch {
            delay(MEDIA_WATCHDOG_INITIAL_DELAY_MS)
            repeat(MEDIA_WATCHDOG_MAX_PROBES) { index ->
                if (!isConnected || peerConnection == null) return@launch
                probeAudioMediaStats("watchdog_${index + 1}")
                delay(MEDIA_WATCHDOG_INTERVAL_MS)
            }
        }
    }

    private fun stopMediaWatchdog() {
        mediaWatchdogJob?.cancel()
        mediaWatchdogJob = null
        mediaRepairRestartTriggered = false
        zeroInboundAudioProbeCount = 0
    }

    private data class AudioMediaStats(
        val inboundPacketsReceived: Long,
        val inboundBytesReceived: Long,
        val outboundPacketsSent: Long,
        val outboundBytesSent: Long
    )

    private fun probeAudioMediaStats(reason: String) {
        val pc = peerConnection ?: return
        try {
            pc.getStats { report: RTCStatsReport ->
                val stats = parseAudioMediaStats(report)
                val state = _iceConnectionState.value
                val hasInboundAudio = stats.inboundPacketsReceived > 0L || stats.inboundBytesReceived > 0L
                val hasOutboundAudio = stats.outboundPacketsSent > 0L || stats.outboundBytesSent > 0L
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent(
                    "WEBRTC_MEDIA",
                    "Stats audio $reason : inPackets=${stats.inboundPacketsReceived}, inBytes=${stats.inboundBytesReceived}, outPackets=${stats.outboundPacketsSent}, outBytes=${stats.outboundBytesSent}, ice=$state"
                )

                if (hasInboundAudio) {
                    zeroInboundAudioProbeCount = 0
                    return@getStats
                }

                val canRepair = remoteAudioTrack != null &&
                    hasOutboundAudio &&
                    (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED)

                if (canRepair) {
                    zeroInboundAudioProbeCount++
                    if (zeroInboundAudioProbeCount >= 2 && !mediaRepairRestartTriggered) {
                        mediaRepairRestartTriggered = true
                        Log.w(TAG, "Audio entrant nul malgré ICE connecté -> ICE restart silencieux de réparation média")
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent(
                            "WEBRTC_MEDIA",
                            "Audio entrant nul sur ${zeroInboundAudioProbeCount} sondes : ICE restart silencieux"
                        )
                        triggerMediaPathRepairRestart()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "probeAudioMediaStats error: ${e.message}")
        }
    }

    private fun parseAudioMediaStats(report: RTCStatsReport): AudioMediaStats {
        var inboundPackets = 0L
        var inboundBytes = 0L
        var outboundPackets = 0L
        var outboundBytes = 0L

        report.statsMap.values.forEach { stats ->
            val members = stats.members
            val mediaKind = (members["kind"] ?: members["mediaType"])?.toString()
            val isAudio = mediaKind.equals("audio", ignoreCase = true) ||
                stats.id.contains("audio", ignoreCase = true)
            if (!isAudio) return@forEach

            when (stats.type) {
                "inbound-rtp" -> {
                    inboundPackets += statsMemberLong(members, "packetsReceived")
                    inboundBytes += statsMemberLong(members, "bytesReceived")
                }
                "outbound-rtp" -> {
                    outboundPackets += statsMemberLong(members, "packetsSent")
                    outboundBytes += statsMemberLong(members, "bytesSent")
                }
            }
        }

        return AudioMediaStats(
            inboundPacketsReceived = inboundPackets,
            inboundBytesReceived = inboundBytes,
            outboundPacketsSent = outboundPackets,
            outboundBytesSent = outboundBytes
        )
    }

    private fun statsMemberLong(members: Map<String, Any>, key: String): Long {
        return when (val value = members[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun triggerMediaPathRepairRestart() {
        val pc = peerConnection ?: return
        if (iceRestartInProgress) {
            Log.d(TAG, "ICE restart média ignoré : restart déjà en cours")
            return
        }
        if (pc.signalingState() != PeerConnection.SignalingState.STABLE) {
            Log.w(TAG, "ICE restart média ignoré : signalingState=${pc.signalingState()}")
            return
        }
        iceRestartInProgress = true
        val currentCall = OrbisCallManager.callState.value
        val isVideo = currentCall?.isVideoCall == true
        restartIce(isVideo) { sdp, type ->
            iceRestartInProgress = false
            if (sdp != null && type != null) {
                Log.i(TAG, "ICE restart média généré (${sdp.length} octets) -> transmission")
                scope.launch { onIceRestartNeeded?.invoke(sdp, type) }
            } else {
                Log.w(TAG, "ICE restart média non généré")
            }
        }
    }

    // ── PeerConnection Observer ──────────────────────────────────────────────

    private fun buildPeerConnectionObserver() = object : PeerConnection.Observer {

        override fun onIceCandidate(candidate: IceCandidate) {
            val candType = extractCandidateType(candidate.sdp)
            val candTransport = extractCandidateTransport(candidate.sdp)
            Log.d(TAG, "onIceCandidate: type=$candType $candTransport, sdp=${candidate.sdp}")
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.onLocalIceCandidate(candType)
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent(
                "WEBRTC_ICE",
                "Candidat ICE local découvert : type=$candType $candTransport (mid=${candidate.sdpMid}, line=${candidate.sdpMLineIndex})"
            )
            // Envoyer via Nostr (trickle ICE — miroir noscall calling_controller.dart onIceCandidateHandler)
            scope.launch { onIceCandidate?.invoke(candidate) }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "ICE connection receiving change: $receiving")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.i(TAG, "ICE connection state: $state")
            _iceConnectionState.value = state
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.onIceConnectionChange(state.name)
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_ICE", "Transition état ICE : $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    // L'établissement réseau réel est confirmé par ICE CONNECTED !
                    clearIceRestartRecovery(resetAttempts = true)
                    notifyConnected()
                }
                // DISCONNECTED = état TEMPORAIRE (switch WiFi→4G, tunnel, signal faible, handover)
                // Période de grâce avant ICE restart : trop court, certains Android/CGNAT cassent l'audio
                // avec une renégociation prématurée alors que la paire ICE peut encore se rétablir.
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.w(TAG, "ICE DISCONNECTED — démarrage période de grâce reconnexion (appel maintenu)")
                    appContext?.let { ctx -> reapplyCallAudioMode(ctx, desiredSpeakerOn, "ice_disconnected") }
                    if (disconnectGraceJob == null) {
                        disconnectGraceJob = scope.launch {
                            delay(ICE_DISCONNECTED_GRACE_MS)
                            if (_iceConnectionState.value == PeerConnection.IceConnectionState.DISCONNECTED) {
                                Log.w(TAG, "ICE toujours DISCONNECTED après ${ICE_DISCONNECTED_GRACE_MS}ms -> déclenchement ICE Restart")
                                triggerIceRestart()
                            }
                        }
                    }
                }
                // FAILED = ICE a échoué -> tenter ICE Restart avant de couper l'appel
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.w(TAG, "ICE FAILED — tentative d'ICE Restart avant abandon")
                    disconnectGraceJob?.cancel()
                    disconnectGraceJob = null
                    if (iceRestartAttempts < MAX_ICE_RESTART_ATTEMPTS) {
                        triggerIceRestart()
                    } else {
                        Log.e(TAG, "ICE FAILED — tentatives d'ICE Restart épuisées -> fin d'appel")
                        scope.launch { onDisconnected?.invoke() }
                    }
                }
                // CLOSED = PeerConnection fermée volontairement -> appel terminé
                PeerConnection.IceConnectionState.CLOSED -> {
                    Log.w(TAG, "ICE CLOSED — fin d'appel")
                    clearIceRestartRecovery(resetAttempts = false)
                    scope.launch { onDisconnected?.invoke() }
                }
                else -> {}
            }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track() ?: return
            // Note : WebRTC déclenche onTrack dès l'application du SDP distant.
            // L'état CONNECTÉ réel de l'appel est géré par onIceConnectionChange(CONNECTED).
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC_MEDIA", "Piste média distante reçue : kind=${track.kind()}, id=${track.id()}")
            when (track.kind()) {
                "video" -> {
                    Log.i(TAG, "Remote video track received")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.onRemoteVideoTrackReceived()
                    val vt = track as? VideoTrack ?: return
                    remoteVideoTrack?.removeSink(remoteRenderer)
                    synchronized(remoteRenderers) {
                        remoteRenderers.forEach { existingSink ->
                            remoteVideoTrack?.removeSink(existingSink)
                        }
                    }
                    remoteVideoTrack = vt
                    vt.addSink(remoteRenderer)
                    // Attacher tous les SurfaceViewRenderers enregistrés
                    synchronized(remoteRenderers) {
                        remoteRenderers.forEach { vt.addSink(it) }
                    }
                    _isRemoteCameraActive.value = true
                }
                "audio" -> {
                    Log.i(TAG, "Remote audio track received")
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.onRemoteAudioTrackReceived()
                    remoteAudioTrack = track as? AudioTrack
                    remoteAudioTrack?.setEnabled(true)
                    appContext?.let { ctx ->
                        val currentCall = OrbisCallManager.callState.value
                        val isSpeaker = currentCall?.isSpeakerOn == true || currentCall?.isVideoCall == true
                        setSpeaker(ctx, isSpeaker)
                    }
                }
            }
        }

        override fun onAddStream(stream: MediaStream?) {
            Log.i(TAG, "MediaStream added (audioTracks=${stream?.audioTracks?.size}, videoTracks=${stream?.videoTracks?.size})")
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent(
                "WEBRTC_MEDIA",
                "MediaStream distant reçu; attente ICE CONNECTED avant statut CONNECTED"
            )
        }
        override fun onRemoveStream(stream: MediaStream?) {
            _isRemoteCameraActive.value = false
        }
        override fun onDataChannel(dc: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "ICE gathering: $state")
        }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
            Log.i(TAG, "PeerConnection state: $state")
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "Transition état PeerConnection : $state")
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    notifyConnected()
                }
                PeerConnection.PeerConnectionState.FAILED -> {
                    Log.w(TAG, "PeerConnection FAILED — ICE restart tenté avant fin d'appel")
                    triggerIceRestart()
                }
                PeerConnection.PeerConnectionState.CLOSED -> {
                    Log.w(TAG, "PeerConnection $state — fin d'appel")
                    clearIceRestartRecovery(resetAttempts = false)
                    scope.launch { onDisconnected?.invoke() }
                }
                else -> {}
            }
        }
    }

    // ── Nettoyage ────────────────────────────────────────────────────────────

    fun stop() {
        Log.i(TAG, "Stopping WebRTC session")
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("WEBRTC", "Arrêt session WebRTC (stop)")
        isConnected = false
        stopMediaWatchdog()
        try {
            try {
                videoCapturer?.stopCapture()
            } catch (e: Exception) {
                Log.w(TAG, "videoCapturer stopCapture error: ${e.message}")
            }

            synchronized(localRenderers) { localRenderers.clear() }
            synchronized(remoteRenderers) { remoteRenderers.clear() }
            try { localVideoTrack?.removeSink(localRenderer) } catch (_: Exception) {}
            try { remoteVideoTrack?.removeSink(remoteRenderer) } catch (_: Exception) {}
            remoteVideoTrack = null
            remoteAudioTrack = null

            try { localVideoTrack?.setEnabled(false) } catch (_: Exception) {}
            try { localAudioTrack?.setEnabled(false) } catch (_: Exception) {}

            // Détacher proprement les senders avant fermeture
            try {
                peerConnection?.senders?.forEach { sender ->
                    try { peerConnection?.removeTrack(sender) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // Fermeture propre de la connexion sans appeler dispose()
            // (dispose() déclenche nativeFreeOwnedPeerConnection en C++ pendant que worker_thread nettoie l'AudioTrack -> SIGILL)
            try {
                peerConnection?.close()
            } catch (e: Exception) {
                Log.w(TAG, "peerConnection close error: ${e.message}")
            }
            peerConnection = null

            localVideoTrack = null
            localAudioTrack = null
            videoCapturer = null
            surfaceTextureHelper = null
            localStream = null

            synchronized(pendingCandidates) { pendingCandidates.clear() }
            cameraEnumerator = null
            frontCameraName = null
            backCameraName = null
            _isFrontCamera.value = true
            _localFrame.value = null
            _remoteFrame.value = null
            _isRemoteCameraActive.value = false
            _isLocalCameraEnabled.value = true
            _iceConnectionState.value = PeerConnection.IceConnectionState.NEW
            onIceCandidate = null
            onConnected = null
            onDisconnected = null
            onIceRestartNeeded = null
            clearIceRestartRecovery(resetAttempts = true)
        } catch (e: Exception) {
            Log.w(TAG, "WebRTC stop cleanup error: ${e.message}")
        }
        Log.i(TAG, "WebRTC session stopped")
    }

    fun dispose() {
        stop()
        factory?.dispose()
        factory = null
        eglBase?.release()
        eglBase = null
    }

    private fun extractCandidateType(sdp: String): String {
        val lower = sdp.lowercase()
        return when {
            lower.contains("typ relay") -> "relay"
            lower.contains("typ srflx") -> "srflx"
            lower.contains("typ prflx") -> "prflx"
            lower.contains("typ host") -> "host"
            else -> "inconnu"
        }
    }

    private fun extractCandidateTransport(sdp: String): String {
        val lower = sdp.lowercase()
        return when {
            lower.contains(" tcp ") -> "[TCP]"
            lower.contains(" udp ") -> "[UDP]"
            else -> ""
        }
    }
}

/** Adapter minimal SdpObserver pour éviter de répéter les méthodes vides */
private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
