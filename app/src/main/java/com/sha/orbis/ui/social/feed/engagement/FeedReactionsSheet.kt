package com.sha.orbis.ui.social.feed.engagement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sha.orbis.R
import com.sha.orbis.social.SocialReaction
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.social.SocialDateFormatter
import com.sha.orbis.ui.social.feed.FeedDesignTokens
import com.sha.orbis.ui.social.feed.popups.FeedBottomSheetDragHandle
import com.sha.orbis.ui.social.feed.popups.FeedModalTopBar

private data class ResolvedReactor(
    val reaction: SocialReaction,
    val displayName: String,
    val avatarPath: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedReactionsSheet(
    reactions: List<SocialReaction>,
    currentPhone: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val friendRepo = remember { FriendRequestRepository(context) }
    val allFriends = remember { friendRepo.loadRequests() }

    val resolvedReactors = remember(reactions, allFriends) {
        reactions.map { reaction ->
            val isMe = currentPhone.isNotBlank() &&
                FriendRequestRepository.isSamePhone(reaction.userPhone, currentPhone)
            if (isMe) {
                ResolvedReactor(
                    reaction = reaction,
                    displayName = context.getString(R.string.reactions_detail_you),
                    avatarPath = null
                )
            } else {
                val friend = allFriends.find {
                    FriendRequestRepository.isSamePhone(it.senderPhone, reaction.userPhone)
                }
                ResolvedReactor(
                    reaction = reaction,
                    displayName = friend?.senderName?.takeIf { it.isNotBlank() } ?: reaction.userPhone,
                    avatarPath = friend?.senderAvatarPath
                )
            }
        }
    }

    val distinctEmojis = remember(reactions) { reactions.map { it.emoji }.distinct() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val filteredReactors = remember(selectedTabIndex, resolvedReactors, distinctEmojis) {
        if (selectedTabIndex == 0) {
            resolvedReactors
        } else {
            val emoji = distinctEmojis.getOrNull(selectedTabIndex - 1) ?: return@remember resolvedReactors
            resolvedReactors.filter { it.reaction.emoji == emoji }
        }
    }

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
                .padding(bottom = 16.dp)
        ) {
            FeedModalTopBar(
                title = stringResource(R.string.reactions_detail_title),
                subtitle = stringResource(R.string.feed_reactions_count, reactions.size),
                onClose = onDismiss,
                closeContentDescription = stringResource(R.string.reactions_detail_close)
            )

            if (distinctEmojis.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(
                            horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                            vertical = 10.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeedReactionFilterChip(
                        label = "${stringResource(R.string.reactions_detail_tab_all)} ${reactions.size}",
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 }
                    )
                    distinctEmojis.forEachIndexed { index, emoji ->
                        val count = reactions.count { it.emoji == emoji }
                        FeedReactionFilterChip(
                            label = "$emoji $count",
                            selected = selectedTabIndex == index + 1,
                            onClick = { selectedTabIndex = index + 1 }
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FeedDesignTokens.DividerAlpha)
                )
            }

            if (filteredReactors.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.reactions_detail_empty),
                        fontSize = FeedDesignTokens.SecondarySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filteredReactors, key = { it.reaction.id }) { reactor ->
                        FeedReactorRow(reactor = reactor)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedReactionFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = FeedDesignTokens.SecondarySize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FeedReactorRow(reactor: ResolvedReactor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                vertical = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            OrbisAvatar(
                avatarPath = reactor.avatarPath,
                name = reactor.displayName,
                size = 44.dp
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(text = reactor.reaction.emoji, fontSize = FeedDesignTokens.MetaSize)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reactor.displayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = FeedDesignTokens.BodySize,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = SocialDateFormatter.formatRelative(LocalContext.current, reactor.reaction.timestamp),
                fontSize = FeedDesignTokens.SecondarySize,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
