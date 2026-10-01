package com.sha.orbis.ui.social.feed.engagement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.social.SocialComment
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.LinkifiedText
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.social.SocialDateFormatter
import com.sha.orbis.ui.social.feed.FeedDesignTokens

@Composable
fun FeedCommentThreadItem(
    comment: SocialComment,
    currentPhone: String,
    isReply: Boolean,
    canDelete: Boolean,
    canEdit: Boolean,
    onReplyClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    onDeleteClick: () -> Unit,
    onReactClick: (emoji: String) -> Unit,
    onUserClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormatted = remember(comment.timestamp) {
        SocialDateFormatter.formatRelative(context, comment.timestamp)
    }
    var showReactionPicker by remember { mutableStateOf(false) }
    val quickEmojis = remember { listOf("👍", "❤️", "😂", "😮", "😢", "🔥") }
    val userHasThumbUp = remember(currentPhone, comment.reactions) {
        currentPhone.isNotBlank() && comment.reactions.any {
            it.emoji == "👍" && FriendRequestRepository.isSamePhone(it.userPhone, currentPhone)
        }
    }
    val userHasThumbDown = remember(currentPhone, comment.reactions) {
        currentPhone.isNotBlank() && comment.reactions.any {
            it.emoji == "👎" && FriendRequestRepository.isSamePhone(it.userPhone, currentPhone)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                vertical = if (isReply) 3.dp else 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            OrbisAvatar(
                avatarPath = comment.authorAvatarPath,
                name = comment.authorName,
                size = if (isReply) 26.dp else 36.dp,
                onClick = onUserClick
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = comment.authorName,
                            fontWeight = FontWeight.Bold,
                            fontSize = FeedDesignTokens.AuthorNameSize,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = if (onUserClick != null) Modifier.clickable { onUserClick() } else Modifier
                        )
                        Text(
                            text = "· $timeFormatted",
                            fontSize = FeedDesignTokens.SecondarySize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        if (canEdit && onEditClick != null) {
                            IconButton(onClick = onEditClick, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.social_action_edit),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        if (canDelete) {
                            IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.social_comment_delete_action),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { showReactionPicker = !showReactionPicker }
                            )
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!comment.replyToAuthorName.isNullOrBlank()) {
                            Text(
                                text = comment.replyToAuthorName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinkifiedText(
                            text = comment.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )
                        )
                    }
                    if (comment.isEdited) {
                        Text(
                            text = "· ${stringResource(R.string.social_post_edited)}",
                            fontSize = FeedDesignTokens.MetaSize,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showReactionPicker) {
                    FeedCommentQuickReactionRow(
                        emojis = quickEmojis,
                        comment = comment,
                        currentPhone = currentPhone,
                        onPick = { emoji ->
                            onReactClick(emoji)
                            showReactionPicker = false
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeedCommentActionText(
                        label = stringResource(R.string.social_comment_reply_btn),
                        onClick = onReplyClick
                    )

                    if (comment.reactions.isNotEmpty()) {
                        FeedCommentReactionSummary(
                            comment = comment,
                            currentPhone = currentPhone,
                            onReactClick = onReactClick,
                            onSummaryClick = { showReactionPicker = !showReactionPicker }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { onReactClick("👍") },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = if (userHasThumbUp) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onReactClick("👎") },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = null,
                            tint = if (userHasThumbDown) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedCommentActionText(
    label: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        leadingIcon?.invoke()
        Text(
            text = label,
            fontSize = FeedDesignTokens.SecondarySize,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeedCommentReactionSummary(
    comment: SocialComment,
    currentPhone: String,
    onReactClick: (String) -> Unit,
    onSummaryClick: () -> Unit = {}
) {
    val grouped = remember(comment.reactions) {
        comment.reactions
            .groupBy { it.emoji }
            .toList()
            .sortedByDescending { (_, list) -> list.size }
            .take(2)
            .toMap()
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSummaryClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        grouped.forEach { (emoji, reactions) ->
            val hasUser = currentPhone.isNotBlank() && reactions.any {
                FriendRequestRepository.isSamePhone(it.userPhone, currentPhone)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (hasUser) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onReactClick(emoji) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = emoji, fontSize = 14.sp)
                Text(
                    text = reactions.size.toString(),
                    fontSize = 12.sp,
                    fontWeight = if (hasUser) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (hasUser) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedCommentQuickReactionRow(
    emojis: List<String>,
    comment: SocialComment,
    currentPhone: String,
    onPick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        emojis.forEach { emoji ->
            val hasReacted = currentPhone.isNotBlank() && comment.reactions.any {
                it.emoji == emoji && FriendRequestRepository.isSamePhone(it.userPhone, currentPhone)
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasReacted) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else Color.Transparent
                    )
                    .clickable { onPick(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 20.sp)
            }
        }
    }
}
