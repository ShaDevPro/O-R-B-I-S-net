package com.sha.orbis.ui.conversation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.media.AudioVoiceHelper
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun VoiceRecordingBar(
    onCancel: () -> Unit,
    onSend: () -> Unit,
    maxDurationSeconds: Int = 900,
    modifier: Modifier = Modifier
) {
    var recordingMillis by remember { mutableIntStateOf(0) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val waveformBars = remember { mutableStateListOf(0.3f, 0.5f, 0.4f, 0.7f, 0.2f, 0.6f, 0.8f, 0.4f, 0.5f, 0.3f) }

    val recordingSeconds = recordingMillis / 1000

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val barBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val barBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "rec_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "rec_scale"
    )

    // Timer & Live Amplitude Polling
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            recordingMillis += 100
            if (recordingMillis >= maxDurationSeconds * 1000) {
                onSend()
                break
            }
            // Read amplitude from AudioVoiceHelper
            val amp = AudioVoiceHelper.getMaxAmplitude()
            val normalizedAmp = if (amp > 0) {
                (amp / 25000f).coerceIn(0.15f, 1.0f)
            } else {
                (Random.nextFloat() * 0.4f + 0.15f)
            }
            waveformBars.add(normalizedAmp)
            if (waveformBars.size > 14) {
                waveformBars.removeAt(0)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(barBg)
            .border(1.dp, barBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Educational Pill Banner with Info Trigger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                .clickable { showInfoDialog = true }
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "🎙️ ${stringResource(R.string.media_voice_banner_title)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• ${stringResource(R.string.media_voice_banner_desc)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
        }

        // 2. Main Action Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Trash / Discard Button
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935).copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.media_voice_cancel),
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Pulsing Red REC Indicator + WhatsApp Style Real-time Timer (mm:ss)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935).copy(alpha = pulseAlpha))
                )

                Text(
                    text = "%02d:%02d".format(
                        recordingSeconds / 60,
                        recordingSeconds % 60
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Live Audio Waveform Equalizer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                waveformBars.forEach { heightRatio ->
                    val barHeight = (heightRatio * 22.dp.value).coerceIn(4f, 24f).dp
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f + heightRatio * 0.25f))
                    )
                }
            }

            // Send Button
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.media_voice_send),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Explanatory Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.media_voice_info_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.media_voice_info_desc),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.close), fontSize = 12.sp)
                }
            }
        )
    }
}

