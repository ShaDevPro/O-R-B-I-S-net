package com.sha.orbis.ui.conversation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.media.MediaAttachmentHelper
import java.io.File

/**
 * Représente une pièce jointe en attente de confirmation et de légende par l'utilisateur.
 */
sealed interface PendingAttachment {
    data class Image(
        val base64: String,
        val bitmap: Bitmap? = null,
        val file: File? = null
    ) : PendingAttachment

    data class Document(
        val file: File,
        val displayName: String,
        val fileSizeFormatted: String,
        val base64: String
    ) : PendingAttachment

    data class PendingImageItem(
        val base64: String,
        val file: File? = null,
        val bitmap: Bitmap? = null
    )

    data class MultiImage(
        val images: List<PendingImageItem>
    ) : PendingAttachment

    data class Video(
        val videoId: String,
        val localFile: File,
        val thumbnailBitmap: Bitmap? = null,
        val durationMs: Long = 0L,
        val width: Int = 720,
        val height: Int = 1280,
        val base64: String? = null,
        val url: String? = null
    ) : PendingAttachment
}

/**
 * Dialogue modulaire de prévisualisation et d'ajout de légende/note avant l'envoi dans la discussion.
 * Style épuré et moderne inspiré de WhatsApp et Telegram.
 */
@Composable
fun AttachmentPreviewDialog(
    attachment: PendingAttachment,
    onDismiss: () -> Unit,
    onSend: (caption: String) -> Unit
) {
    var captionText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (attachment) {
                        is PendingAttachment.Image -> stringResource(R.string.chat_attach_preview_title_image)
                        is PendingAttachment.MultiImage -> stringResource(R.string.chat_attach_selected_count, attachment.images.size)
                        is PendingAttachment.Document -> stringResource(R.string.chat_attach_preview_title_doc)
                        is PendingAttachment.Video -> stringResource(R.string.chat_attach_preview_title_video)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Zone d'aperçu du média / document
                when (attachment) {
                    is PendingAttachment.Image -> {
                        val bmp = remember(attachment) {
                            attachment.bitmap ?: MediaAttachmentHelper.loadBitmap(attachment.base64)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.05f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }

                    is PendingAttachment.MultiImage -> {
                        var activeIdx by remember { mutableIntStateOf(0) }
                        val currentItem = attachment.images.getOrNull(activeIdx) ?: attachment.images.first()
                        val activeBmp = remember(currentItem) {
                            currentItem.bitmap ?: MediaAttachmentHelper.loadBitmap(currentItem.base64)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Large preview of the active photo
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp, max = 200.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (activeBmp != null) {
                                    Image(
                                        bitmap = activeBmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            // Horizontal thumbnail strip (WhatsApp style)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(attachment.images) { idx, itm ->
                                    val thumbBmp = remember(itm) {
                                        itm.bitmap ?: MediaAttachmentHelper.loadBitmap(itm.base64)
                                    }
                                    val isSel = idx == activeIdx
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isSel) 2.5.dp else 1.dp,
                                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { activeIdx = idx },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (thumbBmp != null) {
                                            Image(
                                                bitmap = thumbBmp.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is PendingAttachment.Document -> {
                        val ext = remember(attachment.displayName) {
                            attachment.displayName.substringAfterLast(".", "").lowercase()
                        }
                        val iconBgColor = when (ext) {
                            "pdf" -> Color(0xFFEF4444)
                            "doc", "docx" -> Color(0xFF3B82F6)
                            "xls", "xlsx", "csv" -> Color(0xFF10B981)
                            "txt", "json", "md" -> Color(0xFF8B5CF6)
                            else -> Color(0xFF64748B)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
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

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = attachment.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = attachment.fileSizeFormatted,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is PendingAttachment.Video -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.05f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (attachment.thumbnailBitmap != null) {
                                Image(
                                    bitmap = attachment.thumbnailBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            if (attachment.durationMs > 0L) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = com.sha.orbis.media.VideoMediaHelper.formatDuration(attachment.durationMs),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Champ de saisie pour légende ou note de texte
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    placeholder = {
                        Text(
                            text = when (attachment) {
                                is PendingAttachment.Image, is PendingAttachment.MultiImage -> stringResource(R.string.chat_attach_caption_hint_image)
                                is PendingAttachment.Document -> stringResource(R.string.chat_attach_caption_hint_doc)
                                is PendingAttachment.Video -> stringResource(R.string.chat_attach_caption_hint_video)
                            },
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(captionText.trim()) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (attachment) {
                        is PendingAttachment.MultiImage -> stringResource(R.string.chat_attach_send_photos_btn, attachment.images.size)
                        else -> stringResource(R.string.chat_action_send)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
