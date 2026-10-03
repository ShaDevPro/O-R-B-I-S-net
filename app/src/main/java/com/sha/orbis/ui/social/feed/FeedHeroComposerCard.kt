package com.sha.orbis.ui.social.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.ui.components.OrbisAvatar

/** Minimal Instagram-style composer strip at the top of the feed. */
@Composable
fun FeedHeroComposerCard(
    userName: String,
    userAvatarPath: String?,
    modifier: Modifier = Modifier,
    onOpenMyWall: () -> Unit,
    onCreatePost: () -> Unit
) {
    val displayName = userName.ifBlank { stringResource(R.string.feed_hero_default_name) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                vertical = FeedDesignTokens.HeaderVerticalPadding
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OrbisAvatar(
                avatarPath = userAvatarPath,
                name = displayName,
                size = FeedDesignTokens.HeaderAvatarSize + 2.dp,
                onClick = onOpenMyWall
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        RoundedCornerShape(28.dp)
                    )
                    .clickable(onClick = onCreatePost)
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Text(
                    text = stringResource(R.string.feed_hero_whats_on_your_mind, displayName),
                    fontSize = FeedDesignTokens.HintSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCreatePost,
                modifier = Modifier.size(FeedDesignTokens.ActionTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.feed_hero_create_post),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        FeedSectionDivider(modifier = Modifier.padding(top = FeedDesignTokens.SectionVerticalGap + 2.dp))
    }
}
