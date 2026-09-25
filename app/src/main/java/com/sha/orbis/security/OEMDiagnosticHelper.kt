package com.sha.orbis.security

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

enum class DeviceManufacturer {
    VIVO,
    HONOR,
    HUAWEI,
    XIAOMI,
    SAMSUNG,
    OPPO_REALME,
    GENERIC
}

data class OEMDiagnosticInfo(
    val manufacturer: DeviceManufacturer,
    val manufacturerName: String,
    val isBatteryOptimized: Boolean,
    val recommendedActions: List<String>
)

object OEMDiagnosticHelper {

    private const val TAG = "OEMDiagnosticHelper"

    fun detectManufacturer(): DeviceManufacturer {
        val brand = (Build.BRAND ?: "").lowercase()
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()

        return when {
            brand.contains("vivo") || manufacturer.contains("vivo") || brand.contains("iqoo") -> DeviceManufacturer.VIVO
            brand.contains("honor") || manufacturer.contains("honor") -> DeviceManufacturer.HONOR
            brand.contains("huawei") || manufacturer.contains("huawei") -> DeviceManufacturer.HUAWEI
            brand.contains("xiaomi") || manufacturer.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> DeviceManufacturer.XIAOMI
            brand.contains("samsung") || manufacturer.contains("samsung") -> DeviceManufacturer.SAMSUNG
            brand.contains("oppo") || manufacturer.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> DeviceManufacturer.OPPO_REALME
            else -> DeviceManufacturer.GENERIC
        }
    }

    /**
     * Tente d'ouvrir directement l'écran de gestion d'autostart / protection arrière-plan
     * propre au constructeur (Vivo, Honor, Xiaomi, Huawei, etc.).
     */
    fun openOEMAutostartSettings(context: Context): Boolean {
        val mfr = detectManufacturer()
        val intents = mutableListOf<Intent>()

        when (mfr) {
            DeviceManufacturer.VIVO -> {
                // Vivo Funtouch OS / OriginOS iManager & Permission Manager
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")).putExtra("packagename", context.packageName))
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")).putExtra("packagename", context.packageName))
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
                intents.add(Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.abe.feature.frontandback.BackgroundAppManagerActivity")))
            }
            DeviceManufacturer.HONOR, DeviceManufacturer.HUAWEI -> {
                // Honor MagicUI & Huawei EMUI Startup Management
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
            }
            DeviceManufacturer.XIAOMI -> {
                // Xiaomi MIUI / HyperOS Security Center Autostart
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
            }
            DeviceManufacturer.OPPO_REALME -> {
                // Oppo ColorOS / Realme UI Startup Manager
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
            }
            DeviceManufacturer.SAMSUNG -> {
                // Samsung Device Care / Battery
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")))
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            }
            DeviceManufacturer.GENERIC -> {}
        }

        // Fallback générique : paramètres de l'application
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })
        intents.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.i(TAG, "Opened settings via ${intent.component ?: intent.action}")
                return true
            } catch (_: Exception) {
                // Essayer le prochain intent
            }
        }
        return false
    }

    /**
     * Ouvre les paramètres d'optimisation batterie standard Android.
     */
    fun openStandardBatterySettings(context: Context) {
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
    }

    /**
     * Ouvre la page d'autorisation de réveil plein écran Android 14+ (USE_FULL_SCREEN_INTENT).
     */
    fun openFullScreenIntentSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        return false
    }
}
