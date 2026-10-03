package com.sha.orbis.ui.notifications

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SimCard
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import java.util.Date
import java.util.Locale

enum class NotifFilterCategory {
    ALL,
    UNREAD,
    SOCIAL,
    MESSAGES,
    FRIENDS
}

/**
 * Facebook-style, warm and beginner-friendly Notification Center ("Cloche").
 * Features rich avatar badging, conversational narrative formatting,
 * relative human timestamps, inline friend request actions, and instant tab routing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterBottomSheet(
    onDismiss: () -> Unit,
    onOpenConversation: (convId: String?, senderPhone: String?, senderName: String?) -> Unit,
    onOpenFriendRequests: () -> Unit,
    onOpenPost: ((postId: String, actionType: String?) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val notifRepo = remember(context) { NotificationRepository(context) }
    val friendRepo = remember(context) { FriendRequestRepository(context) }
    val convRepo = remember(context) { ConversationRepository(context) }
    val sessionManager = remember(context) { SessionManager(context) }

    var notifications by remember { mutableStateOf(notifRepo.loadNotifications()) }
    var selectedCategory by remember { mutableStateOf(NotifFilterCategory.ALL) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun refreshList() {
        notifications = notifRepo.loadNotifications()
        OrbisBadgeHub.refresh(context)
    }

    val unreadTotal = notifications.count { !it.isRead }

    LaunchedEffect(Unit) {
        OrbisBadgeHub.markNotificationsRead(context)
        refreshList()
    }

    val filteredNotifications = remember(notifications, selectedCategory) {
        when (selectedCategory) {
            NotifFilterCategory.ALL -> notifications
            NotifFilterCategory.UNREAD -> notifications.filter { !it.isRead }
            NotifFilterCategory.SOCIAL -> notifications.filter { it.type == NotificationType.SOCIAL }
            NotifFilterCategory.MESSAGES -> notifications.filter { it.type == NotificationType.MESSAGE || it.type == NotificationType.PLAIN_SMS || it.type == NotificationType.MISSED_CALL }
            NotifFilterCategory.FRIENDS -> notifications.filter { it.type == NotificationType.FRIEND_REQUEST }
        }
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
        if (!notifName.isNullOrBlank() && !notifName.startsWith("+")) {
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
        val resolvedName = req?.senderName?.takeIf { it.isNotBlank() && !it.startsWith("+") }
            ?: notif.senderName?.takeIf { it.isNotBlank() && !it.startsWith("+") }
            ?: notif.title.takeIf { it.isNotBlank() && !it.startsWith("+") }
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
            android.util.Log.w("NotifSheet", "Nostr ACK error: ${e.message}")
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header Row
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.notif_center_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (unreadTotal > 0) {
                                OrbisPremiumBadge(count = unreadTotal)
                            }
                        }
                        Text(
                            text = if (unreadTotal > 0) "$unreadTotal ${stringResource(R.string.notif_unread_count)}" else stringResource(R.string.notif_all_read),
                            fontSize = 11.sp,
                            color = if (unreadTotal > 0) Color(0xFFFF2A54) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (unreadTotal > 0) {
                        OutlinedButton(
                            onClick = {
                                OrbisBadgeHub.markNotificationsRead(context)
                                refreshList()
                            },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = stringResource(R.string.notif_center_mark_all_read),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (notifications.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                notifRepo.clearAll()
                                refreshList()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.notif_center_clear_all),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 2. Beginner Friendly Categorized Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == NotifFilterCategory.ALL,
                        onClick = { selectedCategory = NotifFilterCategory.ALL },
                        label = { Text(stringResource(R.string.notif_filter_all), fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                item {
                    val count = notifications.count { !it.isRead }
                    FilterChip(
                        selected = selectedCategory == NotifFilterCategory.UNREAD,
                        onClick = { selectedCategory = NotifFilterCategory.UNREAD },
                        label = {
                            Text(
                                text = "🔵 ${stringResource(R.string.notif_filter_unread)}${if (count > 0) " ($count)" else ""}",
                                fontSize = 11.sp
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                item {
                    val count = notifications.count { it.type == NotificationType.SOCIAL && !it.isRead }
                    FilterChip(
                        selected = selectedCategory == NotifFilterCategory.SOCIAL,
                        onClick = { selectedCategory = NotifFilterCategory.SOCIAL },
                        label = {
                            Text(
                                text = "🌟 ${stringResource(R.string.notif_filter_social)}${if (count > 0) " ($count)" else ""}",
                                fontSize = 11.sp
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                item {
                    val count = notifications.count { (it.type == NotificationType.MESSAGE || it.type == NotificationType.PLAIN_SMS) && !it.isRead }
                    FilterChip(
                        selected = selectedCategory == NotifFilterCategory.MESSAGES,
                        onClick = { selectedCategory = NotifFilterCategory.MESSAGES },
                        label = {
                            Text(
                                text = "💬 ${stringResource(R.string.notif_filter_messages)}${if (count > 0) " ($count)" else ""}",
                                fontSize = 11.sp
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                item {
                    val count = notifications.count { it.type == NotificationType.FRIEND_REQUEST && !it.isRead }
                    FilterChip(
                        selected = selectedCategory == NotifFilterCategory.FRIENDS,
                        onClick = { selectedCategory = NotifFilterCategory.FRIENDS },
                        label = {
                            Text(
                                text = "👥 ${stringResource(R.string.notif_filter_friends)}${if (count > 0) " ($count)" else ""}",
                                fontSize = 11.sp
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // 3. Facebook-style Notifications List or Empty State
            if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✨ ${stringResource(R.string.notif_center_empty)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.notif_center_empty_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 460.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredNotifications, key = { it.id }) { notif ->
                        FacebookStyleNotificationCard(
                            notification = notif,
                            onClick = {
                                notifRepo.markAsRead(notif.id)
                                refreshList()
                                onDismiss()
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
                                            val posts = com.sha.orbis.storage.SocialRepository(context).loadPosts()
                                            val myPhone = com.sha.orbis.data.SessionManager(context).userPhone
                                            when (notif.actionType) {
                                                "LIKE" -> {
                                                    posts.firstOrNull { p ->
                                                        com.sha.orbis.storage.FriendRequestRepository.isSamePhone(p.authorPhone, myPhone) &&
                                                        p.reactions.any { r -> notif.senderPhone != null && (r.userPhone == notif.senderPhone || com.sha.orbis.storage.FriendRequestRepository.isSamePhone(r.userPhone, notif.senderPhone)) }
                                                    }?.id ?: posts.firstOrNull { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it.authorPhone, myPhone) }?.id
                                                }
                                                "COMMENT" -> {
                                                    posts.firstOrNull { p ->
                                                        com.sha.orbis.storage.FriendRequestRepository.isSamePhone(p.authorPhone, myPhone) &&
                                                        p.comments.any { c -> notif.senderPhone != null && com.sha.orbis.storage.FriendRequestRepository.isSamePhone(c.authorPhone, notif.senderPhone) }
                                                    }?.id ?: posts.firstOrNull { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it.authorPhone, myPhone) }?.id
                                                }
                                                "POST" -> {
                                                    posts.firstOrNull { p ->
                                                        notif.senderPhone != null && com.sha.orbis.storage.FriendRequestRepository.isSamePhone(p.authorPhone, notif.senderPhone)
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
                            onDelete = {
                                notifRepo.deleteNotification(notif.id)
                                refreshList()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Facebook-style rich notification card:
 * - Profile avatar with an action badge overlay (heart for like, bubble for comment, person for friend request, star for post)
 * - Conversational human narrative with bold actor name
 * - Inline direct actions ("Accepter" / "Refuser" for friend invitations)
 * - Relative friendly time formatting ("Il y a 5 min", "Hier")
 * - Elegant unread accentuation with vibrant blue dot
 */
