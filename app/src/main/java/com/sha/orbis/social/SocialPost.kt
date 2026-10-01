package com.sha.orbis.social

import org.json.JSONArray
import org.json.JSONObject

data class PollOption(
    val id: String,
    val text: String,
    val voteCount: Int = 0,
    val votersPhones: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("voteCount", voteCount)
        put("votersPhones", JSONArray(votersPhones))
    }

    companion object {
        fun fromJson(json: JSONObject): PollOption = PollOption(
            id = json.optString("id"),
            text = json.optString("text"),
            voteCount = json.optInt("voteCount", 0),
            votersPhones = json.optJSONArray("votersPhones")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList()
        )
    }
}

data class SocialPoll(
    val id: String,
    val question: String,
    val options: List<PollOption> = emptyList(),
    val totalVotes: Int = 0,
    val userVotedOptionId: String? = null,
    val isClosed: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("question", question)
        put("options", JSONArray().apply { options.forEach { put(it.toJson()) } })
        put("totalVotes", totalVotes)
        put("userVotedOptionId", userVotedOptionId ?: "")
        put("isClosed", isClosed)
    }

    companion object {
        fun fromJson(json: JSONObject): SocialPoll = SocialPoll(
            id = json.optString("id"),
            question = json.optString("question"),
            options = json.optJSONArray("options")?.let { array ->
                List(array.length()) { index -> PollOption.fromJson(array.getJSONObject(index)) }
            } ?: emptyList(),
            totalVotes = json.optInt("totalVotes", 0),
            userVotedOptionId = json.optString("userVotedOptionId").ifBlank { null },
            isClosed = json.optBoolean("isClosed", false)
        )
    }
}

data class SocialReaction(
    val id: String,
    val targetId: String,
    val userPhone: String,
    val emoji: String, // "❤️", "🔥", "🛡️", "👏", "💡"
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("targetId", targetId)
        put("userPhone", userPhone)
        put("emoji", emoji)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): SocialReaction = SocialReaction(
            id = json.optString("id"),
            targetId = json.optString("targetId"),
            userPhone = json.optString("userPhone"),
            emoji = json.optString("emoji", "❤️"),
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }
}

data class SocialComment(
    val id: String,
    val postId: String,
    val authorPhone: String,
    val authorName: String,
    val authorAvatarPath: String? = null,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val replyToCommentId: String? = null,
    val replyToAuthorName: String? = null,
    val reactions: List<SocialReaction> = emptyList(),
    val isEdited: Boolean = false,
    val editedAt: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("postId", postId)
        put("authorPhone", authorPhone)
        put("authorName", authorName)
        put("authorAvatarPath", authorAvatarPath ?: "")
        put("text", text)
        put("timestamp", timestamp)
        put("replyToCommentId", replyToCommentId ?: "")
        put("replyToAuthorName", replyToAuthorName ?: "")
        put("reactions", JSONArray().apply { reactions.forEach { put(it.toJson()) } })
        put("isEdited", isEdited)
        if (editedAt != null) put("editedAt", editedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): SocialComment = SocialComment(
            id = json.optString("id"),
            postId = json.optString("postId"),
            authorPhone = json.optString("authorPhone"),
            authorName = json.optString("authorName"),
            authorAvatarPath = json.optString("authorAvatarPath").ifBlank { null },
            text = json.optString("text"),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            replyToCommentId = json.optString("replyToCommentId").ifBlank { null },
            replyToAuthorName = json.optString("replyToAuthorName").ifBlank { null },
            reactions = json.optJSONArray("reactions")?.let { array ->
                List(array.length()) { index -> SocialReaction.fromJson(array.getJSONObject(index)) }
            } ?: emptyList(),
            isEdited = json.optBoolean("isEdited", false),
            editedAt = json.optLong("editedAt", 0L).takeIf { it > 0L }
        )
    }
}

