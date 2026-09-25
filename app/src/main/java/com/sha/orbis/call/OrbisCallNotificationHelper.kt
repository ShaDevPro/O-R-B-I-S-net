package com.sha.orbis.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sha.orbis.MainActivity
import com.sha.orbis.R

object OrbisCallNotificationHelper {

    const val CHANNEL_CALLS = "orbis_calls_v5"
    const val CALL_NOTIFICATION_ID = 9001

    fun ensureCallChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            try {
                manager.deleteNotificationChannel("orbis_calls_channel")
                manager.deleteNotificationChannel("orbis_voice_calls_channel_v3")
                manager.deleteNotificationChannel("orbis_voice_calls_channel_v4")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_CALLS,
                "Appels Orbis Net",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications d'appels entrants"
                enableVibration(false) // Géré exclusivement par OrbisCallSoundManager
                setSound(null, null)  // Son unique géré par OrbisCallSoundManager (élimine la double sonnerie)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        callId: String,
        callerPhone: String,
        callerName: String,
        isVideo: Boolean = false,
        isFullScreen: Boolean = false
    ) {
        ensureCallChannel(context)

        // Réveil physique forcé de l'écran matériel (WakeLock matériel pour contourner le sommeil profond et les ROMs agressives type Vivo OriginOS)
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = pm?.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or android.os.PowerManager.ON_AFTER_RELEASE,
                "orbis:notif_screen_wake"
            )
            screenWakeLock?.acquire(15_000L)
        } catch (_: Exception) {}

        // 1. Full Screen Intent targeting MainActivity over lockscreen
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.sha.orbis.INCOMING_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_incoming_call", true)
            putExtra("extra_call_id", callId)
            putExtra("extra_caller_phone", callerPhone)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            CALL_NOTIFICATION_ID,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. Content Intent when user taps notification banner while phone is unlocked
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.sha.orbis.OPEN_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_incoming_call", true)
            putExtra("extra_call_id", callId)
            putExtra("extra_caller_phone", callerPhone)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            CALL_NOTIFICATION_ID + 1,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Accept Call Action Intent (Default / Video)
        val acceptIntent = Intent(context, OrbisCallActionReceiver::class.java).apply {
            action = if (isVideo) OrbisCallActionReceiver.ACTION_ACCEPT_VIDEO_CALL else OrbisCallActionReceiver.ACTION_ACCEPT_CALL
            putExtra("extra_call_id", callId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3b. Accept Voice Only Action Intent (For video calls)
        val acceptVoiceIntent = Intent(context, OrbisCallActionReceiver::class.java).apply {
            action = OrbisCallActionReceiver.ACTION_ACCEPT_VOICE_CALL
            putExtra("extra_call_id", callId)
        }
        val acceptVoicePendingIntent = PendingIntent.getBroadcast(
            context,
            103,
            acceptVoiceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. Decline Call Action Intent
        val declineIntent = Intent(context, OrbisCallActionReceiver::class.java).apply {
            action = OrbisCallActionReceiver.ACTION_DECLINE_CALL
            putExtra("extra_call_id", callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 5. Build Notification with Head-up alert banner (and optional Full Screen Intent if locked)
        val title = if (isVideo) "📹 $callerName" else "📞 $callerName"
        val subtitle = if (isVideo) context.getString(R.string.call_status_incoming_video) else context.getString(R.string.call_status_incoming)

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_logo, context.getString(R.string.call_action_decline), declinePendingIntent)

        // Full-screen intent : Heads-Up en écran déverrouillé, Plein écran au-dessus du lockscreen si verrouillé
        builder.setFullScreenIntent(fullScreenPendingIntent, true)

        if (isVideo) {
            builder.addAction(R.drawable.ic_logo, context.getString(R.string.call_action_accept_voice_only), acceptVoicePendingIntent)
            builder.addAction(R.drawable.ic_logo, context.getString(R.string.call_action_accept_video), acceptPendingIntent)
        } else {
            builder.addAction(R.drawable.ic_logo, context.getString(R.string.call_action_accept), acceptPendingIntent)
        }

        val notification = builder.build().apply {
            flags = flags or Notification.FLAG_ONGOING_EVENT
        }

        try {
            NotificationManagerCompat.from(context).notify(CALL_NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    fun cancelCallNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(CALL_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
