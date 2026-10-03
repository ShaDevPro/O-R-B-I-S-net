package com.sha.orbis.model

import org.json.JSONObject

data class Conversation(
    val id: String,
    val title: String,
    val participants: List<String>,
    val lastMessage: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val disappearingTimerSeconds: Long = 0L, // 0 = désactivé, 3600 = 1h, 86400 = 24h, 604800 = 7j
    val isPinned: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("participants", participants)
        put("lastMessage", lastMessage)
        put("updatedAt", updatedAt)
        put("unreadCount", unreadCount)
        put("isGroup", isGroup)
        put("disappearingTimerSeconds", disappearingTimerSeconds)
        put("isPinned", isPinned)
    }

    companion object {
        fun fromJson(json: JSONObject): Conversation = Conversation(
            id = json.optString("id"),
            title = json.optString("title"),
            participants = json.optJSONArray("participants")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList(),
            lastMessage = json.optString("lastMessage"),
            updatedAt = json.optLong("updatedAt"),
            unreadCount = json.optInt("unreadCount"),
            isGroup = json.optBoolean("isGroup", false),
            disappearingTimerSeconds = json.optLong("disappearingTimerSeconds", 0L),
            isPinned = json.optBoolean("isPinned", false)
        )
    }
}
