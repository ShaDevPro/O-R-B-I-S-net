package com.sha.orbis.ui.call

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import com.sha.orbis.R
import com.sha.orbis.call.CallSession
import com.sha.orbis.call.CallStatus
import com.sha.orbis.call.OrbisWebRTCManager
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.theme.OrbisColorPalette
import org.webrtc.SurfaceViewRenderer
import org.webrtc.RendererCommon

@Composable
fun OrbisVideoCallScreen(
    session: CallSession,
    onAcceptVideoCall: () -> Unit = {},
    onAcceptVoiceOnly: () -> Unit = {},
    onDeclineCall: () -> Unit = {},
    onToggleCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onEndCall: () -> Unit = {}
) {
    // WebRTC VideoFrame flows — non-null = flux actif
    val remoteVideoFrame by OrbisWebRTCManager.remoteFrame.collectAsState()
    val localVideoFrame  by OrbisWebRTCManager.localFrame.collectAsState()
    val isLocalCameraEnabled  = session.isCameraEnabled
    val isRemoteCameraActive  = remoteVideoFrame != null

    val context = LocalContext.current

    var isSwapped by remember { mutableStateOf(false) }
    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    var showCryptoInfoDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val rawPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (session.status == CallStatus.CONNECTED) 1.05f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val safePulseScale = if (rawPulseScale.isNaN()) 1.0f else rawPulseScale.coerceIn(1.0f, 1.25f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // 1. FULLSCREEN MAIN FEED (DEFAULT: DESTINATAIRE / PEER, SWAPPED: APPELANT / LOCAL)
        val showRemoteInMain = !isSwapped
        val hasMainVideo = if (showRemoteInMain) {
            remoteVideoFrame != null && isRemoteCameraActive
        } else {
            localVideoFrame != null && isLocalCameraEnabled
        }

        if (hasMainVideo) {
            // WebRTC SurfaceViewRenderer — rendu natif OpenGL ES (aucune copie CPU)
            key(isSwapped) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(OrbisWebRTCManager.eglBaseContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setEnableHardwareScaler(true)
                            val isLocal = !showRemoteInMain
                            setMirror(isLocal && session.isFrontCamera)
                            if (showRemoteInMain) {
                                OrbisWebRTCManager.attachRemoteRenderer(this)
                            } else {
                                OrbisWebRTCManager.attachLocalRenderer(this)
                            }
                        }
                    },
                    update = { renderer ->
                        val isLocal = !showRemoteInMain
                        renderer.setMirror(isLocal && session.isFrontCamera)
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { renderer ->
                        OrbisWebRTCManager.detachRenderer(renderer)
                        renderer.release()
                    }
                )
            }
            // Subtle dark overlay to ensure top and bottom controls readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        } else {
            // Placeholder / Caller profile view
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B),
                                Color(0xFF0F172A)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Pulsing outer halo in ringing/calling states
                        if (session.status != CallStatus.CONNECTED) {
                            Box(
                                modifier = Modifier
                                    .size(136.dp)
                                    .scale(safePulseScale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            )
                        }

                        OrbisAvatar(
                            avatarPath = session.peerAvatar,
                            name = session.peerName,
                            size = 110.dp
                        )
                    }

                    Text(
                        text = session.peerName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    val statusMessage = when (session.status) {
                        CallStatus.INCOMING_RINGING -> stringResource(R.string.call_status_incoming_video)
                        CallStatus.OUTGOING_CALLING, CallStatus.CONNECTING -> stringResource(R.string.call_status_calling)
                        CallStatus.OUTGOING_RINGING -> stringResource(R.string.call_status_ringing)
                        CallStatus.CONNECTED -> {
                            if (showRemoteInMain) {
                                if (isRemoteCameraActive) {
                                    stringResource(R.string.call_video_connecting)
                                } else {
                                    stringResource(R.string.call_video_peer_camera_off)
                                }
                            } else {
                                stringResource(R.string.call_video_camera_off)
                            }
                        }
                        CallStatus.ENDED -> stringResource(R.string.call_status_ended)
                        CallStatus.IDLE -> ""
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = statusMessage,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 2. TOP SECURITY & DURATION BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // E2EE SAS Chip (clickable for security verification)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.clickable { showCryptoInfoDialog = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = OrbisColorPalette.StatusActive,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "E2EE • SAS: ${session.safetyNumber}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Duration / Active Badge
            if (session.status == CallStatus.CONNECTED) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    val minutes = session.durationSeconds / 60
                    val seconds = session.durationSeconds % 60
                    Text(
                        text = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Camera flip button shortcut in top bar
            IconButton(
                onClick = onSwitchCamera,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = stringResource(R.string.call_action_switch_camera),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 3. FLOATING PIP (PICTURE-IN-PICTURE) PREVIEW - WHATSAPP STYLE
        // By default shows local camera feed so the caller/user can see what they share in real-time
        // Draggable, tap to swap with main screen, flip-camera icon, and "Vous" / Peer label
        if (session.status != CallStatus.INCOMING_RINGING) {
            val showLocalInPip = !isSwapped
            val hasPipVideo = if (showLocalInPip) {
                localVideoFrame != null && isLocalCameraEnabled
            } else {
                remoteVideoFrame != null && isRemoteCameraActive
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 16.dp)
                    .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { totalDragDistance = 0f },
                            onDragEnd = {
                                if (totalDragDistance < 15f) {
                                    isSwapped = !isSwapped
                                }
                            },
                            onDragCancel = {},
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragDistance += kotlin.math.hypot(dragAmount.x, dragAmount.y)
                                pipOffsetX = (pipOffsetX + dragAmount.x).coerceIn(-280f, 20f)
                                pipOffsetY = (pipOffsetY + dragAmount.y).coerceIn(-20f, 450f)
                            }
                        )
                    }
                    .width(112.dp)
                    .height(158.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B))
            ) {
                if (hasPipVideo) {
                    // WebRTC SurfaceViewRenderer pour le PiP
                    // Essentiel : sous Android, un SurfaceView superposé à un autre SurfaceView
                    // doit obligatoirement avoir setZOrderMediaOverlay(true) avant d'être attaché,
                    // sinon le SurfaceView plein écran du correspondant l'occulte totalement.
                    key(isSwapped) {
                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    setZOrderMediaOverlay(true)
                                    init(OrbisWebRTCManager.eglBaseContext, null)
                                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                    setEnableHardwareScaler(true)
                                    val isLocal = showLocalInPip
                                    setMirror(isLocal && session.isFrontCamera)
                                    if (showLocalInPip) {
                                        OrbisWebRTCManager.attachLocalRenderer(this)
                                    } else {
                                        OrbisWebRTCManager.attachRemoteRenderer(this)
                                    }
                                }
                            },
                            update = { renderer ->
                                val isLocal = showLocalInPip
                                renderer.setMirror(isLocal && session.isFrontCamera)
                            },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { renderer ->
                                OrbisWebRTCManager.detachRenderer(renderer)
                                renderer.release()
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (showLocalInPip) {
                                stringResource(R.string.call_video_camera_off)
                            } else {
                                session.peerName
                            },
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Mini camera flip icon in top-right corner of PiP when showing local camera
                if (showLocalInPip) {
                    IconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = stringResource(R.string.call_action_switch_camera),
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Bottom badge: "Vous" / Peer Name
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = if (showLocalInPip) {
                            stringResource(R.string.call_pip_label_self)
                        } else {
                            session.peerName.take(10)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 4. BOTTOM CONTROLS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (session.status == CallStatus.INCOMING_RINGING) {
                // INCOMING VIDEO CALL: 3 WhatsApp-style Choices (Refuser, Vocal seulement, Accepter en vidéo)
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.call_status_incoming_video),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Refuser (Red)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = onDeclineCall,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallEnd,
                                        contentDescription = stringResource(R.string.call_action_decline),
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.call_action_decline),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFCA5A5)
                                )
                            }

                            // 2. Répondre en vocal seulement (Blue / Slate)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = onAcceptVoiceOnly,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = stringResource(R.string.call_action_accept_voice_only),
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.call_action_accept_voice_only),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF93C5FD)
                                )
                            }

                            // 3. Accepter en vidéo (Green)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = onAcceptVideoCall,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = stringResource(R.string.call_action_accept_video),
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.call_action_accept_video),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF86EFAC)
                                )
                            }
                        }
                    }
                }
            } else {
                // ACTIVE CALL CONTROLS: Camera Toggle, Camera Flip, Mute, Speaker, Hangup
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle Camera On/Off
                        IconButton(
                            onClick = onToggleCamera,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isLocalCameraEnabled) Color.White.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.35f))
                        ) {
                            Icon(
                                imageVector = if (isLocalCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = stringResource(R.string.call_action_toggle_camera),
                                tint = if (isLocalCameraEnabled) Color.White else Color(0xFFFCA5A5),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Switch Camera Front/Back
                        IconButton(
                            onClick = onSwitchCamera,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = stringResource(R.string.call_action_switch_camera),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Toggle Mute / Mic
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (!session.isMuted) Color.White.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.35f))
                        ) {
                            Icon(
                                imageVector = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (session.isMuted) Color(0xFFFCA5A5) else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Toggle Speaker
                        IconButton(
                            onClick = onToggleSpeaker,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (session.isSpeakerOn) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = if (session.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // End Call (Red Button)
                        IconButton(
                            onClick = onEndCall,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = stringResource(R.string.call_action_switch_to_gsm),
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // CRYPTO & SAS SECURITY VERIFICATION DIALOG
    if (showCryptoInfoDialog) {
        AlertDialog(
            onDismissRequest = { showCryptoInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.call_crypto_details_title),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.call_crypto_details_body),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.call_sas_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = session.safetyNumber,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 4.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.call_sas_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
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
}
