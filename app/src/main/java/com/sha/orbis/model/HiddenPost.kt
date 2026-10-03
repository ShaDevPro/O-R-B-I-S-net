package com.sha.orbis.model

import org.json.JSONObject

data class HiddenPost(
    val postId: String,
    val authorPhone: String,
    val authorName: String,
    val contentPreview: String,
    val hiddenAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("postId", postId)
        put("authorPhone", authorPhone)
        put("authorName", authorName)
        put("contentPreview", contentPreview)
        put("hiddenAt", hiddenAt)
    }

    companion object {
        fun fromJson(json: JSONObject): HiddenPost = HiddenPost(
            postId = json.optString("postId"),
            authorPhone = json.optString("authorPhone"),
            authorName = json.optString("authorName", "Unknown"),
            contentPreview = json.optString("contentPreview"),
            hiddenAt = json.optLong("hiddenAt", System.currentTimeMillis())
        )
    }
}
