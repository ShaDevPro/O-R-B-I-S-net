package com.sha.orbis.ai.core

import android.content.Context
import androidx.core.content.edit

/**
 * Manages user activation preferences for ORBIS AI LLMs.
 * Both ORBIS Guard-LLM and ORBIS Reply-LLM are disabled by default upon first installation.
 */
class OrbisAiPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "orbis_ai_preferences"
        private const val KEY_GUARD_ENABLED = "guard_llm_enabled"
        private const val KEY_REPLY_ENABLED = "reply_llm_enabled"
    }

    /**
     * Is ORBIS Guard-LLM enabled for SMS spam & cyber threat protection.
     * Default: false (disabled by default on fresh install).
     */
    var isGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_GUARD_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_GUARD_ENABLED, value) }

    /**
     * Is ORBIS Reply-LLM enabled for smart contextual reply suggestions.
     * Default: false (disabled by default on fresh install).
     */
    var isReplyEnabled: Boolean
        get() = prefs.getBoolean(KEY_REPLY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_REPLY_ENABLED, value) }

    /**
     * Convenience property checking if all AI engines are enabled.
     */
    val isMasterEnabled: Boolean
        get() = isGuardEnabled && isReplyEnabled

    /**
     * Enables or disables all AI engines simultaneously.
     */
    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_GUARD_ENABLED, enabled)
            putBoolean(KEY_REPLY_ENABLED, enabled)
        }
    }

    /**
     * Returns a summary count of active engines: 0, 1, or 2.
     */
    fun getActiveEnginesCount(): Int {
        var count = 0
        if (isGuardEnabled) count++
        if (isReplyEnabled) count++
        return count
    }
}
