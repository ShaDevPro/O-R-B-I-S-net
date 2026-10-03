package com.sha.orbis.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sha.orbis.R
import com.sha.orbis.storage.VaultCleanerEngine
import com.sha.orbis.ui.theme.OrbisColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interface moderne et modulaire de gestion du stockage et du cache.
 * Inspirée des standards de Telegram et WhatsApp.
 */
@Composable
fun StorageManagementDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var analysis by remember { mutableStateOf<VaultCleanerEngine.StorageAnalysis?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun refreshStats() {
        scope.launch {
            isLoading = true
            val res = withContext(Dispatchers.IO) {
                VaultCleanerEngine.analyzeStorage(context)
            }
            analysis = res
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.storage_management_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.storage_management_subtitle),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                // Description card
                Text(
                    text = stringResource(R.string.storage_management_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )

                if (isLoading || analysis == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = stringResource(R.string.storage_calculating),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val a = analysis!!
                    val totalBytes = a.totalVaultBytes.coerceAtLeast(1L)
                    val mediaRatio = (a.mediaImagesBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    val dbRatio = ((a.databaseBytes + a.voiceNotesBytes).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    val cacheRatio = (a.temporaryCacheBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

                    // Visual Multi-segment Storage Bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.storage_total_used),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = VaultCleanerEngine.formatBytes(a.totalVaultBytes),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (mediaRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(mediaRatio.coerceAtLeast(0.02f))
                                        .height(10.dp)
                                        .background(Color(0xFF38BDF8))
                                )
                            }
                            if (dbRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(dbRatio.coerceAtLeast(0.02f))
                                        .height(10.dp)
                                        .background(OrbisColorPalette.StatusActive)
                                )
                            }
                            if (cacheRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(cacheRatio.coerceAtLeast(0.02f))
                                        .height(10.dp)
                                        .background(Color(0xFFF59E0B))
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Categories breakdown
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 1. Photos & Media Row
                        StorageCategoryItem(
                            icon = Icons.Default.Image,
                            iconTint = Color(0xFF38BDF8),
                            title = stringResource(R.string.storage_category_media),
                            subtitle = stringResource(R.string.storage_category_media_desc),
                            sizeFormatted = VaultCleanerEngine.formatBytes(a.mediaImagesBytes),
                            actionLabel = if (a.mediaImagesBytes > 0) stringResource(R.string.storage_btn_clear_media) else null,
                            onAction = {
                                scope.launch {
                                    val freed = withContext(Dispatchers.IO) {
                                        VaultCleanerEngine.purgeMediaPhotos(context)
                                    }
                                    val freedFmt = VaultCleanerEngine.formatBytes(freed)
                                    Toast.makeText(context, context.getString(R.string.storage_toast_freed, freedFmt), Toast.LENGTH_SHORT).show()
                                    refreshStats()
                                }
                            }
                        )

                        // 2. Messages & Database Row
                        StorageCategoryItem(
                            icon = Icons.Default.Message,
                            iconTint = OrbisColorPalette.StatusActive,
                            title = stringResource(R.string.storage_category_messages),
                            subtitle = stringResource(R.string.storage_category_messages_desc),
                            sizeFormatted = VaultCleanerEngine.formatBytes(a.databaseBytes + a.voiceNotesBytes),
                            actionLabel = null,
                            onAction = {}
                        )

                        // 3. Temporary Caches Row
                        StorageCategoryItem(
                            icon = Icons.Default.FolderZip,
                            iconTint = Color(0xFFF59E0B),
                            title = stringResource(R.string.storage_category_cache),
                            subtitle = stringResource(R.string.storage_category_cache_desc),
                            sizeFormatted = VaultCleanerEngine.formatBytes(a.temporaryCacheBytes),
                            actionLabel = if (a.temporaryCacheBytes > 0) stringResource(R.string.storage_btn_clear_cache) else null,
                            onAction = {
                                scope.launch {
                                    val freed = withContext(Dispatchers.IO) {
                                        VaultCleanerEngine.purgeTemporaryCaches(context)
                                    }
                                    val freedFmt = VaultCleanerEngine.formatBytes(freed)
                                    Toast.makeText(context, context.getString(R.string.storage_toast_freed, freedFmt), Toast.LENGTH_SHORT).show()
                                    refreshStats()
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Full Optimization Button
                    Button(
                        onClick = {
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    VaultCleanerEngine.purgeAll(context)
                                }
                                if (res.bytesReclaimed > 0) {
                                    val freedFmt = VaultCleanerEngine.formatBytes(res.bytesReclaimed)
                                    Toast.makeText(context, context.getString(R.string.storage_toast_freed, freedFmt), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.storage_toast_already_clean), Toast.LENGTH_SHORT).show()
                                }
                                refreshStats()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.storage_btn_optimize_all),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCategoryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    sizeFormatted: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = sizeFormatted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (actionLabel != null) {
                IconButton(onClick = onAction, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = actionLabel,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
