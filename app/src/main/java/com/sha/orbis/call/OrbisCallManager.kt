package com.sha.orbis.call

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import android.widget.Toast
import com.sha.orbis.R
import com.sha.orbis.data.SessionManager
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import org.json.JSONObject

/** Candidat ICE bufferisé en attente de création de la PeerConnection */
private data class IceCandidateData(val sdp: String, val sdpMid: String, val sdpMLineIndex: Int)

object OrbisCallManager {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var callTimeoutJob: Job? = null
    private var incomingCallWakeLock: PowerManager.WakeLock? = null

    private val _callState = MutableStateFlow<CallSession?>(null)
    val callState: StateFlow<CallSession?> = _callState.asStateFlow()

    private val _callFeedback = MutableStateFlow<CallFeedbackInfo?>(null)
    val callFeedback: StateFlow<CallFeedbackInfo?> = _callFeedback.asStateFlow()

    private var appContext: Context? = null
    private var currentCallLogId: String? = null

    /**
     * Buffer ICE par callId — clé = callId Nostr de l'appel en cours.
     * Les candidats reçus AVANT que la PeerConnection soit créée (pendant la sonnerie)
     * sont stockés ici et rejoués dès que createPeerConnection() est appelé.
     * Fix définitif des bugs ICE « connexion aléatoire selon vitesse réseau ».
     *
     * Réserve 2 fix : Collections.synchronizedList garantit thread-safety
     * des ajouts concurrents pendant flush.
     */
    private val iceCandidateBuffer = ConcurrentHashMap<String, MutableList<IceCandidateData>>()

    /**
     * Réserve 1 fix : candidats ICE reçus AVANT même que _callState/OFFER arrive.
     * Rare mais possible si le relay Nostr livre CANDIDATE avant OFFER.
     * Drainés dans onIncomingCallReceived() dès que le callId est connu.
     */
    private val orphanIceCandidates = java.util.Collections.synchronizedList(
        mutableListOf<Pair<String, IceCandidateData>>()  // Pair(callId, data)
    )

    /** Crée un buffer thread-safe pour un callId donné */
    private fun getOrCreateBuffer(callId: String): MutableList<IceCandidateData> =
        iceCandidateBuffer.getOrPut(callId) {
            java.util.Collections.synchronizedList(mutableListOf())
        }

    /** Vide le buffer pour le callId actuel et envoie tous les candidats à WebRTC */
    private fun flushIceBuffer(callId: String) {
        val buffered = iceCandidateBuffer.remove(callId) ?: return
        val snapshot = synchronized(buffered) { buffered.toList() }
        android.util.Log.i("OrbisCallManager", "Flushing ${snapshot.size} buffered ICE candidates for callId=$callId")
        snapshot.forEach { c ->
            OrbisWebRTCManager.addIceCandidate(c.sdp, c.sdpMid, c.sdpMLineIndex)
        }
    }

    /**
     * Drain les candidats orphelins (arrivés avant OFFER) vers le buffer normal.
     * Appelé dans onIncomingCallReceived() dès que le callId est connu.
     */
    private fun drainOrphanCandidates(callId: String) {
        val matched = synchronized(orphanIceCandidates) {
            val found = orphanIceCandidates.filter { it.first == callId }
            orphanIceCandidates.removeAll(found)
            found
        }
        if (matched.isNotEmpty()) {
            android.util.Log.i("OrbisCallManager",
                "Draining ${matched.size} orphan ICE candidates for callId=$callId")
            val buf = getOrCreateBuffer(callId)
            synchronized(buf) { matched.forEach { buf.add(it.second) } }
        }
    }

    /** Purge tous les buffers ICE des appels terminés (évite fuites mémoire) */
    private fun clearIceBuffer() {
        iceCandidateBuffer.clear()
        synchronized(orphanIceCandidates) { orphanIceCandidates.clear() }
    }

    /**
     * Cache persistant des identifiants d'appels déjà traités ou terminés.
     * Sauvegardé dans SharedPreferences pour survivre aux fermetures / crashs de l'app.
     * Empêche formellement qu'un relay Nostr ne re-déclenche un appel en boucle infinie.
     */
    private const val PREFS_HANDLED_CALLS = "orbis_handled_calls_prefs"
    private const val KEY_HANDLED_CALL_IDS = "handled_call_ids_set"

    private val handledCallIds = java.util.Collections.synchronizedSet(
        java.util.LinkedHashSet<String>()
    )

