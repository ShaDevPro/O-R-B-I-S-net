package com.sha.orbis.call.diagnostic

import android.content.Context
import android.content.SharedPreferences

/**
 * CallDiagnosticSettings — Stockage persistant des ajustements et auto-réparations
 * appliqués par le moteur de diagnostic pour garantir l'universalité des appels.
 */
object CallDiagnosticSettings {

    private const val PREFS_NAME = "orbis_call_diagnostic_prefs"
    private const val KEY_FORCE_CAMERA1 = "force_camera1_fallback"
    private const val KEY_FORCE_SOFTWARE_CODECS = "force_software_codecs"
    private const val KEY_FORCE_TURN_RELAY = "force_turn_relay"
    private const val KEY_LAST_DIAGNOSTIC_TIMESTAMP = "last_diag_timestamp"
    private const val KEY_LAST_DIAGNOSTIC_ALL_PASSED = "last_diag_all_passed"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isForceCamera1(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_FORCE_CAMERA1, false)

    fun setForceCamera1(context: Context, force: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FORCE_CAMERA1, force).apply()
    }

    fun isForceSoftwareCodecs(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_FORCE_SOFTWARE_CODECS, false)

    fun setForceSoftwareCodecs(context: Context, force: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FORCE_SOFTWARE_CODECS, force).apply()
    }

    fun isForceTurnRelay(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_FORCE_TURN_RELAY, false)

    fun setForceTurnRelay(context: Context, force: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FORCE_TURN_RELAY, force).apply()
    }

    fun setLastDiagnosticResult(context: Context, allPassed: Boolean) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_DIAGNOSTIC_TIMESTAMP, System.currentTimeMillis())
            .putBoolean(KEY_LAST_DIAGNOSTIC_ALL_PASSED, allPassed)
            .apply()
    }

    fun getLastDiagnosticTimestamp(context: Context): Long =
        getPrefs(context).getLong(KEY_LAST_DIAGNOSTIC_TIMESTAMP, 0L)

    fun isLastDiagnosticAllPassed(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_LAST_DIAGNOSTIC_ALL_PASSED, false)

    fun resetAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
