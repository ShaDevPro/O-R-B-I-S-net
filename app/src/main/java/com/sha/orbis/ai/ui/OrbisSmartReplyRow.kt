package com.sha.orbis.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.ai.reply.ReplySuggestion

/**
 * Premium horizontal suggestion bar for ORBIS Reply-LLM.
 * Displays 1-tap contextual reply suggestions right above the message input bar.
 */
@Composable
fun OrbisSmartReplyRow(
    suggestions: List<ReplySuggestion>,
    onSelectReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    val scrollState = rememberScrollState()

    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ORBIS IA Brand Indicator Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "ORBIS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }

            // Suggestion Chips
            suggestions.forEach { item ->
                val isPersonalized = item.isPersonalized
                val chipBorderColor = if (isPersonalized) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.outlineVariant
                val chipBgColor = if (isPersonalized) Color(0x1F8B5CF6) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                val textColor = if (isPersonalized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBgColor)
                        .border(1.dp, chipBorderColor, RoundedCornerShape(16.dp))
                        .clickable { onSelectReply(item.text) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isPersonalized) {
                            Text(
                                text = "★",
                                fontSize = 10.sp,
                                color = Color(0xFF8B5CF6),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = item.text,
                            fontSize = 12.sp,
                            fontWeight = if (isPersonalized) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}
