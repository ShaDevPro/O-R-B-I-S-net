package com.sha.orbis.ui.social.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.LinkifiedText
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.social.SocialRoleBadge

private val FeedHeartRed = Color(0xFFED4956)

@Composable
fun FeedPostDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
fun FeedPostHeaderIg(
    post: SocialPost,
    metaLine: String,
    onAuthorClick: (() -> Unit)?,
    trailingActions: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OrbisAvatar(
                avatarPath = post.authorAvatarPath,
                name = post.authorName,
                size = 32.dp,
                onClick = onAuthorClick
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = if (onAuthorClick != null) {
                        Modifier.clip(RoundedCornerShape(4.dp)).combinedClickable(
                            onClick = { onAuthorClick() },
                            onLongClick = {}
                        )
                    } else Modifier
                ) {
                    Text(
                        text = post.authorName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SocialRoleBadge(
                        role = post.authorRole,
                        isOfficialAnnouncement = post.isOfficialAnnouncement
                    )
                }
                Text(
                    text = metaLine,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailingActions()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedActionBarIg(
    post: SocialPost,
    currentPhone: String,
    onQuickLike: () -> Unit,
    onOpenReactionPalette: () -> Unit,
    onCommentClick: () -> Unit,
    onRepost: (() -> Unit)?,
    onShareToChat: (() -> Unit)?,
    showEmojiPalette: Boolean,
    onEmojiSelected: (String) -> Unit,
    onDismissPalette: () -> Unit
) {
    val userReaction = if (currentPhone.isBlank()) null else post.reactions.find {
        FriendRequestRepository.isSamePhone(it.userPhone, currentPhone)
    }
    val liked = userReaction != null

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .combinedClickable(
                            onClick = {
                                if (liked) onOpenReactionPalette() else onQuickLike()
                            },
                            onLongClick = { onOpenReactionPalette() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.feed_action_like),
                        tint = if (liked) FeedHeartRed else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onCommentClick, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = stringResource(R.string.social_comments_title),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (onRepost != null) {
                    IconButton(onClick = onRepost, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = stringResource(R.string.social_action_repost),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                if (onShareToChat != null) {
                    IconButton(onClick = onShareToChat, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.social_action_share_chat),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            if (post.repostsCount > 0 && onRepost == null) {
                Text(
                    text = "${post.repostsCount}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showEmojiPalette) {
            FeedEmojiPaletteRow(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                onEmojiSelected = {
                    onEmojiSelected(it)
                    onDismissPalette()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedEmojiPaletteRow(
    modifier: Modifier = Modifier,
    onEmojiSelected: (String) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("❤️", "🔥", "🛡️", "👏", "💡", "🚀").forEach { emoji ->
            Text(
                text = emoji,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .combinedClickable(onClick = { onEmojiSelected(emoji) }, onLongClick = {})
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun FeedEngagementSummary(
    post: SocialPost,
    onOpenReactions: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (post.reactions.isEmpty()) return

    val distinctEmojis = post.reactions.map { it.emoji }.distinct().take(3).joinToString("")
    val countLabel = stringResource(R.string.feed_reactions_count, post.reactions.size)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(
                if (onOpenReactions != null) {
                    Modifier.clip(RoundedCornerShape(4.dp)).combinedClickable(
                        onClick = { onOpenReactions() },
                        onLongClick = {}
                    )
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (distinctEmojis.isNotBlank()) {
            Text(text = distinctEmojis, fontSize = 13.sp)
        }
        Text(
            text = countLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FeedCaptionBlock(
    authorName: String,
    content: String,
    hashtags: List<String>,
    modifier: Modifier = Modifier
) {
    if (content.isBlank() && hashtags.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (content.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = authorName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 6.dp)
                )
                LinkifiedText(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (hashtags.isNotEmpty()) {
            Text(
                text = hashtags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun FeedCommentPreview(
    comments: List<SocialComment>,
    onViewAllComments: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (comments.isEmpty()) return

    val topLevel = comments.filter { it.replyToCommentId.isNullOrBlank() }
        .ifEmpty { comments }
        .sortedByDescending { it.timestamp }
        .take(2)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (comments.size > 2) {
            Text(
                text = stringResource(R.string.feed_view_all_comments, comments.size),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .combinedClickable(onClick = onViewAllComments, onLongClick = {})
                    .padding(vertical = 2.dp)
            )
        }
        topLevel.forEach { comment ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .combinedClickable(onClick = onViewAllComments, onLongClick = {})
                    .padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = comment.authorName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = comment.text,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

@Composable
fun FeedDoubleTapHeartOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 180 else 320),
        label = "feedHeartScale"
    )
    val alpha = if (visible) 1f else 0f

    if (scale > 0.01f) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = FeedHeartRed.copy(alpha = alpha),
                modifier = Modifier
                    .size(88.dp)
                    .scale(scale)
            )
        }
    }
}

@Composable
fun Modifier.feedDoubleTapLike(
    enabled: Boolean,
    onDoubleTapLike: () -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { onDoubleTapLike() }
        )
    }
}
