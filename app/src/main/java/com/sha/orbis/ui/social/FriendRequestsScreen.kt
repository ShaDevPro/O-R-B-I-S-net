package com.sha.orbis.ui.social

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.sha.orbis.notification.OrbisEventBus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import com.sha.orbis.ui.components.ChooseInviteTypeDialog
import com.sha.orbis.ui.components.InviteToInstallBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import com.sha.orbis.data.OrbisBadgeHub
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.BlockedContact
import com.sha.orbis.model.Contact
import com.sha.orbis.model.FriendRequest
import com.sha.orbis.nostr.service.NostrSyncManager
import com.sha.orbis.social.FriendSuggestion
import com.sha.orbis.social.FriendSuggestionsEngine
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.theme.OrbisColorPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modern Facebook/Instagram Style Friend Requests & Discovery Screen.
 * Dual-delivery: Nostr sovereign mesh + GSM SMS fallback transport.
 */
@Composable
fun FriendRequestsScreen(
    currentAccountId: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val activeAccountId = currentAccountId ?: sessionManager.activeAccountId
    val friendRequestRepo = remember(context, activeAccountId) { FriendRequestRepository(context, activeAccountId) }
    val blockedRepo = remember(context, activeAccountId) { BlockedContactsRepository(context, activeAccountId) }
    val suggestionsEngine = remember(context, activeAccountId) { FriendSuggestionsEngine(context, activeAccountId) }
    val convRepo = remember(context, activeAccountId) { ConversationRepository(context, activeAccountId) }
    val nostrSync = remember(context) {
        try { NostrSyncManager.getInstance(context) } catch (_: Exception) { null }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var receivedRequests by remember(activeAccountId) { mutableStateOf(friendRequestRepo.getPendingReceived()) }
    var sentRequests by remember(activeAccountId) { mutableStateOf(friendRequestRepo.getPendingSent()) }
    var suggestions by remember(activeAccountId) { mutableStateOf(suggestionsEngine.generateSuggestions()) }
    var blockedList by remember(activeAccountId) { mutableStateOf(blockedRepo.loadBlocked()) }

    var inviteContactForInstall by remember { mutableStateOf<Pair<String, String>?>(null) }
    var contactForChooseDialog by remember { mutableStateOf<FriendSuggestion?>(null) }

    fun refreshAll() {
        receivedRequests = friendRequestRepo.getPendingReceived()
        sentRequests = friendRequestRepo.getPendingSent()
        suggestions = suggestionsEngine.generateSuggestions()
        blockedList = blockedRepo.loadBlocked()
        OrbisBadgeHub.refresh(context, activeAccountId)
    }

    LaunchedEffect(activeAccountId) {
        refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Facebook/Instagram Style Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.friends_requests_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "OrbisNet Sovereign Network",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Modern Facebook-style Category Filter Pills
        val pills = listOf(
            stringResource(R.string.friends_tab_pills_requests) to receivedRequests.size,
            stringResource(R.string.friends_tab_pills_suggestions) to suggestions.size,
            stringResource(R.string.friends_tab_pills_sent) to sentRequests.size,
            stringResource(R.string.friends_tab_pills_blocked) to blockedList.size
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pills.size) { index ->
                val (title, count) = pills[index]
                val isSelected = selectedTabIndex == index
                val pillBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val pillTextColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(pillBg)
                        .clickable { selectedTabIndex = index }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            color = pillTextColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        if (count > 0 && index != 1) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = count.toString(),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var isRefreshing by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        // Content Body
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                coroutineScope.launch {
                    try {
                        NostrSyncManager.getInstance(context).reconnect(force = false)
                    } catch (_: Exception) {}
                    refreshAll()
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTabIndex) {
                // Tab 0: Received Requests (Facebook Style)
                0 -> {
                    if (receivedRequests.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            EmptyStateView(
                                icon = Icons.Default.PersonAdd,
                                message = stringResource(R.string.friends_empty_received)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(receivedRequests, key = { it.id }) { req ->
                                FacebookStyleReceivedCard(
                                    request = req,
                                    onAccept = {
                                        // 1. Accept locally in repository
                                        friendRequestRepo.acceptRequest(req.id)
                                        friendRequestRepo.acceptRequestForPhone(req.senderPhone)
                                        friendRequestRepo.ensureAcceptedFriend(
                                            phone = req.senderPhone,
                                            name = req.senderName,
                                            publicKey = req.senderPublicKey,
                                            avatarPath = req.senderAvatarPath
                                        )

                                        // 2. Add or update Contact in conversation repository
                                        val contacts = convRepo.loadContacts().toMutableList()
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
                                        convRepo.saveContacts(contacts)

                                        // 2b. Automatically create/ensure the chat conversation in Discussions
                                        convRepo.ensureConversationForFriend(
                                            phone = req.senderPhone,
                                            name = req.senderName,
                                            initialMessage = context.getString(R.string.friends_connected_last_msg)
                                        )
                                        context.sendBroadcast(Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS))

                                        // 3. Publish Nostr Handshake ACK (Sovereign relay delivery via Internet)
                                        val myAvatarThumb = AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)
                                        try {
                                            nostrSync?.publishFriendInvitationAck(
                                                recipientPhone = req.senderPhone,
                                                recipientPubkeyHex = req.senderPublicKey.takeIf { it.isNotBlank() },
                                                avatarBase64 = myAvatarThumb
                                            )
                                            nostrSync?.refreshSubscriptions()
                                            com.sha.orbis.sync.scheduler.SovereignSyncScheduler.onFriendAddedOrAccepted(
                                                context = context,
                                                peerPhone = req.senderPhone,
                                                peerPubkey = req.senderPublicKey
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.w("FriendRequests", "Nostr ACK error: ${e.message}")
                                        }

                                        Toast.makeText(context, context.getString(R.string.friends_request_accepted_toast), Toast.LENGTH_SHORT).show()
                                        refreshAll()
                                    },
                                    onReject = {
                                        friendRequestRepo.rejectRequest(req.id)
                                        refreshAll()
                                    },
                                    onBlock = {
                                        friendRequestRepo.rejectRequest(req.id)
                                        blockedRepo.blockContact(req.senderPhone, req.senderName)
                                        Toast.makeText(context, context.getString(R.string.friends_contact_blocked_toast), Toast.LENGTH_SHORT).show()
                                        refreshAll()
                                    }
                                )
                            }
                        }
                    }
                }

                // Tab 1: Suggestions (Instagram "Discover People" Style)
                1 -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // Hero Invite Banner (Facebook / Instagram Style)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.friends_hero_invite_title),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.friends_hero_invite_desc),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { inviteContactForInstall = Pair("", "") },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Text(stringResource(R.string.friends_hero_invite_btn), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (suggestions.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = Icons.Default.PersonAdd,
                                    message = stringResource(R.string.friends_empty_suggestions)
                                )
                            }
                        } else {
                            items(suggestions, key = { it.phone }) { item ->
                                val alreadySent = sentRequests.any { FriendRequestRepository.isSamePhone(it.senderPhone, item.phone) }

                                InstagramStyleSuggestionCard(
                                    suggestion = item,
                                    isAlreadySent = alreadySent,
                                    onInvite = {
                                        contactForChooseDialog = item
                                    }
                                )
                            }
                        }
                    }
                }

                // Tab 2: Sent Requests
                2 -> {
                    if (sentRequests.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            EmptyStateView(
                                icon = Icons.AutoMirrored.Filled.Send,
                                message = stringResource(R.string.friends_empty_sent)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(sentRequests, key = { it.id }) { req ->
                                ModernSentRequestCard(
                                    request = req,
                                    onCancel = {
                                        friendRequestRepo.cancelSentRequestForPhone(req.senderPhone)
                                        refreshAll()
                                    }
                                )
                            }
                        }
                    }
                }

                // Tab 3: Blocked Contacts
                3 -> {
                    if (blockedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            EmptyStateView(
                                icon = Icons.Default.Shield,
                                message = stringResource(R.string.friends_empty_blocked)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(blockedList, key = { it.phone }) { blocked ->
                                BlockedCard(
                                    blocked = blocked,
                                    onUnblock = {
                                        blockedRepo.unblockContact(blocked.phone)
                                        Toast.makeText(context, context.getString(R.string.friends_contact_unblocked_toast), Toast.LENGTH_SHORT).show()
                                        refreshAll()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        contactForChooseDialog?.let { item ->
            ChooseInviteTypeDialog(
                targetName = item.name,
                onChooseHasApp = {
                    val cleanDigits = item.phone.filter { it.isDigit() }
                    val identity = sessionManager.getOrCreateIdentity()
                    val groupKey = com.sha.orbis.security.AesCipher.generateKeyBase64()
                    val myAvatarThumb = AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)

                    // 1. Publish Nostr Sovereign Invitation Event (Direct Internet Mesh)
                    try {
                        nostrSync?.publishFriendInvitation(
                            recipientPhone = item.phone,
                            groupKey = groupKey,
                            avatarBase64 = myAvatarThumb
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("FriendRequests", "Nostr Invite publish error: ${e.message}")
                    }

                    // 2. Save to local repository as SENT
                    friendRequestRepo.addOrUpdate(
                        FriendRequest(
                            id = "sent_$cleanDigits",
                            senderPhone = item.phone,
                            senderName = item.name,
                            senderAvatarPath = item.avatarPath,
                            senderPublicKey = "",
                            direction = com.sha.orbis.model.RequestDirection.SENT,
                            mutualFriendsCount = item.mutualCount,
                            groupKey = groupKey
                        )
                    )

                    Toast.makeText(context, context.getString(R.string.invite_sent_success), Toast.LENGTH_SHORT).show()
                    contactForChooseDialog = null
                    refreshAll()
                },
                onChooseNoApp = {
                    inviteContactForInstall = Pair(item.name, item.phone)
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
 * Facebook-style Friend Request Card with large avatar, mutual friends context,
 * and tactile "Confirmer" / "Supprimer" action buttons.
 */
@Composable
private fun FacebookStyleReceivedCard(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onBlock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large Modern Avatar
                OrbisAvatar(
                    avatarPath = request.senderAvatarPath,
                    name = request.senderName,
                    size = 56.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = request.senderName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatRelativeTime(request.timestamp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = request.senderPhone,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (request.mutualFriendsCount > 0) {
                        Text(
                            text = stringResource(R.string.friends_badge_mutual_count, request.mutualFriendsCount),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Facebook Style Primary & Secondary Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Confirmer (Primary Filled)
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.friends_btn_confirm),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Supprimer (Neutral Surface Variant)
                Button(
                    onClick = onReject,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = stringResource(R.string.friends_btn_delete),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 3. Option Block
                IconButton(
                    onClick = onBlock,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = stringResource(R.string.friends_btn_block),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Instagram-style Friend Suggestion Card with crisp layout and "Ajouter" action.
 */
@Composable
private fun InstagramStyleSuggestionCard(
    suggestion: FriendSuggestion,
    isAlreadySent: Boolean,
    onInvite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OrbisAvatar(
                    avatarPath = suggestion.avatarPath,
                    name = suggestion.name,
                    size = 50.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (suggestion.isFromPhonebook) stringResource(R.string.friends_badge_in_phonebook) else stringResource(R.string.friends_badge_suggested),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (suggestion.mutualCount > 0) {
                        Text(
                            text = stringResource(R.string.friends_badge_mutual_count, suggestion.mutualCount),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isAlreadySent) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.friends_btn_added), fontSize = 12.sp)
                    }
                }
            } else {
                Button(
                    onClick = onInvite,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.friends_btn_invite), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Modern Sent Request Card showing pending handshake status and cancel button.
 */
@Composable
private fun ModernSentRequestCard(
    request: FriendRequest,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OrbisAvatar(
                    avatarPath = request.senderAvatarPath,
                    name = request.senderName,
                    size = 46.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.senderName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = stringResource(R.string.friends_btn_added),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(stringResource(R.string.friends_btn_cancel_invite), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BlockedCard(
    blocked: BlockedContact,
    onUnblock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = blocked.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = blocked.phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = onUnblock,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.height(34.dp)
            ) {
                Text(stringResource(R.string.friends_btn_unblock), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val diff = System.currentTimeMillis() - timestamp
    val mins = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L

    return when {
        mins < 1 -> "À l'instant"
        mins < 60 -> "${mins}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}j"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
    }
}
