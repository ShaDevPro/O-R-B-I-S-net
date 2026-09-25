package com.sha.orbis.ui.notifications

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.data.OrbisBadgeHub
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.AppNotification
import com.sha.orbis.model.Contact
import com.sha.orbis.model.FriendRequest
import com.sha.orbis.model.NotificationType
import android.widget.Toast
import com.sha.orbis.nostr.service.NostrSyncManager
import com.sha.orbis.notification.OrbisEventBus
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.NotificationRepository
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.components.OrbisPremiumBadge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Catégories de filtres de notifications.
 */
enum class NotificationFilter {
    ALL,
    UNREAD,
    SOCIAL,
    MESSAGES,
    FRIENDS
}

/**
 * Périodes de groupement temporel.
 */
enum class NotificationTimeGroup {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    OLDER
}

/**
 * Page de Notifications souveraine ("Cloche") de niveau Entreprise / Géants de la Tech.
 *
 * Fonctionnalités majeures :
 * - Expérience plein écran immersive avec animations fluides
 * - Barre d'onglets segmentée moderne avec compteurs dynamiques
 * - Groupement chronologique clair (Aujourd'hui, Hier, Cette semaine, Plus ancien)
 * - Cartes enrichies avec avatars haute résolution et badges d'actions
 * - Actions contextuelles intégrées (Accepter / Refuser, Répondre, Voir le post)
 * - Actions globales : « Tout marquer comme lu », « Effacer tout » avec dialogue
 * - Support trilingue complet (FR, EN, AR avec prise en charge RTL)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    currentAccountId: String? = null,
    onBack: () -> Unit,
    onOpenConversation: (convId: String?, senderPhone: String?, senderName: String?) -> Unit,
    onOpenFriendRequests: () -> Unit,
    onOpenPost: ((postId: String, actionType: String?) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sessionManager = remember(context) { SessionManager(context) }
    val activeAccountId = currentAccountId ?: sessionManager.activeAccountId
    val notifRepo = remember(context, activeAccountId) { NotificationRepository(context, activeAccountId) }
    val friendRepo = remember(context, activeAccountId) { FriendRequestRepository(context, activeAccountId) }
    val convRepo = remember(context, activeAccountId) { ConversationRepository(context, activeAccountId) }

    var notifications by remember(activeAccountId) { mutableStateOf(notifRepo.loadNotifications()) }
    var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    fun refreshList() {
        notifications = notifRepo.loadNotifications()
        OrbisBadgeHub.refresh(context, activeAccountId)
    }

    val unreadTotal = notifications.count { !it.isRead }

    LaunchedEffect(activeAccountId) {
        OrbisBadgeHub.markNotificationsRead(context, activeAccountId)
        refreshList()
    }

    // Filtrage des notifications
    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            NotificationFilter.ALL -> notifications
            NotificationFilter.UNREAD -> notifications.filter { !it.isRead }
            NotificationFilter.SOCIAL -> notifications.filter { it.type == NotificationType.SOCIAL }
            NotificationFilter.MESSAGES -> notifications.filter { it.type == NotificationType.MESSAGE || it.type == NotificationType.PLAIN_SMS }
            NotificationFilter.FRIENDS -> notifications.filter { it.type == NotificationType.FRIEND_REQUEST }
        }
    }

    // Groupement chronologique
    val groupedNotifications = remember(filteredNotifications) {
        val now = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterdayStart = todayStart - 86400000L
        val weekStart = todayStart - (6 * 86400000L)

        val groups = linkedMapOf<NotificationTimeGroup, MutableList<AppNotification>>()
        filteredNotifications.forEach { notif ->
            val group = when {
                notif.timestamp >= todayStart -> NotificationTimeGroup.TODAY
                notif.timestamp >= yesterdayStart -> NotificationTimeGroup.YESTERDAY
                notif.timestamp >= weekStart -> NotificationTimeGroup.THIS_WEEK
                else -> NotificationTimeGroup.OLDER
            }
            groups.getOrPut(group) { mutableListOf() }.add(notif)
        }
        groups
    }

    fun findMatchingFriendRequest(notif: AppNotification): FriendRequest? {
        val allRequests = friendRepo.loadRequests()
        // 1. By ID match (e.g. eventId prefix)
        val notifSuffix = notif.id.removePrefix("notif_invitation_").removePrefix("notif_")
        if (notifSuffix.isNotBlank()) {
            val byId = allRequests.firstOrNull { req ->
                val reqSuffix = req.id.removePrefix("req_nostr_").removePrefix("req_")
                reqSuffix.isNotBlank() && (
                    reqSuffix.startsWith(notifSuffix.take(8)) ||
                    notifSuffix.startsWith(reqSuffix.take(8)) ||
                    req.id == notif.id
                )
            }
            if (byId != null) return byId
        }

        // 2. By phone match (phone format or direct string match)
        val notifPhone = notif.senderPhone?.trim()
        if (!notifPhone.isNullOrBlank()) {
            val byPhone = allRequests.firstOrNull { req ->
                FriendRequestRepository.isSamePhone(req.senderPhone, notifPhone) ||
                req.senderPhone.equals(notifPhone, ignoreCase = true)
            }
            if (byPhone != null) return byPhone
        }

        // 3. By Nostr public key match
        val pubkeyCandidates = listOfNotNull(notif.senderPhone, notif.title, notif.senderName)
        for (candidate in pubkeyCandidates) {
            val hex = FriendRequestRepository.resolveNostrPubkeyHex(candidate)
            if (hex != null) {
                val byKey = allRequests.firstOrNull { req ->
                    FriendRequestRepository.resolveNostrPubkeyHex(req.senderPublicKey) == hex
                }
                if (byKey != null) return byKey
            }
        }

        // 4. By senderName match
        val notifName = notif.senderName?.trim()
        if (!notifName.isNullOrBlank() && notifName != "O R B I S net" && !notifName.startsWith("+")) {
            val byName = allRequests.firstOrNull { req ->
                req.senderName.trim().equals(notifName, ignoreCase = true)
            }
            if (byName != null) return byName
        }

        return null
    }

    fun acceptFriendRequest(notif: AppNotification) {
        val req = findMatchingFriendRequest(notif)
        val resolvedPhone = req?.senderPhone?.takeIf { it.isNotBlank() }
            ?: notif.senderPhone?.takeIf { it.isNotBlank() }
            ?: notif.title.takeIf { it.isNotBlank() }
            ?: ""
        val resolvedName = req?.senderName?.takeIf { it.isNotBlank() && !it.startsWith("+") && it != "O R B I S net" }
            ?: notif.senderName?.takeIf { it.isNotBlank() && !it.startsWith("+") && it != "O R B I S net" }
            ?: notif.title.takeIf { it.isNotBlank() && !it.startsWith("+") && it != "O R B I S net" }
            ?: resolvedPhone
        val resolvedPubkey = req?.senderPublicKey?.takeIf { it.isNotBlank() }
            ?: (if (FriendRequestRepository.isValidNostrKey(notif.senderPhone)) notif.senderPhone else "")
            ?: ""
        val resolvedAvatar = req?.senderAvatarPath ?: notif.senderAvatarPath

        // 1. Accepter dans FriendRequestRepository
        if (req != null) {
            friendRepo.acceptRequest(req.id)
        }
        if (resolvedPhone.isNotBlank()) {
            friendRepo.acceptRequestForPhone(resolvedPhone)
            friendRepo.ensureAcceptedFriend(
                phone = resolvedPhone,
                name = resolvedName,
                publicKey = resolvedPubkey,
                avatarPath = resolvedAvatar
            )
        }

        // 2. Mettre à jour / ajouter dans ConversationRepository contacts
        val contacts = convRepo.loadContacts().toMutableList()
        val cIdx = contacts.indexOfFirst {
            (resolvedPhone.isNotBlank() && (FriendRequestRepository.isSamePhone(it.phone, resolvedPhone) || it.phone.equals(resolvedPhone, ignoreCase = true))) ||
            (resolvedPubkey.isNotBlank() && it.publicKey.equals(resolvedPubkey, ignoreCase = true))
        }
        val cleanDigits = resolvedPhone.filter { it.isDigit() }
        if (cIdx >= 0) {
            contacts[cIdx] = contacts[cIdx].copy(
                status = "Connecté 🛡️",
                publicKey = resolvedPubkey.ifBlank { contacts[cIdx].publicKey },
                avatarPath = resolvedAvatar ?: contacts[cIdx].avatarPath,
                name = if (resolvedName.isNotBlank() && !resolvedName.startsWith("+")) resolvedName else contacts[cIdx].name
            )
        } else {
            contacts.add(0, Contact(
                id = "c_${cleanDigits.ifBlank { System.currentTimeMillis().toString() }}",
                name = resolvedName,
                phone = resolvedPhone,
                publicKey = resolvedPubkey,
                status = "Connecté 🛡️",
                avatarPath = resolvedAvatar
            ))
        }
        convRepo.saveContacts(contacts)

        // 3. Assurer la conversation dans Discussions
        if (resolvedPhone.isNotBlank()) {
            convRepo.ensureConversationForFriend(
                phone = resolvedPhone,
                name = resolvedName,
                initialMessage = context.getString(R.string.friends_connected_last_msg)
            )
        }

        // 4. Émettre l'ACK Nostr
        val myAvatarThumb = AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)
        try {
            val nostrSync = NostrSyncManager.getInstance(context)
            nostrSync.publishFriendInvitationAck(
                recipientPhone = resolvedPhone,
                recipientPubkeyHex = resolvedPubkey.takeIf { it.isNotBlank() },
                avatarBase64 = myAvatarThumb
            )
            nostrSync.refreshSubscriptions()
        } catch (e: Exception) {
            android.util.Log.w("NotifCenter", "Nostr ACK error: ${e.message}")
        }

        // 5. Mettre à jour la notification persistée
        notifRepo.updateNotification(
            notif.copy(
                isRead = true,
                actionType = "FRIEND_ACCEPTED",
                description = context.getString(R.string.notif_friend_request_accepted)
            )
        )

        // 6. Broadcast & feedback
        context.sendBroadcast(Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS))
        Toast.makeText(context, context.getString(R.string.friends_request_accepted_toast), Toast.LENGTH_SHORT).show()
        refreshList()
    }

    fun rejectFriendRequest(notif: AppNotification) {
        val req = findMatchingFriendRequest(notif)
        val resolvedPhone = req?.senderPhone?.takeIf { it.isNotBlank() }
            ?: notif.senderPhone?.takeIf { it.isNotBlank() }
            ?: notif.title.takeIf { it.isNotBlank() }
            ?: ""

        if (req != null) {
            friendRepo.rejectRequest(req.id)
        }
        if (resolvedPhone.isNotBlank()) {
            friendRepo.rejectRequestForPhone(resolvedPhone)
        }
        notifRepo.deleteNotification(notif.id)
        context.sendBroadcast(Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS))
        Toast.makeText(context, context.getString(R.string.friends_request_deleted_state), Toast.LENGTH_SHORT).show()
        refreshList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Back button + Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.notif_center_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (unreadTotal > 0) {
                                    OrbisPremiumBadge(count = unreadTotal)
                                }
                            }
                        }

                        // Right: Actions (Mark all read, Clear all, Settings)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (unreadTotal > 0) {
                                TextButton(
                                    onClick = {
                                        OrbisBadgeHub.markNotificationsRead(context)
                                        refreshList()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.notif_center_mark_all_read),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (notifications.isNotEmpty()) {
                                IconButton(onClick = { showClearConfirmDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = stringResource(R.string.notif_center_clear_all),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            if (onOpenSettings != null) {
                                IconButton(onClick = onOpenSettings) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Paramètres",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Segmented Filter Bar (Modern Tech Giant Pills)
                    NotificationFilterBar(
                        selectedFilter = selectedFilter,
                        onSelectFilter = { selectedFilter = it },
                        totalCount = notifications.size,
                        unreadCount = unreadTotal,
                        socialCount = notifications.count { it.type == NotificationType.SOCIAL && !it.isRead },
                        messagesCount = notifications.count { (it.type == NotificationType.MESSAGE || it.type == NotificationType.PLAIN_SMS) && !it.isRead },
                        friendsCount = notifications.count { it.type == NotificationType.FRIEND_REQUEST && !it.isRead }
                    )
                }
            }
        }
    ) { paddingValues ->
        var isRefreshing by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                coroutineScope.launch {
                    try {
                        NostrSyncManager.getInstance(context).reconnect(force = false)
                    } catch (_: Exception) {}
                    refreshList()
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    NotificationEmptyState(
                        filter = selectedFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedNotifications.forEach { (timeGroup, itemsInGroup) ->
                        // Section Header
                        item(key = "header_${timeGroup.name}") {
                            NotificationSectionHeader(
                                timeGroup = timeGroup,
                                count = itemsInGroup.size
                            )
                        }

                        // Notification Items in this section
                        items(itemsInGroup, key = { it.id }) { notif ->
                            NotificationEnterpriseCard(
                                notification = notif,
                                onClick = {
                                    notifRepo.markAsRead(notif.id)
                                    refreshList()
                                    when (notif.type) {
                                        NotificationType.MESSAGE, NotificationType.PLAIN_SMS, NotificationType.MISSED_CALL -> {
                                            onOpenConversation(notif.targetConvId, notif.senderPhone, notif.senderName)
                                        }
                                        NotificationType.FRIEND_REQUEST -> {
                                            if (notif.actionType == "FRIEND_ACCEPTED" && !notif.senderPhone.isNullOrBlank()) {
                                                onOpenConversation(notif.targetConvId, notif.senderPhone, notif.senderName)
                                            } else {
                                                onOpenFriendRequests()
                                            }
                                        }
                                        NotificationType.SOCIAL -> {
                                            val resolvedPostId = notif.targetPostId?.takeIf { it.isNotBlank() } ?: run {
                                                val posts = com.sha.orbis.storage.SocialRepository(context, activeAccountId).loadPosts()
                                                val myPhone = sessionManager.userPhone
                                                when (notif.actionType) {
                                                    "LIKE" -> {
                                                        posts.firstOrNull { p ->
                                                            FriendRequestRepository.isSamePhone(p.authorPhone, myPhone) &&
                                                            p.reactions.any { r -> notif.senderPhone != null && (r.userPhone == notif.senderPhone || FriendRequestRepository.isSamePhone(r.userPhone, notif.senderPhone)) }
                                                        }?.id ?: posts.firstOrNull { FriendRequestRepository.isSamePhone(it.authorPhone, myPhone) }?.id
                                                    }
                                                    "COMMENT" -> {
                                                        posts.firstOrNull { p ->
                                                            FriendRequestRepository.isSamePhone(p.authorPhone, myPhone) &&
                                                            p.comments.any { c -> notif.senderPhone != null && FriendRequestRepository.isSamePhone(c.authorPhone, notif.senderPhone) }
                                                        }?.id ?: posts.firstOrNull { FriendRequestRepository.isSamePhone(it.authorPhone, myPhone) }?.id
                                                    }
                                                    "POST" -> {
                                                        posts.firstOrNull { p ->
                                                            notif.senderPhone != null && FriendRequestRepository.isSamePhone(p.authorPhone, notif.senderPhone)
                                                        }?.id
                                                    }
                                                    else -> posts.firstOrNull()?.id
                                                }
                                            }
                                            onOpenPost?.invoke(resolvedPostId ?: "", notif.actionType)
                                        }
                                        NotificationType.SECURITY, NotificationType.SIM_STATUS -> {
                                            onOpenSettings?.invoke()
                                        }
                                    }
                                },
                                onAcceptFriend = { acceptFriendRequest(notif) },
                                onRejectFriend = { rejectFriendRequest(notif) },
                                onReply = {
                                    notifRepo.markAsRead(notif.id)
                                    refreshList()
                                    onOpenConversation(notif.targetConvId, notif.senderPhone, notif.senderName)
                                },
                                onViewPost = {
                                    notifRepo.markAsRead(notif.id)
                                    refreshList()
                                    onOpenPost?.invoke(notif.targetPostId ?: "", notif.actionType)
                                },
                                onDelete = {
                                    notifRepo.deleteNotification(notif.id)
                                    refreshList()
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
                    }
                }
            }
        }
    }

    // Dialogue de confirmation pour vider toutes les notifications
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.notif_clear_all_confirm_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.notif_clear_all_confirm_desc),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        notifRepo.clearAll()
                        showClearConfirmDialog = false
                        refreshList()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.notif_clear_confirm_btn), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.notif_cancel_btn))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * Barre de filtres modernes segmentés (Style X / LinkedIn).
 */
