package com.sha.orbis.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sha.orbis.data.SessionManager
import java.security.MessageDigest

/**
 * SimSecurityManager -- Detection de remplacement/retrait de SIM en temps reel.
 *
 * Couches de verification (multi-layer matching) :
 *   1. subscriptionId     -> identifiant Android le plus fiable
 *   2. cardId (API 29+)   -> identifiant physique de la carte
 *   3. ICCID hash         -> empreinte de la puce (si accessible)
 *   4. MSISDN last-8      -> numero grave sur la SIM (fallback)
 *
 * Supporte le Dual-SIM : la SIM d identite peut etre dans n importe quel slot actif.
 * Pas de faux positif en mode Avion (la SIM reste READY, seule la radio est coupee).
 */
object SimSecurityManager {

    private const val TAG = "SimSecurityManager"

    // Statut retourne
    sealed class SimSecurityStatus {
        /** SIM presente et correspond au compte enregistre. */
        object Valid : SimSecurityStatus()
        /** SIM absente ou remplacee. */
        data class Invalid(val reason: SimLockReason) : SimSecurityStatus()
    }

    enum class SimLockReason {
        /** Aucune SIM physique detectee dans l appareil. */
        NO_SIM,
        /** Une SIM est presente mais ce n est pas celle enregistree. */
        SIM_SWAPPED
    }

    // Listener temps reel
    private var subscriptionListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private var onStatusChangedCallback: ((SimSecurityStatus) -> Unit)? = null

    fun startRealTimeMonitoring(context: Context, onStatusChanged: (SimSecurityStatus) -> Unit) {
        onStatusChangedCallback = onStatusChanged
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager ?: return
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() {
                    Log.d(TAG, "onSubscriptionsChanged -> re-verification SIM")
                    onStatusChanged(verifyActiveSim(context))
                }
            }
            subscriptionListener = listener
            try {
                subscriptionManager.addOnSubscriptionsChangedListener(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Impossible d enregistrer le listener SIM: ${e.message}")
            }
        }
    }

    fun stopRealTimeMonitoring(context: Context) {
        subscriptionListener?.let { listener ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                            as? SubscriptionManager
                    subscriptionManager?.removeOnSubscriptionsChangedListener(listener)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur lors du retrait du listener SIM: ${e.message}")
            }
        }
        subscriptionListener = null
        onStatusChangedCallback = null
    }

    fun verifyActiveSim(context: Context): SimSecurityStatus {
        if (!hasPhonePermission(context)) return SimSecurityStatus.Valid

        val sessionManager = SessionManager(context)
        if (!sessionManager.isAuthenticated) return SimSecurityStatus.Valid

        val profile = sessionManager.activeAccount ?: return SimSecurityStatus.Valid

        val activeSlots = getActiveSimSlots(context)
        if (activeSlots.isEmpty()) {
            Log.w(TAG, "Aucune SIM active detectee -> NO_SIM")
            return SimSecurityStatus.Invalid(SimLockReason.NO_SIM)
        }

        val storedSubId  = profile.subscriptionId
        val storedCardId = profile.cardId
        val storedIccId  = profile.iccIdHash
        val storedPhone  = profile.phoneNumber

        val matchingSlot = activeSlots.firstOrNull { slot ->
            matchesStoredSim(slot, storedSubId, storedCardId, storedIccId, storedPhone)
        }

        return if (matchingSlot != null) {
            Log.d(TAG, "SIM OK (slot ${matchingSlot.slotIndex})")
            SimSecurityStatus.Valid
        } else {
            Log.w(TAG, "Aucune SIM ne correspond -> SIM_SWAPPED")
            SimSecurityStatus.Invalid(SimLockReason.SIM_SWAPPED)
        }
    }

    private fun matchesStoredSim(
        slot: ActiveSimSlot,
        storedSubId: Int,
        storedCardId: Int,
        storedIccId: String?,
        storedPhone: String
    ): Boolean {
        // Couche 1 : subscriptionId
        if (storedSubId > 0 && slot.subscriptionId > 0 && slot.subscriptionId == storedSubId) return true
        // Couche 2 : cardId (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (storedCardId >= 0 && slot.cardId >= 0 && slot.cardId == storedCardId) return true
        }
        // Couche 3 : ICCID hash
        if (!storedIccId.isNullOrBlank() && !slot.iccIdHash.isNullOrBlank() && slot.iccIdHash == storedIccId) return true
        // Couche 4 : MSISDN last-8 digits
        if (slot.rawNumber != null) {
            val simLast8   = slot.rawNumber.filter { it.isDigit() }.takeLast(8)
            val phoneLast8 = storedPhone.filter { it.isDigit() }.takeLast(8)
            if (simLast8.length >= 8 && simLast8 == phoneLast8) return true
        }
        return false
    }

    private fun getActiveSimSlots(context: Context): List<ActiveSimSlot> {
        val results = mutableListOf<ActiveSimSlot>()
        if (!hasPhonePermission(context)) return results
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager ?: return results
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        try {
            val activeList = subscriptionManager.activeSubscriptionInfoList
            if (!activeList.isNullOrEmpty()) {
                for (subInfo in activeList) {
                    val slotIdx = subInfo.simSlotIndex
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telephonyManager != null && slotIdx >= 0) {
                        try {
                            if (telephonyManager.getSimState(slotIdx) == TelephonyManager.SIM_STATE_ABSENT) continue
                        } catch (_: Exception) {}
                    }
                    val cardId: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try { subInfo.cardId } catch (_: Exception) { -1 }
                    } else { -1 }
                    val iccIdHash: String? = try {
                        val raw = subInfo.iccId
                        if (!raw.isNullOrBlank()) sha256(raw) else null
                    } catch (_: Exception) { null }
                    var rawNumber: String? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try { rawNumber = subscriptionManager.getPhoneNumber(subInfo.subscriptionId) } catch (_: Exception) {}
                    }
                    if (rawNumber.isNullOrBlank()) {
                        @Suppress("DEPRECATION")
                        rawNumber = subInfo.number
                    }
                    results.add(
                        ActiveSimSlot(
                            slotIndex      = if (slotIdx >= 0) slotIdx else results.size,
                            subscriptionId = subInfo.subscriptionId,
                            cardId         = cardId,
                            iccIdHash      = iccIdHash,
                            rawNumber      = rawNumber?.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur recuperation SIM: ${e.message}")
        }
        return results
    }

    private fun hasPhonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    data class ActiveSimSlot(
        val slotIndex: Int,
        val subscriptionId: Int,
        val cardId: Int,
        val iccIdHash: String?,
        val rawNumber: String?
    )
}
