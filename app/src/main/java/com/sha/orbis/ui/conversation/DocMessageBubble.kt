package com.sha.orbis.ui.conversation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.model.MessageDeliveryStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocMessageBubble(
    id: String,
    fileName: String,
    fileSizeFormatted: String,
    base64Data: String,
    caption: String = "",
    timestamp: Long,
    isMsgMine: Boolean,
    deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    val ext = remember(fileName) {
        fileName.substringAfterLast(".", "").lowercase()
    }

    val iconBgColor = when (ext) {
        "pdf" -> Color(0xFFEF4444)
        "doc", "docx" -> Color(0xFF3B82F6)
        "xls", "xlsx", "csv" -> Color(0xFF10B981)
        "txt", "json", "md" -> Color(0xFF8B5CF6)
        else -> Color(0xFF64748B)
    }

    val bubbleShape = if (isMsgMine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    fun handleOpenFile() {
        val file: File? = MediaAttachmentHelper.base64ToDocFile(context, base64Data, fileName, id)
        if (file != null && file.exists()) {
            val opened = MediaAttachmentHelper.openDocument(context, file)
            if (!opened) {
                Toast.makeText(context, context.getString(R.string.chat_error_file_open), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.chat_error_file_open), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(bubbleShape)
            .background(
                if (isMsgMine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (isMsgMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outlineVariant,
                bubbleShape
            )
            .clickable {
                if (onClick != null) onClick() else handleOpenFile()
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = fileSizeFormatted,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (caption.isNotBlank()) {
            Text(
                text = caption,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { handleOpenFile() },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.size(height = 32.dp, width = 110.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.chat_doc_open),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeFormatted,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
