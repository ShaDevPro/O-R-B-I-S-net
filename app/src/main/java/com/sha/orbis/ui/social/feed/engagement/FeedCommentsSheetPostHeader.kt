package com.sha.orbis.ui.social.feed.engagement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.social.SocialPost
import com.sha.orbis.ui.social.feed.FeedDesignTokens

@Composable
fun FeedCommentsSheetPostHeader(
    post: SocialPost,
    currentPhone: String = "",
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val visibleReactions = remember(post.reactions, post.id, currentPhone) {
        com.sha.orbis.social.SocialEngagementAudiencePolicy.filterVisibleReactions(
            reactions = post.reactions,
            post = post,
            viewerPhone = currentPhone,
            context = context
        )
    }
    val topEmojis = remember(visibleReactions) {
        visibleReactions.map { it.emoji }.distinct().take(3)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                    vertical = 10.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (topEmojis.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        topEmojis.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = FeedDesignTokens.MetaSize)
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.feed_reactions_count, visibleReactions.size),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = FeedDesignTokens.SecondarySize,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.social_comments_shares_count, post.repostsCount),
                fontWeight = FontWeight.SemiBold,
                fontSize = FeedDesignTokens.SecondarySize,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                    vertical = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.social_comments_sort_most_relevant),
                fontWeight = FontWeight.Bold,
                fontSize = FeedDesignTokens.AuthorNameSize,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(
            thickness = FeedDesignTokens.DividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FeedDesignTokens.DividerAlpha)
        )
    }
}
