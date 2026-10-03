package com.sha.orbis.call.fcm

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sha.orbis.call.OrbisCallForegroundService
import com.sha.orbis.call.OrbisCallManager

/**
 * OrbisFirebaseMessagingService — Réception du signal FCM haute priorité pour réveil Doze mode.
 *
 * Message Data-Only : AUCUNE notification visible n'est affichée par FCM.
 * Rôle unique : Tirer le processeur du mode veille et forcer la reconnexion Nostr
 * pour recevoir le signal WebRTC OFFER sans délai.
 */
class OrbisFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "OrbisFCM"
        private const val PREFS_NAME = "orbis_fcm_prefs"
        private const val KEY_FCM_TOKEN = "device_fcm_token"

        fun getSavedToken(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, "") ?: ""
        }

        fun saveToken(context: Context, token: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token)
                .apply()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM Registration Token: $token")
        saveToken(this, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val type = remoteMessage.data["type"]
        Log.i(TAG, "FCM data message received: type=$type")
        if (type == "call_wake") {
            val callId = remoteMessage.data["callId"] ?: return
            val callerPhone = remoteMessage.data["callerPhone"] ?: ""
            val callerName = remoteMessage.data["callerName"] ?: callerPhone
            val isVideo = remoteMessage.data["isVideo"]?.toBoolean() ?: false

            // Garde-fou sécurité : ignorer silencieusement si l'appelant est bloqué
            if (com.sha.orbis.storage.BlockedContactsRepository(this).isBlocked(callerPhone)) {
                Log.d(TAG, "Appel FCM entrant ignoré : appelant bloqué ($callerPhone)")
                return
            }

            // Anti-doublon : si l'appel est déjà pris en charge par Nostr / Telecom, sortir immédiatement
            val current = OrbisCallManager.callState.value
            if (current != null && current.callId == callId) {
                Log.d(TAG, "Appel $callId déjà actif/en cours via Nostr. Zéro notification doublon.")
                return
            }

            // Réveil physique du CPU & de l'Écran (Hardware Wakeup pour contourner le blocage Doze & Vivo OriginOS)
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val cpuWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "orbis:fcm_call_wake")
                cpuWakeLock?.acquire(30_000L)

                @Suppress("DEPRECATION")
                val screenWakeLock = pm?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "orbis:fcm_call_screen_wake"
                )
                screenWakeLock?.acquire(20_000L)
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock error: ${e.message}")
            }

            // Déclarer l'appel au sous-système Android Telecom natif (Core-Telecom Self-Managed VoIP)
            try {
                com.sha.orbis.call.telecom.OrbisTelecomHelper.reportIncomingCall(
                    context = this,
                    callId = callId,
                    callerPhone = callerPhone,
                    callerName = callerName,
                    isVideo = isVideo
                )
            } catch (e: Exception) {
                Log.w(TAG, "Telecom reportIncomingCall error: ${e.message}")
            }

            // Démarrer le Foreground Service pour sécuriser le socket réseau
            OrbisCallForegroundService.start(
                context = this,
                peerName = callerName,
                isVideo = isVideo,
                isActiveMedia = false
            )

            // Notification d'appel entrant plein écran native
            com.sha.orbis.call.OrbisCallNotificationHelper.showIncomingCallNotification(
                context = this,
                callId = callId,
                callerPhone = callerPhone,
                callerName = callerName,
                isVideo = isVideo,
                isFullScreen = true
            )

            // Forcer la reconnexion instantanée des relais Nostr
            com.sha.orbis.nostr.service.NostrSyncManager.getInstance(this).reconnect(force = true)
        } else if (type == "message_wake") {
            val convId = remoteMessage.data["conversationId"] ?: ""
            val senderPhone = remoteMessage.data["senderPhone"] ?: ""
            val senderName = remoteMessage.data["senderName"] ?: senderPhone
            val textSnippet = remoteMessage.data["textSnippet"] ?: "Nouveau message"

            // Silencer ABSOLUMENT les paquets de synchronisation souveraine (Zéro notification, Zéro spam)
            if (com.sha.orbis.sync.protocol.SovereignSyncProtocol.isSyncPacket(textSnippet) ||
                textSnippet.startsWith("[ORBIS_PEER_SYNC") ||
                textSnippet.contains("[ORBIS_PEER_SYNC") ||
                textSnippet.contains("\"action\":\"EXCHANGE_") ||
                textSnippet.contains("\"action\":\"DELTA_") ||
                textSnippet.contains("\"action\":\"SOVEREIGN_") ||
                textSnippet.contains("\"bundle\":{")
            ) {
                Log.d(TAG, "Message FCM de synchronisation silencieuse détecté : aucune notification affichée.")
                com.sha.orbis.nostr.service.NostrSyncManager.getInstance(this).reconnect(force = true)
                return
            }

            // Garde-fou sécurité : ignorer silencieusement si l'expéditeur est bloqué
            if (com.sha.orbis.storage.BlockedContactsRepository(this).isBlocked(senderPhone)) {
                Log.d(TAG, "Message FCM entrant ignoré : expéditeur bloqué ($senderPhone)")
                return
            }

            // Anti-doublon strict : si l'utilisateur est déjà dans la discussion, ne pas émettre de notification
            if (com.sha.orbis.notification.ActiveConversationTracker.isConversationActive(convId, senderPhone)) {
                Log.d(TAG, "Message $convId ignoré : discussion active à l'écran.")
                return
            }

            // Réveil physique temporaire du processeur
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "orbis:fcm_msg_wake")
                wakeLock?.acquire(5_000L)
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock error: ${e.message}")
            }

            // Notification système native Android (Standard WhatsApp)
            com.sha.orbis.sms.SmsNotificationHelper.showIncomingMessageNotification(
                context = this,
                sender = senderName,
                message = textSnippet,
                convId = convId,
                senderPhone = senderPhone
            )

            // Reconnexion immédiate du maillage Nostr en tâche de fond
            com.sha.orbis.nostr.service.NostrSyncManager.getInstance(this).reconnect(force = true)
        }
    }
}
