package com.sha.orbis.model

import org.json.JSONObject

enum class CallDirection {
    OUTGOING,
    INCOMING,
    MISSED
}

data class CallRecord(
    val id: String,
    val peerPhone: String,
    val peerName: String,
    val peerAvatar: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val direction: CallDirection = CallDirection.OUTGOING,
    val isVideo: Boolean = false,
    val isRead: Boolean = (direction != CallDirection.MISSED),
    val threatLevel: String? = null,
    val threatType: String? = null,
    val trustScore: Int = 100
) {
    val isMissed: Boolean
        get() = direction == CallDirection.MISSED

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("peerPhone", peerPhone)
        put("peerName", peerName)
        put("peerAvatar", peerAvatar ?: "")
        put("timestamp", timestamp)
        put("durationSeconds", durationSeconds)
        put("direction", direction.name)
        put("isVideo", isVideo)
        put("isRead", isRead)
        threatLevel?.let { put("threatLevel", it) }
        threatType?.let { put("threatType", it) }
        put("trustScore", trustScore)
    }

    companion object {
        fun fromJson(json: JSONObject): CallRecord {
            val dir = try {
                CallDirection.valueOf(json.optString("direction", "OUTGOING"))
            } catch (_: Exception) {
                CallDirection.OUTGOING
            }
            return CallRecord(
                id = json.optString("id"),
                peerPhone = json.optString("peerPhone"),
                peerName = json.optString("peerName"),
                peerAvatar = json.optString("peerAvatar").ifBlank { null },
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                durationSeconds = json.optInt("durationSeconds", 0),
                direction = dir,
                isVideo = json.optBoolean("isVideo", false),
                isRead = json.optBoolean("isRead", dir != CallDirection.MISSED),
                threatLevel = json.optString("threatLevel").ifBlank { null },
                threatType = json.optString("threatType").ifBlank { null },
                trustScore = json.optInt("trustScore", 100)
            )
        }
    }
}
