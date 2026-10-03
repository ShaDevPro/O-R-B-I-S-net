package com.sha.orbis.storage

import android.content.Context
import android.content.SharedPreferences
import com.sha.orbis.security.SimVerificationService

/**
 * Manages user preferences and smart memory for Dual-SIM / Multi-SIM environments.
 * Remembers preferred SIM slot / subscription ID per contact and global default SIM.
 */
class SimPreferenceManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val simService = SimVerificationService()

    companion object {
        private const val PREFS_NAME = "orbis_sim_preferences"
        private const val KEY_GLOBAL_DEFAULT_SUB_ID = "global_default_sub_id"
        private const val KEY_GLOBAL_DEFAULT_SLOT = "global_default_slot"
        private const val PREFIX_CONTACT_SUB_ID = "pref_sub_id_"
        private const val PREFIX_CONTACT_SLOT = "pref_slot_"
    }

    /**
     * Returns the active physical SIM slots currently inserted in the device.
     */
    fun getActiveSimSlots(): List<SimVerificationService.SimSlotInfo> {
        return simService.getAllActiveSimSlots(context)
    }

    /**
     * Checks if device has 2 or more active SIM cards.
     */
    fun isDualSimActive(): Boolean {
        return getActiveSimSlots().size >= 2
    }

    /**
     * Gets the preferred subscription ID for a specific contact address.
     * Falls back to global default SIM or the first active SIM.
     */
    fun getPreferredSubIdForAddress(address: String): Int {
        val normalized = normalizeAddressKey(address)
        val savedSubId = prefs.getInt(PREFIX_CONTACT_SUB_ID + normalized, -1)
        val activeSlots = getActiveSimSlots()

        // Check if saved subId is still an active subscription
        if (savedSubId >= 0 && activeSlots.any { it.subscriptionId == savedSubId }) {
            return savedSubId
        }

        // Check global default
        val globalSubId = getGlobalDefaultSubId()
        if (globalSubId >= 0 && activeSlots.any { it.subscriptionId == globalSubId }) {
            return globalSubId
        }

        // Fallback to first available active SIM
        return activeSlots.firstOrNull()?.subscriptionId ?: -1
    }

    /**
     * Gets the explicitly saved subscription ID for an address, or -1 if no preference has been saved.
     */
    fun getSavedSubIdForAddress(address: String): Int {
        val normalized = normalizeAddressKey(address)
        val savedSubId = prefs.getInt(PREFIX_CONTACT_SUB_ID + normalized, -1)
        val activeSlots = getActiveSimSlots()
        return if (savedSubId >= 0 && activeSlots.any { it.subscriptionId == savedSubId }) {
            savedSubId
        } else {
            -1
        }
    }

    /**
     * Gets the explicitly saved slot index (0 or 1) for an address, or -1 if no preference has been saved.
     */
    fun getSavedSlotForAddress(address: String): Int {
        val normalized = normalizeAddressKey(address)
        val savedSlot = prefs.getInt(PREFIX_CONTACT_SLOT + normalized, -1)
        val activeSlots = getActiveSimSlots()
        return if (savedSlot in 0..3 && activeSlots.any { it.slotIndex == savedSlot }) {
            savedSlot
        } else {
            -1
        }
    }

    /**
     * Gets the preferred SIM slot index (0 = SIM 1, 1 = SIM 2) for a contact address.
     */
    fun getPreferredSlotForAddress(address: String): Int {
        val subId = getPreferredSubIdForAddress(address)
        val activeSlots = getActiveSimSlots()
        val matched = activeSlots.firstOrNull { it.subscriptionId == subId }
        return matched?.slotIndex ?: 0
    }

    /**
     * Saves the preferred SIM subscription ID and slot for a contact address.
     */
    fun setPreferredSimForAddress(address: String, subId: Int, slotIndex: Int) {
        val normalized = normalizeAddressKey(address)
        prefs.edit()
            .putInt(PREFIX_CONTACT_SUB_ID + normalized, subId)
            .putInt(PREFIX_CONTACT_SLOT + normalized, slotIndex)
            .apply()
    }

    /**
     * Gets the global default subscription ID.
     */
    fun getGlobalDefaultSubId(): Int {
        return prefs.getInt(KEY_GLOBAL_DEFAULT_SUB_ID, -1)
    }

    /**
     * Sets the global default subscription ID and slot.
     */
    fun setGlobalDefaultSim(subId: Int, slotIndex: Int) {
        prefs.edit()
            .putInt(KEY_GLOBAL_DEFAULT_SUB_ID, subId)
            .putInt(KEY_GLOBAL_DEFAULT_SLOT, slotIndex)
            .apply()
    }

    /**
     * Resolves slot index (0 or 1) given a subscription ID.
     */
    fun resolveSlotIndex(subId: Int): Int {
        val activeSlots = getActiveSimSlots()
        return activeSlots.firstOrNull { it.subscriptionId == subId }?.slotIndex ?: -1
    }

    /**
     * Resolves subscription ID given a slot index (0 or 1).
     */
    fun resolveSubIdForSlot(slotIndex: Int): Int {
        val activeSlots = getActiveSimSlots()
        return activeSlots.firstOrNull { it.slotIndex == slotIndex }?.subscriptionId ?: -1
    }

    /**
     * Returns a human-friendly label for a SIM slot (e.g. "SIM 1 • Mobilis").
     */
    fun getSimDisplayName(slotIndex: Int): String {
        val activeSlots = getActiveSimSlots()
        val slot = activeSlots.firstOrNull { it.slotIndex == slotIndex }
        return if (slot != null) {
            "SIM ${slot.slotIndex + 1} (${slot.operatorName})"
        } else {
            "SIM ${slotIndex + 1}"
        }
    }

    private fun normalizeAddressKey(address: String): String {
        val clean = address.trim().replace(" ", "").replace("-", "")
        val digits = clean.filter { it.isDigit() }
        return if (digits.length >= 7) digits.takeLast(9) else clean.lowercase()
    }
}
