package com.sha.orbis.nostr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sha.orbis.data.SessionManager

/**
 * Démarre le service Nostr dès le redémarrage du smartphone ou la mise à jour de l'application
 * si l'utilisateur est authentifié.
 */
class NostrBootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NostrBootReceiver"
        const val ACTION_RESTART_NOSTR_SERVICE = "com.sha.orbis.nostr.RESTART_SERVICE"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        Log.d(TAG, "Reçu broadcast action: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == ACTION_RESTART_NOSTR_SERVICE) {
            val session = SessionManager(context)
            if (session.isAuthenticated || session.isOnboardingCompleted || session.userPhone.isNotBlank()) {
                Log.i(TAG, "Démarrage/Redémarrage de NostrForegroundService (action: $action)")
                NostrForegroundService.start(context)
                NostrSyncWorker.schedulePeriodic(context)
                NostrConnectivityMonitor.start(context)
                NostrSyncManager.getInstance(context).reconnect(force = true)
            }
        }
    }
}
