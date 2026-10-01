package com.sha.orbis.sync.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sha.orbis.sync.engine.SovereignPeerSyncEngine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Worker d'arrière-plan périodique (WorkManager) pour la synchronisation souveraine
 * inter-contacts OrbisNet. Garantit la fraîcheur des données même en veille.
 */
class SovereignPeerSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Exécution du worker de synchronisation souveraine inter-amis...")
        return try {
            suspendCancellableCoroutine { cont ->
                SovereignPeerSyncEngine.getInstance(applicationContext).syncWithAllFriends("workmanager_worker") {
                    if (cont.isActive) {
                        cont.resume(Result.success())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Échec du worker de synchronisation souveraine: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SovereignPeerSyncWorker"
        private const val UNIQUE_WORK_NAME = "orbis_sovereign_peer_sync_periodic"

        fun schedulePeriodic(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<SovereignPeerSyncWorker>(
                    20, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Log.i(TAG, "WorkManager de synchronisation souveraine planifié (intervalle: 20 min)")
            } catch (e: Exception) {
                Log.w(TAG, "Erreur planification WorkManager souverain: ${e.message}")
            }
        }
    }
}
