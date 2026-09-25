package com.sha.orbis.call.diagnostic

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LastCallDebugTracker — Journalise en temps réel les événements techniques critiques
 * du DERNIER appel réel (audio ou vidéo) passé ou reçu.
 *
 * Ces informations sont directement injectées dans le rapport de débogage pour
 * identifier instantanément la cause d'un appel muet, d'un écran noir ou d'un échec ICE.
 */
object LastCallDebugTracker {

    private const val TAG = "LastCallDebugTracker"

    @Volatile var callId: String? = null
    @Volatile var direction: String = "INCONNU"
    @Volatile var isVideo: Boolean = false
    @Volatile var peerPhone: String = ""
    @Volatile var peerName: String = ""
    @Volatile var startTimestamp: Long = 0L
    @Volatile var connectedTimestamp: Long = 0L
    @Volatile var endTimestamp: Long = 0L

    @Volatile var sdpOfferLength: Int = 0
    @Volatile var sdpOfferCodecs: String = ""
    @Volatile var sdpAnswerLength: Int = 0
    @Volatile var sdpAnswerCodecs: String = ""

    @Volatile var localIceCandidatesCount: Int = 0
    @Volatile var localRelayCandidatesCount: Int = 0
    @Volatile var localSrflxCandidatesCount: Int = 0
    @Volatile var localHostCandidatesCount: Int = 0

    @Volatile var remoteIceCandidatesCount: Int = 0
    @Volatile var remoteRelayCandidatesCount: Int = 0
    @Volatile var remoteSrflxCandidatesCount: Int = 0
    @Volatile var remoteHostCandidatesCount: Int = 0

    @Volatile var lastIceState: String = "AUCUN"

    private val iceStateHistory = java.util.Collections.synchronizedList(mutableListOf<String>())

    @Volatile var audioRecordError: String? = null
    @Volatile var audioTrackError: String? = null
    @Volatile var endReason: String = "En cours"

    @Volatile var localVideoStarted: Boolean? = null
    @Volatile var remoteVideoTrackReceived: Boolean = false
    @Volatile var remoteAudioTrackReceived: Boolean = false
    @Volatile var everConnected: Boolean = false

    private val callEventsLog = java.util.Collections.synchronizedList(mutableListOf<String>())
    private const val MAX_LOG_EVENTS = 500

    fun logEvent(tag: String, message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timeStr = sdf.format(Date())
        val line = "[$timeStr] [$tag] $message"
        synchronized(callEventsLog) {
            if (callEventsLog.size >= MAX_LOG_EVENTS) {
                callEventsLog.removeAt(0)
            }
            callEventsLog.add(line)
        }
        Log.i(tag, message)
    }

    fun onCallStarted(
        id: String,
        direction: String,
        peerPhone: String,
        peerName: String,
        isVideo: Boolean
    ) {
        this.callId = id
        this.direction = direction
        this.peerPhone = peerPhone
        this.peerName = peerName
        this.isVideo = isVideo
        this.startTimestamp = System.currentTimeMillis()
        this.connectedTimestamp = 0L
        this.endTimestamp = 0L
        this.sdpOfferLength = 0
        this.sdpOfferCodecs = ""
        this.sdpAnswerLength = 0
        this.sdpAnswerCodecs = ""
        this.localIceCandidatesCount = 0
        this.localRelayCandidatesCount = 0
        this.localSrflxCandidatesCount = 0
        this.localHostCandidatesCount = 0
        this.remoteIceCandidatesCount = 0
        this.remoteRelayCandidatesCount = 0
        this.remoteSrflxCandidatesCount = 0
        this.remoteHostCandidatesCount = 0
        this.lastIceState = "NEW"
        this.iceStateHistory.clear()
        this.iceStateHistory.add("NEW")
        this.audioRecordError = null
        this.audioTrackError = null
        this.localVideoStarted = null
        this.remoteVideoTrackReceived = false
        this.remoteAudioTrackReceived = false
        this.everConnected = false
        this.endReason = "En cours d'établissement"

        logEvent("SESSION", "==================== NOUVEL APPEL ($direction) ====================")
        logEvent(TAG, "Démarrage enregistrement appel réel: id=$id, dir=$direction, isVideo=$isVideo, peer=$peerName ($peerPhone)")
    }

