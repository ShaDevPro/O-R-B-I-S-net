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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
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

@Composable
fun ImageMessageBubble(
    base64OrPath: String,
    caption: String,
    timestamp: Long,
    isMsgMine: Boolean,
    deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    onClick: () -> Unit
) {
    val bitmap: Bitmap? = remember(base64OrPath) {
        MediaAttachmentHelper.loadBitmap(base64OrPath)
    }

    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val bubbleShape = if (isMsgMine) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 3.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    } else {
        RoundedCornerShape(topStart = 3.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
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
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        // Image Thumbnail Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .heightIn(min = 160.dp, max = 240.dp)
                .background(Color.Black.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = caption.ifBlank { "Photo" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 240.dp)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Caption & Meta footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    fontSize = 13.sp,
                    color = textColor,
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
}
