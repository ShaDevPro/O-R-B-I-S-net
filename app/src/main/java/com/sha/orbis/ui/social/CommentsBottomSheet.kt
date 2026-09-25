package com.sha.orbis.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.mutableStateMapOf
import com.sha.orbis.ui.components.LinkifiedText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.social.feed.popups.FeedBottomSheetDragHandle
import com.sha.orbis.ui.social.feed.popups.FeedCommentComposerRow
import com.sha.orbis.ui.social.feed.popups.FeedCommentReplyBanner
import com.sha.orbis.ui.social.feed.popups.FeedDeleteCommentConfirmDialog
import com.sha.orbis.ui.social.feed.popups.FeedEditCommentDialog
import com.sha.orbis.ui.social.feed.popups.FeedModalTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    post: SocialPost,
    currentPhone: String = "",
    onDismiss: () -> Unit,
    onAddComment: (text: String, replyToId: String?, replyToName: String?) -> Unit,
    onEditComment: ((commentId: String, newText: String) -> Unit)? = null,
    onDeleteComment: ((commentId: String) -> Unit)? = null,
    onReactComment: ((commentId: String, emoji: String) -> Unit)? = null,
    onUserClick: ((phone: String, name: String, avatar: String?) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newCommentText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<SocialComment?>(null) }
    var commentToDelete by remember { mutableStateOf<SocialComment?>(null) }
    var commentToEdit by remember { mutableStateOf<SocialComment?>(null) }
    var editCommentText by remember { mutableStateOf("") }
    val expandedThreads = remember { mutableStateMapOf<String, Boolean>() }
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(post.id) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val notifRepo = com.sha.orbis.storage.NotificationRepository(context)
                notifRepo.markPostNotificationsAsRead(post.id)
                com.sha.orbis.data.OrbisBadgeHub.refresh(context)
            } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { FeedBottomSheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            FeedModalTopBar(
                title = stringResource(R.string.social_comments_title),
                subtitle = stringResource(R.string.feed_comments_sheet_subtitle, post.comments.size),
                onClose = onDismiss
            )

            val context = androidx.compose.ui.platform.LocalContext.current
            val blockedRepo = remember { com.sha.orbis.storage.BlockedContactsRepository(context) }
            val visibleComments = remember(post.comments) {
                post.comments.filterNot { blockedRepo.isBlocked(it.authorPhone) }
            }

            val repliesByParentId = remember(visibleComments) {
                visibleComments.filter { !it.replyToCommentId.isNullOrBlank() }.groupBy { it.replyToCommentId!! }
            }
            val topLevelComments = remember(visibleComments) {
                visibleComments.filter { comment ->
                    comment.replyToCommentId.isNullOrBlank() || !visibleComments.any { it.id == comment.replyToCommentId }
                }
            }

            // Comments List
            if (visibleComments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.social_comments_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(topLevelComments, key = { it.id }) { topComment ->
                        val isCommentMine = currentPhone.isNotBlank() && com.sha.orbis.storage.FriendRequestRepository.isSamePhone(topComment.authorPhone, currentPhone)
                        val isPostMine = currentPhone.isNotBlank() && com.sha.orbis.storage.FriendRequestRepository.isSamePhone(post.authorPhone, currentPhone)
                        val canDelete = isCommentMine || isPostMine
                        val canEdit = isCommentMine && onEditComment != null
                        val replies = repliesByParentId[topComment.id].orEmpty()
                        val isExpanded = expandedThreads[topComment.id] == true

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CommentRow(
                                comment = topComment,
                                currentPhone = currentPhone,
                                canDelete = canDelete,
                                canEdit = canEdit,
                                onReplyClick = {
                                    replyingToComment = topComment
                                },
                                onEditClick = {
                                    commentToEdit = topComment
                                    editCommentText = topComment.text
                                },
                                onDeleteClick = {
                                    commentToDelete = topComment
                                },
                                onReactClick = { emoji ->
                                    onReactComment?.invoke(topComment.id, emoji)
                                },
                                onClick = { onUserClick?.invoke(topComment.authorPhone, topComment.authorName, topComment.authorAvatarPath) }
                            )

                            // Thread replies toggle button (like FB/Instagram)
                            if (replies.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 36.dp, top = 2.dp, bottom = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            expandedThreads[topComment.id] = !isExpanded
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height(1.5.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                    Text(
                                        text = if (isExpanded) {
                                            stringResource(R.string.social_hide_replies)
                                        } else {
                                            if (replies.size == 1) stringResource(R.string.social_view_reply_single)
                                            else stringResource(R.string.social_view_replies, replies.size)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // Stacked indented replies with vertical thread indicator
                                if (isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 28.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        replies.forEach { reply ->
                                            val isReplyMine = currentPhone.isNotBlank() && com.sha.orbis.storage.FriendRequestRepository.isSamePhone(reply.authorPhone, currentPhone)
                                            val canDeleteReply = isReplyMine || isPostMine
                                            val canEditReply = isReplyMine && onEditComment != null

                                            CommentRow(
                                                comment = reply,
                                                currentPhone = currentPhone,
                                                isReply = true,
                                                canDelete = canDeleteReply,
                                                canEdit = canEditReply,
                                                onReplyClick = {
                                                    replyingToComment = reply
                                                },
                                                onEditClick = {
                                                    commentToEdit = reply
                                                    editCommentText = reply.text
                                                },
                                                onDeleteClick = {
                                                    commentToDelete = reply
                                                },
                                                onReactClick = { emoji ->
                                                    onReactComment?.invoke(reply.id, emoji)
                                                },
                                                onClick = { onUserClick?.invoke(reply.authorPhone, reply.authorName, reply.authorAvatarPath) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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

    // Delete Comment Confirmation Dialog
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

@Composable
private fun CommentRow(
    comment: SocialComment,
    currentPhone: String,
    isReply: Boolean = false,
    canDelete: Boolean,
    canEdit: Boolean = false,
    onReplyClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit,
    onReactClick: (emoji: String) -> Unit,
    onClick: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val timeFormatted = remember(comment.timestamp) {
        SocialDateFormatter.formatRelative(context, comment.timestamp)
    }
    var showReactionPicker by remember { mutableStateOf(false) }
    val quickEmojis = listOf("❤️", "🔥", "👍", "😂", "💡", "👏")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isReply) 12.dp else 14.dp))
            .background(if (isReply) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(if (isReply) 8.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OrbisAvatar(
            avatarPath = comment.authorAvatarPath,
            name = comment.authorName,
            size = if (isReply) 26.dp else 32.dp,
            onClick = onClick
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = comment.authorName,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isReply) 11.sp else 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeFormatted,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (comment.isEdited) {
                        Text(
                            text = "• ${stringResource(R.string.social_post_edited)}",
                            fontSize = 9.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canEdit && onEditClick != null) {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.social_action_edit),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.social_comment_delete_action),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // Display "↳ En réponse à @Name" if this is a reply
            if (!comment.replyToAuthorName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.social_comment_replying_to, comment.replyToAuthorName),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            LinkifiedText(
                text = comment.text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = if (isReply) 11.sp else 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            // Comment Reaction Badges (Grouped by emoji)
            if (comment.reactions.isNotEmpty()) {
                val groupedReactions = remember(comment.reactions) {
                    comment.reactions.groupBy { it.emoji }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    groupedReactions.forEach { (emoji, reactions) ->
                        val hasUserReacted = currentPhone.isNotBlank() && reactions.any {
                            com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it.userPhone, currentPhone)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (hasUserReacted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (hasUserReacted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onReactClick(emoji) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$emoji ${reactions.size}",
                                fontSize = 11.sp,
                                fontWeight = if (hasUserReacted) FontWeight.Bold else FontWeight.Normal,
                                color = if (hasUserReacted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Quick Emoji Reaction Bar (when expanded)
            if (showReactionPicker) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickEmojis.forEach { emoji ->
                        val hasReacted = currentPhone.isNotBlank() && comment.reactions.any {
                            it.emoji == emoji && com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it.userPhone, currentPhone)
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (hasReacted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    onReactClick(emoji)
                                    showReactionPicker = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 15.sp)
                        }
                    }
                }
            }

            // Action Buttons: Répondre + Réagir
            Row(
                modifier = Modifier
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Reply Action Button
                Row(
                    modifier = Modifier.clickable { onReplyClick() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.social_comment_reply_btn),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // React Action Button
                Row(
                    modifier = Modifier.clickable { showReactionPicker = !showReactionPicker },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "✨",
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Réagir",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (showReactionPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
