package com.sha.orbis.ui.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import com.sha.orbis.ui.settings.ai.OrbisAiSettingsScreen
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VisibilityOff
import com.sha.orbis.security.BatteryOptimizationHelper
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.ui.legal.PrivacyTermsDialog
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sha.orbis.R
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.AccountProfile
import com.sha.orbis.model.HiddenPost
import com.sha.orbis.model.PrivateConversationLock
import com.sha.orbis.nostr.client.RelayPoolManager
import com.sha.orbis.security.BiometricAuthManager
import com.sha.orbis.security.DuressSecurityManager
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.HiddenPostsRepository
import com.sha.orbis.storage.PrivateConversationRepository
import com.sha.orbis.storage.VaultBackupEngine
import com.sha.orbis.ui.admin.AdminConsoleScreen
import com.sha.orbis.ui.conversation.PrivateChatPasswordSetupDialog
import com.sha.orbis.ui.conversation.PrivateChatRecoverDialog
import com.sha.orbis.ui.conversation.PrivateChatRecoveredPasswordDialog
import com.sha.orbis.ui.conversation.PrivateChatUnlockDialog
import com.sha.orbis.ui.feedback.SendFeedbackDialog
import com.sha.orbis.ui.security.QrCodeDialog
import com.sha.orbis.ui.security.SecurityScreen
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.components.OrbisBrandingFooter
import com.sha.orbis.ui.components.OrbisTopHeader
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.ui.theme.OrbisColorPalette
import androidx.compose.runtime.collectAsState
import java.io.File


