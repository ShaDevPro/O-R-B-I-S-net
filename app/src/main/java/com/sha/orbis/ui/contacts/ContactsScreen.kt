package com.sha.orbis.ui.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sha.orbis.R
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.storage.ConversationRepository
import android.os.Build
import android.content.IntentFilter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sha.orbis.data.OrbisBadgeHub
import com.sha.orbis.model.FriendRequest
import com.sha.orbis.nostr.service.NostrSyncManager
import com.sha.orbis.notification.OrbisEventBus
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.security.QrCodeDialog
import com.sha.orbis.ui.components.ChooseInviteTypeDialog
import com.sha.orbis.ui.components.InviteToInstallBottomSheet

/**
 * Unified WhatsApp-style Contacts Screen.
 * Renders the full phonebook directory (all phone contacts + Orbis contacts)
 * with the 3 top quick actions: Nouveau groupe, Nouveau contact (annuaire natif), and Scanner QR Code.
 */
@Composable
fun ContactsScreen(
    currentAccountId: String? = null,
    searchQuery: String = "",
    onContactClick: ((Contact) -> Unit)? = null,
    onOpenWall: ((phone: String, pseudo: String, avatar: String?, role: UserSocialRole) -> Unit)? = null,
    onCreateGroupClick: (() -> Unit)? = null,
    onOpenFriendRequests: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val activeAccountId = currentAccountId ?: sessionManager.activeAccountId
    val repository = remember(context, activeAccountId) { ConversationRepository(context, activeAccountId) }
    val friendRepo = remember(context, activeAccountId) { FriendRequestRepository(context, activeAccountId) }
    val blockedRepo = remember(context, activeAccountId) { com.sha.orbis.storage.BlockedContactsRepository(context, activeAccountId) }
    val nostrSync = remember(context) {
        try { NostrSyncManager.getInstance(context) } catch (_: Exception) { null }
    }

    var pendingRequests by remember(activeAccountId) { mutableStateOf(friendRepo.getPendingReceived()) }
    var blockedVersion by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                pendingRequests = friendRepo.getPendingReceived()
                blockedVersion++
            }
        }
        val filter = IntentFilter(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var deviceContacts by remember { mutableStateOf<List<ContactsPickerHelper.PickedContact>>(emptyList()) }
    var contactForChooseDialog by remember { mutableStateOf<ContactsPickerHelper.PickedContact?>(null) }
    var contactToCancelInvite by remember { mutableStateOf<ContactsPickerHelper.PickedContact?>(null) }
    var inviteContactForInstall by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun refreshContacts() {
        if (hasContactsPermission) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val fetched = ContactsPickerHelper.fetchDeviceContacts(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    deviceContacts = fetched
                }
            }
        }
    }

    LaunchedEffect(activeAccountId) {
        pendingRequests = friendRepo.getPendingReceived()
        refreshContacts()
        OrbisBadgeHub.refresh(context, activeAccountId)
    }

    val contactsPermissionLauncher = com.sha.orbis.permissions.rememberOrbisPermissionLauncher(
        permission = com.sha.orbis.permissions.OrbisPermission.CONTACTS,
        onGranted = {
            hasContactsPermission = true
            refreshContacts()
        }
    )

    // Native contact creation launcher (saves to device phonebook & refreshes list)
    val insertContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshContacts()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        val circleRepo = com.sha.orbis.storage.FriendCircleRepository(context)
        val socialRepo = com.sha.orbis.storage.SocialRepository(context)
        if (isGranted) {
            com.sha.orbis.social.FamilySosManager.dispatchFamilyEmergency(
                context = context,
                sessionManager = sessionManager,
                circleRepo = circleRepo,
                socialRepo = socialRepo,
                onRequestLocationPermission = {},
                onNeedEnableGps = {
                    Toast.makeText(context, context.getString(R.string.family_sos_gps_disabled_prompt), Toast.LENGTH_LONG).show()
                    com.sha.orbis.social.FamilySosManager.openLocationSettings(context)
                },
                onAlertDispatched = {}
            )
        } else {
            Toast.makeText(context, context.getString(R.string.family_sos_gps_error_no_permission), Toast.LENGTH_SHORT).show()
            com.sha.orbis.social.FamilySosManager.dispatchEmergencyWithoutGps(
                context = context,
                sessionManager = sessionManager,
                socialRepo = socialRepo,
                onAlertDispatched = {}
            )
        }
    }

    LaunchedEffect(hasContactsPermission) {
        if (hasContactsPermission) {
            refreshContacts()
        }
    }

    var showQrDialog by remember { mutableStateOf(false) }

    val filteredContacts = remember(deviceContacts, searchQuery, blockedVersion) {
        val query = searchQuery.trim().lowercase()
        val baseList = deviceContacts.filterNot { blockedRepo.isBlocked(it.phoneNumber) }
        if (query.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.name.lowercase().contains(query) || it.phoneNumber.contains(query)
            }
        }
    }

    if (showQrDialog) {
        QrCodeDialog(
            onDismiss = { showQrDialog = false },
            onContactVerified = { verifiedContact ->
                val contacts = repository.loadContacts().toMutableList()
                if (contacts.none { it.id == verifiedContact.id }) {
                    contacts.add(0, verifiedContact)
                    repository.saveContacts(contacts)
                }
                showQrDialog = false
                onContactClick?.invoke(verifiedContact)
            }
        )
    }

    var isRefreshingContacts by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshingContacts,
        onRefresh = {
            isRefreshingContacts = true
            coroutineScope.launch {
                try {
                    nostrSync?.reconnect(force = false)
                } catch (_: Exception) {}
                refreshContacts()
                pendingRequests = friendRepo.getPendingReceived()
                OrbisBadgeHub.refresh(context, activeAccountId)
                isRefreshingContacts = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main list: quick actions + phonebook (search via header magnifier)
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
            // Bannière d'autorisation des contacts si non accordée
            if (!hasContactsPermission) {
                item {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.contacts_perm_banner_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.contacts_perm_banner_desc),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                            androidx.compose.material3.Button(
                                onClick = { contactsPermissionLauncher.launch() },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.contacts_perm_banner_btn),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Facebook-style Pending Friend Requests (Directly visible at the top)
            if (searchQuery.isBlank() && pendingRequests.isNotEmpty()) {
                item {
                    FacebookStylePendingRequestsHeader(
                        pendingRequests = pendingRequests,
                        onAccept = { req ->
                            friendRepo.acceptRequest(req.id)
                            friendRepo.acceptRequestForPhone(req.senderPhone)
                            friendRepo.ensureAcceptedFriend(
                                phone = req.senderPhone,
                                name = req.senderName,
                                publicKey = req.senderPublicKey,
                                avatarPath = req.senderAvatarPath
                            )

                            val contacts = repository.loadContacts().toMutableList()
                            val cleanDigits = req.senderPhone.filter { it.isDigit() }
                            val contactIdx = contacts.indexOfFirst {
                                FriendRequestRepository.isSamePhone(it.phone, req.senderPhone)
                            }
                            if (contactIdx >= 0) {
                                contacts[contactIdx] = contacts[contactIdx].copy(
                                    status = "Connecté 🛡️",
                                    publicKey = req.senderPublicKey,
                                    avatarPath = req.senderAvatarPath
                                )
                            } else {
                                contacts.add(0, Contact(
                                    id = "c_$cleanDigits",
                                    name = req.senderName,
                                    phone = req.senderPhone,
                                    publicKey = req.senderPublicKey,
                                    status = "Connecté 🛡️",
                                    avatarPath = req.senderAvatarPath
                                ))
                            }
                            repository.saveContacts(contacts)

                            // 2b. Automatically create/ensure the chat conversation in Discussions
                            repository.ensureConversationForFriend(
                                phone = req.senderPhone,
                                name = req.senderName,
                                initialMessage = context.getString(R.string.friends_connected_last_msg)
                            )
                            context.sendBroadcast(Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS))

                            // Direct Internet Nostr ACK (No GSM SMS)
                            val myAvatarThumb = AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)
                            try {
                                nostrSync?.publishFriendInvitationAck(
                                    recipientPhone = req.senderPhone,
                                    recipientPubkeyHex = req.senderPublicKey.takeIf { it.isNotBlank() },
                                    avatarBase64 = myAvatarThumb
                                )
                                nostrSync?.refreshSubscriptions()
                            } catch (e: Exception) {
                                android.util.Log.w("ContactsScreen", "Nostr ACK error: ${e.message}")
                            }

                            OrbisBadgeHub.refresh(context, activeAccountId)
                            pendingRequests = friendRepo.getPendingReceived()
                            Toast.makeText(context, context.getString(R.string.friends_request_accepted_toast), Toast.LENGTH_SHORT).show()
                        },
                        onReject = { req ->
                            friendRepo.rejectRequest(req.id)
                            OrbisBadgeHub.refresh(context, activeAccountId)
                            pendingRequests = friendRepo.getPendingReceived()
                        },
                        onSeeAll = { onOpenFriendRequests?.invoke() }
                    )
                }
            }

            // Quick Action Tiles (Top)
            if (searchQuery.isBlank()) {
                // Family Circle Hub Card (Prominent at top of Contacts)
                item {
                    com.sha.orbis.ui.family.FamilyCircleHubCard(
                        onOpenFamilyChat = {
                            val circleRepo = com.sha.orbis.storage.FriendCircleRepository(context)
                            val familyPhones = circleRepo.getFamilyMembers()
                            if (familyPhones.isNotEmpty()) {
                                val conv = com.sha.orbis.model.Conversation(
                                    id = "group_family_circle",
                                    title = "👨‍👩‍👧‍👦 " + context.getString(R.string.family_hub_title),
                                    participants = (listOf(sessionManager.userPhone) + familyPhones).distinct(),
                                    lastMessage = "",
                                    updatedAt = System.currentTimeMillis(),
                                    isGroup = true
                                )
                                val existing = repository.loadConversations().toMutableList()
                                val idx = existing.indexOfFirst { it.id == conv.id }
                                if (idx >= 0) {
                                    existing[idx] = existing[idx].copy(
                                        title = conv.title,
                                        participants = conv.participants,
                                        isGroup = true
                                    )
                                } else {
                                    existing.add(0, conv)
                                }
                                repository.saveConversations(existing)
                                onContactClick?.invoke(Contact(name = conv.title, phone = conv.id))
                            } else {
                                Toast.makeText(context, context.getString(R.string.family_hub_empty_desc), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenDirectChat = { phone, name ->
                            onContactClick?.invoke(Contact(name = name, phone = phone))
                        },
                        onShareFamilyMemory = {
                            Toast.makeText(context, context.getString(R.string.family_hub_action_photo), Toast.LENGTH_SHORT).show()
                        },
                        onSendFamilyEmergency = {
                            val circleRepo = com.sha.orbis.storage.FriendCircleRepository(context)
                            val socialRepo = com.sha.orbis.storage.SocialRepository(context)
                            com.sha.orbis.social.FamilySosManager.dispatchFamilyEmergency(
                                context = context,
                                sessionManager = sessionManager,
                                circleRepo = circleRepo,
                                socialRepo = socialRepo,
                                onRequestLocationPermission = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                onNeedEnableGps = {
                                    Toast.makeText(context, context.getString(R.string.family_sos_gps_disabled_prompt), Toast.LENGTH_LONG).show()
                                    com.sha.orbis.social.FamilySosManager.openLocationSettings(context)
                                },
                                onAlertDispatched = {}
                            )
                        },
                        onFamilyCall = { phone, name, isVideo ->
                            com.sha.orbis.call.OrbisCallManager.startOutgoingCall(
                                context = context,
                                peerPhone = phone,
                                peerName = name,
                                peerAvatar = null,
                                myPhone = sessionManager.userPhone,
                                isVideoCall = isVideo
                            )
                        }
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Compact 4-in-1 Quick Action Row (Instagram/WhatsApp shortcuts)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 0. Demandes d'amis & Suggestions
                            CompactContactActionItem(
                                icon = Icons.Default.People,
                                label = stringResource(R.string.friends_tile_title),
                                badgeCount = pendingRequests.size,
                                onClick = { onOpenFriendRequests?.invoke() },
                                modifier = Modifier.weight(1f)
                            )

                            // 1. Nouveau groupe
                            CompactContactActionItem(
                                icon = Icons.Default.GroupAdd,
                                label = stringResource(R.string.new_chat_action_new_group),
                                onClick = { onCreateGroupClick?.invoke() },
                                modifier = Modifier.weight(1f)
                            )

                            // 2. Rechercher dans les contacts (Fonction Loupe avec icône PersonAdd conservée)
                            CompactContactActionItem(
                                icon = Icons.Default.PersonAdd,
                                label = stringResource(R.string.contacts_action_search),
                                onClick = {
                                    onSearchClick?.invoke()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 3. Scanner QR
                            CompactContactActionItem(
                                icon = Icons.Default.QrCodeScanner,
                                label = stringResource(R.string.new_chat_action_qr_scan),
                                onClick = { showQrDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // Section Header
                        Text(
                            text = if (deviceContacts.isNotEmpty()) {
                                "${stringResource(R.string.new_chat_phonebook_header)} (${deviceContacts.size})"
                            } else {
                                stringResource(R.string.new_chat_phonebook_header)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
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
                            Text(
                                text = "Autoriser l'accès aux contacts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Orbis a besoin de l'accès aux contacts pour vous permettre de démarrer des discussions chiffrées en un clic.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { contactsPermissionLauncher.launch() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Autoriser les contacts")
                            }
                        }
                    }
                }
            }

            // If empty search results
            if (hasContactsPermission && filteredContacts.isEmpty() && deviceContacts.isNotEmpty() && searchQuery.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.contacts_list_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Alphabetical list of Device Contacts
            items(filteredContacts, key = { it.phoneNumber + "_" + it.name }) { item ->
                val dialCode = remember { com.sha.orbis.model.CountryCode.defaultCountry(context).dialCode }
                val normalizedPhone = remember(item.phoneNumber) {
                    ContactsPickerHelper.normalizePhoneNumber(item.phoneNumber, dialCode)
                }
                val isFriend = remember(normalizedPhone, blockedVersion) { friendRepo.isFriend(normalizedPhone) }
                val isPendingSent = remember(normalizedPhone, blockedVersion) { friendRepo.getPendingSentForPhone(normalizedPhone) != null }

                UnifiedContactRow(
                    picked = item,
                    isFriend = isFriend,
                    isPendingSent = isPendingSent,
                    onClick = {
                        if (isFriend) {
                            val cleanDigits = normalizedPhone.filter { it.isDigit() }
                            val contact = Contact(
                                id = "c_$cleanDigits",
                                name = item.name,
                                phone = normalizedPhone,
                                publicKey = "PUB_KEY_VERIFIED",
                                status = "Connecté 🛡️",
                                avatarPath = item.avatarPath
                            )
                            val contacts = repository.loadContacts().toMutableList()
                            val existingIdx = contacts.indexOfFirst { it.phone == normalizedPhone || it.name.equals(item.name, ignoreCase = true) }
                            if (existingIdx >= 0) {
                                if (contacts[existingIdx].avatarPath.isNullOrBlank() && !item.avatarPath.isNullOrBlank()) {
                                    contacts[existingIdx] = contacts[existingIdx].copy(avatarPath = item.avatarPath)
                                    repository.saveContacts(contacts)
                                }
                            } else {
                                contacts.add(0, contact)
                                repository.saveContacts(contacts)
                            }
                            onContactClick?.invoke(contact)
                        } else if (isPendingSent) {
                            contactToCancelInvite = item
                        } else {
                            contactForChooseDialog = item
                        }
                    },
                    onAvatarClick = if (onOpenWall != null) {
                        {
                            val role = com.sha.orbis.admin.AdminSecurityHelper.getUserSocialRole(normalizedPhone, context)
                            onOpenWall(normalizedPhone, item.name, item.avatarPath, role)
                        }
                    } else null
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
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
                        android.util.Log.w("ContactsScreen", "Nostr invite error: ${e.message}")
                    }

                    // 2. Save as SENT
                    friendRepo.addOrUpdate(
                        FriendRequest(
                            id = "sent_$cleanDigits",
                            senderPhone = normalizedPhone,
                            senderName = item.name,
                            senderAvatarPath = item.avatarPath,
                            senderPublicKey = "",
                            direction = com.sha.orbis.model.RequestDirection.SENT,
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

                    OrbisBadgeHub.refresh(context, activeAccountId)
                    blockedVersion++
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

        contactToCancelInvite?.let { target ->
            AlertDialog(
                onDismissRequest = { contactToCancelInvite = null },
                title = {
                    Text(
                        text = stringResource(R.string.contacts_cancel_invite_dialog_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.contacts_cancel_invite_dialog_message, target.name),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val dialCode = com.sha.orbis.model.CountryCode.defaultCountry(context).dialCode
                            val normalizedPhone = ContactsPickerHelper.normalizePhoneNumber(target.phoneNumber, dialCode)
                            friendRepo.cancelSentRequestForPhone(normalizedPhone)

                            val contacts = repository.loadContacts().toMutableList()
                            val existingIdx = contacts.indexOfFirst { it.phone == normalizedPhone || it.name.equals(target.name, ignoreCase = true) }
                            if (existingIdx >= 0) {
                                contacts[existingIdx] = contacts[existingIdx].copy(status = "")
                                repository.saveContacts(contacts)
                            }

                            OrbisBadgeHub.refresh(context, activeAccountId)
                            blockedVersion++
                            Toast.makeText(
                                context,
                                context.getString(R.string.contacts_cancel_invite_success, target.name),
                                Toast.LENGTH_SHORT
                            ).show()
                            contactToCancelInvite = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.contacts_cancel_invite_dialog_confirm),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { contactToCancelInvite = null }
                    ) {
                        Text(
                            text = stringResource(R.string.contacts_cancel_invite_dialog_dismiss),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                shape = RoundedCornerShape(18.dp)
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
}

@Composable
private fun CompactContactActionItem(
    icon: ImageVector,
    label: String,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = MaterialTheme.colorScheme.onError,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FacebookStylePendingRequestsHeader(
    pendingRequests: List<FriendRequest>,
    onAccept: (FriendRequest) -> Unit,
    onReject: (FriendRequest) -> Unit,
    onSeeAll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title + Badge + "Voir tout"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.friends_requests_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = pendingRequests.size.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.friends_see_all),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSeeAll)
                )
            }

            // Pending request items (rendered with Facebook style buttons)
            pendingRequests.take(3).forEach { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OrbisAvatar(
                                avatarPath = req.senderAvatarPath,
                                name = req.senderName,
                                size = 48.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = req.senderName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = req.senderPhone,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Facebook Style Confirm & Delete Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onAccept(req) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.friends_btn_confirm),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { onReject(req) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.friends_btn_delete),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedContactRow(
    picked: ContactsPickerHelper.PickedContact,
    isFriend: Boolean = false,
    isPendingSent: Boolean = false,
    onClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null
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
            name = picked.name,
            avatarPath = picked.avatarPath,
            size = 46.dp,
            onClick = onAvatarClick
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = picked.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = picked.phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            isFriend -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.contacts_status_connected_badge),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            isPendingSent -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.contacts_status_pending_badge),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
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

