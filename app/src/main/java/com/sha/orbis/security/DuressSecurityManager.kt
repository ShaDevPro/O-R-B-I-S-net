package com.sha.orbis.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

object DuressSecurityManager {

    private const val PREFS_NAME = "orbis_security_pin_prefs"
    private const val KEY_PIN_HASH = "key_main_pin_hash"
    private const val KEY_DURESS_PIN_HASH = "key_duress_pin_hash"
    private const val KEY_IS_PIN_ENABLED = "key_is_pin_enabled"
    private const val KEY_IS_DECOY_ACTIVE = "key_is_decoy_active"

    enum class PinValidationResult {
        VALID_MAIN,
        VALID_DURESS,
        INVALID
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isPinEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_PIN_ENABLED, false)
    }

    fun isDecoyActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_DECOY_ACTIVE, false)
    }

    fun resetDecoyMode(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_IS_DECOY_ACTIVE, false).apply()
    }

    fun disablePin(context: Context) {
        setSecurityPins(context, "", null)
    }

    fun setSecurityPins(context: Context, mainPin: String, duressPin: String?) {
        val editor = getPrefs(context).edit()
        if (mainPin.isBlank()) {
            editor.putBoolean(KEY_IS_PIN_ENABLED, false)
            editor.remove(KEY_PIN_HASH)
            editor.remove(KEY_DURESS_PIN_HASH)
        } else {
            editor.putBoolean(KEY_IS_PIN_ENABLED, true)
            editor.putString(KEY_PIN_HASH, hashString(mainPin))
            if (!duressPin.isNullOrBlank()) {
                editor.putString(KEY_DURESS_PIN_HASH, hashString(duressPin))
            } else {
                editor.remove(KEY_DURESS_PIN_HASH)
            }
        }
        editor.apply()
    }

    fun validatePin(context: Context, inputPin: String): PinValidationResult {
        val prefs = getPrefs(context)
        val mainHash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        val duressHash = prefs.getString(KEY_DURESS_PIN_HASH, "") ?: ""
        val inputHash = hashString(inputPin)

        return when {
            mainHash.isNotEmpty() && inputHash == mainHash -> {
                prefs.edit().putBoolean(KEY_IS_DECOY_ACTIVE, false).apply()
                PinValidationResult.VALID_MAIN
            }
            duressHash.isNotEmpty() && inputHash == duressHash -> {
                prefs.edit().putBoolean(KEY_IS_DECOY_ACTIVE, true).apply()
                try {
                    com.sha.orbis.telemetry.TelemetryManager.getInstance(context)
                        .recordEvent(com.sha.orbis.telemetry.FeatureType.SOS_DURESS)
                } catch (_: Exception) {}
                PinValidationResult.VALID_DURESS
            }
            else -> PinValidationResult.INVALID
        }
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
