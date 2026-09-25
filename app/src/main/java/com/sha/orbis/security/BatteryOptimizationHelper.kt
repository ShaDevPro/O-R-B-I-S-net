package com.sha.orbis.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    fun shouldPrompt(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) return false
        val prefs = context.getSharedPreferences("battery_optim_prefs", Context.MODE_PRIVATE)
        return !prefs.getBoolean("has_prompted", false)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context, force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isIgnoringBatteryOptimizations(context)) {
                if (force) {
                    Toast.makeText(context, "L'optimisation batterie est déjà désactivée pour Orbis.", Toast.LENGTH_SHORT).show()
                }
                return
            }
            val prefs = context.getSharedPreferences("battery_optim_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("has_prompted", true).apply()
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Impossible d'ouvrir les paramètres batterie : ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
