package com.sha.orbis.ui.chat

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.sha.orbis.R
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.CountryCode
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.ui.auth.CountryCodePickerDialog
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.security.QrCodeDialog
import com.sha.orbis.ui.components.ChooseInviteTypeDialog
import com.sha.orbis.ui.components.InviteToInstallBottomSheet
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.nostr.service.NostrSyncManager
import com.sha.orbis.model.FriendRequest
import com.sha.orbis.model.RequestDirection
import com.sha.orbis.data.OrbisBadgeHub

/**
 * Standard WhatsApp-style "Nouvelle discussion / Select Contact" screen.
 * Provides full-screen navigation, search bar, quick action rows (Group, Contact, QR),
 * and live alphabetical directory of phonebook and Orbis contacts.
 */
@Composable
fun SelectContactScreen(
    onBack: () -> Unit,
    onStartDirectChat: (Conversation) -> Unit,
    onCreateGroupClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ConversationRepository(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Contact List State
    val deviceContacts = remember { mutableStateListOf<ContactsPickerHelper.PickedContact>() }

    fun refreshContacts() {
        if (hasContactsPermission) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val fetched = ContactsPickerHelper.fetchDeviceContacts(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    deviceContacts.clear()
                    deviceContacts.addAll(fetched)
                }
            }
        }
    }

    val contactsPermissionLauncher = com.sha.orbis.permissions.rememberOrbisPermissionLauncher(
        permission = com.sha.orbis.permissions.OrbisPermission.CONTACTS,
        onGranted = {
            hasContactsPermission = true
            refreshContacts()
        }
    )

    LaunchedEffect(hasContactsPermission) {
        if (hasContactsPermission) {
            refreshContacts()
        }
    }

    // Search Mode State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialogs
    var showDirectNumberDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    val sessionManager = remember { SessionManager(context) }
    val friendRepo = remember(context) { FriendRequestRepository(context) }
    val nostrSync = remember(context) {
        try { NostrSyncManager.getInstance(context) } catch (_: Exception) { null }
    }
    var contactForChooseDialog by remember { mutableStateOf<ContactsPickerHelper.PickedContact?>(null) }
    var inviteContactForInstall by remember { mutableStateOf<Pair<String, String>?>(null) }

    val savedContacts = remember { repository.loadContacts() }

    // Filtered Contacts
    val filteredContacts = remember(deviceContacts, searchQuery, savedContacts) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) {
            deviceContacts
        } else {
            deviceContacts.filter {
                it.name.lowercase().contains(query) || it.phoneNumber.contains(query)
            }
        }
    }

    // Direct Manual Number Dialog
    if (showDirectNumberDialog) {
        DirectNumberInputDialog(
            onDismiss = { showDirectNumberDialog = false },
            onConfirm = { name, fullPhoneNumber, countryIso ->
                val cleanDigits = fullPhoneNumber.filter { it.isDigit() }
                val isFriend = friendRepo.isFriend(fullPhoneNumber)
                val isPending = friendRepo.getPendingSentForPhone(fullPhoneNumber) != null

                if (isFriend) {
                    val conv = Conversation(
                        id = "conv_$cleanDigits",
                        title = name.ifBlank { fullPhoneNumber },
                        participants = listOf("me", fullPhoneNumber),
                        lastMessage = "Conversation démarrée",
                        updatedAt = System.currentTimeMillis(),
                        unreadCount = 0
                    )
                    showDirectNumberDialog = false
                    onStartDirectChat(conv)
                } else if (isPending) {
                    showDirectNumberDialog = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.contacts_status_pending_toast, name.ifBlank { fullPhoneNumber }),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val freshKey = com.sha.orbis.security.AesCipher.generateKeyBase64()
                    val myAvatarThumb = AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)
                    try {
                        nostrSync?.publishFriendInvitation(
                            recipientPhone = fullPhoneNumber,
                            groupKey = freshKey,
                            avatarBase64 = myAvatarThumb
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("SelectContactScreen", "Nostr invite error: ${e.message}")
                    }

                    friendRepo.addOrUpdate(
                        FriendRequest(
                            id = "sent_$cleanDigits",
                            senderPhone = fullPhoneNumber,
                            senderName = name.ifBlank { fullPhoneNumber },
                            senderAvatarPath = null,
                            senderPublicKey = "",
                            direction = RequestDirection.SENT,
                            groupKey = freshKey
                        )
                    )

                    val contacts = repository.loadContacts().toMutableList()
                    val existingIdx = contacts.indexOfFirst { FriendRequestRepository.isSamePhone(it.phone, fullPhoneNumber) }
                    if (existingIdx >= 0) {
                        contacts[existingIdx] = contacts[existingIdx].copy(status = "En attente ⏳")
                    } else {
                        contacts.add(
                            0,
                            Contact(
                                id = "c_$cleanDigits",
                                name = name.ifBlank { fullPhoneNumber },
                                phone = fullPhoneNumber,
                                publicKey = "PUB_KEY_PENDING",
                                status = "En attente ⏳"
                            )
                        )
                    }
                    repository.saveContacts(contacts)

                    OrbisBadgeHub.refresh(context)
                    Toast.makeText(context, context.getString(R.string.new_chat_invite_success), Toast.LENGTH_LONG).show()
                    showDirectNumberDialog = false
                }
            }
        )
    }

    // QR Code Dialog
    if (showQrDialog) {
        QrCodeDialog(
            onDismiss = { showQrDialog = false },
            onContactVerified = { verifiedContact ->
                val contacts = repository.loadContacts().toMutableList()
                if (contacts.none { it.id == verifiedContact.id }) {
                    contacts.add(0, verifiedContact)
                    repository.saveContacts(contacts)
                }

                val conv = Conversation(
                    id = "conv_${verifiedContact.phone.filter { it.isDigit() }}",
                    title = verifiedContact.name,
                    participants = listOf("me", verifiedContact.phone),
                    lastMessage = "Contact certifié par QR Code.",
                    updatedAt = System.currentTimeMillis(),
                    unreadCount = 0
                )
                showQrDialog = false
                onStartDirectChat(conv)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ---------------------------------------------------------------------
        // 1. WhatsApp-Style Top Bar
        // ---------------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            if (!isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.new_chat),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val totalCount = if (hasContactsPermission) deviceContacts.size else savedContacts.size
                            Text(
                                text = stringResource(R.string.new_chat_contacts_count, totalCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            isSearchActive = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Rechercher",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (hasContactsPermission) {
                            IconButton(onClick = { refreshContacts() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualiser",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Active Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        isSearchActive = false
                        searchQuery = ""
                        keyboardController?.hide()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Fermer recherche",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_contact), fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                    )

                    LaunchedEffect(Unit) {
                        searchFocusRequester.requestFocus()
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // 2. Contacts List & Quick Actions
        // ---------------------------------------------------------------------
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Quick Actions (Visible when not actively filtering or when search is empty)
            if (!isSearchActive || searchQuery.isBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // 1. Nouveau groupe
                        WhatsAppActionTile(
                            icon = Icons.Default.GroupAdd,
                            title = stringResource(R.string.new_chat_action_new_group),
                            subtitle = stringResource(R.string.new_chat_action_new_group_sub),
                            onClick = onCreateGroupClick
                        )

                        // 2. Nouveau contact (Saisie directe)
                        WhatsAppActionTile(
                            icon = Icons.Default.PersonAdd,
                            title = stringResource(R.string.new_chat_action_new_contact),
                            subtitle = stringResource(R.string.new_chat_action_new_contact_sub),
                            onClick = { showDirectNumberDialog = true }
                        )

                        // 3. Scanner QR Code
                        WhatsAppActionTile(
                            icon = Icons.Default.QrCodeScanner,
                            title = stringResource(R.string.new_chat_action_qr_scan),
                            subtitle = stringResource(R.string.new_chat_action_qr_scan_sub),
                            onClick = { showQrDialog = true }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // Section Header
                        Text(
                            text = stringResource(R.string.new_chat_phonebook_header),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Permission Request Card if not granted
            if (!hasContactsPermission) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Contacts,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Text(
                                text = stringResource(R.string.new_chat_perm_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = stringResource(R.string.new_chat_perm_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            Button(
                                onClick = { contactsPermissionLauncher.launch() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.new_chat_perm_btn),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // If empty search results
            if (hasContactsPermission && filteredContacts.isEmpty() && searchQuery.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.new_chat_empty_search, searchQuery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Contacts List Items
            items(filteredContacts, key = { it.phoneNumber }) { contact ->
                val dialCode = remember { com.sha.orbis.model.CountryCode.defaultCountry(context).dialCode }
                val normalizedPhone = remember(contact.phoneNumber) {
                    ContactsPickerHelper.normalizePhoneNumber(contact.phoneNumber, dialCode)
                }
                val isFriend = remember(normalizedPhone) { friendRepo.isFriend(normalizedPhone) }
                val isPendingSent = remember(normalizedPhone) { friendRepo.getPendingSentForPhone(normalizedPhone) != null }

                WhatsAppContactRow(
                    name = contact.name,
                    phoneNumber = contact.phoneNumber,
                    avatarPath = contact.avatarPath,
                    isFriend = isFriend,
                    isPendingSent = isPendingSent,
                    onClick = {
                        if (isFriend) {
                            val cleanDigits = normalizedPhone.filter { it.isDigit() }
                            val conv = Conversation(
                                id = "conv_$cleanDigits",
                                title = contact.name,
                                participants = listOf("me", normalizedPhone),
                                lastMessage = "Conversation démarrée",
                                updatedAt = System.currentTimeMillis(),
                                unreadCount = 0
                            )
                            onStartDirectChat(conv)
                        } else if (isPendingSent) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.contacts_status_pending_toast, contact.name),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            contactForChooseDialog = contact
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
            }
        }

        contactForChooseDialog?.let { item ->
            ChooseInviteTypeDialog(
                targetName = item.name,
                onChooseHasApp = {
                    val dialCode = com.sha.orbis.model.CountryCode.defaultCountry(context).dialCode
                    val normalizedPhone = ContactsPickerHelper.normalizePhoneNumber(item.phoneNumber, dialCode)
                    val cleanDigits = normalizedPhone.filter { it.isDigit() }
                    val freshKey = com.sha.orbis.security.AesCipher.generateKeyBase64()
                    val myAvatarThumb = AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)

                    // 1. Silent 1-click Nostr Sovereign Invitation
                    try {
                        nostrSync?.publishFriendInvitation(
                            recipientPhone = normalizedPhone,
                            groupKey = freshKey,
                            avatarBase64 = myAvatarThumb
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("SelectContactScreen", "Nostr invite error: ${e.message}")
                    }

                    // 2. Save as SENT
                    friendRepo.addOrUpdate(
                        FriendRequest(
                            id = "sent_$cleanDigits",
                            senderPhone = normalizedPhone,
                            senderName = item.name,
                            senderAvatarPath = item.avatarPath,
                            senderPublicKey = "",
                            direction = RequestDirection.SENT,
                            groupKey = freshKey
                        )
                    )

                    // 3. Update local contact
                    val contacts = repository.loadContacts().toMutableList()
                    val existingIdx = contacts.indexOfFirst { it.phone == normalizedPhone || it.name.equals(item.name, ignoreCase = true) }
                    if (existingIdx >= 0) {
                        contacts[existingIdx] = contacts[existingIdx].copy(status = "En attente ⏳")
                    } else {
                        contacts.add(0, Contact(
                            id = "c_$cleanDigits",
                            name = item.name,
                            phone = normalizedPhone,
                            publicKey = "PUB_KEY_PENDING",
                            status = "En attente ⏳",
                            avatarPath = item.avatarPath
                        ))
                    }
                    repository.saveContacts(contacts)

                    OrbisBadgeHub.refresh(context)
                    Toast.makeText(context, context.getString(R.string.invite_sent_success), Toast.LENGTH_SHORT).show()
                    contactForChooseDialog = null
                },
                onChooseNoApp = {
                    val dialCode = com.sha.orbis.model.CountryCode.defaultCountry(context).dialCode
                    val normalizedPhone = ContactsPickerHelper.normalizePhoneNumber(item.phoneNumber, dialCode)
                    inviteContactForInstall = Pair(item.name, normalizedPhone)
                    contactForChooseDialog = null
                },
                onDismiss = { contactForChooseDialog = null }
            )
        }

        inviteContactForInstall?.let { contact ->
            InviteToInstallBottomSheet(
                targetName = contact.first.takeIf { it.isNotBlank() },
                targetPhone = contact.second.takeIf { it.isNotBlank() },
                onDismiss = { inviteContactForInstall = null }
            )
        }
    }
}

/**
 * Clean WhatsApp Action Row (Circle Icon + Title + Subtitle)
 */
@Composable
private fun WhatsAppActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Standard WhatsApp Contact Row with Avatar & Status
 */
@Composable
private fun WhatsAppContactRow(
    name: String,
    phoneNumber: String,
    avatarPath: String?,
    isFriend: Boolean = false,
    isPendingSent: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OrbisAvatar(
            name = name,
            avatarPath = avatarPath,
            size = 46.dp
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            isFriend -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Connecté 🛡️",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            isPendingSent -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.contacts_status_pending_badge),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.invite_contact),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Sleek Popup for Direct Phone Number Input
 */
@Composable
private fun DirectNumberInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, fullPhoneNumber: String, countryIso: String) -> Unit
) {
    val context = LocalContext.current
    var contactName by remember { mutableStateOf("") }
    var nationalPhone by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(CountryCode.defaultCountry(context)) }
    var showCountryPicker by remember { mutableStateOf(false) }

    if (showCountryPicker) {
        CountryCodePickerDialog(
            selectedCountry = selectedCountry,
            onCountrySelected = { selectedCountry = it },
            onDismissRequest = { showCountryPicker = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.new_chat_direct_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    text = stringResource(R.string.new_chat_direct_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text(stringResource(R.string.new_chat_contact_name_label)) },
                    placeholder = { Text(stringResource(R.string.new_chat_contact_name_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { showCountryPicker = true }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(selectedCountry.flagEmoji, fontSize = 16.sp)
                            Text(selectedCountry.dialCode, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    OutlinedTextField(
                        value = nationalPhone,
                        onValueChange = { nationalPhone = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.new_chat_phone_label)) },
                        placeholder = { Text("550123456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val cleanDigits = nationalPhone.filter { it.isDigit() }.trimStart('0')
                        if (cleanDigits.length < 7) {
                            Toast.makeText(context, context.getString(R.string.auth_error_phone), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val fullPhone = "${selectedCountry.dialCode}$cleanDigits"
                        val name = contactName.trim().ifBlank { fullPhone }
                        onConfirm(name, fullPhone, selectedCountry.isoCode)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = nationalPhone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.new_chat_btn_start_chat),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
