package com.sha.orbis.ui.social

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.social.SocialReaction
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.OrbisAvatar

/**
 * Data class holding the resolved display info for a reaction.
 */
private data class ResolvedReactor(
    val reaction: SocialReaction,
    val displayName: String,
    val avatarPath: String?
)

/**
 * Facebook-style reactions detail bottom sheet.
 * Shows tabs per emoji type ("All", "❤️", "🔥", …) and the list
 * of friends who reacted with name + avatar.
 *
 * @param reactions The list of reactions to display.
 * @param currentPhone The current user's phone (to label "You").
 * @param onDismiss Callback to close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionsDetailSheet(
    reactions: List<SocialReaction>,
    currentPhone: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Resolve each reactor's name and avatar from the friend list
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
                    displayName = friend?.senderName?.takeIf { it.isNotBlank() }
                        ?: reaction.userPhone,
                    avatarPath = friend?.senderAvatarPath
                )
            }
        }
    }

    // Build emoji tabs: "All" tab + one tab per distinct emoji
    val distinctEmojis = remember(reactions) {
        reactions.map { it.emoji }.distinct()
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Filter reactors based on selected tab
    val filteredReactors = remember(selectedTabIndex, resolvedReactors, distinctEmojis) {
        if (selectedTabIndex == 0) {
            resolvedReactors
        } else {
            val targetEmoji = distinctEmojis.getOrNull(selectedTabIndex - 1) ?: return@remember resolvedReactors
            resolvedReactors.filter { it.reaction.emoji == targetEmoji }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.reactions_detail_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.reactions_detail_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Emoji Tabs
            if (distinctEmojis.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                ) {
                    // "All" tab
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Text(
                                text = "${stringResource(R.string.reactions_detail_tab_all)} ${reactions.size}",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    // Per-emoji tabs
                    distinctEmojis.forEachIndexed { index, emoji ->
                        val count = reactions.count { it.emoji == emoji }
                        Tab(
                            selected = selectedTabIndex == index + 1,
                            onClick = { selectedTabIndex = index + 1 },
                            text = {
                                Text(
                                    text = "$emoji $count",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTabIndex == index + 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // Reactors list
            if (filteredReactors.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.reactions_detail_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredReactors, key = { it.reaction.id }) { reactor ->
                        ReactorRow(reactor = reactor)
                    }
                }
            }
        }
    }
}

/**
 * A single row showing a reactor's avatar, name, and the emoji they used.
 */
@Composable
private fun ReactorRow(reactor: ResolvedReactor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with emoji badge
        Box {
            OrbisAvatar(
                avatarPath = reactor.avatarPath,
                name = reactor.displayName,
                size = 44.dp
            )
            // Emoji badge at bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = reactor.reaction.emoji,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name
        Text(
            text = reactor.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Timestamp
        Text(
            text = SocialDateFormatter.formatRelative(
                LocalContext.current,
                reactor.reaction.timestamp
            ),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
