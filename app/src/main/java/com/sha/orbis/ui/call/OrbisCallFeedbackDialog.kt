package com.sha.orbis.ui.call

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.call.CallFeedbackInfo
import com.sha.orbis.call.CallFeedbackReason
import com.sha.orbis.media.AudioVoiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OrbisCallFeedbackDialog(
    feedback: CallFeedbackInfo,
    onDismiss: () -> Unit,
    onOpenChat: (phone: String) -> Unit = {},
    onSendPlainSms: (phone: String) -> Unit = onOpenChat
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var recordingMillis by remember { mutableIntStateOf(0) }
    var isSending by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "walkie_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    fun startRecordingVoice() {
        if (isRecording || isSending) return
        val file = AudioVoiceHelper.startRecording(context)
        if (file != null) {
            isRecording = true
            recordingMillis = 0
        } else {
            Toast.makeText(context, context.getString(R.string.call_voice_recording_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelRecordingVoice() {
        if (!isRecording) return
        isRecording = false
        AudioVoiceHelper.cancelRecording()
    }

    fun sendVoiceMemo() {
        if (!isRecording) return
        isRecording = false
        isSending = true
        val (file, dur) = AudioVoiceHelper.stopRecording()
        if (file != null && dur > 0) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val base64 = AudioVoiceHelper.fileToBase64(file)
                    if (base64.isNotBlank() && AudioVoiceHelper.isPayloadWithinNostrLimit(base64)) {
                        val audioPayload = "[AUDIO:$base64:$dur]"
                        val peerKey = feedback.peerPhone
                        val digits = peerKey.filter { it.isDigit() }
                        val convId = if (digits.isNotBlank()) "conv_$digits" else "conv_${peerKey.take(16)}"
                        val msgId = "msg_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"

                        com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendDirectMessage(
                            recipientNpubOrHex = peerKey,
                            conversationId = convId,
                            text = audioPayload,
                            messageId = msgId
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.call_ended_voice_memo_sent), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.chat_voice_too_long_nostr), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("OrbisCallFeedbackDialog", "Voice memo send error: ${e.message}")
                } finally {
                    isSending = false
                }
            }
        } else {
            isSending = false
        }
    }

    // Auto-stop after 30s
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingMillis = 0
            while (isRecording) {
                delay(100)
                recordingMillis += 100
                if (recordingMillis >= 30_000) {
                    sendVoiceMemo()
                    break
                }
            }
        }
    }

    val isNoAnswer = feedback.reason == CallFeedbackReason.NO_ANSWER
    val isFailed = feedback.reason == CallFeedbackReason.CALL_FAILED
    val accentColor = if (isNoAnswer || isFailed) Color(0xFFF59E0B) else Color(0xFFEF4444)

    val titleText = when (feedback.reason) {
        CallFeedbackReason.NO_ANSWER -> stringResource(R.string.call_feedback_no_answer_title)
        CallFeedbackReason.REJECTED_BY_PEER -> stringResource(R.string.call_feedback_rejected_title)
        CallFeedbackReason.CALL_FAILED -> stringResource(R.string.call_feedback_no_answer_title)
        else -> stringResource(R.string.call_feedback_no_answer_title)
    }

    val subText = when (feedback.reason) {
        CallFeedbackReason.NO_ANSWER -> stringResource(R.string.call_feedback_no_answer_sub)
        CallFeedbackReason.REJECTED_BY_PEER -> feedback.peerName
        CallFeedbackReason.CALL_FAILED -> stringResource(R.string.call_feedback_no_answer_sub)
        else -> stringResource(R.string.call_feedback_no_answer_sub)
    }

    val descText = when (feedback.reason) {
        CallFeedbackReason.NO_ANSWER -> stringResource(R.string.call_feedback_no_answer_desc)
        CallFeedbackReason.REJECTED_BY_PEER -> stringResource(R.string.call_feedback_rejected_desc, feedback.peerName)
        CallFeedbackReason.CALL_FAILED -> stringResource(R.string.call_feedback_no_answer_desc)
        else -> stringResource(R.string.call_feedback_no_answer_desc)
    }

    AlertDialog(
        onDismissRequest = {
            if (isRecording) cancelRecordingVoice()
            onDismiss()
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PhoneMissed,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.1f),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = subText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Module 5: Walkie-Talkie (PTT) Interactive Audio Composer
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isRecording) Color(0xFFEF4444).copy(alpha = 0.08f) else Color(0xFF0284C7).copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isRecording) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF0284C7).copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .scale(if (isRecording) pulseScale else 1f)
                                    .clip(CircleShape)
                                    .background(if (isRecording) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF0284C7).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (isRecording) Color(0xFFEF4444) else Color(0xFF0284C7),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isRecording) {
                                        val sec = recordingMillis / 1000
                                        stringResource(R.string.call_ended_recording_in_progress, sec / 60, sec % 60)
                                    } else {
                                        stringResource(R.string.call_fallback_ptt_action)
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.call_ended_walkie_talkie_hint),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isRecording) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { cancelRecordingVoice() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.call_voice_memo_cancel), fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { sendVoiceMemo() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.call_voice_memo_send), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = { startRecordingVoice() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isSending
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = stringResource(R.string.call_ended_recording_press_hold),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (isNoAnswer || isFailed) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.call_feedback_hint),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isRecording) cancelRecordingVoice()
                    onOpenChat(feedback.peerPhone)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.call_fallback_chat_action),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    if (isRecording) cancelRecordingVoice()
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.call_feedback_btn_dismiss),
                    fontSize = 12.sp
                )
            }
        }
    )
}
