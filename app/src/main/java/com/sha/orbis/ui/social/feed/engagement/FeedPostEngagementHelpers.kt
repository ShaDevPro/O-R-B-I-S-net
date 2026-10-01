package com.sha.orbis.ui.social.feed.engagement

import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost

/** Repost / shared-to-wall posts: no inline comment teaser on the feed (Instagram-style). */
fun SocialPost.isRepostOrSharedOnFeed(): Boolean {
    return !repostOriginalPostId.isNullOrBlank() ||
        !repostAuthorName.isNullOrBlank() ||
        id.startsWith("repost_")
}

fun partitionCommentThreads(comments: List<SocialComment>): Pair<List<SocialComment>, Map<String, List<SocialComment>>> {
    val repliesByParentId = comments
        .filter { !it.replyToCommentId.isNullOrBlank() }
        .groupBy { it.replyToCommentId!! }
    val topLevel = comments.filter { comment ->
        comment.replyToCommentId.isNullOrBlank() ||
            !comments.any { it.id == comment.replyToCommentId }
    }
    return topLevel to repliesByParentId
}