@Composable
fun SettingsScreen(
    onOpenAccountSwitcher: (() -> Unit)? = null,
    onOpenAdminConsole: (() -> Unit)? = null,
    onOpenProfile: (() -> Unit)? = null,
    onOpenWall: ((phone: String, pseudo: String, avatar: String?, role: UserSocialRole) -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    var isBiometricActive by remember { mutableStateOf(sessionManager.isBiometricEnabled) }
    var isPinActive by remember { mutableStateOf(DuressSecurityManager.isPinEnabled(context)) }
    var showSecurityHub by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showPinConfigDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAdminConsole by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showHelpCenter by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }
    var showOemDiagnostic by remember { mutableStateOf(false) }
    var showBackupMigrationDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showShareAppDialog by remember { mutableStateOf(false) }
    var showPrivacyTermsDialog by remember { mutableStateOf(false) }
    var isPresenceHidden by remember { mutableStateOf(sessionManager.isPresenceHidden) }
    var showBlockedContactsDialog by remember { mutableStateOf(false) }
    val blockedRepo = remember(context) { com.sha.orbis.storage.BlockedContactsRepository(context) }
    var blockedList by remember { mutableStateOf(blockedRepo.loadBlocked()) }
    val hiddenPostsRepo = remember(context) { HiddenPostsRepository(context) }
    var hiddenPostsList by remember { mutableStateOf(hiddenPostsRepo.loadHiddenPosts()) }
    var showHiddenPostsDialog by remember { mutableStateOf(false) }
    val privateChatRepo = remember(context) { PrivateConversationRepository(context) }
    var privateLocks by remember { mutableStateOf(privateChatRepo.loadLocks()) }
    var showPrivateChatsDialog by remember { mutableStateOf(false) }
    var privateLockToChange by remember { mutableStateOf<PrivateConversationLock?>(null) }
    var privateLockToRecover by remember { mutableStateOf<PrivateConversationLock?>(null) }
    var privateLockToRemove by remember { mutableStateOf<PrivateConversationLock?>(null) }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var removeLockError by remember { mutableStateOf<String?>(null) }
    var recoveredPassword by remember { mutableStateOf<String?>(null) }
    var isBatteryExempted by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }
    val isAdmin = remember(sessionManager.userPhone) { AdminSecurityHelper.isAdmin(sessionManager.userPhone) }
    var userAvatarPath by remember { mutableStateOf(sessionManager.userAvatarPath) }

    fun restoreHiddenPost(item: HiddenPost) {
        if (hiddenPostsRepo.unhidePost(item.postId)) {
            hiddenPostsList = hiddenPostsRepo.loadHiddenPosts()
            Toast.makeText(
                context,
                context.getString(R.string.settings_hidden_post_restored),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = AvatarManager.saveAvatarFromUri(
                context = context,
                imageUri = uri,
                identifier = "avatar_${sessionManager.activeAccountId}_${System.currentTimeMillis()}"
            )
            if (savedPath != null) {
                userAvatarPath = savedPath
                sessionManager.updateActiveAccountAvatar(savedPath)
                Toast.makeText(context, context.getString(R.string.contact_photo_assigned), Toast.LENGTH_SHORT).show()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).publishProfileUpdate(avatarPath = savedPath)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassphrase by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf("") }

    val settingsRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreDialog = true
        }
    }

    if (showRestoreDialog && pendingRestoreUri != null) {
        Dialog(onDismissRequest = { showRestoreDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.vault_restore_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        placeholder = { Text(stringResource(R.string.vault_passphrase_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (restoreError.isNotBlank()) {
                        Text(text = restoreError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showRestoreDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Button(
                            onClick = {
                                if (restorePassphrase.isBlank()) {
                                    restoreError = context.getString(R.string.vault_restore_passphrase_empty_error)
                                    return@Button
                                }
                                val stats = VaultBackupEngine.restoreVaultFromUri(context, pendingRestoreUri!!, restorePassphrase.trim())
                                if (stats != null) {
                                    showRestoreDialog = false
                                    Toast.makeText(context, context.getString(R.string.vault_restore_success), Toast.LENGTH_LONG).show()
                                } else {
                                    restoreError = context.getString(R.string.vault_restore_wrong_password)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.vault_import_title))
                        }
                    }
                }
            }
        }
    }

    if (showPinConfigDialog) {
        PinConfigDialog(
            isPinAlreadyEnabled = isPinActive,
            onPinStateChanged = { isPinActive = DuressSecurityManager.isPinEnabled(context) },
            onDismiss = { showPinConfigDialog = false }
        )
    }

    if (showQrDialog) {
        QrCodeDialog(
            onDismiss = { showQrDialog = false },
            onContactVerified = { verifiedContact ->
                ConversationRepository(context).insertOrUpdateContact(verifiedContact)
                Toast.makeText(context, context.getString(R.string.contact_certified_toast, verifiedContact.name), Toast.LENGTH_SHORT).show()
                showQrDialog = false
            }
        )
    }

    if (showDiagnosticsDialog) {
        SystemDiagnosticsDialog(
            onDismiss = { showDiagnosticsDialog = false }
        )
    }

    if (showBackupMigrationDialog) {
        BackupMigrationDialog(
            onDismiss = { showBackupMigrationDialog = false }
        )
    }

    if (showStorageDialog) {
        StorageManagementDialog(
            onDismiss = { showStorageDialog = false }
        )
    }

    if (showAdminConsole) {
        AdminConsoleScreen(onBack = { showAdminConsole = false })
        return
    }

    if (showFeedbackDialog) {
        SendFeedbackDialog(onDismiss = { showFeedbackDialog = false })
    }

    if (showPrivacyTermsDialog) {
        PrivacyTermsDialog(onDismiss = { showPrivacyTermsDialog = false })
    }

    if (showShareAppDialog) {
        ShareAppDialog(onDismiss = { showShareAppDialog = false })
    }

    if (showSecurityHub) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            OrbisTopHeader(
                title = stringResource(R.string.security_title),
                subtitle = stringResource(R.string.settings_vault_schnorr_subtitle),
                onBack = { showSecurityHub = false }
            )
            SecurityScreen()
        }
        return
    }

    if (showHelpCenter) {
        com.sha.orbis.ui.help.HelpCenterScreen(
            onBack = { showHelpCenter = false }
        )
        return
    }

    if (showAiSettings) {
        OrbisAiSettingsScreen(
            onBack = { showAiSettings = false }
        )
        return
    }

    if (showOemDiagnostic) {
        OrbisCallDiagnosticScreen(
            onBack = { showOemDiagnostic = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 0: Admin Developer Console (Exclusive to Admin Account)
        if (isAdmin) {
            SettingsGroupCard(title = "Administration & Télémétrie") {
                SettingsClickableRow(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.admin_console_title),
                    subtitle = stringResource(R.string.admin_console_subtitle),
                    onClick = {
                        if (onOpenAdminConsole != null) {
                            onOpenAdminConsole.invoke()
                        } else {
                            showAdminConsole = true
                        }
                    },
                    badge = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(OrbisColorPalette.StatusActive.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.admin_badge),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbisColorPalette.StatusActive,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                )
            }
        }

        // Section 1: Identity & SIM Card
        SettingsGroupCard(title = stringResource(R.string.settings_section_profile)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenProfile?.invoke() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    OrbisAvatar(
                        avatarPath = userAvatarPath,
                        name = sessionManager.userName,
                        size = 52.dp,
                        onClick = { onOpenProfile?.invoke() ?: photoPickerLauncher.launch("image/*") }
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onOpenProfile?.invoke() ?: photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sessionManager.userName.ifBlank { "Utilisateur" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = sessionManager.userPhone.ifBlank { "+213000000000" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (sessionManager.userSimSlotIndex >= 0) "SIM ${sessionManager.userSimSlotIndex + 1}" else "Sandbox",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // Direct Access to My P2P Wall & Posts (UX Simplifiée)
            SettingsClickableRow(
                icon = Icons.Default.Public,
                title = stringResource(R.string.settings_open_my_wall),
                subtitle = stringResource(R.string.settings_open_my_wall_sub),
                onClick = {
                    val myRole = AdminSecurityHelper.getUserSocialRole(sessionManager.userPhone, context)
                    onOpenWall?.invoke(sessionManager.userPhone, sessionManager.userName.ifBlank { "Moi" }, userAvatarPath, myRole)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // Switch / Add Dual-SIM account
            SettingsClickableRow(
                icon = Icons.Default.SimCard,
                title = stringResource(R.string.settings_dual_sim_row_title),
                subtitle = stringResource(R.string.settings_dual_sim_row_sub, sessionManager.getAccounts().size),
                onClick = { onOpenAccountSwitcher?.invoke() }
            )
        }

        // Section 2: Security & Encryption Vault
        SettingsGroupCard(title = stringResource(R.string.settings_section_security)) {
            SettingsClickableRow(
                icon = Icons.Default.QrCode,
                title = stringResource(R.string.settings_qr_title),
                subtitle = stringResource(R.string.settings_qr_sub),
                onClick = { showQrDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_vault_row_title),
                subtitle = stringResource(R.string.settings_vault_row_sub),
                onClick = { showSecurityHub = true }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.security_pin_title),
                subtitle = if (isPinActive) stringResource(R.string.settings_pin_active_desc) else stringResource(R.string.settings_pin_inactive_desc),
                onClick = { showPinConfigDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsToggleRow(
                icon = Icons.Default.Fingerprint,
                title = stringResource(R.string.biometric_setting_title),
                subtitle = stringResource(R.string.biometric_setting_desc),
                checked = isBiometricActive,
                onCheckedChange = {
                    if (it && !BiometricAuthManager.isBiometricAvailable(context)) {
                        Toast.makeText(context, context.getString(R.string.biometric_not_available), Toast.LENGTH_SHORT).show()
                    } else {
                        isBiometricActive = it
                        sessionManager.isBiometricEnabled = it
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsToggleRow(
                icon = Icons.Default.VisibilityOff,
                title = stringResource(R.string.presence_hide_title),
                subtitle = stringResource(R.string.presence_hide_desc),
                checked = isPresenceHidden,
                onCheckedChange = { isOffline ->
                    isPresenceHidden = isOffline
                    sessionManager.isPresenceHidden = isOffline

                    // 1. Émettre immédiatement le signal éphémère de présence ou déconnexion sur Nostr
                    try {
                        com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).broadcastPresence(isOnline = !isOffline)
                    } catch (_: Exception) {}

                    // 2. Diffuser un broadcast pour actualiser instantanément toute l'interface locale
                    val intent = Intent(com.sha.orbis.notification.OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)

                    // 3. Toast de confirmation visuelle
                    val toastMsg = if (isOffline) {
                        context.getString(R.string.presence_toast_offline_active)
                    } else {
                        context.getString(R.string.presence_toast_online_active)
                    }
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.Block,
                title = stringResource(R.string.settings_blocked_contacts_title),
                subtitle = if (blockedList.isNotEmpty()) {
                    stringResource(R.string.settings_blocked_contacts_count, blockedList.size)
                } else {
                    stringResource(R.string.settings_blocked_contacts_empty_sub)
                },
                badge = if (blockedList.isNotEmpty()) {
                    {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${blockedList.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else null,
                onClick = {
                    blockedList = blockedRepo.loadBlocked()
                    showBlockedContactsDialog = true
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.VisibilityOff,
                title = stringResource(R.string.settings_hidden_posts_title),
                subtitle = if (hiddenPostsList.isNotEmpty()) {
                    stringResource(R.string.settings_hidden_posts_count, hiddenPostsList.size)
                } else {
                    stringResource(R.string.settings_hidden_posts_empty)
                },
                badge = if (hiddenPostsList.isNotEmpty()) {
                    {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${hiddenPostsList.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else null,
                onClick = {
                    hiddenPostsList = hiddenPostsRepo.loadHiddenPosts()
                    showHiddenPostsDialog = true
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.private_chat_settings_title),
                subtitle = if (privateLocks.isNotEmpty()) {
                    stringResource(R.string.private_chat_settings_count, privateLocks.size)
                } else {
                    stringResource(R.string.private_chat_settings_empty)
                },
                onClick = {
                    privateLocks = privateChatRepo.loadLocks()
                    showPrivateChatsDialog = true
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.oem_diag_settings_row_title),
                subtitle = stringResource(R.string.oem_diag_settings_row_sub),
                onClick = { showOemDiagnostic = true }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.BatteryChargingFull,
                title = stringResource(R.string.battery_opt_title),
                subtitle = stringResource(R.string.battery_opt_desc),
                onClick = {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    isBatteryExempted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                },
                badge = {
                    val isExempt = isBatteryExempted
                    val badgeText = if (isExempt) stringResource(R.string.battery_opt_active) else stringResource(R.string.battery_opt_inactive)
                    val badgeBg = if (isExempt) OrbisColorPalette.StatusActive.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    val badgeColor = if (isExempt) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.settings_permissions_title),
                subtitle = stringResource(R.string.settings_permissions_subtitle),
                onClick = {
                    com.sha.orbis.permissions.OrbisPermissionManager.openAppSettings(context)
                }
            )

        }

        // Section: Intelligence Artificielle ORBIS (LLM) & Benchmark
        val aiPrefs = remember { com.sha.orbis.ai.core.OrbisAiPreferences(context) }
        val activeAiCount = aiPrefs.getActiveEnginesCount()
        val aiSubtitle = when (activeAiCount) {
            0 -> stringResource(R.string.settings_ai_status_disabled)
            1 -> stringResource(R.string.settings_ai_status_partial)
            else -> stringResource(R.string.settings_ai_status_all_active)
        }

        SettingsGroupCard(title = stringResource(R.string.settings_section_ai)) {
            SettingsClickableRow(
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.settings_ai_title),
                subtitle = aiSubtitle,
                onClick = { showAiSettings = true },
                badge = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (activeAiCount > 0) OrbisColorPalette.StatusActive.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (activeAiCount > 0) "$activeAiCount/2 Actif ⚡" else "Désactivé 💤",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeAiCount > 0) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            )
        }

        // Section 3: Nostr Network — Relay status (replaces SMS Quota section)
        val relayPool = remember { RelayPoolManager.getInstance(context) }
        val poolHealth by relayPool.poolHealth.collectAsState()
        val connectedRelays = poolHealth.connectedCount
        val totalRelays = poolHealth.totalRelays
        val avgPingMs = poolHealth.averagePingMs

        SettingsGroupCard(title = stringResource(R.string.settings_section_nostr_network)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Connected count row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.nostr_relay_connected_count, connectedRelays, totalRelays),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (avgPingMs > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.nostr_relay_avg_ping, avgPingMs.toInt()),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Relay health progress bar
                val ratio = if (totalRelays > 0) (connectedRelays.toFloat() / totalRelays.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    ratio >= 0.6f -> OrbisColorPalette.StatusActive
                                    ratio > 0f -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                    )
                }

                // Status label + Reconnect button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            ratio >= 0.6f -> stringResource(R.string.nostr_relay_status_good)
                            ratio > 0f -> stringResource(R.string.nostr_relay_status_degraded)
                            else -> stringResource(R.string.nostr_relay_status_offline)
                        },
                        fontSize = 11.sp,
                        color = when {
                            ratio >= 0.6f -> OrbisColorPalette.StatusActive
                            ratio > 0f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = stringResource(R.string.nostr_relay_reconnect),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).reconnect()
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Background sync info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_nostr_bg_sync_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_nostr_fg_service_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OrbisColorPalette.StatusActive.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_nostr_fg_service_status),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbisColorPalette.StatusActive
                        )
                    }
                }
            }
        }


        // Section 4: Sovereign Vault, P2P Migration & Storage Cleaner
        SettingsGroupCard(title = stringResource(R.string.vault_hub_title)) {
            SettingsClickableRow(
                icon = Icons.Default.Security,
                title = stringResource(R.string.vault_hub_row_title),
                subtitle = stringResource(R.string.vault_hub_row_subtitle),
                onClick = { showBackupMigrationDialog = true },
                badge = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "PBKDF2 🛡️",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            )
        }

        // Section: Help & User Guide (Guides hors-ligne & FAQ)
        SettingsGroupCard(title = stringResource(R.string.help_center_title)) {
            SettingsClickableRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = stringResource(R.string.settings_help_row_title),
                subtitle = stringResource(R.string.settings_help_row_sub),
                onClick = { showHelpCenter = true }
            )
        }

        // Section: App Sharing & Community (QR Code & Téléchargement Direct)
        SettingsGroupCard(title = stringResource(R.string.settings_section_share)) {
            SettingsClickableRow(
                icon = Icons.Default.QrCode,
                title = stringResource(R.string.settings_share_app_title),
                subtitle = stringResource(R.string.settings_share_app_subtitle),
                onClick = { showShareAppDialog = true },
                badge = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_share_app_badge),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            )
        }

        // Section 5: Language & Appearance
        SettingsGroupCard(title = stringResource(R.string.settings_lang_title)) {
            LanguageSelector()
        }

        // Section 6: Storage & Maintenance
        SettingsGroupCard(title = stringResource(R.string.settings_section_storage)) {
            SettingsClickableRow(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.storage_management_title),
                subtitle = stringResource(R.string.storage_management_subtitle),
                onClick = {
                    showStorageDialog = true
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.CleaningServices,
                title = stringResource(R.string.settings_clear_cache),
                subtitle = stringResource(R.string.settings_clear_cache_sub),
                onClick = {
                    val freed = clearAppCache(context)
                    val formatted = formatCacheSize(freed)
                    Toast.makeText(context, "Cache vidé : $formatted libérés ✅", Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_diagnostics),
                subtitle = stringResource(R.string.settings_diagnostics_sub),
                onClick = {
                    showDiagnosticsDialog = true
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.BugReport,
                title = stringResource(R.string.feedback_row_title),
                subtitle = stringResource(R.string.feedback_row_subtitle),
                onClick = { showFeedbackDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsClickableRow(
                icon = Icons.Default.Policy,
                title = stringResource(R.string.privacy_terms_row_title),
                subtitle = stringResource(R.string.privacy_terms_row_sub),
                onClick = { showPrivacyTermsDialog = true }
            )
        }

        // Logout Button Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLogoutDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.profile_logout),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.profile_logout_confirm_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.profile_logout_confirm_msg)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            onLogout?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.profile_logout))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showBlockedContactsDialog) {
            AlertDialog(
                onDismissRequest = { showBlockedContactsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_blocked_dialog_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                text = {
                    if (blockedList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🛡️",
                                fontSize = 32.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_blocked_dialog_empty),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (blockedList.isNotEmpty()) {
                                item("blocked_header") {
                                    Text(
                                        text = stringResource(R.string.settings_blocked_contacts_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            items(blockedList, key = { it.phone + "_" + (it.publicKey ?: "") }) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = item.phone.ifBlank { item.publicKey?.take(16) ?: "" },
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                blockedRepo.unblockContact(item.phone)
                                                if (!item.publicKey.isNullOrBlank()) {
                                                    blockedRepo.unblockContact(item.publicKey)
                                                }
                                                blockedList = blockedRepo.loadBlocked()
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.settings_blocked_toast_unblocked, item.name),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_blocked_unblock_btn),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBlockedContactsDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showHiddenPostsDialog) {
            AlertDialog(
                onDismissRequest = { showHiddenPostsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_hidden_posts_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                text = {
                    if (hiddenPostsList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_hidden_posts_empty),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(hiddenPostsList, key = { it.postId }) { item ->
                                HiddenPostRestoreCard(
                                    item = item,
                                    onRestore = { restoreHiddenPost(item) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHiddenPostsDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showPrivateChatsDialog) {
            AlertDialog(
                onDismissRequest = { showPrivateChatsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.private_chat_settings_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                text = {
                    if (privateLocks.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.private_chat_settings_empty),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(privateLocks, key = { it.conversationId }) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = item.conversationTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            TextButton(
                                                onClick = { privateLockToChange = item },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(stringResource(R.string.private_chat_change_pass_action), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            TextButton(
                                                onClick = {
                                                    recoveryError = null
                                                    privateLockToRecover = item
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(stringResource(R.string.private_chat_forgot_action), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            TextButton(
                                                onClick = {
                                                    removeLockError = null
                                                    privateLockToRemove = item
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    stringResource(R.string.private_chat_remove_lock_action),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPrivateChatsDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (privateLockToChange != null) {
            val lock = privateLockToChange!!
            PrivateChatPasswordSetupDialog(
                conversationTitle = lock.conversationTitle,
                isChange = true,
                onDismiss = { privateLockToChange = null },
                onConfirm = { password, answers ->
                    privateChatRepo.changePassword(lock.conversationId, password, answers)
                    privateLocks = privateChatRepo.loadLocks()
                    privateLockToChange = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.private_chat_toast_pass_changed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        if (privateLockToRecover != null) {
            val lock = privateLockToRecover!!
            PrivateChatRecoverDialog(
                conversationTitle = lock.conversationTitle,
                errorMessage = recoveryError,
                onDismiss = {
                    privateLockToRecover = null
                    recoveryError = null
                },
                onRecover = { answers ->
                    val recovered = privateChatRepo.recoverPassword(lock.conversationId, answers)
                    if (recovered != null) {
                        recoveredPassword = recovered
                        privateLockToRecover = null
                        recoveryError = null
                    } else {
                        recoveryError = context.getString(R.string.private_chat_error_wrong_answers)
                    }
                }
            )
        }

        if (privateLockToRemove != null) {
            val lock = privateLockToRemove!!
            PrivateChatUnlockDialog(
                conversationTitle = lock.conversationTitle,
                errorMessage = removeLockError,
                onDismiss = {
                    privateLockToRemove = null
                    removeLockError = null
                },
                onUnlock = { password ->
                    if (privateChatRepo.verifyPassword(lock.conversationId, password)) {
                        privateChatRepo.unlockConversation(lock.conversationId)
                        privateLocks = privateChatRepo.loadLocks()
                        privateLockToRemove = null
                        removeLockError = null
                        Toast.makeText(
                            context,
                            context.getString(R.string.private_chat_toast_unlocked, lock.conversationTitle),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        removeLockError = context.getString(R.string.private_chat_error_wrong_pass)
                    }
                }
            )
        }

        if (recoveredPassword != null) {
            PrivateChatRecoveredPasswordDialog(
                password = recoveredPassword!!,
                onDismiss = { recoveredPassword = null }
            )
        }

        // Premium Branding Footer
        OrbisBrandingFooter()
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (badge != null) {
            badge()
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun PinConfigDialog(
    isPinAlreadyEnabled: Boolean,
    onPinStateChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mainPin by remember { mutableStateOf("") }
    var duressPin by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.security_pin_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.security_pin_duress_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Main Secret PIN
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.security_pin_main),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = mainPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) mainPin = it },
                        placeholder = { Text("ex: 1234") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Duress Decoy PIN
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.security_pin_duress),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = duressPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) duressPin = it },
                        placeholder = { Text("ex: 9999 (Leurre)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPinAlreadyEnabled) {
                        TextButton(
                            onClick = {
                                DuressSecurityManager.disablePin(context)
                                onPinStateChanged()
                                Toast.makeText(context, "Code PIN désactivé.", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        ) {
                            Text("Désactiver", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                DuressSecurityManager.setSecurityPins(context, mainPin, duressPin.ifBlank { null })
                                onPinStateChanged()
                                Toast.makeText(context, context.getString(R.string.security_pin_saved), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            enabled = mainPin.length >= 4
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenPostRestoreCard(
    item: HiddenPost,
    onRestore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.authorName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.contentPreview.ifBlank { stringResource(R.string.settings_hidden_post_empty_preview) },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onRestore,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_hidden_post_restore_btn),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun clearAppCache(context: Context): Long {
    var deletedBytes = 0L
    fun deleteRecursively(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        var total = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                total += deleteRecursively(child)
            }
        }
        val size = file.length()
        if (file.delete()) {
            total += size
        }
        return total
    }

    deletedBytes += deleteRecursively(context.cacheDir)
    context.externalCacheDir?.let {
        deletedBytes += deleteRecursively(it)
    }
    val tempAudioDir = File(context.filesDir, "audio_cache")
    if (tempAudioDir.exists()) {
        deletedBytes += deleteRecursively(tempAudioDir)
    }
    val tempDir = File(context.filesDir, "temp")
    if (tempDir.exists()) {
        deletedBytes += deleteRecursively(tempDir)
    }

    return deletedBytes
}

private fun formatCacheSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f Mo", bytes.toFloat() / (1024 * 1024))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f Ko", bytes.toFloat() / 1024)
        else -> "$bytes octets"
    }
}
