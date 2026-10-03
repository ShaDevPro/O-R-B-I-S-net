package com.sha.orbis.social

import android.content.Context
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.FriendRequestRepository

/**
 * Enforces strict sovereign peer-to-peer privacy policies for social interactions
 * (Comments, Reactions, Poll Votes, Story Reactions).
 *
 * Fundamental principle:
 * In Orbis, social engagement is sovereign and peer-to-peer:
 * Only authenticated friends (plus the post author / current user / verified founder-dev)
 * are permitted to interact, view, and be counted in social engagements.
 * Strangers, non-friends, and excluded users are completely filtered out at both the network
 * ingestion layer (NostrSyncManager) and the UI presentation layer.
 */
object SocialEngagementAudiencePolicy {

    /**
     * Determines whether an incoming comment is authorized to be ingested and persisted from the network.
     */
    fun isCommentAuthorizedForIngestion(
        commentAuthorPhone: String,
        commentAuthorPubkeyHex: String?,
        parentPost: SocialPost?,
        currentUserPhone: String,
        currentUserPubkeyHex: String?,
        friendRepo: FriendRequestRepository,
        blockedRepo: BlockedContactsRepository
    ): Boolean {
        val phone = commentAuthorPhone.trim()
        val pubkey = commentAuthorPubkeyHex?.trim()?.lowercase()

        // 1. Blocked users are immediately rejected
        if (phone.isNotBlank() && blockedRepo.isBlocked(phone)) return false
        if (!pubkey.isNullOrBlank() && blockedRepo.isBlocked(pubkey)) return false

        // 2. Comments from the current user are always accepted
        if (currentUserPhone.isNotBlank() && phone.isNotBlank() && FriendRequestRepository.isSamePhone(phone, currentUserPhone)) return true
        if (!pubkey.isNullOrBlank() && !currentUserPubkeyHex.isNullOrBlank() && pubkey.equals(currentUserPubkeyHex, ignoreCase = true)) return true

        // 3. Comments from Founder Dev / Admin are always accepted (official core announcements / support)
        if (phone.isNotBlank() && AdminSecurityHelper.isAdmin(phone)) return true
        if (!pubkey.isNullOrBlank() && AdminSecurityHelper.isAdmin(pubkey)) return true

        // 4. Parent post exclusions
        if (parentPost != null) {
            // Commenter is explicitly excluded from this post
            if (parentPost.excludedPhones.any { FriendRequestRepository.isSamePhone(it, phone) }) return false
            // Current user is explicitly excluded from this post
            if (parentPost.excludedPhones.any { FriendRequestRepository.isSamePhone(it, currentUserPhone) }) return false
        }

        // 5. Host of the parent post commenting on their own post
        if (parentPost != null) {
            if (phone.isNotBlank() && FriendRequestRepository.isSamePhone(parentPost.authorPhone, phone)) return true
            if (!pubkey.isNullOrBlank() && !parentPost.authorPubkey.isNullOrBlank() &&
                parentPost.authorPubkey.equals(pubkey, ignoreCase = true)
            ) return true
        }

        // 6. Accepted friend or connected contact of the current user
        if (phone.isNotBlank() && (friendRepo.isFriend(phone) || friendRepo.isConnectedContact(phone))) return true
        if (!pubkey.isNullOrBlank() && (friendRepo.isFriend(pubkey) || friendRepo.isConnectedContact(pubkey))) return true

        return false
    }

