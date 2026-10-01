package com.sha.orbis.ui.social.feed.post

import android.graphics.Bitmap
import android.os.SystemClock
import com.sha.orbis.call.diagnostic.FeedDebugTracker
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.cache.MediaMemoryCache
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.media.MediaDownloadManager
import com.sha.orbis.social.SocialPost
import com.sha.orbis.ui.components.FullScreenImageViewerDialog
import com.sha.orbis.ui.components.OrbisVideoPlayer
import com.sha.orbis.ui.social.feed.FeedDesignTokens
import com.sha.orbis.ui.social.feed.FeedDoubleTapHeartOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun FeedPostMediaBlock(
    post: SocialPost,
    showHeartBurst: Boolean,
    onDoubleTapLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val postImageSource = remember(post.mediaPath, post.mediaData) {
        val validLocalPath = post.mediaPath?.takeIf { path ->
            if (path.isBlank()) return@takeIf false
            try {
                val f = java.io.File(path)
                f.exists() && f.isFile && f.length() > 0L
            } catch (_: Exception) { false }
        }
        validLocalPath ?: post.mediaData?.takeIf { it.isNotBlank() }
    }

    var showFullScreenImage by remember { mutableStateOf(false) }
    if (showFullScreenImage && !postImageSource.isNullOrBlank()) {
        FullScreenImageViewerDialog(
            imagePathOrBase64 = postImageSource,
            title = post.content.take(40).ifBlank { post.authorName },
            onDismiss = { showFullScreenImage = false }
        )
    }

    val isVideoPost = remember(post.mediaType, post.mediaPath) {
        val validVideoFile = post.mediaPath?.takeIf { path ->
            try {
                val f = java.io.File(path)
                f.exists() && f.isFile && f.length() > 0L
            } catch (_: Exception) { false }
        }
        post.mediaType == "video" || (validVideoFile != null && post.mediaPath?.endsWith(".mp4", ignoreCase = true) == true)
    }

    var localVideoPath by remember(post.id, post.mediaPath, isVideoPost) {
        mutableStateOf(
            if (isVideoPost) {
                post.mediaPath?.takeIf { it.startsWith("/") }
            } else null
        )
    }
    var isDownloadingVideo by remember(post.id) { mutableStateOf(false) }
    var videoDownloadProgress by remember(post.id) { mutableIntStateOf(0) }

    LaunchedEffect(post.id, post.mediaPath, isVideoPost) {
        if (isVideoPost && localVideoPath == null) {
            val resolved = withContext(Dispatchers.IO) {
                post.mediaPath?.takeIf { java.io.File(it).exists() }
                    ?: com.sha.orbis.media.VideoMediaHelper.getVideoFile(context, post.id)?.absolutePath
            }
            if (resolved != null) {
                localVideoPath = resolved
            }
        }
    }

    LaunchedEffect(post.id, post.mediaData) {
        if (isVideoPost && localVideoPath == null && !post.mediaData.isNullOrBlank()) {
            isDownloadingVideo = true
            val file = withContext(Dispatchers.IO) {
                val cleanPostId = post.id.filter { it.isLetterOrDigit() || it == '_' }.take(32)
                    .ifBlank { "post_${System.currentTimeMillis()}" }
                val cleanBase64 = if (post.mediaData!!.contains(",")) {
                    post.mediaData!!.substringAfter(",")
                } else {
                    post.mediaData!!
                }
                com.sha.orbis.media.VideoMediaHelper.base64ToVideoFile(context, cleanBase64.trim(), cleanPostId) { pct ->
                    videoDownloadProgress = pct
                }
            }
            if (file != null && file.exists()) {
                localVideoPath = file.absolutePath
            }
            isDownloadingVideo = false
        }
    }

    LaunchedEffect(localVideoPath) {
        if (localVideoPath != null) {
            com.sha.orbis.telemetry.FeedTelemetryTracker.trackMediaViewed(context)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        when {
            isVideoPost -> {
                FeedPostVideoSurface(
                    localVideoPath = localVideoPath,
                    isDownloading = isDownloadingVideo,
                    downloadProgress = videoDownloadProgress
                )
            }
            !postImageSource.isNullOrBlank() -> {
                FeedPostPhotoSurface(
                    postId = post.id,
                    imageSource = postImageSource,
                    contentDescription = post.content.take(30),
                    showHeartBurst = showHeartBurst,
                    onSingleTap = {
                        showFullScreenImage = true
                        com.sha.orbis.telemetry.FeedTelemetryTracker.trackMediaViewed(context)
                    },
                    onDoubleTap = onDoubleTapLike,
                    onDownload = {
                        MediaDownloadManager.saveImageAsync(context, postImageSource, post.content.take(30))
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedPostVideoSurface(
    localVideoPath: String?,
    isDownloading: Boolean,
    downloadProgress: Int
) {
    val aspect = FeedDesignTokens.MediaAspectVideo
    when {
        !localVideoPath.isNullOrBlank() && java.io.File(localVideoPath).exists() -> {
            FeedVideoThumbnailPlayer(
                videoPath = localVideoPath,
                aspect = aspect
            )
        }
        isDownloading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(42.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = stringResource(R.string.video_downloading_progress, downloadProgress),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = FeedDesignTokens.HintSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.video_file_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Affiche une miniature statique de la vidéo avec un bouton Play.
 * ExoPlayer n'est instancié que lorsque l'utilisateur appuie sur Play.
 * Quand le composable quitte la composition (scroll éloigné),
 * DisposableEffect remet isPlayerActive à false → le player est libéré → pas d'accumulation RAM.
 */
@Composable
private fun FeedVideoThumbnailPlayer(
    videoPath: String,
    aspect: Float
) {
    var isPlayerActive by remember(videoPath) { mutableStateOf(false) }

    // Libérer le player si le composable quitte la composition (item scrollé hors de vue)
    DisposableEffect(videoPath) {
        onDispose {
            isPlayerActive = false
        }
    }

    if (isPlayerActive) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .background(Color.Black)
        ) {
            OrbisVideoPlayer(
                videoPathOrId = videoPath,
                modifier = Modifier.fillMaxSize(),
                autoPlay = true,
                showFullScreenButton = true
            )
        }
    } else {
        VideoThumbnailPreview(
            videoPath = videoPath,
            aspect = aspect,
            onPlayClick = { isPlayerActive = true }
        )
    }
}

/**
 * Extrait et affiche la première frame du fichier vidéo comme miniature.
 * Extraction effectuée sur le thread IO via MediaMetadataRetriever.
 */
@Composable
private fun VideoThumbnailPreview(
    videoPath: String,
    aspect: Float,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(videoPath) {
        val cacheKey = "vid_thumb_$videoPath"
        mutableStateOf(MediaMemoryCache.get(cacheKey))
    }

    LaunchedEffect(videoPath) {
        if (thumbnail != null) {
            FeedDebugTracker.logMediaLoad(videoPath, "video_thumb", fromCache = true, durationMs = 0)
            return@LaunchedEffect
        }
        val cacheKey = "vid_thumb_$videoPath"
        val start = SystemClock.elapsedRealtime()
        val loaded = withContext(Dispatchers.IO) {
            try {
                if (!isActive) return@withContext null

                // 1. Chercher si une miniature existe déjà sur le disque (créée par VideoMediaHelper)
                val pregenThumb = com.sha.orbis.media.VideoMediaHelper.getVideoThumbnailFile(context, videoPath)
                if (pregenThumb != null && pregenThumb.exists() && pregenThumb.length() > 0) {
                    val bmp = android.graphics.BitmapFactory.decodeFile(pregenThumb.absolutePath)
                    if (bmp != null) {
                        MediaMemoryCache.put(cacheKey, bmp)
                        return@withContext bmp
                    }
                }

                if (!isActive) return@withContext null

                // 2. Si non trouvée, extraire la frame avec MediaMetadataRetriever
                val raw: Bitmap? = android.media.MediaMetadataRetriever().use { mmr ->
                    mmr.setDataSource(videoPath)
                    mmr.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (raw == null) return@withContext null
                // Downscale to max 480px to avoid OOM (native frame can be 1080p+)
                val maxDim = 480
                val w = raw.width
                val h = raw.height
                val scaled = if (w > maxDim || h > maxDim) {
                    val ratio = w.toFloat() / h.toFloat()
                    val (tw, th) = if (w > h) Pair(maxDim, (maxDim / ratio).toInt().coerceAtLeast(1))
                                   else Pair((maxDim * ratio).toInt().coerceAtLeast(1), maxDim)
                    val s = Bitmap.createScaledBitmap(raw, tw, th, true)
                    raw.recycle()
                    s
                } else raw
                // Store in memory cache
                MediaMemoryCache.put(cacheKey, scaled)
                scaled
            } catch (e: Exception) {
                FeedDebugTracker.logMediaLoad(videoPath, "video_thumb", fromCache = false, durationMs = 0, error = e.message)
                null
            }
        }
        val elapsed = SystemClock.elapsedRealtime() - start
        thumbnail = loaded
        FeedDebugTracker.logMediaLoad(videoPath, "video_thumb", fromCache = false, durationMs = elapsed)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val bmp = thumbnail
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Bouton Play central
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.video_play),
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun FeedPostPhotoSurface(
    postId: String,
    imageSource: String,
    contentDescription: String,
    showHeartBurst: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onDownload: () -> Unit
) {
    var postBitmap by remember(imageSource) {
        mutableStateOf(MediaMemoryCache.get(imageSource))
    }
    var loadFailed by remember(imageSource) { mutableStateOf(false) }

    LaunchedEffect(imageSource) {
        if (postBitmap != null) {
            FeedDebugTracker.logMediaLoad(postId, "image", fromCache = true, durationMs = 0)
            return@LaunchedEffect
        }
        val start = SystemClock.elapsedRealtime()
        val loaded = withContext(Dispatchers.IO) {
            MediaAttachmentHelper.loadBitmap(imageSource)
        }
        val elapsed = SystemClock.elapsedRealtime() - start
        postBitmap = loaded
        if (loaded == null) {
            loadFailed = true
        }
        FeedDebugTracker.logMediaLoad(postId, "image", fromCache = false, durationMs = elapsed)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(FeedDesignTokens.MediaAspectPhoto)
            .background(Color.Black)
            .pointerInput(postId) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { onDoubleTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val bmp = postBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDownload,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = stringResource(R.string.media_download_image),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else if (loadFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        FeedDoubleTapHeartOverlay(
            visible = showHeartBurst,
            modifier = Modifier.matchParentSize()
        )
    }
}
