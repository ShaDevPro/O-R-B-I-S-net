package com.sha.orbis.nostr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sha.orbis.MainActivity
import com.sha.orbis.R
import com.sha.orbis.nostr.client.RelayPoolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service d'avant-plan (Foreground Service) permanent pour maintenir la connexion
 * au maillage souverain Nostr même lorsque le téléphone est en veille / écran éteint.
 *
 * Évite que le système Android n'interrompe les WebSockets ou ne gèle les threads
 * en arrière-plan (Doze mode / App Standby).
 */
class NostrForegroundService : Service() {

    companion object {
        private const val TAG = "NostrForegroundService"
        const val CHANNEL_ID = "orbis_nostr_service"
        private const val NOTIFICATION_ID = 40401

        fun start(context: Context) {
            try {
                NostrSyncManager.getInstance(context).start()
                val intent = Intent(context, NostrForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "NostrForegroundService démarré avec succès pour écoute en arrière-plan")
            } catch (e: Exception) {
                Log.w(TAG, "Impossible de démarrer NostrForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, NostrForegroundService::class.java)
                context.stopService(intent)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
                Log.d(TAG, "Arrêt et suppression de la notification permanente NostrForegroundService")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur arrêt NostrForegroundService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepAliveJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NostrForegroundService onCreate()")
        createNotificationChannel()
        promoteToForeground()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "NostrForegroundService onStartCommand()")
        promoteToForeground()

        // Assurer que le moteur Nostr est démarré
        NostrSyncManager.getInstance(this).start()

        // Lancer la boucle de surveillance keep-alive en veille
        startStandbyWatchdog()

        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = buildPersistentNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (_: Throwable) {
                    try {
                        @Suppress("DEPRECATION")
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } catch (_: Throwable) {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    @Suppress("DEPRECATION")
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (_: Throwable) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Cannot promote to foreground service: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (fatal: Throwable) {
                Log.e(TAG, "Fatal foreground service promotion failure: ${fatal.message}")
                stopSelf()
            }
        }
    }

    private fun buildPersistentNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = getString(R.string.nostr_fg_service_title)
        val content = getString(R.string.nostr_fg_service_desc)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.nostr_fg_service_channel_name)
            val desc = getString(R.string.nostr_fg_service_channel_desc)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = desc
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "Écran allumé / Déverrouillé -> Vérification immédiate du maillage Nostr")
                        serviceScope.launch {
                            val pool = RelayPoolManager.getInstance(this@NostrForegroundService)
                            val health = pool.poolHealth.value
                            if (health.connectedCount == 0) {
                                NostrSyncManager.getInstance(this@NostrForegroundService).reconnect(force = true)
                            } else {
                                NostrSyncManager.getInstance(this@NostrForegroundService).reconnect(force = false)
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    /**
     * Surveille périodiquement l'état des relais quand l'appareil est en veille.
     * Si tous les relais sont déconnectés (ex: coupure réseau, changement d'antenne),
     * réveille brièvement le CPU pour forcer la reconnexion.
     */
    private fun startStandbyWatchdog() {
        keepAliveJob?.cancel()
        keepAliveJob = serviceScope.launch {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            while (isActive) {
                delay(45_000L) // Vérification toutes les 45 secondes
                try {
                    val pool = RelayPoolManager.getInstance(this@NostrForegroundService)
                    val health = pool.poolHealth.value
                    if (health.connectedCount == 0) {
                        Log.w(TAG, "Watchdog en veille: 0 relais connectés. Reconnexion forcée...")
                        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OrbisNet:WatchdogLock")
                        try {
                            wakeLock?.acquire(3000L)
                            NostrSyncManager.getInstance(this@NostrForegroundService).reconnect(force = true)
                        } finally {
                            if (wakeLock?.isHeld == true) {
                                wakeLock.release()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur lors du cycle watchdog veille: ${e.message}")
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved: L'application a été balayée du launcher / recent apps")
        try {
            // 1. Programmer une alarme de réveil pour garantir le maintien ou redémarrage du service
            val restartIntent = Intent(applicationContext, NostrBootReceiver::class.java).apply {
                action = NostrBootReceiver.ACTION_RESTART_NOSTR_SERVICE
                setPackage(packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                40402,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            val triggerTime = android.os.SystemClock.elapsedRealtime() + 1000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager?.set(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            // 2. Déclencher un travail immédiat WorkManager en filet de sécurité
            NostrSyncWorker.scheduleImmediate(applicationContext)

            // 3. Réactiver la connexion active du pool
            start(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onTaskRemoved: ", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "NostrForegroundService onDestroy()")
        keepAliveJob?.cancel()
        serviceScope.cancel()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        screenReceiver = null
    }
}
