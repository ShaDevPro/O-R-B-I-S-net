package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.model.HiddenPost
import com.sha.orbis.social.SocialPost
import org.json.JSONArray
import java.io.File

class HiddenPostsRepository(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId?.takeIf { it.isNotBlank() } ?: try {
        com.sha.orbis.data.SessionManager(context).activeAccountId
    } catch (_: Exception) { "" }

    private val storageFile: File by lazy {
        AccountStorageManager.getAccountFile(context, "hidden_posts.json", currentAccountId)
    }

    @Synchronized
    fun loadHiddenPosts(): List<HiddenPost> {
        if (!storageFile.exists()) return emptyList()
        return try {
            val array = JSONArray(storageFile.readText())
            List(array.length()) { index -> HiddenPost.fromJson(array.getJSONObject(index)) }
                .filter { it.postId.isNotBlank() }
                .sortedByDescending { it.hiddenAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveHiddenPosts(items: List<HiddenPost>) {
        val array = JSONArray()
        items.distinctBy { it.postId }.forEach { array.put(it.toJson()) }
        storageFile.writeText(array.toString(2))
    }

    fun isHidden(postId: String?): Boolean {
        if (postId.isNullOrBlank()) return false
        return loadHiddenPosts().any { it.postId == postId }
    }

    @Synchronized
    fun hidePost(post: SocialPost) {
        if (post.id.isBlank()) return
        val current = loadHiddenPosts().filterNot { it.postId == post.id }.toMutableList()
        current.add(
            0,
            HiddenPost(
                postId = post.id,
                authorPhone = post.authorPhone,
                authorName = post.authorName,
                contentPreview = post.content.take(90)
            )
        )
        saveHiddenPosts(current)
    }

    @Synchronized
    fun unhidePost(postId: String): Boolean {
        val current = loadHiddenPosts().toMutableList()
        val removed = current.removeAll { it.postId == postId }
        if (removed) saveHiddenPosts(current)
        return removed
    }
}
