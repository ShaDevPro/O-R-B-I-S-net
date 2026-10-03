package com.sha.orbis.ui.social.feed.engagement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.sha.orbis.R
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.ui.social.feed.FeedDesignTokens
import com.sha.orbis.ui.social.feed.popups.FeedBottomSheetDragHandle
import com.sha.orbis.ui.social.feed.popups.FeedCommentComposerRow
import com.sha.orbis.ui.social.feed.popups.FeedCommentReplyBanner
import com.sha.orbis.ui.social.feed.popups.FeedDeleteCommentConfirmDialog
import com.sha.orbis.ui.social.feed.popups.FeedEditCommentDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedCommentsSheet(
    post: SocialPost,
    currentPhone: String,
    currentUserName: String = "",
    currentUserAvatarPath: String? = null,
    onDismiss: () -> Unit,
    onAddComment: (text: String, replyToId: String?, replyToName: String?) -> Unit,
    onEditComment: ((commentId: String, newText: String) -> Unit)? = null,
    onDeleteComment: ((commentId: String) -> Unit)? = null,
    onReactComment: ((commentId: String, emoji: String) -> Unit)? = null,
    onUserClick: ((phone: String, name: String, avatar: String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var newCommentText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<SocialComment?>(null) }
    var commentToDelete by remember { mutableStateOf<SocialComment?>(null) }
    var commentToEdit by remember { mutableStateOf<SocialComment?>(null) }
    var editCommentText by remember { mutableStateOf("") }

    LaunchedEffect(post.id) {
        withContext(Dispatchers.IO) {
            try {
                val notifRepo = com.sha.orbis.storage.NotificationRepository(context)
                notifRepo.markPostNotificationsAsRead(post.id)
                com.sha.orbis.data.OrbisBadgeHub.refresh(context)
            } catch (_: Exception) {
            }
        }
    }

    val visibleComments = remember(post.comments, post.id, currentPhone) {
        com.sha.orbis.social.SocialEngagementAudiencePolicy.filterVisibleComments(
            comments = post.comments,
            post = post,
            viewerPhone = currentPhone,
            context = context
        )
    }

    val screenHeightDp = configuration.screenHeightDp.dp
    val minSheetHeight = screenHeightDp * 0.42f
    val maxSheetHeight = screenHeightDp * 0.92f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { FeedBottomSheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minSheetHeight, max = maxSheetHeight)
                .wrapContentHeight(Alignment.Top)
                .navigationBarsPadding()
                .imePadding()
        ) {
            FeedCommentsSheetPostHeader(post = post, currentPhone = currentPhone)

            if (visibleComments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = FeedDesignTokens.ContentPaddingHorizontal),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.social_comments_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                FeedCommentThreadList(
                    post = post,
                    visibleComments = visibleComments,
                    currentPhone = currentPhone,
                    onEditComment = onEditComment,
                    onReactComment = onReactComment,
                    onReplyTo = { replyingToComment = it },
                    onRequestEdit = { comment ->
                        commentToEdit = comment
                        editCommentText = comment.text
                    },
                    onRequestDelete = { commentToDelete = it },
                    onUserClick = onUserClick,
                    modifier = Modifier.weight(1f)
                )
            }

            if (replyingToComment != null) {
                FeedCommentReplyBanner(
                    replyingTo = replyingToComment!!,
                    onDismissReply = { replyingToComment = null }
                )
            }

            FeedCommentComposerRow(
                text = newCommentText,
                onTextChange = { newCommentText = it },
                replyingTo = replyingToComment,
                currentUserName = currentUserName,
                currentUserAvatarPath = currentUserAvatarPath,
                onSend = {
                    if (newCommentText.isNotBlank()) {
                        onAddComment(
                            newCommentText.trim(),
                            replyingToComment?.id,
                            replyingToComment?.authorName
                        )
                        newCommentText = ""
                        replyingToComment = null
                    }
                }
            )
        }
    }

    if (commentToDelete != null) {
        val c = commentToDelete!!
        FeedDeleteCommentConfirmDialog(
            commentPreview = "\"${c.text}\"",
            onDismiss = { commentToDelete = null },
            onConfirm = {
                onDeleteComment?.invoke(c.id)
                commentToDelete = null
            }
        )
    }

    if (commentToEdit != null) {
        val c = commentToEdit!!
        FeedEditCommentDialog(
            editText = editCommentText,
            onEditTextChange = { editCommentText = it },
            onDismiss = { commentToEdit = null },
            onSave = {
                if (editCommentText.isNotBlank()) {
                    onEditComment?.invoke(c.id, editCommentText.trim())
                    commentToEdit = null
                }
            }
        )
    }
}
