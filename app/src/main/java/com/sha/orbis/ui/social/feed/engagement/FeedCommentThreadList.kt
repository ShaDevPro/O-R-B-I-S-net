package com.sha.orbis.ui.social.feed.engagement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sha.orbis.R
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.social.feed.FeedDesignTokens

@Composable
fun FeedCommentThreadList(
    post: SocialPost,
    visibleComments: List<SocialComment>,
    currentPhone: String,
    onEditComment: ((commentId: String, newText: String) -> Unit)?,
    onReactComment: ((commentId: String, emoji: String) -> Unit)?,
    onReplyTo: (SocialComment) -> Unit,
    onRequestEdit: (SocialComment) -> Unit,
    onRequestDelete: (SocialComment) -> Unit,
    onUserClick: ((phone: String, name: String, avatar: String?) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val expandedThreads = remember { mutableStateMapOf<String, Boolean>() }
    val (topLevelComments, repliesByParentId) = remember(visibleComments) {
        partitionCommentThreads(visibleComments)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(topLevelComments, key = { it.id }) { topComment ->
            val isCommentMine = currentPhone.isNotBlank() &&
                FriendRequestRepository.isSamePhone(topComment.authorPhone, currentPhone)
            val isPostMine = currentPhone.isNotBlank() &&
                FriendRequestRepository.isSamePhone(post.authorPhone, currentPhone)
            val canDelete = isCommentMine || isPostMine
            val canEdit = isCommentMine && onEditComment != null
            val replies = repliesByParentId[topComment.id].orEmpty()
            val isExpanded = expandedThreads[topComment.id] == true

            Column(modifier = Modifier.fillMaxWidth()) {
                FeedCommentThreadItem(
                    comment = topComment,
                    currentPhone = currentPhone,
                    isReply = false,
                    canDelete = canDelete,
                    canEdit = canEdit,
                    onReplyClick = { onReplyTo(topComment) },
                    onEditClick = if (canEdit) {
                        { onRequestEdit(topComment) }
                    } else null,
                    onDeleteClick = { onRequestDelete(topComment) },
                    onReactClick = { emoji -> onReactComment?.invoke(topComment.id, emoji) },
                    onUserClick = {
                        onUserClick?.invoke(
                            topComment.authorPhone,
                            topComment.authorName,
                            topComment.authorAvatarPath
                        )
                    }
                )

                if (replies.isNotEmpty()) {
                    FeedRepliesToggleRow(
                        replyCount = replies.size,
                        expanded = isExpanded,
                        onToggle = { expandedThreads[topComment.id] = !isExpanded }
                    )
                    if (isExpanded) {
                        replies.forEach { reply ->
                            val isReplyMine = currentPhone.isNotBlank() &&
                                FriendRequestRepository.isSamePhone(reply.authorPhone, currentPhone)
                            val canDeleteReply = isReplyMine || isPostMine
                            val canEditReply = isReplyMine && onEditComment != null
                            FeedCommentThreadItem(
                                comment = reply,
                                currentPhone = currentPhone,
                                isReply = true,
                                canDelete = canDeleteReply,
                                canEdit = canEditReply,
                                onReplyClick = { onReplyTo(reply) },
                                onEditClick = if (canEditReply) {
                                    { onRequestEdit(reply) }
                                } else null,
                                onDeleteClick = { onRequestDelete(reply) },
                                onReactClick = { emoji -> onReactComment?.invoke(reply.id, emoji) },
                                onUserClick = {
                                    onUserClick?.invoke(
                                        reply.authorPhone,
                                        reply.authorName,
                                        reply.authorAvatarPath
                                    )
                                },
                                modifier = Modifier.padding(start = 26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRepliesToggleRow(
    replyCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(
                start = FeedDesignTokens.ContentPaddingHorizontal + 42.dp,
                bottom = 8.dp
            )
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (expanded) {
                stringResource(R.string.social_hide_replies)
            } else if (replyCount == 1) {
                stringResource(R.string.social_view_reply_single)
            } else {
                stringResource(R.string.social_view_replies, replyCount)
            },
            fontSize = FeedDesignTokens.SecondarySize,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
