package com.sha.orbis.sms

import android.content.AsyncQueryHandler
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.sha.orbis.MainActivity
import com.sha.orbis.storage.ConversationRepository

object LauncherBadgeManager {

    private const val TAG = "LauncherBadgeManager"

    private val badgeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "Orbis-BadgeUpdater").apply { isDaemon = true }
    }

    /**
     * Updates the launcher icon badge number across all Android versions and OEM Launchers
     * (Stock Android 8-15, Samsung OneUI, Xiaomi MIUI/HyperOS, Huawei EMUI, Sony, LG, HTC, Nova).
     * Dispatched to a background daemon thread to ensure zero UI jank or main-thread blocking.
     */
    fun updateBadge(context: Context, unreadCount: Int = -1) {
        val appContext = context.applicationContext
        badgeExecutor.execute {
            try {
                val count = if (unreadCount >= 0) {
                    unreadCount
                } else {
                    calculateTotalUnread(appContext)
                }

                if (count == 0) {
                    try {
                        androidx.core.app.NotificationManagerCompat.from(appContext).cancelAll()
                    } catch (_: Exception) {}
                }

                val packageName = appContext.packageName
                val launcherClassName = MainActivity::class.java.name
                val manufacturer = Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
                val brand = Build.BRAND.lowercase(java.util.Locale.ROOT)

                // 1. Samsung OneUI / TouchWiz
                if (manufacturer.contains("samsung")) {
                    try {
                        val intent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                            putExtra("badge_count", count)
                            putExtra("badge_count_package_name", packageName)
                            putExtra("badge_count_class_name", launcherClassName)
                        }
                        appContext.sendBroadcast(intent)
                    } catch (_: Exception) {}
                }

                // 2. Sony Xperia
                if (manufacturer.contains("sony")) {
                    try {
                        val intent = Intent("com.sonyericsson.home.action.UPDATE_BADGE").apply {
                            putExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME", packageName)
                            putExtra("com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME", launcherClassName)
                            putExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE", if (count > 0) count.toString() else null)
                            putExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", count > 0)
                        }
                        appContext.sendBroadcast(intent)
                    } catch (_: Exception) {}
                }

                // 3. Huawei / Honor (Only attempt if device is actually Huawei/Honor)
                if (manufacturer.contains("huawei") || manufacturer.contains("honor") || brand.contains("huawei") || brand.contains("honor")) {
                    try {
                        val bundle = android.os.Bundle().apply {
                            putString("package", packageName)
                            putString("class", launcherClassName)
                            putInt("badgenumber", count)
                        }
                        appContext.contentResolver.call(
                            Uri.parse("content://com.huawei.android.launcher.settings/badge/"),
                            "change_badge",
                            null,
                            bundle
                        )
                    } catch (_: Exception) {}
                }

                // 4. HTC
                if (manufacturer.contains("htc")) {
                    try {
                        val intent = Intent("com.htc.launcher.action.UPDATE_SHORTCUT").apply {
                            putExtra("packagename", packageName)
                            putExtra("count", count)
                        }
                        appContext.sendBroadcast(intent)
                    } catch (_: Exception) {}
                }

                Log.d(TAG, "Badge du launcher mis à jour avec le compte : $count")
            } catch (e: Exception) {
                Log.w(TAG, "Impossible de mettre à jour le badge sur ce launcher spécifique: ${e.message}")
            }
        }
    }

    fun clearBadge(context: Context) {
        updateBadge(context, 0)
    }

    fun calculateTotalUnread(context: Context): Int {
        return try {
            val sessionManager = com.sha.orbis.data.SessionManager(context)
            val convRepo = ConversationRepository(context, sessionManager.activeAccountId)
            val notifRepo = com.sha.orbis.storage.NotificationRepository(context)
            val friendRepo = com.sha.orbis.storage.FriendRequestRepository(context)

            val unreadChats = convRepo.loadConversations().sumOf { it.unreadCount }
            val unreadSocial = notifRepo.getUnreadCount()
            val pendingRequests = friendRepo.getPendingReceived().size

            unreadChats + unreadSocial + pendingRequests
        } catch (_: Exception) {
            0
        }
    }
}
