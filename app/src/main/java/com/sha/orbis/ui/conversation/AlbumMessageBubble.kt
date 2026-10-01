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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.model.MessageDeliveryStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WhatsApp-style multi-photo album message bubble.
 * Renders photos in a compact 2x2 collage with "+ N" overlay when > 4 photos.
 */
@Composable
fun AlbumMessageBubble(
    images: List<String>,
    caption: String,
    timestamp: Long,
    isMsgMine: Boolean,
    deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    onImageClick: (index: Int, base64OrPath: String) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

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

    val textColor = when {
        isDark -> Color(0xFFF8FAFC)
        else -> Color(0xFF0F172A)
    }

    val subTextColor = when {
        isDark -> Color(0xFF94A3B8)
        else -> Color(0xFF64748B)
    }

    Column(
        modifier = Modifier
            .width(270.dp)
            .clip(bubbleShape)
            .background(bubbleBg)
            .border(1.dp, bubbleBorder, bubbleShape)
            .padding(3.dp)
    ) {
        // Image Collage Container (Rounded inner frame)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.05f))
        ) {
            when {
                // 1 Image
                images.size == 1 -> {
                    AlbumCellImage(
                        base64OrPath = images[0],
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable { onImageClick(0, images[0]) }
                    )
                }

                // 2 Images: side by side
                images.size == 2 -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AlbumCellImage(
                            base64OrPath = images[0],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable { onImageClick(0, images[0]) }
                        )
                        AlbumCellImage(
                            base64OrPath = images[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable { onImageClick(1, images[1]) }
                        )
                    }
                }

                // 3 Images: 1 top, 2 bottom
                images.size == 3 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AlbumCellImage(
                            base64OrPath = images[0],
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(125.dp)
                                .clickable { onImageClick(0, images[0]) }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AlbumCellImage(
                                base64OrPath = images[1],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable { onImageClick(1, images[1]) }
                            )
                            AlbumCellImage(
                                base64OrPath = images[2],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable { onImageClick(2, images[2]) }
                            )
                        }
                    }
                }

                // 4+ Images: 2x2 grid with "+ N" overlay on 4th cell
                else -> {
                    val remainingCount = images.size - 4

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Top Row: 2 images
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AlbumCellImage(
                                base64OrPath = images[0],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable { onImageClick(0, images[0]) }
                            )
                            AlbumCellImage(
                                base64OrPath = images[1],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable { onImageClick(1, images[1]) }
                            )
                        }

                        // Bottom Row: 2 images (with overlay on 4th if > 4)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AlbumCellImage(
                                base64OrPath = images[2],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable { onImageClick(2, images[2]) }
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable { onImageClick(3, images[3]) },
                                contentAlignment = Alignment.Center
                            ) {
                                AlbumCellImage(
                                    base64OrPath = images[3],
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (remainingCount > 0) {
                                    // WhatsApp "+ N" semi-transparent dark overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.52f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+ $remainingCount",
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Optional Caption & Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    fontSize = 13.sp,
                    color = textColor,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
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
}

@Composable
private fun AlbumCellImage(
    base64OrPath: String,
    modifier: Modifier = Modifier
) {
    val bitmap: Bitmap? = remember(base64OrPath) {
        MediaAttachmentHelper.loadBitmap(base64OrPath)
    }

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
