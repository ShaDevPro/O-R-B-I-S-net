package com.sha.orbis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TikTok / Telegram Style Ultra-Premium Numbered Notification Badge.
 * 
 * Features:
 * - 1..9: Exact 18dp circle
 * - 10..99: Sleek 18dp height stadium pill with horizontal padding
 * - >99: "99+" stadium capsule
 * - 1.5dp surface outline preventing icon blending
 * - Spring entrance and exit scale animation
 */
@Composable
fun OrbisPremiumBadge(
    count: Int,
    modifier: Modifier = Modifier,
    isDotOnly: Boolean = false,
    badgeColor: Color = Color(0xFFFF2A54), // TikTok Vibrant Crimson
    textColor: Color = Color.White,
    borderColor: Color = MaterialTheme.colorScheme.surface,
    borderWidth: Dp = 1.5.dp
) {
    AnimatedVisibility(
        visible = count > 0 || isDotOnly,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
        exit = scaleOut(),
        modifier = modifier
    ) {
        if (isDotOnly) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .border(borderWidth, borderColor, CircleShape)
            )
        } else {
            val displayCount = when {
                count > 99 -> "99+"
                else -> count.toString()
            }

            val isSingleDigit = count in 1..9
            val badgeShape = if (isSingleDigit) CircleShape else RoundedCornerShape(10.dp)

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(badgeShape)
                    .background(badgeColor)
                    .border(borderWidth, borderColor, badgeShape)
                    .padding(horizontal = if (isSingleDigit) 0.dp else 4.dp, vertical = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayCount,
                    color = textColor,
                    fontSize = if (displayCount.length > 2) 9.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp
                )
            }
        }
    }
}
