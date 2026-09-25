package com.sha.orbis.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.sha.orbis.R
import java.util.Locale

class SimVerificationService {

    data class SimSlotInfo(
        val slotIndex: Int,           // 0 for SIM 1, 1 for SIM 2
        val subscriptionId: Int,      // Android Subscription ID
        val operatorName: String,     // e.g. "Mobilis", "Djezzy", "Ooredoo"
        val countryIso: String,       // e.g. "DZ", "FR"
        val rawNumber: String?,       // Burned MSISDN if available
        val isReady: Boolean
    )

    data class SimDetails(
        val isPresent: Boolean,
        val isReady: Boolean,
        val operatorName: String,
        val countryIso: String,
        val rawNumberOnSim: String?,
        val slotIndex: Int,
        val subscriptionId: Int = -1,
        val totalSimSlots: Int = 1,
        val availableSlots: List<SimSlotInfo> = emptyList()
    )

    sealed class VerificationResult {
        data class Success(
            val fullPhoneNumber: String,
            val simDetails: SimDetails,
            val matchedExactSimNumber: Boolean,
            val hardwareBindingSignature: String = "",
            val requiresOtpChallenge: Boolean = false
        ) : VerificationResult()

        data class Failure(
            val reason: FailureReason,
            val messageResId: Int,
            val formatArgs: List<Any> = emptyList(),
            val detail: String = ""
        ) : VerificationResult()
    }

    enum class FailureReason {
        NO_SIM_CARD,
        SIM_NOT_READY,
        PERMISSION_DENIED,
        COUNTRY_MISMATCH,
        OPERATOR_PREFIX_MISMATCH,
        NUMBER_MISMATCH,
        INVALID_FORMAT,
        NOT_A_MOBILE_NUMBER,
        FAKE_OR_DUMMY_PATTERN,
        SIM_ALREADY_BOUND
    }

