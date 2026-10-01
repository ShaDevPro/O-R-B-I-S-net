package com.sha.orbis.nostr.protocol

import android.util.Log
import com.sha.orbis.model.Message
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.nostr.crypto.Bech32
import com.sha.orbis.nostr.crypto.NostrCipher
import com.sha.orbis.nostr.crypto.Secp256k1
import com.sha.orbis.nostr.identity.NostrIdentityManager
import android.content.Context
import com.sha.orbis.nostr.model.NostrEvent
import com.sha.orbis.social.Reaction
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialStory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Moteur protocolaire central pour OrbisNet.
 * Assure la conversion bidirectionnelle entre les modèles métiers de l'application
 * (Discussions, Fil d'actualité, Profil, Contacts, Appels) et les événements Nostr normalisés (NIPs).
 */
object NostrProtocolEngine {

    private const val TAG = "NostrProtocolEngine"
    const val TAG_ORBISNET = "orbisnet"
    const val TAG_ORBISNET_STORY = "orbisnet-story"

    // ====================================================================
    // FLOW 1 : DISCUSSIONS DIRECTES & GROUPES (KIND 4 / E2EE)
    // ====================================================================

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


    /**
     * Construit et signe un événement Kind 4 chiffré de bout en bout (NIP-04 / AES-GCM).
     */
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
    ): NostrEvent {
        val payloadJson = JSONObject().apply {
            put("msgId", messageId)
            put("convId", conversationId)
            put("text", plainText)
            put("timestamp", System.currentTimeMillis())
            if (!audioBase64.isNullOrBlank()) put("audio", audioBase64)
            if (!locationPayload.isNullOrBlank()) put("loc", locationPayload)
            if (ephemeralTimerMs > 0) put("ephMs", ephemeralTimerMs)
            if (!senderAvatarBase64.isNullOrBlank()) put("avatar", senderAvatarBase64)
            if (!senderName.isNullOrBlank()) put("senderName", senderName)
            if (!senderPhone.isNullOrBlank()) put("senderPhone", senderPhone)
        }

        val myIdentity = identityManager.getOrCreateIdentity()
        val cleanRecipientHex = if (recipientPubKeyHex.startsWith("npub1")) {
            try { Bech32.decodeToHex(recipientPubKeyHex).second } catch (_: Exception) { recipientPubKeyHex }
        } else recipientPubKeyHex
        val recipientBytes = Bech32.hexToBytes(cleanRecipientHex)

        val encryptedContent = NostrCipher.encrypt(
            plainText = payloadJson.toString(),
            myPrivKey32 = myIdentity.privateKey,
            theirPubKey32 = recipientBytes
        )

        val tags = listOf(
            listOf("p", cleanRecipientHex.lowercase()),
            listOf("t", TAG_ORBISNET)
        )

        return identityManager.signEvent(
            kind = NostrEvent.KIND_ENCRYPTED_DIRECT_MESSAGE,
            tags = tags,
            content = encryptedContent
        )
    }

    /**
     * Tente de déchiffrer et d'extraire un message direct Kind 4 reçu.
     */
    fun parseChatMessageEvent(
        event: NostrEvent,
        identityManager: NostrIdentityManager
    ): ParsedChatMessage? {
        if (event.kind != NostrEvent.KIND_ENCRYPTED_DIRECT_MESSAGE) return null

        return try {
            val myIdentity = identityManager.getOrCreateIdentity()
            val senderBytes = Bech32.hexToBytes(event.pubkey)

            val decryptedJson = NostrCipher.decrypt(
                encryptedContent = event.content,
                myPrivKey32 = myIdentity.privateKey,
                theirPubKey32 = senderBytes
            )

            val obj = JSONObject(decryptedJson)
            ParsedChatMessage(
                messageId = obj.optString("msgId", event.id),
                conversationId = obj.optString("convId", event.pubkey),
                senderPubkey = event.pubkey,
                text = obj.optString("text", ""),
                timestamp = obj.optLong("timestamp", event.createdAt * 1000L),
                audioBase64 = obj.optString("audio").takeIf { it.isNotBlank() },
                locationPayload = obj.optString("loc").takeIf { it.isNotBlank() },
                ephemeralTimerMs = obj.optLong("ephMs", 0L),
                senderAvatarBase64 = obj.optString("avatar").takeIf { it.isNotBlank() },
                senderName = obj.optString("senderName").takeIf { it.isNotBlank() },
                senderPhone = obj.optString("senderPhone").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de déchiffrer l'événement Kind 4 ${event.id.take(8)}: ${e.message}")
            null
        }
    }

    /**
     * Construit et signe un accusé de réception éphémère Kind 20003 (NIP-20003 souverain).
     * Permet d'attester de la bonne réception physique du message par le destinataire (✓✓).
     */
    fun buildDeliveryReceiptEvent(
        identityManager: NostrIdentityManager,
        recipientPubKeyHex: String,
        conversationId: String,
        messageId: String,
        status: String = "DELIVERED"
    ): NostrEvent {
        val payloadJson = JSONObject().apply {
            put("type", "DELIVERY_RECEIPT")
            put("msgId", messageId)
            put("convId", conversationId)
            put("status", status)
            put("timestamp", System.currentTimeMillis())
        }

        val myIdentity = identityManager.getOrCreateIdentity()
        val cleanRecipientHex = if (recipientPubKeyHex.startsWith("npub1")) {
            try { Bech32.decodeToHex(recipientPubKeyHex).second } catch (_: Exception) { recipientPubKeyHex }
        } else recipientPubKeyHex
        val recipientBytes = Bech32.hexToBytes(cleanRecipientHex)

        val encryptedContent = try {
            NostrCipher.encrypt(
                plainText = payloadJson.toString(),
                myPrivKey32 = myIdentity.privateKey,
                theirPubKey32 = recipientBytes
            )
        } catch (_: Exception) {
            payloadJson.toString()
        }

        val tags = listOf(
            listOf("p", cleanRecipientHex.lowercase()),
            listOf("m", messageId),
            listOf("c", conversationId),
            listOf("t", TAG_ORBISNET)
        )

        return identityManager.signEvent(
            kind = NostrEvent.KIND_EPHEMERAL_RECEIPT,
            tags = tags,
            content = encryptedContent
        )
    }

    /**
     * Tente de parser un accusé de réception Kind 20003 reçu.
     */
    fun parseDeliveryReceiptEvent(
        event: NostrEvent,
        identityManager: NostrIdentityManager
    ): ParsedDeliveryReceipt? {
        if (event.kind != NostrEvent.KIND_EPHEMERAL_RECEIPT) return null

        val tagMsgId = event.tags.find { it.size >= 2 && it[0] == "m" }?.get(1)
        val tagConvId = event.tags.find { it.size >= 2 && it[0] == "c" }?.get(1)

        return try {
            val myIdentity = identityManager.getOrCreateIdentity()
            val senderBytes = Bech32.hexToBytes(event.pubkey)

            val decryptedJson = try {
                NostrCipher.decrypt(
                    encryptedContent = event.content,
                    myPrivKey32 = myIdentity.privateKey,
                    theirPubKey32 = senderBytes
                )
            } catch (_: Exception) {
                event.content
            }

            val obj = JSONObject(decryptedJson)
            val msgId = obj.optString("msgId", tagMsgId ?: "")
            val convId = obj.optString("convId", tagConvId ?: event.pubkey)
            val status = obj.optString("status", "DELIVERED")

            if (msgId.isBlank()) null
            else ParsedDeliveryReceipt(
                messageId = msgId,
                conversationId = convId,
                status = status,
                senderPubkey = event.pubkey,
                timestamp = obj.optLong("timestamp", event.createdAt * 1000L)
            )
        } catch (e: Exception) {
            if (!tagMsgId.isNullOrBlank()) {
                ParsedDeliveryReceipt(
                    messageId = tagMsgId,
                    conversationId = tagConvId ?: event.pubkey,
                    status = "DELIVERED",
                    senderPubkey = event.pubkey,
                    timestamp = event.createdAt * 1000L
                )
            } else {
                null
            }
        }
    }


    // ====================================================================
    // FLOW 2 : FIL D'ACTUALITÉ / TIMELINE (KIND 1 & KIND 7)
    // ====================================================================

    /**
     * Convertit de manière déterministe tout identifiant (ex: 'post_12345') en hash hexadécimal 64 caractères
     * conforme NIP-01 / NIP-25 pour les tags 'e' de Nostr.
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

    /**
     * Publie un post souverain sur le fil d'actualité OrbisNet (Kind 1).
     */
    fun buildPostEvent(
        identityManager: NostrIdentityManager,
        post: SocialPost
    ): NostrEvent {
        val tags = mutableListOf(
            listOf("t", TAG_ORBISNET),
            listOf("post_id", post.id)
        )

        val contentObj = JSONObject().apply {
            put("postId", post.id)
            put("text", post.content)
            put("authorName", post.authorName)
            put("authorPhone", post.authorPhone)
            if (!post.authorPubkey.isNullOrBlank()) {
                put("authorPubkey", post.authorPubkey)
            } else {
                put("authorPubkey", identityManager.publicKeyHex)
            }
            if (!post.authorAvatarPath.isNullOrBlank()) {
                val thumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(post.authorAvatarPath, 96)
                if (!thumb.isNullOrBlank()) {
                    put("avatar", thumb)
                }
            }
            if (post.poll != null) {
                put("poll", post.poll.toJson())
            }
            if (!post.targetCircleId.isNullOrBlank()) {
                put("targetCircleId", post.targetCircleId)
            }
            if (post.excludedCircleIds.isNotEmpty()) {
                put("excludedCircleIds", JSONArray(post.excludedCircleIds))
            }
            if (post.excludedPhones.isNotEmpty()) {
                put("excludedPhones", JSONArray(post.excludedPhones))
            }
            if (!post.mediaType.isNullOrBlank()) {
                put("mediaType", post.mediaType)
            }
            if (!post.mediaUrl.isNullOrBlank()) {
                put("mediaUrl", post.mediaUrl)
            }
            // Ne sérialise le Base64 que s'il n'y a pas d'URL CDN et que ce n'est pas une vidéo (évite le rejet WebSocket >128 Ko)
            if (!post.mediaData.isNullOrBlank() && post.mediaUrl.isNullOrBlank() && post.mediaType != "video") {
                put("mediaData", post.mediaData)
            }
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_TEXT_NOTE,
            tags = tags,
            content = contentObj.toString(),
            createdAtSeconds = post.timestamp / 1000L
        )
    }

    /**
     * Parse un événement Kind 1 en SocialPost Orbis.
     */
    fun parsePostEvent(event: NostrEvent): SocialPost? {
        if (event.kind != NostrEvent.KIND_TEXT_NOTE) return null

        return try {
            val content = event.content
            if (content.startsWith("{") && content.endsWith("}")) {
                val obj = JSONObject(content)
                val pollObj = obj.optJSONObject("poll")
                val poll = if (pollObj != null) com.sha.orbis.social.SocialPoll.fromJson(pollObj) else null
                val authorPhone = obj.optString("authorPhone").ifBlank { Bech32.npubEncode(event.pubkey) }
                val mediaUrl = obj.optString("mediaUrl").ifBlank { null }
                val mediaType = obj.optString("mediaType").ifBlank {
                    if (mediaUrl?.contains(".mp4", ignoreCase = true) == true) "video" else null
                }
                val mediaData = obj.optString("mediaData").ifBlank { null }
                val authorPubkey = obj.optString("authorPubkey").ifBlank { event.pubkey }
                val targetCircleId = obj.optString("targetCircleId").ifBlank { null }
                val excludedCircleIds = obj.optJSONArray("excludedCircleIds")?.let { array ->
                    List(array.length()) { index -> array.getString(index) }
                } ?: emptyList()
                val excludedPhones = obj.optJSONArray("excludedPhones")?.let { array ->
                    List(array.length()) { index -> array.getString(index) }
                } ?: emptyList()

                SocialPost(
                    id = obj.optString("postId", event.id),
                    authorPhone = authorPhone,
                    authorName = obj.optString("authorName", "Utilisateur OrbisNet"),
                    authorAvatarPath = obj.optString("avatar").takeIf { it.isNotBlank() },
                    content = obj.optString("text", ""),
                    timestamp = event.createdAt * 1000L,
                    targetCircleId = targetCircleId,
                    excludedCircleIds = excludedCircleIds,
                    excludedPhones = excludedPhones,
                    poll = poll,
                    mediaType = mediaType,
                    mediaData = mediaData,
                    mediaUrl = mediaUrl,
                    mediaPath = null,
                    authorPubkey = authorPubkey,
                    receivedAt = System.currentTimeMillis()
                )
            } else {
                // Post Nostr standard en texte brut
                SocialPost(
                    id = event.id,
                    authorPhone = Bech32.npubEncode(event.pubkey),
                    authorName = "OrbisNet",
                    authorAvatarPath = null,
                    content = content,
                    timestamp = event.createdAt * 1000L,
                    authorPubkey = event.pubkey,
                    receivedAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur parsing post Kind 1: ${e.message}")
            null
        }
    }

    /**
     * Publie une réaction (Like / Emoji) sur un post OU un commentaire (Kind 7).
     * Conforme NIP-25 avec hash 64-hex pour 'e', type 'k'=1, et 'p' uniquement si 64-hex valide.
     * Quand [commentId] est fourni : ajoute tag "comment_id" + "t"="comment_reaction" (cible = commentaire, pas le post).
     */
    fun buildReactionEvent(
        identityManager: NostrIdentityManager,
        postId: String,
        postAuthorPubkey: String?,
        emoji: String = "❤️",
        authorName: String? = null,
        authorPhone: String? = null,
        authorAvatarBase64: String? = null,
        commentId: String? = null
    ): NostrEvent {
        val hexPostId = toNostrHex(postId)
        val tags = mutableListOf(
            listOf("e", hexPostId),
            listOf("k", "1"),
            listOf("post_id", postId),
            listOf("t", TAG_ORBISNET)
        )
        if (!commentId.isNullOrBlank()) {
            tags.add(listOf("comment_id", commentId.trim()))
            tags.add(listOf("t", "comment_reaction"))
        }
        if (!authorName.isNullOrBlank()) {
            tags.add(listOf("author_name", authorName.trim()))
        }
        if (!authorPhone.isNullOrBlank()) {
            tags.add(listOf("author_phone", authorPhone.trim()))
        }
        if (!authorAvatarBase64.isNullOrBlank()) {
            tags.add(listOf("author_avatar", authorAvatarBase64.trim()))
        }
        val cleanPubkey = postAuthorPubkey?.trim()?.lowercase()
        if (isValidHex64(cleanPubkey)) {
            tags.add(listOf("p", cleanPubkey!!))
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_REACTION,
            tags = tags,
            content = emoji
        )
    }

    fun buildStoryReactionEvent(
        identityManager: NostrIdentityManager,
        storyId: String,
        storyAuthorPubkey: String?,
        emoji: String = "❤️",
        authorName: String? = null,
        authorPhone: String? = null,
        authorAvatarBase64: String? = null
    ): NostrEvent {
        val hexStoryId = toNostrHex(storyId)
        val tags = mutableListOf(
            listOf("e", hexStoryId),
            listOf("k", "1"),
            listOf("story_id", storyId),
            listOf("t", TAG_ORBISNET),
            listOf("t", TAG_ORBISNET_STORY),
            listOf("t", "story_reaction")
        )
        if (!authorName.isNullOrBlank()) {
            tags.add(listOf("author_name", authorName.trim()))
        }
        if (!authorPhone.isNullOrBlank()) {
            tags.add(listOf("author_phone", authorPhone.trim()))
        }
        if (!authorAvatarBase64.isNullOrBlank()) {
            tags.add(listOf("author_avatar", authorAvatarBase64.trim()))
        }
        val cleanPubkey = storyAuthorPubkey?.trim()?.lowercase()
        if (isValidHex64(cleanPubkey)) {
            tags.add(listOf("p", cleanPubkey!!))
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_REACTION,
            tags = tags,
            content = emoji
        )
    }

    /**
     * Publie un commentaire sur un post (Kind 1 avec tag 'e').
     * Conforme NIP-10 avec hash 64-hex pour 'e' et 'p' uniquement si 64-hex valide.
     */
    fun buildCommentEvent(
        identityManager: NostrIdentityManager,
        postId: String,
        postAuthorPubkey: String?,
        comment: SocialComment
    ): NostrEvent {
        val hexPostId = toNostrHex(postId)
        val tags = mutableListOf(
            listOf("e", hexPostId),
            listOf("post_id", postId),
            listOf("t", TAG_ORBISNET),
            listOf("t", "comment")
        )
        val cleanPubkey = postAuthorPubkey?.trim()?.lowercase()
        if (isValidHex64(cleanPubkey)) {
            tags.add(listOf("p", cleanPubkey!!))
        }

        val contentObj = JSONObject().apply {
            put("commentId", comment.id)
            put("postId", postId)
            put("text", comment.text)
            put("authorName", comment.authorName)
            put("authorPhone", comment.authorPhone)
            if (!comment.authorAvatarPath.isNullOrBlank()) {
                val thumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(comment.authorAvatarPath, 96)
                if (!thumb.isNullOrBlank()) {
                    put("avatar", thumb)
                }
            }
            if (!comment.replyToCommentId.isNullOrBlank()) {
                put("replyToId", comment.replyToCommentId)
            }
            if (!comment.replyToAuthorName.isNullOrBlank()) {
                put("replyToName", comment.replyToAuthorName)
            }
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_TEXT_NOTE,
            tags = tags,
            content = contentObj.toString(),
            createdAtSeconds = comment.timestamp / 1000L
        )
    }

    /**
     * Parse un événement Kind 1 en SocialComment s'il contient un tag 'e' ou 'post_id'.
     */
    fun parseCommentEvent(event: NostrEvent): SocialComment? {
        val eTag = event.tags.find { it.size >= 2 && it[0] == "e" }?.get(1)
        val postIdTag = event.tags.find { it.size >= 2 && it[0] == "post_id" }?.get(1)
        val fallbackTargetPostId = postIdTag ?: eTag ?: return null

        return try {
            val content = event.content
            if (content.startsWith("{") && content.endsWith("}")) {
                val obj = JSONObject(content)
                val authorPhone = obj.optString("authorPhone").ifBlank { Bech32.npubEncode(event.pubkey) }
                val resolvedPostId = obj.optString("postId").ifBlank { fallbackTargetPostId }
                SocialComment(
                    id = obj.optString("commentId", event.id),
                    postId = resolvedPostId,
                    authorPhone = authorPhone,
                    authorName = obj.optString("authorName", "Utilisateur"),
                    authorAvatarPath = obj.optString("avatar").takeIf { it.isNotBlank() },
                    text = obj.optString("text", ""),
                    timestamp = event.createdAt * 1000L,
                    replyToCommentId = obj.optString("replyToId").takeIf { it.isNotBlank() },
                    replyToAuthorName = obj.optString("replyToName").takeIf { it.isNotBlank() }
                )
            } else {
                SocialComment(
                    id = event.id,
                    postId = fallbackTargetPostId,
                    authorPhone = Bech32.npubEncode(event.pubkey),
                    authorName = "Utilisateur",
                    authorAvatarPath = null,
                    text = content,
                    timestamp = event.createdAt * 1000L
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur parsing post comment Kind 1: ${e.message}")
            null
        }
    }

    /**
     * Publie la suppression d'un post (NIP-09 Kind 5).
     */
    fun buildDeletePostEvent(
        identityManager: NostrIdentityManager,
        postId: String
    ): NostrEvent {
        val hexPostId = toNostrHex(postId)
        val tags = listOf(
            listOf("e", hexPostId),
            listOf("post_id", postId),
            listOf("t", TAG_ORBISNET)
        )
        return identityManager.signEvent(
            kind = NostrEvent.KIND_DELETION,
            tags = tags,
            content = "Publication supprimée"
        )
    }

    /**
     * Publie la suppression d'un commentaire (NIP-09 Kind 5).
     */
    fun buildDeleteCommentEvent(
        identityManager: NostrIdentityManager,
        postId: String,
        commentId: String
    ): NostrEvent {
        val hexCommentId = toNostrHex(commentId)
        val tags = listOf(
            listOf("e", hexCommentId),
            listOf("post_id", postId),
            listOf("comment_id", commentId),
            listOf("t", TAG_ORBISNET)
        )
        return identityManager.signEvent(
            kind = NostrEvent.KIND_DELETION,
            tags = tags,
            content = "Commentaire supprimé"
        )
    }

    /**
     * Publie la suppression d'une story (NIP-09 Kind 5).
     */
    fun buildDeleteStoryEvent(
        identityManager: NostrIdentityManager,
        storyId: String
    ): NostrEvent {
        val hexStoryId = toNostrHex(storyId)
        val tags = listOf(
            listOf("e", hexStoryId),
            listOf("story_id", storyId),
            listOf("t", "story"),
            listOf("t", TAG_ORBISNET)
        )
        return identityManager.signEvent(
            kind = NostrEvent.KIND_DELETION,
            tags = tags,
            content = "Story supprimée"
        )
    }


    // ====================================================================
    // FLOW 3 : PROFIL UTILISATEUR & WALL (KIND 0)
    // ====================================================================

    data class NostrProfile(
        val name: String,
        val about: String,
        val picture: String? = null,
        val phone: String? = null
    )

    fun buildProfileMetadataEvent(
        identityManager: NostrIdentityManager,
        displayName: String,
        bio: String,
        avatarBase64OrUrl: String? = null,
        phone: String? = null
    ): NostrEvent {
        val metadata = JSONObject().apply {
            put("name", displayName)
            put("display_name", displayName)
            put("about", bio)
            if (!avatarBase64OrUrl.isNullOrBlank()) {
                put("picture", avatarBase64OrUrl)
            }
            if (!phone.isNullOrBlank()) {
                put("phone", phone)
            }
        }

        val tags = mutableListOf(listOf("t", TAG_ORBISNET))
        if (!phone.isNullOrBlank()) {
            tags.add(listOf("phone", phone))
            val digits = phone.filter { it.isDigit() }
            if (digits.isNotBlank()) {
                tags.add(listOf("phone_digits", digits))
            }
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_METADATA,
            tags = tags,
            content = metadata.toString()
        )
    }

    fun parseProfileMetadataEvent(event: NostrEvent): NostrProfile? {
        if (event.kind != NostrEvent.KIND_METADATA) return null
        return try {
            val obj = JSONObject(event.content)
            val phoneTag = event.tags.find { it.size >= 2 && (it[0] == "phone" || it[0] == "phone_digits") }?.get(1)
            val phoneInJson = obj.optString("phone").takeIf { it.isNotBlank() }
            NostrProfile(
                name = obj.optString("display_name").ifBlank { obj.optString("name", "Anonyme") },
                about = obj.optString("about", ""),
                picture = obj.optString("picture").takeIf { it.isNotBlank() },
                phone = phoneInJson ?: phoneTag
            )
        } catch (e: Exception) {
            null
        }
    }

    // ====================================================================
    // APPELS VOCAUX INSTANTANÉS (KIND 20001 ÉPHÉMÈRE)
    // ====================================================================

    fun buildCallSignalEvent(
        identityManager: NostrIdentityManager,
        recipientPubKeyHex: String,
        signalType: String, // "OFFER", "ANSWER", "END"
        callId: String,
        payloadJson: JSONObject
    ): NostrEvent {
        return buildCallSignalEvent(
            myIdentity = identityManager.getOrCreateIdentity(),
            recipientPubKeyHex = recipientPubKeyHex,
            signalType = signalType,
            callId = callId,
            payloadJson = payloadJson
        )
    }

    fun buildCallSignalEvent(
        myIdentity: Secp256k1.KeyPair,
        recipientPubKeyHex: String,
        signalType: String, // "OFFER", "ANSWER", "END"
        callId: String,
        payloadJson: JSONObject
    ): NostrEvent {
        payloadJson.put("sigType", signalType)
        payloadJson.put("callId", callId)
        payloadJson.put("timestamp", System.currentTimeMillis())

        val cleanRecipientHex = if (recipientPubKeyHex.startsWith("npub1")) {
            try { Bech32.decodeToHex(recipientPubKeyHex).second.lowercase() } catch (_: Exception) { recipientPubKeyHex.lowercase() }
        } else recipientPubKeyHex.lowercase()
        val recipientBytes = Bech32.hexToBytes(cleanRecipientHex)

        val encryptedContent = NostrCipher.encrypt(
            plainText = payloadJson.toString(),
            myPrivKey32 = myIdentity.privateKey,
            theirPubKey32 = recipientBytes
        )

        return NostrEvent.createAndSign(
            pubkeyHex = myIdentity.publicKeyHex,
            privkey32 = myIdentity.privateKey,
            kind = NostrEvent.KIND_EPHEMERAL_CALL_SIGNAL,
            tags = listOf(
                listOf("p", cleanRecipientHex),
                listOf("t", TAG_ORBISNET)
            ),
            content = encryptedContent
        )
    }

    // ====================================================================
    // FLOW 5 : INVITATIONS CONTACT & ACCUSÉ DE RÉCEPTION (KIND 1 TAGGED)
    // ====================================================================

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

    /**
     * Diffuse une invitation OrbisNet vers les relais Nostr.
     * L'invitation est un Kind 1 (public) tagué #orbisnet-invite et #invite_<phone_digits>.
     * Le destinataire identifie instantanément l'invitation par son numéro de téléphone.
     */
    fun buildInvitationEvent(
        identityManager: NostrIdentityManager,
        senderName: String,
        senderPhone: String,
        recipientPhone: String,
        groupKey: String,
        avatarBase64: String? = null,
        fcmToken: String? = null
    ): NostrEvent {
        val cleanRecipient = recipientPhone.filter { it.isDigit() }
        val cleanLast8 = cleanRecipient.takeLast(8)

        val tags = mutableListOf(
            listOf("t", TAG_ORBISNET),
            listOf("t", "orbisnet-invite"),
            listOf("t", "invite_$cleanRecipient"),
            listOf("phone", recipientPhone),
            listOf("sender_phone", senderPhone)
        )
        if (cleanLast8 != cleanRecipient && cleanLast8.length >= 8) {
            tags.add(listOf("t", "invite_$cleanLast8"))
        }

        val contentObj = JSONObject().apply {
            put("type", "ORBISNET_INVITE")
            put("senderName", senderName)
            put("senderPhone", senderPhone)
            put("recipientPhone", recipientPhone)
            put("groupKey", groupKey)
            put("pubkey", identityManager.publicKeyHex)
            if (!avatarBase64.isNullOrBlank()) {
                put("avatar", avatarBase64)
            }
            if (!fcmToken.isNullOrBlank()) {
                put("fcmToken", fcmToken)
            }
            put("timestamp", System.currentTimeMillis())
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_TEXT_NOTE,
            tags = tags,
            content = contentObj.toString().replace("\\/", "/")
        )
    }

    /**
     * Surcharge de compatibilité ascendante pour les anciens appels sans senderPhone/groupKey explicites.
     */
    fun buildInvitationEvent(
        identityManager: NostrIdentityManager,
        senderName: String,
        recipientPhone: String,
        payload: String
    ): NostrEvent {
        val cleanRecipient = recipientPhone.filter { it.isDigit() }
        val cleanLast8 = cleanRecipient.takeLast(8)

        val tags = mutableListOf(
            listOf("t", TAG_ORBISNET),
            listOf("t", "orbisnet-invite"),
            listOf("t", "invite_$cleanRecipient"),
            listOf("phone", recipientPhone)
        )
        if (cleanLast8 != cleanRecipient && cleanLast8.length >= 8) {
            tags.add(listOf("t", "invite_$cleanLast8"))
        }

        val extractedKey = if (payload.contains(":KEY:")) {
            payload.substringAfter(":KEY:").substringBefore(":PHONE:")
        } else {
            ""
        }

        val contentObj = JSONObject().apply {
            put("type", "ORBISNET_INVITE")
            put("senderName", senderName)
            put("senderPhone", "")
            put("recipientPhone", recipientPhone)
            put("payload", payload)
            put("groupKey", extractedKey)
            put("pubkey", identityManager.publicKeyHex)
            put("timestamp", System.currentTimeMillis())
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_TEXT_NOTE,
            tags = tags,
            content = contentObj.toString()
        )
    }

    /**
     * Diffuse un accusé de réception (ACK handshake) lorsqu'une invitation est acceptée.
     */
    fun buildInvitationAckEvent(
        identityManager: NostrIdentityManager,
        responderName: String,
        responderPhone: String,
        recipientPhone: String,
        recipientPubkeyHex: String? = null,
        avatarBase64: String? = null,
        fcmToken: String? = null
    ): NostrEvent {
        val cleanRecipient = recipientPhone.filter { it.isDigit() }
        val cleanLast8 = cleanRecipient.takeLast(8)
        val cleanWithoutZero = cleanRecipient.trimStart('0')
        val resolvedHex = com.sha.orbis.storage.FriendRequestRepository.resolveNostrPubkeyHex(recipientPubkeyHex)
            ?: recipientPubkeyHex?.lowercase()

        val tags = mutableListOf(
            listOf("t", TAG_ORBISNET),
            listOf("t", "orbisnet-invite-ack"),
            listOf("phone", recipientPhone)
        )
        if (cleanRecipient.isNotBlank()) {
            tags.add(listOf("t", "ack_$cleanRecipient"))
        }
        if (cleanWithoutZero.isNotBlank() && cleanWithoutZero != cleanRecipient) {
            tags.add(listOf("t", "ack_$cleanWithoutZero"))
        }
        if (cleanLast8.length >= 8 && cleanLast8 != cleanRecipient && cleanLast8 != cleanWithoutZero) {
            tags.add(listOf("t", "ack_$cleanLast8"))
        }
        if (!resolvedHex.isNullOrBlank()) {
            tags.add(listOf("p", resolvedHex.lowercase()))
        }

        val contentObj = JSONObject().apply {
            put("type", "ORBISNET_INVITE_ACK")
            put("responderName", responderName)
            put("responderPhone", responderPhone)
            put("recipientPhone", recipientPhone)
            if (!resolvedHex.isNullOrBlank()) {
                put("recipientPubkey", resolvedHex.lowercase())
            }
            put("pubkey", identityManager.publicKeyHex)
            if (!avatarBase64.isNullOrBlank()) {
                put("avatar", avatarBase64)
            }
            if (!fcmToken.isNullOrBlank()) {
                put("fcmToken", fcmToken)
            }
            put("timestamp", System.currentTimeMillis())
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_TEXT_NOTE,
            tags = tags,
            content = contentObj.toString().replace("\\/", "/")
        )
    }

    /**
     * Parse un événement Nostr en ParsedInvitation s'il s'agit d'une invitation OrbisNet.
     */
    fun parseInvitationEvent(event: NostrEvent): ParsedInvitation? {
        if (event.kind != NostrEvent.KIND_TEXT_NOTE) return null
        val isInvite = event.tags.any { it.size >= 2 && it[0] == "t" && (it[1] == "orbisnet-invite" || it[1].startsWith("invite_")) }
        if (!isInvite && !event.content.contains("ORBISNET_INVITE") && !event.content.contains("\"type\":\"INVITE\"")) return null

        return try {
            val obj = JSONObject(event.content)
            val type = obj.optString("type")
            if (type != "ORBISNET_INVITE" && type != "INVITE") return null

            val phoneTag = event.tags.find { it.size >= 2 && it[0] == "phone" }?.get(1)
            val senderPhoneTag = event.tags.find { it.size >= 2 && it[0] == "sender_phone" }?.get(1)

            val senderName = obj.optString("senderName", "Utilisateur OrbisNet")
            val senderPhone = obj.optString("senderPhone").ifBlank { senderPhoneTag ?: "" }
            val recipientPhone = obj.optString("recipientPhone").ifBlank { phoneTag ?: "" }
            var groupKey = obj.optString("groupKey")

            if (groupKey.isBlank()) {
                val payload = obj.optString("payload")
                if (payload.contains(":KEY:")) {
                    groupKey = payload.substringAfter(":KEY:").substringBefore(":PHONE:")
                }
            }

            val avatar = obj.optString("avatar").takeIf { it.isNotBlank() }
            val fcmToken = obj.optString("fcmToken").takeIf { it.isNotBlank() }

            ParsedInvitation(
                eventId = event.id,
                senderPubkey = obj.optString("pubkey", event.pubkey).ifBlank { event.pubkey },
                senderName = senderName,
                senderPhone = senderPhone,
                recipientPhone = recipientPhone,
                groupKey = groupKey,
                avatarBase64 = avatar,
                fcmToken = fcmToken,
                timestamp = obj.optLong("timestamp", event.createdAt * 1000L)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors du parsing d'invitation Nostr: ${e.message}")
            null
        }
    }

    /**
     * Parse un événement Nostr en ParsedInvitationAck s'il s'agit d'un accusé d'acceptation.
     */
    fun parseInvitationAckEvent(event: NostrEvent): ParsedInvitationAck? {
        if (event.kind != NostrEvent.KIND_TEXT_NOTE) return null
        val isAck = event.tags.any { it.size >= 2 && it[0] == "t" && (it[1] == "orbisnet-invite-ack" || it[1].startsWith("ack_")) }
        if (!isAck && !event.content.contains("ORBISNET_INVITE_ACK")) return null

        return try {
            val obj = JSONObject(event.content)
            if (obj.optString("type") != "ORBISNET_INVITE_ACK") return null

            val phoneTag = event.tags.find { it.size >= 2 && it[0] == "phone" }?.get(1)
            val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)
            val recipientPubkey = obj.optString("recipientPubkey").ifBlank { pTag ?: "" }.takeIf { it.isNotBlank() }
            val fcmToken = obj.optString("fcmToken").takeIf { it.isNotBlank() }

            ParsedInvitationAck(
                eventId = event.id,
                responderPubkey = obj.optString("pubkey", event.pubkey).ifBlank { event.pubkey },
                responderName = obj.optString("responderName", "Ami OrbisNet"),
                responderPhone = obj.optString("responderPhone", ""),
                recipientPhone = obj.optString("recipientPhone").ifBlank { phoneTag ?: "" },
                recipientPubkey = recipientPubkey,
                avatarBase64 = obj.optString("avatar").takeIf { it.isNotBlank() },
                fcmToken = fcmToken,
                timestamp = obj.optLong("timestamp", event.createdAt * 1000L)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors du parsing ACK d'invitation Nostr: ${e.message}")
            null
        }
    }

    // ====================================================================
    // FLOW 6 : STORIES ÉPHÉMÈRES 24H (KIND 1 TAGGED #story)
    // ====================================================================

    fun buildStoryEvent(
        identityManager: NostrIdentityManager,
        story: SocialStory
    ): NostrEvent {
        val tags = mutableListOf(
            listOf("t", TAG_ORBISNET),
            listOf("t", TAG_ORBISNET_STORY),
            listOf("t", "story"),
            listOf("story_id", story.id),
            listOf("author_phone", story.authorPhone),
            listOf("expiration", (story.expiresAt / 1000L).toString())
        )
        if (story.hasMedia) {
            tags.add(listOf("media_type", story.mediaType ?: if (story.isVideo) "video" else "image"))
        }
        val cleanPubkey = story.authorPubkey?.trim()?.lowercase()
        if (isValidHex64(cleanPubkey)) {
            tags.add(listOf("p", cleanPubkey!!))
        }

        val contentObj = JSONObject().apply {
            put("type", "ORBISNET_STORY")
            put("storyId", story.id)
            put("authorPhone", story.authorPhone)
            put("authorName", story.authorName)
            put("content", story.content)
            if (!story.mediaType.isNullOrBlank()) {
                put("mediaType", story.mediaType)
            }
            if (!story.mediaUrl.isNullOrBlank()) {
                put("mediaUrl", story.mediaUrl)
            }
            if (!story.mediaBase64.isNullOrBlank() && story.mediaUrl.isNullOrBlank() && story.mediaType != "video") {
                put("mediaBase64", story.mediaBase64)
            } else {
                put("mediaBase64", "")
            }
            put("backgroundGradientIndex", story.backgroundGradientIndex)
            put("createdAt", story.createdAt)
            put("expiresAt", story.expiresAt)
            if (!story.targetCircleId.isNullOrBlank()) {
                put("targetCircleId", story.targetCircleId)
            }
            if (story.excludedCircleIds.isNotEmpty()) {
                put("excludedCircleIds", JSONArray(story.excludedCircleIds))
            }
            if (story.excludedPhones.isNotEmpty()) {
                put("excludedPhones", JSONArray(story.excludedPhones))
            }
            if (!story.authorAvatarPath.isNullOrBlank()) {
                val thumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(story.authorAvatarPath, 96)
                if (!thumb.isNullOrBlank()) {
                    put("avatar", thumb)
                }
            }
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_TEXT_NOTE,
            tags = tags,
            content = contentObj.toString().replace("\\/", "/")
        )
    }

    fun buildStoryViewEvent(
        identityManager: NostrIdentityManager,
        story: SocialStory,
        viewerName: String,
        viewerPhone: String,
        viewerAvatarBase64: String? = null
    ): NostrEvent {
        val tags = mutableListOf(
            listOf("t", TAG_ORBISNET),
            listOf("t", TAG_ORBISNET_STORY),
            listOf("t", "story_view"),
            listOf("story_id", story.id),
            listOf("author_phone", story.authorPhone),
            listOf("viewer_phone", viewerPhone)
        )
        val cleanPubkey = story.authorPubkey?.trim()?.lowercase()
        if (isValidHex64(cleanPubkey)) {
            tags.add(listOf("p", cleanPubkey!!))
        }

        val contentObj = JSONObject().apply {
            put("type", "ORBISNET_STORY_VIEW")
            put("storyId", story.id)
            put("authorPhone", story.authorPhone)
            put("viewerPhone", viewerPhone)
            put("viewerName", viewerName)
            put("timestamp", System.currentTimeMillis())
            if (!viewerAvatarBase64.isNullOrBlank()) {
                put("avatar", viewerAvatarBase64)
            }
        }

        return identityManager.signEvent(
            kind = NostrEvent.KIND_TEXT_NOTE,
            tags = tags,
            content = contentObj.toString().replace("\\/", "/")
        )
    }

    fun parseStoryEvent(event: NostrEvent, context: Context): SocialStory? {
        if (event.kind != NostrEvent.KIND_TEXT_NOTE) return null
        val isStory = event.tags.any { it.size >= 2 && it[0] == "t" && (it[1] == TAG_ORBISNET_STORY || it[1] == "story") } ||
                      event.content.contains("ORBISNET_STORY")
        if (!isStory) return null
        val trimmedContent = event.content.trim()
        if (!trimmedContent.startsWith("{")) return null

        return try {
            val obj = JSONObject(trimmedContent)
            if (obj.optString("type") != "ORBISNET_STORY") return null

            val storyId = obj.optString("storyId").ifBlank {
                event.tags.find { it.size >= 2 && it[0] == "story_id" }?.get(1) ?: "story_${event.id.take(8)}"
            }
            val authorPhone = obj.optString("authorPhone").ifBlank {
                event.tags.find { it.size >= 2 && it[0] == "author_phone" }?.get(1) ?: Bech32.npubEncode(event.pubkey)
            }
            val authorName = obj.optString("authorName").ifBlank { "Ami OrbisNet" }
            val content = obj.optString("content", "")
            val mediaUrl = obj.optString("mediaUrl").ifBlank { null }
            val mediaType = obj.optString("mediaType").ifBlank {
                if (mediaUrl?.contains(".mp4", ignoreCase = true) == true || storyId.contains("vid", ignoreCase = true)) "video" else null
            }
            val mediaBase64 = obj.optString("mediaBase64").ifBlank { null }
            val backgroundGradientIndex = obj.optInt("backgroundGradientIndex", 0)
            val createdAt = obj.optLong("createdAt", event.createdAt * 1000L)
            val expiresAt = obj.optLong("expiresAt", createdAt + 86_400_000L)
            val targetCircleId = obj.optString("targetCircleId").ifBlank { null }
            val excludedCircleIds = obj.optJSONArray("excludedCircleIds")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList()
            val excludedPhones = obj.optJSONArray("excludedPhones")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList()

            // Skip expired stories
            if (System.currentTimeMillis() > expiresAt) return null

            // Avatar caching
            val avatarBase64 = obj.optString("avatar").takeIf { it.isNotBlank() }
            val savedAvatar = if (avatarBase64 != null) {
                val avatarId = authorPhone.filter { it.isDigit() }.ifBlank { event.pubkey.take(8) }
                com.sha.orbis.ui.components.AvatarManager.saveAvatarFromBase64(context, avatarBase64, "avatar_$avatarId")
            } else null

            // Media file caching
            val savedMediaPath = if (!mediaBase64.isNullOrBlank()) {
                try {
                    if (mediaType == "video") {
                        val cleanStoryId = storyId.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "story_${System.currentTimeMillis()}" }
                        com.sha.orbis.media.VideoMediaHelper.base64ToVideoFile(context, mediaBase64.trim(), "story_$cleanStoryId")?.absolutePath
                    } else {
                        val imagesDir = java.io.File(context.filesDir, "media/images").apply { if (!exists()) mkdirs() }
                        val destFile = java.io.File(imagesDir, "story_${storyId}.jpg")
                        val decoded = android.util.Base64.decode(mediaBase64.trim(), android.util.Base64.DEFAULT)
                        val raw = try { com.sha.orbis.sms.BinarySmsCompressor.decompress(decoded) } catch (_: Exception) { decoded }
                        destFile.writeBytes(raw)
                        destFile.absolutePath
                    }
                } catch (e: Exception) {
                    null
                }
            } else null

            SocialStory(
                id = storyId,
                authorPhone = authorPhone,
                authorName = authorName,
                authorAvatarPath = savedAvatar,
                content = content,
                mediaType = mediaType,
                mediaPath = savedMediaPath,
                mediaBase64 = mediaBase64,
                mediaUrl = mediaUrl,
                backgroundGradientIndex = backgroundGradientIndex,
                createdAt = createdAt,
                expiresAt = expiresAt,
                targetCircleId = targetCircleId,
                excludedCircleIds = excludedCircleIds,
                excludedPhones = excludedPhones,
                seenBy = emptyList(),
                authorPubkey = event.pubkey
            )
        } catch (e: Exception) {
            Log.w(TAG, "Erreur parsing story event: ${e.message}")
            null
        }
    }
}
