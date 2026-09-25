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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.media.LinkContentType
import com.sha.orbis.media.LinkPlatform
import com.sha.orbis.media.LinkPreviewHelper
import com.sha.orbis.media.LinkPreviewMetadata
import com.sha.orbis.media.MediaAttachmentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Carte de prévisualisation riche d'un lien externe (TikTok, Instagram, Facebook, YouTube, Web)
 * affichant la miniature du post et permettant le clic direct pour ouvrir dans l'application émettrice.
 */
@Composable
fun SocialLinkPreviewCard(
    url: String,
    modifier: Modifier = Modifier,
    isMsgMine: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var preview by remember(url) { mutableStateOf<LinkPreviewMetadata?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var thumbnailBitmap by remember(url) { mutableStateOf<Bitmap?>(null) }

    val platform = remember(url) { LinkPlatform.fromUrl(url) }

    LaunchedEffect(url) {
        isLoading = true
        val meta = LinkPreviewHelper.getPreview(context, url)
        preview = meta
        isLoading = false

        if (meta?.localThumbnailPath != null) {
            val bmp = withContext(Dispatchers.IO) {
                MediaAttachmentHelper.loadBitmap(meta.localThumbnailPath)
            }
            thumbnailBitmap = bmp
        }
    }

    val handleOpen = {
        onClick?.invoke() ?: LinkPreviewHelper.openInSourceApp(context, url)
    }

    val cardBg = if (isMsgMine) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val brandColor = Color(platform.brandColorHex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { handleOpen() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. En-tête Badge de la plateforme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val platformIcon: ImageVector = when (platform) {
                        LinkPlatform.TIKTOK -> Icons.Default.MusicNote
                        LinkPlatform.INSTAGRAM -> Icons.Default.PhotoCamera
                        LinkPlatform.FACEBOOK -> Icons.Default.Public
                        LinkPlatform.YOUTUBE -> Icons.Default.Videocam
                        else -> Icons.Default.Public
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(brandColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = platformIcon,
                            contentDescription = null,
                            tint = brandColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    val typeLabel = when (platform.contentType) {
                        LinkContentType.VIDEO -> stringResource(R.string.link_preview_video)
                        LinkContentType.REEL_OR_POST -> stringResource(R.string.link_preview_reel)
                        LinkContentType.POST -> stringResource(R.string.link_preview_post)
                        LinkContentType.GENERIC -> "Lien"
                    }

                    Text(
                        text = "${platform.displayName} • $typeLabel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = brandColor
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = brandColor
                    )
                }
            }

            // 2. Miniature (Thumbnail) de la publication / vidéo
            if (thumbnailBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 200.dp)
                        .background(Color.Black.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = preview?.title ?: "Miniature",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 200.dp)
                    )

                    // Overlay de lecture vidéo si applicable
                    if (platform.contentType == LinkContentType.VIDEO || platform.contentType == LinkContentType.REEL_OR_POST) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Lire",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // 3. Titre, Description et Métadonnées
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val titleText = preview?.title?.ifBlank { null }
                    ?: if (isLoading) stringResource(R.string.link_preview_loading) else "${platform.displayName} Post"

                Text(
                    text = titleText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )

                if (!preview?.description.isNullOrBlank()) {
                    Text(
                        text = preview!!.description!!,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(2.dp))

                // 4. Barre d'action avec Clic vers l'Application Émettrice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val host = try {
                        java.net.URI(url).host?.removePrefix("www.") ?: platform.displayName.lowercase()
                    } catch (_: Exception) {
                        platform.displayName.lowercase()
                    }

                    Text(
                        text = host,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Bouton Pilule d'ouverture directe
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(brandColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (platform != LinkPlatform.GENERIC) {
                                stringResource(R.string.link_preview_open_in_app, platform.displayName)
                            } else {
                                stringResource(R.string.link_preview_open_link)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandColor
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = brandColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
