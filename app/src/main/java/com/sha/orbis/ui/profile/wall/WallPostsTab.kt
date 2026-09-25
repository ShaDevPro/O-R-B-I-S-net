package com.sha.orbis.ui.profile.wall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.social.SocialPost
import com.sha.orbis.ui.social.SocialPostCard

fun LazyListScope.wallPostsTab(
    posts: List<SocialPost>,
    currentPhone: String,
    isMyProfile: Boolean,
    isRepostsTab: Boolean = false,
    onReact: (postId: String, emoji: String) -> Unit,
    onVotePoll: (postId: String, optionId: String) -> Unit,
    onOpenComments: (SocialPost) -> Unit,
    onDeletePost: ((SocialPost) -> Unit)? = null,
    onEditPost: ((post: SocialPost, newContent: String, newHashtags: List<String>) -> Unit)? = null,
    onTogglePin: ((SocialPost) -> Unit)? = null,
    onRepost: ((SocialPost) -> Unit)? = null,
    onShareToChat: ((SocialPost) -> Unit)? = null,
    onShowReactions: ((SocialPost) -> Unit)? = null,
    onCreatePost: (() -> Unit)? = null
) {
    if (posts.isEmpty()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRepostsTab) Icons.Default.Repeat else Icons.Default.Article,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Text(
                    text = if (isRepostsTab) stringResource(R.string.wall_empty_reposts) else stringResource(R.string.wall_empty_posts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (isRepostsTab) stringResource(R.string.wall_empty_reposts_desc) else stringResource(R.string.wall_empty_posts_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (isMyProfile && !isRepostsTab && onCreatePost != null) {
                    Button(
                        onClick = onCreatePost,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = stringResource(R.string.wall_action_new_post),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        items(posts, key = { it.id }) { post ->
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SocialPostCard(
                    post = post,
                    currentPhone = currentPhone,
                    onReact = { emoji -> onReact(post.id, emoji) },
                    onVotePoll = { optId -> onVotePoll(post.id, optId) },
                    onOpenComments = { onOpenComments(post) },
                    onDeletePost = if (isMyProfile || (currentPhone.isNotBlank() && com.sha.orbis.storage.FriendRequestRepository.isSamePhone(post.authorPhone, currentPhone))) {
                        { onDeletePost?.invoke(post) }
                    } else null,
                    onEditPost = if (onEditPost != null && (isMyProfile || (currentPhone.isNotBlank() && com.sha.orbis.storage.FriendRequestRepository.isSamePhone(post.authorPhone, currentPhone)))) {
                        { _, newContent, newHashtags -> onEditPost(post, newContent, newHashtags) }
                    } else null,
                    onTogglePin = if (isMyProfile) {
                        { onTogglePin?.invoke(post) }
                    } else null,
                    onRepost = { onRepost?.invoke(post) },
                    onShareToChat = { onShareToChat?.invoke(post) },
                    onShowReactions = onShowReactions?.let { { it(post) } },
                    onAuthorClick = null
                )
            }
        }
    }
}
