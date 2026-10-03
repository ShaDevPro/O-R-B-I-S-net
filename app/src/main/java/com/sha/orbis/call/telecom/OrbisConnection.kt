package com.sha.orbis.call.telecom

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.sha.orbis.call.OrbisCallManager
import com.sha.orbis.call.OrbisCallSoundManager
import com.sha.orbis.call.OrbisWebRTCManager

/**
 * OrbisConnection — Représentation système d'un appel VoIP OrbisNet via Android Telecom.
 *
 * 100% INTERNET (Wi-Fi / Données Mobiles) — JAMAIS de GSM cellulaire.
 * Utilise TelecomManager avec CAPABILITY_SELF_MANAGED pour :
 *   1. Immunité contre les tueurs de batterie (Vivo FunTouch, Honor MagicUI, MIUI)
 *   2. Intégration audio native de l'OS (écouteur, haut-parleur, Bluetooth de voiture/casque)
 *   3. Notification d'appel système de premier rang (Heads-up / Écran de verrouillage)
 *   4. Gestion des conflits d'appels (ex: mise en attente si appel GSM entrant)
 */
@RequiresApi(Build.VERSION_CODES.O)
class OrbisConnection(
    val callId: String,
    val peerPhone: String,
    val peerName: String,
    val isVideo: Boolean,
    val isIncoming: Boolean
) : Connection() {

    companion object {
        private const val TAG = "OrbisConnection"
    }

    init {
        // Déclare la connexion comme VoIP auto-gérée
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
        
        // Capacités de l'appel
        connectionCapabilities = CAPABILITY_MUTE or
                CAPABILITY_SUPPORT_HOLD or
                CAPABILITY_HOLD

        // Adresses et nom affichés par le système Android
        val addressUri = Uri.fromParts("sip", peerPhone.filter { it.isDigit() || it == '+' }, null)
        setAddress(addressUri, TelecomManager.PRESENTATION_ALLOWED)
        setCallerDisplayName(peerName.ifBlank { peerPhone }, TelecomManager.PRESENTATION_ALLOWED)

        if (isVideo) {
            videoState = android.telecom.VideoProfile.STATE_BIDIRECTIONAL
            try {
                setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            } catch (_: Throwable) {}
        } else {
            videoState = android.telecom.VideoProfile.STATE_AUDIO_ONLY
        }

        val extras = Bundle().apply {
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, isVideo)
            putString("com.sha.orbis.CALL_ID", callId)
        }
        putExtras(extras)
    }

    /**
     * L'utilisateur ou le système répond à l'appel entrant (depuis notification ou montre/voiture).
     */
    private var isTerminated = false

    override fun onAnswer(videoState: Int) {
        Log.i(TAG, "[$callId] onAnswer called from Android Telecom (videoState=$videoState)")
        try { setActive() } catch (_: Exception) {}
        if (isVideo) {
            try { setAudioRoute(CallAudioState.ROUTE_SPEAKER) } catch (_: Throwable) {}
        }
        OrbisCallSoundManager.stopAll()
        OrbisCallManager.acceptCall()
    }

    /**
     * L'utilisateur rejette l'appel entrant depuis le système.
     */
    override fun onReject() {
        Log.i(TAG, "[$callId] onReject called from Android Telecom")
        if (isTerminated) return
        isTerminated = true
        try { setDisconnected(DisconnectCause(DisconnectCause.REJECTED)) } catch (_: Exception) {}
        try { destroy() } catch (_: Exception) {}
        OrbisCallManager.rejectCall()
    }

    /**
     * L'utilisateur ou le système raccroche l'appel en cours.
     */
    override fun onDisconnect() {
        Log.i(TAG, "[$callId] onDisconnect called from Android Telecom")
        if (isTerminated) return
        isTerminated = true
        try { setDisconnected(DisconnectCause(DisconnectCause.LOCAL)) } catch (_: Exception) {}
        try { destroy() } catch (_: Exception) {}
        OrbisCallManager.endCall(sendSignal = true)
    }

    override fun onAbort() {
        Log.i(TAG, "[$callId] onAbort called from Android Telecom")
        if (isTerminated) return
        isTerminated = true
        try { setDisconnected(DisconnectCause(DisconnectCause.CANCELED)) } catch (_: Exception) {}
        try { destroy() } catch (_: Exception) {}
        OrbisCallManager.endCall(sendSignal = true)
    }

    override fun onSilence() {
        Log.i(TAG, "[$callId] onSilence: silence ringtone")
        OrbisCallSoundManager.stopAll()
    }

    /**
     * Changement de routage audio géré par le système Android (Haut-parleur, Bluetooth, Écouteur).
     */
    override fun onCallAudioStateChanged(state: CallAudioState?) {
        if (state == null) return
        Log.i(TAG, "[$callId] onCallAudioStateChanged: route=${state.route}, isMuted=${state.isMuted}")
        
        // Synchroniser le mute avec WebRTC si différent
        val currentMute = OrbisCallManager.callState.value?.isMuted ?: false
        if (state.isMuted != currentMute) {
            OrbisWebRTCManager.toggleMute(state.isMuted)
        }

        // Synchroniser l'extinction d'écran de proximité avec le routage audio Telecom :
        // L'écran s'éteint à l'oreille uniquement si le son sort par l'écouteur (ROUTE_EARPIECE).
        // S'il est sur haut-parleur, bluetooth ou casque filaire, l'écran ne doit pas s'éteindre.
        val isEarPiece = (state.route == CallAudioState.ROUTE_EARPIECE)
        try {
            com.sha.orbis.call.OrbisProximityManager.setSpeakerOn(!isEarPiece)
        } catch (_: Throwable) {}
    }

    override fun onHold() {
        Log.i(TAG, "[$callId] onHold")
        try { setOnHold() } catch (_: Exception) {}
    }

    override fun onUnhold() {
        Log.i(TAG, "[$callId] onUnhold")
        try { setActive() } catch (_: Exception) {}
    }

    /**
     * Notification à Android Telecom que l'appel distant sonne.
     */
    fun notifyRinging() {
        if (isTerminated) return
        try { setRinging() } catch (_: Exception) {}
    }

    /**
     * Notification à Android Telecom que la communication est établie.
     */
    fun notifyConnected() {
        if (isTerminated) return
        try { setActive() } catch (_: Exception) {}
        if (isVideo) {
            try { setAudioRoute(CallAudioState.ROUTE_SPEAKER) } catch (_: Throwable) {}
        }
    }

    /**
     * Bascule la route audio système entre Haut-Parleur et Écouteur.
     */
    fun setAudioRouteSpeaker(on: Boolean) {
        if (isTerminated) return
        try {
            val targetRoute = if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            setAudioRoute(targetRoute)
            Log.i(TAG, "[$callId] setAudioRoute applied: route=$targetRoute (onSpeaker=$on)")
        } catch (e: Throwable) {
            Log.w(TAG, "[$callId] setAudioRoute error: ${e.message}")
        }
    }

    /**
     * Clôture propre et définitive de la connexion Telecom.
     */
    @Synchronized
    fun notifyTerminated(cause: Int = DisconnectCause.LOCAL) {
        if (isTerminated) return
        isTerminated = true
        try {
            setDisconnected(DisconnectCause(cause))
        } catch (_: Throwable) {}
        try {
            destroy()
        } catch (_: Throwable) {}
        try {
            OrbisConnectionService.clearActiveConnection(this)
        } catch (_: Throwable) {}
    }
}