@Composable
private fun NotificationFilterBar(
    selectedFilter: NotificationFilter,
    onSelectFilter: (NotificationFilter) -> Unit,
    totalCount: Int,
    unreadCount: Int,
    socialCount: Int,
    messagesCount: Int,
    friendsCount: Int
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFilter == NotificationFilter.ALL,
                onClick = { onSelectFilter(NotificationFilter.ALL) },
                label = {
                    Text(
                        text = stringResource(R.string.notif_filter_all),
                        fontWeight = if (selectedFilter == NotificationFilter.ALL) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        item {
            FilterChip(
                selected = selectedFilter == NotificationFilter.UNREAD,
                onClick = { onSelectFilter(NotificationFilter.UNREAD) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.notif_filter_unread),
                            fontWeight = if (selectedFilter == NotificationFilter.UNREAD) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedFilter == NotificationFilter.UNREAD) Color.White else MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$unreadCount",
                                    color = if (selectedFilter == NotificationFilter.UNREAD) MaterialTheme.colorScheme.primary else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        item {
            FilterChip(
                selected = selectedFilter == NotificationFilter.SOCIAL,
                onClick = { onSelectFilter(NotificationFilter.SOCIAL) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "🌟 ${stringResource(R.string.notif_filter_social)}",
                            fontWeight = if (selectedFilter == NotificationFilter.SOCIAL) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        if (socialCount > 0) {
                            Text(
                                text = "($socialCount)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        item {
            FilterChip(
                selected = selectedFilter == NotificationFilter.MESSAGES,
                onClick = { onSelectFilter(NotificationFilter.MESSAGES) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "💬 ${stringResource(R.string.notif_filter_messages)}",
                            fontWeight = if (selectedFilter == NotificationFilter.MESSAGES) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        if (messagesCount > 0) {
                            Text(
                                text = "($messagesCount)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        item {
            FilterChip(
                selected = selectedFilter == NotificationFilter.FRIENDS,
                onClick = { onSelectFilter(NotificationFilter.FRIENDS) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "👥 ${stringResource(R.string.notif_filter_friends)}",
                            fontWeight = if (selectedFilter == NotificationFilter.FRIENDS) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        if (friendsCount > 0) {
                            Text(
                                text = "($friendsCount)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

/**
 * En-tête de section chronologique.
 */
@Composable
private fun NotificationSectionHeader(
    timeGroup: NotificationTimeGroup,
    count: Int
) {
    val title = when (timeGroup) {
        NotificationTimeGroup.TODAY -> stringResource(R.string.notif_section_today)
        NotificationTimeGroup.YESTERDAY -> stringResource(R.string.notif_section_yesterday)
        NotificationTimeGroup.THIS_WEEK -> stringResource(R.string.notif_section_this_week)
        NotificationTimeGroup.OLDER -> stringResource(R.string.notif_section_older)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

/**
 * Carte de notification moderne et soignée (Niveau Entreprise / Géants).
 */
@Composable
private fun NotificationEnterpriseCard(
    notification: AppNotification,
    onClick: () -> Unit,
    onAcceptFriend: () -> Unit,
    onRejectFriend: () -> Unit,
    onReply: () -> Unit,
    onViewPost: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val orbisMemberText = stringResource(R.string.notif_sender_orbis_member)

    val rawCandidate = notification.senderName?.takeIf { it.isNotBlank() && !it.startsWith("+") && it != "O R B I S net" }
        ?: notification.title
    val isRawKey = rawCandidate.startsWith("npub1", ignoreCase = true) ||
        (rawCandidate.length == 64 && rawCandidate.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })

    val actorName = if (isRawKey) orbisMemberText else rawCandidate

    var cleanDescription = notification.description
    if (isRawKey && cleanDescription.contains(rawCandidate)) {
        cleanDescription = cleanDescription.replace(rawCandidate, actorName)
    }
    cleanDescription = cleanDescription.replace(Regex("npub1[a-z0-9]{20,}", RegexOption.IGNORE_CASE), orbisMemberText)

    val isUnread = !notification.isRead

    val (badgeIcon: ImageVector, badgeColor: Color) = when {
        notification.actionType == "LIKE" -> Pair(Icons.Default.Favorite, Color(0xFFE11D48))
        notification.actionType == "COMMENT" -> Pair(Icons.Default.ChatBubble, Color(0xFF0284C7))
        notification.actionType == "POST" -> Pair(Icons.Default.Public, Color(0xFF8B5CF6))
        notification.actionType == "FRIEND_REQUEST" -> Pair(Icons.Default.PersonAdd, Color(0xFF10B981))
        notification.actionType == "FRIEND_ACCEPTED" -> Pair(Icons.Default.Check, Color(0xFF10B981))
        notification.type == NotificationType.MESSAGE -> Pair(Icons.AutoMirrored.Filled.Message, Color(0xFF3B82F6))
        notification.type == NotificationType.MISSED_CALL -> Pair(Icons.AutoMirrored.Filled.CallMissed, Color(0xFFEF4444))
        notification.type == NotificationType.PLAIN_SMS -> Pair(Icons.Default.Forum, Color(0xFF0284C7))
        notification.type == NotificationType.SECURITY -> Pair(Icons.Default.Lock, Color(0xFF10B981))
        notification.type == NotificationType.SIM_STATUS -> Pair(Icons.Default.SimCard, Color(0xFFFFA000))
        notification.type == NotificationType.SOCIAL -> Pair(Icons.Default.Public, Color(0xFF8B5CF6))
        else -> Pair(Icons.Default.Notifications, MaterialTheme.colorScheme.primary)
    }

    val cardBg by animateColorAsState(
        targetValue = if (isUnread) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        animationSpec = tween(durationMillis = 250),
        label = "cardBg"
    )

    val cardBorder = if (isUnread) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Avatar de l'utilisateur avec Badge d'action superposé
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                OrbisAvatar(
                    avatarPath = notification.senderAvatarPath,
                    name = actorName,
                    size = 48.dp
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // 2. Contenu textuel narratif & boutons d'action
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.5.sp
                            )
                        ) {
                            append(actorName)
                        }
                        append(" ")
                        withStyle(
                            SpanStyle(
                                fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        ) {
                            append(cleanDescription)
                        }
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                // Actions contextuelles directes
                when (notification.actionType) {
                    "FRIEND_REQUEST" -> {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onAcceptFriend,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.notif_action_accept),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = onRejectFriend,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.notif_action_reject),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    "FRIEND_ACCEPTED" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = stringResource(R.string.notif_action_accepted),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                    else -> {
                        // Pour les messages ou posts, bouton action rapide léger
                        if (notification.type == NotificationType.MESSAGE || notification.type == NotificationType.PLAIN_SMS) {
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onReply,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.notif_action_reply),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else if (notification.type == NotificationType.SOCIAL && !notification.targetPostId.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onViewPost,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = Color(0xFF8B5CF6)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.notif_action_view_post),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8B5CF6)
                                    )
                                }
                            }
                        }
                    }
                }

                // Horodatage relatif dynamique
                Text(
                    text = formatRelativeTime(context, notification.timestamp),
                    fontSize = 11.5.sp,
                    color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            // 3. Indicateur non-lu + bouton fermeture
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(52.dp)
            ) {
                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB))
                    )
                } else {
                    Spacer(Modifier.size(10.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Supprimer",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * État vide raffiné selon le filtre sélectionné.
 */
@Composable
private fun NotificationEmptyState(
    filter: NotificationFilter,
    modifier: Modifier = Modifier
) {
    val (icon: ImageVector, titleRes: Int, subRes: Int) = when (filter) {
        NotificationFilter.ALL -> Triple(
            Icons.Default.NotificationsActive,
            R.string.notif_center_empty,
            R.string.notif_center_empty_sub
        )
        NotificationFilter.UNREAD -> Triple(
            Icons.Default.CheckCircle,
            R.string.notif_filter_empty_unread,
            R.string.notif_center_empty_sub
        )
        NotificationFilter.SOCIAL -> Triple(
            Icons.Default.Public,
            R.string.notif_filter_empty_social,
            R.string.notif_center_empty_sub
        )
        NotificationFilter.MESSAGES -> Triple(
            Icons.Default.ChatBubble,
            R.string.notif_filter_empty_messages,
            R.string.notif_center_empty_sub
        )
        NotificationFilter.FRIENDS -> Triple(
            Icons.Default.PersonAdd,
            R.string.notif_filter_empty_friends,
            R.string.notif_center_empty_sub
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(subRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Formatage d'heure relative convivial.
 */
private fun formatRelativeTime(context: Context, timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> context.getString(R.string.notif_time_just_now)
        diff < 3600_000L -> context.getString(R.string.notif_time_minutes_ago, (diff / 60_000L).coerceAtLeast(1))
        diff < 86400_000L -> context.getString(R.string.notif_time_hours_ago, (diff / 3600_000L).coerceAtLeast(1))
        diff < 172800_000L -> context.getString(R.string.notif_time_yesterday)
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }
}
