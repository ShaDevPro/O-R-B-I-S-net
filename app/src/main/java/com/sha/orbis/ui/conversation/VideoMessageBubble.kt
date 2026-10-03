package com.sha.orbis.ui.conversation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.media.MediaDownloadManager
import com.sha.orbis.media.VideoMediaHelper
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.ui.components.FullScreenVideoDialog
import com.sha.orbis.ui.components.OrbisVideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bulle de message vidéo souveraine pour les discussions.
 * Affiche la miniature locale, la durée, et permet la lecture in-line ou plein écran
 * avec l'ensemble des contrôles (seek, play/pause, durée, mute).
 */
@Composable
fun VideoMessageBubble(
    videoPayload: VideoMediaHelper.VideoPayload,
    timestamp: Long,
    isMsgMine: Boolean,
    deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    onVideoClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val videoId = videoPayload.id
    var localFileState by remember(videoId) { mutableStateOf(VideoMediaHelper.getVideoFile(context, videoId)) }
    val videoSource = localFileState?.takeIf { it.exists() }?.absolutePath ?: videoPayload.url ?: videoId
    val hasPlayableSource = (localFileState != null && localFileState!!.exists()) || !videoPayload.url.isNullOrBlank()

    var thumbnailBitmap by remember(videoId) {
        mutableStateOf<Bitmap?>(VideoMediaHelper.loadThumbnailBitmap(context, videoId))
    }

    var isDownloading by remember(videoId) { mutableStateOf(false) }
    var downloadProgress by remember(videoId) { mutableIntStateOf(0) }
    var isSavingVideo by remember(videoId) { mutableStateOf(false) }

    fun saveVideoToDevice() {
        if (isSavingVideo || !hasPlayableSource) return
        isSavingVideo = true
        MediaDownloadManager.saveVideoAsync(
            context = context,
            videoSource = videoSource,
            customName = videoId,
            showToast = true
        ) {
            isSavingVideo = false
        }
    }

    // Téléchargement automatique en arrière-plan depuis Blossom CDN ou reconstitution Base64
    LaunchedEffect(videoId, videoPayload.url, videoPayload.base64Data) {
        if (localFileState == null || !localFileState!!.exists()) {
            if (!videoPayload.url.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    val file = com.sha.orbis.nostr.media.BlossomMediaManager.downloadVideo(context, videoPayload.url, videoId)
                    if (file != null && file.exists()) {
                        localFileState = file
                        if (thumbnailBitmap == null) {
                            thumbnailBitmap = VideoMediaHelper.loadThumbnailBitmap(context, videoId)
                        }
                    }
                }
            } else if (!videoPayload.base64Data.isNullOrBlank()) {
                isDownloading = true
                val file = withContext(Dispatchers.IO) {
                    VideoMediaHelper.base64ToVideoFile(context, videoPayload.base64Data, videoId) { pct ->
                        downloadProgress = pct
                    }
                }
                localFileState = file
                thumbnailBitmap = withContext(Dispatchers.IO) {
                    VideoMediaHelper.loadThumbnailBitmap(context, videoId)
                }
                isDownloading = false
            }
        }
    }

    var isPlayingInline by remember { mutableStateOf(false) }
    var isFullScreenOpen by remember { mutableStateOf(false) }

    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    val bubbleShape = if (isMsgMine) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

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
    val captionTextColor = when {
        isDark -> Color(0xFFF8FAFC)
        else -> Color(0xFF0F172A)
    }
    val subTextColor = when {
        isDark -> Color(0xFF94A3B8)
        else -> Color(0xFF64748B)
    }

    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(bubbleShape)
            .background(bubbleBg)
            .border(1.dp, bubbleBorder, bubbleShape)
    ) {
        if (isPlayingInline && hasPlayableSource) {
            // Lecteur vidéo interactif in-line
            OrbisVideoPlayer(
                videoPathOrId = videoSource,
                autoPlay = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                showDownloadButton = true,
                downloadName = videoId,
                onPlayerEnded = {
                    isPlayingInline = false
                }
            )
        } else {
            // Miniature vidéo avec badge de durée et bouton Play ou progression de téléchargement
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 220.dp)
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable {
                        if (!isDownloading && hasPlayableSource) {
                            if (onVideoClick != null) {
                                onVideoClick()
                            } else {
                                isPlayingInline = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = "Vidéo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 220.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                if (isDownloading) {
                    // Jauge circulaire avec pourcentage de téléchargement
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(36.dp),
                                color = Color.White,
                                strokeWidth = 3.5.dp
                            )
                            Text(
                                text = stringResource(R.string.video_downloading_progress, downloadProgress),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (hasPlayableSource) {
                    // Bouton Play central
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Lire la vidéo",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                if (hasPlayableSource) {
                    IconButton(
                        onClick = { saveVideoToDevice() },
                        enabled = !isSavingVideo,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f))
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
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Badge de durée en bas à droite
                if (videoPayload.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = VideoMediaHelper.formatDuration(videoPayload.durationMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Légende et statut
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (videoPayload.caption.isNotBlank()) {
                Text(
                    text = videoPayload.caption,
                    fontSize = 13.sp,
                    color = captionTextColor,
                    lineHeight = 17.sp
                )
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

    if (isFullScreenOpen && hasPlayableSource) {
        FullScreenVideoDialog(
            videoPathOrId = videoSource,
            showDownloadButton = true,
            downloadName = videoId,
            onDismiss = { _, _ -> isFullScreenOpen = false }
        )
    }
}
