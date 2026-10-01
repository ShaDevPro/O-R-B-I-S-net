package com.sha.orbis.sync.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.CallDirection
import com.sha.orbis.model.FriendRequestStatus
import com.sha.orbis.nostr.identity.NostrIdentityManager
import com.sha.orbis.nostr.service.NostrSyncManager
import com.sha.orbis.notification.OrbisEventBus
import com.sha.orbis.social.PresenceHelper
import com.sha.orbis.storage.CallLogRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.SocialRepository
import com.sha.orbis.sync.model.SovereignSyncBundle
import com.sha.orbis.sync.model.SovereignSyncPacket
import com.sha.orbis.sync.model.SyncAction
import com.sha.orbis.sync.model.SyncPresenceItem
import com.sha.orbis.sync.protocol.SovereignSyncProtocol
import com.sha.orbis.sync.security.SovereignSyncSecurityPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Moteur central de synchronisation P2P souveraine entre amis OrbisNet.
 * Exécute l'échange bilatéral hors du thread UI (Dispatchers.IO) :
 * - Fil d'actualité (Publications, Commentaires, Réactions, Sondages)
 * - Stories éphémères (24h)
 * - Messagerie directe partagée (réconciliation des messages manqués)
 * - Journal d'appels partagé (réconciliation des appels audio/vidéo)
 * - Statut en ligne et présence
 */
class SovereignPeerSyncEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "SovereignPeerSyncEngine"

        @Volatile
        private var INSTANCE: SovereignPeerSyncEngine? = null

        fun getInstance(context: Context): SovereignPeerSyncEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SovereignPeerSyncEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isSyncingAll = AtomicBoolean(false)
    private val lastPeerSyncTimestamps = ConcurrentHashMap<String, Long>()

    // Limiteur de fréquence : max 1 échange par pair toutes les 20 secondes
    private val PEER_SYNC_COOLDOWN_MS = 20_000L

    /**
     * Lance la synchronisation ciblée avec un ami spécifique.
     */
    fun syncWithPeer(
        peerPhone: String,
        peerPubkey: String? = null,
        isResponse: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        scope.launch {
            try {
                executeSyncWithPeer(peerPhone, peerPubkey, isResponse)
            } catch (e: Exception) {
                Log.w(TAG, "Erreur syncWithPeer pour $peerPhone: ${e.message}")
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private suspend fun executeSyncWithPeer(
        peerPhone: String,
        peerPubkey: String?,
        isResponse: Boolean
    ) {
        val cleanPhone = peerPhone.trim()
        if (cleanPhone.isBlank() && peerPubkey.isNullOrBlank()) return

        // 1. Vérification stricte des permissions et règles de souveraineté
        if (!SovereignSyncSecurityPolicy.canSyncWithPeer(appContext, cleanPhone, peerPubkey)) {
            Log.d(TAG, "Synchronisation refusée par la politique de sécurité pour $cleanPhone")
            return
        }

        // 2. Limiteur de fréquence par pair
        val now = System.currentTimeMillis()
        val cacheKey = cleanPhone.ifBlank { peerPubkey ?: "" }
        val lastSync = lastPeerSyncTimestamps[cacheKey] ?: 0L
        if (!isResponse && (now - lastSync) < PEER_SYNC_COOLDOWN_MS) {
            Log.d(TAG, "Synchronisation ignorée (cooldown actif pour $cleanPhone)")
            return
        }
        lastPeerSyncTimestamps[cacheKey] = now

        // 3. Résolution de la clé Nostr du destinataire
        val friendRepo = FriendRequestRepository(appContext)
        val convRepo = ConversationRepository(appContext)
        val targetPubkey = peerPubkey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
            ?: friendRepo.resolveNostrPubkeyForPhone(cleanPhone)
            ?: run {
                convRepo.loadContacts().find { FriendRequestRepository.isSamePhone(it.phone, cleanPhone) }?.publicKey
            }

        if (targetPubkey.isNullOrBlank()) {
            Log.w(TAG, "Impossible de synchroniser avec $cleanPhone: clé publique Nostr introuvable")
            return
        }

        val sessionManager = SessionManager(appContext)
        val myPhone = sessionManager.userPhone.trim()
        val myName = sessionManager.userName.trim().ifBlank { "Orbis" }
        val identityManager = NostrIdentityManager.getInstance(appContext)
        val myPubkey = identityManager.publicKeyHex

        // 4. Extraction et filtrage hermétique des publications
        val socialRepo = SocialRepository(appContext)
        val allPosts = socialRepo.loadPosts()
        val filteredPosts = SovereignSyncSecurityPolicy.filterPostsForPeer(
            context = appContext,
            posts = allPosts,
            peerPhone = cleanPhone,
            myPhone = myPhone
        ).take(100) // 100 posts récents max pour garantir la vélocité réseau

        // 5. Extraction et filtrage hermétique des stories actives (24h)
        val allStories = socialRepo.loadStories()
        val filteredStories = SovereignSyncSecurityPolicy.filterStoriesForPeer(
            context = appContext,
            stories = allStories,
            peerPhone = cleanPhone,
            myPhone = myPhone
        )

        // 6. Extraction et filtrage hermétique de la messagerie directe bilatérale
        val allConvs = convRepo.loadConversations()
        val matchedConv = allConvs.find { conv ->
            conv.participants.any { p -> p != "me" && FriendRequestRepository.isSamePhone(p, cleanPhone) } ||
                FriendRequestRepository.isSamePhone(conv.id.removePrefix("conv_"), cleanPhone)
        }
        val mutualMessages = if (matchedConv != null) {
            val msgs = convRepo.loadMessages(matchedConv.id)
            SovereignSyncSecurityPolicy.filterMessagesForPeer(
                messages = msgs,
                peerPhone = cleanPhone,
                myPhone = myPhone
            ).filterNot { com.sha.orbis.storage.LocalMessageStore.isSpamOrSyncMessage(it) }.takeLast(100)
        } else {
            emptyList()
        }

        // 7. Extraction et filtrage hermétique du journal d'appels bilatéral
        val callRepo = CallLogRepository(appContext)
        val allCalls = callRepo.loadCalls()
        val mutualCalls = SovereignSyncSecurityPolicy.filterCallsForPeer(
            calls = allCalls,
            peerPhone = cleanPhone
        ).take(50)

        // 8. Cercles et Groupes partagés (Restauration automatique du réseau)
        val circleMembership = SovereignSyncSecurityPolicy.getMutualCircleMemberships(appContext, cleanPhone)
        val mutualGroups = SovereignSyncSecurityPolicy.filterGroupConversationsForPeer(allConvs, cleanPhone)
        val mutualGroupMessages = mutualGroups.flatMap { convRepo.loadMessages(it.id) }
            .filterNot { com.sha.orbis.storage.LocalMessageStore.isSpamOrSyncMessage(it) }
            .takeLast(100)

        // 9. Informations de présence en ligne
        val presenceItem = if (!sessionManager.isPresenceHidden) {
            SyncPresenceItem(
                phone = myPhone,
                pubkey = myPubkey,
                isOnline = true,
                timestamp = now,
                displayName = myName
            )
        } else null

        // 10. Construction du paquet chiffré
        val bundle = SovereignSyncBundle(
            posts = filteredPosts,
            stories = filteredStories,
            messages = mutualMessages,
            calls = mutualCalls,
            presence = presenceItem,
            circleMembership = circleMembership,
            groupConversations = mutualGroups,
            groupMessages = mutualGroupMessages
        )

        val action = if (isResponse) SyncAction.EXCHANGE_RESPONSE else SyncAction.EXCHANGE_REQUEST
        val packet = SovereignSyncPacket(
            version = 1,
            action = action,
            senderPhone = myPhone,
            senderPubkey = myPubkey,
            senderName = myName,
            targetPeerPhone = cleanPhone,
            timestamp = now,
            bundle = bundle
        )

        val packedPayload = SovereignSyncProtocol.pack(packet)

        // 10. Expédition E2EE via le pool de relais Nostr (Kind 4 AES-GCM)
        val convId = matchedConv?.id ?: "conv_${cleanPhone.filter { it.isDigit() }.takeLast(8)}"
        NostrSyncManager.getInstance(appContext).sendDirectMessage(
            recipientNpubOrHex = targetPubkey,
            conversationId = convId,
            text = packedPayload,
            senderName = myName,
            senderPhone = myPhone
        )

        Log.i(TAG, "Paquet de synchronisation envoyé avec succès vers $cleanPhone (${action.name}, posts: ${filteredPosts.size}, stories: ${filteredStories.size}, msgs: ${mutualMessages.size}, calls: ${mutualCalls.size})")
    }

    /**
     * Traite et intègre de façon atomique un paquet de synchronisation reçu d'un pair.
     */
    fun integrateIncomingSyncPacket(
        rawPayload: String,
        senderPubkey: String,
        senderPhoneCandidate: String?
    ) {
        scope.launch {
            try {
                executeIntegrateIncomingSyncPacket(rawPayload, senderPubkey, senderPhoneCandidate)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur intégration paquet de synchronisation: ${e.message}", e)
            }
        }
    }

    private suspend fun executeIntegrateIncomingSyncPacket(
        rawPayload: String,
        senderPubkey: String,
        senderPhoneCandidate: String?
    ) {
        val packet = SovereignSyncProtocol.unpack(rawPayload) ?: return
        val senderPhone = packet.senderPhone.trim().ifBlank { senderPhoneCandidate?.trim() ?: "" }

        // 1. Contrôle de sécurité inviolable
        if (!SovereignSyncSecurityPolicy.canSyncWithPeer(appContext, senderPhone, senderPubkey)) {
            Log.w(TAG, "Paquet rejeté : l'expéditeur $senderPhone n'est pas autorisé")
            return
        }

        val friendRepo = FriendRequestRepository(appContext)
        val convRepo = ConversationRepository(appContext)
        val socialRepo = SocialRepository(appContext)
        val callRepo = CallLogRepository(appContext)

        // 2. Assurer que le contact et l'ami sont bien enregistrés
        val effectiveName = packet.senderName.takeIf { it.isNotBlank() && !it.startsWith("+") } ?: "Ami Orbis"
        friendRepo.ensureAcceptedFriend(
            phone = senderPhone,
            name = effectiveName,
            publicKey = senderPubkey
        )

        // 3. Intégration souveraine des Publications
        var newPostsCount = 0
        val bundle = packet.bundle
        for (post in bundle.posts) {
            if (socialRepo.isPostDeleted(post.id)) continue
            socialRepo.addPost(post)
            newPostsCount++
        }

        // 4. Intégration souveraine des Stories actives
        var newStoriesCount = 0
        for (story in bundle.stories) {
            if (!story.isExpired) {
                socialRepo.addStory(story)
                newStoriesCount++
            }
        }

        // 5. Intégration de la Messagerie partagée (réconciliation bilatérale)
        var newMessagesCount = 0
        if (bundle.messages.isNotEmpty()) {
            val conv = convRepo.ensureConversationForContact(
                phone = senderPhone,
                name = effectiveName,
                publicKey = senderPubkey
            )
            val existingMsgs = convRepo.loadMessages(conv.id)
            val existingIds = existingMsgs.map { it.id }.toSet()

            for (msg in bundle.messages) {
                if (com.sha.orbis.storage.LocalMessageStore.isSpamOrSyncMessage(msg)) continue
                if (!existingIds.contains(msg.id) && !msg.isDeletedForEveryone) {
                    convRepo.addMessage(conv.id, msg.copy(conversationId = conv.id))
                    newMessagesCount++
                }
            }
        }

        // 6. Intégration du Journal d'appels partagé (réconciliation bilatérale)
        var newCallsCount = 0
        if (bundle.calls.isNotEmpty()) {
            val existingCalls = callRepo.loadCalls()
            for (call in bundle.calls) {
                val exists = existingCalls.any { existing ->
                    existing.id == call.id ||
                        (Math.abs(existing.timestamp - call.timestamp) < 3000L &&
                            FriendRequestRepository.isSamePhone(existing.peerPhone, senderPhone))
                }
                if (!exists) {
                    // Inverser la direction pour le récepteur
                    val invertedDirection = when (call.direction) {
                        CallDirection.OUTGOING -> if (call.durationSeconds > 0) CallDirection.INCOMING else CallDirection.MISSED
                        CallDirection.INCOMING, CallDirection.MISSED -> CallDirection.OUTGOING
                    }
                    val reconciledCall = call.copy(
                        peerPhone = senderPhone,
                        peerName = effectiveName,
                        direction = invertedDirection
                    )
                    callRepo.addCall(reconciledCall)
                    newCallsCount++
                }
            }
        }

        // 7. Restauration souveraine des Cercles & Groupes partagés
        val circleRepo = com.sha.orbis.storage.FriendCircleRepository(appContext)
        if (bundle.circleMembership.contains("circle_family")) {
            circleRepo.addFamilyMember(senderPhone)
        }
        for (circleId in bundle.circleMembership) {
            if (circleId != "circle_family") {
                circleRepo.addMemberToCircle(circleId, senderPhone)
            }
        }

        val myUserPhone = SessionManager(appContext).userPhone.trim()
        var newGroupsCount = 0
        for (groupConv in bundle.groupConversations) {
            if (groupConv.isGroup && groupConv.participants.any { FriendRequestRepository.isSamePhone(it, myUserPhone) }) {
                convRepo.addConversation(groupConv)
                newGroupsCount++
            }
        }
        for (groupMsg in bundle.groupMessages) {
            if (com.sha.orbis.storage.LocalMessageStore.isSpamOrSyncMessage(groupMsg)) continue
            if (!groupMsg.isDeletedForEveryone) {
                convRepo.addMessage(groupMsg.conversationId, groupMsg)
            }
        }

        // 8. Enregistrement de la présence en direct
        if (senderPhone.isNotBlank()) {
            PresenceHelper.recordPeerActivity(senderPhone)
        }
        if (senderPubkey.isNotBlank()) {
            PresenceHelper.recordPeerActivity(senderPubkey)
        }

        Log.i(TAG, "Intégration réussie depuis $senderPhone (${packet.action.name}) : +$newPostsCount posts, +$newStoriesCount stories, +$newMessagesCount msgs, +$newCallsCount appels, +$newGroupsCount groupes")

        // 8. Rafraîchissement réactif de l'interface utilisateur
        if (newPostsCount > 0) {
            val intent = Intent(OrbisEventBus.ACTION_ORBIS_POST_RECEIVED).apply {
                setPackage(appContext.packageName)
            }
            appContext.sendBroadcast(intent)
        }
        if (newStoriesCount > 0) {
            val intent = Intent(OrbisEventBus.ACTION_ORBIS_STORY_RECEIVED).apply {
                setPackage(appContext.packageName)
            }
            appContext.sendBroadcast(intent)
        }
        if (newMessagesCount > 0 || newGroupsCount > 0) {
            val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                setPackage(appContext.packageName)
            }
            appContext.sendBroadcast(intent)
        }
        if (newCallsCount > 0) {
            val intent = Intent(OrbisEventBus.ACTION_REFRESH_CALL_LOGS).apply {
                setPackage(appContext.packageName)
            }
            appContext.sendBroadcast(intent)
        }

        // 9. Échange réciproque automatique si c'était une requête initiale
        if (packet.action == SyncAction.EXCHANGE_REQUEST) {
            Log.d(TAG, "Réponse automatique d'échange réciproque vers $senderPhone...")
            delay(300L) // Petite temporisation pour fluidité
            executeSyncWithPeer(senderPhone, senderPubkey, isResponse = true)
        }
    }

    /**
     * Synchronise avec l'ensemble des amis acceptés (déclenché à chaque ouverture de l'app ou périodiquement).
     */
    fun syncWithAllFriends(reason: String, onFinished: (() -> Unit)? = null) {
        if (!isSyncingAll.compareAndSet(false, true)) {
            Log.d(TAG, "syncWithAllFriends déjà en cours d'exécution, requête ignorée ($reason)")
            onFinished?.invoke()
            return
        }

        scope.launch {
            try {
                Log.i(TAG, "Démarrage de l'auto-synchronisation globale avec tous les amis (motif: $reason)...")
                val friendRepo = FriendRequestRepository(appContext)
                val convRepo = ConversationRepository(appContext)
                val acceptedRequests = friendRepo.loadRequests().filter { it.status == FriendRequestStatus.ACCEPTED }

                val seenPhones = mutableSetOf<String>()
                val peersToSync = mutableListOf<Pair<String, String?>>()

                acceptedRequests.forEach { req ->
                    val phone = req.senderPhone.trim()
                    if (phone.isNotBlank() && seenPhones.none { FriendRequestRepository.isSamePhone(it, phone) }) {
                        seenPhones.add(phone)
                        peersToSync.add(phone to req.senderPublicKey.takeIf { it.isNotBlank() })
                    }
                }

                try {
                    convRepo.loadContacts().forEach { c ->
                        val phone = c.phone.trim()
                        if (phone.isNotBlank() && friendRepo.isFriend(phone) && seenPhones.none { FriendRequestRepository.isSamePhone(it, phone) }) {
                            seenPhones.add(phone)
                            peersToSync.add(phone to c.publicKey.takeIf { it.isNotBlank() })
                        }
                    }
                } catch (_: Exception) {}

                Log.i(TAG, "Nombre d'amis à synchroniser : ${peersToSync.size}")

                for ((phone, pubkey) in peersToSync) {
                    try {
                        executeSyncWithPeer(phone, pubkey, isResponse = false)
                    } catch (e: Exception) {
                        Log.w(TAG, "Erreur synchronisation avec ami $phone: ${e.message}")
                    }
                    // Échelonnage de 150ms pour préserver totalement la bande passante et la fluidité 120Hz
                    delay(150L)
                }
                Log.i(TAG, "Auto-synchronisation globale terminée avec succès")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur dans syncWithAllFriends: ${e.message}", e)
            } finally {
                isSyncingAll.set(false)
                onFinished?.invoke()
            }
        }
    }
}
