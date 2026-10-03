package com.sha.orbis.model

import org.json.JSONArray
import org.json.JSONObject

data class AccountProfile(
    val id: String,                  // Unique ID e.g. "acc_+213550123456"
    val name: String,                // Display name e.g. "Bob" or "Bob Pro"
    val phoneNumber: String,         // Full phone number e.g. "+213550123456"
    val simSlotIndex: Int,           // 0 for SIM 1, 1 for SIM 2, -1 for Sandbox
    val subscriptionId: Int,         // Android Subscription ID for SMS routing
    val operatorName: String,        // Operator e.g. "Mobilis", "Djezzy", "Ooredoo"
    val countryIso: String,          // Country ISO e.g. "DZ"
    val publicKeyBase64: String,     // Dedicated RSA-2048 Public Key
    val privateKeyBase64: String,    // Dedicated RSA-2048 Private Key
    val avatarPath: String? = null,  // Local file path to profile avatar
    val bio: String = "",            // Bio / status / quote
    val jobTitle: String = "",       // Profession / occupation
    val location: String = "",       // City / Region
    val interests: List<String> = emptyList(), // Topics / tags e.g. ["Tech", "Art"]
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    // SIM Security binding (multi-layer matching)
    val cardId: Int = -1,            // Physical SIM card ID (API 29+), -1 if unavailable
    val iccIdHash: String? = null    // SHA-256 hash of SIM ICCID (never store raw ICCID)
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phoneNumber", phoneNumber)
        put("simSlotIndex", simSlotIndex)
        put("subscriptionId", subscriptionId)
        put("operatorName", operatorName)
        put("countryIso", countryIso)
        put("publicKeyBase64", publicKeyBase64)
        put("privateKeyBase64", privateKeyBase64)
        if (!avatarPath.isNullOrBlank()) {
            put("avatarPath", avatarPath)
        }
        put("bio", bio)
        put("jobTitle", jobTitle)
        put("location", location)
        val arr = JSONArray()
        interests.forEach { arr.put(it) }
        put("interests", arr)
        put("isDefault", isDefault)
        put("createdAt", createdAt)
        if (cardId >= 0) put("cardId", cardId)
        if (!iccIdHash.isNullOrBlank()) put("iccIdHash", iccIdHash)
    }

    companion object {
        fun fromJson(json: JSONObject): AccountProfile {
            val interestsList = mutableListOf<String>()
            val arr = json.optJSONArray("interests")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    interestsList.add(arr.getString(i))
                }
            }

            return AccountProfile(
                id = json.getString("id"),
                name = json.optString("name", "Utilisateur"),
                phoneNumber = json.getString("phoneNumber"),
                simSlotIndex = json.optInt("simSlotIndex", 0),
                subscriptionId = json.optInt("subscriptionId", -1),
                operatorName = json.optString("operatorName", "Opérateur GSM"),
                countryIso = json.optString("countryIso", "DZ"),
                publicKeyBase64 = json.getString("publicKeyBase64"),
                privateKeyBase64 = json.getString("privateKeyBase64"),
                avatarPath = json.optString("avatarPath").ifBlank { null },
                bio = json.optString("bio", ""),
                jobTitle = json.optString("jobTitle", ""),
                location = json.optString("location", ""),
                interests = interestsList,
                isDefault = json.optBoolean("isDefault", false),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                cardId = json.optInt("cardId", -1),
                iccIdHash = json.optString("iccIdHash").ifBlank { null }
            )
        }
    }
}
