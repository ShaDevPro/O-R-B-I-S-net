package com.sha.orbis.call.diagnostic

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.sha.orbis.call.OrbisCallNotificationHelper
import com.sha.orbis.security.BatteryOptimizationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sonde de diagnostic pour la résilience en arrière-plan (Doze mode, canal de notification d'appel).
 */
object BackgroundDozeProbe {

    private const val TAG = "BackgroundDozeProbe"

    suspend fun checkBackgroundAndDoze(context: Context): DiagnosticStepResult = withContext(Dispatchers.IO) {
        val isBatteryExempted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)

        // 1. Vérification du canal de notification d'appel
        var isChannelOk = true
        var channelDetail = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = nm?.getNotificationChannel(OrbisCallNotificationHelper.CHANNEL_CALLS)
            if (channel == null) {
                isChannelOk = false
                channelDetail = "Canal de notification d'appel absent."
            } else if (channel.importance < NotificationManager.IMPORTANCE_HIGH) {
                isChannelOk = false
                channelDetail = "Importance du canal d'appel insuffisante (${channel.importance} < IMPORTANCE_HIGH)."
            }
        }

        // 2. Vérification Full Screen Intent (Android 14+)
        var isFullScreenOk = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            isFullScreenOk = NotificationManagerCompat.from(context).canUseFullScreenIntent()
        }

        return@withContext when {
            isBatteryExempted && isChannelOk && isFullScreenOk -> {
                DiagnosticStepResult(
                    stepId = DiagnosticStepId.BACKGROUND_DOZE,
                    status = DiagnosticStatus.SUCCESS,
                    detail = "Exemption batterie active, canal de sonnerie configuré en priorité maximale (écran de veille déverrouillé)."
                )
            }
            !isChannelOk -> {
                DiagnosticStepResult(
                    stepId = DiagnosticStepId.BACKGROUND_DOZE,
                    status = DiagnosticStatus.WARNING,
                    detail = "$channelDetail Les appels entrants risquent de ne pas sonner à l'écran.",
                    canAutoRepair = true
                )
            }
            !isBatteryExempted -> {
                DiagnosticStepResult(
                    stepId = DiagnosticStepId.BACKGROUND_DOZE,
                    status = DiagnosticStatus.WARNING,
                    detail = "Optimisation de batterie activée : l'OS peut geler OrbisNet en veille et retarder les appels.",
                    canAutoRepair = true
                )
            }
            else -> {
                DiagnosticStepResult(
                    stepId = DiagnosticStepId.BACKGROUND_DOZE,
                    status = DiagnosticStatus.WARNING,
                    detail = "Autorisation d'affichage d'appel plein écran restreinte par Android 14+.",
                    canAutoRepair = true
                )
            }
        }
    }

    /**
     * Répare le canal de notification d'appel.
     */
    fun repairNotificationChannel(context: Context): Boolean {
        return try {
            OrbisCallNotificationHelper.ensureCallChannel(context)
            Log.i(TAG, "Canal de notification d'appel recréé avec succès.")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Erreur réparation canal notif: ${e.message}")
            false
        }
    }

    /**
     * Ouvre directement la page système des paramètres Android 14+ pour accorder
     * l'autorisation d'affichage plein écran des notifications d'appel (USE_FULL_SCREEN_INTENT).
     */
    fun openFullScreenIntentSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Throwable) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Throwable) {}
        }
    }
}
