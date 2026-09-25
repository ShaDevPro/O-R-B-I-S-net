package com.sha.orbis.ui.backup

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sha.orbis.R
import com.sha.orbis.storage.VaultBackupEngine
import java.io.File

private val ColorTelegram = Color(0xFF2AABEE)
private val ColorWhatsApp = Color(0xFF25D366)

/**
 * Dialog shown immediately after creating an encrypted .orbis vault.
 * Explains that Gmail/email services block archive files and guides the user
 * to securely store it on Telegram (Saved Messages) or WhatsApp (self-chat).
 */
@Composable
fun VaultShareTargetDialog(
    file: File,
    onDismiss: () -> Unit,
    onShareCompleted: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.vault_share_dialog_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                // File name & size pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "📦 ${file.name} (${file.length() / 1024} Ko)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Warning / Educational Note about Gmail vs Telegram/WhatsApp
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "💡 " + stringResource(R.string.vault_share_dialog_desc),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.vault_choose_destination_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: Telegram
                ShareTargetCard(
                    icon = Icons.Default.Send,
                    iconColor = ColorTelegram,
                    title = stringResource(R.string.vault_share_opt_telegram_title),
                    subtitle = stringResource(R.string.vault_share_opt_telegram_sub),
                    onClick = {
                        VaultBackupEngine.shareVaultFile(context, file, VaultBackupEngine.TARGET_TELEGRAM)
                        onShareCompleted?.invoke(VaultBackupEngine.TARGET_TELEGRAM)
                        onDismiss()
                    }
                )

                // Option 2: WhatsApp
                ShareTargetCard(
                    icon = Icons.Default.Chat,
                    iconColor = ColorWhatsApp,
                    title = stringResource(R.string.vault_share_opt_whatsapp_title),
                    subtitle = stringResource(R.string.vault_share_opt_whatsapp_sub),
                    onClick = {
                        VaultBackupEngine.shareVaultFile(context, file, VaultBackupEngine.TARGET_WHATSAPP)
                        onShareCompleted?.invoke(VaultBackupEngine.TARGET_WHATSAPP)
                        onDismiss()
                    }
                )

                // Option 3: Other / System share
                ShareTargetCard(
                    icon = Icons.Default.Share,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.vault_share_opt_other_title),
                    subtitle = stringResource(R.string.vault_share_opt_other_sub),
                    onClick = {
                        VaultBackupEngine.shareVaultFile(context, file, VaultBackupEngine.TARGET_OTHER)
                        onShareCompleted?.invoke(VaultBackupEngine.TARGET_OTHER)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTargetCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Dialog guiding the user during restore. Suggests Telegram (Saved Messages) or
 * WhatsApp (self-chat), or local storage, and highlights the previously used backup target.
 * Automatically performs 1-Click Transparent decryption if zero-password, or prompts
 * for custom password if the archive is password-protected.
 */
@Composable
fun VaultRestoreSourceDialog(
    onDismiss: () -> Unit,
    onRestoreSuccess: (VaultBackupEngine.BackupStats) -> Unit,
    onFileSelected: ((Uri) -> Unit)? = null
) {
    val context = LocalContext.current
    val lastTarget = remember { VaultBackupEngine.getLastBackupTargetApp(context) }
    var pendingPassphraseUri by remember { mutableStateOf<Uri?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onFileSelected?.invoke(uri)
            val isProtected = VaultBackupEngine.isVaultPasswordProtected(context, uri)
            when (isProtected) {
                false -> {
                    // 1-Click Transparent mode (Zero password)
                    val stats = VaultBackupEngine.restoreVaultFromUri(context, uri, "")
                    if (stats != null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.vault_restore_stats_summary, stats.contactsCount, stats.conversationsCount, stats.messagesCount),
                            Toast.LENGTH_LONG
                        ).show()
                        onRestoreSuccess(stats)
                        onDismiss()
                    } else {
                        Toast.makeText(context, context.getString(R.string.vault_restore_passphrase_error), Toast.LENGTH_LONG).show()
                    }
                }
                true -> {
                    // Custom passphrase required
                    pendingPassphraseUri = uri
                }
                null -> {
                    Toast.makeText(context, context.getString(R.string.vault_restore_invalid_format), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (pendingPassphraseUri != null) {
        VaultPassphraseInputDialog(
            uri = pendingPassphraseUri!!,
            onDismiss = { pendingPassphraseUri = null },
            onSuccess = { stats ->
                pendingPassphraseUri = null
                onRestoreSuccess(stats)
                onDismiss()
            }
        )
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.vault_restore_dialog_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.vault_restore_dialog_subtitle),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Source 1: Telegram
                RestoreSourceCard(
                    icon = Icons.Default.Send,
                    iconColor = ColorTelegram,
                    title = stringResource(R.string.vault_restore_opt_telegram_title),
                    description = stringResource(R.string.vault_restore_opt_telegram_desc),
                    isRecommended = lastTarget == VaultBackupEngine.TARGET_TELEGRAM,
                    openAppLabel = stringResource(R.string.vault_restore_btn_open_telegram),
                    onOpenApp = {
                        val opened = VaultBackupEngine.openExternalApp(context, VaultBackupEngine.TARGET_TELEGRAM)
                        if (!opened) {
                            Toast.makeText(context, context.getString(R.string.vault_telegram_not_installed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    pickFileLabel = stringResource(R.string.vault_restore_btn_pick_file),
                    onPickFile = { filePicker.launch(arrayOf("*/*")) }
                )

                // Source 2: WhatsApp
                RestoreSourceCard(
                    icon = Icons.Default.Chat,
                    iconColor = ColorWhatsApp,
                    title = stringResource(R.string.vault_restore_opt_whatsapp_title),
                    description = stringResource(R.string.vault_restore_opt_whatsapp_desc),
                    isRecommended = lastTarget == VaultBackupEngine.TARGET_WHATSAPP,
                    openAppLabel = stringResource(R.string.vault_restore_btn_open_whatsapp),
                    onOpenApp = {
                        val opened = VaultBackupEngine.openExternalApp(context, VaultBackupEngine.TARGET_WHATSAPP)
                        if (!opened) {
                            Toast.makeText(context, context.getString(R.string.vault_whatsapp_not_installed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    pickFileLabel = stringResource(R.string.vault_restore_btn_pick_file),
                    onPickFile = { filePicker.launch(arrayOf("*/*")) }
                )

                // Source 3: Local Files / Documents
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Text(
                                text = stringResource(R.string.vault_restore_opt_local_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.vault_restore_opt_local_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.vault_restore_btn_browse), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}
}

@Composable
private fun RestoreSourceCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    isRecommended: Boolean,
    openAppLabel: String,
    onOpenApp: () -> Unit,
    pickFileLabel: String,
    onPickFile: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(
            1.dp,
            if (isRecommended) iconColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isRecommended) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(iconColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = iconColor, modifier = Modifier.size(11.dp))
                            Text(
                                text = stringResource(R.string.vault_restore_last_used_badge),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = iconColor
                            )
                        }
                    }
                }
            }

            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenApp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(openAppLabel, fontSize = 11.sp)
                }

                Button(
                    onClick = onPickFile,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(pickFileLabel, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Clean Passphrase Decryption Dialog shown once a .orbis file has been selected.
 */
@Composable
fun VaultPassphraseInputDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onSuccess: (VaultBackupEngine.BackupStats) -> Unit
) {
    val context = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var isPassphraseVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.vault_restore_passphrase_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; errorMessage = null },
                    placeholder = { Text(stringResource(R.string.vault_passphrase_hint)) },
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                            Icon(
                                imageVector = if (isPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = "❌ $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isRestoring) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = {
                            if (passphrase.isBlank()) {
                                errorMessage = context.getString(R.string.vault_restore_passphrase_empty_error)
                                return@Button
                            }
                            isRestoring = true
                            val stats = try {
                                VaultBackupEngine.restoreVaultFromUri(context, uri, passphrase.trim())
                            } catch (e: Exception) {
                                null
                            }
                            isRestoring = false
                            if (stats != null) {
                                onSuccess(stats)
                                onDismiss()
                            } else {
                                errorMessage = context.getString(R.string.vault_restore_passphrase_error)
                            }
                        },
                        enabled = !isRestoring && passphrase.isNotBlank()
                    ) {
                        Text(stringResource(R.string.vault_import_title))
                    }
                }
            }
        }
    }
}
