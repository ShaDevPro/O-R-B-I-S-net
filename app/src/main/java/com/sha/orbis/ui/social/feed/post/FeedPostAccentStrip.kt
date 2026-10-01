package com.sha.orbis.ui.social.feed.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sha.orbis.ui.social.feed.FeedDesignTokens

@Composable
fun FeedPostAccentStrip(
    isEmergencySos: Boolean,
    isOfficialAnnouncement: Boolean,
    isPinned: Boolean
) {
    when {
        isEmergencySos -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(FeedDesignTokens.AccentSos)
            )
        }
        isOfficialAnnouncement -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(FeedDesignTokens.AccentOfficial)
            )
        }
        isPinned -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            )
        }
    }
}
