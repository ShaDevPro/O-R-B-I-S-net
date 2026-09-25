package com.sha.orbis.nostr.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Worker d'arrière-plan périodique (WorkManager) pour la relève des messages Nostr en veille.
 * Se déclenche périodiquement quand le réseau est disponible, assure le réveil du pool
 * et la relève des messages directs (Kind 4 E2EE) manqués pendant que l'écran était éteint.
 */
class NostrSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Exécution de la synchronisation d'arrière-plan Nostr...")
        return try {
            val syncManager = NostrSyncManager.getInstance(applicationContext)
            syncManager.reconnect()
            // Laisser le temps aux WebSockets d'effectuer le handshake et de recevoir les nouveaux messages
            delay(5000L)
            Log.i(TAG, "Synchronisation d'arrière-plan Nostr terminée avec succès")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Échec du worker de synchronisation Nostr: ", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NostrSyncWorker"
        private const val UNIQUE_WORK_NAME = "orbis_nostr_periodic_sync"

        fun schedulePeriodic(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncRequest = PeriodicWorkRequestBuilder<NostrSyncWorker>(
                    15, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
                Log.i(TAG, "WorkManager Nostr périodique planifié (intervalle: 15 min)")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur planification WorkManager Nostr: ", e)
            }
        }

        fun scheduleImmediate(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val immediateRequest = androidx.work.OneTimeWorkRequestBuilder<NostrSyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "orbis_nostr_immediate_sync",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    immediateRequest
                )
                Log.i(TAG, "WorkManager Nostr immédiat planifié avec succès")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur planification WorkManager immédiat: ", e)
            }
        }
    }
}
