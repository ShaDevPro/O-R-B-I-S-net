package com.sha.orbis.sync.security

import android.content.Context
import com.sha.orbis.model.CallRecord
import com.sha.orbis.model.Message
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialStory
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.SocialRepository

/**
 * Moteur souverain d'application des politiques de sécurité et de confidentialité OrbisNet.
 * Garantit de façon hermétique et inviolable :
 * 1. Aucun échange avec les contacts bloqués.
 * 2. Échange restreint aux amis acceptés (amis / amis).
 * 3. Respect absolu des cercles (Famille, Proches, Cercles personnalisés).
 * 4. Respect absolu des exclusions sélectives (téléphones ou cercles exclus).
 * 5. Herméticité absolue des messages et appels : seuls les éléments échangés entre ces DEUX pairs sont transmis.
 */
object SovereignSyncSecurityPolicy {

    /**
     * Vérifie si l'utilisateur local est autorisé à synchroniser avec ce pair.
     */
    fun canSyncWithPeer(
        context: Context,
        peerPhone: String,
        peerPubkey: String? = null
    ): Boolean {
        if (peerPhone.isBlank() && peerPubkey.isNullOrBlank()) return false

        val blockedRepo = BlockedContactsRepository(context)
        if (blockedRepo.isBlocked(peerPhone) || (!peerPubkey.isNullOrBlank() && blockedRepo.isBlocked(peerPubkey))) {
            return false
        }

        val friendRepo = FriendRequestRepository(context)
        val isFriend = friendRepo.isFriend(peerPhone) ||
            (!peerPubkey.isNullOrBlank() && friendRepo.isFriend(peerPubkey))

        return isFriend
    }

    /**
     * Filtre les publications sociales pour un pair spécifique en appliquant
     * rigoureusement les cercles et listes d'exclusion.
     */
    fun filterPostsForPeer(
        context: Context,
        posts: List<SocialPost>,
        peerPhone: String,
        myPhone: String
    ): List<SocialPost> {
        if (peerPhone.isBlank()) return emptyList()

        val socialRepo = SocialRepository(context)
        val circleRepo = FriendCircleRepository(context)
        val familyMembers = circleRepo.getFamilyMembers()
        val allCircles = circleRepo.loadCircles()

        val isPeerInFamily = familyMembers.any { FriendRequestRepository.isSamePhone(it, peerPhone) }

        return posts.filter { post ->
            // 1. Post supprimé localement ? Ignorer
            if (socialRepo.isPostDeleted(post.id)) {
                return@filter false
            }

            // 2. Annonces officielles globales : toujours autorisées
            if (post.isOfficialAnnouncement) {
                return@filter true
            }

            // 3. Exclusions sélectives par numéro
            if (post.excludedPhones.any { FriendRequestRepository.isSamePhone(it, peerPhone) }) {
                return@filter false
            }

            // 4. Exclusions sélectives par cercle famille
            if (post.excludedCircleIds.contains("circle_family") && isPeerInFamily) {
                return@filter false
            }

            // 5. Exclusions sélectives par autres cercles personnalisés
            if (post.excludedCircleIds.any { excludedId ->
                    val circle = allCircles.find { it.id == excludedId }
                    circle?.memberPhones?.any { FriendRequestRepository.isSamePhone(it, peerPhone) } == true
                }) {
                return@filter false
            }

            // 6. Restriction de cercle cible
            val targetCircle = post.targetCircleId
            if (targetCircle != null && targetCircle.isNotBlank()) {
                if (targetCircle == "circle_family") {
                    if (!isPeerInFamily) return@filter false
                } else {
                    val circle = allCircles.find { it.id == targetCircle }
                    val isMember = circle?.memberPhones?.any { FriendRequestRepository.isSamePhone(it, peerPhone) } == true
                    if (!isMember) return@filter false
                }
            }

            true
        }
    }