    /**
     * Determines whether an incoming reaction is authorized to be ingested and persisted from the network.
     */
    fun isReactionAuthorizedForIngestion(
        reactorIdentifier: String,
        reactorPhoneTag: String?,
        reactorPubkeyHex: String?,
        parentPost: SocialPost?,
        currentUserPhone: String,
        currentUserPubkeyHex: String?,
        friendRepo: FriendRequestRepository,
        blockedRepo: BlockedContactsRepository
    ): Boolean {
        val id = reactorIdentifier.trim()
        val phone = reactorPhoneTag?.trim() ?: id
        val pubkey = reactorPubkeyHex?.trim()?.lowercase()

        // 1. Blocked check
        if (id.isNotBlank() && blockedRepo.isBlocked(id)) return false
        if (phone.isNotBlank() && blockedRepo.isBlocked(phone)) return false
        if (!pubkey.isNullOrBlank() && blockedRepo.isBlocked(pubkey)) return false

        // 2. From current user
        if (currentUserPhone.isNotBlank() && (
            (id.isNotBlank() && FriendRequestRepository.isSamePhone(id, currentUserPhone)) ||
            (phone.isNotBlank() && FriendRequestRepository.isSamePhone(phone, currentUserPhone))
        )) return true
        if (!pubkey.isNullOrBlank() && !currentUserPubkeyHex.isNullOrBlank() && pubkey.equals(currentUserPubkeyHex, ignoreCase = true)) return true

        // 3. Founder Dev / Admin
        if (AdminSecurityHelper.isAdmin(id) || AdminSecurityHelper.isAdmin(phone)) return true
        if (!pubkey.isNullOrBlank() && AdminSecurityHelper.isAdmin(pubkey)) return true

        // 4. Exclusions
        if (parentPost != null) {
            if (parentPost.excludedPhones.any { FriendRequestRepository.isSamePhone(it, id) || FriendRequestRepository.isSamePhone(it, phone) }) return false
            if (parentPost.excludedPhones.any { FriendRequestRepository.isSamePhone(it, currentUserPhone) }) return false
        }

        // 5. Host of post
        if (parentPost != null) {
            if (FriendRequestRepository.isSamePhone(parentPost.authorPhone, id) ||
                FriendRequestRepository.isSamePhone(parentPost.authorPhone, phone)
            ) return true
            if (!pubkey.isNullOrBlank() && !parentPost.authorPubkey.isNullOrBlank() &&
                parentPost.authorPubkey.equals(pubkey, ignoreCase = true)
            ) return true
        }

        // 6. Accepted friend or connected contact
        if (id.isNotBlank() && (friendRepo.isFriend(id) || friendRepo.isConnectedContact(id))) return true
        if (phone.isNotBlank() && (friendRepo.isFriend(phone) || friendRepo.isConnectedContact(phone))) return true
        if (!pubkey.isNullOrBlank() && (friendRepo.isFriend(pubkey) || friendRepo.isConnectedContact(pubkey))) return true

        return false
    }

    /**
     * Determines whether a comment is visible to [viewerPhone] in the UI.
     */
    fun isCommentVisible(
        comment: SocialComment,
        post: SocialPost?,
        viewerPhone: String,
        friendRepo: FriendRequestRepository,
        blockedRepo: BlockedContactsRepository
    ): Boolean {
        val authorPhone = comment.authorPhone.trim()
        if (authorPhone.isBlank()) return false
        if (blockedRepo.isBlocked(authorPhone)) return false

        // Exclusions from parent post
        if (post != null && post.excludedPhones.any { FriendRequestRepository.isSamePhone(it, authorPhone) }) {
            return false
        }

        // Viewer always sees their own comments
        if (viewerPhone.isNotBlank() && FriendRequestRepository.isSamePhone(authorPhone, viewerPhone)) {
            return true
        }

        // Founder Dev / Admin comments are always visible
        if (AdminSecurityHelper.isAdmin(authorPhone)) {
            return true
        }

        // Host of the post commenting on their own thread
        if (post != null && FriendRequestRepository.isSamePhone(authorPhone, post.authorPhone)) {
            return true
        }

        // Accepted friend of viewer
        if (friendRepo.isFriend(authorPhone) || friendRepo.isConnectedContact(authorPhone)) {
            return true
        }

        return false
    }

    /**
     * Determines whether a reaction is visible to [viewerPhone] in the UI.
     */
    fun isReactionVisible(
        reaction: SocialReaction,
        post: SocialPost?,
        viewerPhone: String,
        friendRepo: FriendRequestRepository,
        blockedRepo: BlockedContactsRepository
    ): Boolean {
        val reactorPhone = reaction.userPhone.trim()
        if (reactorPhone.isBlank()) return false
        if (blockedRepo.isBlocked(reactorPhone)) return false

        // Exclusions from parent post
        if (post != null && post.excludedPhones.any { FriendRequestRepository.isSamePhone(it, reactorPhone) }) {
            return false
        }

        // Viewer always sees their own reactions
        if (viewerPhone.isNotBlank() && FriendRequestRepository.isSamePhone(reactorPhone, viewerPhone)) {
            return true
        }

        // Founder Dev / Admin reactions are always visible
        if (AdminSecurityHelper.isAdmin(reactorPhone)) {
            return true
        }

        // Host of the post reacting to their own thread
        if (post != null && FriendRequestRepository.isSamePhone(reactorPhone, post.authorPhone)) {
            return true
        }

        // Accepted friend of viewer
        if (friendRepo.isFriend(reactorPhone) || friendRepo.isConnectedContact(reactorPhone)) {
            return true
        }

        return false
    }

