package com.sha.orbis.ui.social.feed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared layout and visual tokens for the Instagram-style feed (OrbisNet only).
 */
object FeedDesignTokens {
    val ContentPaddingHorizontal = 14.dp
    val HeaderVerticalPadding = 8.dp
    val SectionVerticalGap = 6.dp
    val ActionIconSize = 26.dp
    val ActionTouchTarget = 44.dp
    val HeaderAvatarSize = 36.dp

    val MediaAspectPhoto = 4f / 5f
    val MediaAspectVideo = 4f / 5f

    val HeartRed = Color(0xFFED4956)
    val AccentSos = Color(0xFFEF4444)
    val AccentOfficial = Color(0xFF38BDF8)

    val AuthorNameSize = 14.sp
    val MetaSize = 11.sp
    val BodySize = 14.sp
    val SecondarySize = 13.sp
    val HintSize = 12.sp

    val DividerThickness = 0.5.dp
    val DividerAlpha = 0.35f
}
