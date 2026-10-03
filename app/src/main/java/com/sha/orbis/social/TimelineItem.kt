package com.sha.orbis.social

import org.json.JSONObject

data class TimelineItem(
    val id: String,
    val author: String,
    val content: String,
    val encrypted: Boolean,
    val timestamp: Long,
    val reactions: List<Reaction>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("author", author)
        put("content", content)
        put("encrypted", encrypted)
        put("timestamp", timestamp)
        put("reactions", org.json.JSONArray().apply { reactions.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): TimelineItem = TimelineItem(
            id = json.optString("id"),
            author = json.optString("author"),
            content = json.optString("content"),
            encrypted = json.optBoolean("encrypted"),
            timestamp = json.optLong("timestamp"),
            reactions = json.optJSONArray("reactions")?.let { array ->
                List(array.length()) { index -> Reaction.fromJson(array.getJSONObject(index)) }
            } ?: emptyList()
        )
    }
}
