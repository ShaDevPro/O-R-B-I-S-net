package com.sha.orbis.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sha.orbis.MainActivity
import com.sha.orbis.R
import com.sha.orbis.data.OrbisBadgeHub
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.AppNotification
import com.sha.orbis.model.CallDirection
import com.sha.orbis.model.CallRecord
import com.sha.orbis.model.NotificationType
import com.sha.orbis.notification.OrbisEventBus
import com.sha.orbis.storage.CallLogRepository
import com.sha.orbis.storage.NotificationRepository
import com.sha.orbis.ui.components.AvatarManager

/**
 * OrbisMissedCallManager — Module dédié et modulaire gérant le cycle de vie complet
 * des notifications d'appels en absence (style WhatsApp) :
 * - Canal de notification dédié haute priorité avec son standard et vibration
 * - Notification interactive avec actions directes « Rappeler » et « Message »
 * - Regroupement dynamique du nombre d'appels manqués par correspondant
 * - Enregistrement dans le journal d'appels (CallLogRepository) et le centre de notifications (Cloche)
 * - Synchronisation réactive avec OrbisBadgeHub pour l'affichage des badges d'onglets
 * - Internationalisation complète (FR, EN, AR)
 */
object OrbisMissedCallManager {

    private const val TAG = "OrbisMissedCallManager"
    const val CHANNEL_MISSED_CALLS = "orbis_missed_calls_channel"
    private const val PREFS_MISSED_CALLS = "orbis_missed_calls_tracker"
    private const val MISSED_CALL_BASE_ID = 9100

    /**
     * Calcule un ID de notification stable et unique par numéro de correspondant.
     */
    fun getNotificationIdForPhone(phone: String): Int {
        val clean = phone.filter { it.isDigit() }
        val hash = if (clean.isNotBlank()) clean.hashCode() else phone.hashCode()
        return MISSED_CALL_BASE_ID + (hash and 0x7FFFFFFF) % 10000
    }

    /**
     * Initialise et garantit la présence du canal de notification pour les appels manqués.
     */
    fun ensureMissedCallChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_MISSED_CALLS,
                context.getString(R.string.notif_channel_missed_calls),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_missed_calls_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                enableLights(true)
                lightColor = Color.RED
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Déclenche le flow complet d'appel en absence suite à un timeout ou une annulation distante.
     */
    fun handleMissedCall(
        context: Context,
        callId: String,
        peerPhone: String,
        peerName: String,
        peerAvatar: String? = null,
        isVideo: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val targetAccountId = SessionManager(context).activeAccountId
        val displayName = peerName.ifBlank { peerPhone }
        Log.i(TAG, "Traitement appel en absence : callId=$callId, peer=$displayName ($peerPhone), isVideo=$isVideo")

        // 1. Enregistrement dans CallLogRepository
        val callRepo = CallLogRepository(context, targetAccountId)
        val record = CallRecord(
            id = callId,
            peerPhone = peerPhone,
            peerName = displayName,
            peerAvatar = peerAvatar,
            timestamp = timestamp,
            durationSeconds = 0,
            direction = CallDirection.MISSED,
            isVideo = isVideo,
            isRead = false
        )
        callRepo.addCall(record)

        // 2. Enregistrement dans NotificationRepository (Centre de notifications in-app / Cloche)
        val notifRepo = NotificationRepository(context, targetAccountId)
        val notifText = if (isVideo) {
            context.getString(R.string.missed_call_video)
        } else {
            context.getString(R.string.missed_call_audio)
        }
        val appNotif = AppNotification(
            id = "missed_call_$callId",
            title = displayName,
            description = notifText,
            timestamp = timestamp,
            type = NotificationType.MISSED_CALL,
            isRead = false,
            senderPhone = peerPhone,
            senderName = displayName,
            senderAvatarPath = peerAvatar
        )
        notifRepo.addNotification(appNotif)

        // 3. Incrémenter le compteur d'appels manqués non lus pour ce correspondant
        val count = incrementMissedCount(context, peerPhone)

        // 4. Construire et afficher la notification système interactive WhatsApp
        showMissedCallSystemNotification(
            context = context,
            callId = callId,
            peerPhone = peerPhone,
            peerName = displayName,
            peerAvatar = peerAvatar,
            isVideo = isVideo,
            timestamp = timestamp,
            missedCount = count
        )

        // 5. Rafraîchir les badges et diffuser les événements
        OrbisBadgeHub.refresh(context, targetAccountId)
        try {
            context.sendBroadcast(Intent(OrbisEventBus.ACTION_REFRESH_CALL_LOGS))
            context.sendBroadcast(Intent(OrbisEventBus.ACTION_REFRESH_NOTIFICATIONS))
        } catch (_: Exception) {}
    }

