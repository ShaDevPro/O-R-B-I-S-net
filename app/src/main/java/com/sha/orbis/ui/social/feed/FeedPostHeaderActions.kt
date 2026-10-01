package com.sha.orbis.ui.social.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sha.orbis.R
import com.sha.orbis.social.SocialPost
import com.sha.orbis.storage.FriendRequestRepository

@Composable
fun FeedPostHeaderActions(
    post: SocialPost,
    currentPhone: String,
    showPostOptionsMenu: Boolean,
    onShowPostOptionsMenuChange: (Boolean) -> Unit,
    onRequestBlockConfirm: () -> Unit,
    onHidePost: (() -> Unit)? = null,
    onEditClick: (() -> Unit)?,
    onTogglePin: (() -> Unit)?,
    onDeletePost: (() -> Unit)?
) {
    val isPostMine = currentPhone.isNotBlank() &&
        FriendRequestRepository.isSamePhone(post.authorPhone, currentPhone)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isPostMine && onEditClick != null) {
            HeaderIconChip(
                onClick = onEditClick,
                background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.social_action_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (isPostMine && onTogglePin != null) {
            HeaderIconChip(
                onClick = onTogglePin,
                background = if (post.isPinned) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = if (post.isPinned) {
                        stringResource(R.string.social_action_unpin)
                    } else {
                        stringResource(R.string.social_action_pin)
                    },
                    tint = if (post.isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    },
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (isPostMine && onDeletePost != null) {
            HeaderIconChip(
                onClick = onDeletePost,
                background = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.chat_action_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (!isPostMine && !post.isOfficialAnnouncement) {
            Box(contentAlignment = Alignment.Center) {
                HeaderIconChip(
                    onClick = { onShowPostOptionsMenuChange(true) },
                    background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.feed_post_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                DropdownMenu(
                    expanded = showPostOptionsMenu,
                    onDismissRequest = { onShowPostOptionsMenuChange(false) }
                ) {
                    // Hide this post
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.social_action_hide_post)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            onShowPostOptionsMenuChange(false)
                            onHidePost?.invoke()
                        }
                    )
                    // Block user
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.social_action_block_user),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            onShowPostOptionsMenuChange(false)
                            onRequestBlockConfirm()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIconChip(
    onClick: () -> Unit,
    background: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