    /**
     * Filters a list of comments for a given post, ensuring only sovereign visible comments
     * are retained, and also filtering the reactions on each comment.
     */
    fun filterVisibleComments(
        comments: List<SocialComment>,
        post: SocialPost?,
        viewerPhone: String,
        friendRepo: FriendRequestRepository,
        blockedRepo: BlockedContactsRepository
    ): List<SocialComment> {
        return comments.filter { comment ->
            isCommentVisible(comment, post, viewerPhone, friendRepo, blockedRepo)
        }.map { comment ->
            if (comment.reactions.isEmpty()) {
                comment
            } else {
                val filteredReactions = comment.reactions.filter { reaction ->
                    isReactionVisible(reaction, post, viewerPhone, friendRepo, blockedRepo)
                }
                comment.copy(reactions = filteredReactions)
            }
        }
    }

    /**
     * Overload accepting Context for convenient UI usage.
     */
    fun filterVisibleComments(
        comments: List<SocialComment>,
        post: SocialPost?,
        viewerPhone: String,
        context: Context,
        accountId: String? = null
    ): List<SocialComment> {
        val targetAccountId = accountId?.takeIf { it.isNotBlank() } ?: try {
            SessionManager(context).activeAccountId
        } catch (_: Exception) { "" }
        val friendRepo = FriendRequestRepository(context, targetAccountId)
        val blockedRepo = BlockedContactsRepository(context, targetAccountId)
        return filterVisibleComments(comments, post, viewerPhone, friendRepo, blockedRepo)
    }

    /**
     * Filters a list of reactions for a given post, ensuring only sovereign visible reactions
     * are retained.
     */
    fun filterVisibleReactions(
        reactions: List<SocialReaction>,
        post: SocialPost?,
        viewerPhone: String,
        friendRepo: FriendRequestRepository,
        blockedRepo: BlockedContactsRepository
    ): List<SocialReaction> {
        return reactions.filter { reaction ->
            isReactionVisible(reaction, post, viewerPhone, friendRepo, blockedRepo)
        }
    }

    /**
     * Overload accepting Context for convenient UI usage.
     */
    fun filterVisibleReactions(
        reactions: List<SocialReaction>,
        post: SocialPost?,
        viewerPhone: String,
        context: Context,
        accountId: String? = null
    ): List<SocialReaction> {
        val targetAccountId = accountId?.takeIf { it.isNotBlank() } ?: try {
            SessionManager(context).activeAccountId
        } catch (_: Exception) { "" }
        val friendRepo = FriendRequestRepository(context, targetAccountId)
        val blockedRepo = BlockedContactsRepository(context, targetAccountId)
        return filterVisibleReactions(reactions, post, viewerPhone, friendRepo, blockedRepo)
    }