    fun markCallHandled(callId: String) {
        if (callId.isBlank()) return
        synchronized(handledCallIds) {
            handledCallIds.add(callId)
            if (handledCallIds.size > 200) {
                val it = handledCallIds.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            try {
                val ctx = appContext
                if (ctx != null) {
                    val prefs = ctx.getSharedPreferences(PREFS_HANDLED_CALLS, Context.MODE_PRIVATE)
                    prefs.edit().putStringSet(KEY_HANDLED_CALL_IDS, HashSet(handledCallIds)).apply()
                }
            } catch (_: Exception) {}
        }
    }

    fun isCallAlreadyHandled(callId: String): Boolean {
        if (callId.isBlank()) return false
        synchronized(handledCallIds) {
            if (handledCallIds.contains(callId)) return true
        }
        val ctx = appContext ?: return false
        return try {
            val prefs = ctx.getSharedPreferences(PREFS_HANDLED_CALLS, Context.MODE_PRIVATE)
            val savedSet = prefs.getStringSet(KEY_HANDLED_CALL_IDS, null)
            savedSet?.contains(callId) == true
        } catch (_: Exception) {
            false
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            val prefs = appContext?.getSharedPreferences(PREFS_HANDLED_CALLS, Context.MODE_PRIVATE)
            val savedSet = prefs?.getStringSet(KEY_HANDLED_CALL_IDS, null)
            if (!savedSet.isNullOrEmpty()) {
                synchronized(handledCallIds) {
                    handledCallIds.addAll(savedSet)
                }
            }
        } catch (_: Exception) {}
    }

    fun clearCallFeedback() {
        _callFeedback.value = null
    }

    private fun logCallRecord(record: com.sha.orbis.model.CallRecord) {
        val ctx = appContext ?: return
        try {
            com.sha.orbis.storage.CallLogRepository(ctx).addCall(record)
        } catch (e: Exception) {
            android.util.Log.e("OrbisCallManager", "Failed to log call: ${e.message}")
        }
    }

    private fun updateCallDurationInLog(durationSeconds: Int) {
        val ctx = appContext ?: return
        val logId = currentCallLogId ?: _callState.value?.callId ?: return
        try {
            com.sha.orbis.storage.CallLogRepository(ctx).updateCallDuration(logId, durationSeconds)
        } catch (e: Exception) {
            android.util.Log.e("OrbisCallManager", "Failed to update call duration: ${e.message}")
        }
    }

    // 1. OUTGOING CALL: Initiated by Caller
    fun startOutgoingCall(
        context: Context,
        peerPhone: String,
        peerName: String,
        peerAvatar: String?,
        myPhone: String,
        peerNostrKey: String? = null,
        isVideoCall: Boolean = false
    ) {
        appContext = context.applicationContext
        val callId = "call_${System.currentTimeMillis()}"
        val sas = computeSasCode(peerPhone, myPhone)

        val session = CallSession(
            callId = callId,
            peerPhone = peerPhone,
            peerName = peerName,
            peerAvatar = peerAvatar,
            status = CallStatus.OUTGOING_CALLING,
            startTime = 0L,
            durationSeconds = 0,
            isMuted = false,
            isSpeakerOn = isVideoCall,
            safetyNumber = sas,
            peerNostrKey = peerNostrKey,
            isVideoCall = isVideoCall,
            isCameraEnabled = true,
            isFrontCamera = true
        )
        _callState.value = session
        currentCallLogId = callId
        logCallRecord(
            com.sha.orbis.model.CallRecord(
                id = callId,
                peerPhone = peerPhone,
                peerName = peerName,
                peerAvatar = peerAvatar,
                timestamp = System.currentTimeMillis(),
                durationSeconds = 0,
                direction = com.sha.orbis.model.CallDirection.OUTGOING,
                isVideo = isVideoCall
            )
        )

        // Réinitialisation du rapport de diagnostic pour cette nouvelle session d'appel
        com.sha.orbis.call.diagnostic.CallDiagnosticLogger.resetReportFile(context)
        com.sha.orbis.call.diagnostic.FeedDebugTracker.resetForNewCall("SORTANT", peerPhone)

        com.sha.orbis.call.diagnostic.LastCallDebugTracker.onCallStarted(
            id = callId,
            direction = "SORTANT",
            peerPhone = peerPhone,
            peerName = peerName,
            isVideo = isVideoCall
        )
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("CALL_OUT", "Démarrage appel SORTANT : callId=$callId vers $peerName ($peerPhone), isVideo=$isVideoCall")

        // Audio routing WebRTC — un seul point de contrôle pour éviter les conflits
        // sur Vivo/Honor/Xiaomi (couches audio custom FunTouch/MagicUI)
        OrbisWebRTCManager.setupCallAudioMode(context, isSpeaker = isVideoCall)

        try {
            com.sha.orbis.telemetry.TelemetryManager.getInstance(context).recordEvent(
                if (isVideoCall) com.sha.orbis.telemetry.FeatureType.VIDEO_CALL
                else com.sha.orbis.telemetry.FeatureType.VOICE_CALL
            )
        } catch (_: Exception) {}
        OrbisProximityManager.start(context, speakerInitiallyOn = isVideoCall)

        // Play real outgoing ringback tone
        OrbisCallSoundManager.playOutgoingRingback(isSpeaker = isVideoCall)

        val sessionManager = SessionManager(context)
        val destinationKey = peerNostrKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
            ?: FriendRequestRepository(context).resolveNostrPubkeyForPhone(peerPhone)
            ?: peerNostrKey?.ifBlank { null }
            ?: peerPhone

        // ── WebRTC setup (remplace OrbisAudioStreamer + OrbisVideoStreamer UDP) ──
        // Inspiré du flow noscall calling_controller.dart : createOffer → send via Nostr
        OrbisWebRTCManager.createPeerConnection(context, isVideoCall)

        // ── ForegroundService microphone|camera — protège l'appel en arrière-plan ──
        // Sans ça, Android 13+ peut couper le micro/caméra quand l'écran s'éteint
        OrbisCallForegroundService.start(
            context = context,
            peerName = peerName,
            isVideo = isVideoCall,
            isActiveMedia = true
        )

        // ── Android Telecom (Self-Managed VoIP 100% Internet) ──
        com.sha.orbis.call.telecom.OrbisTelecomHelper.startOutgoingCall(
            context = context,
            callId = callId,
            peerPhone = peerPhone,
            peerName = peerName,
            isVideo = isVideoCall
        )

        // ICE trickle : chaque candidat envoyé via Nostr CANDIDATE (miroir noscall)
        OrbisWebRTCManager.onIceCandidate = { candidate ->
            scope.launch(Dispatchers.IO) {
                try {
                    val payload = JSONObject().apply {
                        put("candidate", candidate.sdp)
                        put("sdpMid", candidate.sdpMid ?: "0")
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                    }
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("SIGNALING", "Candidat ICE envoyé via Nostr (sdpMid=${candidate.sdpMid})")
                    com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendCallSignal(
                        recipientNpubOrHex = destinationKey,
                        callId = callId,
                        signalType = "CANDIDATE",
                        payloadJson = payload
                    )
                } catch (e: Exception) {
                    android.util.Log.w("OrbisCallManager", "Could not send ICE candidate: ${e.message}")
                }
            }
        }
        OrbisWebRTCManager.onConnected = {
            scope.launch { onCallConnectedViaUdp(false) }
        }
        OrbisWebRTCManager.onDisconnected = {
            scope.launch { onCallEndedRemote() }
        }
        OrbisWebRTCManager.onIceRestartNeeded = { sdp, type ->
            scope.launch(Dispatchers.IO) {
                try {
                    val payload = JSONObject().apply {
                        put("sdp", sdp)
                        put("type", type)
                        put("iceRestart", true)
                    }
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("ICE_RESTART", "Envoi ICE restart OFFER vers $destinationKey")
                    com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendCallSignal(
                        recipientNpubOrHex = destinationKey,
                        callId = callId,
                        signalType = "OFFER",
                        payloadJson = payload
                    )
                } catch (e: Exception) {
                    android.util.Log.w("OrbisCallManager", "ICE restart offer failed: ${e.message}")
                }
            }
        }

        // createOffer → envoyer SDP OFFER via Nostr (E2EE Nostr + DTLS-SRTP média)
        OrbisWebRTCManager.createOffer(isVideoCall) { sdp, type ->
            if (sdp == null) {
                android.util.Log.e("OrbisCallManager", "createOffer failed — aborting")
                return@createOffer
            }
            scope.launch(Dispatchers.IO) {
                val offerPayload = JSONObject().apply {
                    put("callerPhone", myPhone)
                    put("callerName", sessionManager.userName.ifBlank { myPhone })
                    put("safetyNumber", sas)
                    put("isVideo", isVideoCall)
                    put("sdp", sdp)
                    put("type", type ?: "offer")
                    // Rétro-compatibilité pairs v1.1.0 (UDP sans WebRTC SDP)
                    put("ip", "0.0.0.0")
                    put("port", 50005)
                    put("localIp", "0.0.0.0")
                    put("localPort", 50005)
                }
                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("SIGNALING", "Signal OFFER envoyé via Nostr (${sdp.length} octets) vers $destinationKey")
                val sent = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendCallSignal(
                    recipientNpubOrHex = destinationKey,
                    callId = callId,
                    signalType = "OFFER",
                    payloadJson = offerPayload
                )
                if (!sent) {
                    com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("SIGNALING", "Échec émission OFFER vers $destinationKey (clé non résolue)")
                    OrbisCallSoundManager.stopAll()
                    endCall(sendSignal = false)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.chat_nostr_waiting_friend_key), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // Réveil FCM haute priorité (Standard WhatsApp/Signal pour Doze mode)
                try {
                    val peerToken = FriendRequestRepository(context).getPeerFcmToken(destinationKey)
                        ?: FriendRequestRepository(context).getPeerFcmToken(peerPhone)
                    if (!peerToken.isNullOrBlank()) {
                        com.sha.orbis.call.fcm.OrbisFirebasePushHelper.sendWakeupPush(
                            peerFcmToken = peerToken,
                            callId = callId,
                            callerPhone = myPhone,
                            callerName = sessionManager.userName.ifBlank { myPhone },
                            isVideo = isVideoCall
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.w("OrbisCallManager", "Erreur réveil FCM appel: ${e.message}")
                }
            }
        }

        // Audio routing déjà configuré via OrbisWebRTCManager.setupCallAudioMode (ligne 174)
        // Pas de double appel — les couches audio Vivo/Honor ne supportent pas
        OrbisProximityManager.start(context, speakerInitiallyOn = isVideoCall)

        // Set status to OUTGOING_RINGING after 1.5s
        scope.launch {
            delay(1500)
            if (_callState.value?.status == CallStatus.OUTGOING_CALLING) {
                _callState.value = _callState.value?.copy(status = CallStatus.OUTGOING_RINGING)
            }
        }

        // Set 30s Ringing Timeout (Standard Telecom Ring Limit)
        callTimeoutJob?.cancel()
        callTimeoutJob = scope.launch {
            delay(30_000)
            val current = _callState.value
            if (current?.status == CallStatus.OUTGOING_RINGING || current?.status == CallStatus.OUTGOING_CALLING) {
                _callFeedback.value = CallFeedbackInfo(
                    peerPhone = current.peerPhone,
                    peerName = current.peerName,
                    reason = CallFeedbackReason.NO_ANSWER
                )
                endCall(sendSignal = false)
            }
        }
    }

    /**
     * Internet VoIP media connection: called only after WebRTC confirms ICE CONNECTED.
     */
    fun onCallConnectedViaUdp(isGsmFallback: Boolean = false) {
        val current = _callState.value ?: return
        if (current.status == CallStatus.CONNECTED) {
            if (current.isGsmFallback != isGsmFallback) {
                _callState.value = current.copy(isGsmFallback = isGsmFallback)
            }
            return
        }

        android.util.Log.i("OrbisCallManager", "WebRTC Internet media connected. Switching to CONNECTED in <100ms.")
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("CALL_STATE", "Flux média WebRTC Internet connecté ! Statut basculé vers CONNECTED")
        callTimeoutJob?.cancel()
        OrbisCallSoundManager.stopAll()
        releaseIncomingCallWakeLock()
        val ctx = appContext
        if (ctx != null) {
            OrbisCallNotificationHelper.cancelCallNotification(ctx)
            OrbisProximityManager.start(ctx, speakerInitiallyOn = current.isSpeakerOn || current.isVideoCall)
        }

        val startTime = System.currentTimeMillis()
        _callState.value = current.copy(
            status = CallStatus.CONNECTED,
            isGsmFallback = isGsmFallback,
            startTime = startTime,
            durationSeconds = 0
        )
        try {
            val tmCtx = ctx ?: appContext
            if (tmCtx != null) {
                val tm = com.sha.orbis.telemetry.TelemetryManager.getInstance(tmCtx)
                if (current.isVideoCall) {
                    tm.recordEvent(com.sha.orbis.telemetry.FeatureType.VIDEO_CALL_SUCCESS)
                } else {
                    tm.recordEvent(com.sha.orbis.telemetry.FeatureType.VOICE_CALL_SUCCESS)
                }
            }
        } catch (_: Exception) {}
        // ── Notifier Android Telecom que l'appel est connecté ──
        com.sha.orbis.call.telecom.OrbisTelecomHelper.onCallConnected(current.callId)

        startTimer()
    }

    /**
     * Dynamically updates encryption mode when incoming handshake bursts arrive during the call.
     */
    fun onPeerHandshakeModeChanged(isGsmFallback: Boolean) {
        val current = _callState.value ?: return
        if (current.isGsmFallback != isGsmFallback) {
            android.util.Log.i("OrbisCallManager", "Peer handshake encryption mode updated dynamically: isGsmFallback=$isGsmFallback")
            _callState.value = current.copy(isGsmFallback = isGsmFallback)
        }
    }

    // 2. INCOMING CALL: Received on Callee Device via Nostr Signal
    fun onIncomingCallReceived(
        context: Context,
        callId: String,
        callerPhone: String,
        callerName: String,
        safetyNumber: String,
        senderAddress: String = "",
        peerIp: String = "",
        peerPort: Int = 0,
        peerLocalIp: String = "",
        peerLocalPort: Int = 0,
        peerNostrKey: String? = null,
        isVideo: Boolean = false,
        peerVideoIp: String = "",
        peerVideoPort: Int = 0,
        peerVideoLocalPort: Int = 0,
        peerSdpOffer: String? = null   // WebRTC SDP offer from caller
    ) {
        appContext = context.applicationContext
        if (isCallAlreadyHandled(callId)) {
            android.util.Log.w("OrbisCallManager", "Appel entrant ignoré : callId=$callId est déjà terminé (évite boucle infinie)")
            return
        }
        if (_callState.value?.callId == callId) {
            android.util.Log.d("OrbisCallManager", "Appel entrant ignoré : callId=$callId est déjà en cours")
            return
        }

        val effectiveCallerPhone = if (senderAddress.filter { it.isDigit() }.length >= 8) senderAddress else callerPhone

        // Garde-fou sécurité : rejeter immédiatement si l'appelant est bloqué
        val blockedRepo = com.sha.orbis.storage.BlockedContactsRepository(context)
        if (blockedRepo.isBlocked(effectiveCallerPhone) || blockedRepo.isBlocked(callerPhone) || (!peerNostrKey.isNullOrBlank() && blockedRepo.isBlocked(peerNostrKey))) {
            android.util.Log.w("OrbisCallManager", "Appel entrant rejeté : l'appelant est bloqué ($callerPhone)")
            return
        }

        val repo = ConversationRepository(context)
        val contacts = repo.loadContacts()
        val contact = contacts.find {
            (peerNostrKey != null && (it.publicKey.equals(peerNostrKey, ignoreCase = true) || it.publicKey.equals(senderAddress, ignoreCase = true))) ||
            (callerPhone.isNotBlank() && FriendRequestRepository.isSamePhone(it.phone, callerPhone)) ||
            (senderAddress.isNotBlank() && FriendRequestRepository.isSamePhone(it.phone, senderAddress)) ||
            it.name.equals(callerName, ignoreCase = true)
        }
        val displayName = contact?.name?.takeIf { !it.startsWith("+") }
            ?: callerName.takeIf { !it.startsWith("+") && it != "Appel OrbisNet" }
            ?: contact?.name
            ?: callerPhone

        val resolvedPeerNostrKey = peerNostrKey?.takeIf { it.startsWith("npub") || it.length == 64 }
            ?: senderAddress.takeIf { it.startsWith("npub") || it.length == 64 }
            ?: contact?.publicKey?.takeIf { it.startsWith("npub") || it.length == 64 }

        val sessionManager = SessionManager(context)

        // Evaluate incoming call security with VoIP Guard Engine
        val callGuardAnalysis = com.sha.orbis.ai.guard.OrbisCallGuardEngine.analyzeIncomingCall(
            context = context,
            callerPhone = effectiveCallerPhone,
            callerName = displayName,
            peerNostrKey = resolvedPeerNostrKey,
            isSavedContact = (contact != null)
        )

        // Module 4: Mode Conduite & Ne Pas Déranger Auto
        val aiPrefs = com.sha.orbis.ai.core.OrbisAiPreferences(context)
        val isDriving = aiPrefs.isDrivingAutoDeclineEnabled && isBluetoothAudioConnected(context)
        val isDnd = aiPrefs.isDndAutoDeclineEnabled && isDoNotDisturbActive(context)

        if (isDriving || isDnd) {
            val targetIntent = if (isDriving) {
                com.sha.orbis.ai.suggestions.CallDeclineIntent.DRIVING
            } else {
                com.sha.orbis.ai.suggestions.CallDeclineIntent.BUSY_GENERAL
            }
            val suggestions = com.sha.orbis.ai.suggestions.OrbisSuggestionLibrary.getDiverseDeclineSuggestions(
                context = context,
                peerId = effectiveCallerPhone
            )
            val replyText = suggestions.firstOrNull { it.declineIntent == targetIntent }?.text
                ?: suggestions.firstOrNull()?.text
                ?: if (isDriving) "Je suis au volant, écris-moi." else "Je ne peux pas parler pour le moment."

            android.util.Log.i("OrbisCallManager", "Auto-declining incoming call (driving=$isDriving, dnd=$isDnd) with reply: $replyText")
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUTO_DECLINE", "Appel refusé auto (driving=$isDriving, dnd=$isDnd)")

            // Enregistrer l'appel en tant que manqué
            logCallRecord(
                com.sha.orbis.model.CallRecord(
                    id = callId,
                    peerPhone = if (contact?.phone?.isNotBlank() == true) contact.phone else effectiveCallerPhone,
                    peerName = displayName,
                    peerAvatar = contact?.avatarPath,
                    timestamp = System.currentTimeMillis(),
                    durationSeconds = 0,
                    direction = com.sha.orbis.model.CallDirection.MISSED,
                    isVideo = isVideo,
                    threatLevel = callGuardAnalysis.threatLevel.name,
                    threatType = callGuardAnalysis.threatType.name,
                    trustScore = callGuardAnalysis.trustScore
                )
            )

            // Rejeter l'appel via signal Nostr et transmettre le message de réponse
            scope.launch(Dispatchers.IO) {
                try {
                    val destKey = resolvedPeerNostrKey?.takeIf { it.isNotBlank() } ?: effectiveCallerPhone
                    com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendCallSignal(
                        recipientNpubOrHex = destKey,
                        callId = callId,
                        signalType = "DECLINE",
                        payloadJson = JSONObject().apply {
                            put("reason", if (isDriving) "DRIVING" else "DND")
                        }
                    )
                    val digits = destKey.filter { it.isDigit() }
                    val convId = if (digits.isNotBlank()) "conv_$digits" else "conv_${destKey.take(16)}"
                    val msgId = "msg_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
                    com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendDirectMessage(
                        recipientNpubOrHex = destKey,
                        conversationId = convId,
                        text = replyText,
                        messageId = msgId
                    )
                } catch (e: Throwable) {
                    android.util.Log.e("OrbisCallManager", "Failed to dispatch auto-reply signal: ${e.message}")
                }
            }

            scope.launch(Dispatchers.Main) {
                val toastRes = if (isDriving) R.string.call_guard_auto_declined_driving else R.string.call_guard_auto_declined_dnd
                Toast.makeText(context, context.getString(toastRes), Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            com.sha.orbis.telemetry.TelemetryManager.getInstance(context).recordEvent(
                if (isVideo) com.sha.orbis.telemetry.FeatureType.VIDEO_CALL
                else com.sha.orbis.telemetry.FeatureType.VOICE_CALL
            )
        } catch (_: Exception) {}

        val session = CallSession(
            callId = callId,
            peerPhone = if (contact?.phone?.isNotBlank() == true) contact.phone else effectiveCallerPhone,
            peerName = displayName,
            peerAvatar = contact?.avatarPath,
            status = CallStatus.INCOMING_RINGING,
            startTime = 0L,
            durationSeconds = 0,
            isMuted = false,
            isSpeakerOn = isVideo,
            safetyNumber = safetyNumber,
            peerIp = peerIp.ifBlank { null },
            peerPort = peerPort,
            peerLocalIp = peerLocalIp.ifBlank { null },
            peerLocalPort = peerLocalPort,
            isGsmFallback = false,
            hasSmsCredit = true,
            peerNostrKey = resolvedPeerNostrKey,
            isVideoCall = isVideo,
            isCameraEnabled = true,
            isFrontCamera = true,
            // STUN-discovered video endpoints from OFFER signal (legacy fallback)
            peerVideoIp = peerVideoIp.ifBlank { peerIp.ifBlank { null } },
            peerVideoPort = peerVideoPort,
            peerVideoLocalPort = peerVideoLocalPort,
            // WebRTC SDP offer — stored for acceptCall()
            peerSdpOffer = peerSdpOffer?.ifBlank { null },
            guardAnalysis = callGuardAnalysis
        )
        _callState.value = session

        // Réinitialisation du rapport de diagnostic pour cette nouvelle session d'appel
        com.sha.orbis.call.diagnostic.CallDiagnosticLogger.resetReportFile(context)
        com.sha.orbis.call.diagnostic.FeedDebugTracker.resetForNewCall("ENTRANT", callerPhone)

        com.sha.orbis.call.diagnostic.LastCallDebugTracker.onCallStarted(
            id = callId,
            direction = "ENTRANT",
            peerPhone = callerPhone,
            peerName = callerName,
            isVideo = isVideo
        )
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("CALL_IN", "Appel ENTRANT reçu : callId=$callId de $callerName ($callerPhone), isVideo=$isVideo, sdpLen=${peerSdpOffer?.length ?: 0}")
        if (!peerSdpOffer.isNullOrBlank()) {
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.onSdpOffer(peerSdpOffer)
        }

        // Réserve 1 fix : si des candidats ICE sont arrivés AVANT cet OFFER via Nostr,
        // ils ont été stockés dans orphanIceCandidates. On les draine vers le buffer normal.
        drainOrphanCandidates(callId)

        val isAppInForeground = com.sha.orbis.notification.ActiveConversationTracker.isAppInForeground
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val isScreenLocked = km?.isKeyguardLocked ?: false
        val isInteractive = pm?.isInteractive ?: true

        // 1. Wake screen physically
        try {
            incomingCallWakeLock?.release()
            @Suppress("DEPRECATION")
            incomingCallWakeLock = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "orbis:incoming_call_wake"
            )?.apply {
                acquire(30_000L)
            }
        } catch (e: Exception) {
            android.util.Log.e("OrbisCallManager", "incomingCallWakeLock error: ${e.message}")
        }

        // Module 6: Filtrage Silencieux des Spams et Bursts
        val isMutedThreat = aiPrefs.isSilentBurstShieldEnabled && (
            callGuardAnalysis.threatLevel == com.sha.orbis.ai.guard.ThreatLevel.CRITICAL ||
            callGuardAnalysis.threatType == com.sha.orbis.ai.guard.CallThreatType.RAPID_BURST_FLOODING
        )

        // 2. Play incoming ringtone & vibrate (unmuted only if safe or non-critical)
        if (!isMutedThreat) {
            OrbisCallSoundManager.playIncomingRingtone(context)
        } else {
            android.util.Log.w("OrbisCallManager", "Guard suppressed ringtone for suspicious call burst/threat from $effectiveCallerPhone")
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("GUARD_SILENT", "Sonnerie coupée silencieusement par Guard (threat=${callGuardAnalysis.threatType})")
        }

        // ── Signalement Android Telecom (Core-Telecom Self-Managed VoIP) ──
        com.sha.orbis.call.telecom.OrbisTelecomHelper.reportIncomingCall(
            context = context,
            callId = callId,
            callerPhone = effectiveCallerPhone,
            callerName = displayName,
            isVideo = isVideo
        )

        // 3. Notification vs In-App UI handling:
        if (!isAppInForeground) {
            val shouldFullScreen = !isInteractive || isScreenLocked
            // Display incoming call notification (Heads-up alert banner if unlocked, or full-screen intent if locked)
            OrbisCallNotificationHelper.showIncomingCallNotification(
                context = context,
                callId = callId,
                callerPhone = effectiveCallerPhone,
                callerName = displayName,
                isVideo = isVideo,
                isFullScreen = shouldFullScreen
            )

            // If phone is locked or screen off, directly launch OrbisIncomingCallActivity over lockscreen
            if (shouldFullScreen) {
                try {
                    val incomingCallIntent = Intent(context, com.sha.orbis.MainActivity::class.java).apply {
                        action = "com.sha.orbis.INCOMING_CALL"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("extra_incoming_call", true)
                        putExtra("extra_call_id", callId)
                        putExtra("extra_caller_phone", effectiveCallerPhone)
                    }
                    context.startActivity(incomingCallIntent)
                } catch (e: Exception) {
                    android.util.Log.e("OrbisCallManager", "Failed to launch OrbisIncomingCallActivity: ${e.message}")
                }
            }
        } else {
            // App is ALREADY open and in foreground!
            // Do NOT show notification banner (popup). MainActivity renders the full call screen directly!
            android.util.Log.d("OrbisCallManager", "App in foreground: displaying call screen directly without notification popup.")
            OrbisCallNotificationHelper.cancelCallNotification(context)
        }

        // Auto timeout after 40s
        callTimeoutJob?.cancel()
        callTimeoutJob = scope.launch {
            delay(40_000)
            val ringingSession = _callState.value
            if (ringingSession?.status == CallStatus.INCOMING_RINGING) {
                appContext?.let { ctx ->
                    OrbisMissedCallManager.handleMissedCall(
                        context = ctx,
                        callId = ringingSession.callId,
                        peerPhone = ringingSession.peerPhone,
                        peerName = ringingSession.peerName,
                        peerAvatar = ringingSession.peerAvatar,
                        isVideo = ringingSession.isVideoCall,
                        timestamp = System.currentTimeMillis()
                    )
                }
                rejectCall()
            }
        }
    }

    // 3. ACCEPT INCOMING CALL (Pure Nostr E2EE Internet & Data)
    fun acceptCall(withVideo: Boolean = true) {
        val current = _callState.value ?: return
        callTimeoutJob?.cancel()
        OrbisCallSoundManager.stopAll()
        releaseIncomingCallWakeLock()

        val isVideoSession = current.isVideoCall && withVideo
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("CALL_ACCEPT", "Appel ACCEPTÉ localement : callId=${current.callId}, isVideoSession=$isVideoSession")
        val context = appContext
        if (context != null) {
            OrbisCallNotificationHelper.cancelCallNotification(context)

            // Audio routing WebRTC — AVANT createPeerConnection pour que
            // JavaAudioDeviceModule hérite du bon mode audio
            OrbisWebRTCManager.setupCallAudioMode(context, isSpeaker = isVideoSession)
            OrbisProximityManager.start(context, speakerInitiallyOn = isVideoSession)

            // ── WebRTC — callee : setRemoteOffer → createAnswer → send ANSWER ──
            // Miroir noscall calling_controller.dart acceptCall flow
            OrbisWebRTCManager.createPeerConnection(context, isVideoSession)

            val recipientKey = current.peerNostrKey ?: current.peerPhone

            // ICE trickle vers caller
            OrbisWebRTCManager.onIceCandidate = { candidate ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val payload = JSONObject().apply {
                            put("candidate", candidate.sdp)
                            put("sdpMid", candidate.sdpMid ?: "0")
                            put("sdpMLineIndex", candidate.sdpMLineIndex)
                        }
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("SIGNALING", "Candidat ICE envoyé (callee) via Nostr (sdpMid=${candidate.sdpMid})")
                        com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendCallSignal(
                            recipientNpubOrHex = recipientKey,
                            callId = current.callId,
                            signalType = "CANDIDATE",
                            payloadJson = payload
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("OrbisCallManager", "Could not send ICE candidate (callee): ${e.message}")
                    }
                }
            }
            OrbisWebRTCManager.onConnected = {
                scope.launch { onCallConnectedViaUdp(false) }
            }
            OrbisWebRTCManager.onDisconnected = {
                scope.launch { onCallEndedRemote() }
            }
            OrbisWebRTCManager.onIceRestartNeeded = { sdp, type ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val payload = JSONObject().apply {
                            put("sdp", sdp)
                            put("type", type)
                            put("iceRestart", true)
                        }
                        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("ICE_RESTART", "Envoi ICE restart OFFER (callee) vers $recipientKey")
                        com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendCallSignal(
                            recipientNpubOrHex = recipientKey,
                            callId = current.callId,
                            signalType = "OFFER",
                            payloadJson = payload
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("OrbisCallManager", "ICE restart offer (callee) failed: ${e.message}")
                    }
                }
            }

            // ── FLUSH ICE BUFFER — rejoue tous les candidats reçus pendant la sonnerie ──
            // Fix définitif Bug ICE : les candidats arrivés avant acceptCall() sont maintenant appliqués
            flushIceBuffer(current.callId)

            // ── ForegroundService microphone|camera — protège l'appel en arrière-plan ──
            OrbisCallForegroundService.start(
                context = context,
                peerName = current.peerName,
                isVideo = isVideoSession,
                isActiveMedia = true
            )

            val remoteSdp = current.peerSdpOffer
            if (!remoteSdp.isNullOrBlank()) {
                // WebRTC path: set remote offer, create answer SDP, send via Nostr
                OrbisWebRTCManager.setRemoteOfferAndCreateAnswer(remoteSdp, isVideoSession) { sdp, type ->
                    if (sdp != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val answerPayload = JSONObject().apply {
                                    put("action", "ACCEPTED")
                                    put("isVideoAccepted", isVideoSession)
                                    put("sdp", sdp)
                                    put("type", type ?: "answer")
                                }
                                com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("SIGNALING", "Signal ANSWER envoyé via Nostr (${sdp.length} octets) vers $recipientKey")
                                com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendCallSignal(
                                    recipientNpubOrHex = recipientKey,
                                    callId = current.callId,
                                    signalType = "ANSWER",
                                    payloadJson = answerPayload
                                )
                            } catch (e: Exception) {
                                android.util.Log.w("OrbisCallManager", "Could not send Nostr call answer: ${e.message}")
                            }
                        }
                    } else {
                        android.util.Log.e("OrbisCallManager", "createAnswer failed — callee")
                    }
                }
            } else {
                android.util.Log.e("OrbisCallManager", "SDP offer absent — impossible d'établir l'appel WebRTC")
                _callFeedback.value = CallFeedbackInfo(
                    peerPhone = current.peerPhone,
                    peerName = current.peerName,
                    reason = CallFeedbackReason.NO_ANSWER
                )
                OrbisWebRTCManager.stop()
                OrbisWebRTCManager.resetCallAudioMode(appContext)
                OrbisCallForegroundService.stop(appContext)
                clearIceBuffer()
                _callState.value = current.copy(status = CallStatus.ENDED)
                scope.launch { delay(1200); _callState.value = null }
                return
            }
        }

        val startTime = System.currentTimeMillis()
        _callState.value = current.copy(
            status = CallStatus.CONNECTING,
            isGsmFallback = false,
            isVideoCall = isVideoSession,
            isSpeakerOn = isVideoSession,
            startTime = 0L,
            durationSeconds = 0
        )

        currentCallLogId = current.callId
        logCallRecord(
            com.sha.orbis.model.CallRecord(
                id = current.callId,
                peerPhone = current.peerPhone,
                peerName = current.peerName,
                peerAvatar = current.peerAvatar,
                timestamp = startTime,
                durationSeconds = 0,
                direction = com.sha.orbis.model.CallDirection.INCOMING,
                isVideo = isVideoSession,
                threatLevel = current.guardAnalysis?.threatLevel?.name,
                threatType = current.guardAnalysis?.threatType?.name,
                trustScore = current.guardAnalysis?.trustScore ?: 100
            )
        )
    }

