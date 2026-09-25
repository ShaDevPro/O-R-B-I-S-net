package com.sha.orbis.social

import org.json.JSONObject

data class Reaction(
    val id: String,
    val messageId: String,
    val userId: String,
    val type: String,
    val timestamp: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("messageId", messageId)
        put("userId", userId)
        put("type", type)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): Reaction = Reaction(
            id = json.optString("id"),
            messageId = json.optString("messageId"),
            userId = json.optString("userId"),
            type = json.optString("type"),
            timestamp = json.optLong("timestamp")
        )
    }
}
