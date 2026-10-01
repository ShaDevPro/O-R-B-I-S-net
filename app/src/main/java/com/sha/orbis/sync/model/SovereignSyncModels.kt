package com.sha.orbis.sync.model

import com.sha.orbis.model.CallRecord
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.Message
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialStory
import org.json.JSONArray
import org.json.JSONObject

enum class SyncAction {
    EXCHANGE_REQUEST,   // Request exchange & send local authorized data
    EXCHANGE_RESPONSE,  // Response back with recipient's authorized data
    DELTA_EXCHANGE,     // Periodic update delta
    HEARTBEAT           // Real-time presence/liveness check
}

data class SyncPresenceItem(
    val phone: String,
    val pubkey: String? = null,
    val isOnline: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val displayName: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("phone", phone)
        if (!pubkey.isNullOrBlank()) put("pubkey", pubkey)
        put("isOnline", isOnline)
        put("timestamp", timestamp)
        if (!displayName.isNullOrBlank()) put("displayName", displayName)
    }

    companion object {
        fun fromJson(json: JSONObject): SyncPresenceItem = SyncPresenceItem(
            phone = json.optString("phone"),
            pubkey = json.optString("pubkey").ifBlank { null },
            isOnline = json.optBoolean("isOnline", true),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            displayName = json.optString("displayName").ifBlank { null }
        )
    }
}

data class SovereignSyncBundle(
    val posts: List<SocialPost> = emptyList(),
    val stories: List<SocialStory> = emptyList(),
    val messages: List<Message> = emptyList(),
    val calls: List<CallRecord> = emptyList(),
    val presence: SyncPresenceItem? = null,
    val circleMembership: List<String> = emptyList(),
    val groupConversations: List<Conversation> = emptyList(),
    val groupMessages: List<Message> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("posts", JSONArray().apply { posts.forEach { put(it.toJson()) } })
        put("stories", JSONArray().apply { stories.forEach { put(it.toJson()) } })
        put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
        put("calls", JSONArray().apply { calls.forEach { put(it.toJson()) } })
        presence?.let { put("presence", it.toJson()) }
        if (circleMembership.isNotEmpty()) put("circleMembership", JSONArray(circleMembership))
        if (groupConversations.isNotEmpty()) put("groupConversations", JSONArray().apply { groupConversations.forEach { put(it.toJson()) } })
        if (groupMessages.isNotEmpty()) put("groupMessages", JSONArray().apply { groupMessages.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): SovereignSyncBundle {
            val postsList = mutableListOf<SocialPost>()
            json.optJSONArray("posts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    try { postsList.add(SocialPost.fromJson(arr.getJSONObject(i))) } catch (_: Exception) {}
                }
            }

            val storiesList = mutableListOf<SocialStory>()
            json.optJSONArray("stories")?.let { arr ->
                for (i in 0 until arr.length()) {
                    try { storiesList.add(SocialStory.fromJson(arr.getJSONObject(i))) } catch (_: Exception) {}
                }
            }

            val messagesList = mutableListOf<Message>()
            json.optJSONArray("messages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    try { messagesList.add(Message.fromJson(arr.getJSONObject(i))) } catch (_: Exception) {}
                }
            }

            val callsList = mutableListOf<CallRecord>()
            json.optJSONArray("calls")?.let { arr ->
                for (i in 0 until arr.length()) {
                    try { callsList.add(CallRecord.fromJson(arr.getJSONObject(i))) } catch (_: Exception) {}
                }
            }

            val presenceItem = json.optJSONObject("presence")?.let {
                try { SyncPresenceItem.fromJson(it) } catch (_: Exception) { null }
            }

            val circlesList = json.optJSONArray("circleMembership")?.let { arr ->
                List(arr.length()) { index -> arr.getString(index) }
            } ?: emptyList()

            val groupConvsList = mutableListOf<Conversation>()
            json.optJSONArray("groupConversations")?.let { arr ->
                for (i in 0 until arr.length()) {
                    try { groupConvsList.add(Conversation.fromJson(arr.getJSONObject(i))) } catch (_: Exception) {}
                }
            }

            val groupMsgsList = mutableListOf<Message>()
            json.optJSONArray("groupMessages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    try { groupMsgsList.add(Message.fromJson(arr.getJSONObject(i))) } catch (_: Exception) {}
                }
            }

            return SovereignSyncBundle(
                posts = postsList,
                stories = storiesList,
                messages = messagesList,
                calls = callsList,
                presence = presenceItem,
                circleMembership = circlesList,
                groupConversations = groupConvsList,
                groupMessages = groupMsgsList
            )
        }
    }
}

data class SovereignSyncPacket(
    val version: Int = 1,
    val action: SyncAction = SyncAction.EXCHANGE_REQUEST,
    val senderPhone: String,
    val senderPubkey: String,
    val senderName: String = "",
    val targetPeerPhone: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val bundle: SovereignSyncBundle = SovereignSyncBundle()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("action", action.name)
        put("senderPhone", senderPhone)
        put("senderPubkey", senderPubkey)
        put("senderName", senderName)
        put("targetPeerPhone", targetPeerPhone)
        put("timestamp", timestamp)
        put("bundle", bundle.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): SovereignSyncPacket {
            val act = try {
                SyncAction.valueOf(json.optString("action", SyncAction.EXCHANGE_REQUEST.name))
            } catch (_: Exception) {
                SyncAction.EXCHANGE_REQUEST
            }

            val bundleObj = json.optJSONObject("bundle")
            val bundle = if (bundleObj != null) {
                SovereignSyncBundle.fromJson(bundleObj)
            } else {
                SovereignSyncBundle()
            }

            return SovereignSyncPacket(
                version = json.optInt("version", 1),
                action = act,
                senderPhone = json.optString("senderPhone"),
                senderPubkey = json.optString("senderPubkey"),
                senderName = json.optString("senderName"),
                targetPeerPhone = json.optString("targetPeerPhone"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                bundle = bundle
            )
        }
    }
}
