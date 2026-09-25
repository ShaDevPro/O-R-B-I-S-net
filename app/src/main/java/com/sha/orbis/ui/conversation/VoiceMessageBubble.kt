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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import kotlinx.coroutines.delay
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var currentSec by remember { mutableIntStateOf(0) }
    var currentSpeed by remember { mutableFloatStateOf(AudioVoiceHelper.getPlaybackSpeed()) }

    // Generate unique, natural waveform heights based on message ID hash
    val waveformBars = remember(messageId) {
        val seed = abs(messageId.hashCode())
        val count = 18
        List(count) { i ->
            val harmonic = (sin((i + seed % 10) * 0.7) * 0.35 + 0.55).toFloat()
            val variance = ((seed * (i + 1) * 31) % 40) / 100f
            (harmonic + variance).coerceIn(0.25f, 1.0f)
        }
    }

    // Active playback tracking loop
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

    val playButtonBg = if (isMsgMine) Color.White else Color(0xFF5E6BB2)
    val playIconTint = if (isMsgMine) Color(0xFF5E6BB2) else Color.White
    val activeBarColor = if (isMsgMine) Color.White else Color(0xFF5E6BB2)
    val inactiveBarColor = if (isMsgMine) Color.White.copy(alpha = 0.40f) else Color(0xFFCBD5E1)
    val textColor = if (isMsgMine) Color.White else if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
    val speedBg = if (isMsgMine) Color.White.copy(alpha = 0.22f) else Color(0xFF5E6BB2).copy(alpha = 0.12f)
    val speedTextColor = if (isMsgMine) Color.White else Color(0xFF5E6BB2)

    Row(
        modifier = modifier
            .widthIn(min = 170.dp, max = 220.dp)
            .padding(vertical = 1.dp, horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Compact Play / Pause Button with dynamic halo when active
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

        // 2. Audio Equalizer Waveform & Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Waveform equalizer bars with interactive scrubbing
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

            // Sub-row: Live duration & Speed Toggle Pill
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

                // Playback Speed Switcher (1.0x -> 1.5x -> 2.0x)
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
