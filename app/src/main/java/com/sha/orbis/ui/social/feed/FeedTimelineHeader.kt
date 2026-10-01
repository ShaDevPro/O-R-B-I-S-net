package com.sha.orbis.ui.social.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Search + actions + primary feed tabs (fixed above the scrollable feed). */
@Composable
fun FeedTimelineHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    pendingFriendRequestsCount: Int,
    onOpenFriendRequests: () -> Unit,
    onRefreshFeed: () -> Unit,
    onFetchNostrTimeline: suspend () -> Unit,
    selectedFeedTabIndex: Int,
    feedTabs: List<Pair<String, ImageVector>>,
    onFeedTabSelected: (Int) -> Unit,
    onOpenMyWallShortcut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FeedTimelineToolbar(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            pendingFriendRequestsCount = pendingFriendRequestsCount,
            onOpenFriendRequests = onOpenFriendRequests,
            onRefreshFeed = onRefreshFeed,
            onFetchNostrTimeline = onFetchNostrTimeline
        )
        FeedPrimaryTabsRow(
            selectedTabIndex = selectedFeedTabIndex,
            tabs = feedTabs,
            onTabSelected = onFeedTabSelected,
            onMyWallShortcut = onOpenMyWallShortcut
        )
        FeedSectionDivider()
    }
}