    // 1. Detect ALL Active Physical SIM Slots (Dual-SIM / Multi-SIM)
    fun getAllActiveSimSlots(context: Context): List<SimSlotInfo> {
        val results = mutableListOf<SimSlotInfo>()
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        // Try SubscriptionManager first (returns only genuinely inserted & active SIMs)
        try {
            val activeList = subscriptionManager?.activeSubscriptionInfoList
            if (!activeList.isNullOrEmpty()) {
                for (subInfo in activeList) {
                    val slotIdx = subInfo.simSlotIndex

                    // Verify that the hardware slot state is NOT absent if telephonyManager is available
                    var isSlotAbsent = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telephonyManager != null && slotIdx >= 0) {
                        try {
                            val state = telephonyManager.getSimState(slotIdx)
                            if (state == TelephonyManager.SIM_STATE_ABSENT) {
                                isSlotAbsent = true
                            }
                        } catch (_: Exception) {}
                    }

                    if (isSlotAbsent) continue

                    var num: String? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            num = subscriptionManager.getPhoneNumber(subInfo.subscriptionId)
                        } catch (_: Exception) {}
                    }
                    if (num.isNullOrBlank()) {
                        @Suppress("DEPRECATION")
                        num = subInfo.number
                    }

                    val operator = subInfo.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: subInfo.carrierName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${if (slotIdx >= 0) slotIdx + 1 else 1}"

                    val country = subInfo.countryIso?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: "DZ"

                    results.add(
                        SimSlotInfo(
                            slotIndex = if (slotIdx >= 0) slotIdx else results.size,
                            subscriptionId = subInfo.subscriptionId,
                            operatorName = operator,
                            countryIso = country,
                            rawNumber = num?.takeIf { it.isNotBlank() },
                            isReady = true
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Handled gracefully
        }

        // Fallback ONLY if results is empty (e.g. before READ_PHONE_STATE permission granted)
        if (results.isEmpty() && telephonyManager != null) {
            val phoneCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager.activeModemCount
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.phoneCount
            }

            if (phoneCount > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (slot in 0 until phoneCount) {
                    try {
                        val state = telephonyManager.getSimState(slot)
                        if (state != TelephonyManager.SIM_STATE_ABSENT && state != TelephonyManager.SIM_STATE_UNKNOWN) {
                            val op = telephonyManager.simOperatorName.takeIf { it.isNotBlank() }
                                ?: telephonyManager.networkOperatorName.takeIf { it.isNotBlank() }
                                ?: "SIM ${slot + 1}"
                            val country = telephonyManager.simCountryIso.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: "DZ"
                            results.add(
                                SimSlotInfo(
                                    slotIndex = slot,
                                    subscriptionId = -1,
                                    operatorName = op,
                                    countryIso = country,
                                    rawNumber = null,
                                    isReady = state == TelephonyManager.SIM_STATE_READY
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
            } else {
                val simState = telephonyManager.simState
                if (simState != TelephonyManager.SIM_STATE_ABSENT && simState != TelephonyManager.SIM_STATE_UNKNOWN) {
                    val operator = telephonyManager.simOperatorName.takeIf { it.isNotBlank() }
                        ?: telephonyManager.networkOperatorName.takeIf { it.isNotBlank() }
                        ?: "Opérateur GSM"
                    val country = telephonyManager.simCountryIso.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: "DZ"
                    results.add(
                        SimSlotInfo(
                            slotIndex = 0,
                            subscriptionId = -1,
                            operatorName = operator,
                            countryIso = country,
                            rawNumber = null,
                            isReady = simState == TelephonyManager.SIM_STATE_READY
                        )
                    )
                }
            }
        }

        return results.distinctBy { it.slotIndex }.sortedBy { it.slotIndex }
    }

    // 2. Get Overall SIM Details (or targeted slot)
    fun getSimDetails(context: Context, targetSlotIndex: Int = 0): SimDetails {
        val allSlots = getAllActiveSimSlots(context)

        if (allSlots.isEmpty()) {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val isPresent = telephonyManager?.simState != TelephonyManager.SIM_STATE_ABSENT && telephonyManager?.simState != TelephonyManager.SIM_STATE_UNKNOWN
            return SimDetails(
                isPresent = isPresent,
                isReady = telephonyManager?.simState == TelephonyManager.SIM_STATE_READY,
                operatorName = "Inconnu",
                countryIso = "DZ",
                rawNumberOnSim = null,
                slotIndex = -1,
                totalSimSlots = 0,
                availableSlots = emptyList()
            )
        }

        val selectedSlot = allSlots.firstOrNull { it.slotIndex == targetSlotIndex } ?: allSlots.first()

        return SimDetails(
            isPresent = true,
            isReady = selectedSlot.isReady,
            operatorName = selectedSlot.operatorName,
            countryIso = selectedSlot.countryIso,
            rawNumberOnSim = selectedSlot.rawNumber,
            slotIndex = selectedSlot.slotIndex,
            subscriptionId = selectedSlot.subscriptionId,
            totalSimSlots = allSlots.size,
            availableSlots = allSlots
        )
    }

    // 3. Verify Identity with specific SIM Slot using Triple-Layer PhoneVerificationEngine
    fun verifyIdentityWithSim(
        context: Context,
        dialCode: String,
        nationalNumber: String,
        targetSlotIndex: Int = 0
    ): VerificationResult {
        val engineResult = PhoneVerificationEngine.verifyPhoneNumber(
            context = context,
            dialCode = dialCode,
            nationalNumber = nationalNumber,
            targetSlotIndex = targetSlotIndex
        )

        val simDetails = getSimDetails(context, targetSlotIndex)

        return when (engineResult) {
            is PhoneVerificationEngine.VerificationResult.Success -> {
                VerificationResult.Success(
                    fullPhoneNumber = engineResult.normalizedPhoneNumber,
                    simDetails = simDetails,
                    matchedExactSimNumber = engineResult.matchedExactHardwareSim,
                    hardwareBindingSignature = engineResult.hardwareBindingSignature,
                    requiresOtpChallenge = engineResult.requiresOtpChallenge
                )
            }
            is PhoneVerificationEngine.VerificationResult.Failure -> {
                val reason = when (engineResult.reason) {
                    PhoneVerificationEngine.FailureReason.NO_SIM_CARD -> FailureReason.NO_SIM_CARD
                    PhoneVerificationEngine.FailureReason.SIM_NOT_READY -> FailureReason.SIM_NOT_READY
                    PhoneVerificationEngine.FailureReason.PERMISSION_DENIED -> FailureReason.PERMISSION_DENIED
                    PhoneVerificationEngine.FailureReason.COUNTRY_MISMATCH -> FailureReason.COUNTRY_MISMATCH
                    PhoneVerificationEngine.FailureReason.OPERATOR_PREFIX_MISMATCH -> FailureReason.OPERATOR_PREFIX_MISMATCH
                    PhoneVerificationEngine.FailureReason.NUMBER_MISMATCH_WITH_SIM -> FailureReason.NUMBER_MISMATCH
                    PhoneVerificationEngine.FailureReason.INVALID_FORMAT_LIBPHONE -> FailureReason.INVALID_FORMAT
                    PhoneVerificationEngine.FailureReason.NOT_A_MOBILE_NUMBER -> FailureReason.NOT_A_MOBILE_NUMBER
                    PhoneVerificationEngine.FailureReason.FAKE_OR_DUMMY_PATTERN -> FailureReason.FAKE_OR_DUMMY_PATTERN
                    PhoneVerificationEngine.FailureReason.SIM_ALREADY_BOUND -> FailureReason.SIM_ALREADY_BOUND
                }
                VerificationResult.Failure(
                    reason = reason,
                    messageResId = engineResult.messageResId,
                    formatArgs = engineResult.formatArgs,
                    detail = engineResult.technicalDetail
                )
            }
        }
    }

    private fun normalizePhoneNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            digits.startsWith("0") && digits.length >= 9 -> "+${digits.drop(1)}"
            else -> "+$digits"
        }
    }
}