    /**
     * Construit et affiche la notification Heads-Up riche dans la barre d'état.
     */
    private fun showMissedCallSystemNotification(
        context: Context,
        callId: String,
        peerPhone: String,
        peerName: String,
        peerAvatar: String?,
        isVideo: Boolean,
        timestamp: Long,
        missedCount: Int
    ) {
        ensureMissedCallChannel(context)
        val notifId = getNotificationIdForPhone(peerPhone)

        val contentText = if (missedCount > 1) {
            context.getString(R.string.missed_call_multiple, missedCount)
        } else if (isVideo) {
            context.getString(R.string.missed_call_video)
        } else {
            context.getString(R.string.missed_call_audio)
        }

        // Clic sur le corps de la notification : ouvre l'app vers la discussion ou les appels
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.sha.orbis.OPEN_MISSED_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_phone", peerPhone)
            putExtra("extra_title", peerName)
            putExtra("extra_target_tab", 2) // Tab Appels
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notifId + 1,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 1 : « Rappeler » (Call back direct)
        val callBackIntent = Intent(context, MainActivity::class.java).apply {
            action = OrbisCallActionReceiver.ACTION_CALL_BACK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_is_callback", true)
            putExtra("extra_phone", peerPhone)
            putExtra("extra_title", peerName)
            putExtra("extra_is_video", isVideo)
        }
        val callBackPendingIntent = PendingIntent.getActivity(
            context,
            notifId + 2,
            callBackIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 2 : « Message » (Ouvre la discussion)
        val messageIntent = Intent(context, MainActivity::class.java).apply {
            action = OrbisCallActionReceiver.ACTION_MESSAGE_BACK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_phone", peerPhone)
            putExtra("extra_title", peerName)
        }
        val messagePendingIntent = PendingIntent.getActivity(
            context,
            notifId + 3,
            messageIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Rejet / Balayage de la notification (DeleteIntent)
        val deleteIntent = Intent(context, OrbisCallActionReceiver::class.java).apply {
            action = OrbisCallActionReceiver.ACTION_DISMISS_MISSED_CALL
            putExtra("extra_peer_phone", peerPhone)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 4,
            deleteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Avatar large icon si disponible
        val avatarBitmap = peerAvatar?.takeIf { it.isNotBlank() }?.let { path ->
            try {
                val f = java.io.File(path)
                if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
            } catch (_: Exception) { null }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MISSED_CALLS)
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentTitle(peerName)
            .setContentText(contentText)
            .setWhen(timestamp)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setColor(0xFFEF4444.toInt()) // Rouge appel manqué
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .addAction(
                android.R.drawable.ic_menu_call,
                context.getString(R.string.missed_call_action_callback),
                callBackPendingIntent
            )
            .addAction(
                android.R.drawable.ic_dialog_email,
                context.getString(R.string.missed_call_action_message),
                messagePendingIntent
            )

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap)
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Erreur affichage notification d'appel manqué: ${e.message}")
        }
    }

    /**
     * Annule la notification d'appel en absence pour un correspondant et remet à zéro son compteur.
     */
    fun cancelMissedCallNotification(context: Context, peerPhone: String) {
        if (peerPhone.isBlank()) return
        val notifId = getNotificationIdForPhone(peerPhone)
        try {
            NotificationManagerCompat.from(context).cancel(notifId)
        } catch (_: Exception) {}

        clearMissedCount(context, peerPhone)

        val targetAccountId = SessionManager(context).activeAccountId
        val callRepo = CallLogRepository(context, targetAccountId)
        callRepo.markMissedAsReadForPhone(peerPhone)
        OrbisBadgeHub.refresh(context, targetAccountId)
    }

    /**
     * Réinitialise toutes les notifications d'appels manqués et marque tout comme lu.
     */
    fun clearAllMissedCalls(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_MISSED_CALLS, Context.MODE_PRIVATE)
            val allKeys = prefs.all.keys
            for (key in allKeys) {
                if (key.startsWith("count_")) {
                    val phone = key.removePrefix("count_")
                    val notifId = getNotificationIdForPhone(phone)
                    try {
                        NotificationManagerCompat.from(context).cancel(notifId)
                    } catch (_: Exception) {}
                }
            }
            prefs.edit().clear().apply()
        } catch (_: Exception) {}

        val targetAccountId = SessionManager(context).activeAccountId
        val callRepo = CallLogRepository(context, targetAccountId)
        callRepo.markAllMissedAsRead()
        OrbisBadgeHub.refresh(context, targetAccountId)
    }

    private fun incrementMissedCount(context: Context, peerPhone: String): Int {
        val prefs = context.getSharedPreferences(PREFS_MISSED_CALLS, Context.MODE_PRIVATE)
        val key = "count_${peerPhone.filter { it.isDigit() }.ifBlank { peerPhone }}"
        val current = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, current).apply()
        return current
    }

    fun clearMissedCount(context: Context, peerPhone: String) {
        val prefs = context.getSharedPreferences(PREFS_MISSED_CALLS, Context.MODE_PRIVATE)
        val key = "count_${peerPhone.filter { it.isDigit() }.ifBlank { peerPhone }}"
        prefs.edit().remove(key).apply()
    }
}
