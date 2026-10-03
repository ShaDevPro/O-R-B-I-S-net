package com.sha.orbis.data

import android.content.Context
import com.sha.orbis.sms.LauncherBadgeManager
import com.sha.orbis.storage.CallLogRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central Reactive Hub for App Badges across all Flows (TikTok / Telegram style).
 * Synchronizes in-app tab badges, header bell, and the Android OS Launcher badge.
 */
object OrbisBadgeHub {

    data class BadgeSnapshot(
        val unreadNotifications: Int = 0,      // Centre de Notifications (Cloche)
        val unreadSocial: Int = 0,             // Tab 0: Fil d'actualité
        val unreadChats: Int = 0,              // Tab 1: Discussions Privées E2EE
        val pendingFriendRequests: Int = 0,    // Tab 3: Contacts & Clés RSA
        val unreadPlainSms: Int = 0,           // Tab 4: SMS GSM Classiques
        val unreadMissedCalls: Int = 0         // Tab 2: Appels manqués (WhatsApp style)
    ) {
        val totalCount: Int
            get() = unreadNotifications + unreadChats + pendingFriendRequests + unreadPlainSms + unreadMissedCalls
    }

    private val _badgeState = MutableStateFlow(BadgeSnapshot())
    val badgeState: StateFlow<BadgeSnapshot> = _badgeState.asStateFlow()

    @Synchronized
    fun refresh(context: Context, accountId: String? = null): BadgeSnapshot {
        val sessionManager = SessionManager(context)
        val targetAccountId = accountId ?: sessionManager.activeAccountId

        val convRepo = ConversationRepository(context, targetAccountId)
        val notifRepo = NotificationRepository(context, targetAccountId)
        val friendRepo = FriendRequestRepository(context, targetAccountId)
        val callLogRepo = CallLogRepository(context, targetAccountId)

        val unreadChats = try {
            convRepo.loadConversations().sumOf { it.unreadCount }
        } catch (_: Exception) { 0 }

        val unreadNotifications = try {
            notifRepo.getUnreadCount()
        } catch (_: Exception) { 0 }

        val unreadSocial = try {
            notifRepo.loadNotifications().count { it.type == com.sha.orbis.model.NotificationType.SOCIAL && !it.isRead }
        } catch (_: Exception) { 0 }

        val pendingRequests = try {
            friendRepo.getPendingReceived().size
        } catch (_: Exception) { 0 }

        val unreadMissedCalls = try {
            callLogRepo.getUnreadMissedCount()
        } catch (_: Exception) { 0 }

        val snapshot = BadgeSnapshot(
            unreadNotifications = unreadNotifications,
            unreadSocial = unreadSocial,
            unreadChats = unreadChats,
            pendingFriendRequests = pendingRequests,
            unreadPlainSms = 0,
            unreadMissedCalls = unreadMissedCalls
        )

        _badgeState.value = snapshot

        // Automatically update the native Android Launcher icon badge
        LauncherBadgeManager.updateBadge(context, snapshot.totalCount)

        return snapshot
    }

    fun markNotificationsRead(context: Context, accountId: String? = null) {
        try {
            val sessionManager = SessionManager(context)
            val targetAccountId = accountId ?: sessionManager.activeAccountId
            val notifRepo = NotificationRepository(context, targetAccountId)
            notifRepo.markAllAsRead()
            refresh(context, targetAccountId)
        } catch (_: Exception) {}
    }

    fun markSocialRead(context: Context, accountId: String? = null) {
        try {
            val sessionManager = SessionManager(context)
            val targetAccountId = accountId ?: sessionManager.activeAccountId
            val notifRepo = NotificationRepository(context, targetAccountId)
            notifRepo.markCategoryAsRead(com.sha.orbis.model.NotificationType.SOCIAL)
            refresh(context, targetAccountId)
        } catch (_: Exception) {}
    }

    fun markConversationRead(context: Context, convId: String, accountId: String? = null) {
        try {
            val sessionManager = SessionManager(context)
            val targetAccountId = accountId ?: sessionManager.activeAccountId
            val convRepo = ConversationRepository(context, targetAccountId)
            val convs = convRepo.loadConversations().toMutableList()
            val index = convs.indexOfFirst { it.id == convId }
            if (index >= 0 && convs[index].unreadCount > 0) {
                convs[index] = convs[index].copy(unreadCount = 0)
                convRepo.saveConversations(convs)
            }
            com.sha.orbis.sms.SmsNotificationHelper.cancelNotification(context, convId)
            refresh(context, targetAccountId)
        } catch (_: Exception) {}
    }
}