    // 4. REJECT INCOMING CALL (Pure Nostr Signal)
    fun rejectCall() {
        val current = _callState.value ?: return
        markCallHandled(current.callId)
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("CALL_REJECT", "Appel REJETÉ localement : callId=${current.callId}")
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.onCallEnded("Appel rejeté localement")

        // 1. Envoyer le signal de rejet Nostr immédiatement
        val context = appContext
        if (context != null) {
            val rejectedCallId = current.callId
            val recipientKey = current.peerNostrKey ?: current.peerPhone
            scope.launch(Dispatchers.IO) {
                try {
                    val answerPayload = JSONObject().apply {
                        put("action", "REJECTED")
                    }
                    com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendCallSignal(
                        recipientNpubOrHex = recipientKey,
                        callId = rejectedCallId,
                        signalType = "ANSWER",
                        payloadJson = answerPayload
                    )
                } catch (e: Throwable) {
                    android.util.Log.w("OrbisCallManager", "Could not dispatch Nostr call reject: ${e.message}")
                }
            }
        }

        // 2. Nettoyage sécurisé
        try { callTimeoutJob?.cancel() } catch (_: Throwable) {}
        try { OrbisCallSoundManager.stopAll() } catch (_: Throwable) {}
        try { releaseIncomingCallWakeLock() } catch (_: Throwable) {}
        try { OrbisProximityManager.stop() } catch (_: Throwable) {}
        if (context != null) {
            try { OrbisCallNotificationHelper.cancelCallNotification(context) } catch (_: Throwable) {}
        }
        try { OrbisWebRTCManager.stop() } catch (_: Throwable) {}
        try { OrbisWebRTCManager.resetCallAudioMode(appContext) } catch (_: Throwable) {}
        try { clearIceBuffer() } catch (_: Throwable) {}
        try { OrbisCallForegroundService.stop(appContext) } catch (_: Throwable) {}
        try { com.sha.orbis.call.telecom.OrbisTelecomHelper.onCallEnded(current.callId) } catch (_: Throwable) {}

        _callState.value = current.copy(status = CallStatus.ENDED)
        // Log missed call if it was incoming ringing (not yet accepted)
        if (current.status == CallStatus.INCOMING_RINGING) {
            logCallRecord(
                com.sha.orbis.model.CallRecord(
                    id = current.callId,
                    peerPhone = current.peerPhone,
                    peerName = current.peerName,
                    peerAvatar = current.peerAvatar,
                    timestamp = System.currentTimeMillis(),
                    durationSeconds = 0,
                    direction = com.sha.orbis.model.CallDirection.MISSED,
                    isVideo = current.isVideoCall,
                    threatLevel = current.guardAnalysis?.threatLevel?.name,
                    threatType = current.guardAnalysis?.threatType?.name,
                    trustScore = current.guardAnalysis?.trustScore ?: 100
                )
            )
        }
        currentCallLogId = null

        // Auto-save diagnostic report
        scope.launch(Dispatchers.IO) {
            try {
                appContext?.let { ctx ->
                    com.sha.orbis.call.diagnostic.CallDiagnosticLogger.generateReportSync(
                        ctx,
                        com.sha.orbis.call.diagnostic.CallDiagnosticEngine.state.value
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.e("OrbisCallManager", "Auto-save report error in rejectCall: ${t.message}")
            }
        }

        scope.launch {
            delay(1000)
            _callState.value = null
        }
    }

    /**
     * Declines the incoming VoIP call and dispatches an instant smart reply via E2EE Nostr Direct Message.
     */
    fun rejectCallWithQuickReply(context: Context, replyText: String) {
        val current = _callState.value
        val peerKey = current?.peerNostrKey?.takeIf { it.isNotBlank() } ?: current?.peerPhone
        rejectCall()
        if (!peerKey.isNullOrBlank() && replyText.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val digits = peerKey.filter { it.isDigit() }
                    val convId = if (digits.isNotBlank()) "conv_$digits" else "conv_${peerKey.take(16)}"
                    val msgId = "msg_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
                    com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendDirectMessage(
                        recipientNpubOrHex = peerKey,
                        conversationId = convId,
                        text = replyText,
                        messageId = msgId
                    )
                } catch (e: Throwable) {
                    android.util.Log.e("OrbisCallManager", "Could not send smart decline message: ${e.message}")
                }
            }
        }
    }

