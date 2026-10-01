package com.sha.orbis

import android.app.Application
import com.sha.orbis.data.SessionManager
import com.sha.orbis.nostr.service.NostrConnectivityMonitor
import com.sha.orbis.nostr.service.NostrForegroundService
import com.sha.orbis.nostr.service.NostrSyncManager
import com.sha.orbis.nostr.service.NostrSyncWorker
import com.sha.orbis.ui.i18n.LocaleManager

class OrbisApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Capture globale souveraine des crashs inattendus pour diagnostic immédiat
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("ORBIS_CRASH", "FATAL CRASH DETECTED on thread ${thread.name}:", throwable)
            try {
                val diagDir = getExternalFilesDir("diagnostics") ?: filesDir
                diagDir.mkdirs()
                val crashFile = java.io.File(diagDir, "last_crash.txt")
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                val crashReport = "=== ORBIS CRASH REPORT ===\nDate: ${java.util.Date()}\nThread: ${thread.name}\nError: ${throwable::class.java.name}: ${throwable.message}\n\nStacktrace:\n$sw\n"
                crashFile.writeText(crashReport)
                com.sha.orbis.call.diagnostic.FeedDebugTracker.logEvent("FATAL_CRASH", "${throwable::class.java.simpleName}: ${throwable.message}")
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        LocaleManager.applySavedLocale(this)
        com.sha.orbis.admin.AdminSecurityHelper.init(this)

        // Désactivation de la composition pausable expérimentale dans le préchargement LazyLayout
        // Élimine définitivement le bug Compose: "measure is called on a deactivated node" (Google Issue 372509991 / 482223006)
        try {
            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            androidx.compose.foundation.ComposeFoundationFlags.isPausableCompositionInPrefetchEnabled = false
        } catch (_: Throwable) {}

        com.sha.orbis.notification.ActiveConversationTracker.init(this)

        // FCM : Récupération et enregistrement du jeton au démarrage
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (!token.isNullOrBlank()) {
                            com.sha.orbis.call.fcm.OrbisFirebaseMessagingService.saveToken(this, token)
                            android.util.Log.i("OrbisApplication", "Jeton FCM prêt : ${token.take(12)}...")
                        }
                    }
                }
        } catch (e: Exception) {
            android.util.Log.w("OrbisApplication", "Erreur init FCM token: ${e.message}")
        }

        // Service d'arrière-plan résilient pour écoute 24/7 (appels & messages en veille)
        val session = SessionManager(this)
        if (session.isAuthenticated || session.userPhone.isNotBlank()) {
            NostrForegroundService.start(this)
        } else {
            NostrSyncManager.getInstance(this).start()
        }

        NostrConnectivityMonitor.start(this)
        NostrSyncWorker.schedulePeriodic(this)
        com.sha.orbis.sync.scheduler.SovereignSyncScheduler.schedulePeriodicSync(this)

        // Sovereign Cache & Storage Maintenance
        com.sha.orbis.cache.OrbisCacheCoordinator.triggerBackgroundMaintenance(this)

        // Native Android Telecom (Core-Telecom VoIP registration) & Call Lifecycle
        com.sha.orbis.call.OrbisCallManager.init(this)
        com.sha.orbis.call.telecom.OrbisTelecomHelper.registerPhoneAccount(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.sha.orbis.cache.OrbisCacheCoordinator.onTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        com.sha.orbis.cache.OrbisCacheCoordinator.clearAllMemoryCaches()
    }
}

