package com.sha.orbis.nostr.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sha.orbis.R
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.FriendRequest
import com.sha.orbis.model.FriendRequestStatus
import com.sha.orbis.model.Message
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.model.RequestDirection
import com.sha.orbis.nostr.client.RelayPoolManager
import com.sha.orbis.nostr.crypto.Bech32
import com.sha.orbis.nostr.identity.NostrIdentityManager
import com.sha.orbis.nostr.model.NostrEvent
import com.sha.orbis.nostr.model.NostrFilter
import com.sha.orbis.nostr.protocol.NostrProtocolEngine
import com.sha.orbis.notification.OrbisEventBus
import com.sha.orbis.sms.SmsNotificationHelper
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialStory
import com.sha.orbis.social.StoryAudiencePolicy
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.ProcessedEventsRepository
import com.sha.orbis.storage.SocialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Gestionnaire d'orchestration et de synchronisation Nostr pour OrbisNet.
 * Écoute en continu le pool de relais, déchiffre les messages entrants,
 * persiste les données en base locale et notifie l'utilisateur.
 */
class NostrSyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NostrSyncManager"
        private const val SUB_ID_DMS = "sub_orbis_dms"
        private const val SUB_ID_TIMELINE = "sub_orbis_timeline"
        private const val SUB_ID_CALLS = "sub_orbis_calls"
        private const val SUB_ID_INVITES = "sub_orbis_invites"
        private const val SUB_ID_STORIES = "sub_orbis_stories"
        private const val SUB_ID_RECEIPTS = "sub_orbis_receipts"
        private const val SUB_ID_DELETIONS = "sub_orbis_deletions"
        private const val SUB_ID_PROFILES = "sub_orbis_profiles"

        // SharedPreferences — persistance du timestamp de la dernière synchro Nostr
        // Permet de ne récupérer que les événements manqués depuis la dernière connexion.
        private const val PREFS_NOSTR_SYNC = "orbis_nostr_sync_state"
        private const val KEY_LAST_SYNC_TS = "last_sync_timestamp_ms"

        @Volatile
        private var INSTANCE: NostrSyncManager? = null

        fun getInstance(context: Context): NostrSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NostrSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relayPool = RelayPoolManager.getInstance(context)
    private val identityManager get() = NostrIdentityManager.getInstance(context)
    private val sessionManager = SessionManager(context)
    private val currentAccountId: String get() = sessionManager.activeAccountId
    private val conversationRepo get() = ConversationRepository(context, currentAccountId)
    private val socialRepo get() = SocialRepository(context, currentAccountId)
    private val friendRequestRepo get() = FriendRequestRepository(context, currentAccountId)
    private val blockedRepo get() = BlockedContactsRepository(context, currentAccountId)
    private val processedEventsRepo = ProcessedEventsRepository.getInstance(context)
    private val notifRepo get() = com.sha.orbis.storage.NotificationRepository(context, currentAccountId)


    private var isStarted = false

    /**
     * Collecte les clés publiques Nostr en hexadécimal brut (64 caractères minuscules)
     * de tous les amis acceptés + soi-même.
     * Décode automatiquement les clés au format "npub1..." pour respecter strictement NIP-01.
     * Retourne null si aucun ami (pas de restriction authors -> filtre par tag seulement).
     */
    private fun getFriendPubkeys(): List<String>? {
        val myPubkey = identityManager.publicKeyHex.lowercase().trim()
        val friendPubkeys = mutableListOf<String>()
        if (myPubkey.length == 64) friendPubkeys.add(myPubkey)
        try {
            friendRequestRepo.loadRequests()
                .filter { it.status == FriendRequestStatus.ACCEPTED }
                .forEach { req ->
                    val hexKey = FriendRequestRepository.resolveNostrPubkeyHex(req.senderPublicKey)
                    if (!hexKey.isNullOrBlank() && hexKey.length == 64 && !friendPubkeys.contains(hexKey)) {
                        friendPubkeys.add(hexKey)
                    }
                }
            conversationRepo.loadContacts()
                .forEach { c ->
                    val hexKey = FriendRequestRepository.resolveNostrPubkeyHex(c.publicKey)
                    if (!hexKey.isNullOrBlank() && hexKey.length == 64 && !friendPubkeys.contains(hexKey)) {
                        friendPubkeys.add(hexKey)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "getFriendPubkeys: erreur lecture amis: ${e.message}")
        }
        Log.d(TAG, "getFriendPubkeys: ${friendPubkeys.size} clés hex résolues pour les filtres Nostr (self inclus)")
        // Si aucun ami (seulement self ou vide), on retourne null pour ne pas bloquer les posts/stories publics OrbisNet
        return if (friendPubkeys.size <= 1) null else friendPubkeys
    }

    /**
     * Met à jour dynamiquement les abonnements du fil d'actualité et du réseau social
     * (Timeline, Stories, Suppressions, Profils) auprès des relais Nostr connectés.
     * Utilise une fenêtre temporelle de 72 heures pour le fil d'actualité,
     * garantissant qu'aucun post publié pendant la veille, l'extinction ou une coupure 4G
     * ne soit manqué lors de la reconnexion.
     */
    fun refreshSubscriptions(forceNetworkQuery: Boolean = false) {
        if (!isStarted) {
            start()
            return
        }
        val myPubkey = identityManager.publicKeyHex.lowercase().trim()
        val friendPubkeys = getFriendPubkeys()

        // Fenêtre temporelle pour le fil : 72 heures en arrière (3 jours)
        // Les relais Nostr limitent à 150 événements récents, et notre cache LRU déduplique instantanément.
        val timelineSince = (System.currentTimeMillis() - 3L * 24 * 3600 * 1000L) / 1000L

        Log.i(TAG, "Mise à jour dynamique des abonnements Nostr social/feed (${friendPubkeys?.size ?: 0} amis inclus)...")

        val timelineFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_TEXT_NOTE, NostrEvent.KIND_REACTION),
            authors = friendPubkeys,
            tTags = listOf(NostrProtocolEngine.TAG_ORBISNET),
            since = timelineSince,
            limit = 150
        )
        val mentionsFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_TEXT_NOTE, NostrEvent.KIND_REACTION),
            pTags = listOf(myPubkey),
            since = timelineSince,
            limit = 150
        )
        relayPool.subscribe(SUB_ID_TIMELINE, listOf(timelineFilter, mentionsFilter))

        // Stories éphémères (24h) — restreintes aux amis
        val storiesSince = (System.currentTimeMillis() - 86_400_000L) / 1000L
        val storiesFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_TEXT_NOTE),
            authors = friendPubkeys,
            tTags = listOf(NostrProtocolEngine.TAG_ORBISNET_STORY),
            since = storiesSince,
            limit = 50
        )
        relayPool.subscribe(SUB_ID_STORIES, listOf(storiesFilter))

        // Suppressions de posts/comments/stories (Kind 5 NIP-09) — des amis uniquement
        val deletionsFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_DELETION),
            authors = friendPubkeys,
            tTags = listOf(NostrProtocolEngine.TAG_ORBISNET),
            since = timelineSince
        )
        relayPool.subscribe(SUB_ID_DELETIONS, listOf(deletionsFilter))

        // Profils souverains (Kind 0 NIP-01) — métadonnées des amis (pseudo, bio, avatar)
        val profileFilters = mutableListOf<NostrFilter>()
        if (!friendPubkeys.isNullOrEmpty()) {
            profileFilters.add(
                NostrFilter(
                    kinds = listOf(NostrEvent.KIND_METADATA),
                    authors = friendPubkeys
                )
            )
        }
        if (myPubkey.isNotBlank()) {
            profileFilters.add(
                NostrFilter(
                    kinds = listOf(NostrEvent.KIND_METADATA),
                    pTags = listOf(myPubkey)
                )
            )
        }
        if (profileFilters.isNotEmpty()) {
            relayPool.subscribe(SUB_ID_PROFILES, profileFilters)
        }
    }

    /**
     * Force une interrogation immédiate et ciblée du fil d'actualité auprès des relais Nostr.
     * Utilisé lors de l'ouverture de l'écran Timeline ou d'un rafraîchissement manuel.
     */
    fun fetchFreshTimeline() {
        refreshSubscriptions(forceNetworkQuery = true)
    }

    fun start() {
        if (isStarted) return
        isStarted = true
        Log.d(TAG, "Démarrage de la synchronisation Nostr...")

        // 1. Démarrer le pool de relais
        relayPool.start()

        // Publier notre profil actuel en tâche de fond pour que les relais soient toujours à jour
        scope.launch {
            try {
                if (sessionManager.userName.isNotBlank()) {
                    publishProfileUpdate()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur publication profil au démarrage: ${e.message}")
            }
        }

        // 2. Établir les abonnements
        val myPubkey = identityManager.publicKeyHex

        // Timestamp intelligent — on utilise la dernière synchro persistée pour ne refetch que
        // ce qu'on a manqué. Nostr = transport uniquement, le stockage local est illimité.
        val syncPrefs = context.getSharedPreferences(PREFS_NOSTR_SYNC, android.content.Context.MODE_PRIVATE)
        val lastSync = syncPrefs.getLong(KEY_LAST_SYNC_TS, 0L)
        val sinceTimestamp = if (lastSync > 0L) {
            // Overlap de 2 heures pour éviter de rater des événements lors de coupures ou veilles
            maxOf(0L, (lastSync - 2 * 3600 * 1000L) / 1000L)
        } else {
            // Premier install → 90 jours pour la synchro initiale du fil
            (System.currentTimeMillis() - 90L * 24 * 3600 * 1000L) / 1000L
        }
        syncPrefs.edit().putLong(KEY_LAST_SYNC_TS, System.currentTimeMillis()).apply()

        // A. Messages Directs E2EE
        val dmFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_ENCRYPTED_DIRECT_MESSAGE),
            pTags = listOf(myPubkey),
            since = sinceTimestamp
        )
        relayPool.subscribe(SUB_ID_DMS, listOf(dmFilter))

        // B. Signalisation d'appels vocaux éphémères
        val callFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_EPHEMERAL_CALL_SIGNAL),
            pTags = listOf(myPubkey),
            since = (System.currentTimeMillis() - 20_000L) / 1000L
        )
        relayPool.subscribe(SUB_ID_CALLS, listOf(callFilter))

        // B.bis Accusés de réception éphémères (WhatsApp Delivery Receipts ✓✓)
        val receiptFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_EPHEMERAL_RECEIPT),
            pTags = listOf(myPubkey),
            since = sinceTimestamp
        )
        relayPool.subscribe(SUB_ID_RECEIPTS, listOf(receiptFilter))

        // D. Invitations d'amis et Handshake ACK souverain
        val myPhoneDigits = sessionManager.userPhone.filter { it.isDigit() }
        val myLast8 = myPhoneDigits.takeLast(8)
        val inviteTags = mutableListOf("orbisnet-invite", "orbisnet-invite-ack")
        if (myPhoneDigits.isNotBlank()) {
            inviteTags.add("invite_$myPhoneDigits")
            inviteTags.add("ack_$myPhoneDigits")
        }
        if (myLast8 != myPhoneDigits && myLast8.length >= 8) {
            inviteTags.add("invite_$myLast8")
            inviteTags.add("ack_$myLast8")
        }

        val invitesFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_TEXT_NOTE),
            tTags = inviteTags,
            since = sinceTimestamp
        )
        val ackFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_TEXT_NOTE),
            pTags = listOf(myPubkey),
            since = sinceTimestamp
        )
        relayPool.subscribe(SUB_ID_INVITES, listOf(invitesFilter, ackFilter))

        // C, E, F, G. Fil d'actualité, Stories, Suppressions et Profils
        refreshSubscriptions()

        // 3. Observer les événements entrants
        scope.launch {
            relayPool.incomingEvents.collect { event ->
                handleIncomingEvent(event)
            }
        }
    }

    /**
     * Réveille les connexions WebSocket du pool et rafraîchit les souscriptions.
     * Appelé lors du retour de la connectivité réseau, allumage écran ou lors d'un cycle de veille.
     */
    fun reconnect(force: Boolean = false) {
        if (!isStarted) {
            start()
            return
        }
        Log.d(TAG, "Reconnexion et rafraîchissement des abonnements Nostr (force=$force)...")
        relayPool.reconnect(force)

        val myPubkey = identityManager.publicKeyHex

        val syncPrefsR = context.getSharedPreferences(PREFS_NOSTR_SYNC, android.content.Context.MODE_PRIVATE)
        val lastSyncR = syncPrefsR.getLong(KEY_LAST_SYNC_TS, 0L)
        val sinceTimestamp = if (lastSyncR > 0L) {
            maxOf(0L, (lastSyncR - 2 * 3600 * 1000L) / 1000L)
        } else {
            (System.currentTimeMillis() - 90L * 24 * 3600 * 1000L) / 1000L
        }
        syncPrefsR.edit().putLong(KEY_LAST_SYNC_TS, System.currentTimeMillis()).apply()

        val dmFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_ENCRYPTED_DIRECT_MESSAGE),
            pTags = listOf(myPubkey),
            since = sinceTimestamp
        )
        relayPool.subscribe(SUB_ID_DMS, listOf(dmFilter))

        val callFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_EPHEMERAL_CALL_SIGNAL),
            pTags = listOf(myPubkey),
            since = (System.currentTimeMillis() - 20_000L) / 1000L
        )
        relayPool.subscribe(SUB_ID_CALLS, listOf(callFilter))

        val receiptFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_EPHEMERAL_RECEIPT),
            pTags = listOf(myPubkey),
            since = sinceTimestamp
        )
        relayPool.subscribe(SUB_ID_RECEIPTS, listOf(receiptFilter))

        val myPhoneDigits = sessionManager.userPhone.filter { it.isDigit() }
        val myLast8 = myPhoneDigits.takeLast(8)
        val inviteTags = mutableListOf("orbisnet-invite", "orbisnet-invite-ack")
        if (myPhoneDigits.isNotBlank()) {
            inviteTags.add("invite_$myPhoneDigits")
            inviteTags.add("ack_$myPhoneDigits")
        }
        if (myLast8 != myPhoneDigits && myLast8.length >= 8) {
            inviteTags.add("invite_$myLast8")
            inviteTags.add("ack_$myLast8")
        }

        val invitesFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_TEXT_NOTE),
            tTags = inviteTags,
            since = sinceTimestamp
        )
        val ackFilter = NostrFilter(
            kinds = listOf(NostrEvent.KIND_TEXT_NOTE),
            pTags = listOf(myPubkey),
            since = sinceTimestamp
        )
        relayPool.subscribe(SUB_ID_INVITES, listOf(invitesFilter, ackFilter))

        // Fil d'actualité, Stories, Suppressions et Profils
        refreshSubscriptions()
    }

    private fun handleIncomingEvent(event: NostrEvent) {
        when (event.kind) {
            NostrEvent.KIND_METADATA -> handleProfileMetadata(event)
            NostrEvent.KIND_ENCRYPTED_DIRECT_MESSAGE -> handleDirectMessage(event)
            NostrEvent.KIND_EPHEMERAL_CALL_SIGNAL -> handleCallSignal(event)
            NostrEvent.KIND_EPHEMERAL_RECEIPT -> handleReceipt(event)
            NostrEvent.KIND_TEXT_NOTE -> {

                // 1. Intercepter en priorité les invitations souveraines
                val parsedInvite = NostrProtocolEngine.parseInvitationEvent(event)
                if (parsedInvite != null) {
                    handleInvitation(parsedInvite)
                    return
                }

                // 2. Intercepter les accusés de réception d'invitation (Handshake ACK)
                val parsedAck = NostrProtocolEngine.parseInvitationAckEvent(event)
                if (parsedAck != null) {
                    handleInvitationAck(parsedAck)
                    return
                }

                // 3. Intercepter les accusés de vue des stories
                if (isStoryViewEvent(event)) {
                    handleStoryView(event)
                    return
                }

                // 4. Intercepter les stories éphémères (24h)
                val parsedStory = NostrProtocolEngine.parseStoryEvent(event, context)
                if (parsedStory != null) {
                    handleStory(parsedStory)
                    return
                }

                // 5. Sinon traiter comme post social ou commentaire
                handleTimelinePost(event)
            }
            NostrEvent.KIND_REACTION -> handleReaction(event)
            NostrEvent.KIND_DELETION -> handleDeletion(event)
            else -> {}
        }
    }

    private fun handleDirectMessage(event: NostrEvent) {
        if (processedEventsRepo.isProcessed(event.id)) {
            return
        }
        processedEventsRepo.markProcessed(event.id)

        val parsed = NostrProtocolEngine.parseChatMessageEvent(event, identityManager) ?: return
        Log.d(TAG, "Nouveau message direct reçu de ${event.pubkey.take(8)}")

        val senderNpub = Bech32.npubEncode(parsed.senderPubkey)
        val contacts = conversationRepo.loadContacts().toMutableList()
        val friendRequests = friendRequestRepo.loadRequests()

        // Match contact by pubkey, npub, or phone
        val matchedContactIdx = contacts.indexOfFirst {
            it.publicKey.equals(parsed.senderPubkey, ignoreCase = true) ||
            it.publicKey.equals(senderNpub, ignoreCase = true) ||
            it.phone == senderNpub ||
            (!parsed.senderPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.phone, parsed.senderPhone))
        }
        val matchedRequest = friendRequests.find {
            it.senderPublicKey.equals(parsed.senderPubkey, ignoreCase = true) ||
            it.senderPublicKey.equals(senderNpub, ignoreCase = true) ||
            (!parsed.senderPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.senderPhone, parsed.senderPhone))
        }

        // Save incoming avatar to disk if provided
        val cleanPhoneCandidate = if (matchedContactIdx >= 0 && contacts[matchedContactIdx].phone.any { it.isDigit() }) {
            contacts[matchedContactIdx].phone
        } else (parsed.senderPhone?.takeIf { it.any { c -> c.isDigit() } } ?: matchedRequest?.senderPhone ?: senderNpub)

        // Vérifier si l'expéditeur est bloqué
        if (blockedRepo.isBlocked(senderNpub) ||
            blockedRepo.isBlocked(event.pubkey) ||
            (!parsed.senderPhone.isNullOrBlank() && blockedRepo.isBlocked(parsed.senderPhone)) ||
            (cleanPhoneCandidate.isNotBlank() && blockedRepo.isBlocked(cleanPhoneCandidate))) {
            Log.d(TAG, "Message direct ignoré : l'expéditeur est bloqué ($cleanPhoneCandidate / $senderNpub)")
            return
        }

        val cleanDigits = cleanPhoneCandidate.filter { it.isDigit() }
        val avatarId = cleanDigits.ifBlank { parsed.senderPubkey.take(8) }
        val savedAvatar = if (!parsed.senderAvatarBase64.isNullOrBlank()) {
            com.sha.orbis.ui.components.AvatarManager.saveAvatarFromBase64(context, parsed.senderAvatarBase64, "avatar_$avatarId")
        } else null

        val parsedName = parsed.senderName?.takeIf { it.isNotBlank() && !it.startsWith("+") && it != "O R B I S net" && it != "Ami OrbisNet" }
        val senderDisplayName = parsedName
            ?: (if (matchedContactIdx >= 0 && !contacts[matchedContactIdx].name.startsWith("+") && contacts[matchedContactIdx].name != "O R B I S net" && contacts[matchedContactIdx].name.isNotBlank()) {
                contacts[matchedContactIdx].name
            } else (matchedRequest?.senderName ?: "Ami OrbisNet"))

        // Update contact with latest avatar or pubkey
        if (matchedContactIdx >= 0) {
            val existing = contacts[matchedContactIdx]
            val updatedName = parsedName ?: (if (existing.name.isNotBlank() && !existing.name.startsWith("+") && existing.name != "O R B I S net") {
                existing.name
            } else senderDisplayName)

            val updated = existing.copy(
                publicKey = parsed.senderPubkey,
                phone = cleanPhoneCandidate,
                avatarPath = savedAvatar ?: existing.avatarPath,
                name = updatedName,
                status = "Connecté 🛡️"
            )
            contacts[matchedContactIdx] = updated
            conversationRepo.saveContacts(contacts)
        } else {
            contacts.add(0, Contact(
                id = "c_$avatarId",
                name = senderDisplayName,
                phone = cleanPhoneCandidate,
                publicKey = parsed.senderPubkey,
                status = "Connecté 🛡️",
                avatarPath = savedAvatar
            ))
            conversationRepo.saveContacts(contacts)
        }

        // Ensure accepted friend in repository
        friendRequestRepo.ensureAcceptedFriend(
            phone = cleanPhoneCandidate,
            name = senderDisplayName,
            publicKey = parsed.senderPubkey,
            avatarPath = savedAvatar
        )

        val isGroupConv = parsed.conversationId.startsWith("conv_group_")
        val convId = if (isGroupConv) {
            parsed.conversationId
        } else {
            if (cleanDigits.length >= 6) "conv_$cleanDigits" else "conv_${parsed.senderPubkey.take(16)}"
        }

        // Intercepter en priorité les paquets de synchronisation inter-amis souveraine (Herméticité absolue du chat)
        val isSyncPacket = com.sha.orbis.sync.protocol.SovereignSyncProtocol.isSyncPacket(parsed.text) ||
            parsed.text.trim().startsWith("[ORBIS_PEER_SYNC_V1]") ||
            parsed.text.contains("[ORBIS_PEER_SYNC_V1]") ||
            parsed.text.contains("\"action\":\"EXCHANGE_REQUEST\"") ||
            parsed.text.contains("\"action\":\"EXCHANGE_RESPONSE\"") ||
            parsed.text.contains("\"action\":\"DELTA_EXCHANGE\"") ||
            parsed.text.contains("\"action\":\"HEARTBEAT\"") ||
            (parsed.text.trim().startsWith("{") && parsed.text.contains("\"bundle\":{"))

        if (isSyncPacket) {
            Log.d(TAG, "Paquet de synchronisation inter-amis hermétique intercepté de ${parsed.senderPubkey.take(8)}")
            try {
                com.sha.orbis.sync.engine.SovereignPeerSyncEngine.getInstance(context).integrateIncomingSyncPacket(
                    rawPayload = parsed.text,
                    senderPubkey = parsed.senderPubkey,
                    senderPhoneCandidate = cleanPhoneCandidate
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erreur intégration paquet de synchronisation: ${e.message}", e)
            }
            return
        }

        // Handle revocation (delete for everyone) packet.
        // New packets carry several matching keys; old [REVOKE:id] packets remain supported.
        val revocationTarget = com.sha.orbis.model.MessageRevocation.parse(parsed.text)
        if (revocationTarget != null) {
            val matchedConvId = conversationRepo.deleteMessageForEveryone(convId, revocationTarget)
            val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                putExtra(OrbisEventBus.EXTRA_CONV_ID, matchedConvId ?: convId)
                revocationTarget.messageId?.let { putExtra(OrbisEventBus.EXTRA_MESSAGE_ID, it) }
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            return
        }

        // Handle reaction packet
        if (parsed.text.startsWith("[REACTION:") && parsed.text.endsWith("]")) {
            val payload = parsed.text.removeSurrounding("[REACTION:", "]")
            val parts = payload.split(":", limit = 2)
            if (parts.size == 2) {
                val targetMsgId = parts[0]
                val emoji = parts[1]
                val reactionSender = cleanPhoneCandidate.ifBlank { senderNpub }
                conversationRepo.addReactionToMessage(convId, targetMsgId, reactionSender, emoji)
                val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                    putExtra(OrbisEventBus.EXTRA_CONV_ID, convId)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
            }
            return
        }

        val expiresAt = if (parsed.ephemeralTimerMs > 0) parsed.timestamp + parsed.ephemeralTimerMs else null

        val message = Message(
            id = parsed.messageId,
            conversationId = convId,
            senderId = senderNpub,
            text = parsed.text,
            timestamp = parsed.timestamp,
            encrypted = true,
            status = MessageDeliveryStatus.DELIVERED,
            expiresAt = expiresAt,
            networkEventId = event.id
        )

        // Sauvegarder le message dans le repository local
        conversationRepo.addMessage(convId, message)

        // Pré-téléchargement silencieux en tâche de fond si le message contient une vidéo Blossom
        if (com.sha.orbis.media.VideoMediaHelper.isVideoPayload(parsed.text)) {
            val vidPayload = com.sha.orbis.media.VideoMediaHelper.parseVideoPayload(parsed.text)
            if (vidPayload?.url != null) {
                scope.launch {
                    try {
                        com.sha.orbis.nostr.media.BlossomMediaManager.downloadVideo(context, vidPayload.url, vidPayload.id)
                    } catch (_: Exception) {}
                }
            }
        }

        // Ensure conversation exists in list with proper metadata
        val existingConv = conversationRepo.loadConversations().find { it.id == convId }
        val isChatActive = com.sha.orbis.notification.ActiveConversationTracker.isConversationActive(convId, cleanPhoneCandidate)
        val unread = if (isChatActive) 0 else ((existingConv?.unreadCount ?: 0) + 1)
        val convTitle = if (isGroupConv) {
            existingConv?.title ?: "Groupe Orbis"
        } else {
            parsedName ?: (existingConv?.title ?: senderDisplayName)
        }
        val currentParticipants = ((existingConv?.participants ?: emptyList()) + listOf("me", cleanPhoneCandidate)).distinct()

        val updatedConv = Conversation(
            id = convId,
            title = convTitle,
            participants = currentParticipants,
            lastMessage = parsed.text,
            updatedAt = parsed.timestamp,
            unreadCount = unread,
            isGroup = isGroupConv
        )
        conversationRepo.addConversation(updatedConv)

        // Déclencher notification locale uniquement si l'utilisateur n'est PAS dans la discussion active
        if (!isChatActive) {
            SmsNotificationHelper.showIncomingMessageNotification(
                context = context,
                sender = senderDisplayName,
                message = parsed.text,
                convId = convId,
                senderPhone = cleanPhoneCandidate
            )

            // Enregistrer dans la Cloche & rafraîchir le Launcher
            val isPrivateConversation = com.sha.orbis.storage.PrivateConversationRepository(context).isLocked(convId)
            val notif = com.sha.orbis.model.AppNotification(
                id = "notif_msg_${parsed.messageId}",
                title = if (isPrivateConversation) context.getString(R.string.private_chat_notification_title) else senderDisplayName,
                description = if (isPrivateConversation) {
                    context.getString(R.string.private_chat_notification_body)
                } else {
                    parsed.text.take(80)
                },
                timestamp = parsed.timestamp,
                type = com.sha.orbis.model.NotificationType.MESSAGE,
                isRead = false,
                senderPhone = cleanPhoneCandidate,
                senderName = if (isPrivateConversation) null else senderDisplayName,
                senderAvatarPath = if (isPrivateConversation) null else ((existingConv?.participants?.firstOrNull()) ?: savedAvatar),
                targetConvId = convId,
                actionType = "MESSAGE"
            )
            notifRepo.addNotification(notif)
            com.sha.orbis.data.OrbisBadgeHub.refresh(context)
        }


        // Enregistrer la présence active du contact
        com.sha.orbis.social.PresenceHelper.recordPeerActivity(parsed.senderPubkey)
        if (cleanPhoneCandidate.isNotBlank()) {
            com.sha.orbis.social.PresenceHelper.recordPeerActivity(cleanPhoneCandidate)
        }

        // Envoyer immédiatement l'accusé de réception automatique (WhatsApp Delivery Receipt ✓✓)
        sendDeliveryReceipt(
            recipientPubKeyHex = parsed.senderPubkey,
            conversationId = convId,
            messageId = parsed.messageId
        )

        // Diffuser un broadcast pour rafraîchir l'écran de conversation actif
        val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
            putExtra(OrbisEventBus.EXTRA_CONV_ID, convId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Traite un accusé de réception éphémère reçu (Kind 20003) pour faire passer
     * le message de SENT (✓ 1 coche) à DELIVERED (✓✓ 2 coches) ou mettre à jour la présence en temps réel.
     */
    private fun handleReceipt(event: NostrEvent) {
        if (blockedRepo.isBlocked(event.pubkey)) {
            Log.d(TAG, "Accusé de réception ignoré : émetteur bloqué (${event.pubkey.take(8)})")
            return
        }

        val receipt = NostrProtocolEngine.parseDeliveryReceiptEvent(event, identityManager) ?: return
        if (blockedRepo.isBlocked(receipt.senderPubkey)) {
            Log.d(TAG, "Accusé de réception ignoré : contact bloqué (${receipt.senderPubkey.take(8)})")
            return
        }

        Log.d(TAG, "Accusé de réception reçu pour msg=${receipt.messageId} conv=${receipt.conversationId} status=${receipt.status}")

        // Cas 1 : Notification explicite de déconnexion / passage hors-ligne
        if (receipt.status.equals("OFFLINE", ignoreCase = true)) {
            com.sha.orbis.social.PresenceHelper.recordPeerOffline(receipt.senderPubkey)
            val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                putExtra(OrbisEventBus.EXTRA_CONV_ID, receipt.conversationId)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            return
        }

        // Cas 2 : Battement de présence active (En ligne)
        if (receipt.status.equals("PRESENCE", ignoreCase = true)) {
            if (!sessionManager.isPresenceHidden) {
                com.sha.orbis.social.PresenceHelper.recordPeerActivity(receipt.senderPubkey)
                val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                    putExtra(OrbisEventBus.EXTRA_CONV_ID, receipt.conversationId)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
            }
            return
        }

        // Cas 3 : Accusé de réception de message physique (DELIVERED ou READ)
        if (!sessionManager.isPresenceHidden) {
            com.sha.orbis.social.PresenceHelper.recordPeerActivity(receipt.senderPubkey)
        }

        val targetStatus = when (receipt.status.uppercase()) {
            "READ" -> MessageDeliveryStatus.READ
            else -> MessageDeliveryStatus.DELIVERED
        }
        conversationRepo.updateMessageStatus(receipt.conversationId, receipt.messageId, targetStatus)

        // Notifier l'UI pour mettre à jour instantanément la coche (✓ -> ✓✓)
        val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
            putExtra(OrbisEventBus.EXTRA_CONV_ID, receipt.conversationId)
            putExtra(OrbisEventBus.EXTRA_MESSAGE_ID, receipt.messageId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Envoie un accusé de réception éphémère (Kind 20003) au correspondant.
     */
    fun sendDeliveryReceipt(
        recipientPubKeyHex: String,
        conversationId: String,
        messageId: String,
        status: String = "DELIVERED"
    ) {
        if (blockedRepo.isBlocked(recipientPubKeyHex)) return
        if (status.equals("PRESENCE", ignoreCase = true) && sessionManager.isPresenceHidden) return

        scope.launch {
            try {
                val receiptEvent = NostrProtocolEngine.buildDeliveryReceiptEvent(
                    identityManager = identityManager,
                    recipientPubKeyHex = recipientPubKeyHex,
                    conversationId = conversationId,
                    messageId = messageId,
                    status = status
                )
                relayPool.publish(receiptEvent)
                Log.d(TAG, "Accusé de réception Kind 20003 émis ($status) pour msg=$messageId vers ${recipientPubKeyHex.take(8)}")
            } catch (e: Exception) {
                Log.w(TAG, "Échec émission accusé de réception pour $messageId: ${e.message}")
            }
        }
    }

    /**
     * Diffuse un signal éphémère de présence (PRESENCE ou OFFLINE) à tous les amis acceptés.
     * Si l'utilisateur est en mode hors-ligne (isPresenceHidden), diffuse OFFLINE pour garantir
     * qu'aucun contact ne le voit en ligne.
     */
    fun broadcastPresence(isOnline: Boolean) {
        scope.launch {
            val myPubkey = identityManager.publicKeyHex.lowercase().trim()
            if (myPubkey.length != 64) return@launch

            val effectiveStatus = if (isOnline && !sessionManager.isPresenceHidden) "PRESENCE" else "OFFLINE"

            val acceptedFriends = try {
                friendRequestRepo.loadRequests()
                    .filter { it.status == com.sha.orbis.model.FriendRequestStatus.ACCEPTED && !it.senderPublicKey.isNullOrBlank() }
                    .mapNotNull { FriendRequestRepository.resolveNostrPubkeyHex(it.senderPublicKey) }
                    .distinct()
            } catch (_: Exception) { emptyList() }

            for (friendKey in acceptedFriends) {
                if (blockedRepo.isBlocked(friendKey)) continue
                try {
                    sendDeliveryReceipt(
                        recipientPubKeyHex = friendKey,
                        conversationId = "presence_${friendKey.take(8)}",
                        messageId = "presence_${System.currentTimeMillis()}",
                        status = effectiveStatus
                    )
                } catch (_: Exception) {}
            }
            Log.d(TAG, "Signal de présence '$effectiveStatus' diffusé à ${acceptedFriends.size} ami(s)")
        }
    }


    private fun handleCallSignal(event: NostrEvent) {
        try {
            val myIdentity = identityManager.getOrCreateIdentity()
            val senderBytes = Bech32.hexToBytes(event.pubkey)
            val decrypted = com.sha.orbis.nostr.crypto.NostrCipher.decrypt(
                encryptedContent = event.content,
                myPrivKey32 = myIdentity.privateKey,
                theirPubKey32 = senderBytes
            )
            val json = org.json.JSONObject(decrypted)
            val sigType = json.optString("sigType")
            val callId = json.optString("callId")
            val senderNpub = Bech32.npubEncode(event.pubkey)
            val activeCallForSignalAge = com.sha.orbis.call.OrbisCallManager.callState.value

            // Anti-boucle infinie : Ignorer si l'appel a déjà été traité ou terminé
            if (callId.isNotBlank() && com.sha.orbis.call.OrbisCallManager.isCallAlreadyHandled(callId)) {
                Log.d(TAG, "Signal Nostr ignoré : callId=$callId a déjà été traité ou terminé")
                return
            }

            // Anti-boucle infinie : les OFFER entrants restent courts, mais les ANSWER/CANDIDATE
            // peuvent arriver en retard sur 4G/4G via relais Nostr. Avec l'isolation par callId,
            // accepter ces candidats tardifs pendant l'appel actif évite les appels muets CGNAT.
            val nowSeconds = System.currentTimeMillis() / 1000L
            val signalAgeSeconds = if (event.createdAt > 0) nowSeconds - event.createdAt else 0L
            val maxAgeSeconds = when (sigType) {
                "CANDIDATE" -> if (activeCallForSignalAge?.callId == callId) 180L else 60L
                "ANSWER" -> 120L
                "END" -> 300L
                else -> 45L
            }
            if (event.createdAt > 0 && signalAgeSeconds > maxAgeSeconds) {
                Log.w(TAG, "Signal Nostr expiré ignoré (${signalAgeSeconds}s de retard, max=${maxAgeSeconds}s, sigType=$sigType, callId=$callId)")
                return
            }

            when (sigType) {
                "OFFER" -> {
                    val callerPhone = json.optString("callerPhone").ifBlank { senderNpub }
                    if (blockedRepo.isBlocked(callerPhone) || blockedRepo.isBlocked(senderNpub) || blockedRepo.isBlocked(event.pubkey)) {
                        Log.d(TAG, "Signal appel entrant ignoré : l'appelant est bloqué ($callerPhone)")
                        return
                    }

                    // ── ICE Restart renegotiation : OFFER reçu pendant un appel actif ──
                    val isIceRestart = json.optBoolean("iceRestart", false)
                    val activeCall = com.sha.orbis.call.OrbisCallManager.callState.value
                    if (isIceRestart && activeCall != null && activeCall.callId == callId) {
                        val sdpOffer = json.optString("sdp", "")
                        if (sdpOffer.isNotBlank()) {
                            Log.i(TAG, "ICE Restart: renegotiation OFFER reçu pour callId=$callId")
                            val isVideo = activeCall.isVideoCall
                            com.sha.orbis.call.OrbisWebRTCManager.setRemoteOfferAndCreateAnswer(sdpOffer, isVideo) { answerSdp, answerType ->
                                if (answerSdp != null) {
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                            val answerPayload = JSONObject().apply {
                                                put("action", "ACCEPTED")
                                                put("isVideoAccepted", isVideo)
                                                put("sdp", answerSdp)
                                                put("type", answerType ?: "answer")
                                                put("iceRestart", true)
                                            }
                                            sendCallSignal(
                                                recipientNpubOrHex = senderNpub,
                                                callId = callId,
                                                signalType = "ANSWER",
                                                payloadJson = answerPayload
                                            )
                                        } catch (e: Exception) {
                                            Log.w(TAG, "ICE restart answer failed: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                        return
                    }

                    val callerName = json.optString("callerName").ifBlank { "Appel OrbisNet" }
                    val peerLocalIp = json.optString("localIp", "")
                    val peerLocalPort = json.optInt("localPort", com.sha.orbis.call.OrbisAudioStreamer.AUDIO_PORT)
                    val isVideo = json.optBoolean("isVideo", false)
                    // WebRTC SDP offer (primary path — remplace ip/port UDP)
                    val sdpOffer = json.optString("sdp", "").ifBlank { null }
                    // Legacy video endpoints (kept for backward compat)
                    val videoIp = json.optString("videoIp", "").ifBlank { null }
                    val videoPort = json.optInt("videoPort", com.sha.orbis.call.OrbisVideoStreamer.VIDEO_PORT)
                    val videoLocalPort = json.optInt("videoLocalPort", com.sha.orbis.call.OrbisVideoStreamer.VIDEO_PORT)
                    com.sha.orbis.call.OrbisCallManager.onIncomingCallReceived(
                        context = context,
                        callId = callId,
                        callerPhone = callerPhone,
                        callerName = callerName,
                        safetyNumber = json.optString("safetyNumber", "0000"),
                        senderAddress = callerPhone,
                        peerIp = json.optString("ip", ""),
                        peerPort = json.optInt("port", com.sha.orbis.call.OrbisAudioStreamer.AUDIO_PORT),
                        peerLocalIp = peerLocalIp,
                        peerLocalPort = peerLocalPort,
                        peerNostrKey = senderNpub,
                        isVideo = isVideo,
                        peerVideoIp = videoIp ?: json.optString("ip", ""),
                        peerVideoPort = videoPort,
                        peerVideoLocalPort = videoLocalPort,
                        peerSdpOffer = sdpOffer
                    )
                }
                "ANSWER" -> {
                    val peerLocalIp = json.optString("localIp", "")
                    val peerLocalPort = json.optInt("localPort", com.sha.orbis.call.OrbisAudioStreamer.AUDIO_PORT)
                    val isVideoAccepted = json.optBoolean("isVideoAccepted", true)
                    // WebRTC SDP answer (primary path — remplace ip/port UDP)
                    val sdpAnswer = json.optString("sdp", "").ifBlank { null }
                    // Legacy video endpoints (kept for backward compat)
                    val videoIp = json.optString("videoIp", "").ifBlank { null }
                    val videoPort = json.optInt("videoPort", com.sha.orbis.call.OrbisVideoStreamer.VIDEO_PORT)
                    val videoLocalPort = json.optInt("videoLocalPort", com.sha.orbis.call.OrbisVideoStreamer.VIDEO_PORT)
                    com.sha.orbis.call.OrbisCallManager.onCallAnswerReceived(
                        callId = callId,
                        action = json.optString("action", "ACCEPT"),
                        ip = json.optString("ip", ""),
                        port = json.optInt("port", com.sha.orbis.call.OrbisAudioStreamer.AUDIO_PORT),
                        localIp = peerLocalIp,
                        localPort = peerLocalPort,
                        isVideoAccepted = isVideoAccepted,
                        peerVideoIp = videoIp ?: json.optString("ip", ""),
                        peerVideoPort = videoPort,
                        peerVideoLocalPort = videoLocalPort,
                        peerSdpAnswer = sdpAnswer
                    )
                }
                "END" -> {
                    com.sha.orbis.call.OrbisCallManager.onCallEndedRemote(callId)
                }
                // WebRTC ICE trickle — miroir noscall calling_controller.dart onIceCandidateHandler
                "CANDIDATE" -> {
                    val candidateSdp = json.optString("candidate", "")
                    val sdpMid = json.optString("sdpMid", "0")
                    val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)
                    if (candidateSdp.isNotBlank()) {
                        com.sha.orbis.call.OrbisCallManager.onIceCandidateReceived(
                            candidateSdp  = candidateSdp,
                            sdpMid        = sdpMid,
                            sdpMLineIndex = sdpMLineIndex,
                            callId        = callId   // Fix Bug 3 : callId obligatoire pour isoler les appels
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur traitement signal appel: ${e.message}")
        }
    }

    private fun handleTimelinePost(event: NostrEvent) {
        if (socialRepo.isPostDeleted(event.id)) {
            return
        }

        val isComment = event.tags.any { it.size >= 2 && (it[0] == "e" || it[0] == "post_id") } &&
            (event.tags.any { it.size >= 2 && it[0] == "t" && it[1] == "comment" } || event.content.contains("\"commentId\""))

        val myPubkey = identityManager.publicKeyHex
        val myNpub = try { Bech32.npubEncode(myPubkey) } catch (_: Exception) { "" }

        if (isComment) {
            val comment = NostrProtocolEngine.parseCommentEvent(event) ?: return
            if (socialRepo.isPostDeleted(comment.postId)) {
                Log.d(TAG, "Commentaire ignoré : post parent supprimé (${comment.postId})")
                return
            }
            if (blockedRepo.isBlocked(comment.authorPhone) || blockedRepo.isBlocked(event.pubkey)) {
                Log.d(TAG, "Commentaire ignoré : auteur bloqué (${comment.authorPhone})")
                return
            }

            // Ne bloquer que si le commentaire est RÉELLEMENT présent dans le post.
            // Un event peut avoir été "processed" alors que le post parent n'existait pas encore,
            // auquel cas le commentaire n'a jamais été ajouté et doit être ré-essayé.
            if (processedEventsRepo.isProcessed(event.id) || processedEventsRepo.isProcessed(comment.id)) {
                val parentPost = socialRepo.findPostById(comment.postId)
                val commentAlreadyInPost = parentPost?.comments?.any { it.id == comment.id } == true
                if (commentAlreadyInPost) {
                    return
                }
                // Le commentaire n'est PAS dans le post → continuer pour le ré-ajouter
                Log.d(TAG, "Commentaire ${comment.id} marqué processed mais absent du post → ré-ajout")
            }

            val savedAvatar = if (!comment.authorAvatarPath.isNullOrBlank() && comment.authorAvatarPath!!.length > 50) {
                com.sha.orbis.ui.components.AvatarManager.saveAvatarFromBase64(context, comment.authorAvatarPath, "avatar_${comment.authorPhone.filter { it.isDigit() }.ifBlank { event.pubkey.take(8) }}")
            } else null
            val finalComment = if (savedAvatar != null) comment.copy(authorAvatarPath = savedAvatar) else comment
            val isNewlyAdded = socialRepo.addComment(finalComment.postId, finalComment)
            when {
                isNewlyAdded -> {
                    processedEventsRepo.markProcessed(event.id)
                    processedEventsRepo.markProcessed(comment.id)
                }
                socialRepo.findPostById(finalComment.postId) != null -> {
                    processedEventsRepo.markProcessed(event.id)
                    processedEventsRepo.markProcessed(comment.id)
                }
                else -> socialRepo.enqueuePendingComment(finalComment)
            }

            // Déclencher notification sociale pour le commentaire si la publication nous appartient
            val isFromMe = event.pubkey.equals(myPubkey, ignoreCase = true) ||
                (myNpub.isNotBlank() && comment.authorPhone == myNpub) ||
                FriendRequestRepository.isSamePhone(comment.authorPhone, sessionManager.userPhone)

            if (!isFromMe) {
                val allPosts = socialRepo.loadPosts()
                val targetPost = allPosts.find {
                    it.id == finalComment.postId ||
                    (finalComment.postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(finalComment.postId, ignoreCase = true))
                }
                if (targetPost != null && !socialRepo.isPostDeleted(targetPost.id)) {
                    val isMyPost = FriendRequestRepository.isSamePhone(targetPost.authorPhone, sessionManager.userPhone) ||
                        (myNpub.isNotBlank() && targetPost.authorPhone == myNpub) ||
                        targetPost.authorPhone.equals(myPubkey, ignoreCase = true)

                    if (isMyPost) {
                        val cleanCommentAuthor = if (finalComment.authorName.startsWith("npub1", ignoreCase = true) || finalComment.authorName.startsWith("+") || finalComment.authorName.isBlank()) {
                            val c = conversationRepo.loadContacts().find { it.phone == finalComment.authorPhone || it.publicKey == finalComment.authorPhone }
                            val r = friendRequestRepo.loadRequests().find { it.senderPhone == finalComment.authorPhone || it.senderPublicKey == finalComment.authorPhone }
                            c?.name?.takeIf { !it.startsWith("npub1") && !it.startsWith("+") }
                                ?: r?.senderName?.takeIf { !it.startsWith("npub1") && !it.startsWith("+") }
                                ?: context.getString(R.string.notif_sender_orbis_member)
                        } else finalComment.authorName

                        val notifText = context.getString(R.string.notif_social_commented_my_post, finalComment.text.take(60))
                        val notifId = "notif_comment_${event.id.take(16)}"
                        val notifAlreadyExists = notifRepo.hasNotificationForEvent(notifId)
                        val notif = com.sha.orbis.model.AppNotification(
                            id = notifId,
                            title = cleanCommentAuthor,
                            description = notifText,
                            timestamp = finalComment.timestamp,
                            type = com.sha.orbis.model.NotificationType.SOCIAL,
                            isRead = false,
                            senderPhone = finalComment.authorPhone,
                            senderName = cleanCommentAuthor,
                            senderAvatarPath = savedAvatar ?: finalComment.authorAvatarPath,
                            targetPostId = targetPost.id,
                            actionType = "COMMENT"
                        )
                        val addedToNotifRepo = notifRepo.addNotification(notif)

                        // Ne notifier (pop-up & système) QUE SI:
                        // 1. Événement en direct (< 2 min) - évite le rejeu historique au lancement / vidage du launcher
                        // 2. Événement pas déjà traité
                        // 3. Nouveau commentaire effectivement inséré
                        // 4. Notification pas déjà présente
                        val eventAgeMs = System.currentTimeMillis() - (event.createdAt * 1000L)
                        val isLiveEvent = eventAgeMs in -30_000L..120_000L

                        if (isLiveEvent && isNewlyAdded && addedToNotifRepo && !notifAlreadyExists) {
                            SmsNotificationHelper.showSocialNotification(
                                context = context,
                                title = cleanCommentAuthor,
                                text = notifText,
                                postId = targetPost.id,
                                actionType = "COMMENT"
                            )
                        }
                        com.sha.orbis.data.OrbisBadgeHub.refresh(context)
                    }
                }
            }

            val intent = Intent(OrbisEventBus.ACTION_ORBIS_POST_RECEIVED)
            context.sendBroadcast(intent)
        } else {
            val post = NostrProtocolEngine.parsePostEvent(event) ?: return
            if (socialRepo.isPostDeleted(post.id) || socialRepo.isPostDeleted(event.id)) {
                Log.d(TAG, "Publication ignorée : marquée comme supprimée (${post.id} / ${event.id})")
                return
            }
            if (blockedRepo.isBlocked(post.authorPhone) || blockedRepo.isBlocked(event.pubkey)) {
                Log.d(TAG, "Publication ignorée : auteur bloqué (${post.authorPhone})")
                return
            }

            val isAlreadyProcessed = processedEventsRepo.isProcessed(event.id) || processedEventsRepo.isProcessed(post.id)
            processedEventsRepo.markProcessed(event.id)
            processedEventsRepo.markProcessed(post.id)

            val savedAvatar = if (!post.authorAvatarPath.isNullOrBlank() && post.authorAvatarPath!!.length > 50) {
                com.sha.orbis.ui.components.AvatarManager.saveAvatarFromBase64(context, post.authorAvatarPath, "avatar_${post.authorPhone.filter { it.isDigit() }.ifBlank { event.pubkey.take(8) }}")
            } else null

            // Save incoming Base64 image to local file cache and strip giant string from RAM/posts.json.
            // IMPORTANT: mediaData = Base64(BinarySmsCompressor(jpegBytes)) — must use base64ToImageFile()
            // which decompresses correctly. Writing raw decoded bytes = fichier corrompu non-JPEG.
            val savedMediaPath = if (!post.mediaData.isNullOrBlank() && post.mediaData!!.length > 50) {
                try {
                    val cleanPostId = post.id.filter { it.isLetterOrDigit() || it == '_' }.take(32).ifBlank { "post_${System.currentTimeMillis()}" }
                    val cleanBase64 = if (post.mediaData!!.contains(",")) post.mediaData!!.substringAfter(",") else post.mediaData!!
                    if (post.mediaType == "video" || post.isVideo) {
                        com.sha.orbis.media.VideoMediaHelper.base64ToVideoFile(context, cleanBase64.trim(), cleanPostId)?.absolutePath
                    } else {
                        com.sha.orbis.media.MediaAttachmentHelper.base64ToImageFile(context, cleanBase64.trim(), cleanPostId)?.absolutePath
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "handleTimelinePost: échec sauvegarde média post ${post.id}: ${e.message}")
                    null
                }
            } else post.mediaPath

            val finalPost = post.copy(
                authorAvatarPath = savedAvatar ?: post.authorAvatarPath,
                mediaPath = savedMediaPath ?: post.mediaPath,
                mediaUrl = post.mediaUrl,
                mediaData = null // Strip large Base64 to protect memory footprint
            )

            // Guard confidentialité — vérifier l'appartenance AVANT de persister
            val isFromMe = event.pubkey.equals(myPubkey, ignoreCase = true) ||
                (myNpub.isNotBlank() && post.authorPhone == myNpub) ||
                FriendRequestRepository.isSamePhone(post.authorPhone, sessionManager.userPhone)
            val isFriend = friendRequestRepo.isFriend(finalPost.authorPhone)
            val isOfficial = finalPost.isOfficialAnnouncement

            if (!isFromMe && !isFriend && !isOfficial) {
                Log.d(TAG, "Post ignoré (auteur non-ami) : ${finalPost.authorPhone}")
                return
            }

            if (socialRepo.isPostDeleted(finalPost.id) || socialRepo.isPostDeleted(event.id)) {
                return
            }

            // Persister uniquement si auteur de confiance
            socialRepo.addPost(finalPost)

            // Notification pour les posts d'amis ou annonces officielles
            if (!isFromMe) {
                if (isFriend || isOfficial) {
                    val cleanPostAuthor = if (finalPost.authorName.startsWith("npub1", ignoreCase = true) || finalPost.authorName.startsWith("+") || finalPost.authorName.isBlank()) {
                        val c = conversationRepo.loadContacts().find { it.phone == finalPost.authorPhone || it.publicKey == finalPost.authorPhone }
                        val r = friendRequestRepo.loadRequests().find { it.senderPhone == finalPost.authorPhone || it.senderPublicKey == finalPost.authorPhone }
                        c?.name?.takeIf { !it.startsWith("npub1") && !it.startsWith("+") }
                            ?: r?.senderName?.takeIf { !it.startsWith("npub1") && !it.startsWith("+") }
                            ?: context.getString(R.string.notif_sender_orbis_member)
                    } else finalPost.authorName

                    val notifText = context.getString(R.string.notif_social_new_post)
                    val notifId = "notif_post_${event.id.take(16)}"
                    val notifAlreadyExists = notifRepo.hasNotificationForEvent(notifId)
                    val notif = com.sha.orbis.model.AppNotification(
                        id = notifId,
                        title = cleanPostAuthor,
                        description = notifText,
                        timestamp = finalPost.timestamp,
                        type = com.sha.orbis.model.NotificationType.SOCIAL,
                        isRead = false,
                        senderPhone = finalPost.authorPhone,
                        senderName = cleanPostAuthor,
                        senderAvatarPath = savedAvatar ?: finalPost.authorAvatarPath,
                        targetPostId = finalPost.id,
                        actionType = "POST"
                    )
                    val addedToNotifRepo = notifRepo.addNotification(notif)

                    val eventAgeMs = System.currentTimeMillis() - (event.createdAt * 1000L)
                    val isLiveEvent = eventAgeMs in -30_000L..120_000L

                    if (isLiveEvent && !isAlreadyProcessed && addedToNotifRepo && !notifAlreadyExists) {
                        SmsNotificationHelper.showSocialNotification(
                            context = context,
                            title = cleanPostAuthor,
                            text = notifText,
                            postId = finalPost.id,
                            actionType = "POST"
                        )
                    }
                    com.sha.orbis.data.OrbisBadgeHub.refresh(context)
                }
            }

            val intent = Intent(OrbisEventBus.ACTION_ORBIS_POST_RECEIVED)
            context.sendBroadcast(intent)
        }
    }

    private fun handleReaction(event: NostrEvent) {
        val targetStoryId = event.tags.find { it.size >= 2 && it[0] == "story_id" }?.get(1)
        if (!targetStoryId.isNullOrBlank()) {
            handleStoryReaction(event, targetStoryId)
            return
        }

        val targetPostId = event.tags.find { it.size >= 2 && it[0] == "post_id" }?.get(1)
            ?: event.tags.find { it.size >= 2 && it[0] == "e" }?.get(1)
            ?: return
        val targetCommentId = event.tags.find { it.size >= 2 && it[0] == "comment_id" }?.get(1)?.trim()

        val targetPollOptionId = event.tags.find { it.size >= 2 && (it[0] == "poll_option" || it[0] == "option_id") }?.get(1)?.trim()
            ?: if (event.content.startsWith("POLL_VOTE:")) event.content.removePrefix("POLL_VOTE:").trim() else null

        if (!targetPollOptionId.isNullOrBlank()) {
            handlePollVote(event, targetPostId, targetPollOptionId)
            return
        }

        if (socialRepo.isPostDeleted(targetPostId)) {
            Log.d(TAG, "Réaction ignorée : post cible supprimé ($targetPostId)")
            return
        }

        if (processedEventsRepo.isProcessed(event.id)) {
            return
        }

        val senderNpub = Bech32.npubEncode(event.pubkey)
        val emoji = event.content.ifBlank { "❤️" }

        val tagAuthorName = event.tags.find { it.size >= 2 && it[0] == "author_name" }?.get(1)?.trim()
        val tagAuthorPhone = event.tags.find { it.size >= 2 && it[0] == "author_phone" }?.get(1)?.trim()
        val tagAuthorAvatar = event.tags.find { it.size >= 2 && it[0] == "author_avatar" }?.get(1)?.trim()

        val contacts = conversationRepo.loadContacts()
        val matchedContact = contacts.find {
            it.publicKey.equals(event.pubkey, ignoreCase = true) ||
            it.publicKey.equals(senderNpub, ignoreCase = true) ||
            (!tagAuthorPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.phone, tagAuthorPhone))
        }
        val userIdentifier = matchedContact?.phone?.takeIf { it.isNotBlank() } ?: tagAuthorPhone ?: senderNpub

        if (blockedRepo.isBlocked(userIdentifier) || blockedRepo.isBlocked(senderNpub) || blockedRepo.isBlocked(event.pubkey)) {
            Log.d(TAG, "Réaction ignorée : expéditeur bloqué ($userIdentifier)")
            return
        }

        val isCommentReaction = !targetCommentId.isNullOrBlank()
        val applied: Boolean = if (isCommentReaction) {
            socialRepo.applyIncomingCommentReaction(targetPostId, targetCommentId!!, userIdentifier, emoji) ||
                socialRepo.enqueuePendingCommentReaction(targetPostId, targetCommentId, userIdentifier, emoji)
        } else {
            socialRepo.applyIncomingReaction(targetPostId, userIdentifier, emoji) ||
                socialRepo.enqueuePendingReaction(targetPostId, userIdentifier, emoji)
        }
        if (applied) {
            processedEventsRepo.markProcessed(event.id)
        }
        val isNewlyAdded: Boolean = if (isCommentReaction) {
            applied && socialRepo.findPostById(targetPostId)?.comments?.any { c ->
                c.id == targetCommentId && c.reactions.any {
                    FriendRequestRepository.isSamePhone(it.userPhone, userIdentifier) && it.emoji == emoji
                }
            } == true
        } else {
            applied && socialRepo.findPostById(targetPostId)?.reactions?.any {
                FriendRequestRepository.isSamePhone(it.userPhone, userIdentifier) && it.emoji == emoji
            } == true
        }

        // Rafraîchir le feed en temps réel — sans ce broadcast, les réactions
        // n'apparaissent que lors du prochain événement post (arrivée notification OK, affichage NON).
        context.sendBroadcast(
            android.content.Intent(com.sha.orbis.notification.OrbisEventBus.ACTION_ORBIS_REACTION_RECEIVED)
        )

        // Si la réaction vise notre publication/commentaire et ne vient pas de nous-même, créer une notification sociale
        val myPubkey = identityManager.publicKeyHex
        val myNpub = try { Bech32.npubEncode(myPubkey) } catch (_: Exception) { "" }
        val isFromMe = event.pubkey.equals(myPubkey, ignoreCase = true) ||
            senderNpub.equals(myNpub, ignoreCase = true) ||
            FriendRequestRepository.isSamePhone(userIdentifier, sessionManager.userPhone)

        if (!isFromMe) {
            val allPosts = socialRepo.loadPosts()
            val targetPost = allPosts.find {
                it.id == targetPostId ||
                (targetPostId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(targetPostId, ignoreCase = true))
            }
            if (targetPost != null && !socialRepo.isPostDeleted(targetPost.id)) {
                val isMyPost = FriendRequestRepository.isSamePhone(targetPost.authorPhone, sessionManager.userPhone) ||
                    (myNpub.isNotBlank() && targetPost.authorPhone == myNpub) ||
                    targetPost.authorPhone.equals(myPubkey, ignoreCase = true)
                val targetComment = if (isCommentReaction) {
                    targetPost.comments.firstOrNull { it.id == targetCommentId }
                } else null
                val isMyComment = isCommentReaction && targetComment != null && (
                    FriendRequestRepository.isSamePhone(targetComment.authorPhone, sessionManager.userPhone) ||
                        (myNpub.isNotBlank() && targetComment.authorPhone == myNpub) ||
                        targetComment.authorPhone.equals(myPubkey, ignoreCase = true)
                    )

                if (isMyPost || isMyComment) {
                    val savedAvatar = if (!tagAuthorAvatar.isNullOrBlank() && tagAuthorAvatar.length > 50) {
                        com.sha.orbis.ui.components.AvatarManager.saveAvatarFromBase64(
                            context,
                            tagAuthorAvatar,
                            "avatar_${tagAuthorPhone?.filter { it.isDigit() }?.ifBlank { event.pubkey.take(8) } ?: event.pubkey.take(8)}"
                        )
                    } else null

                    val matchedRequest = friendRequestRepo.loadRequests().find {
                        it.senderPublicKey.equals(event.pubkey, ignoreCase = true) ||
                        it.senderPublicKey.equals(senderNpub, ignoreCase = true) ||
                        (!tagAuthorPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.senderPhone, tagAuthorPhone))
                    }

                    val postAuthorMatch = if (matchedContact == null && matchedRequest == null && tagAuthorName.isNullOrBlank()) {
                        allPosts.asSequence().mapNotNull { p ->
                            if (p.authorPhone.equals(senderNpub, ignoreCase = true) || p.authorPhone.equals(event.pubkey, ignoreCase = true) || (!tagAuthorPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(p.authorPhone, tagAuthorPhone))) {
                                p.authorName to p.authorAvatarPath
                            } else {
                                p.comments.firstOrNull { c ->
                                    c.authorPhone.equals(senderNpub, ignoreCase = true) || c.authorPhone.equals(event.pubkey, ignoreCase = true) || (!tagAuthorPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(c.authorPhone, tagAuthorPhone))
                                }?.let { it.authorName to it.authorAvatarPath }
                            }
                        }.firstOrNull { it.first.isNotBlank() && !it.first.startsWith("npub1") && !it.first.startsWith("+") }
                    } else null

                    val effectiveAvatar = savedAvatar
                        ?: matchedContact?.avatarPath
                        ?: matchedRequest?.senderAvatarPath
                        ?: postAuthorMatch?.second

                    val cleanSenderName = when {
                        !tagAuthorName.isNullOrBlank() && !tagAuthorName.startsWith("npub1") && !tagAuthorName.startsWith("+") -> tagAuthorName
                        matchedContact != null && matchedContact.name.isNotBlank() && !matchedContact.name.startsWith("+") && matchedContact.name != "O R B I S net" && !matchedContact.name.startsWith("npub1") -> matchedContact.name
                        matchedRequest != null && matchedRequest.senderName.isNotBlank() && !matchedRequest.senderName.startsWith("+") && !matchedRequest.senderName.startsWith("npub1") -> matchedRequest.senderName
                        postAuthorMatch != null -> postAuthorMatch.first
                        else -> context.getString(R.string.notif_sender_orbis_member)
                    }

                    val notifId = "notif_reaction_${event.id.take(16)}"
                    val notifAlreadyExists = notifRepo.hasNotificationForEvent(notifId)
                    val likedCommentAuthorName = targetComment?.authorName?.takeIf { it.isNotBlank() && !it.startsWith("npub1") }
                    val notifDescription = when {
                        isCommentReaction && likedCommentAuthorName != null && !isMyComment -> {
                            context.getString(R.string.notif_social_liked_comment_author, likedCommentAuthorName)
                        }
                        isCommentReaction -> context.getString(R.string.notif_social_liked_comment)
                        else -> context.getString(R.string.notif_social_liked_post)
                    }
                    val notifAction = if (isCommentReaction) "COMMENT_LIKE" else "LIKE"
                    val notif = com.sha.orbis.model.AppNotification(
                        id = notifId,
                        title = cleanSenderName,
                        description = notifDescription,
                        timestamp = event.createdAt * 1000L,
                        type = com.sha.orbis.model.NotificationType.SOCIAL,
                        isRead = false,
                        senderPhone = matchedContact?.phone ?: tagAuthorPhone ?: userIdentifier,
                        senderName = cleanSenderName,
                        senderAvatarPath = effectiveAvatar,
                        targetPostId = targetPost.id,
                        actionType = notifAction
                    )
                    val addedToNotifRepo = notifRepo.addNotification(notif)

                    val eventAgeMs = System.currentTimeMillis() - (event.createdAt * 1000L)
                    val isLiveEvent = eventAgeMs in -30_000L..120_000L

                    if (isLiveEvent && isNewlyAdded && addedToNotifRepo && !notifAlreadyExists) {
                        SmsNotificationHelper.showSocialNotification(
                            context = context,
                            title = cleanSenderName,
                            text = notifDescription,
                            postId = targetPost.id,
                            actionType = notifAction
                        )
                    }
                    com.sha.orbis.data.OrbisBadgeHub.refresh(context)
                }
            }
        }

        val intent = Intent(OrbisEventBus.ACTION_ORBIS_POST_RECEIVED)
        context.sendBroadcast(intent)
    }

    private fun handlePollVote(event: NostrEvent, targetPostId: String, targetPollOptionId: String) {
        if (socialRepo.isPostDeleted(targetPostId)) {
            Log.d(TAG, "Vote sondage ignoré : post cible supprimé ($targetPostId)")
            return
        }

        if (processedEventsRepo.isProcessed(event.id)) {
            return
        }

        val senderNpub = Bech32.npubEncode(event.pubkey)
        val tagAuthorName = event.tags.find { it.size >= 2 && it[0] == "author_name" }?.get(1)?.trim()
        val tagAuthorPhone = event.tags.find { it.size >= 2 && it[0] == "author_phone" }?.get(1)?.trim()
        val tagAuthorAvatar = event.tags.find { it.size >= 2 && it[0] == "author_avatar" }?.get(1)?.trim()

        val contacts = conversationRepo.loadContacts()
        val matchedContact = contacts.find {
            it.publicKey.equals(event.pubkey, ignoreCase = true) ||
            it.publicKey.equals(senderNpub, ignoreCase = true) ||
            (!tagAuthorPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.phone, tagAuthorPhone))
        }
        val userIdentifier = matchedContact?.phone?.takeIf { it.isNotBlank() } ?: tagAuthorPhone ?: senderNpub

        if (blockedRepo.isBlocked(userIdentifier) || blockedRepo.isBlocked(senderNpub) || blockedRepo.isBlocked(event.pubkey)) {
            Log.d(TAG, "Vote sondage ignoré : expéditeur bloqué ($userIdentifier)")
            return
        }

        val applied = socialRepo.applyIncomingPollVote(targetPostId, targetPollOptionId, userIdentifier) ||
            socialRepo.enqueuePendingPollVote(targetPostId, targetPollOptionId, userIdentifier)
        if (applied) {
            processedEventsRepo.markProcessed(event.id)
        }

        // Rafraîchir l'interface et le vote en temps réel
        context.sendBroadcast(
            Intent(com.sha.orbis.notification.OrbisEventBus.ACTION_ORBIS_REACTION_RECEIVED)
        )
        context.sendBroadcast(
            Intent(com.sha.orbis.notification.OrbisEventBus.ACTION_ORBIS_POST_RECEIVED)
        )

        // Notification si le post est à nous et le vote vient d'un tiers
        val myPubkey = identityManager.publicKeyHex
        val myNpub = try { Bech32.npubEncode(myPubkey) } catch (_: Exception) { "" }
        val isFromMe = event.pubkey.equals(myPubkey, ignoreCase = true) ||
            senderNpub.equals(myNpub, ignoreCase = true) ||
            FriendRequestRepository.isSamePhone(userIdentifier, sessionManager.userPhone)

        if (!isFromMe) {
            val allPosts = socialRepo.loadPosts()
            val targetPost = allPosts.find {
                it.id == targetPostId ||
                (targetPostId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(targetPostId, ignoreCase = true))
            }
            if (targetPost != null && !socialRepo.isPostDeleted(targetPost.id)) {
                val isMyPost = FriendRequestRepository.isSamePhone(targetPost.authorPhone, sessionManager.userPhone) ||
                    (myNpub.isNotBlank() && targetPost.authorPhone == myNpub) ||
                    targetPost.authorPhone.equals(myPubkey, ignoreCase = true)

                if (isMyPost) {
                    val savedAvatar = if (!tagAuthorAvatar.isNullOrBlank() && tagAuthorAvatar.length > 50) {
                        com.sha.orbis.ui.components.AvatarManager.saveAvatarFromBase64(
                            context,
                            tagAuthorAvatar,
                            "avatar_${tagAuthorPhone?.filter { it.isDigit() }?.ifBlank { event.pubkey.take(8) } ?: event.pubkey.take(8)}"
                        )
                    } else null

                    val matchedRequest = friendRequestRepo.loadRequests().find {
                        it.senderPublicKey.equals(event.pubkey, ignoreCase = true) ||
                        it.senderPublicKey.equals(senderNpub, ignoreCase = true) ||
                        (!tagAuthorPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.senderPhone, tagAuthorPhone))
                    }

                    val postAuthorMatch = if (matchedContact == null && matchedRequest == null && tagAuthorName.isNullOrBlank()) {
                        allPosts.asSequence().mapNotNull { p ->
                            if (p.authorPhone.equals(senderNpub, ignoreCase = true) || p.authorPhone.equals(event.pubkey, ignoreCase = true) || (!tagAuthorPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(p.authorPhone, tagAuthorPhone))) {
                                p.authorName to p.authorAvatarPath
                            } else null
                        }.firstOrNull { it.first.isNotBlank() && !it.first.startsWith("npub1") && !it.first.startsWith("+") }
                    } else null

                    val cleanSenderName = when {
                        !matchedContact?.name.isNullOrBlank() && !matchedContact!!.name.startsWith("npub1") && !matchedContact.name.startsWith("+") -> matchedContact.name
                        !matchedRequest?.senderName.isNullOrBlank() && !matchedRequest!!.senderName.startsWith("npub1") && !matchedRequest.senderName.startsWith("+") -> matchedRequest.senderName
                        !tagAuthorName.isNullOrBlank() && !tagAuthorName.startsWith("npub1") && !tagAuthorName.startsWith("+") -> tagAuthorName
                        postAuthorMatch != null -> postAuthorMatch.first
                        else -> {
                            val displayDigits = (tagAuthorPhone ?: userIdentifier).filter { it.isDigit() }.takeLast(4)
                            if (displayDigits.isNotBlank()) "Orbis ($displayDigits)" else context.getString(R.string.notif_sender_orbis_member)
                        }
                    }

                    val effectiveAvatar = matchedContact?.avatarPath
                        ?: matchedRequest?.senderAvatarPath
                        ?: savedAvatar
                        ?: postAuthorMatch?.second

                    val notifDescription = context.getString(R.string.notif_social_poll_voted, cleanSenderName)
                    val notifId = "notif_poll_vote_${event.id.take(16)}"
                    val notifAlreadyExists = notifRepo.hasNotificationForEvent(notifId)

                    val notif = com.sha.orbis.model.AppNotification(
                        id = notifId,
                        title = cleanSenderName,
                        description = notifDescription,
                        timestamp = event.createdAt * 1000L,
                        type = com.sha.orbis.model.NotificationType.SOCIAL,
                        isRead = false,
                        senderPhone = matchedContact?.phone ?: tagAuthorPhone ?: userIdentifier,
                        senderName = cleanSenderName,
                        senderAvatarPath = effectiveAvatar,
                        targetPostId = targetPost.id,
                        actionType = "poll_vote"
                    )
                    val addedToNotifRepo = notifRepo.addNotification(notif)

                    val eventAgeMs = System.currentTimeMillis() - (event.createdAt * 1000L)
                    val isLiveEvent = eventAgeMs in -30_000L..120_000L

                    if (isLiveEvent && applied && addedToNotifRepo && !notifAlreadyExists) {
                        SmsNotificationHelper.showSocialNotification(
                            context = context,
                            title = cleanSenderName,
                            text = notifDescription,
                            postId = targetPost.id,
                            actionType = "poll_vote"
                        )
                    }
                    com.sha.orbis.data.OrbisBadgeHub.refresh(context)
                }
            }
        }
    }

    private fun handleStoryReaction(event: NostrEvent, targetStoryId: String) {
        val isAlreadyProcessed = processedEventsRepo.isProcessed(event.id)
        processedEventsRepo.markProcessed(event.id)
        if (isAlreadyProcessed) return

        val senderNpub = Bech32.npubEncode(event.pubkey)
        val myPubkey = identityManager.publicKeyHex
        val myNpub = try { Bech32.npubEncode(myPubkey) } catch (_: Exception) { "" }
        val emoji = event.content.ifBlank { "❤️" }

        val tagAuthorPhone = event.tags.find { it.size >= 2 && it[0] == "author_phone" }?.get(1)?.trim()
        val contacts = conversationRepo.loadContacts()
        val matchedContact = contacts.find {
            it.publicKey.equals(event.pubkey, ignoreCase = true) ||
                it.publicKey.equals(senderNpub, ignoreCase = true) ||
                (!tagAuthorPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.phone, tagAuthorPhone))
        }
        val userIdentifier = matchedContact?.phone?.takeIf { it.isNotBlank() } ?: tagAuthorPhone ?: senderNpub
        val isFromMe = event.pubkey.equals(myPubkey, ignoreCase = true) ||
            senderNpub.equals(myNpub, ignoreCase = true) ||
            FriendRequestRepository.isSamePhone(userIdentifier, sessionManager.userPhone)

        if (isFromMe) {
            return
        }
        if (blockedRepo.isBlocked(userIdentifier) || blockedRepo.isBlocked(senderNpub) || blockedRepo.isBlocked(event.pubkey)) {
            Log.d(TAG, "Réaction story ignorée : expéditeur bloqué ($userIdentifier)")
            return
        }

        val hasStory = socialRepo.loadStories().any {
            it.id == targetStoryId ||
                (targetStoryId.length == 64 && NostrProtocolEngine.toNostrHex(it.id).equals(targetStoryId, ignoreCase = true))
        }
        if (!hasStory) {
            Log.d(TAG, "Réaction story ignorée : story inconnue ($targetStoryId)")
            return
        }

        socialRepo.addStoryReaction(targetStoryId, userIdentifier, emoji)
        context.sendBroadcast(Intent(OrbisEventBus.ACTION_ORBIS_STORY_RECEIVED))
    }

    private fun handleDeletion(event: NostrEvent) {
        val storyId = event.tags.firstOrNull { it.size >= 2 && it[0] == "story_id" }?.get(1)
        val commentId = event.tags.firstOrNull { it.size >= 2 && it[0] == "comment_id" }?.get(1)
        val targetPostIds = event.tags.filter { it.size >= 2 && (it[0] == "post_id" || it[0] == "e") }.map { it[1] }

        if (!storyId.isNullOrBlank()) {
            socialRepo.deleteStory(storyId)
            val intent = Intent(OrbisEventBus.ACTION_ORBIS_STORY_RECEIVED)
            context.sendBroadcast(intent)
            return
        } else if (!commentId.isNullOrBlank() && targetPostIds.isNotEmpty()) {
            targetPostIds.forEach { pid ->
                socialRepo.deleteComment(pid, commentId)
            }
        } else if (targetPostIds.isNotEmpty()) {
            targetPostIds.forEach { pid ->
                socialRepo.deletePost(pid)
            }
        } else {
            return
        }
        val intent = Intent(OrbisEventBus.ACTION_ORBIS_POST_RECEIVED)
        context.sendBroadcast(intent)
    }

    private fun isStoryViewEvent(event: NostrEvent): Boolean {
        return event.tags.any { it.size >= 2 && it[0] == "t" && it[1] == "story_view" } ||
            event.content.contains("ORBISNET_STORY_VIEW")
    }

    private fun handleStoryView(event: NostrEvent) {
        val isAlreadyProcessed = processedEventsRepo.isProcessed(event.id)
        processedEventsRepo.markProcessed(event.id)
        if (isAlreadyProcessed) return

        val obj = try {
            event.content.trim().takeIf { it.startsWith("{") }?.let { JSONObject(it) }
        } catch (_: Exception) {
            null
        }
        val storyId = obj?.optString("storyId")?.ifBlank { null }
            ?: event.tags.find { it.size >= 2 && it[0] == "story_id" }?.get(1)
            ?: return
        val viewerPhone = obj?.optString("viewerPhone")?.ifBlank { null }
            ?: event.tags.find { it.size >= 2 && it[0] == "viewer_phone" }?.get(1)
            ?: return

        val localStory = socialRepo.loadStories().firstOrNull {
            it.id == storyId ||
                (storyId.length == 64 && NostrProtocolEngine.toNostrHex(it.id).equals(storyId, ignoreCase = true))
        } ?: return

        val myPubkey = identityManager.publicKeyHex
        val isMyStory = FriendRequestRepository.isSamePhone(localStory.authorPhone, sessionManager.userPhone) ||
            localStory.authorPubkey.equals(myPubkey, ignoreCase = true)
        if (!isMyStory || FriendRequestRepository.isSamePhone(viewerPhone, sessionManager.userPhone)) {
            return
        }

        val senderNpub = Bech32.npubEncode(event.pubkey)
        val contacts = conversationRepo.loadContacts()
        val matchedContact = contacts.find {
            it.publicKey.equals(event.pubkey, ignoreCase = true) ||
                it.publicKey.equals(senderNpub, ignoreCase = true) ||
                FriendRequestRepository.isSamePhone(it.phone, viewerPhone)
        }
        val viewerIdentifier = matchedContact?.phone?.takeIf { it.isNotBlank() } ?: viewerPhone

        if (blockedRepo.isBlocked(viewerIdentifier) || blockedRepo.isBlocked(senderNpub) || blockedRepo.isBlocked(event.pubkey)) {
            return
        }
        val isTrustedViewer = matchedContact != null ||
            friendRequestRepo.isFriend(viewerIdentifier) ||
            friendRequestRepo.isConnectedContact(viewerIdentifier)
        if (!isTrustedViewer) {
            Log.d(TAG, "Vue story ignorée : viewer non-ami ($viewerIdentifier)")
            return
        }

        if (socialRepo.markStorySeen(localStory.id, viewerIdentifier)) {
            context.sendBroadcast(Intent(OrbisEventBus.ACTION_ORBIS_STORY_RECEIVED))
        }
    }

    private fun handleStory(story: SocialStory) {
        if (story.isExpired) return
        if (blockedRepo.isBlocked(story.authorPhone)) return

        // Guard confidentialité — n'accepter que les stories de soi-même ou d'un ami accepté
        val isSelf = FriendRequestRepository.isSamePhone(story.authorPhone, sessionManager.userPhone)
        val isTrustedAuthor = isSelf
            || friendRequestRepo.isFriend(story.authorPhone)
            || friendRequestRepo.isConnectedContact(story.authorPhone)
            || (!story.authorPubkey.isNullOrBlank() && story.authorPubkey == identityManager.publicKeyHex)
        if (!isTrustedAuthor) {
            Log.d(TAG, "Story ignorée (auteur non-ami) : ${story.authorPhone}")
            return
        }
        if (!isSelf && !StoryAudiencePolicy.isVisibleToCurrentUser(context, story, sessionManager.userPhone, currentAccountId)) {
            Log.d(TAG, "Story ignorée (audience non éligible) : ${story.id}")
            return
        }

        socialRepo.addStory(story)
        val intent = Intent(OrbisEventBus.ACTION_ORBIS_STORY_RECEIVED)
        context.sendBroadcast(intent)
    }

    /**
     * Publie une story éphémère (24h) sur le réseau décentralisé Nostr.
     */
    fun publishStory(story: SocialStory): NostrEvent {
        val event = NostrProtocolEngine.buildStoryEvent(identityManager, story)
        val sentCount = relayPool.publish(event)
        Log.d(TAG, "Story ${story.id} publiée vers $sentCount relais")
        try {
            com.sha.orbis.telemetry.TelemetryManager.getInstance(context).recordEvent(com.sha.orbis.telemetry.FeatureType.STORY_POST)
        } catch (_: Exception) {}
        return event
    }

    fun publishStoryView(story: SocialStory): NostrEvent? {
        if (FriendRequestRepository.isSamePhone(story.authorPhone, sessionManager.userPhone)) {
            return null
        }
        val avatarThumb = sessionManager.userAvatarPath
            ?.takeIf { it.isNotBlank() }
            ?.let { com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(it, 96) }
        val event = NostrProtocolEngine.buildStoryViewEvent(
            identityManager = identityManager,
            story = story,
            viewerName = sessionManager.userName.ifBlank { context.getString(R.string.notif_sender_orbis_member) },
            viewerPhone = sessionManager.userPhone,
            viewerAvatarBase64 = avatarThumb
        )
        val sentCount = relayPool.publish(event)
        Log.d(TAG, "Vue story ${story.id} diffusée vers $sentCount relais")
        return event
    }

    fun publishStoryReaction(storyId: String, storyAuthorPubkey: String?, emoji: String): NostrEvent {
        val avatarThumb = sessionManager.userAvatarPath
            ?.takeIf { it.isNotBlank() }
            ?.let { com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(it, 96) }
        val event = NostrProtocolEngine.buildStoryReactionEvent(
            identityManager = identityManager,
            storyId = storyId,
            storyAuthorPubkey = storyAuthorPubkey,
            emoji = emoji,
            authorName = sessionManager.userName,
            authorPhone = sessionManager.userPhone,
            authorAvatarBase64 = avatarThumb
        )
        val sentCount = relayPool.publish(event)
        Log.d(TAG, "Réaction story $storyId diffusée vers $sentCount relais")
        return event
    }

    /**
     * Publie la suppression d'une story sur le réseau décentralisé Nostr (Kind 5).
     */
    fun publishDeleteStory(storyId: String): NostrEvent {
        val event = NostrProtocolEngine.buildDeleteStoryEvent(identityManager, storyId)
        val sentCount = relayPool.publish(event)
        Log.d(TAG, "Suppression de la story $storyId diffusée vers $sentCount relais")
        return event
    }

    /**
     * Envoie un message de discussion direct via le réseau Nostr.
     */
    fun sendDirectMessage(
        recipientNpubOrHex: String,
        conversationId: String,
        text: String,
        messageId: String? = null,
        audioBase64: String? = null,
        locationPayload: String? = null,
        ephemeralTimerMs: Long = 0L,
        senderAvatarBase64: String? = null,
        senderName: String? = null,
        senderPhone: String? = null
    ): NostrEvent {
        try {
            com.sha.orbis.telemetry.TelemetryManager.getInstance(context).recordEvent(com.sha.orbis.telemetry.FeatureType.NOSTR_CHAT)
        } catch (_: Exception) {}
        val resolvedHex = FriendRequestRepository.resolveNostrPubkeyHex(recipientNpubOrHex)
            ?: friendRequestRepo.resolveNostrPubkeyForPhone(recipientNpubOrHex)
            ?: run {
                val contacts = conversationRepo.loadContacts()
                val c = contacts.find { FriendRequestRepository.isSamePhone(it.phone, recipientNpubOrHex) || it.name.equals(recipientNpubOrHex, ignoreCase = true) }
                c?.publicKey?.let { FriendRequestRepository.resolveNostrPubkeyHex(it) }
            }
        val recipientPubkeyHex = resolvedHex ?: recipientNpubOrHex

        val effectiveSenderPhone = senderPhone ?: sessionManager.userPhone.trim().takeIf { it.isNotBlank() }
        val effectiveSenderName = senderName ?: sessionManager.userName.trim().takeIf { it.isNotBlank() }

        val event = NostrProtocolEngine.buildChatMessageEvent(
            identityManager = identityManager,
            recipientPubKeyHex = recipientPubkeyHex,
            conversationId = conversationId,
            plainText = text,
            messageId = messageId ?: "msg_${System.currentTimeMillis()}",
            audioBase64 = audioBase64,
            locationPayload = locationPayload,
            ephemeralTimerMs = ephemeralTimerMs,
            senderAvatarBase64 = senderAvatarBase64,
            senderName = effectiveSenderName,
            senderPhone = effectiveSenderPhone
        )

        val sentCount = relayPool.publish(event)
        Log.d(TAG, "Message transmis à $sentCount relais pour ${recipientNpubOrHex.take(12)}")

        // Impulsion push FCM haute priorité (Standard WhatsApp) pour réveiller le destinataire en Doze mode
        try {
            val peerFcmToken = friendRequestRepo.getPeerFcmToken(recipientPubkeyHex)
                ?: friendRequestRepo.getPeerFcmToken(recipientNpubOrHex)
                ?: friendRequestRepo.getPeerFcmToken(conversationId.removePrefix("conv_"))
            if (!peerFcmToken.isNullOrBlank()) {
                val preview = when {
                    audioBase64 != null -> "🎤 Message vocal"
                    locationPayload != null -> "📍 Localisation"
                    text.isNotBlank() -> text.take(120)
                    else -> "Nouveau message"
                }
                com.sha.orbis.call.fcm.OrbisFirebasePushHelper.sendMessagePush(
                    peerFcmToken = peerFcmToken,
                    conversationId = conversationId,
                    senderPhone = effectiveSenderPhone ?: "",
                    senderName = effectiveSenderName ?: "Orbis",
                    textSnippet = preview
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur push message FCM: ${e.message}")
        }

        return event
    }


    /**
     * Publie un post sur le fil d'actualité décentralisé.
     */
    fun publishPost(post: SocialPost): NostrEvent {
        val event = NostrProtocolEngine.buildPostEvent(identityManager, post)
        relayPool.publish(event)
        try {
            val isExtra = !post.id.startsWith("post_") && post.authorPhone.isBlank()
            val hasMedia = !post.mediaPath.isNullOrBlank() || !post.mediaUrl.isNullOrBlank()
            com.sha.orbis.telemetry.FeedTelemetryTracker.trackPostCreated(context, isExtraOrbis = isExtra, hasMedia = hasMedia)
            if (!post.targetCircleId.isNullOrBlank()) {
                com.sha.orbis.telemetry.FeedTelemetryTracker.trackCircleAction(context)
            }
        } catch (_: Exception) {}
        return event
    }

    /**
     * Résout la clé publique hexadécimale Nostr (32 bytes / 64 hex) pour un contact ou identifiant.
     * Si l'identifiant est un numéro de téléphone, cherche dans les contacts locaux et demandes d'amis.
     */
    fun resolveAuthorPubkeyHex(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()
        if (trimmed.startsWith("npub1")) {
            return try { Bech32.decodeToHex(trimmed).second.lowercase() } catch (_: Exception) { null }
        }
        if (trimmed.length == 64 && trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return trimmed.lowercase()
        }
        // Recherche dans les contacts
        val contacts = conversationRepo.loadContacts()
        val c = contacts.find { FriendRequestRepository.isSamePhone(it.phone, trimmed) || it.name.equals(trimmed, ignoreCase = true) }
        val cand = c?.publicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
        if (cand != null) {
            return FriendRequestRepository.resolveNostrPubkeyHex(cand)
        }
        // Recherche dans les demandes d'amis reçues
        val r = friendRequestRepo.loadRequests().find { FriendRequestRepository.isSamePhone(it.senderPhone, trimmed) }
        val rCand = r?.senderPublicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
        if (rCand != null) {
            return FriendRequestRepository.resolveNostrPubkeyHex(rCand)
        }
        return null
    }

    /**
     * Publie une réaction (like) sur un post.
     */
    fun publishReaction(
        postId: String,
        postAuthorNpubOrHex: String,
        emoji: String = "❤️",
        senderName: String? = null,
        senderPhone: String? = null,
        senderAvatarBase64: String? = null
    ): NostrEvent {
        val authorPubkeyHex = resolveAuthorPubkeyHex(postAuthorNpubOrHex)
        val myName = senderName ?: sessionManager.userName.trim().takeIf { it.isNotBlank() }
        val myPhone = senderPhone ?: sessionManager.userPhone.trim().takeIf { it.isNotBlank() }
        val myAvatar = senderAvatarBase64 ?: com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)

        val event = NostrProtocolEngine.buildReactionEvent(
            identityManager = identityManager,
            postId = postId,
            postAuthorPubkey = authorPubkeyHex,
            emoji = emoji,
            authorName = myName,
            authorPhone = myPhone,
            authorAvatarBase64 = myAvatar
        )
        relayPool.publish(event)
        try {
            com.sha.orbis.telemetry.FeedTelemetryTracker.trackReaction(context)
        } catch (_: Exception) {}
        return event
    }

    /**
     * Publie une réaction sur un COMMENTAIRE (Kind 7 avec tag "comment_id").
     * Identité locale persistée d'abord par l'appelant ; cette méthode s'occupe uniquement de la diffusion Nostr.
     */
    fun publishCommentReaction(
        postId: String,
        commentId: String,
        postAuthorNpubOrHex: String,
        emoji: String = "❤️",
        senderName: String? = null,
        senderPhone: String? = null,
        senderAvatarBase64: String? = null
    ): NostrEvent {
        val authorPubkeyHex = resolveAuthorPubkeyHex(postAuthorNpubOrHex)
        val myName = senderName ?: sessionManager.userName.trim().takeIf { it.isNotBlank() }
        val myPhone = senderPhone ?: sessionManager.userPhone.trim().takeIf { it.isNotBlank() }
        val myAvatar = senderAvatarBase64 ?: com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)

        val event = NostrProtocolEngine.buildReactionEvent(
            identityManager = identityManager,
            postId = postId,
            postAuthorPubkey = authorPubkeyHex,
            emoji = emoji,
            authorName = myName,
            authorPhone = myPhone,
            authorAvatarBase64 = myAvatar,
            commentId = commentId
        )
        relayPool.publish(event)
        try {
            com.sha.orbis.telemetry.FeedTelemetryTracker.trackReaction(context)
        } catch (_: Exception) {}
        return event
    }

    /**
     * Publie un commentaire sur un post via Nostr.
     */
    fun publishComment(postId: String, postAuthorNpubOrHex: String, comment: SocialComment): NostrEvent {
        val authorPubkeyHex = resolveAuthorPubkeyHex(postAuthorNpubOrHex)

        val event = NostrProtocolEngine.buildCommentEvent(
            identityManager = identityManager,
            postId = postId,
            postAuthorPubkey = authorPubkeyHex,
            comment = comment
        )
        relayPool.publish(event)
        try {
            com.sha.orbis.telemetry.FeedTelemetryTracker.trackComment(context)
        } catch (_: Exception) {}
        return event
    }

    /**
     * Publie un vote de sondage décentralisé sur les relais Nostr (Kind 7 avec tag poll_option).
     */
    fun publishPollVote(
        postId: String,
        optionId: String,
        postAuthorNpubOrHex: String
    ): NostrEvent {
        val authorPubkeyHex = resolveAuthorPubkeyHex(postAuthorNpubOrHex)
        val myName = sessionManager.userName.trim().takeIf { it.isNotBlank() }
        val myPhone = sessionManager.userPhone.trim().takeIf { it.isNotBlank() }
        val myAvatar = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)

        val tags = mutableListOf<List<String>>(
            listOf("e", postId),
            listOf("poll_option", optionId),
            listOf("t", NostrProtocolEngine.TAG_ORBISNET),
            listOf("t", "poll_vote")
        )
        if (!authorPubkeyHex.isNullOrBlank()) {
            tags.add(listOf("p", authorPubkeyHex))
        }
        if (!myName.isNullOrBlank()) {
            tags.add(listOf("author_name", myName))
        }
        if (!myPhone.isNullOrBlank()) {
            tags.add(listOf("author_phone", myPhone))
        }
        if (!myAvatar.isNullOrBlank()) {
            tags.add(listOf("author_avatar", myAvatar))
        }

        val event = identityManager.signEvent(
            kind = NostrEvent.KIND_REACTION,
            tags = tags,
            content = "POLL_VOTE:$optionId"
        )
        relayPool.publish(event)
        try {
            com.sha.orbis.telemetry.FeedTelemetryTracker.trackPollVote(context)
        } catch (_: Exception) {}
        return event
    }

    /**
     * Publie la suppression d'un post (Kind 5 NIP-09) sur les relais Nostr.
     */
    fun publishDeletePost(postId: String): NostrEvent {
        val event = NostrProtocolEngine.buildDeletePostEvent(identityManager, postId)
        relayPool.publish(event)
        return event
    }

    /**
     * Publie la suppression d'un commentaire (Kind 5 NIP-09) sur les relais Nostr.
     */
    fun publishDeleteComment(postId: String, commentId: String): NostrEvent {
        val event = NostrProtocolEngine.buildDeleteCommentEvent(identityManager, postId, commentId)
        relayPool.publish(event)
        return event
    }

    /**
     * Émet un signal d'appel chiffré de bout en bout via Nostr (Kind 20001 éphémère).
     */
    fun sendCallSignal(
        recipientNpubOrHex: String,
        callId: String,
        signalType: String,
        payloadJson: JSONObject = JSONObject()
    ): Boolean {
        return try {
            val resolvedHex = if (recipientNpubOrHex.startsWith("npub1")) {
                try { Bech32.decodeToHex(recipientNpubOrHex).second.lowercase() } catch (_: Exception) { null }
            } else if (recipientNpubOrHex.length == 64 && recipientNpubOrHex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                recipientNpubOrHex.lowercase()
            } else {
                // Look up in contacts or friend requests by phone or name
                val contacts = conversationRepo.loadContacts()
                val c = contacts.find { FriendRequestRepository.isSamePhone(it.phone, recipientNpubOrHex) || it.name.equals(recipientNpubOrHex, ignoreCase = true) }
                val r = friendRequestRepo.loadRequests().find { FriendRequestRepository.isSamePhone(it.senderPhone, recipientNpubOrHex) }
                val cand = c?.publicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
                    ?: r?.senderPublicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
                cand?.let { FriendRequestRepository.resolveNostrPubkeyHex(it) }
            }

            if (resolvedHex == null) {
                Log.e(TAG, "Impossible d'émettre le signal d'appel '$signalType' ($callId): Clé publique introuvable pour $recipientNpubOrHex")
                return false
            }

            val event = NostrProtocolEngine.buildCallSignalEvent(
                identityManager = identityManager,
                recipientPubKeyHex = resolvedHex,
                signalType = signalType,
                callId = callId,
                payloadJson = payloadJson
            )
            val sentRelays = relayPool.publish(event)
            Log.d(TAG, "Signal d'appel '$signalType' ($callId) transmis à $sentRelays relais pour ${recipientNpubOrHex.take(12)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Échec émission signal appel '$signalType': ${e.message}", e)
            false
        }
    }

    private fun handleInvitation(parsed: NostrProtocolEngine.ParsedInvitation) {
        val myPhone = sessionManager.userPhone.trim()
        val myPubkey = identityManager.publicKeyHex
        val myDigits = myPhone.filter { it.isDigit() }
        val myLast8 = myDigits.takeLast(8)
        val recDigits = parsed.recipientPhone.filter { it.isDigit() }

        // Ne pas traiter nos propres invitations
        if (parsed.senderPubkey.equals(myPubkey, ignoreCase = true)) return
        if (myDigits.isNotBlank() && FriendRequestRepository.isSamePhone(parsed.senderPhone, myPhone)) return

        // Vérifier si l'invitation nous est destinée
        val isForMe = FriendRequestRepository.isSamePhone(parsed.recipientPhone, myPhone) ||
                      (myLast8.length >= 8 && recDigits.endsWith(myLast8)) ||
                      parsed.recipientPhone.isBlank()
        if (!isForMe) return

        // Vérifier si expéditeur bloqué
        if (blockedRepo.isBlocked(parsed.senderPhone)) {
            Log.d(TAG, "Invitation ignorée car le numéro ${parsed.senderPhone} est bloqué")
            return
        }

        val cleanPhone = parsed.senderPhone.ifBlank { Bech32.npubEncode(parsed.senderPubkey) }

        // 0. Déduplication persistante
        val isEventAlreadyProcessed = processedEventsRepo.isProcessed(parsed.eventId)
        if (isEventAlreadyProcessed) {
            return
        }
        processedEventsRepo.markProcessed(parsed.eventId)

        // Vérifier si contact explicitement rejeté
        val existingRequest = friendRequestRepo.loadRequests().find {
            FriendRequestRepository.isSamePhone(it.senderPhone, cleanPhone) ||
            it.id == "req_nostr_${parsed.eventId.take(12)}"
        }
        if (existingRequest?.status == FriendRequestStatus.REJECTED) {
            Log.d(TAG, "Invitation de $cleanPhone ignorée car le contact est rejeté.")
            return
        }

        val isAlreadyFriend = friendRequestRepo.isFriend(cleanPhone) ||
                friendRequestRepo.isConnectedContact(cleanPhone) ||
                existingRequest?.status == FriendRequestStatus.ACCEPTED

        val cleanDigits = cleanPhone.filter { it.isDigit() }
        val avatarId = cleanDigits.ifBlank { parsed.senderPubkey.take(8) }
        val savedAvatar = if (!parsed.avatarBase64.isNullOrBlank()) {
            com.sha.orbis.ui.components.AvatarManager.saveAvatarFromBase64(context, parsed.avatarBase64, "avatar_$avatarId")
        } else null

        // CAS CRITIQUE : L'expéditeur est déjà un ami confirmé (ex: réinstallation après wipe de données ou rotation de clé)
        if (isAlreadyFriend) {
            Log.i(TAG, "Invitation reçue d'un ami déjà existant ($cleanPhone). Mise à jour automatique des clés et renvoi de l'ACK...")

            val contacts = conversationRepo.loadContacts().toMutableList()
            val contactIdx = contacts.indexOfFirst {
                FriendRequestRepository.isSamePhone(it.phone, cleanPhone) ||
                (!parsed.senderPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.phone, parsed.senderPhone)) ||
                (it.publicKey.isNotBlank() && it.publicKey.equals(parsed.senderPubkey, ignoreCase = true))
            }

            val oldKey = if (contactIdx >= 0) contacts[contactIdx].publicKey else existingRequest?.senderPublicKey
            val hasValidPubkey = FriendRequestRepository.isValidNostrKey(parsed.senderPubkey)
            val isKeyRotated = hasValidPubkey && !oldKey.isNullOrBlank() &&
                    !oldKey.equals("PUB_KEY_PENDING", ignoreCase = true) &&
                    !oldKey.equals("NPUB_PENDING", ignoreCase = true) &&
                    !oldKey.equals(parsed.senderPubkey, ignoreCase = true)

            val keepName = if (contactIdx >= 0) {
                val existing = contacts[contactIdx]
                if (existing.name.isNotBlank() && !existing.name.startsWith("+") && existing.name != "O R B I S net") {
                    existing.name
                } else if (parsed.senderName.isNotBlank() && parsed.senderName != "O R B I S net") {
                    parsed.senderName
                } else existing.name
            } else if (parsed.senderName.isNotBlank() && parsed.senderName != "O R B I S net") {
                parsed.senderName
            } else "Ami OrbisNet"

            val keepPhone = if (contactIdx >= 0 && contacts[contactIdx].phone.any { it.isDigit() }) {
                contacts[contactIdx].phone
            } else cleanPhone

            val updatedKey = if (hasValidPubkey) parsed.senderPubkey else (oldKey ?: "")

            // 1. Mettre à jour le contact en "Connecté 🛡️"
            if (contactIdx >= 0) {
                val existing = contacts[contactIdx]
                contacts[contactIdx] = existing.copy(
                    publicKey = updatedKey,
                    phone = keepPhone,
                    name = keepName,
                    status = "Connecté 🛡️",
                    avatarPath = savedAvatar ?: existing.avatarPath
                )
            } else {
                contacts.add(0, Contact(
                    id = "c_$avatarId",
                    name = keepName,
                    phone = keepPhone,
                    publicKey = updatedKey,
                    status = "Connecté 🛡️",
                    avatarPath = savedAvatar
                ))
            }
            conversationRepo.saveContacts(contacts)

            // 2. Confirmer dans FriendRequestRepository
            friendRequestRepo.ensureAcceptedFriend(
                phone = cleanPhone,
                name = keepName,
                publicKey = updatedKey,
                avatarPath = savedAvatar
            )
            if (parsed.groupKey.isNotBlank() || !parsed.fcmToken.isNullOrBlank()) {
                val reqs = friendRequestRepo.loadRequests().toMutableList()
                val rIdx = reqs.indexOfFirst { FriendRequestRepository.isSamePhone(it.senderPhone, cleanPhone) }
                if (rIdx >= 0) {
                    reqs[rIdx] = reqs[rIdx].copy(
                        groupKey = parsed.groupKey.ifBlank { reqs[rIdx].groupKey },
                        peerFcmToken = parsed.fcmToken ?: reqs[rIdx].peerFcmToken
                    )
                    friendRequestRepo.saveRequests(reqs)
                }
            }

            // 3. Assurer la conversation dans Discussions
            conversationRepo.ensureConversationForFriend(
                phone = cleanPhone,
                name = keepName,
                initialMessage = context.getString(R.string.friends_connected_last_msg)
            )

            // 4. RÉPONDRE IMMÉDIATEMENT PAR UN ACK (HANDSHAKE AUTOMATIQUE)
            val myAvatarThumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)
            try {
                publishFriendInvitationAck(
                    recipientPhone = cleanPhone,
                    recipientPubkeyHex = if (hasValidPubkey) parsed.senderPubkey else null,
                    avatarBase64 = myAvatarThumb
                )
                Log.i(TAG, "Handshake ACK automatique renvoyé avec succès à $cleanPhone (clé renouvelée=$isKeyRotated)")
            } catch (e: Exception) {
                Log.e(TAG, "Échec émission ACK automatique pour $cleanPhone: ${e.message}")
            }

            // 5. Si la clé a tourné (réinstallation de l'ami), notifier discrètement
            if (isKeyRotated) {
                val notif = com.sha.orbis.model.AppNotification(
                    id = "notif_rotated_${parsed.eventId.take(16)}",
                    title = keepName,
                    description = context.getString(R.string.notif_friend_key_rotated_desc),
                    timestamp = parsed.timestamp,
                    type = com.sha.orbis.model.NotificationType.FRIEND_REQUEST,
                    isRead = false,
                    senderPhone = cleanPhone,
                    senderName = keepName,
                    senderAvatarPath = savedAvatar,
                    actionType = "FRIEND_RECONNECTED"
                )
                notifRepo.addNotification(notif)
                com.sha.orbis.data.OrbisBadgeHub.refresh(context)
            }

            // 6. Diffusion broadcast pour rafraîchir l'écran actif
            val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS)
            context.sendBroadcast(intent)

            // 7. Rafraîchir les abonnements Nostr pour inclure immédiatement le fil et les stories de l'ami
            refreshSubscriptions()
            return
        }

        Log.i(TAG, "Nouvelle invitation d'ami Nostr reçue de '${parsed.senderName}' (${parsed.senderPhone})")

        // 1. Enregistrer dans FriendRequestRepository avec avatar sauvegardé sur disque
        val reqId = "req_nostr_${parsed.eventId.take(12)}"
        val request = FriendRequest(
            id = reqId,
            senderPhone = cleanPhone,
            senderName = parsed.senderName,
            senderAvatarPath = savedAvatar,
            senderPublicKey = parsed.senderPubkey,
            status = FriendRequestStatus.PENDING,
            direction = RequestDirection.RECEIVED,
            groupKey = parsed.groupKey,
            peerFcmToken = parsed.fcmToken,
            timestamp = parsed.timestamp
        )
        friendRequestRepo.addOrUpdate(request)

        // 2. Synchroniser le contact local
        val contacts = conversationRepo.loadContacts().toMutableList()
        val contactIdx = contacts.indexOfFirst {
            FriendRequestRepository.isSamePhone(it.phone, cleanPhone) ||
            (!parsed.senderPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.phone, parsed.senderPhone)) ||
            (it.publicKey.isNotBlank() && it.publicKey.equals(parsed.senderPubkey, ignoreCase = true))
        }
        if (contactIdx >= 0) {
            val existing = contacts[contactIdx]
            val keepName = if (existing.name.isNotBlank() && !existing.name.startsWith("+") && existing.name != "O R B I S net") {
                existing.name
            } else if (parsed.senderName.isNotBlank() && parsed.senderName != "O R B I S net") {
                parsed.senderName
            } else existing.name

            val keepPhone = if (existing.phone.any { it.isDigit() }) existing.phone else cleanPhone

            contacts[contactIdx] = existing.copy(
                publicKey = parsed.senderPubkey.ifBlank { existing.publicKey },
                phone = keepPhone,
                name = keepName,
                avatarPath = savedAvatar ?: existing.avatarPath
            )
        } else {
            contacts.add(0, Contact(
                id = "c_$avatarId",
                name = parsed.senderName,
                phone = cleanPhone,
                publicKey = parsed.senderPubkey,
                status = "Demande d'ami reçue",
                avatarPath = savedAvatar
            ))
        }
        conversationRepo.saveContacts(contacts)

        // 3. Déclencher notification système Android & InApp uniquement pour cette nouvelle demande
        SmsNotificationHelper.showInvitationNotification(
            context = context,
            senderName = parsed.senderName,
            senderPhone = cleanPhone
        )

        // Enregistrer dans la Cloche & rafraîchir le Launcher
        val notif = com.sha.orbis.model.AppNotification(
            id = "notif_invitation_${parsed.eventId.take(16)}",
            title = parsed.senderName,
            description = context.getString(R.string.notif_friend_request_received),
            timestamp = parsed.timestamp,
            type = com.sha.orbis.model.NotificationType.FRIEND_REQUEST,
            isRead = false,
            senderPhone = cleanPhone,
            senderName = parsed.senderName,
            senderAvatarPath = savedAvatar,
            actionType = "FRIEND_REQUEST"
        )
        notifRepo.addNotification(notif)
        com.sha.orbis.data.OrbisBadgeHub.refresh(context)

        // 4. Diffusion broadcast pour rafraîchir l'écran actif
        val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS)
        context.sendBroadcast(intent)
    }

    private fun handleInvitationAck(parsed: NostrProtocolEngine.ParsedInvitationAck) {
        val myPhone = sessionManager.userPhone.trim()
        val myPubkey = identityManager.publicKeyHex
        val myDigits = myPhone.filter { it.isDigit() }
        val myLast8 = myDigits.takeLast(8)
        val recDigits = parsed.recipientPhone.filter { it.isDigit() }

        if (parsed.responderPubkey.equals(myPubkey, ignoreCase = true)) return

        val recPub = parsed.recipientPubkey?.lowercase()
        val isPubMatch = !recPub.isNullOrBlank() && recPub == myPubkey.lowercase()
        val isForMe = isPubMatch ||
                      FriendRequestRepository.isSamePhone(parsed.recipientPhone, myPhone) ||
                      (myLast8.length >= 8 && recDigits.endsWith(myLast8)) ||
                      parsed.recipientPhone.isBlank()
        if (!isForMe) return

        val cleanPhone = parsed.responderPhone.ifBlank { Bech32.npubEncode(parsed.responderPubkey) }

        // 0. Déduplication persistante & vérification de l'état d'ami existant
        val isEventAlreadyProcessed = processedEventsRepo.isProcessed(parsed.eventId)
        val wasAlreadyFriend = friendRequestRepo.isFriend(cleanPhone) || friendRequestRepo.isConnectedContact(cleanPhone)

        val contacts = conversationRepo.loadContacts().toMutableList()
        val contactIdx = contacts.indexOfFirst {
            FriendRequestRepository.isSamePhone(it.phone, cleanPhone) ||
            (!parsed.responderPhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.phone, parsed.responderPhone)) ||
            (it.publicKey.isNotBlank() && it.publicKey.equals(parsed.responderPubkey, ignoreCase = true))
        }
        val existingContact = if (contactIdx >= 0) contacts[contactIdx] else null
        val oldKey = existingContact?.publicKey
        val hasValidPubkey = FriendRequestRepository.isValidNostrKey(parsed.responderPubkey)
        val isKeyPending = existingContact?.publicKey.isNullOrBlank() ||
                existingContact?.publicKey?.equals("PUB_KEY_PENDING", ignoreCase = true) == true ||
                existingContact?.publicKey?.equals("NPUB_PENDING", ignoreCase = true) == true
        val isKeyRotated = hasValidPubkey && !oldKey.isNullOrBlank() && !isKeyPending && !oldKey.equals(parsed.responderPubkey, ignoreCase = true)

        if (isEventAlreadyProcessed && wasAlreadyFriend && !isKeyPending && !isKeyRotated) {
            Log.d(TAG, "ACK d'invitation ${parsed.eventId.take(8)} déjà traité et ami déjà connecté avec clé à jour.")
            return
        }
        processedEventsRepo.markProcessed(parsed.eventId)

        Log.i(TAG, "Accusé de réception (ACK) d'invitation reçu de '${parsed.responderName}' (${parsed.responderPhone})")

        val cleanDigits = cleanPhone.filter { it.isDigit() }
        val avatarId = cleanDigits.ifBlank { parsed.responderPubkey.take(8) }
        val savedAvatar = if (!parsed.avatarBase64.isNullOrBlank()) {
            com.sha.orbis.ui.components.AvatarManager.saveAvatarFromBase64(context, parsed.avatarBase64, "avatar_$avatarId")
        } else null

        // 1. Confirmer l'amitié dans FriendRequestRepository
        friendRequestRepo.ensureAcceptedFriend(
            phone = cleanPhone,
            name = parsed.responderName,
            publicKey = parsed.responderPubkey,
            avatarPath = savedAvatar
        )
        if (parsed.responderPhone.isNotBlank() && parsed.responderPhone != cleanPhone) {
            friendRequestRepo.ensureAcceptedFriend(
                phone = parsed.responderPhone,
                name = parsed.responderName,
                publicKey = parsed.responderPubkey,
                avatarPath = savedAvatar
            )
        }
        if (!parsed.fcmToken.isNullOrBlank()) {
            friendRequestRepo.updatePeerFcmToken(cleanPhone, parsed.fcmToken)
            if (parsed.responderPhone.isNotBlank()) {
                friendRequestRepo.updatePeerFcmToken(parsed.responderPhone, parsed.fcmToken)
            }
        }

        // 2. Mettre à jour le contact en "Connecté 🛡️"
        val keepName = if (contactIdx >= 0) {
            val existing = contacts[contactIdx]
            if (existing.name.isNotBlank() && !existing.name.startsWith("+") && existing.name != "O R B I S net") {
                existing.name
            } else if (parsed.responderName.isNotBlank() && parsed.responderName != "O R B I S net") {
                parsed.responderName
            } else existing.name
        } else parsed.responderName

        val keepPhone = if (contactIdx >= 0 && contacts[contactIdx].phone.any { it.isDigit() }) contacts[contactIdx].phone else cleanPhone

        if (contactIdx >= 0) {
            val existing = contacts[contactIdx]
            contacts[contactIdx] = existing.copy(
                status = "Connecté 🛡️",
                publicKey = parsed.responderPubkey,
                phone = keepPhone,
                name = keepName,
                avatarPath = savedAvatar ?: existing.avatarPath
            )
        } else {
            contacts.add(0, Contact(
                id = "c_$avatarId",
                name = parsed.responderName,
                phone = cleanPhone,
                publicKey = parsed.responderPubkey,
                status = "Connecté 🛡️",
                avatarPath = savedAvatar
            ))
        }
        conversationRepo.saveContacts(contacts)

        // 2b. Automatically create/ensure the chat conversation in Discussions
        conversationRepo.ensureConversationForFriend(
            phone = cleanPhone,
            name = parsed.responderName,
            initialMessage = context.getString(R.string.friends_connected_last_msg)
        )

        // 3. Notification système confirmant la connexion
        if (!wasAlreadyFriend || isKeyPending) {
            SmsNotificationHelper.showSocialNotification(
                context = context,
                title = context.getString(R.string.friends_invite_ack_notif_title),
                text = context.getString(R.string.friends_invite_ack_notif_body, parsed.responderName)
            )

            // Enregistrer dans la Cloche & rafraîchir le Launcher
            val notif = com.sha.orbis.model.AppNotification(
                id = "notif_ack_${parsed.eventId.take(16)}",
                title = parsed.responderName,
                description = context.getString(R.string.notif_friend_request_accepted),
                timestamp = System.currentTimeMillis(),
                type = com.sha.orbis.model.NotificationType.FRIEND_REQUEST,
                isRead = false,
                senderPhone = cleanPhone,
                senderName = parsed.responderName,
                senderAvatarPath = savedAvatar,
                actionType = "FRIEND_ACCEPTED"
            )
            notifRepo.addNotification(notif)
            com.sha.orbis.data.OrbisBadgeHub.refresh(context)
        } else if (isKeyRotated) {
            val notif = com.sha.orbis.model.AppNotification(
                id = "notif_rotated_ack_${parsed.eventId.take(16)}",
                title = parsed.responderName,
                description = context.getString(R.string.notif_friend_key_rotated_desc),
                timestamp = System.currentTimeMillis(),
                type = com.sha.orbis.model.NotificationType.FRIEND_REQUEST,
                isRead = false,
                senderPhone = cleanPhone,
                senderName = parsed.responderName,
                senderAvatarPath = savedAvatar,
                actionType = "FRIEND_RECONNECTED"
            )
            notifRepo.addNotification(notif)
            com.sha.orbis.data.OrbisBadgeHub.refresh(context)
        }

        // 4. Diffusion broadcast pour rafraîchir la liste d'amis et discussions
        val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS)
        context.sendBroadcast(intent)

        // 5. Déclencher l'auto-synchronisation souveraine bilatérale avec le nouvel ami
        com.sha.orbis.sync.scheduler.SovereignSyncScheduler.onFriendAddedOrAccepted(
            context = context,
            peerPhone = cleanPhone,
            peerPubkey = parsed.responderPubkey
        )

        // 5. Rafraîchir immédiatement les abonnements Nostr pour inclure ce nouvel ami dans la Timeline et les Stories
        refreshSubscriptions()
    }

    /**
     * Publie une invitation d'ami sur les relais Nostr pour le numéro spécifié.
     */
    fun publishFriendInvitation(
        recipientPhone: String,
        groupKey: String,
        avatarBase64: String? = null
    ): NostrEvent {
        val myFcmToken = com.sha.orbis.call.fcm.OrbisFirebaseMessagingService.getSavedToken(context).takeIf { it.isNotBlank() }
        val event = NostrProtocolEngine.buildInvitationEvent(
            identityManager = identityManager,
            senderName = sessionManager.userName.ifBlank { "Utilisateur" },
            senderPhone = sessionManager.userPhone,
            recipientPhone = recipientPhone,
            groupKey = groupKey,
            avatarBase64 = avatarBase64,
            fcmToken = myFcmToken
        )
        relayPool.publish(event)
        return event
    }

    /**
     * Publie un accusé de réception d'invitation (ACK handshake) sur les relais Nostr.
     */
    fun publishFriendInvitationAck(
        recipientPhone: String,
        recipientPubkeyHex: String? = null,
        avatarBase64: String? = null
    ): NostrEvent {
        val myFcmToken = com.sha.orbis.call.fcm.OrbisFirebaseMessagingService.getSavedToken(context).takeIf { it.isNotBlank() }
        val event = NostrProtocolEngine.buildInvitationAckEvent(
            identityManager = identityManager,
            responderName = sessionManager.userName.ifBlank { "Utilisateur" },
            responderPhone = sessionManager.userPhone,
            recipientPhone = recipientPhone,
            recipientPubkeyHex = recipientPubkeyHex,
            avatarBase64 = avatarBase64,
            fcmToken = myFcmToken
        )
        relayPool.publish(event)
        return event
    }

    /**
     * Traite les métadonnées de profil reçues (Kind 0 NIP-01) pour un contact ou un ami.
     * Met à jour dynamiquement son pseudo, son avatar et sa bio sur le terminal de ses pairs.
     */
    private fun handleProfileMetadata(event: NostrEvent) {
        val parsed = NostrProtocolEngine.parseProfileMetadataEvent(event) ?: return
        val senderPubkey = event.pubkey
        val senderNpub = Bech32.npubEncode(senderPubkey)

        // Récupérer le numéro de téléphone depuis les tags ou les métadonnées
        val phoneFromTag = event.tags.find { it.size >= 2 && (it[0] == "phone" || it[0] == "phone_digits") }?.get(1)?.trim()
        val effectivePhone = parsed.phone?.takeIf { it.isNotBlank() } ?: phoneFromTag

        // Vérifier si l'auteur est bloqué
        if (blockedRepo.isBlocked(senderNpub) ||
            blockedRepo.isBlocked(senderPubkey) ||
            (!effectivePhone.isNullOrBlank() && blockedRepo.isBlocked(effectivePhone))) {
            Log.d(TAG, "Profil ignoré : auteur bloqué ($senderPubkey / $effectivePhone)")
            return
        }

        val contacts = conversationRepo.loadContacts().toMutableList()
        val friendRequests = friendRequestRepo.loadRequests().toMutableList()

        // Localiser le contact
        val matchedContactIdx = contacts.indexOfFirst {
            it.publicKey.equals(senderPubkey, ignoreCase = true) ||
            it.publicKey.equals(senderNpub, ignoreCase = true) ||
            it.phone == senderNpub ||
            (!effectivePhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.phone, effectivePhone))
        }

        val matchedRequestIdx = friendRequests.indexOfFirst {
            it.senderPublicKey.equals(senderPubkey, ignoreCase = true) ||
            it.senderPublicKey.equals(senderNpub, ignoreCase = true) ||
            (!effectivePhone.isNullOrBlank() && FriendRequestRepository.isSamePhone(it.senderPhone, effectivePhone))
        }

        val isFriend = (matchedRequestIdx >= 0 && friendRequests[matchedRequestIdx].status == FriendRequestStatus.ACCEPTED) ||
                (matchedContactIdx >= 0 && friendRequestRepo.isConnectedContact(contacts[matchedContactIdx].phone)) ||
                (!effectivePhone.isNullOrBlank() && friendRequestRepo.isFriend(effectivePhone))

        if (!isFriend && matchedContactIdx < 0 && matchedRequestIdx < 0) {
            Log.d(TAG, "Profil ignoré : l'auteur n'est ni un ami ni un contact ($senderPubkey)")
            return
        }

        val cleanPhoneCandidate = if (matchedContactIdx >= 0 && contacts[matchedContactIdx].phone.any { it.isDigit() }) {
            contacts[matchedContactIdx].phone
        } else (effectivePhone?.takeIf { it.any { c -> c.isDigit() } } ?: matchedRequestIdx.takeIf { it >= 0 }?.let { friendRequests[it].senderPhone } ?: senderNpub)

        val cleanDigits = cleanPhoneCandidate.filter { it.isDigit() }
        val avatarId = cleanDigits.ifBlank { senderPubkey.take(8) }

        // Sauvegarder l'avatar sur disque si fourni
        val savedAvatar = if (!parsed.picture.isNullOrBlank()) {
            com.sha.orbis.ui.components.AvatarManager.saveAvatarFromBase64(context, parsed.picture, "avatar_$avatarId")
        } else null

        val validNewName = if (parsed.name.isNotBlank() && !parsed.name.startsWith("+") && parsed.name != "Anonyme" && parsed.name != "O R B I S net") {
            parsed.name
        } else null

        var somethingChanged = false

        // Mise à jour du contact dans ConversationRepository
        if (matchedContactIdx >= 0) {
            val existing = contacts[matchedContactIdx]
            val updatedName = validNewName ?: existing.name
            val updatedAvatar = savedAvatar ?: existing.avatarPath
            val updatedBio = if (parsed.about.isNotBlank()) parsed.about else existing.bio

            if (existing.name != updatedName || existing.avatarPath != updatedAvatar || existing.bio != updatedBio) {
                contacts[matchedContactIdx] = existing.copy(
                    name = updatedName,
                    avatarPath = updatedAvatar,
                    bio = updatedBio,
                    publicKey = senderPubkey
                )
                conversationRepo.saveContacts(contacts)
                somethingChanged = true
                Log.d(TAG, "Contact mis à jour depuis Nostr Kind 0 : $updatedName")
            }
        }

        // Mise à jour dans FriendRequestRepository
        if (matchedRequestIdx >= 0) {
            val existingReq = friendRequests[matchedRequestIdx]
            val updatedReqName = validNewName ?: existingReq.senderName
            val updatedReqAvatar = savedAvatar ?: existingReq.senderAvatarPath

            if (existingReq.senderName != updatedReqName || existingReq.senderAvatarPath != updatedReqAvatar) {
                friendRequests[matchedRequestIdx] = existingReq.copy(
                    senderName = updatedReqName,
                    senderAvatarPath = updatedReqAvatar,
                    senderPublicKey = senderPubkey
                )
                friendRequestRepo.saveRequests(friendRequests)
                somethingChanged = true
                Log.d(TAG, "Demande d'ami mise à jour depuis Nostr Kind 0 : $updatedReqName")
            }
        } else if (cleanPhoneCandidate.isNotBlank() && isFriend) {
            friendRequestRepo.ensureAcceptedFriend(
                phone = cleanPhoneCandidate,
                name = validNewName ?: "",
                publicKey = senderPubkey,
                avatarPath = savedAvatar
            )
            somethingChanged = true
        }

        // Mise à jour des titres de discussions 1-to-1 actives
        if (validNewName != null) {
            val convs = conversationRepo.loadConversations().toMutableList()
            var convsModified = false
            convs.forEachIndexed { idx, conv ->
                if (!conv.isGroup && (conv.participants.any { it.equals(senderPubkey, true) || (cleanPhoneCandidate.isNotBlank() && FriendRequestRepository.isSamePhone(it, cleanPhoneCandidate)) } || (cleanDigits.length >= 6 && conv.id.contains(cleanDigits.takeLast(8))))) {
                    if (conv.title != validNewName) {
                        convs[idx] = conv.copy(title = validNewName)
                        convsModified = true
                    }
                }
            }
            if (convsModified) {
                conversationRepo.saveConversations(convs)
                somethingChanged = true
            }
        }

        if (somethingChanged) {
            val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                putExtra(OrbisEventBus.EXTRA_CONV_ID, "all_convs")
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    /**
     * Publie les métadonnées de profil actuelles de l'utilisateur (Kind 0 NIP-01)
     * sur les relais Nostr pour que tous ses amis reçoivent immédiatement le pseudo et l'avatar actualisés.
     */
    fun publishProfileUpdate(
        displayName: String? = null,
        bio: String? = null,
        avatarPath: String? = null
    ): NostrEvent? {
        val myName = displayName ?: sessionManager.userName.ifBlank { "Utilisateur" }
        val myBio = bio ?: sessionManager.userBio
        val myAvatarPath = avatarPath ?: sessionManager.userAvatarPath
        val avatarThumbBase64 = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(myAvatarPath, 96)

        val myPhone = sessionManager.userPhone

        val event = NostrProtocolEngine.buildProfileMetadataEvent(
            identityManager = identityManager,
            displayName = myName,
            bio = myBio,
            avatarBase64OrUrl = avatarThumbBase64,
            phone = myPhone
        )

        // Ajouter les p-tags pour notifier tous les amis acceptés
        val extraTags = event.tags.toMutableList()
        val friendKeys = getFriendPubkeys()
        friendKeys?.forEach { k ->
            if (k != identityManager.publicKeyHex && !extraTags.any { it.size >= 2 && it[0] == "p" && it[1].equals(k, true) }) {
                extraTags.add(listOf("p", k))
            }
        }

        val finalEvent = identityManager.signEvent(
            kind = NostrEvent.KIND_METADATA,
            tags = extraTags,
            content = event.content
        )

        relayPool.publish(finalEvent)
        Log.d(TAG, "Profil publié sur Nostr (Kind 0) pour $myName (avatar=${!avatarThumbBase64.isNullOrBlank()})")
        return finalEvent
    }
}
