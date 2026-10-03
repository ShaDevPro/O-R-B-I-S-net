package com.sha.orbis.model

import org.json.JSONArray
import org.json.JSONObject

data class FriendCircle(
    val id: String,
    val name: String,
    val iconEmoji: String = "👥",
    val memberPhones: List<String> = emptyList(),
    val colorHex: String = "#0E1117"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("iconEmoji", iconEmoji)
        put("memberPhones", JSONArray(memberPhones))
        put("colorHex", colorHex)
    }

    companion object {
        fun fromJson(json: JSONObject): FriendCircle = FriendCircle(
            id = json.optString("id"),
            name = json.optString("name"),
            iconEmoji = json.optString("iconEmoji", "👥"),
            memberPhones = json.optJSONArray("memberPhones")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList(),
            colorHex = json.optString("colorHex", "#0E1117")
        )
    }
}