@Composable
private fun FacebookStyleNotificationCard(
    notification: AppNotification,
    onClick: () -> Unit,
    onAcceptFriend: () -> Unit,
    onRejectFriend: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val orbisMemberText = stringResource(R.string.notif_sender_orbis_member)

    val rawCandidate = notification.senderName?.takeIf { it.isNotBlank() && !it.startsWith("+") }
        ?: notification.title
    val isRawKey = rawCandidate.startsWith("npub1", ignoreCase = true) ||
        (rawCandidate.length == 64 && rawCandidate.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })

    val actorName = if (isRawKey) {
        orbisMemberText
    } else {
        rawCandidate
    }

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

    val cardBg = if (isUnread) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val cardBorder = if (isUnread) {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Profile Avatar with Action Badge Overlay
            Box(
                modifier = Modifier.size(50.dp),
                contentAlignment = Alignment.Center
            ) {
                OrbisAvatar(
                    avatarPath = notification.senderAvatarPath,
                    name = actorName,
                    size = 46.dp
                )

                // Facebook-style action badge on bottom right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // 2. Narrative Content & Actions
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
                                fontSize = 13.sp
                            )
                        ) {
                            append(actorName)
                        }
                        append(" ")
                        withStyle(
                            SpanStyle(
                                fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.5.sp
                            )
                        ) {
                            append(cleanDescription)
                        }
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )

                // Facebook-style inline friend action buttons
                if (notification.actionType == "FRIEND_REQUEST") {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAcceptFriend,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
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
                            shape = RoundedCornerShape(10.dp),
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
                } else if (notification.actionType == "FRIEND_ACCEPTED") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.notif_action_accepted),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                // Relative human timestamp
                Text(
                    text = formatRelativeTime(context, notification.timestamp),
                    fontSize = 11.sp,
                    color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal
                )
            }

            // 3. Right Status: Unread Dot + Dismiss Icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(48.dp)
            ) {
                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB))
                    )
                } else {
                    Spacer(Modifier.size(9.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

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

