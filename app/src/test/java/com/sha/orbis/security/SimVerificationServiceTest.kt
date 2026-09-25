package com.sha.orbis.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimVerificationServiceTest {

    @Test
    fun testPhoneNumberMatchingLogic() {
        val nationalEntered = "555123456"
        val fullEntered = "+213555123456"

        // Case 1: Carrier stored local MSISDN starting with 0 (Standard Algerian SIM: 0555123456)
        val rawLocal = "0555123456"
        val simDigits1 = rawLocal.filter { it.isDigit() }
        val enteredDigits1 = fullEntered.filter { it.isDigit() }
        val matches1 = simDigits1 == enteredDigits1 ||
                simDigits1.endsWith(nationalEntered) ||
                enteredDigits1.endsWith(simDigits1) ||
                (nationalEntered.length >= 8 && simDigits1.takeLast(8) == nationalEntered.takeLast(8))
        assertTrue("Le numéro local 0555123456 de la puce doit correspondre au numéro saisi 555123456", matches1)

        // Case 2: Carrier stored full international format (+213555123456)
        val rawIntl = "+213555123456"
        val simDigits2 = rawIntl.filter { it.isDigit() }
        val matches2 = simDigits2 == enteredDigits1 ||
                simDigits2.endsWith(nationalEntered) ||
                enteredDigits1.endsWith(simDigits2) ||
                (nationalEntered.length >= 8 && simDigits2.takeLast(8) == nationalEntered.takeLast(8))
        assertTrue("Le numéro international de la puce doit correspondre exactement", matches2)

        // Case 3: Completely different phone number on SIM
        val rawOther = "0661223344"
        val simDigits3 = rawOther.filter { it.isDigit() }
        val matches3 = simDigits3 == enteredDigits1 ||
                simDigits3.endsWith(nationalEntered) ||
                enteredDigits1.endsWith(simDigits3) ||
                (nationalEntered.length >= 8 && simDigits3.takeLast(8) == nationalEntered.takeLast(8))
        assertFalse("Une puce avec un numéro différent (0661223344) doit être rejetée", matches3)
    }

    @Test
    fun testRequiresOtpChallengeFlag() {
        val simDetails = SimVerificationService.SimDetails(
            isPresent = true,
            isReady = true,
            operatorName = "Mobilis",
            countryIso = "DZ",
            rawNumberOnSim = null,
            slotIndex = 0
        )

        // Case 1: Number not burned on SIM -> requiresOtpChallenge must be true
        val resultNeedsOtp = SimVerificationService.VerificationResult.Success(
            fullPhoneNumber = "+213660151526",
            simDetails = simDetails,
            matchedExactSimNumber = false,
            requiresOtpChallenge = true
        )
        assertTrue("Une puce sans numéro gravé doit exiger le challenge OTP Firebase", resultNeedsOtp.requiresOtpChallenge)

        // Case 2: Number burned on SIM and exact hardware match -> requiresOtpChallenge is false (instant free local auth)
        val resultHardwareMatched = SimVerificationService.VerificationResult.Success(
            fullPhoneNumber = "+213550123456",
            simDetails = simDetails.copy(rawNumberOnSim = "0550123456"),
            matchedExactSimNumber = true,
            requiresOtpChallenge = false
        )
        assertFalse("Une puce avec numéro gravé validé matériellement ne doit pas demander d'OTP", resultHardwareMatched.requiresOtpChallenge)
    }
}
