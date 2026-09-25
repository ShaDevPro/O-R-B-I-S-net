package com.sha.orbis.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.model.AppNotification
import com.sha.orbis.model.NotificationType
import com.sha.orbis.util.OtpExtractor
import org.json.JSONArray
import java.io.File

class NotificationRepository(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId?.takeIf { it.isNotBlank() } ?: try {
        com.sha.orbis.data.SessionManager(context).activeAccountId
    } catch (_: Exception) { "" }

    private val storageFile: File by lazy {
        AccountStorageManager.getAccountFile(context, "notifications.json", currentAccountId)
    }

    private fun getDefaultNotifications(): List<AppNotification> = listOf(
        AppNotification(
            id = "notif_welcome_1",
            title = context.getString(com.sha.orbis.R.string.notif_welcome_security_title),
            description = context.getString(com.sha.orbis.R.string.notif_welcome_security_desc),
            timestamp = System.currentTimeMillis() - 7200000L,
            type = NotificationType.SECURITY,
            isRead = false
        ),
        AppNotification(
            id = "notif_welcome_2",
            title = context.getString(com.sha.orbis.R.string.notif_welcome_sim_title),
            description = context.getString(com.sha.orbis.R.string.notif_welcome_sim_desc),
            timestamp = System.currentTimeMillis() - 14400000L,
            type = NotificationType.SIM_STATUS,
            isRead = true
        )
    )

    @Synchronized
    fun loadNotifications(): List<AppNotification> {
        if (!storageFile.exists()) {
            val defaults = getDefaultNotifications()
            saveNotifications(defaults)
            return defaults
        }
        return try {
            val array = JSONArray(storageFile.readText())
            val list = mutableListOf<AppNotification>()
            for (i in 0 until array.length()) {
                list.add(AppNotification.fromJson(array.getJSONObject(i)))
            }
            if (list.isEmpty()) {
                val defaults = getDefaultNotifications()
                saveNotifications(defaults)
                defaults
            } else {
                var modified = false
                val migrated = list.map { notif ->
                    val isTitleRaw = notif.title.startsWith("npub1", ignoreCase = true) ||
                        (notif.title.length == 64 && notif.title.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                    val isSenderRaw = notif.senderName?.startsWith("npub1", ignoreCase = true) == true ||
                        (notif.senderName?.length == 64 && notif.senderName!!.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })

                    if (notif.id == "notif_welcome_1" || notif.title.contains("GSM", ignoreCase = true) || notif.description.contains("RSA-2048")) {
                        modified = true
                        notif.copy(
                            title = context.getString(com.sha.orbis.R.string.notif_welcome_security_title),
                            description = context.getString(com.sha.orbis.R.string.notif_welcome_security_desc)
                        )
                    } else if (notif.id == "notif_welcome_2" || notif.description.contains("cryptographique")) {
                        modified = true
                        notif.copy(
                            title = context.getString(com.sha.orbis.R.string.notif_welcome_sim_title),
                            description = context.getString(com.sha.orbis.R.string.notif_welcome_sim_desc)
                        )
                    } else if (isTitleRaw || isSenderRaw) {
                        modified = true
                        val (cleanName, cleanAvatar) = resolveCleanSender(notif)
                        val cleanDesc = if (isTitleRaw && notif.description.contains(notif.title)) {
                            notif.description.replace(notif.title, cleanName)
                        } else notif.description
                        val (resolvedPostId, resolvedAction) = if (notif.type == NotificationType.SOCIAL && (notif.targetPostId.isNullOrBlank() || notif.actionType.isNullOrBlank())) {
                            resolveTargetSocialPost(notif)
                        } else Pair(notif.targetPostId, notif.actionType)
                        notif.copy(
                            title = if (isTitleRaw) cleanName else notif.title,
                            senderName = cleanName,
                            senderAvatarPath = cleanAvatar ?: notif.senderAvatarPath,
                            description = cleanDesc,
                            targetPostId = resolvedPostId,
                            actionType = resolvedAction
                        )
                    } else if (notif.type == NotificationType.SOCIAL && (notif.targetPostId.isNullOrBlank() || notif.actionType.isNullOrBlank())) {
                        modified = true
                        val (resolvedPostId, resolvedAction) = resolveTargetSocialPost(notif)
                        notif.copy(
                            targetPostId = resolvedPostId,
                            actionType = resolvedAction
                        )
                    } else {
                        notif
                    }
                }
                if (modified) {
                    saveNotifications(migrated)
                }
                migrated.sortedByDescending { it.timestamp }
            }
        } catch (_: Exception) {
            getDefaultNotifications()
        }
    }

    private fun resolveCleanSender(notif: AppNotification): Pair<String, String?> {
        val keyCandidate = when {
            !notif.senderPhone.isNullOrBlank() -> notif.senderPhone
            notif.title.startsWith("npub1", ignoreCase = true) || notif.title.length == 64 -> notif.title
            notif.senderName?.startsWith("npub1", ignoreCase = true) == true || notif.senderName?.length == 64 -> notif.senderName
            else -> null
        }

        if (keyCandidate != null) {
            val convRepo = ConversationRepository(context)
            val contacts = convRepo.loadContacts()
            val c = contacts.find {
                it.publicKey.equals(keyCandidate, ignoreCase = true) ||
                it.phone.equals(keyCandidate, ignoreCase = true) ||
                (it.phone.isNotBlank() && keyCandidate.isNotBlank() && FriendRequestRepository.isSamePhone(it.phone, keyCandidate))
            }
            if (c != null && c.name.isNotBlank() && !c.name.startsWith("+") && c.name != "O R B I S net" && !c.name.startsWith("npub1")) {
                return Pair(c.name, c.avatarPath ?: notif.senderAvatarPath)
            }

            val friendRepo = FriendRequestRepository(context)
            val r = friendRepo.loadRequests().find {
                it.senderPublicKey.equals(keyCandidate, ignoreCase = true) ||
                it.senderPhone.equals(keyCandidate, ignoreCase = true) ||
                (it.senderPhone.isNotBlank() && keyCandidate.isNotBlank() && FriendRequestRepository.isSamePhone(it.senderPhone, keyCandidate))
            }
            if (r != null && r.senderName.isNotBlank() && !r.senderName.startsWith("+") && r.senderName != "O R B I S net" && !r.senderName.startsWith("npub1")) {
                return Pair(r.senderName, r.senderAvatarPath ?: notif.senderAvatarPath)
            }

            val socialRepo = SocialRepository(context)
            for (p in socialRepo.loadPosts()) {
                if (p.authorPhone.equals(keyCandidate, ignoreCase = true) && p.authorName.isNotBlank() && !p.authorName.startsWith("npub1") && !p.authorName.startsWith("+")) {
                    return Pair(p.authorName, p.authorAvatarPath ?: notif.senderAvatarPath)
                }
                for (comm in p.comments) {
                    if (comm.authorPhone.equals(keyCandidate, ignoreCase = true) && comm.authorName.isNotBlank() && !comm.authorName.startsWith("npub1") && !comm.authorName.startsWith("+")) {
                        return Pair(comm.authorName, comm.authorAvatarPath ?: notif.senderAvatarPath)
                    }
                }
            }
        }

        return Pair(context.getString(com.sha.orbis.R.string.notif_sender_orbis_member), notif.senderAvatarPath)
    }

    private fun resolveTargetSocialPost(notif: AppNotification): Pair<String?, String?> {
        val resolvedAction = notif.actionType ?: when {
            notif.description.contains("aimé", ignoreCase = true) || notif.description.contains("like", ignoreCase = true) -> "LIKE"
            notif.description.contains("comment", ignoreCase = true) -> "COMMENT"
            notif.description.contains("publi", ignoreCase = true) || notif.description.contains("post", ignoreCase = true) -> "POST"
            else -> "LIKE"
        }

        val socialRepo = SocialRepository(context)
        val posts = socialRepo.loadPosts()
        val sessionMgr = com.sha.orbis.data.SessionManager(context)
        val myPhone = sessionMgr.userPhone

        val resolvedPostId = when (resolvedAction) {
            "LIKE" -> posts.firstOrNull { p ->
                FriendRequestRepository.isSamePhone(p.authorPhone, myPhone) &&
                p.reactions.any { r -> notif.senderPhone != null && (r.userPhone == notif.senderPhone || FriendRequestRepository.isSamePhone(r.userPhone, notif.senderPhone)) }
            }?.id ?: posts.firstOrNull { FriendRequestRepository.isSamePhone(it.authorPhone, myPhone) }?.id
            "COMMENT" -> posts.firstOrNull { p ->
                FriendRequestRepository.isSamePhone(p.authorPhone, myPhone) &&
                p.comments.any { c -> notif.senderPhone != null && FriendRequestRepository.isSamePhone(c.authorPhone, notif.senderPhone) }
            }?.id ?: posts.firstOrNull { FriendRequestRepository.isSamePhone(it.authorPhone, myPhone) }?.id
            else -> posts.firstOrNull { p -> notif.senderPhone != null && FriendRequestRepository.isSamePhone(p.authorPhone, notif.senderPhone) }?.id ?: posts.firstOrNull()?.id
        }

        return Pair(resolvedPostId, resolvedAction)
    }

    @Synchronized
    fun saveNotifications(list: List<AppNotification>) {
        try {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            storageFile.writeText(array.toString(2))
        } catch (_: Exception) {}
    }

    @Synchronized
    fun hasNotificationForEvent(notifId: String): Boolean {
        if (notifId.isBlank()) return false
        return loadNotifications().any { it.id == notifId }
    }

    @Synchronized
    fun addNotification(notification: AppNotification): Boolean {
        val current = loadNotifications().toMutableList()
        val isDuplicate = current.any {
            it.id == notification.id ||
            (it.targetPostId != null && it.targetPostId == notification.targetPostId &&
             it.actionType == notification.actionType &&
             it.senderPhone == notification.senderPhone &&
             Math.abs(it.timestamp - notification.timestamp) < 300_000L) ||
            (it.title == notification.title && it.description == notification.description && Math.abs(it.timestamp - notification.timestamp) < 60000L)
        }
        if (isDuplicate) {
            return false
        }
        current.add(0, notification)
        saveNotifications(current)
        return true
    }

    @Synchronized
    fun updateNotification(notification: AppNotification) {
        val current = loadNotifications().toMutableList()
        val idx = current.indexOfFirst { it.id == notification.id }
        if (idx >= 0) {
            current[idx] = notification
            saveNotifications(current)
        } else {
            current.add(0, notification)
            saveNotifications(current)
        }
    }

    @Synchronized
    fun markPostNotificationsAsRead(postId: String) {
        if (postId.isBlank()) return
        val current = loadNotifications().toMutableList()
        var changed = false
        val hexId = try { com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(postId) } catch (_: Exception) { "" }
        for (i in current.indices) {
            val notif = current[i]
            val matches = notif.targetPostId == postId ||
                (hexId.isNotBlank() && notif.targetPostId == hexId) ||
                (postId.length == 64 && notif.targetPostId?.let { com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it).equals(postId, ignoreCase = true) } == true)
            if (matches && !notif.isRead) {
                current[i] = notif.copy(isRead = true)
                changed = true
            }
        }
        if (changed) {
            saveNotifications(current)
        }
    }

    @Synchronized
    fun deletePostNotifications(postId: String) {
        if (postId.isBlank()) return
        val current = loadNotifications().toMutableList()
        val hexId = try { com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(postId) } catch (_: Exception) { "" }
        val changed = current.removeAll { notif ->
            notif.targetPostId == postId ||
            (hexId.isNotBlank() && notif.targetPostId == hexId) ||
            (postId.length == 64 && notif.targetPostId?.let { com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it).equals(postId, ignoreCase = true) } == true)
        }
        if (changed) {
            saveNotifications(current)
        }
    }

    @Synchronized
    fun markAsRead(id: String) {
        val current = loadNotifications().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            current[idx] = current[idx].copy(isRead = true)
            saveNotifications(current)
        }
    }

    @Synchronized
    fun deleteNotification(id: String) {
        val current = loadNotifications().toMutableList()
        if (current.removeAll { it.id == id }) {
            saveNotifications(current)
        }
    }


    @Synchronized
    fun markSenderNotificationsAsRead(phone: String) {
        if (phone.isBlank()) return
        val current = loadNotifications().toMutableList()
        val cleanPhone = phone.filter { it.isDigit() }
        var changed = false
        for (i in current.indices) {
            val notif = current[i]
            val notifPhone = notif.senderPhone?.filter { it.isDigit() } ?: ""
            val matches = if (cleanPhone.length >= 7 && notifPhone.length >= 7) {
                cleanPhone.takeLast(7) == notifPhone.takeLast(7)
            } else {
                notif.senderPhone.equals(phone, ignoreCase = true)
            }
            if (matches && !notif.isRead) {
                current[i] = notif.copy(isRead = true)
                changed = true
            }
        }
        if (changed) {
            saveNotifications(current)
        }
    }

    @Synchronized
    fun markCategoryAsRead(type: NotificationType) {
        val current = loadNotifications().toMutableList()
        var changed = false
        for (i in current.indices) {
            if (current[i].type == type && !current[i].isRead) {
                current[i] = current[i].copy(isRead = true)
                changed = true
            }
        }
        if (changed) {
            saveNotifications(current)
        }
    }

    @Synchronized
    fun syncUnreadPlainSms(context: Context) {
        // Obsolete in OrbisNet (pure Nostr decentralized messaging)
    }

    @Synchronized
    fun markAllAsRead() {
        val current = loadNotifications().map { it.copy(isRead = true) }
        saveNotifications(current)
    }

    @Synchronized
    fun clearAll() {
        saveNotifications(emptyList())
    }

    fun getUnreadCount(): Int = loadNotifications().count { !it.isRead }
}
