package com.sha.orbis.model

import org.json.JSONObject

enum class MessageDeliveryStatus {
    SENDING,   // En cours d'émission GSM (🕒)
    SENT,      // ✓ Envoyé au réseau GSM
    DELIVERED, // ✓✓ Livré au téléphone distant (Accusé réseau DLR)
    READ,      // ✓✓ Lu / Déchiffré
    FAILED     // ⚠️ Échec de transmission (Solde insuffisant ou réseau indisponible)
}

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val encrypted: Boolean = true,
    val status: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    val expiresAt: Long? = null, // Timestamp d'autodestruction locale (null = permanent)
    val isDeletedForEveryone: Boolean = false,
    val networkEventId: String? = null,
    val reactions: Map<String, String> = emptyMap() // senderPhone -> emoji
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("conversationId", conversationId)
        put("senderId", senderId)
        put("text", text)
        put("timestamp", timestamp)
        put("encrypted", encrypted)
        put("status", status.name)
        if (expiresAt != null) {
            put("expiresAt", expiresAt)
        }
        put("isDeletedForEveryone", isDeletedForEveryone)
        if (!networkEventId.isNullOrBlank()) {
            put("networkEventId", networkEventId)
        }
        if (reactions.isNotEmpty()) {
            val reactionsObj = JSONObject()
            reactions.forEach { (phone, emoji) -> reactionsObj.put(phone, emoji) }
            put("reactions", reactionsObj)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Message = Message(
            id = json.optString("id"),
            conversationId = json.optString("conversationId"),
            senderId = json.optString("senderId"),
            text = json.optString("text"),
            timestamp = json.optLong("timestamp"),
            encrypted = json.optBoolean("encrypted", true),
            status = try {
                MessageDeliveryStatus.valueOf(json.optString("status", MessageDeliveryStatus.SENT.name))
            } catch (_: Exception) {
                MessageDeliveryStatus.SENT
            },
            expiresAt = if (json.has("expiresAt") && !json.isNull("expiresAt")) json.optLong("expiresAt") else null,
            isDeletedForEveryone = json.optBoolean("isDeletedForEveryone", false),
            networkEventId = json.optString("networkEventId").ifBlank { null },
            reactions = json.optJSONObject("reactions")?.let { obj ->
                buildMap {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, obj.getString(key))
                    }
                }
            } ?: emptyMap()
        )
    }
}
