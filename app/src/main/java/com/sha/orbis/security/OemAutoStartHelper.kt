package com.sha.orbis.security

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * OemAutoStartHelper — Résout le problème des tueurs de tâches constructeurs (OEM).
 *
 * Sur Xiaomi (MIUI/HyperOS), Huawei (EMUI), Samsung (OneUI), Oppo (ColorOS) et Vivo,
 * désactiver l'optimisation batterie Android standard ne suffit PAS :
 * le système constructeur bloque les services d'arrière-plan et tue les sockets réseau
 * dès que l'écran s'éteint, sauf si l'autorisation « Démarrage automatique » propriétaire
 * est activée par l'utilisateur.
 *
 * Les géants (WhatsApp, FB, Instagram) ont leur package en liste blanche usine dans les ROMs.
 * Les applications souveraines tierces (Telegram, Signal, Orbis) doivent orienter l'utilisateur
 * directement vers la page de réglage dédiée du constructeur.
 */
object OemAutoStartHelper {

    private const val TAG = "OemAutoStartHelper"
    private const val PREFS_NAME = "oem_autostart_prefs"
    private const val KEY_PROMPTED = "has_prompted_autostart"

    enum class OemBrand {
        XIAOMI,
        SAMSUNG,
        HUAWEI,
        OPPO,
        VIVO,
        ONEPLUS,
        TRANSSION, // Infinix, Tecno, Itel
        ASUS,
        STANDARD
    }

    /**
     * Détecte la marque du smartphone.
     */
    fun getDeviceBrand(): OemBrand {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                    manufacturer.contains("poco") || brand.contains("xiaomi") ||
                    brand.contains("redmi") || brand.contains("poco") -> OemBrand.XIAOMI

            manufacturer.contains("samsung") -> OemBrand.SAMSUNG

            manufacturer.contains("huawei") || manufacturer.contains("honor") ||
                    brand.contains("huawei") || brand.contains("honor") -> OemBrand.HUAWEI

            manufacturer.contains("oppo") || manufacturer.contains("realme") ||
                    brand.contains("oppo") || brand.contains("realme") -> OemBrand.OPPO

            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ||
                    brand.contains("vivo") || brand.contains("iqoo") -> OemBrand.VIVO

            manufacturer.contains("oneplus") -> OemBrand.ONEPLUS

            manufacturer.contains("transsion") || manufacturer.contains("infinix") ||
                    manufacturer.contains("tecno") || manufacturer.contains("itel") -> OemBrand.TRANSSION

            manufacturer.contains("asus") -> OemBrand.ASUS

            else -> OemBrand.STANDARD
        }
    }

    /**
     * Retourne true si l'appareil appartient à un constructeur avec tueur de tâches agressif.
     */
    fun isAggressiveOem(): Boolean {
        return getDeviceBrand() != OemBrand.STANDARD
    }

    /**
     * Vérifie si le dialogue explicatif constructeur doit être proposé à l'utilisateur.
     */
    fun shouldPrompt(context: Context): Boolean {
        if (!isAggressiveOem()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(KEY_PROMPTED, false)
    }

    /**
     * Marque la boîte de dialogue comme déjà affichée pour éviter d'importuner l'utilisateur.
     */
    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROMPTED, true)
            .apply()
    }

    /**
     * Tente d'ouvrir directement l'écran de réglage d'autostart spécifique au constructeur.
     * En cas d'échec de tous les composants constructeur, bascule sur les détails de l'application.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        markPrompted(context)
        val brand = getDeviceBrand()
        val intentList = getIntentsForBrand(context, brand)

        for (intent in intentList) {
            try {
                if (isIntentCallable(context, intent)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.i(TAG, "Écran d'autostart constructeur ouvert : ${intent.component}")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Échec tentative intent: ${e.message}")
            }
        }

        // Repli universel : paramètres système de l'application
        return openAppDetailsSettings(context)
    }

    private fun getIntentsForBrand(context: Context, brand: OemBrand): List<Intent> {
        val intents = mutableListOf<Intent>()

        when (brand) {
            OemBrand.XIAOMI -> {
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")))
                intents.add(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.securityscan.MainActivity")))
            }
            OemBrand.SAMSUNG -> {
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")))
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")))
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")))
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            }
            OemBrand.HUAWEI -> {
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
            }
            OemBrand.OPPO -> {
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.coloros.phonemanager", "com.coloros.phonemanager.MainActivity")))
            }
            OemBrand.VIVO -> {
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")))
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")))
            }
            OemBrand.ONEPLUS -> {
                intents.add(Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")))
            }
            OemBrand.TRANSSION -> {
                intents.add(Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.itel.autostart.AutoStartListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.MainActivity")))
            }
            OemBrand.ASUS -> {
                intents.add(Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")))
            }
            OemBrand.STANDARD -> {}
        }

        return intents
    }

    private fun isIntentCallable(context: Context, intent: Intent): Boolean {
        val list = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return list.isNotEmpty()
    }

    private fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Impossible d'ouvrir les détails de l'application: ${e.message}")
            false
        }
    }

    /**
     * Fournit le texte d'instruction personnalisé selon la marque détectée.
     */
    fun getBrandInstructions(context: Context): String {
        return when (getDeviceBrand()) {
            OemBrand.XIAOMI -> "Sur Xiaomi/Redmi/Poco : activez « Démarrage automatique » et définissez l'économiseur de batterie sur « Pas de restrictions »."
            OemBrand.SAMSUNG -> "Sur Samsung : désactivez la « Mise en veille profonde » pour OrbisNet dans les paramètres de batterie."
            OemBrand.HUAWEI -> "Sur Huawei/Honor : réglez le lancement d'OrbisNet sur « Gérer manuellement » et activez toutes les options."
            OemBrand.OPPO -> "Sur Oppo/Realme : autorisez le « Démarrage automatique » et l'exécution en arrière-plan dans Gestionnaire du téléphone."
            OemBrand.VIVO -> "Sur Vivo : activez l'autorisation d'exécution en arrière-plan dans i Manager."
            OemBrand.ONEPLUS -> "Sur OnePlus : activez le démarrage automatique et désactivez l'optimisation de batterie."
            else -> "Autorisez le fonctionnement en arrière-plan et le démarrage automatique pour ne manquer aucun appel."
        }
    }
}
