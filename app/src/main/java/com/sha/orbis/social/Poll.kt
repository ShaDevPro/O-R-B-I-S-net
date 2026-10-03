package com.sha.orbis.social

import org.json.JSONArray
import org.json.JSONObject

data class Poll(
    val id: String,
    val question: String,
    val options: List<String>,
    val votes: Map<String, Int>,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("question", question)
        put("options", JSONArray().apply { options.forEach { put(it) } })
        put("votes", JSONObject().apply {
            votes.forEach { (key, value) -> put(key, value) }
        })
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Poll = Poll(
            id = json.optString("id"),
            question = json.optString("question"),
            options = json.optJSONArray("options")?.let { array ->
                List(array.length()) { index -> array.getString(index) }
            } ?: emptyList(),
            votes = json.optJSONObject("votes")?.let { obj ->
                buildMap {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, obj.getInt(key))
                    }
                }
            } ?: emptyMap(),
            createdAt = json.optLong("createdAt")
        )
    }
}