    /**
     * Determines whether an incoming story reaction is authorized to be ingested and persisted.
     */
    fun isStoryReactionAuthorizedForIngestion(
        reactorIdentifier: String,
        reactorPhoneTag: String?,
        reactorPubkeyHex: String?,
        story: SocialStory?,
        currentUserPhone: String,
        currentUserPubkeyHex: String?,
        friendRepo: FriendRequestRepository,
        blockedRepo: BlockedContactsRepository
    ): Boolean {
        val id = reactorIdentifier.trim()
        val phone = reactorPhoneTag?.trim() ?: id
        val pubkey = reactorPubkeyHex?.trim()?.lowercase()

        // 1. Blocked check
        if (id.isNotBlank() && blockedRepo.isBlocked(id)) return false
        if (phone.isNotBlank() && blockedRepo.isBlocked(phone)) return false
        if (!pubkey.isNullOrBlank() && blockedRepo.isBlocked(pubkey)) return false

        // 2. From current user
        if (currentUserPhone.isNotBlank() && (
            (id.isNotBlank() && FriendRequestRepository.isSamePhone(id, currentUserPhone)) ||
            (phone.isNotBlank() && FriendRequestRepository.isSamePhone(phone, currentUserPhone))
        )) return true
        if (!pubkey.isNullOrBlank() && !currentUserPubkeyHex.isNullOrBlank() && pubkey.equals(currentUserPubkeyHex, ignoreCase = true)) return true

        // 3. Founder Dev / Admin
        if (AdminSecurityHelper.isAdmin(id) || AdminSecurityHelper.isAdmin(phone)) return true
        if (!pubkey.isNullOrBlank() && AdminSecurityHelper.isAdmin(pubkey)) return true

        // 4. Exclusions from story
        if (story != null) {
            if (story.excludedPhones.any { FriendRequestRepository.isSamePhone(it, id) || FriendRequestRepository.isSamePhone(it, phone) }) return false
            if (story.excludedPhones.any { FriendRequestRepository.isSamePhone(it, currentUserPhone) }) return false
        }

        // 5. Host of story
        if (story != null) {
            if (FriendRequestRepository.isSamePhone(story.authorPhone, id) ||
                FriendRequestRepository.isSamePhone(story.authorPhone, phone)
            ) return true
            if (!pubkey.isNullOrBlank() && !story.authorPubkey.isNullOrBlank() &&
                story.authorPubkey.equals(pubkey, ignoreCase = true)
            ) return true
        }

        // 6. Accepted friend or connected contact
        if (id.isNotBlank() && (friendRepo.isFriend(id) || friendRepo.isConnectedContact(id))) return true
        if (phone.isNotBlank() && (friendRepo.isFriend(phone) || friendRepo.isConnectedContact(phone))) return true
        if (!pubkey.isNullOrBlank() && (friendRepo.isFriend(pubkey) || friendRepo.isConnectedContact(pubkey))) return true

        return false
    }

    /**
     * Determines whether a story reaction is visible to [viewerPhone] in the UI.
     */
    fun isStoryReactionVisible(
        reaction: SocialReaction,
        story: SocialStory?,
        viewerPhone: String,
        friendRepo: FriendRequestRepository,
        blockedRepo: BlockedContactsRepository
    ): Boolean {
        val reactorPhone = reaction.userPhone.trim()
        if (reactorPhone.isBlank()) return false
        if (blockedRepo.isBlocked(reactorPhone)) return false

        if (story != null && story.excludedPhones.any { FriendRequestRepository.isSamePhone(it, reactorPhone) }) {
            return false
        }

        if (viewerPhone.isNotBlank() && FriendRequestRepository.isSamePhone(reactorPhone, viewerPhone)) {
            return true
        }

        if (AdminSecurityHelper.isAdmin(reactorPhone)) {
            return true
        }

        if (story != null && FriendRequestRepository.isSamePhone(reactorPhone, story.authorPhone)) {
            return true
        }

        if (friendRepo.isFriend(reactorPhone) || friendRepo.isConnectedContact(reactorPhone)) {
            return true
        }

        return false
    }

    /**
     * Filters a list of story reactions, ensuring only sovereign visible reactions
     * are retained.
     */
    fun filterVisibleStoryReactions(
        reactions: List<SocialReaction>,
        story: SocialStory?,
        viewerPhone: String,
        friendRepo: FriendRequestRepository,
        blockedRepo: BlockedContactsRepository
    ): List<SocialReaction> {
        return reactions.filter { reaction ->
            isStoryReactionVisible(reaction, story, viewerPhone, friendRepo, blockedRepo)
        }
    }

    /**
     * Overload accepting Context for convenient UI usage.
     */
    fun filterVisibleStoryReactions(
        reactions: List<SocialReaction>,
        story: SocialStory?,
        viewerPhone: String,
        context: Context,
        accountId: String? = null
    ): List<SocialReaction> {
        val targetAccountId = accountId?.takeIf { it.isNotBlank() } ?: try {
            SessionManager(context).activeAccountId
        } catch (_: Exception) { "" }
        val friendRepo = FriendRequestRepository(context, targetAccountId)
        val blockedRepo = BlockedContactsRepository(context, targetAccountId)
        return filterVisibleStoryReactions(reactions, story, viewerPhone, friendRepo, blockedRepo)
    }
}
