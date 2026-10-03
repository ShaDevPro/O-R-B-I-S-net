package com.sha.orbis.model

import org.json.JSONObject

data class PrivateConversationLock(
    val conversationId: String,
    val conversationTitle: String,
    val password: String,
    val recoveryAnswer1: String,
    val recoveryAnswer2: String,
    val recoveryAnswer3: String,
    val lockedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("conversationId", conversationId)
        put("conversationTitle", conversationTitle)
        put("password", password)
        put("recoveryAnswer1", recoveryAnswer1)
        put("recoveryAnswer2", recoveryAnswer2)
        put("recoveryAnswer3", recoveryAnswer3)
        put("lockedAt", lockedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): PrivateConversationLock = PrivateConversationLock(
            conversationId = json.optString("conversationId"),
            conversationTitle = json.optString("conversationTitle"),
            password = json.optString("password"),
            recoveryAnswer1 = json.optString("recoveryAnswer1"),
            recoveryAnswer2 = json.optString("recoveryAnswer2"),
            recoveryAnswer3 = json.optString("recoveryAnswer3"),
            lockedAt = json.optLong("lockedAt", System.currentTimeMillis())
        )
    }
}
