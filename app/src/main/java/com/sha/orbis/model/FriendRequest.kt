package com.sha.orbis.model

import org.json.JSONObject

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

enum class RequestDirection {
    RECEIVED,
    SENT
}

enum class TrustLevel {
    LEVEL_1_UNVERIFIED,
    LEVEL_2_SMS_EXCHANGED,
    LEVEL_3_IN_PERSON_CERTIFIED
}

data class FriendRequest(
    val id: String,
    val senderPhone: String,
    val senderName: String,
    val senderAvatarPath: String? = null,
    val senderPublicKey: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val direction: RequestDirection = RequestDirection.RECEIVED,
    val mutualFriendsCount: Int = 0,
    val trustLevel: TrustLevel = TrustLevel.LEVEL_2_SMS_EXCHANGED,
    val groupKey: String = "",
    val peerFcmToken: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("senderPhone", senderPhone)
        put("senderName", senderName)
        put("senderAvatarPath", senderAvatarPath ?: "")
        put("senderPublicKey", senderPublicKey)
        put("timestamp", timestamp)
        put("status", status.name)
        put("direction", direction.name)
        put("mutualFriendsCount", mutualFriendsCount)
        put("trustLevel", trustLevel.name)
        put("groupKey", groupKey)
        put("peerFcmToken", peerFcmToken ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): FriendRequest = FriendRequest(
            id = json.optString("id"),
            senderPhone = json.optString("senderPhone"),
            senderName = json.optString("senderName"),
            senderAvatarPath = json.optString("senderAvatarPath").ifBlank { null },
            senderPublicKey = json.optString("senderPublicKey"),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            status = try {
                FriendRequestStatus.valueOf(json.optString("status", "PENDING"))
            } catch (_: Exception) {
                FriendRequestStatus.PENDING
            },
            direction = try {
                RequestDirection.valueOf(json.optString("direction", "RECEIVED"))
            } catch (_: Exception) {
                RequestDirection.RECEIVED
            },
            mutualFriendsCount = json.optInt("mutualFriendsCount", 0),
            trustLevel = try {
                TrustLevel.valueOf(json.optString("trustLevel", "LEVEL_2_SMS_EXCHANGED"))
            } catch (_: Exception) {
                TrustLevel.LEVEL_2_SMS_EXCHANGED
            },
            groupKey = json.optString("groupKey", ""),
            peerFcmToken = json.optString("peerFcmToken").ifBlank { null }
        )
    }
}
