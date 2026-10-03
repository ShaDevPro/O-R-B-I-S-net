package com.sha.orbis.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sha.orbis.MainActivity
import com.sha.orbis.R

class OrbisCallForegroundService : Service() {

    private var cpuWakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseCpuWakeLock()
            stopSelf()
            return START_NOT_STICKY
        }

        acquireCpuWakeLock()

        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank {
            getString(R.string.app_name)
        }
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) == true
        val isActiveMedia = intent?.getBooleanExtra(EXTRA_ACTIVE_MEDIA, true) != false
        val notification = buildNotification(peerName, isVideo, isActiveMedia)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val serviceType = if (isActiveMedia) {
                    if (isVideo) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isActiveMedia) {
                val serviceType = if (isVideo) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start call foreground service with phone/media type: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (fallback: Exception) {
                Log.e(TAG, "Could not start call foreground service: ${fallback.message}")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        releaseCpuWakeLock()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun acquireCpuWakeLock() {
        if (cpuWakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            cpuWakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "orbis:call_foreground_cpu_wake"
            )?.apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L) // 2 heures max (sécurité)
            }
            Log.i(TAG, "Call Foreground Service: PARTIAL_WAKE_LOCK acquis (CPU maintenu éveillé)")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur acquisition WakeLock CPU: ${e.message}")
        }
    }

    private fun releaseCpuWakeLock() {
        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
                Log.i(TAG, "Call Foreground Service: PARTIAL_WAKE_LOCK libéré")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur libération WakeLock CPU: ${e.message}")
        } finally {
            cpuWakeLock = null
        }
    }

    private fun buildNotification(
        peerName: String,
        isVideo: Boolean,
        isActiveMedia: Boolean
    ): Notification {
        ensureChannel()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = "com.sha.orbis.OPEN_CALL"
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val endIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, OrbisCallActionReceiver::class.java).apply {
                action = OrbisCallActionReceiver.ACTION_END_CALL
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = getString(
            if (isVideo) R.string.call_service_video_title else R.string.call_service_voice_title,
            peerName
        )
        val text = getString(
            if (isActiveMedia) R.string.call_service_active_content else R.string.call_service_ringing_content
        )

        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_logo, getString(R.string.call_action_end), endIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.call_service_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "OrbisCallFgService"
        private const val CHANNEL_ID = "orbis_call_foreground_channel"
        private const val NOTIFICATION_ID = 9101
        private const val ACTION_START = "com.sha.orbis.call.START_FOREGROUND"
        private const val ACTION_STOP = "com.sha.orbis.call.STOP_FOREGROUND"
        private const val EXTRA_PEER_NAME = "extra_peer_name"
        private const val EXTRA_IS_VIDEO = "extra_is_video"
        private const val EXTRA_ACTIVE_MEDIA = "extra_active_media"

        fun start(
            context: Context,
            peerName: String,
            isVideo: Boolean,
            isActiveMedia: Boolean = true
        ) {
            val intent = Intent(context, OrbisCallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_ACTIVE_MEDIA, isActiveMedia)
            }
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to request call foreground service: ${e.message}")
            }
        }

        fun stop(context: Context?) {
            val appContext = context?.applicationContext ?: return
            try {
                appContext.stopService(Intent(appContext, OrbisCallForegroundService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop call foreground service: ${e.message}")
            }
        }
    }
}
