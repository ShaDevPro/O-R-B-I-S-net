package com.sha.orbis.model

import org.json.JSONObject

enum class NotificationType {
    MESSAGE,
    PLAIN_SMS,
    FRIEND_REQUEST,
    SECURITY,
    SIM_STATUS,
    SOCIAL,
    MISSED_CALL
}

data class AppNotification(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: NotificationType = NotificationType.MESSAGE,
    val isRead: Boolean = false,
    val senderPhone: String? = null,
    val targetConvId: String? = null,
    val senderAvatarPath: String? = null,
    val senderName: String? = null,
    val targetPostId: String? = null,
    val actionType: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("timestamp", timestamp)
        put("type", type.name)
        put("isRead", isRead)
        put("senderPhone", senderPhone ?: "")
        put("targetConvId", targetConvId ?: "")
        put("senderAvatarPath", senderAvatarPath ?: "")
        put("senderName", senderName ?: "")
        put("targetPostId", targetPostId ?: "")
        put("actionType", actionType ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): AppNotification = AppNotification(
            id = json.optString("id"),
            title = json.optString("title"),
            description = json.optString("description"),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            type = try {
                NotificationType.valueOf(json.optString("type", "MESSAGE"))
            } catch (_: Exception) {
                NotificationType.MESSAGE
            },
            isRead = json.optBoolean("isRead", false),
            senderPhone = json.optString("senderPhone").ifBlank { null },
            targetConvId = json.optString("targetConvId").ifBlank { null },
            senderAvatarPath = json.optString("senderAvatarPath").ifBlank { null },
            senderName = json.optString("senderName").ifBlank { null },
            targetPostId = json.optString("targetPostId").ifBlank { null },
            actionType = json.optString("actionType").ifBlank { null }
        )
    }
}