    /**
     * Ends the outgoing call prematurely and triggers the Walkie-Talkie post-call memo composer.
     */
    fun leaveVoiceMemoFallback() {
        val current = _callState.value ?: return
        _callFeedback.value = CallFeedbackInfo(
            peerPhone = current.peerPhone,
            peerName = current.peerName,
            reason = CallFeedbackReason.NO_ANSWER
        )
        endCall(sendSignal = true)
    }

    // 5. CALL ANSWERED (Received on Caller Device from Nostr Signal)
    fun onCallAnswerReceived(
        callId: String,
        action: String,
        ip: String = "",
        port: Int = 0,
        localIp: String = "",
        localPort: Int = 0,
        isVideoAccepted: Boolean = true,
        peerVideoIp: String = "",
        peerVideoPort: Int = 0,
        peerVideoLocalPort: Int = 0,
        peerSdpAnswer: String? = null   // WebRTC SDP answer from callee
    ) {
        val current = _callState.value
        android.util.Log.i("OrbisCallManager", "onCallAnswerReceived called: action=$action, callId=$callId, currentSession=${current?.callId}, currentStatus=${current?.status}, isVideoAccepted=$isVideoAccepted")
        if (current == null) {
            return
        }
        if (current.callId != callId) {
            android.util.Log.w("OrbisCallManager", "ANSWER ignored: callId=$callId != active=${current.callId}")
            return
        }

        val normalizedAction = action.trim().uppercase(java.util.Locale.ROOT)
        val isRejected = normalizedAction.contains("REJECT") ||
            normalizedAction.contains("DECLINE") ||
            normalizedAction.contains("BUSY")
        val isAccepted = !isRejected && (
            normalizedAction.startsWith("ACCEPT") ||
                normalizedAction.startsWith("ANSWER") ||
                !peerSdpAnswer.isNullOrBlank()
            )
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("SIGNALING", "Signal ANSWER reçu via Nostr : callId=$callId, action=$action, isAccepted=$isAccepted, sdpLen=${peerSdpAnswer?.length ?: 0}")

        if (isAccepted) {
            callTimeoutJob?.cancel()
            OrbisCallSoundManager.stopAll()
            releaseIncomingCallWakeLock()

            val effectiveIsVideo = current.isVideoCall && isVideoAccepted

            val context = appContext
            if (context != null) {
                OrbisCallNotificationHelper.cancelCallNotification(context)
                OrbisProximityManager.start(context, speakerInitiallyOn = effectiveIsVideo)
            }

            if (current.status != CallStatus.CONNECTED) {
                _callState.value = current.copy(
                    status = CallStatus.CONNECTING,
                    isGsmFallback = false,
                    startTime = 0L,
                    durationSeconds = 0,
                    isVideoCall = effectiveIsVideo,
                    isSpeakerOn = effectiveIsVideo
                )
            } else {
                _callState.value = current.copy(
                    isGsmFallback = false,
                    isVideoCall = effectiveIsVideo,
                    isSpeakerOn = effectiveIsVideo
                )
            }

            // ── WebRTC : appliquer le SDP answer reçu du callee ──
            // ICE negotiation démarre automatiquement (DTLS-SRTP E2EE garanti)
            if (context != null) {
                OrbisWebRTCManager.setSpeaker(context, effectiveIsVideo)
                if (!peerSdpAnswer.isNullOrBlank()) {
                    OrbisWebRTCManager.setRemoteAnswer(peerSdpAnswer)
                } else {
                    android.util.Log.e("OrbisCallManager", "SDP answer absent — fin de l'appel")
                    _callFeedback.value = CallFeedbackInfo(
                        peerPhone = current.peerPhone,
                        peerName = current.peerName,
                        reason = CallFeedbackReason.NO_ANSWER
                    )
                    endCall(sendSignal = false)
                }
            }
        } else {
            // Rejected
            callTimeoutJob?.cancel()
            releaseIncomingCallWakeLock()
            try { OrbisProximityManager.stop() } catch (_: Throwable) {}
            if (appContext != null) {
                OrbisCallNotificationHelper.cancelCallNotification(appContext!!)
            }
            OrbisWebRTCManager.stop()
            OrbisCallSoundManager.playEndCallTone()
            _callFeedback.value = CallFeedbackInfo(
                peerPhone = current.peerPhone,
                peerName = current.peerName,
                reason = CallFeedbackReason.REJECTED_BY_PEER
            )
            _callState.value = current.copy(status = CallStatus.ENDED)
            scope.launch {
                delay(1200)
                _callState.value = null
            }
        }
    }



