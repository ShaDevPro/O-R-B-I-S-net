package com.sha.orbis.sync.scheduler

import android.content.Context
import android.util.Log
import com.sha.orbis.sync.engine.SovereignPeerSyncEngine
import com.sha.orbis.sync.worker.SovereignPeerSyncWorker
import java.util.concurrent.ConcurrentHashMap

/**
 * Planificateur et coordinateur des déclenchements de synchronisation souveraine.
 * Évite les tempêtes de synchronisation grâce à un anti-rebond (debouncing) précis.
 */
object SovereignSyncScheduler {

    private const val TAG = "SovereignSyncScheduler"

    // Cooldown global pour onAppOpened : au plus une fois toutes les 45 secondes
    private const val APP_OPENED_COOLDOWN_MS = 45_000L
    @Volatile
    private var lastAppOpenedSyncTimestamp = 0L

    // Cooldown par ami pour l'ouverture d'une conversation
    private val conversationOpenedTimestamps = ConcurrentHashMap<String, Long>()
    private const val CONVERSATION_OPENED_COOLDOWN_MS = 30_000L

    /**
     * Déclenché automatiquement à chaque ouverture ou reprise de l'application OrbisNet.
     */
    fun onAppOpened(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastAppOpenedSyncTimestamp) < APP_OPENED_COOLDOWN_MS) {
            Log.d(TAG, "onAppOpened ignoré (cooldown actif)")
            return
        }
        lastAppOpenedSyncTimestamp = now
        Log.i(TAG, "onAppOpened déclenché : démarrage de l'auto-synchronisation souveraine...")
        try {
            val friendRepo = com.sha.orbis.storage.FriendRequestRepository(context)
            val hasFriends = friendRepo.loadRequests().any { it.status == com.sha.orbis.model.FriendRequestStatus.ACCEPTED }
            if (!hasFriends) {
                // Utilisateur fraîchement réinstallé sans backup : diffuser le signal de reconnexion pour réveiller les anciens amis
                com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).publishReconnectionSignal()
            }
        } catch (_: Exception) {}
        SovereignPeerSyncEngine.getInstance(context).syncWithAllFriends("app_opened")
    }

    /**
     * Déclenché immédiatement lorsqu'une invitation d'ami est acceptée ou un ami ajouté.
     */
    fun onFriendAddedOrAccepted(context: Context, peerPhone: String, peerPubkey: String? = null) {
        if (peerPhone.isBlank() && peerPubkey.isNullOrBlank()) return
        Log.i(TAG, "onFriendAddedOrAccepted déclenché pour $peerPhone")
        SovereignPeerSyncEngine.getInstance(context).syncWithPeer(
            peerPhone = peerPhone,
            peerPubkey = peerPubkey,
            isResponse = false
        )
    }

    /**
     * Déclenché lorsqu'une discussion avec un ami est ouverte par l'utilisateur.
     */
    fun onConversationOpened(context: Context, peerPhone: String) {
        val cleanPhone = peerPhone.trim()
        if (cleanPhone.isBlank()) return

        val now = System.currentTimeMillis()
        val lastOpened = conversationOpenedTimestamps[cleanPhone] ?: 0L
        if ((now - lastOpened) < CONVERSATION_OPENED_COOLDOWN_MS) {
            return
        }
        conversationOpenedTimestamps[cleanPhone] = now
        Log.d(TAG, "onConversationOpened déclenché pour $cleanPhone")
        SovereignPeerSyncEngine.getInstance(context).syncWithPeer(
            peerPhone = cleanPhone,
            isResponse = false
        )
    }

    /**
     * Planifie la tâche périodique WorkManager en arrière-plan.
     */
    fun schedulePeriodicSync(context: Context) {
        SovereignPeerSyncWorker.schedulePeriodic(context)
    }
}
