package com.sha.orbis.ui.social.feed.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.algorithm.RecommendationReason
import com.sha.orbis.ui.social.feed.FeedActionBarIg
import com.sha.orbis.ui.social.feed.FeedCaptionBlock
import com.sha.orbis.ui.social.feed.FeedDesignTokens
import com.sha.orbis.ui.social.feed.FeedPostContextRibbons
import com.sha.orbis.ui.social.feed.FeedPostDivider
import com.sha.orbis.ui.social.feed.FeedPostHeaderIg
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

/**
 * Instagram-style post shell: header → media → actions → reactions (right-aligned) → caption.
 */
@Composable
fun FeedPostCardIg(
    post: SocialPost,
    currentPhone: String,
    metaLine: String,
    cleanContent: String,
    isEmergencySos: Boolean,
    hasMedia: Boolean,
    onReact: (emoji: String) -> Unit,
    onOpenComments: () -> Unit,
    onRepost: (() -> Unit)? = null,
    onShareToChat: (() -> Unit)? = null,
    onShowReactions: (() -> Unit)?,
    onAuthorClick: (() -> Unit)?,
    recommendationReason: RecommendationReason? = null,
    matchingTags: List<String> = emptyList(),
    headerActions: @Composable () -> Unit,
    hasActiveStory: Boolean = false,
    hasUnseenStory: Boolean = false,
    onStoryClick: (() -> Unit)? = null,
    aboveMedia: @Composable () -> Unit = {},
    belowMedia: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showEmojiPalette by remember { mutableStateOf(false) }
    var showHeartBurst by remember { mutableStateOf(false) }

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650)
            showHeartBurst = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        FeedPostAccentStrip(
            isEmergencySos = isEmergencySos,
            isOfficialAnnouncement = post.isOfficialAnnouncement,
            isPinned = post.isPinned
        )

        FeedPostContextRibbons(
            post = post,
            isEmergencySos = isEmergencySos,
            recommendationReason = recommendationReason,
            matchingTags = matchingTags
        )

        FeedPostHeaderIg(
            post = post,
            metaLine = metaLine,
            onAuthorClick = onAuthorClick,
            trailingActions = headerActions,
            hasActiveStory = hasActiveStory,
            hasUnseenStory = hasUnseenStory,
            onStoryClick = onStoryClick
        )

        // Caption/content ABOVE media — text posts show content right after header
        FeedCaptionBlock(
            content = cleanContent,
            hashtags = post.hashtags
        )

        aboveMedia()

        if (hasMedia) {
            FeedPostMediaBlock(
                post = post,
                showHeartBurst = showHeartBurst,
                onDoubleTapLike = {
                    showHeartBurst = true
                    onReact("❤️")
                }
            )
        }

        belowMedia()

        FeedActionBarIg(
            post = post,
            currentPhone = currentPhone,
            onQuickLike = { onReact("❤️") },
            onOpenReactionPalette = { showEmojiPalette = !showEmojiPalette },
            onCommentClick = onOpenComments,
            onRepost = onRepost,
            onShareToChat = onShareToChat,
            onOpenReactions = onShowReactions,
            showEmojiPalette = showEmojiPalette,
            onEmojiSelected = { emoji -> onReact(emoji) },
            onDismissPalette = { showEmojiPalette = false }
        )

        // FeedCommentPreview removed — first comment display belongs in the comments page only

        FeedPostDivider()
    }
}
