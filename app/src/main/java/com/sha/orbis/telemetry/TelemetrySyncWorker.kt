package com.sha.orbis.telemetry

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class TelemetrySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Exécution de la synchronisation de télémétrie en arrière-plan...")
        return try {
            val telemetryManager = TelemetryManager.getInstance(applicationContext)
            telemetryManager.syncTelemetry()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Échec lors de la synchronisation de télémétrie: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TelemetrySyncWorker"
        private const val UNIQUE_WORK_NAME = "orbis_telemetry_periodic_sync"

        fun schedulePeriodic(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncRequest = PeriodicWorkRequestBuilder<TelemetrySyncWorker>(
                    6, TimeUnit.HOURS,
                    30, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
                Log.d(TAG, "Worker de télémétrie planifié (toutes les 6h)")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur planification worker télémétrie: ${e.message}")
            }
        }
    }
}
