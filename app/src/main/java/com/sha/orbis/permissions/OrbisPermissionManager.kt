package com.sha.orbis.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sha.orbis.security.BatteryOptimizationHelper
import com.sha.orbis.security.OEMDiagnosticHelper

/**
 * OrbisPermissionManager — Moteur centralisé et universel pour l'audit et la gestion des autorisations.
 */
object OrbisPermissionManager {

    /**
     * Vérifie si toutes les permissions sous-jacentes d'une [OrbisPermission] sont accordées.
     */
    fun isGranted(context: Context, permission: OrbisPermission): Boolean {
        if (permission == OrbisPermission.BATTERY_OPTIMIZATION) {
            return isBatteryOptimizationIgnored(context)
        }

        val manifestPerms = permission.getManifestPermissions()
        if (manifestPerms.isEmpty()) return true

        return manifestPerms.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Renvoie la liste des permissions manifestes encore manquantes pour cette [OrbisPermission].
     */
    fun getMissingPermissions(context: Context, permission: OrbisPermission): Array<String> {
        if (permission == OrbisPermission.BATTERY_OPTIMIZATION) return emptyArray()

        return permission.getManifestPermissions().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    /**
     * Évalue le statut précis d'une permission (Granted, Denied avec Rationale, ou PermanentlyDenied).
     */
    fun getStatus(activity: Activity, permission: OrbisPermission): OrbisPermissionStatus {
        if (permission == OrbisPermission.BATTERY_OPTIMIZATION) {
            return if (isBatteryOptimizationIgnored(activity)) {
                OrbisPermissionStatus.Granted
            } else {
                OrbisPermissionStatus.Denied(shouldShowRationale = true)
            }
        }

        val manifestPerms = permission.getManifestPermissions()
        if (manifestPerms.isEmpty()) return OrbisPermissionStatus.NotApplicable

        val missing = getMissingPermissions(activity, permission)
        if (missing.isEmpty()) return OrbisPermissionStatus.Granted

        // Vérifier si l'OS permet encore d'afficher l'explication native (shouldShowRequestPermissionRationale)
        val shouldShowRationale = missing.any { perm ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }

        val prefs = activity.getSharedPreferences("orbis_perm_audit", Context.MODE_PRIVATE)
        val hasRequestedBefore = missing.any { perm ->
            prefs.getBoolean("req_$perm", false)
        }

        return if (!shouldShowRationale && hasRequestedBefore) {
            OrbisPermissionStatus.PermanentlyDenied
        } else {
            OrbisPermissionStatus.Denied(shouldShowRationale = shouldShowRationale)
        }
    }

    /**
     * Marque qu'une demande d'autorisation a été tentée auprès de l'OS.
     */
    fun markPermissionRequested(context: Context, permissions: Array<String>) {
        val prefs = context.getSharedPreferences("orbis_perm_audit", Context.MODE_PRIVATE)
        prefs.edit().apply {
            permissions.forEach { perm ->
                putBoolean("req_$perm", true)
            }
        }.apply()
    }

    /**
     * Redirige directement vers la page des paramètres de l'application dans Android Settings.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    /**
     * Déclenche la demande de dérogation d'optimisation batterie.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context, force = true)
    }

    /**
     * Vérifie si l'application est exemptée d'optimisation de batterie.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    /**
     * Ouvre les réglages d'autostart spécifiques au constructeur (Vivo, Xiaomi, Honor, Huawei, Samsung).
     */
    fun openOEMAutostartSettings(context: Context): Boolean {
        return OEMDiagnosticHelper.openOEMAutostartSettings(context)
    }

    /**
     * Liste des permissions recommandées pour l'onboarding initial (Option A : Progressive).
     */
    fun getEssentialOnboardingPermissions(): List<OrbisPermission> = listOf(
        OrbisPermission.SIM_TELEPHONY,
        OrbisPermission.NOTIFICATIONS,
        OrbisPermission.RECORD_AUDIO,
        OrbisPermission.CONTACTS
    )
}