    // 6. CALL ENDED BY REMOTE PEER
    fun onCallEndedRemote(callId: String = "") {
        val current = _callState.value ?: return
        android.util.Log.d("OrbisCallManager", "onCallEndedRemote received. Terminating current call session: ${current.callId}")
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("CALL_END", "Signal END reçu du correspondant distant (callId=${current.callId})")

        val wasIncomingRinging = (current.status == CallStatus.INCOMING_RINGING)
        if (wasIncomingRinging) {
            appContext?.let { ctx ->
                OrbisMissedCallManager.handleMissedCall(
                    context = ctx,
                    callId = current.callId,
                    peerPhone = current.peerPhone,
                    peerName = current.peerName,
                    peerAvatar = current.peerAvatar,
                    isVideo = current.isVideoCall,
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        markCallHandled(current.callId)
        if (callId.isNotBlank()) {
            markCallHandled(callId)
        }

        // Update call duration in log
        if (current.startTime > 0) {
            val duration = ((System.currentTimeMillis() - current.startTime) / 1000).toInt()
            try { updateCallDurationInLog(duration) } catch (_: Throwable) {}
        }
        currentCallLogId = null

        try { callTimeoutJob?.cancel() } catch (_: Throwable) {}
        try { timerJob?.cancel() } catch (_: Throwable) {}
        try { releaseIncomingCallWakeLock() } catch (_: Throwable) {}
        try { OrbisProximityManager.stop() } catch (_: Throwable) {}
        if (appContext != null) {
            try { OrbisCallNotificationHelper.cancelCallNotification(appContext!!) } catch (_: Throwable) {}
        }
        try { OrbisWebRTCManager.stop() } catch (t: Throwable) {
            android.util.Log.w("OrbisCallManager", "WebRTC stop error: ${t.message}")
        }
        try { clearIceBuffer() } catch (_: Throwable) {}
        try { OrbisCallForegroundService.stop(appContext) } catch (t: Throwable) {
            android.util.Log.w("OrbisCallManager", "Foreground service stop error: ${t.message}")
        }
        try { com.sha.orbis.call.telecom.OrbisTelecomHelper.onCallEnded(current.callId) } catch (_: Throwable) {}
        try { OrbisCallSoundManager.playEndCallTone() } catch (_: Throwable) {}

        com.sha.orbis.call.diagnostic.LastCallDebugTracker.onCallEnded("Terminé par le correspondant distant")
        _callState.value = current.copy(status = CallStatus.ENDED)

        try { OrbisWebRTCManager.resetCallAudioMode(appContext) } catch (_: Throwable) {}

        // Auto-save diagnostic report to disk immediately
        scope.launch(Dispatchers.IO) {
            try {
                appContext?.let { ctx ->
                    com.sha.orbis.call.diagnostic.CallDiagnosticLogger.generateReportSync(
                        ctx,
                        com.sha.orbis.call.diagnostic.CallDiagnosticEngine.state.value
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.e("OrbisCallManager", "Auto-save report error in onCallEndedRemote: ${t.message}")
            }
        }

        scope.launch {
            delay(1200)
            _callState.value = null
        }
    }

    // 7. END CALL (Local Action)
    fun endCall(sendSignal: Boolean = true) {
        val current = _callState.value
        if (current != null) {
            markCallHandled(current.callId)
            val reason = if (current.status == CallStatus.CONNECTED) "Appel terminé normalement (Local)"
                         else "Appel interrompu avant connexion (Statut: ${current.status})"
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("CALL_END", "Appel terminé localement : $reason (callId=${current.callId})")
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.onCallEnded(reason)

            if (current.status != CallStatus.CONNECTED && current.status != CallStatus.ENDED && current.status != CallStatus.IDLE && appContext != null) {
                try {
                    val tm = com.sha.orbis.telemetry.TelemetryManager.getInstance(appContext!!)
                    if (current.isVideoCall) {
                        tm.recordEvent(com.sha.orbis.telemetry.FeatureType.VIDEO_CALL_FAIL)
                    } else {
                        tm.recordEvent(com.sha.orbis.telemetry.FeatureType.VOICE_CALL_FAIL)
                    }
                } catch (_: Exception) {}
            }
        }

        // 1. DISPATCH NOSTR "END" SIGNAL FIRST — avant tout nettoyage local
        // Garantit que le correspondant reçoit la fin d'appel même si le teardown local rencontre une anomalie
        if (current != null && sendSignal && appContext != null) {
            val endedCallId = current.callId
            val recipientKey = current.peerNostrKey ?: current.peerPhone
            scope.launch(Dispatchers.IO) {
                try {
                    com.sha.orbis.nostr.service.NostrSyncManager.getInstance(appContext!!).sendCallSignal(
                        recipientNpubOrHex = recipientKey,
                        callId = endedCallId,
                        signalType = "END",
                        payloadJson = JSONObject()
                    )
                } catch (e: Throwable) {
                    android.util.Log.w("OrbisCallManager", "Could not dispatch Nostr call end: ${e.message}")
                }
            }
        }

        // 2. Basculer immédiatement le statut local vers ENDED pour mettre à jour l'UI
        if (current != null) {
            _callState.value = current.copy(status = CallStatus.ENDED)
        }

        // 3. Arrêt des timers et wakeLock
        try { callTimeoutJob?.cancel() } catch (_: Throwable) {}
        try { timerJob?.cancel() } catch (_: Throwable) {}
        try { releaseIncomingCallWakeLock() } catch (_: Throwable) {}
        try { OrbisProximityManager.stop() } catch (_: Throwable) {}
        if (appContext != null) {
            try { OrbisCallNotificationHelper.cancelCallNotification(appContext!!) } catch (_: Throwable) {}
        }

        // 4. Arrêt WebRTC
        try {
            OrbisWebRTCManager.stop()
        } catch (t: Throwable) {
            android.util.Log.w("OrbisCallManager", "WebRTC stop error in endCall: ${t.message}")
        }
        try { clearIceBuffer() } catch (_: Throwable) {}

        // 5. Arrêt Foreground Service
        try {
            OrbisCallForegroundService.stop(appContext)
        } catch (t: Throwable) {
            android.util.Log.w("OrbisCallManager", "Foreground service stop error: ${t.message}")
        }

        // 6. Arrêt Telecom VoIP
        if (current != null) {
            try {
                com.sha.orbis.call.telecom.OrbisTelecomHelper.onCallEnded(current.callId)
            } catch (t: Throwable) {
                android.util.Log.w("OrbisCallManager", "Telecom onCallEnded error: ${t.message}")
            }
        }

        // 7. Tonalité de fin d'appel et reset audio
        try { OrbisCallSoundManager.playEndCallTone() } catch (_: Throwable) {}
        try { OrbisWebRTCManager.resetCallAudioMode(appContext) } catch (_: Throwable) {}

        // 8. Mise à jour de l'historique d'appels
        if (current != null && current.startTime > 0) {
            val duration = ((System.currentTimeMillis() - current.startTime) / 1000).toInt()
            try { updateCallDurationInLog(duration) } catch (_: Throwable) {}
        }
        currentCallLogId = null

        // Auto-save diagnostic report to disk immediately
        scope.launch(Dispatchers.IO) {
            try {
                appContext?.let { ctx ->
                    com.sha.orbis.call.diagnostic.CallDiagnosticLogger.generateReportSync(
                        ctx,
                        com.sha.orbis.call.diagnostic.CallDiagnosticEngine.state.value
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.e("OrbisCallManager", "Auto-save report error in endCall: ${t.message}")
            }
        }

        scope.launch {
            delay(1200)
            _callState.value = null
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                val current = _callState.value ?: break
                if (current.status == CallStatus.CONNECTED) {
                    _callState.value = current.copy(durationSeconds = current.durationSeconds + 1)
                } else {
                    break
                }
            }
        }
    }

    fun toggleMute() {
        val current = _callState.value ?: return
        val newMute = !current.isMuted
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO", "Bascule micro : ${if (newMute) "COUPÉ (Mute)" else "ACTIF (Unmute)"}")
        // Mute via WebRTC audio track uniquement — pas AudioManager.isMicrophoneMute
        // qui entre en conflit avec JavaAudioDeviceModule sur Vivo/Honor
        OrbisWebRTCManager.toggleMute(newMute)
        _callState.value = current.copy(isMuted = newMute)
    }

    fun toggleSpeaker() {
        val current = _callState.value ?: return
        val newSpeaker = !current.isSpeakerOn
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("AUDIO", "Bascule haut-parleur : ${if (newSpeaker) "HAUT-PARLEUR" else "ÉCOUTEUR INTERNE"}")
        val context = appContext
        if (context != null) {
            OrbisWebRTCManager.setSpeaker(context, newSpeaker)
        }
        _callState.value = current.copy(isSpeakerOn = newSpeaker)
        OrbisProximityManager.setSpeakerOn(newSpeaker)
        com.sha.orbis.call.telecom.OrbisTelecomHelper.setSpeaker(newSpeaker)

        if (current.status == CallStatus.OUTGOING_CALLING || current.status == CallStatus.OUTGOING_RINGING) {
            OrbisCallSoundManager.playOutgoingRingback(isSpeaker = newSpeaker)
        }
    }

    fun toggleCamera(enabled: Boolean? = null) {
        val current = _callState.value ?: return
        val newEnabled = enabled ?: !current.isCameraEnabled
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("VIDEO", "Bascule caméra locale : enabled=$newEnabled")
        OrbisWebRTCManager.toggleCamera(newEnabled)
        _callState.value = current.copy(isCameraEnabled = newEnabled)
    }

    fun switchCamera() {
        OrbisWebRTCManager.switchCamera { isFront ->
            val current = _callState.value ?: return@switchCamera
            com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("VIDEO", "Caméra basculée : isFront=$isFront")
            _callState.value = current.copy(isFrontCamera = isFront)
        }
    }

    /**
     * Reçu de NostrSyncManager quand sigType == "CANDIDATE" — ICE trickle WebRTC.
     *
     * Correction définitive Bug 2+3 + Réserves 1 & 2 :
     * - callId obligatoire : candidat d'un ancien appel ou appel concurrent ignoré
     * - Si _callState n'existe pas encore (CANDIDATE arrivé avant OFFER) :
     *   stocké dans orphanIceCandidates, drainé dans onIncomingCallReceived()
     * - Si PeerConnection pas encore prête (sonnerie avant acceptCall) :
     *   bufferisé dans iceCandidateBuffer[callId] (thread-safe)
     * - Sinon : livré directement à OrbisWebRTCManager
     */
    fun onIceCandidateReceived(candidateSdp: String, sdpMid: String, sdpMLineIndex: Int, callId: String) {
        val current = _callState.value
        val data = IceCandidateData(candidateSdp, sdpMid, sdpMLineIndex)
        com.sha.orbis.call.diagnostic.LastCallDebugTracker.logEvent("ICE_SIGNAL", "Candidat ICE distant reçu via Nostr : callId=$callId, mid=$sdpMid, line=$sdpMLineIndex")

        // Réserve 1 : si _callState n'existe pas encore (CANDIDATE arrivé avant OFFER)
        if (current == null) {
            synchronized(orphanIceCandidates) {
                orphanIceCandidates.add(callId to data)
            }
            android.util.Log.d("OrbisCallManager",
                "ICE candidat orphelin [pas encore de callState] callId=$callId")
            return
        }

        // Rejeter les candidats qui n'appartiennent pas à l'appel actif
        // SAUF si l'état est ENDED (fenêtre de 1200ms) → ils vont dans orphanIceCandidates
        if (current.callId != callId) {
            if (current.status == CallStatus.ENDED) {
                // L'appel précédent se termine → ce candidat appartient peut-être au prochain appel
                synchronized(orphanIceCandidates) {
                    orphanIceCandidates.add(callId to data)
                }
                android.util.Log.d("OrbisCallManager",
                    "ICE candidat orphelin [appel ENDED en cours] callId=$callId → bufferisé")
            } else {
                android.util.Log.w("OrbisCallManager",
                    "ICE candidate ignoré (callId=$callId ≠ actif=${current.callId})")
            }
            return
        }

        // Si PeerConnection pas encore créée (sonnerie entrante avant acceptCall)
        if (!OrbisWebRTCManager.isPeerConnectionReady()) {
            val buf = getOrCreateBuffer(callId)
            synchronized(buf) { buf.add(data) }
            android.util.Log.d("OrbisCallManager",
                "ICE candidat bufferisé [PeerConnection pas prête] callId=$callId")
            return
        }

        OrbisWebRTCManager.addIceCandidate(candidateSdp, sdpMid, sdpMLineIndex)
    }


    fun computeSasCode(peerPhone: String, myPhone: String): String {
        return try {
            val combined = listOf(peerPhone.filter { it.isDigit() }, myPhone.filter { it.isDigit() }).sorted().joinToString(":")
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(combined.toByteArray())
            val num = abs(ByteBufferToInt(hash)) % 9000 + 1000
            num.toString()
        } catch (_: Exception) {
            "7842"
        }
    }

    private fun releaseIncomingCallWakeLock() {
        try {
            if (incomingCallWakeLock?.isHeld == true) {
                incomingCallWakeLock?.release()
            }
            incomingCallWakeLock = null
        } catch (_: Exception) {}
    }

    private fun ByteBufferToInt(bytes: ByteArray): Int {
        var result = 0
        for (i in 0 until 4.coerceAtMost(bytes.size)) {
            result = (result shl 8) or (bytes[i].toInt() and 0xFF)
        }
        return result
    }

    fun isBluetoothAudioConnected(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { device ->
                    device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun isDoNotDisturbActive(context: Context): Boolean {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (notificationManager != null) {
                val filter = notificationManager.currentInterruptionFilter
                filter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE ||
                filter == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                filter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
