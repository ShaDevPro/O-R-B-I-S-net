package com.sha.orbis.model

import org.json.JSONObject

data class BlockedContact(
    val phone: String,
    val name: String,
    val blockedAt: Long = System.currentTimeMillis(),
    val reason: String = "Bloqué par l'utilisateur",
    val publicKey: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("phone", phone)
        put("name", name)
        put("blockedAt", blockedAt)
        put("reason", reason)
        if (!publicKey.isNullOrBlank()) {
            put("publicKey", publicKey)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): BlockedContact = BlockedContact(
            phone = json.optString("phone"),
            name = json.optString("name", "Inconnu"),
            blockedAt = json.optLong("blockedAt", System.currentTimeMillis()),
            reason = json.optString("reason", "Bloqué"),
            publicKey = json.optString("publicKey").takeIf { it.isNotBlank() }
        )
    }
}
