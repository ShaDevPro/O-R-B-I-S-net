package com.sha.orbis.model

import org.json.JSONArray
import org.json.JSONObject

data class Contact(
    val id: String = "",
    val name: String,
    val phone: String,
    val publicKey: String = "",
    val status: String = "",
    val avatarPath: String? = null,
    val bio: String = "",
    val jobTitle: String = "",
    val location: String = "",
    val interests: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phone", phone)
        put("publicKey", publicKey)
        put("status", status)
        if (!avatarPath.isNullOrBlank()) {
            put("avatarPath", avatarPath)
        }
        put("bio", bio)
        put("jobTitle", jobTitle)
        put("location", location)
        val arr = JSONArray()
        interests.forEach { arr.put(it) }
        put("interests", arr)
    }

    companion object {
        fun fromJson(json: JSONObject): Contact {
            val interestsList = mutableListOf<String>()
            val arr = json.optJSONArray("interests")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    interestsList.add(arr.getString(i))
                }
            }

            return Contact(
                id = json.optString("id"),
                name = json.optString("name"),
                phone = json.optString("phone"),
                publicKey = json.optString("publicKey"),
                status = json.optString("status"),
                avatarPath = json.optString("avatarPath").ifBlank { null },
                bio = json.optString("bio", ""),
                jobTitle = json.optString("jobTitle", ""),
                location = json.optString("location", ""),
                interests = interestsList
            )
        }
    }
}
