package com.sha.orbis.nostr.service

import android.content.Context
import com.sha.orbis.nostr.identity.NostrIdentityManager
import com.sha.orbis.nostr.model.NostrEvent
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialStory
import com.sha.orbis.storage.FriendRequestRepository

/**
 * OrbisNet — NostrSyncManager (Public Stub)
 *
 * This is a compilable public stub of the OrbisNet Nostr orchestration layer.
 * The proprietary implementation handles relay pool management, event routing,
 * end-to-end encryption, delivery receipts, and multi-account sync.
 *
 * Method signatures are intentionally public to allow forks to compile.
 * The actual implementation is not distributed under this license.
 *
 * Licensed under BUSL-1.1 — © 2024 S.H.A Dev / ShaDevPro
 * See LICENSE for terms.
 */
class NostrSyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NostrSyncManager"

        @Volatile
        private var INSTANCE: NostrSyncManager? = null

        fun getInstance(context: Context): NostrSyncManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NostrSyncManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── Internal dependencies ─────────────────────────────────────────────────
    internal val identityManager: NostrIdentityManager by lazy {
        NostrIdentityManager.getInstance(context)
    }
    internal val friendRequestRepo: FriendRequestRepository by lazy {
        FriendRequestRepository(context)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() { /* proprietary */ }

    fun reconnect(force: Boolean = false) { /* proprietary */ }

    fun refreshSubscriptions(forceNetworkQuery: Boolean = false) { /* proprietary */ }

    fun fetchFreshTimeline() { /* proprietary */ }

    // ── Presence ──────────────────────────────────────────────────────────────

    fun broadcastPresence(isOnline: Boolean) { /* proprietary */ }

    // ── Messaging ─────────────────────────────────────────────────────────────

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
        senderPhone: String? = null,
        skipFcm: Boolean = false
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun sendDeliveryReceipt(
        recipientPubKeyHex: String,
        conversationId: String,
        messageId: String,
        status: String = "DELIVERED"
    ) { /* proprietary */ }

    fun publishReconnectionSignal(): NostrEvent? = null

    // ── Calls ─────────────────────────────────────────────────────────────────

    fun sendCallSignal(
        recipientNpubOrHex: String,
        callId: String,
        signalType: String,
        payloadJson: org.json.JSONObject
    ): Boolean = false /* proprietary */

    // ── Social — Posts ────────────────────────────────────────────────────────

    fun publishPost(post: SocialPost): NostrEvent =
        error("OrbisNet proprietary core — not available in public build")

    fun publishDeletePost(postId: String): NostrEvent =
        error("OrbisNet proprietary core — not available in public build")

    fun publishReaction(
        postId: String,
        postAuthorNpubOrHex: String,
        emoji: String = "❤️",
        senderName: String? = null,
        senderPhone: String? = null,
        senderAvatarBase64: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun publishPollVote(
        postId: String,
        optionId: String,
        postAuthorNpubOrHex: String
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    // ── Social — Comments ─────────────────────────────────────────────────────

    fun publishComment(
        postId: String,
        postAuthorNpubOrHex: String,
        comment: SocialComment
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun publishDeleteComment(postId: String, commentId: String): NostrEvent =
        error("OrbisNet proprietary core — not available in public build")

    fun publishCommentReaction(
        postId: String,
        commentId: String,
        postAuthorNpubOrHex: String,
        emoji: String = "❤️",
        senderName: String? = null,
        senderPhone: String? = null,
        senderAvatarBase64: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    // ── Social — Stories ──────────────────────────────────────────────────────

    fun publishStory(story: SocialStory): NostrEvent =
        error("OrbisNet proprietary core — not available in public build")

    fun publishStoryView(story: SocialStory): NostrEvent? = null

    fun publishStoryReaction(
        storyId: String,
        storyAuthorPubkey: String?,
        emoji: String
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun publishDeleteStory(storyId: String): NostrEvent =
        error("OrbisNet proprietary core — not available in public build")

    // ── Friends ───────────────────────────────────────────────────────────────

    fun publishFriendInvitation(
        recipientPhone: String,
        groupKey: String,
        avatarBase64: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun publishFriendInvitationAck(
        recipientPhone: String,
        recipientPubkeyHex: String? = null,
        avatarBase64: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    // ── Profile ───────────────────────────────────────────────────────────────

    fun publishProfileUpdate(
        displayName: String? = null,
        bio: String? = null,
        avatarPath: String? = null
    ): NostrEvent? = null
}
