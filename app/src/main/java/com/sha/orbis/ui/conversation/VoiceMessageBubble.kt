package com.sha.orbis.ui.conversation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.media.AudioVoiceHelper
import com.sha.orbis.model.MessageDeliveryStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun VoiceMessageBubble(
    messageId: String,
    base64Audio: String,
    durSec: Int,
    isMsgMine: Boolean,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    timestamp: Long = System.currentTimeMillis(),
    deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var currentSec by remember { mutableIntStateOf(0) }
    var currentSpeed by remember { mutableFloatStateOf(AudioVoiceHelper.getPlaybackSpeed()) }

    val waveformBars = remember(messageId) {
        val seed = abs(messageId.hashCode())
        val count = 18
        List(count) { i ->
            val harmonic = (sin((i + seed % 10) * 0.7) * 0.35 + 0.55).toFloat()
            val variance = ((seed * (i + 1) * 31) % 40) / 100f
            (harmonic + variance).coerceIn(0.25f, 1.0f)
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                delay(80)
                playbackProgress = AudioVoiceHelper.getPlaybackProgress()
                currentSec = AudioVoiceHelper.getCurrentPositionSeconds()
            }
        } else {
            playbackProgress = 0f
            currentSec = 0
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val bubbleShape = if (isMsgMine) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    }

    val bubbleBg = when {
        isMsgMine && isDark -> Color(0xFF1E3A5F)
        isMsgMine -> Color(0xFFDCF0FA)
        isDark -> Color(0xFF1E293B)
        else -> Color(0xFFFFFFFF)
    }
    val bubbleBorder = when {
        isMsgMine && isDark -> Color(0xFF2B4C7E)
        isMsgMine -> Color(0xFFBAE6FD)
        isDark -> Color(0xFF334155)
        else -> Color(0xFFE2E8F0)
    }

    val playButtonBg = when {
        isMsgMine && isDark -> Color.White
        isMsgMine -> MaterialTheme.colorScheme.primary
        isDark -> Color(0xFF38BDF8)
        else -> MaterialTheme.colorScheme.primary
    }
    val playIconTint = when {
        isMsgMine && isDark -> Color(0xFF1E3A5F)
        isMsgMine -> Color.White
        isDark -> Color.White
        else -> Color.White
    }
    val activeBarColor = when {
        isMsgMine && isDark -> Color.White
        isMsgMine -> MaterialTheme.colorScheme.primary
        isDark -> Color(0xFF38BDF8)
        else -> MaterialTheme.colorScheme.primary
    }
    val inactiveBarColor = when {
        isMsgMine && isDark -> Color.White.copy(alpha = 0.40f)
        isMsgMine -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        isDark -> Color(0xFF94A3B8)
        else -> Color(0xFFCBD5E1)
    }
    val textColor = when {
        isMsgMine && isDark -> Color(0xFFFFFFFF)
        isMsgMine -> Color(0xFF0F172A)
        isDark -> Color(0xFFF1F5F9)
        else -> Color(0xFF2D3748)
    }
    val speedBg = when {
        isMsgMine && isDark -> Color.White.copy(alpha = 0.18f)
        isMsgMine -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        isDark -> Color(0xFF38BDF8).copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }
    val speedTextColor = when {
        isMsgMine && isDark -> Color.White
        isMsgMine -> MaterialTheme.colorScheme.primary
        isDark -> Color(0xFF38BDF8)
        else -> MaterialTheme.colorScheme.primary
    }
    val subTextColor = when {
        isDark -> Color(0xFF94A3B8)
        else -> Color(0xFF64748B)
    }

    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    Card(
        shape = bubbleShape,
        colors = CardDefaults.cardColors(containerColor = bubbleBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, bubbleBorder),
        modifier = modifier.widthIn(min = 200.dp, max = 250.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp, horizontal = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        PlayingHaloBox(color = playButtonBg.copy(alpha = 0.25f))
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(playButtonBg)
                            .clickable(onClick = onTogglePlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = playIconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        waveformBars.forEachIndexed { index, heightRatio ->
                            val barFraction = index.toFloat() / waveformBars.size.toFloat()
                            val isBarActive = isPlaying && barFraction <= playbackProgress
                            val barColor = if (isBarActive) activeBarColor else inactiveBarColor
                            val barHeight = (heightRatio * 16.dp.value).coerceIn(3f, 18f).dp

                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(barColor)
                                    .clickable {
                                        if (isPlaying) {
                                            AudioVoiceHelper.seekToFraction(barFraction)
                                        }
                                    }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayTime = if (isPlaying) {
                            "%02d:%02d".format(currentSec / 60, currentSec % 60)
                        } else {
                            "%02d:%02d".format(durSec / 60, durSec % 60)
                        }

                        Text(
                            text = displayTime,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(speedBg)
                                .clickable {
                                    val nextSpeed = when (currentSpeed) {
                                        1.0f -> 1.5f
                                        1.5f -> 2.0f
                                        else -> 1.0f
                                    }
                                    currentSpeed = nextSpeed
                                    AudioVoiceHelper.setPlaybackSpeed(nextSpeed)
                                }
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${currentSpeed}x",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = speedTextColor
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormatted,
                    fontSize = 10.sp,
                    color = subTextColor
                )
                if (isMsgMine) {
                    val statusIcon = when (deliveryStatus) {
                        MessageDeliveryStatus.READ, MessageDeliveryStatus.DELIVERED -> Icons.Default.DoneAll
                        MessageDeliveryStatus.SENT -> Icons.Default.Done
                        else -> null
                    }
                    if (statusIcon != null) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = if (deliveryStatus == MessageDeliveryStatus.READ) Color(0xFF38BDF8)
                            else subTextColor,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayingHaloBox(color: androidx.compose.ui.graphics.Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(color)
    )
}
