package com.sha.orbis.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.sha.orbis.ui.backup.VaultRestoreSourceDialog
import com.sha.orbis.ui.backup.VaultShareTargetDialog
import java.io.File
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
import androidx.compose.ui.graphics.StrokeCap
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
import com.sha.orbis.backup.P2pMigrationHelper
import com.sha.orbis.storage.VaultBackupEngine
import com.sha.orbis.storage.VaultCleanerEngine
import com.sha.orbis.ui.theme.OrbisColorPalette

/**
 * Sovereign Vault Backup, P2P Migration & Storage Cleaner Modal.
 */
@Composable
fun BackupMigrationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.vault_hub_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.vault_hub_subtitle),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Tabs Navigation (4 Tabs)
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.vault_tab_backup), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.vault_tab_restore), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.vault_tab_p2p), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text(stringResource(R.string.vault_tab_cleaner), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Tab Content Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> BackupTabContent(context)
                        1 -> RestoreTabContent(context)
                        2 -> P2pMigrationTabContent(context)
                        3 -> CleanerTabContent(context)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupTabContent(context: Context) {
    var passphrase by remember { mutableStateOf("") }
    var isPassVisible by remember { mutableStateOf(false) }
    var showAdvancedPassword by remember { mutableStateOf(false) }
    var shareDialogFile by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1-Click Banner
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.vault_mode_one_click_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.vault_mode_one_click_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }

        // Primary 1-Click Backup Button
        Button(
            onClick = {
                val file = VaultBackupEngine.exportVault(context, passphrase.trim())
                if (file != null) {
                    shareDialogFile = file
                } else {
                    Toast.makeText(context, "Erreur lors de la sauvegarde.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.vault_btn_one_click_save), fontWeight = FontWeight.Bold)
        }

        // Advanced Options Toggle
        TextButton(
            onClick = { showAdvancedPassword = !showAdvancedPassword },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.vault_custom_password_toggle),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (showAdvancedPassword) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showAdvancedPassword) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.vault_custom_password_desc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 15.sp
                )

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.vault_passphrase_label)) },
                    placeholder = { Text(stringResource(R.string.vault_passphrase_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassVisible = !isPassVisible }) {
                            Icon(
                                imageVector = if (isPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }

    if (shareDialogFile != null) {
        VaultShareTargetDialog(
            file = shareDialogFile!!,
            onDismiss = { shareDialogFile = null }
        )
    }
}

@Composable
private fun RestoreTabContent(context: Context) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var isPassVisible by remember { mutableStateOf(false) }
    var isFileProtected by remember { mutableStateOf<Boolean?>(null) }
    var restoreStats by remember { mutableStateOf<VaultBackupEngine.BackupStats?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var showRestoreSourceDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            restoreError = null
            val isProtected = VaultBackupEngine.isVaultPasswordProtected(context, uri)
            isFileProtected = isProtected
            if (isProtected == false) {
                val stats = VaultBackupEngine.restoreVaultFromUri(context, uri, "")
                if (stats != null) {
                    restoreStats = stats
                    Toast.makeText(context, context.getString(R.string.vault_restore_stats_summary, stats.contactsCount, stats.conversationsCount, stats.messagesCount), Toast.LENGTH_LONG).show()
                } else {
                    restoreError = context.getString(R.string.vault_restore_passphrase_error)
                }
            } else if (isProtected == null) {
                restoreError = context.getString(R.string.vault_restore_invalid_format)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "📥 ${stringResource(R.string.vault_restore_desc_title)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.vault_restore_desc_body),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }

        // Guided WhatsApp / Telegram Restore Button
        Button(
            onClick = { showRestoreSourceDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.vault_btn_recovery_guide), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        // File Selection Button
        OutlinedButton(
            onClick = { filePicker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedUri != null) stringResource(R.string.vault_file_selected_ok) else stringResource(R.string.vault_btn_select_file))
        }

        if (isFileProtected == true) {
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it; restoreError = null },
                label = { Text(stringResource(R.string.vault_passphrase_label)) },
                placeholder = { Text(stringResource(R.string.vault_passphrase_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPassVisible = !isPassVisible }) {
                        Icon(
                            imageVector = if (isPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )

            Button(
                onClick = {
                    val uri = selectedUri
                    if (uri == null) {
                        Toast.makeText(context, context.getString(R.string.vault_select_file_first), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val stats = VaultBackupEngine.restoreVaultFromUri(context, uri, passphrase.trim())
                    if (stats != null) {
                        restoreStats = stats
                        restoreError = null
                        Toast.makeText(context, context.getString(R.string.vault_restore_success), Toast.LENGTH_LONG).show()
                    } else {
                        restoreError = context.getString(R.string.vault_restore_passphrase_error)
                    }
                },
                enabled = selectedUri != null && passphrase.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.vault_btn_execute_restore))
            }
        }

        if (restoreError != null) {
            Text(
                text = "❌ $restoreError",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (restoreStats != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OrbisColorPalette.StatusActive.copy(alpha = 0.12f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, OrbisColorPalette.StatusActive.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "✅ " + stringResource(R.string.vault_restore_success_badge), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OrbisColorPalette.StatusActive)
                    Text(text = "• " + stringResource(R.string.vault_stat_contacts_count, restoreStats!!.contactsCount), fontSize = 11.sp)
                    Text(text = "• " + stringResource(R.string.vault_stat_conversations_count, restoreStats!!.conversationsCount), fontSize = 11.sp)
                    Text(text = "• " + stringResource(R.string.vault_stat_messages_count, restoreStats!!.messagesCount), fontSize = 11.sp)
                    Text(text = "• " + stringResource(R.string.vault_stat_posts_count, restoreStats!!.postsCount), fontSize = 11.sp)
                    if (restoreStats!!.nostrIdentityRestored) {
                        Text(text = "• " + stringResource(R.string.vault_stat_nostr_restored), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showRestoreSourceDialog) {
        VaultRestoreSourceDialog(
            onDismiss = { showRestoreSourceDialog = false },
            onRestoreSuccess = { stats ->
                restoreStats = stats
                restoreError = null
                showRestoreSourceDialog = false
            }
        )
    }
}

@Composable
private fun P2pMigrationTabContent(context: Context) {
    var pairingKey by remember { mutableStateOf("") }
    var exportedPayload by remember { mutableStateOf<String?>(null) }
    var importPayloadInput by remember { mutableStateOf("") }
    var migrationResult by remember { mutableStateOf<P2pMigrationHelper.MigrationResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "📲 ${stringResource(R.string.vault_p2p_desc_title)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.vault_p2p_desc_body),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }

        OutlinedTextField(
            value = pairingKey,
            onValueChange = { pairingKey = it },
            label = { Text(stringResource(R.string.vault_p2p_pairing_key_label)) },
            placeholder = { Text(stringResource(R.string.vault_p2p_pairing_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (pairingKey.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.vault_p2p_enter_code_error), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val payload = P2pMigrationHelper.exportAccountMigrationPayload(context, pairingKey)
                    exportedPayload = payload
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.vault_p2p_btn_generate))
            }
        }

        if (exportedPayload != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = stringResource(R.string.vault_p2p_payload_generated_title), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        text = exportedPayload!!.take(120) + "...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, exportedPayload)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, context.getString(R.string.vault_p2p_share_chooser_title)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.vault_p2p_btn_share_payload), fontSize = 11.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Text(text = stringResource(R.string.vault_p2p_import_section_title), fontWeight = FontWeight.Bold, fontSize = 13.sp)

        OutlinedTextField(
            value = importPayloadInput,
            onValueChange = { importPayloadInput = it },
            label = { Text(stringResource(R.string.vault_p2p_paste_label)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Button(
            onClick = {
                val res = P2pMigrationHelper.importAccountMigrationPayload(context, importPayloadInput.trim(), pairingKey.trim())
                migrationResult = res
                if (res.success) {
                    Toast.makeText(context, context.getString(R.string.vault_p2p_restore_success_toast, res.userName ?: "", res.userPhone ?: ""), Toast.LENGTH_LONG).show()
                }
            },
            enabled = importPayloadInput.isNotBlank() && pairingKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.vault_p2p_btn_apply_import))
        }

        if (migrationResult != null) {
            if (migrationResult!!.success) {
                Text(
                    text = "✅ " + stringResource(R.string.vault_p2p_account_imported_badge, migrationResult!!.userName ?: "", migrationResult!!.userPhone ?: ""),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbisColorPalette.StatusActive
                )
            } else {
                Text(
                    text = "❌ ${migrationResult!!.errorMessage}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CleanerTabContent(context: Context) {
    var analysis by remember { mutableStateOf<VaultCleanerEngine.StorageAnalysis?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    fun refreshAnalysis() {
        analysis = VaultCleanerEngine.analyzeStorage(context)
    }

    LaunchedEffect(Unit) {
        refreshAnalysis()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🧹 ${stringResource(R.string.vault_cleaner_desc_title)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.vault_cleaner_desc_body),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }

        if (analysis != null) {
            val totalMb = "%.2f".format(analysis!!.totalVaultBytes / (1024f * 1024f))
            val reclaimableMb = "%.2f".format(analysis!!.reclaimableBytes / (1024f * 1024f))
            val voiceMb = "%.2f".format(analysis!!.voiceNotesBytes / (1024f * 1024f))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.vault_cleaner_total_space), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "$totalMb Mo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "• " + stringResource(R.string.vault_cleaner_expired_stories_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = stringResource(R.string.vault_cleaner_purgeable_count, analysis!!.expiredStoriesCount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "• " + stringResource(R.string.vault_cleaner_temp_fragments_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = stringResource(R.string.vault_cleaner_fragments_count, analysis!!.orphanedChunksCount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "• " + stringResource(R.string.vault_cleaner_voice_notes_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${analysis!!.voiceNotesCount} ($voiceMb Mo)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.vault_cleaner_reclaimable_space_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "$reclaimableMb Mo", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = OrbisColorPalette.StatusActive)
                    }
                }
            }
        }

        Button(
            onClick = {
                val res = VaultCleanerEngine.purgeAll(context)
                val freedKb = res.bytesReclaimed / 1024
                Toast.makeText(context, context.getString(R.string.vault_cleaner_done_toast, res.itemsDeletedCount, freedKb), Toast.LENGTH_LONG).show()
                refreshAnalysis()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.vault_cleaner_btn_purge))
        }
    }
}