data class SocialStory(
    val id: String,
    val authorPhone: String,
    val authorName: String,
    val authorAvatarPath: String? = null,
    val content: String = "",
    val mediaType: String? = null,
    val mediaPath: String? = null,
    val mediaBase64: String? = null,
    val mediaUrl: String? = null,
    val backgroundGradientIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 86_400_000L, // 24 hours
    val targetCircleId: String? = null,
    val excludedCircleIds: List<String> = emptyList(),
    val excludedPhones: List<String> = emptyList(),
    val seenBy: List<String> = emptyList(),
    val reactions: List<SocialReaction> = emptyList(),
    val authorPubkey: String? = null
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    val hasMedia: Boolean get() = !mediaPath.isNullOrBlank() || !mediaBase64.isNullOrBlank() || !mediaUrl.isNullOrBlank()
    val isVideo: Boolean get() = mediaType == "video" || mediaPath?.endsWith(".mp4", ignoreCase = true) == true || mediaUrl?.contains(".mp4", ignoreCase = true) == true

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("authorPhone", authorPhone)
        put("authorName", authorName)
        put("authorAvatarPath", authorAvatarPath ?: "")
        put("content", content)
        if (!mediaType.isNullOrBlank()) put("mediaType", mediaType)
        put("mediaPath", mediaPath ?: "")
        if (!mediaBase64.isNullOrBlank()) {
            put("mediaBase64", mediaBase64)
        } else {
            put("mediaBase64", "")
        }
        if (!mediaUrl.isNullOrBlank()) put("mediaUrl", mediaUrl)
        put("backgroundGradientIndex", backgroundGradientIndex)
        put("createdAt", createdAt)
        put("expiresAt", expiresAt)
        put("targetCircleId", targetCircleId ?: "")
        if (excludedCircleIds.isNotEmpty()) put("excludedCircleIds", JSONArray(excludedCircleIds))
        if (excludedPhones.isNotEmpty()) put("excludedPhones", JSONArray(excludedPhones))
        put("seenBy", JSONArray(seenBy))
        put("reactions", JSONArray().apply { reactions.forEach { put(it.toJson()) } })
        put("authorPubkey", authorPubkey ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): SocialStory = SocialStory(
            id = json.optString("id"),
            authorPhone = json.optString("authorPhone"),
            authorName = json.optString("authorName"),
            authorAvatarPath = json.optString("authorAvatarPath").ifBlank { null },
            content = json.optString("content"),
            mediaType = json.optString("mediaType").ifBlank {
                val p = json.optString("mediaPath")
                val u = json.optString("mediaUrl")
                if (p.endsWith(".mp4", ignoreCase = true) || u.contains(".mp4", ignoreCase = true)) "video" else null
            },
            mediaPath = json.optString("mediaPath").ifBlank { null },
            mediaBase64 = json.optString("mediaBase64").ifBlank { null },
            mediaUrl = json.optString("mediaUrl").ifBlank { null },
            backgroundGradientIndex = json.optInt("backgroundGradientIndex", 0),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            expiresAt = json.optLong("expiresAt", System.currentTimeMillis() + 86_400_000L),
            targetCircleId = json.optString("targetCircleId").ifBlank { null },
            excludedCircleIds = json.optJSONArray("excludedCircleIds")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList(),
            excludedPhones = json.optJSONArray("excludedPhones")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList(),
            seenBy = json.optJSONArray("seenBy")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList(),
            reactions = json.optJSONArray("reactions")?.let { array ->
                List(array.length()) { index -> SocialReaction.fromJson(array.getJSONObject(index)) }
            } ?: emptyList(),
            authorPubkey = json.optString("authorPubkey").ifBlank { null }
        )
    }
}

/**
 * Regroupe la pile chronologique des stories actives d'un même utilisateur (style Instagram).
 */
data class UserStoryGroup(
    val authorPhone: String,
    val authorName: String,
    val authorAvatarPath: String? = null,
    val authorPubkey: String? = null,
    val stories: List<SocialStory> = emptyList()
) {
    fun hasUnseen(currentPhone: String): Boolean {
        if (currentPhone.isBlank()) return true
        return stories.any { story ->
            !story.seenBy.any { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it, currentPhone) }
        }
    }

    fun getFirstUnseenIndex(currentPhone: String): Int {
        if (currentPhone.isBlank()) return 0
        val idx = stories.indexOfFirst { story ->
            !story.seenBy.any { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it, currentPhone) }
        }
        return if (idx >= 0) idx else 0
    }
}


