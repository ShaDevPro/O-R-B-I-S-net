package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialStory

/**
 * Persistent local storage for social data (posts, stories, reactions, comments) — public stub.
 * Full caching, merge, and disk-write logic is proprietary and not included in this repository.
 */
class SocialRepository(
    private val context: Context,
    private val accountId: String? = null
) {

    companion object {
        fun invalidateCache() {}
        fun invalidateCacheForAccount(accountId: String) {}
    }

    // ── Posts ──────────────────────────────────────────────────────────────────

    fun loadPosts(): List<SocialPost> = emptyList()
    fun savePosts(posts: List<SocialPost>) {}
    fun addPost(post: SocialPost) {}
    fun editPost(postId: String, newContent: String, newHashtags: List<String>): Boolean = false
    fun deletePost(postId: String) {}
    fun togglePinPost(postId: String) {}
    fun findPostById(postId: String?): SocialPost? = null
    fun isPostDeleted(postId: String?): Boolean = false
    fun markPostAsDeleted(postId: String) {}

    fun repost(
        originalPost: SocialPost,
        currentUserPhone: String,
        currentUserName: String,
        currentUserAvatar: String?
    ): SocialPost = originalPost

    // ── Reactions ──────────────────────────────────────────────────────────────

    fun addReaction(postId: String, userPhone: String, emoji: String): Boolean = false
    fun applyIncomingReaction(postId: String, userPhone: String, emoji: String): Boolean = false

    // ── Comments ──────────────────────────────────────────────────────────────

    fun addComment(postId: String, comment: SocialComment): Boolean = false
    fun deleteComment(postId: String, commentId: String) {}
    fun editComment(postId: String, commentId: String, newText: String): Boolean = false
    fun addCommentReaction(postId: String, commentId: String, userPhone: String, emoji: String) {}
    fun applyIncomingCommentReaction(
        postId: String, commentId: String, userPhone: String, emoji: String
    ): Boolean = false

    // ── Polls ─────────────────────────────────────────────────────────────────

    fun votePoll(
        postId: String, optionId: String, voterPhone: String, isLocalUser: Boolean = false
    ): Boolean = false
    fun applyIncomingPollVote(postId: String, optionId: String, voterPhone: String): Boolean = false

    // ── Stories ───────────────────────────────────────────────────────────────

    fun loadStories(): List<SocialStory> = emptyList()
    fun saveStories(stories: List<SocialStory>) {}
    fun addStory(story: SocialStory) {}
    fun markStorySeen(storyId: String, viewerPhone: String): Boolean = false
    fun addStoryReaction(storyId: String, userPhone: String, emoji: String): Boolean = false
    fun deleteStory(storyId: String) {}

    // ── Pending engagement queue ───────────────────────────────────────────────

    fun enqueuePendingComment(comment: SocialComment): Boolean = false
    fun enqueuePendingReaction(postId: String, userPhone: String, emoji: String): Boolean = false
    fun enqueuePendingCommentReaction(
        postId: String, commentId: String, userPhone: String, emoji: String
    ): Boolean = false
    fun enqueuePendingPollVote(postId: String, optionId: String, voterPhone: String): Boolean = false
}
