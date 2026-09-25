package com.sha.orbis.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.call.CallFeedbackInfo
import com.sha.orbis.call.CallFeedbackReason

@Composable
fun OrbisCallFeedbackDialog(
    feedback: CallFeedbackInfo,
    onDismiss: () -> Unit,
    onOpenChat: (phone: String) -> Unit = {},
    onSendPlainSms: (phone: String) -> Unit = onOpenChat
) {
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
        onDismissRequest = onDismiss,
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                onClick = { onOpenChat(feedback.peerPhone) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.call_feedback_btn_chat),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.call_feedback_btn_dismiss),
                    fontSize = 12.sp
                )
            }
        }
    )
}
