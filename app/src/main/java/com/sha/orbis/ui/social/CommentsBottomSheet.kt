package com.sha.orbis.ui.social

import androidx.compose.runtime.Composable
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.ui.social.feed.engagement.FeedCommentsSheet

/**
 * Public entry point for the comments bottom sheet (delegates to modular feed/engagement UI).
 */
@Composable
fun CommentsBottomSheet(
    post: SocialPost,
    currentPhone: String = "",
    currentUserName: String = "",
    currentUserAvatarPath: String? = null,
    onDismiss: () -> Unit,
    onAddComment: (text: String, replyToId: String?, replyToName: String?) -> Unit,
    onEditComment: ((commentId: String, newText: String) -> Unit)? = null,
    onDeleteComment: ((commentId: String) -> Unit)? = null,
    onReactComment: ((commentId: String, emoji: String) -> Unit)? = null,
    onUserClick: ((phone: String, name: String, avatar: String?) -> Unit)? = null
) {
    FeedCommentsSheet(
        post = post,
        currentPhone = currentPhone,
        currentUserName = currentUserName,
        currentUserAvatarPath = currentUserAvatarPath,
        onDismiss = onDismiss,
        onAddComment = onAddComment,
        onEditComment = onEditComment,
        onDeleteComment = onDeleteComment,
        onReactComment = onReactComment,
        onUserClick = onUserClick
    )
}