    /**
     * Filtre les stories actives (24h) pour un pair spécifique avec les mêmes règles de confidentialité.
     */
    fun filterStoriesForPeer(
        context: Context,
        stories: List<SocialStory>,
        peerPhone: String,
        myPhone: String
    ): List<SocialStory> {
        if (peerPhone.isBlank()) return emptyList()

        val now = System.currentTimeMillis()
        val circleRepo = FriendCircleRepository(context)
        val familyMembers = circleRepo.getFamilyMembers()
        val allCircles = circleRepo.loadCircles()
        val isPeerInFamily = familyMembers.any { FriendRequestRepository.isSamePhone(it, peerPhone) }

        return stories.filter { story ->
            // 1. Story expirée ?
            if (story.expiresAt <= now) {
                return@filter false
            }

            // 2. Exclusions sélectives par numéro
            if (story.excludedPhones.any { FriendRequestRepository.isSamePhone(it, peerPhone) }) {
                return@filter false
            }

            // 3. Exclusions sélectives par cercle famille
            if (story.excludedCircleIds.contains("circle_family") && isPeerInFamily) {
                return@filter false
            }

            // 4. Exclusions sélectives par autres cercles
            if (story.excludedCircleIds.any { excludedId ->
                    val circle = allCircles.find { it.id == excludedId }
                    circle?.memberPhones?.any { FriendRequestRepository.isSamePhone(it, peerPhone) } == true
                }) {
                return@filter false
            }

            // 5. Restriction de cercle cible
            val targetCircle = story.targetCircleId
            if (targetCircle != null && targetCircle.isNotBlank()) {
                if (targetCircle == "circle_family") {
                    if (!isPeerInFamily) return@filter false
                } else {
                    val circle = allCircles.find { it.id == targetCircle }
                    val isMember = circle?.memberPhones?.any { FriendRequestRepository.isSamePhone(it, peerPhone) } == true
                    if (!isMember) return@filter false
                }
            }

            true
        }
    }

    /**
     * Filtre hermétiquement les messages de discussion : seuls les messages échangés
     * entre l'utilisateur local et CE contact spécifique sont autorisés.
     */
    fun filterMessagesForPeer(
        messages: List<Message>,
        peerPhone: String,
        myPhone: String
    ): List<Message> {
        val cleanPeerDigits = peerPhone.filter { it.isDigit() }
        val cleanMyDigits = myPhone.filter { it.isDigit() }

        return messages.filter { msg ->
            if (msg.isDeletedForEveryone) return@filter false
            if (com.sha.orbis.storage.LocalMessageStore.isSpamOrSyncMessage(msg)) return@filter false

            val isSenderMe = msg.senderId == "me" ||
                (cleanMyDigits.length >= 6 && FriendRequestRepository.isSamePhone(msg.senderId, myPhone))
            val isSenderPeer = (cleanPeerDigits.length >= 6 && FriendRequestRepository.isSamePhone(msg.senderId, peerPhone)) ||
                msg.senderId.contains(peerPhone, ignoreCase = true)

            val convMatches = msg.conversationId.contains(cleanPeerDigits.takeLast(8)) ||
                FriendRequestRepository.isSamePhone(msg.conversationId.removePrefix("conv_"), peerPhone)

            (isSenderMe || isSenderPeer) && convMatches
        }
    }

    /**
     * Filtre hermétiquement le journal d'appels : seuls les appels échangés avec ce contact
     * spécifique sont autorisés.
     */
    fun filterCallsForPeer(
        calls: List<CallRecord>,
        peerPhone: String
    ): List<CallRecord> {
        return calls.filter { call ->
            FriendRequestRepository.isSamePhone(call.peerPhone, peerPhone)
        }
    }

    /**
     * Filtre hermétiquement les groupes de discussion partagés :
     * Seuls les groupes dont le pair est membre effectif peuvent être transmis.
     */
    fun filterGroupConversationsForPeer(
        conversations: List<com.sha.orbis.model.Conversation>,
        peerPhone: String
    ): List<com.sha.orbis.model.Conversation> {
        if (peerPhone.isBlank()) return emptyList()
        return conversations.filter { conv ->
            conv.isGroup && conv.participants.any { FriendRequestRepository.isSamePhone(it, peerPhone) }
        }
    }

    /**
     * Détermine les cercles auxquels le pair appartient dans le référentiel local de l'utilisateur.
     * Ex: Si le pair est dans le cercle Famille de l'utilisateur émetteur, "circle_family" sera inclus.
     */
    fun getMutualCircleMemberships(
        context: Context,
        peerPhone: String
    ): List<String> {
        if (peerPhone.isBlank()) return emptyList()
        val circleRepo = FriendCircleRepository(context)
        val memberships = mutableListOf<String>()
        if (circleRepo.isFamilyMember(peerPhone)) {
            memberships.add("circle_family")
        }
        val allCircles = circleRepo.loadCircles()
        for (circle in allCircles) {
            if (circle.id != "circle_family" && circle.memberPhones.any { FriendRequestRepository.isSamePhone(it, peerPhone) }) {
                memberships.add(circle.id)
            }
        }
        return memberships
    }
}