    fun onSdpOffer(sdp: String) {
        this.sdpOfferLength = sdp.length
        this.sdpOfferCodecs = extractCodecs(sdp)
        Log.d(TAG, "SDP Offer enregistrée: longueur=${sdp.length}, codecs=$sdpOfferCodecs")
    }

    fun onSdpAnswer(sdp: String) {
        this.sdpAnswerLength = sdp.length
        this.sdpAnswerCodecs = extractCodecs(sdp)
        Log.d(TAG, "SDP Answer enregistrée: longueur=${sdp.length}, codecs=$sdpAnswerCodecs")
    }

    fun onLocalIceCandidate(type: String = "inconnu") {
        localIceCandidatesCount++
        when (type.lowercase()) {
            "relay" -> localRelayCandidatesCount++
            "srflx" -> localSrflxCandidatesCount++
            "host" -> localHostCandidatesCount++
        }
    }

    fun onRemoteIceCandidate(type: String = "inconnu") {
        remoteIceCandidatesCount++
        when (type.lowercase()) {
            "relay" -> remoteRelayCandidatesCount++
            "srflx" -> remoteSrflxCandidatesCount++
            "host" -> remoteHostCandidatesCount++
        }
    }

    fun onIceConnectionChange(newState: String) {
        this.lastIceState = newState
        synchronized(iceStateHistory) {
            iceStateHistory.add(newState)
        }
        if (newState.equals("CONNECTED", ignoreCase = true) || newState.equals("COMPLETED", ignoreCase = true)) {
            everConnected = true
            connectedTimestamp = System.currentTimeMillis()
        }
        Log.i(TAG, "ICE State transition: $newState (local=$localIceCandidatesCount [relay=$localRelayCandidatesCount], remote=$remoteIceCandidatesCount [relay=$remoteRelayCandidatesCount])")
    }

    fun onAudioError(isRecord: Boolean, errorMsg: String) {
        if (isRecord) {
            audioRecordError = errorMsg
        } else {
            audioTrackError = errorMsg
        }
        Log.e(TAG, "Erreur audio [${if (isRecord) "Record" else "Track"}]: $errorMsg")
    }

    fun onLocalVideoStarted(started: Boolean) {
        this.localVideoStarted = started
        Log.i(TAG, "Local video capturer started=$started")
    }

    fun onRemoteVideoTrackReceived() {
        this.remoteVideoTrackReceived = true
        Log.i(TAG, "Remote video track received")
    }

    fun onRemoteAudioTrackReceived() {
        this.remoteAudioTrackReceived = true
        Log.i(TAG, "Remote audio track received")
    }

    fun onCallEnded(reason: String) {
        this.endTimestamp = System.currentTimeMillis()
        this.endReason = reason
        logEvent(TAG, "Fin enregistrement appel: durée=${getEffectiveDurationSec()}s, dernier état ICE=$lastIceState, raison=$reason")
        logEvent("SESSION", "==================== FIN D'APPEL ====================")
    }

    fun getEffectiveDurationSec(): Long {
        if (connectedTimestamp <= 0L) return 0L
        val end = if (endTimestamp > 0L) endTimestamp else System.currentTimeMillis()
        return maxOf(0L, (end - connectedTimestamp) / 1000)
    }

    private fun extractCodecs(sdp: String): String {
        val codecs = mutableSetOf<String>()
        val regex = Regex("a=rtpmap:\\d+\\s+([A-Za-z0-9-]+)/")
        regex.findAll(sdp).forEach { match ->
            codecs.add(match.groupValues[1])
        }
        return codecs.joinToString(", ")
    }