enum class UserSocialRole {
    FOUNDER_DEV,    // 👑 Orbis Dev Core (Gold badge)
    VERIFIED_E2EE,  // 🛡️ RSA-2048 Certifié (Emerald green badge)
    PIONEER,        // 🚀 Pionnier P2P (Amethyst badge)
    CIRCLE_ADMIN,   // ⭐ Admin Cercle (Sky blue badge)
    STANDARD        // Utilisateur standard
}

data class SocialPost(
    val id: String,
    val authorPhone: String,
    val authorName: String,
    val authorAvatarPath: String? = null,
    val content: String,
    val hashtags: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val targetCircleId: String? = null, // null = Public P2P, non-null = Restricted circle
    val excludedCircleIds: List<String> = emptyList(), // Circles excluded from seeing this post (e.g. "circle_family")
    val excludedPhones: List<String> = emptyList(), // Specific phones excluded from seeing this post
    val rsaSignature: String = "",
    val poll: SocialPoll? = null,
    val reactions: List<SocialReaction> = emptyList(),
    val comments: List<SocialComment> = emptyList(),
    val isPinned: Boolean = false,
    val isOfficialAnnouncement: Boolean = false,
    val authorRole: UserSocialRole = UserSocialRole.STANDARD,
    val repostAuthorName: String? = null,
    val repostAuthorPhone: String? = null,
    val repostOriginalPostId: String? = null,
    val repostsCount: Int = 0,
    val mediaType: String? = null, // "image", etc.
    val mediaPath: String? = null, // local cache path
    val mediaData: String? = null, // Base64 compressed for Nostr sync
    val mediaUrl: String? = null, // Blossom CDN / Nostr Media URL
    val authorPubkey: String? = null,
    val isEdited: Boolean = false,
    val editedAt: Long? = null,
    val receivedAt: Long = timestamp
) {
    val isVideo: Boolean get() = mediaType == "video" || mediaPath?.endsWith(".mp4", ignoreCase = true) == true || mediaUrl?.contains(".mp4", ignoreCase = true) == true

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("authorPhone", authorPhone)
        put("authorName", authorName)
        put("authorAvatarPath", authorAvatarPath ?: "")
        put("content", content)
        put("hashtags", JSONArray(hashtags))
        put("timestamp", timestamp)
        put("targetCircleId", targetCircleId ?: "")
        if (excludedCircleIds.isNotEmpty()) put("excludedCircleIds", JSONArray(excludedCircleIds))
        if (excludedPhones.isNotEmpty()) put("excludedPhones", JSONArray(excludedPhones))
        put("rsaSignature", rsaSignature)
        if (poll != null) put("poll", poll.toJson())
        put("reactions", JSONArray().apply { reactions.forEach { put(it.toJson()) } })
        put("comments", JSONArray().apply { comments.forEach { put(it.toJson()) } })
        put("isPinned", isPinned)
        put("isOfficialAnnouncement", isOfficialAnnouncement)
        put("authorRole", authorRole.name)
        put("repostAuthorName", repostAuthorName ?: "")
        put("repostAuthorPhone", repostAuthorPhone ?: "")
        put("repostOriginalPostId", repostOriginalPostId ?: "")
        put("repostsCount", repostsCount)
        if (!mediaType.isNullOrBlank()) put("mediaType", mediaType)
        val validMediaPath = mediaPath?.takeIf { path ->
            if (path.isBlank() || path.endsWith("/")) return@takeIf false
            try {
                val f = java.io.File(path)
                !f.isDirectory && (!f.exists() || (f.isFile && f.length() > 0L))
            } catch (_: Exception) { false }
        }
        if (!validMediaPath.isNullOrBlank()) put("mediaPath", validMediaPath)
        if (!mediaUrl.isNullOrBlank()) put("mediaUrl", mediaUrl)
        // Always serialize mediaData (base64) for photos without mediaUrl
        if (!mediaData.isNullOrBlank()) put("mediaData", mediaData)
        if (!authorPubkey.isNullOrBlank()) put("authorPubkey", authorPubkey)
        put("isEdited", isEdited)
        if (editedAt != null) put("editedAt", editedAt)
        put("receivedAt", receivedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): SocialPost {
            val authorPhone = json.optString("authorPhone")
            val isAuthorAdmin = com.sha.orbis.admin.AdminSecurityHelper.isAdmin(authorPhone)
            val parsedRole = try {
                UserSocialRole.valueOf(json.optString("authorRole", "STANDARD"))
            } catch (_: Exception) {
                UserSocialRole.STANDARD
            }
            val finalRole = if (isAuthorAdmin) UserSocialRole.FOUNDER_DEV else parsedRole

            val rawMediaPath = json.optString("mediaPath").ifBlank { null }
            val cleanMediaPath = rawMediaPath?.takeIf { path ->
                if (path.isBlank() || path.endsWith("/")) return@takeIf false
                try {
                    val f = java.io.File(path)
                    !f.isDirectory && (!f.exists() || (f.isFile && f.length() > 0L))
                } catch (_: Exception) { false }
            }

            return SocialPost(
                id = json.optString("id"),
                authorPhone = authorPhone,
                authorName = json.optString("authorName"),
                authorAvatarPath = json.optString("authorAvatarPath").ifBlank { null },
                content = json.optString("content"),
                hashtags = json.optJSONArray("hashtags")?.let { array ->
                    List(array.length()) { index -> array.getString(index) }
                } ?: emptyList(),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                targetCircleId = json.optString("targetCircleId").ifBlank { null },
                excludedCircleIds = json.optJSONArray("excludedCircleIds")?.let { array ->
                    List(array.length()) { index -> array.getString(index) }
                } ?: emptyList(),
                excludedPhones = json.optJSONArray("excludedPhones")?.let { array ->
                    List(array.length()) { index -> array.getString(index) }
                } ?: emptyList(),
                rsaSignature = json.optString("rsaSignature"),
                poll = json.optJSONObject("poll")?.let { SocialPoll.fromJson(it) },
                reactions = json.optJSONArray("reactions")?.let { array ->
                    List(array.length()) { index -> SocialReaction.fromJson(array.getJSONObject(index)) }
                } ?: emptyList(),
                comments = json.optJSONArray("comments")?.let { array ->
                    List(array.length()) { index -> SocialComment.fromJson(array.getJSONObject(index)) }
                } ?: emptyList(),
                isPinned = json.optBoolean("isPinned", false),
                isOfficialAnnouncement = json.optBoolean("isOfficialAnnouncement", isAuthorAdmin),
                authorRole = finalRole,
                repostAuthorName = json.optString("repostAuthorName").ifBlank { null },
                repostAuthorPhone = json.optString("repostAuthorPhone").ifBlank { null },
                repostOriginalPostId = json.optString("repostOriginalPostId").ifBlank { null },
                repostsCount = json.optInt("repostsCount", 0),
                mediaType = json.optString("mediaType").ifBlank {
                    val p = cleanMediaPath
                    val u = json.optString("mediaUrl")
                    if (p?.endsWith(".mp4", ignoreCase = true) == true || u.contains(".mp4", ignoreCase = true)) "video" else null
                },
                mediaPath = cleanMediaPath,
                mediaData = json.optString("mediaData").ifBlank { null },
                mediaUrl = json.optString("mediaUrl").ifBlank { null },
                authorPubkey = json.optString("authorPubkey").ifBlank { null },
                isEdited = json.optBoolean("isEdited", false),
                editedAt = json.optLong("editedAt", 0L).takeIf { it > 0L },
                receivedAt = json.optLong("receivedAt", json.optLong("timestamp", System.currentTimeMillis()))
            )
        }
    }
}
