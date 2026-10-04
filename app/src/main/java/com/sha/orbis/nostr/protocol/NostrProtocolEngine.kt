package com.sha.orbis.nostr.protocol

import android.content.Context
import com.sha.orbis.nostr.crypto.Secp256k1
import com.sha.orbis.nostr.identity.NostrIdentityManager
import com.sha.orbis.nostr.model.NostrEvent
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialStory
import org.json.JSONObject

/**
 * OrbisNet — NostrProtocolEngine (Public Stub)
 *
 * Compilable public stub of the OrbisNet Nostr protocol engine.
 * The proprietary implementation handles bidirectional conversion between
 * OrbisNet business models (chat, feed, profiles, contacts, calls) and
 * normalized Nostr events (NIP-01, NIP-04, NIP-25, NIP-57, etc.).
 *
 * All data classes and function signatures are intentionally public to allow
 * forks to compile. The actual cryptographic and relay logic is not distributed
 * under this license.
 *
 * Licensed under BUSL-1.1 — © 2024 S.H.A Dev / ShaDevPro
 * See LICENSE for terms.
 */
object NostrProtocolEngine {

    private const val TAG = "NostrProtocolEngine"
    const val TAG_ORBISNET = "orbisnet"
    const val TAG_ORBISNET_STORY = "orbisnet-story"

    // ── Data classes ──────────────────────────────────────────────────────────

    data class ParsedChatMessage(
        val messageId: String,
        val conversationId: String,
        val senderPubkey: String,
        val text: String,
        val timestamp: Long,
        val audioBase64: String? = null,
        val locationPayload: String? = null,
        val ephemeralTimerMs: Long = 0L,
        val senderAvatarBase64: String? = null,
        val senderName: String? = null,
        val senderPhone: String? = null
    )

    data class ParsedDeliveryReceipt(
        val messageId: String,
        val conversationId: String,
        val status: String,
        val senderPubkey: String,
        val timestamp: Long
    )

    data class NostrProfile(
        val name: String,
        val about: String,
        val picture: String? = null,
        val phone: String? = null
    )

    data class ParsedInvitation(
        val eventId: String,
        val senderPubkey: String,
        val senderName: String,
        val senderPhone: String,
        val recipientPhone: String,
        val groupKey: String,
        val avatarBase64: String? = null,
        val fcmToken: String? = null,
        val timestamp: Long
    )

    data class ParsedInvitationAck(
        val eventId: String,
        val responderPubkey: String,
        val responderName: String,
        val responderPhone: String,
        val recipientPhone: String,
        val recipientPubkey: String? = null,
        val avatarBase64: String? = null,
        val fcmToken: String? = null,
        val timestamp: Long
    )

    // ── Flow 1: Direct messages & delivery receipts ───────────────────────────

    fun buildChatMessageEvent(
        identityManager: NostrIdentityManager,
        recipientPubKeyHex: String,
        conversationId: String,
        plainText: String,
        messageId: String = "msg_${System.currentTimeMillis()}",
        audioBase64: String? = null,
        locationPayload: String? = null,
        ephemeralTimerMs: Long = 0L,
        senderAvatarBase64: String? = null,
        senderName: String? = null,
        senderPhone: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun parseChatMessageEvent(
        event: NostrEvent,
        identityManager: NostrIdentityManager
    ): ParsedChatMessage? = null

    fun buildDeliveryReceiptEvent(
        identityManager: NostrIdentityManager,
        recipientPubKeyHex: String,
        conversationId: String,
        messageId: String,
        status: String = "DELIVERED"
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun parseDeliveryReceiptEvent(
        event: NostrEvent,
        identityManager: NostrIdentityManager
    ): ParsedDeliveryReceipt? = null

    // ── Flow 2: Timeline / Feed ───────────────────────────────────────────────

    /**
     * Converts any identifier (e.g. 'post_12345') to a 64-char hex hash
     * compliant with NIP-01 / NIP-25 for Nostr 'e' tags.
     */
    fun toNostrHex(id: String): String {
        val trimmed = id.trim().lowercase()
        if (trimmed.length == 64 && trimmed.all { it in '0'..'9' || it in 'a'..'f' }) {
            return trimmed
        }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isValidHex64(s: String?): Boolean {
        if (s.isNullOrBlank() || s.length != 64) return false
        return s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    fun buildPostEvent(
        identityManager: NostrIdentityManager,
        post: SocialPost
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun parsePostEvent(event: NostrEvent): SocialPost? = null

    fun buildReactionEvent(
        identityManager: NostrIdentityManager,
        postId: String,
        postAuthorPubkeyHex: String,
        emoji: String = "❤️",
        senderName: String? = null,
        senderPhone: String? = null,
        senderAvatarBase64: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun buildStoryReactionEvent(
        identityManager: NostrIdentityManager,
        storyId: String,
        storyAuthorPubkey: String?,
        emoji: String
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun buildCommentEvent(
        identityManager: NostrIdentityManager,
        postId: String,
        postAuthorPubkeyHex: String,
        comment: SocialComment
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun parseCommentEvent(event: NostrEvent): SocialComment? = null

    fun buildDeletePostEvent(
        identityManager: NostrIdentityManager,
        postId: String
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun buildDeleteCommentEvent(
        identityManager: NostrIdentityManager,
        postId: String,
        commentId: String
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun buildDeleteStoryEvent(
        identityManager: NostrIdentityManager,
        storyId: String
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    // ── Flow 3: Profile metadata ──────────────────────────────────────────────

    fun buildProfileMetadataEvent(
        identityManager: NostrIdentityManager,
        displayName: String,
        bio: String,
        avatarBase64OrUrl: String? = null,
        phone: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun parseProfileMetadataEvent(event: NostrEvent): NostrProfile? = null

    // ── Flow 4: Call signals ──────────────────────────────────────────────────

    fun buildCallSignalEvent(
        identityManager: NostrIdentityManager,
        recipientPubKeyHex: String,
        signalType: String,
        callId: String,
        payloadJson: JSONObject
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun buildCallSignalEvent(
        myIdentity: Secp256k1.KeyPair,
        recipientPubKeyHex: String,
        signalType: String,
        callId: String,
        payloadJson: JSONObject
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    // ── Flow 5: Friend invitations ────────────────────────────────────────────

    fun buildInvitationEvent(
        identityManager: NostrIdentityManager,
        senderName: String,
        senderPhone: String,
        recipientPhone: String,
        groupKey: String,
        avatarBase64: String? = null,
        fcmToken: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun buildInvitationEvent(
        myIdentity: Secp256k1.KeyPair,
        senderName: String,
        senderPhone: String,
        recipientPhone: String,
        groupKey: String,
        avatarBase64: String? = null,
        fcmToken: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun buildInvitationAckEvent(
        identityManager: NostrIdentityManager,
        recipientPhone: String,
        recipientPubkeyHex: String? = null,
        avatarBase64: String? = null,
        fcmToken: String? = null
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun parseInvitationEvent(event: NostrEvent): ParsedInvitation? = null

    fun parseInvitationAckEvent(event: NostrEvent): ParsedInvitationAck? = null

    // ── Flow 6: Stories ───────────────────────────────────────────────────────

    fun buildStoryEvent(
        identityManager: NostrIdentityManager,
        story: SocialStory
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun buildStoryViewEvent(
        identityManager: NostrIdentityManager,
        story: SocialStory
    ): NostrEvent = error("OrbisNet proprietary core — not available in public build")

    fun parseStoryEvent(event: NostrEvent, context: Context): SocialStory? = null
}
