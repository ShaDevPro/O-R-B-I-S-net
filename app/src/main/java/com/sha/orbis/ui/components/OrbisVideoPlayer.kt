package com.sha.orbis.ui.components

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sha.orbis.R
import com.sha.orbis.media.MediaDownloadManager
import com.sha.orbis.media.VideoMediaHelper
import kotlinx.coroutines.delay
import java.io.File

/**
 * Composant lecteur vidéo modulaire et souverain basé sur AndroidX Media3 (ExoPlayer).
 * Intègre l'ensemble des contrôles :
 * - Lecture / Pause (bouton central & barre de transport)
 * - Curseur interactif de recherche (Seeking / Scrubber)
 * - Durée écoulée et totale (ex: 00:15 / 00:45)
 * - Sourdine / Rétablissement du son
 * - Mode Plein Écran
 * - Gestion du cycle de vie sans fuite mémoire
 */
@OptIn(UnstableApi::class)
@Composable
fun OrbisVideoPlayer(
    videoPathOrId: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    loop: Boolean = false,
    initialMuted: Boolean = false,
    showFullScreenButton: Boolean = true,
    showDownloadButton: Boolean = false,
    downloadName: String? = null,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onPlayerEnded: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isNetworkUrl = remember(videoPathOrId) {
        videoPathOrId.startsWith("http://", ignoreCase = true) || videoPathOrId.startsWith("https://", ignoreCase = true)
    }
    val videoFile = remember(videoPathOrId, isNetworkUrl) {
        if (isNetworkUrl) null else VideoMediaHelper.getVideoFile(context, videoPathOrId)
    }

    if (!isNetworkUrl && (videoFile == null || !videoFile.exists())) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.video_file_not_found),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
        return
    }

    val videoUri = remember(videoPathOrId, isNetworkUrl, videoFile) {
        if (isNetworkUrl) Uri.parse(videoPathOrId) else Uri.fromFile(videoFile!!)
    }

    var isPlaying by remember { mutableStateOf(autoPlay) }
    var isBuffering by remember { mutableStateOf(false) }
    var isEnded by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(initialMuted) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderValue by remember { mutableFloatStateOf(0f) }
    var isFullScreenOpen by remember { mutableStateOf(false) }
    var isSavingVideo by remember(videoPathOrId) { mutableStateOf(false) }

    fun saveVideoToDevice() {
        if (isSavingVideo) return
        isSavingVideo = true
        MediaDownloadManager.saveVideoAsync(
            context = context,
            videoSource = videoPathOrId,
            customName = downloadName,
            showToast = true
        ) {
            isSavingVideo = false
        }
    }

    // Instance ExoPlayer
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (initialMuted) 0f else 1f
            playWhenReady = autoPlay
            prepare()
        }
    }

    // Gestion du cycle de vie du lecteur
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        isEnded = false
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        isEnded = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isEnded = true
                        isPlaying = false
                        onPlayerEnded?.invoke()
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) isEnded = false
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Suivi régulier de la position de lecture
    LaunchedEffect(exoPlayer, isPlaying, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (durationMs <= 0L && exoPlayer.duration > 0L) {
                durationMs = exoPlayer.duration
            }
            delay(200L)
        }
    }

    // Masquage automatique des contrôles après 3,5 secondes de lecture ininterrompue
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500L)
            showControls = false
        }
    }

    // Boîte englobante
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showControls = !showControls
            },
        contentAlignment = Alignment.Center
    ) {
        // Vue vidéo native ExoPlayer
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Indicateur de chargement / buffering
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(44.dp),
                strokeWidth = 3.dp
            )
        }

        // Overlay des contrôles (Play, Pause, Seek, Durée, Volume, Plein Écran)
        AnimatedVisibility(
            visible = showControls || !isPlaying || isEnded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            ) {
                // Bouton central Play / Pause / Replay
                Box(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    IconButton(
                        onClick = {
                            if (isEnded) {
                                exoPlayer.seekTo(0L)
                                exoPlayer.play()
                                isEnded = false
                            } else if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = when {
                                isEnded -> Icons.Default.Replay
                                isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Barre inférieure de contrôles (Play/Pause, Slider Seeking, Durée, Download, Mute, Fullscreen)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Slider de Seeking interactif
                    val totalDuration = if (durationMs > 0) durationMs.toFloat() else 1f
                    val currentPos = if (isUserSeeking) seekSliderValue else currentPositionMs.toFloat()

                    Slider(
                        value = currentPos.coerceIn(0f, totalDuration),
                        onValueChange = { newVal ->
                            isUserSeeking = true
                            seekSliderValue = newVal
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(seekSliderValue.toLong())
                            currentPositionMs = seekSliderValue.toLong()
                            isUserSeeking = false
                        },
                        valueRange = 0f..totalDuration,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Bouton Play / Pause miniature
                        IconButton(
                            onClick = {
                                if (isEnded) {
                                    exoPlayer.seekTo(0L)
                                    exoPlayer.play()
                                    isEnded = false
                                } else if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Affichage temporel : 00:15 / 01:30
                        val posDisplay = if (isUserSeeking) seekSliderValue.toLong() else currentPositionMs
                        Text(
                            text = "${VideoMediaHelper.formatDuration(posDisplay)} / ${VideoMediaHelper.formatDuration(durationMs)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        if (showDownloadButton) {
                            IconButton(
                                onClick = { saveVideoToDevice() },
                                enabled = !isSavingVideo,
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (isSavingVideo) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = stringResource(R.string.video_action_download),
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Bouton Mute / Unmute
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Bouton Plein Écran
                        if (showFullScreenButton) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    isFullScreenOpen = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Plein écran",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue modal plein écran complet
    if (isFullScreenOpen) {
        FullScreenVideoDialog(
            videoPathOrId = videoPathOrId,
            startPositionMs = currentPositionMs,
            isMutedInitial = isMuted,
            showDownloadButton = showDownloadButton,
            downloadName = downloadName,
            onDismiss = { resumePos, mutedState ->
                isFullScreenOpen = false
                isMuted = mutedState
                exoPlayer.volume = if (mutedState) 0f else 1f
                exoPlayer.seekTo(resumePos)
            }
        )
    }
}

/**
 * Dialogue plein écran souverain pour une immersion vidéo totale.
 */
@OptIn(UnstableApi::class)
@Composable
fun FullScreenVideoDialog(
    videoPathOrId: String,
    startPositionMs: Long = 0L,
    isMutedInitial: Boolean = false,
    showDownloadButton: Boolean = false,
    downloadName: String? = null,
    onDismiss: (resumePositionMs: Long, isMuted: Boolean) -> Unit
) {
    val context = LocalContext.current
    val isNetworkUrl = remember(videoPathOrId) {
        videoPathOrId.startsWith("http://", ignoreCase = true) || videoPathOrId.startsWith("https://", ignoreCase = true)
    }
    val videoFile = remember(videoPathOrId, isNetworkUrl) {
        if (isNetworkUrl) null else VideoMediaHelper.getVideoFile(context, videoPathOrId)
    }

    if (!isNetworkUrl && (videoFile == null || !videoFile.exists())) {
        onDismiss(0L, isMutedInitial)
        return
    }

    val videoUri = remember(videoPathOrId, isNetworkUrl, videoFile) {
        if (isNetworkUrl) Uri.parse(videoPathOrId) else Uri.fromFile(videoFile!!)
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isEnded by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(isMutedInitial) }
    var currentPositionMs by remember { mutableLongStateOf(startPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderValue by remember { mutableFloatStateOf(0f) }
    var isSavingVideo by remember(videoPathOrId) { mutableStateOf(false) }

    fun saveVideoToDevice() {
        if (isSavingVideo) return
        isSavingVideo = true
        MediaDownloadManager.saveVideoAsync(
            context = context,
            videoSource = videoPathOrId,
            customName = downloadName,
            showToast = true
        ) {
            isSavingVideo = false
        }
    }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            volume = if (isMutedInitial) 0f else 1f
            seekTo(startPositionMs)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isEnded = true
                        isPlaying = false
                    }
                    Player.STATE_IDLE -> isBuffering = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) isEnded = false
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (durationMs <= 0L && exoPlayer.duration > 0L) {
                durationMs = exoPlayer.duration
            }
            delay(200L)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000L)
            showControls = false
        }
    }

    Dialog(
        onDismissRequest = { onDismiss(currentPositionMs, isMuted) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    showControls = !showControls
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 3.dp
                )
            }

            // Contrôles Plein Écran
            AnimatedVisibility(
                visible = showControls || !isPlaying || isEnded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                ) {
                    // Barre d'action supérieure (Fermer)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = videoFile?.name ?: stringResource(R.string.chat_attach_video),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        IconButton(
                            onClick = { onDismiss(currentPositionMs, isMuted) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = Color.White
                            )
                        }
                    }

                    // Bouton Play / Pause central
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        IconButton(
                            onClick = {
                                if (isEnded) {
                                    exoPlayer.seekTo(0L)
                                    exoPlayer.play()
                                    isEnded = false
                                } else if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = when {
                                    isEnded -> Icons.Default.Replay
                                    isPlaying -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // Barre de contrôle inférieure
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        val totalDuration = if (durationMs > 0) durationMs.toFloat() else 1f
                        val currentPos = if (isUserSeeking) seekSliderValue else currentPositionMs.toFloat()

                        Slider(
                            value = currentPos.coerceIn(0f, totalDuration),
                            onValueChange = { newVal ->
                                isUserSeeking = true
                                seekSliderValue = newVal
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(seekSliderValue.toLong())
                                currentPositionMs = seekSliderValue.toLong()
                                isUserSeeking = false
                            },
                            valueRange = 0f..totalDuration,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    if (isEnded) {
                                        exoPlayer.seekTo(0L)
                                        exoPlayer.play()
                                        isEnded = false
                                    } else if (isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            val posDisplay = if (isUserSeeking) seekSliderValue.toLong() else currentPositionMs
                            Text(
                                text = "${VideoMediaHelper.formatDuration(posDisplay)} / ${VideoMediaHelper.formatDuration(durationMs)}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            if (showDownloadButton) {
                                IconButton(
                                    onClick = { saveVideoToDevice() },
                                    enabled = !isSavingVideo
                                ) {
                                    if (isSavingVideo) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = stringResource(R.string.video_action_download),
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else 1f
                                }
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = { onDismiss(currentPositionMs, isMuted) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Quitter plein écran",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
