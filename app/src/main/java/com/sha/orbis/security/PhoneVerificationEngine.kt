package com.sha.orbis.security

import android.content.Context

object PhoneVerificationEngine {

    sealed class VerificationResult {
        data class Success(
            val normalizedPhoneNumber: String,
            val isoCountryCode: String = "",
            val slotIndex: Int = 0,
            val matchedExactHardwareSim: Boolean = false,
            val hardwareBindingSignature: String = "",
            val requiresOtpChallenge: Boolean = false
        ) : VerificationResult()

        data class Failure(
            val reason: FailureReason,
            val detail: String = "",
            val messageResId: Int = 0,
            val formatArgs: List<Any> = emptyList(),
            val technicalDetail: String = ""
        ) : VerificationResult()
    }

    enum class FailureReason {
        NO_SIM_CARD,
        SIM_NOT_READY,
        INVALID_FORMAT_LIBPHONE,
        NUMBER_MISMATCH_WITH_SIM,
        COUNTRY_MISMATCH,
        OPERATOR_PREFIX_MISMATCH,
        NOT_A_MOBILE_NUMBER,
        FAKE_OR_DUMMY_PATTERN,
        SIM_ALREADY_BOUND,
        PERMISSION_DENIED
    }

    fun verifyPhoneNumber(
        context: Context,
        dialCode: String,
        nationalNumber: String,
        targetSlotIndex: Int = 0
    ): VerificationResult {
        return VerificationResult.Failure(reason = FailureReason.PERMISSION_DENIED)
    }
}
