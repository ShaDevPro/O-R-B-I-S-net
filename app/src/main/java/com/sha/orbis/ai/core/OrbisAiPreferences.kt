package com.sha.orbis.ai.core

import android.content.Context
import androidx.core.content.edit

/**
 * Manages user activation preferences for ORBIS AI LLMs.
 * Both ORBIS Guard-LLM and ORBIS Reply-LLM are enabled by default upon first installation.
 */
class OrbisAiPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "orbis_ai_preferences"
        private const val KEY_GUARD_ENABLED = "guard_llm_enabled"
        private const val KEY_REPLY_ENABLED = "reply_llm_enabled"
        private const val KEY_DRIVING_AUTO_DECLINE = "driving_auto_decline_enabled"
        private const val KEY_DND_AUTO_DECLINE = "dnd_auto_decline_enabled"
        private const val KEY_SMART_RECALL = "smart_recall_enabled"
        private const val KEY_SILENT_BURST_SHIELD = "silent_burst_shield_enabled"
    }

    /**
     * Is Auto-decline when Driving Mode active enabled.
     * Default: false (user opt-in).
     */
    var isDrivingAutoDeclineEnabled: Boolean
        get() = prefs.getBoolean(KEY_DRIVING_AUTO_DECLINE, false)
        set(value) = prefs.edit { putBoolean(KEY_DRIVING_AUTO_DECLINE, value) }

    /**
     * Is Auto-decline when system Do Not Disturb active enabled.
     * Default: false (user opt-in).
     */
    var isDndAutoDeclineEnabled: Boolean
        get() = prefs.getBoolean(KEY_DND_AUTO_DECLINE, false)
        set(value) = prefs.edit { putBoolean(KEY_DND_AUTO_DECLINE, value) }

    /**
     * Is Smart Recall protection for off-hours (22h-08h) enabled.
     * Default: true.
     */
    var isSmartRecallEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_RECALL, true)
        set(value) = prefs.edit { putBoolean(KEY_SMART_RECALL, value) }

    /**
     * Is Silent Burst & Spam shielding enabled (instantly muting suspicious call storms).
     * Default: true.
     */
    var isSilentBurstShieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_SILENT_BURST_SHIELD, true)
        set(value) = prefs.edit { putBoolean(KEY_SILENT_BURST_SHIELD, value) }

    /**
     * Is ORBIS Guard-LLM enabled for spam & cyber threat protection.
     * Default: true (enabled by default on fresh install).
     */
    var isGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_GUARD_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_GUARD_ENABLED, value) }

    /**
     * Is ORBIS Reply-LLM enabled for smart contextual reply suggestions.
     * Default: true (enabled by default on fresh install).
     */
    var isReplyEnabled: Boolean
        get() = prefs.getBoolean(KEY_REPLY_ENABLED, true)
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
