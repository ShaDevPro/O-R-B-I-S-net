package com.sha.orbis.ui.social

import androidx.compose.runtime.Composable
import com.sha.orbis.social.SocialReaction
import com.sha.orbis.ui.social.feed.engagement.FeedReactionsSheet

/**
 * Public entry point for the reactions detail sheet (delegates to modular feed/engagement UI).
 */
@Composable
fun ReactionsDetailSheet(
    reactions: List<SocialReaction>,
    currentPhone: String,
    onDismiss: () -> Unit
) {
    FeedReactionsSheet(
        reactions = reactions,
        currentPhone = currentPhone,
        onDismiss = onDismiss
    )
}
