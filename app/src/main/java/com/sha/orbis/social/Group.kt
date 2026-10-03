package com.sha.orbis.social

import org.json.JSONObject

data class Group(
    val id: String,
    val name: String,
    val description: String,
    val members: List<String>,
    val groupKey: String,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("members", org.json.JSONArray().apply { members.forEach { put(it) } })
        put("groupKey", groupKey)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Group = Group(
            id = json.optString("id"),
            name = json.optString("name"),
            description = json.optString("description"),
            members = json.optJSONArray("members")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList(),
            groupKey = json.optString("groupKey"),
            createdAt = json.optLong("createdAt")
        )
    }
}
