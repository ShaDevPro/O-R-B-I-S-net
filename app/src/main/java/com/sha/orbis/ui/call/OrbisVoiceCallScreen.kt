package com.sha.orbis.ui.call

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.sha.orbis.ai.guard.ThreatLevel
import com.sha.orbis.ai.suggestions.OrbisSuggestionLibrary
import com.sha.orbis.call.OrbisCallManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import com.sha.orbis.call.OrbisProximityManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.call.CallSession
import com.sha.orbis.call.CallStatus
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.theme.OrbisColorPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbisVoiceCallScreen(
    session: CallSession,
    onAcceptCall: () -> Unit = {},
    onAcceptVoiceOnly: () -> Unit = onAcceptCall,
    onDeclineCall: () -> Unit = {},
    onDeclineWithReply: ((message: String) -> Unit)? = null,
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onLeaveVoiceMemo: () -> Unit = { OrbisCallManager.leaveVoiceMemoFallback() }
) {
    val isDark = isSystemInDarkTheme()
    var showCryptoInfoDialog by remember { mutableStateOf(false) }
    var showQuickDeclineSheet by remember { mutableStateOf(false) }
    val isNear by OrbisProximityManager.isNear.collectAsState()

    // Pulsing animation for calling / ringing states with safe bounded float values
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val rawPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (session.status == CallStatus.CONNECTED) 1.06f else 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val safePulseScale = if (rawPulseScale.isNaN()) 1.0f else rawPulseScale.coerceIn(1.0f, 1.25f)

    val bgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val cardBgColor = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    val subtextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
        // TOP: Security Bar & SAS Chip
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Badge (Pure E2EE Internet Audio)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardBgColor,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                shadowElevation = 1.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = OrbisColorPalette.StatusActive,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Orbis E2EE • Internet Audio",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbisColorPalette.StatusActive
                    )
                }
            }

            // SAS Safety Verification Chip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showCryptoInfoDialog = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${stringResource(R.string.call_sas_label)} : ${session.safetyNumber}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // CENTER: Avatar with Glowing Halo & Contact Info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(180.dp)
            ) {
                // Outer Pulsing Glow Circle
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .scale(safePulseScale)
                        .clip(CircleShape)
                        .background(
                            OrbisColorPalette.StatusActive
                                .copy(alpha = if (session.status == CallStatus.CONNECTED) 0.12f else 0.22f)
                        )
                )

                // Middle Ring
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            OrbisColorPalette.StatusActive.copy(alpha = 0.5f),
                            CircleShape
                        )
                )

                // Avatar
                OrbisAvatar(
                    avatarPath = session.peerAvatar,
                    name = session.peerName,
                    size = 110.dp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = session.peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = session.peerPhone,
                    fontSize = 13.sp,
                    color = subtextColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Call Status / Duration
                val statusText = when (session.status) {
                    CallStatus.IDLE, CallStatus.OUTGOING_CALLING, CallStatus.CONNECTING -> stringResource(R.string.call_status_calling)
                    CallStatus.OUTGOING_RINGING -> stringResource(R.string.call_status_ringing)
                    CallStatus.INCOMING_RINGING -> stringResource(R.string.call_status_incoming)
                    CallStatus.CONNECTED -> {
                        val minutes = session.durationSeconds / 60
                        val seconds = session.durationSeconds % 60
                        val label = stringResource(R.string.call_status_connected)
                        String.format(java.util.Locale.US, "%02d:%02d • %s", minutes, seconds, label)
                    }
                    CallStatus.ENDED -> stringResource(R.string.call_status_ended)
                }

                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (session.status == CallStatus.CONNECTED) OrbisColorPalette.StatusActive else subtextColor,
                    textAlign = TextAlign.Center
                )
            }
        }

        // BOTTOM: Call Control Buttons
        if (session.status == CallStatus.INCOMING_RINGING) {
            // INCOMING CALL DECISION CARD & CONTROLS
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBgColor,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Notice Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (session.isVideoCall) stringResource(R.string.call_status_incoming_video) else stringResource(R.string.call_status_incoming),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }

                    session.guardAnalysis?.let { analysis ->
                        CallGuardBanner(analysis = analysis)
                    }

                    Text(
                        text = stringResource(R.string.call_receiver_notice_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = subtextColor,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Action Buttons (Refuser / Message / Répondre)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline Button (Red)
                        androidx.compose.material3.Button(
                            onClick = onDeclineCall,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                                contentColor = Color(0xFFEF4444)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.call_action_decline),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Smart Message Quick Decline Button (Sky Blue)
                        androidx.compose.material3.IconButton(
                            onClick = { showQuickDeclineSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF0284C7).copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Message,
                                contentDescription = stringResource(R.string.missed_call_action_message),
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (session.isVideoCall) {
                            // Voice Only Button (Blue)
                            androidx.compose.material3.Button(
                                onClick = onAcceptVoiceOnly,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                    contentColor = Color(0xFF3B82F6)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.call_action_accept_voice_only),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Accept Button (Green)
                        androidx.compose.material3.Button(
                            onClick = onAcceptCall,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22C55E),
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (session.isVideoCall) stringResource(R.string.call_action_accept_video) else stringResource(R.string.call_action_accept),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                }
            }
        } else {
            // OUTGOING / CONNECTED / ENDED CONTROLS: Mute, Speaker, Info, End Call
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Outgoing Ringing / Calling: Caller Security Notice (without switch button)
                if (session.status == CallStatus.OUTGOING_CALLING || session.status == CallStatus.OUTGOING_RINGING) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFFF59E0B).copy(alpha = 0.4f)
                        ),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.call_caller_waiting_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = subtextColor,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Mic Button
                    CallActionButton(
                        icon = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (session.isMuted) stringResource(R.string.call_action_mute) else stringResource(R.string.call_action_unmute),
                        isActive = session.isMuted,
                        activeColor = MaterialTheme.colorScheme.error,
                        onClick = onToggleMute
                    )

                    // Speaker Button
                    CallActionButton(
                        icon = if (session.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                        label = if (session.isSpeakerOn) stringResource(R.string.call_action_speaker) else stringResource(R.string.call_action_earpiece),
                        isActive = session.isSpeakerOn,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = onToggleSpeaker
                    )

                    // Walkie-Talkie Voice Memo Button (on Outgoing Call)
                    val isOutgoingRinging = session.status == CallStatus.OUTGOING_CALLING || session.status == CallStatus.OUTGOING_RINGING
                    if (isOutgoingRinging) {
                        CallActionButton(
                            icon = Icons.Default.Mic,
                            label = stringResource(R.string.call_action_leave_voice_note),
                            isActive = false,
                            activeColor = Color(0xFF0284C7),
                            onClick = onLeaveVoiceMemo
                        )
                    }

                    // Crypto Info Button
                    CallActionButton(
                        icon = Icons.Default.Shield,
                        label = stringResource(R.string.call_action_crypto_info),
                        isActive = false,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = { showCryptoInfoDialog = true }
                    )
                }

                // End Call Button (Large Red Circular Button)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .clickable { onEndCall() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = stringResource(R.string.call_action_end),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Crypto / Security Info Dialog
    if (showCryptoInfoDialog) {
        AlertDialog(
            onDismissRequest = { showCryptoInfoDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = OrbisColorPalette.StatusActive,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.call_crypto_details_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.call_crypto_details_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${stringResource(R.string.call_sas_label)} : ${session.safetyNumber}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.call_sas_hint),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showCryptoInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

        if (showQuickDeclineSheet) {
            val context = LocalContext.current
            val suggestions = remember(session.guardAnalysis) {
                OrbisSuggestionLibrary.getDiverseDeclineSuggestions(
                    isContactSaved = session.guardAnalysis?.isVerifiedContact ?: true
                )
            }
            var customReplyText by remember { mutableStateOf("") }

            ModalBottomSheet(
                onDismissRequest = { showQuickDeclineSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = cardBgColor,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Refuser et répondre par message",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }

                    Text(
                        text = "L'appel sera refusé et votre réponse sera transmise instantanément :",
                        style = MaterialTheme.typography.bodySmall,
                        color = subtextColor
                    )

                    // Contextual smart suggestions
                    suggestions.forEach { suggestion ->
                        Surface(
                            onClick = {
                                showQuickDeclineSheet = false
                                if (onDeclineWithReply != null) {
                                    onDeclineWithReply(suggestion.text)
                                } else {
                                    OrbisCallManager.rejectCallWithQuickReply(context, suggestion.text)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = suggestion.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = suggestion.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Custom message input row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customReplyText,
                            onValueChange = { customReplyText = it },
                            placeholder = { Text("Autre message...", fontSize = 13.sp, color = subtextColor) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        IconButton(
                            onClick = {
                                if (customReplyText.isNotBlank()) {
                                    showQuickDeclineSheet = false
                                    val text = customReplyText.trim()
                                    if (onDeclineWithReply != null) {
                                        onDeclineWithReply(text)
                                    } else {
                                        OrbisCallManager.rejectCallWithQuickReply(context, text)
                                    }
                                }
                            },
                            enabled = customReplyText.isNotBlank(),
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = if (customReplyText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Envoyer",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // True Black & Touch-absorbing overlay when phone is near ear (Proximity Sensor)
        if (isNear && !session.isSpeakerOn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {}
            )
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val defaultBtnBg = if (isDark) Color(0xFF1E293B) else Color(0xFFEEF2F6)
    val defaultIconTint = if (isDark) Color(0xFFF8FAFC) else Color(0xFF334155)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else defaultBtnBg)
                .border(
                    width = 1.dp,
                    color = if (isActive) activeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.White else defaultIconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CallGuardBanner(
    analysis: com.sha.orbis.ai.guard.CallGuardAnalysisResult,
    modifier: Modifier = Modifier
) {
    val isDangerous = analysis.isDangerous
    val isSuspicious = analysis.threatLevel == ThreatLevel.SUSPICIOUS

    val bannerBg = when {
        isDangerous -> Color(0xFFEF4444).copy(alpha = 0.12f)
        isSuspicious -> Color(0xFFF59E0B).copy(alpha = 0.12f)
        else -> Color(0xFF0284C7).copy(alpha = 0.10f)
    }
    val borderCol = when {
        isDangerous -> Color(0xFFEF4444).copy(alpha = 0.4f)
        isSuspicious -> Color(0xFFF59E0B).copy(alpha = 0.4f)
        else -> Color(0xFF0284C7).copy(alpha = 0.35f)
    }
    val iconVector = when {
        isDangerous -> Icons.Default.Warning
        isSuspicious -> Icons.Default.Info
        else -> Icons.Default.VerifiedUser
    }
    val iconTint = when {
        isDangerous -> Color(0xFFEF4444)
        isSuspicious -> Color(0xFFF59E0B)
        else -> Color(0xFF0284C7)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bannerBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = analysis.threatTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = iconTint
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = iconTint.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "${analysis.trustScore}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = iconTint,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = analysis.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