    /**
     * Génère la section texte formatée à intégrer dans le rapport de débogage.
     */
    fun buildReportSection(): String {
        val sb = StringBuilder()
        sb.appendLine("[5. JOURNAL DU DERNIER APPEL RÉEL EFFECTUÉ]")
        if (callId == null) {
            sb.appendLine("Aucun appel audio ou vidéo n'a encore été effectué depuis le lancement de l'application.")
            sb.appendLine("Passez un appel test entre les deux téléphones puis relancez le diagnostic pour voir les métriques.")
            return sb.toString()
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = if (startTimestamp > 0) sdf.format(Date(startTimestamp)) else "N/A"
        val durationSec = if (connectedTimestamp > 0) {
            val end = if (endTimestamp > 0) endTimestamp else System.currentTimeMillis()
            (end - connectedTimestamp) / 1000
        } else 0L

        val isSuccess = everConnected ||
                lastIceState.equals("CONNECTED", ignoreCase = true) ||
                lastIceState.equals("COMPLETED", ignoreCase = true)
        val isFailed = lastIceState.equals("FAILED", ignoreCase = true)
        val isCheckingStuck = !isSuccess && !isFailed && iceStateHistory.contains("CHECKING")
        val badgeText = when {
            isSuccess -> "CONNECTÉ ✓"
            isFailed -> "ÉCHEC ICE"
            isCheckingStuck -> "BLOCAGE ICE (CHECKING - NAT Symétrique)"
            else -> "ICE: $lastIceState"
        }

        sb.appendLine("================== [SYNTHÈSE CARTE VISUELLE APPLICATION] ==================")
        sb.appendLine("Badge Résultat                    : [$badgeText]")
        sb.appendLine("Type & Direction                  : ${if (isVideo) "Vidéo" else "Audio"} • $direction")
        sb.appendLine("Correspondant                     : $peerName ($peerPhone)")
        sb.appendLine("Statut affiché                    : $endReason")
        sb.appendLine("Codecs SDP négociés               : Offer=[${sdpOfferCodecs.ifEmpty { "N/A" }}] ➔ Answer=[${sdpAnswerCodecs.ifEmpty { "N/A" }}]")
        sb.appendLine("Candidats ICE                     : Local=$localIceCandidatesCount (relay=$localRelayCandidatesCount, srflx=$localSrflxCandidatesCount, host=$localHostCandidatesCount) | Reçus=$remoteIceCandidatesCount (relay=$remoteRelayCandidatesCount, srflx=$remoteSrflxCandidatesCount, host=$remoteHostCandidatesCount)")
        sb.appendLine("Flux distants                     : Audio=${if (remoteAudioTrackReceived) "Reçu ✓" else "Non reçu ✗"}${if (isVideo) " | Vidéo=${if (remoteVideoTrackReceived) "Reçue ✓" else "Non reçue ✗"}" else ""}")
        if (audioRecordError != null) sb.appendLine("Alerte Micro                      : $audioRecordError")
        if (audioTrackError != null) sb.appendLine("Alerte Écouteur                   : $audioTrackError")
        sb.appendLine("============================================================================")
        sb.appendLine()
        sb.appendLine("Identifiant de l'appel (Call ID) : $callId")
        sb.appendLine("Date de l'appel                   : $dateStr")
        sb.appendLine("Durée de communication effective  : ${durationSec}s")
        sb.appendLine()
        sb.appendLine("--- Échange de Signalisation WebRTC / Nostr ---")
        sb.appendLine("  * SDP Offer transmise/reçue     : ${if (sdpOfferLength > 0) "OUI ($sdpOfferLength octets) [Codecs: $sdpOfferCodecs]" else "NON / ABSENTE"}")
        sb.appendLine("  * SDP Answer transmise/reçue    : ${if (sdpAnswerLength > 0) "OUI ($sdpAnswerLength octets) [Codecs: $sdpAnswerCodecs]" else "NON / ABSENTE"}")
        sb.appendLine("  * Candidats ICE émis (Local)    : $localIceCandidatesCount (relay=$localRelayCandidatesCount, srflx=$localSrflxCandidatesCount, host=$localHostCandidatesCount)")
        sb.appendLine("  * Candidats ICE reçus (Remote)  : $remoteIceCandidatesCount (relay=$remoteRelayCandidatesCount, srflx=$remoteSrflxCandidatesCount, host=$remoteHostCandidatesCount)")
        sb.appendLine()
        sb.appendLine("--- Connectivité Média & ICE ---")
        sb.appendLine("  * Dernier état ICE WebRTC       : $lastIceState")
        sb.appendLine("  * Historique transitions ICE    : ${iceStateHistory.joinToString(" ➔ ")}")
        if (audioRecordError != null) {
            sb.appendLine("  * ALERTE AudioRecord (Micro)   : $audioRecordError")
        }
        if (audioTrackError != null) {
            sb.appendLine("  * ALERTE AudioTrack (Écouteur) : $audioTrackError")
        }
        sb.appendLine()
        sb.appendLine("--- Flux Média Reçus & Émis ---")
        sb.appendLine("  * Flux Audio distant reçu       : ${if (remoteAudioTrackReceived) "OUI (Piste audio reconnue)" else "NON (Silence / aucune piste reçue)"}")
        if (isVideo) {
            sb.appendLine("  * Caméra locale démarrée        : ${when (localVideoStarted) { true -> "OUI"; false -> "ÉCHEC CAPTUREUR CAMÉRA"; else -> "N/A" }}")
            sb.appendLine("  * Flux Vidéo distant reçu       : ${if (remoteVideoTrackReceived) "OUI (Flux vidéo reçu)" else "NON (Écran noir / aucun flux reçu)"}")
        }

        sb.appendLine()
        sb.appendLine("--- Diagnostic Automatique de l'Appel ---")
        when {
            lastIceState.equals("CONNECTED", ignoreCase = true) || lastIceState.equals("COMPLETED", ignoreCase = true) -> {
                sb.appendLine("  -> Le flux P2P / TURN a été établi avec succès !")
                if (localRelayCandidatesCount > 0 || remoteRelayCandidatesCount > 0) {
                    sb.appendLine("  -> Note : Des candidats TURN relay ont été échangés pour traverser le NAT.")
                }
                if (isVideo && (sdpOfferCodecs.contains("H264") && !sdpAnswerCodecs.contains("H264") && !sdpAnswerCodecs.contains("VP8"))) {
                    sb.appendLine("  -> AVERTISSEMENT : Incohérence possible des codecs vidéo négociés.")
                }
            }
            sdpOfferLength == 0 -> {
                sb.appendLine("  -> ÉCHEC : La proposition d'appel (SDP Offer) n'a pas été générée ou reçue via Nostr.")
            }
            sdpAnswerLength == 0 -> {
                sb.appendLine("  -> ÉCHEC : L'autre appareil n'a pas renvoyé de réponse SDP (Answer) ou l'appel a été refusé.")
            }
            remoteIceCandidatesCount == 0 -> {
                sb.appendLine("  -> ÉCHEC : Aucun candidat ICE reçu de l'autre appareil ! Vérifiez la connexion Nostr du correspondant.")
            }
            isCheckingStuck -> {
                sb.appendLine("  -> ÉCHEC TRAVERSÉE NAT : L'état ICE est resté bloqué en CHECKING.")
                sb.appendLine("     Les deux appareils sont sous NAT symétrique (CGNAT 4G/5G).")
                sb.appendLine("     Candidats relay échangés : Local=$localRelayCandidatesCount, Reçus=$remoteRelayCandidatesCount.")
                if (localRelayCandidatesCount == 0 && remoteRelayCandidatesCount == 0) {
                    sb.appendLine("     AUCUN candidat relay n'a été produit. Vérifiez l'accessibilité des serveurs TURN.")
                }
            }
            lastIceState.equals("FAILED", ignoreCase = true) -> {
                sb.appendLine("  -> ÉCHEC ICE FAILED : Les deux téléphones n'ont pas pu se joindre.")
                sb.appendLine("     Causes possibles : NAT symétrique 4G non traversé, ou relais TURN bloqué.")
            }
            else -> {
                sb.appendLine("  -> État intermédiaire : $lastIceState ($endReason)")
            }
        }
        return sb.toString()
    }

    fun buildCallEventsSection(): String {
        val sb = StringBuilder()
        sb.appendLine("[6. FLUX EN DIRECT DES ÉVÉNEMENTS D'APPEL (TIMELINE LOGS)]")
        synchronized(callEventsLog) {
            if (callEventsLog.isEmpty()) {
                sb.appendLine("Aucun événement d'appel enregistré pour le moment.")
            } else {
                callEventsLog.forEach { sb.appendLine(it) }
            }
        }
        return sb.toString()
    }
}
