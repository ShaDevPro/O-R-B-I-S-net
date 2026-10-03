package com.sha.orbis.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.sha.orbis.call.diagnostic.BackgroundDozeProbe
import com.sha.orbis.security.BatteryOptimizationHelper
import com.sha.orbis.security.DeviceManufacturer
import com.sha.orbis.security.OEMDiagnosticHelper

/**
 * Moteur logique centralisé pour la vérification et l'activation des deux autorisations vitales :
 * 1. Exemption de batterie (Doze Mode)
 * 2. Réveil de l'écran pendant les appels entrants (Plein écran & Lockscreen)
 * 3. Réglages spécifiques constructeurs (Xiaomi, Vivo, Huawei, Oppo, etc.)
 */
object CallReadinessHelper {

    /**
     * Vérifie si Orbis est exempté de l'optimisation de batterie.
     */
    fun isBatteryExempt(context: Context): Boolean {
        return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    /**
     * Vérifie si Orbis peut réveiller l'écran en plein écran lors d'un appel.
     * Sur Android 14+ (API 34), vérifie canUseFullScreenIntent().
     * Sur les versions antérieures, les Window Flags et canaux de notification haute importance gèrent le réveil.
     */
    fun isScreenWakeGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManagerCompat.from(context).canUseFullScreenIntent()
        } else {
            true
        }
    }

    /**
     * Détecte le constructeur de l'appareil.
     */
    fun detectManufacturer(): DeviceManufacturer {
        return OEMDiagnosticHelper.detectManufacturer()
    }

    /**
     * Renvoie le nom d'affichage lisible du constructeur (ex: "Xiaomi", "Samsung", "Vivo").
     */
    fun getManufacturerDisplayName(): String {
        val brand = (Build.BRAND ?: "").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val mfr = (Build.MANUFACTURER ?: "").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return when (detectManufacturer()) {
            DeviceManufacturer.XIAOMI -> "Xiaomi (MIUI / HyperOS)"
            DeviceManufacturer.SAMSUNG -> "Samsung"
            DeviceManufacturer.VIVO -> "Vivo"
            DeviceManufacturer.HUAWEI -> "Huawei"
            DeviceManufacturer.HONOR -> "Honor"
            DeviceManufacturer.OPPO_REALME -> if (brand.isNotBlank()) brand else "Oppo / Realme"
            DeviceManufacturer.GENERIC -> if (brand.isNotBlank()) brand else mfr.ifBlank { "Android" }
        }
    }

    /**
     * Détermine si le constructeur requiert une configuration d'arrière-plan agressive (ex: Xiaomi, Vivo, Huawei).
     */
    fun isOemSpecificRequired(): Boolean {
        val mfr = detectManufacturer()
        return mfr in listOf(
            DeviceManufacturer.XIAOMI,
            DeviceManufacturer.VIVO,
            DeviceManufacturer.HUAWEI,
            DeviceManufacturer.HONOR,
            DeviceManufacturer.OPPO_REALME
        )
    }

    /**
     * Vérifie si les 2 autorisations vitales fondamentales sont actives.
     */
    fun isAllRequiredReady(context: Context): Boolean {
        return isBatteryExempt(context) && isScreenWakeGranted(context)
    }

    /**
     * Lance la demande native d'exemption d'optimisation batterie.
     */
    fun requestBatteryExemption(context: Context) {
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context, force = true)
    }
    private const val PREFS_NAME = "call_readiness_prefs"
    private const val KEY_PROMPTED_VERSION = "prompted_readiness_version"
    // Version 3 correspond au déploiement universel du flow d'appels & autonomie (mise à jour & onboarding)
    private const val CURRENT_READINESS_VERSION = 3

    /**
     * Détermine si le flux de préparation des appels doit être proposé au lancement.
     * S'exécute aussi bien lors d'une première installation que lors d'une MISE À JOUR par-dessus une ancienne version,
     * sur TOUS les téléphones (ROMs globales et ROMs constructeurs).
     */
    fun shouldPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastPromptedVersion = prefs.getInt(KEY_PROMPTED_VERSION, 0)

        // Si l'utilisateur a déjà complété ou ignoré ce flow pour la version courante (ou supérieure)
        if (lastPromptedVersion >= CURRENT_READINESS_VERSION) {
            return false
        }

        return true
    }

    /**
     * Marque le flow comme complété ou reporté ("Plus tard") pour cette version.
     */
    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PROMPTED_VERSION, CURRENT_READINESS_VERSION)
            .apply()

        // Synchroniser également avec l'ancien OemAutoStartHelper pour cohérence
        try {
            com.sha.orbis.security.OemAutoStartHelper.markPrompted(context)
        } catch (_: Throwable) {}
    }

    /**
     * Réinitialise l'état pour les diagnostics ou si l'utilisateur souhaite relancer la configuration.
     */
    fun resetPrompted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PROMPTED_VERSION)
            .apply()
    }

    /**
     * Ouvre directement la page système d'autorisation de réveil plein écran ou gestion lockscreen.
     */
    fun requestScreenWake(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            BackgroundDozeProbe.openFullScreenIntentSettings(context)
            return
        }

        // Sur Xiaomi (MIUI / HyperOS) : tente d'ouvrir l'éditeur de permissions pour cocher
        // "Afficher sur l'écran de verrouillage" et "Afficher des fenêtres contextuelles en arrière-plan"
        if (detectManufacturer() == DeviceManufacturer.XIAOMI) {
            val xiaomiIntents = listOf(
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    putExtra("extra_pkgname", context.packageName)
                },
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                    putExtra("extra_pkgname", context.packageName)
                },
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    putExtra("extra_pkgname", context.packageName)
                }
            )
            for (intent in xiaomiIntents) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
        }

        // Fallback générique : paramètres de l'application
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Ouvre le gestionnaire d'autostart / lancement arrière-plan du constructeur (Vivo, Xiaomi, Huawei, etc.).
     */
    fun requestOemAutostart(context: Context): Boolean {
        return OEMDiagnosticHelper.openOEMAutostartSettings(context)
    }
}
