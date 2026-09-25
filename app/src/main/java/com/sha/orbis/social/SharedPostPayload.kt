package com.sha.orbis.social

import org.json.JSONArray
import org.json.JSONObject

data class SharedPostPayload(
    val postId: String,
    val authorPhone: String,
    val authorName: String,
    val authorAvatarPath: String? = null,
    val contentSnippet: String,
    val hashtags: List<String> = emptyList(),
    val note: String? = null
) {
    fun encode(): String {
        val json = JSONObject().apply {
            put("type", "shared_post")
            put("postId", postId)
            put("authorPhone", authorPhone)
            put("authorName", authorName)
            if (!authorAvatarPath.isNullOrBlank()) {
                put("authorAvatarPath", authorAvatarPath)
            }
            put("snippet", contentSnippet)
            put("hashtags", JSONArray(hashtags))
            if (!note.isNullOrBlank()) {
                put("note", note)
            }
        }
        return PREFIX + json.toString()
    }

    companion object {
        const val PREFIX = "[ORB_POST_SHARE]"

        fun parse(text: String): SharedPostPayload? {
            if (!text.startsWith(PREFIX)) return null
            return try {
                val rawJson = text.removePrefix(PREFIX)
                val json = JSONObject(rawJson)
                if (json.optString("type") != "shared_post") return null

                val tags = mutableListOf<String>()
                json.optJSONArray("hashtags")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        tags.add(arr.getString(i))
                    }
                }

                SharedPostPayload(
                    postId = json.optString("postId"),
                    authorPhone = json.optString("authorPhone"),
                    authorName = json.optString("authorName"),
                    authorAvatarPath = if (json.has("authorAvatarPath")) json.optString("authorAvatarPath") else null,
                    contentSnippet = json.optString("snippet"),
                    hashtags = tags,
                    note = if (json.has("note")) json.optString("note") else null
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
