package com.sha.orbis.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SelfSmsVerificationManagerTest {

    @Before
    fun setUp() {
        SelfSmsVerificationManager.clear()
    }

    @After
    fun tearDown() {
        SelfSmsVerificationManager.clear()
    }

    @Test
    fun testGenerateSessionTokenFormat() {
        val token = SelfSmsVerificationManager.generateSessionToken("+213660151526")
        assertNotNull(token)
        assertEquals("Le jeton cryptographique de session doit comporter exactement 8 caractères hexadécimaux pour respecter la norme Google OTP 4-10 car.", 8, token.length)
        assertTrue("Le jeton doit être composé uniquement de chiffres et de lettres hexadécimales majuscules", token.matches(Regex("^[0-9A-F]{8}$")))
    }

    @Test
    fun testVerifyIncomingMessageWithCryptographicHash() {
        val testToken = "881401F4"
        SelfSmsVerificationManager.setTestCode(testToken, "+213660151526")

        val incomingSms = "<#> OrbisNet Security Token [HASH:$testToken]\nFA+9qCX9VSu"
        val isVerified = SelfSmsVerificationManager.verifyIncomingMessage(incomingSms)

        assertTrue("La réception réelle du SMS au format Google SMS Retriever doit valider la ligne", isVerified)
        assertFalse("Après validation réussie, la session en cours doit être nettoyée", SelfSmsVerificationManager.isChallengePending())
    }

    @Test
    fun testVerifyIncomingMessageWithBiSimSender() {
        // Scénario Bi-SIM : La SIM 1 Mobilis (+213660151526) a 0 DA de crédit.
        // L'utilisateur expédie le SMS depuis la SIM 2 Ooredoo (+213550998877) vers la SIM 1.
        // La SIM 1 reçoit gratuitement le SMS avec le jeton.
        val testToken = "A1B2C3D4"
        SelfSmsVerificationManager.setTestCode(testToken, "+213660151526")

        val incomingSmsOnSim1 = "<#> OrbisNet Security Token [HASH:$testToken]\nFA+9qCX9VSu"
        val isVerified = SelfSmsVerificationManager.verifyIncomingMessage(incomingSmsOnSim1)

        assertTrue("Le SMS reçu sur la SIM cible doit valider le compte même s'il a été expédié depuis la SIM 2", isVerified)
    }

    @Test
    fun testVerifyTokenDirectOrFromClipboard() {
        val testToken = "881401F4"
        SelfSmsVerificationManager.setTestCode(testToken, "+213660151526")

        // 1. Direct raw token verification
        val isVerifiedDirect = SelfSmsVerificationManager.verifyToken(testToken)
        assertTrue("Le jeton brut doit être validé", isVerifiedDirect)

        // 2. Token from copied SMS
        SelfSmsVerificationManager.setTestCode(testToken, "+213660151526")
        val isVerifiedCopied = SelfSmsVerificationManager.verifyToken("OrbisNet Security Token [HASH:$testToken]")
        assertTrue("Le texte complet du SMS copié contenant le jeton doit être validé", isVerifiedCopied)
    }

    @Test
    fun testVerifyIncomingMessageFailsWithWrongHash() {
        val testToken = "881401F4"
        SelfSmsVerificationManager.setTestCode(testToken, "+213660151526")

        val forgedSms = "<#> OrbisNet Security Token [HASH:00000000]\nFA+9qCX9VSu"
        val isVerified = SelfSmsVerificationManager.verifyIncomingMessage(forgedSms)

        assertFalse("Un SMS contenant un mauvais jeton ou un hash falsifié doit être rejeté", isVerified)
        assertTrue("La session doit rester active tant que le bon jeton n'a pas été reçu", SelfSmsVerificationManager.isChallengePending())
    }

    @Test
    fun testVerifyIncomingMessageFailsWhenExpired() {
        val testToken = "881401F4"
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000L)
        SelfSmsVerificationManager.setTestCode(testToken, "+213660151526", sixMinutesAgo)

        val lateSms = "<#> OrbisNet Security Token [HASH:$testToken]\nFA+9qCX9VSu"
        val isVerified = SelfSmsVerificationManager.verifyIncomingMessage(lateSms)

        assertFalse("Un SMS reçu après l'expiration de la session (5 min) doit être rejeté", isVerified)
    }

    @Test
    fun testExtractTokenFromMessageWithHash() {
        val msg = "<#> OrbisNet Security Token [HASH:881401F4]\nFA+9qCX9VSu"
        val extracted = SelfSmsVerificationManager.extractTokenFromMessage(msg)
        assertEquals("881401F4", extracted)
    }

    @Test
    fun testVerificationSucceedsWithCorrectCode() {
        SelfSmsVerificationManager.setTestCode("482910", "+213660151526")

        val (isValid, errorMsg) = SelfSmsVerificationManager.verifyCode(null, "482910")

        assertTrue("Le code exact doit valider la session avec succès", isValid)
        assertNull("Aucun message d'erreur ne doit être renvoyé en cas de succès", errorMsg)
    }

    @Test
    fun testVerificationFailsWithWrongCode() {
        SelfSmsVerificationManager.setTestCode("482910", "+213660151526")

        val (isValid, errorMsg) = SelfSmsVerificationManager.verifyCode(null, "000000")

        assertFalse("Un mauvais code doit être rejeté", isValid)
        assertTrue("Un message d'erreur avec tentatives restantes doit être retourné", errorMsg?.contains("Tentatives restantes") == true)
    }

    @Test
    fun testMaxAttemptsLockout() {
        SelfSmsVerificationManager.setTestCode("482910", "+213660151526")

        // Attempt 1: wrong
        val (valid1, _) = SelfSmsVerificationManager.verifyCode(null, "111111")
        assertFalse(valid1)

        // Attempt 2: wrong
        val (valid2, _) = SelfSmsVerificationManager.verifyCode(null, "222222")
        assertFalse(valid2)

        // Attempt 3: wrong -> locks out
        val (valid3, error3) = SelfSmsVerificationManager.verifyCode(null, "333333")
        assertFalse(valid3)
        assertTrue("Après 3 échecs, l'erreur doit signaler le dépassement de tentatives", error3?.contains("dépassé") == true)

        // Attempt 4: even if now correct code, session was cleared/locked out
        val (valid4, _) = SelfSmsVerificationManager.verifyCode(null, "482910")
        assertFalse("La session doit être invalidée après le nombre maximal d'essais", valid4)
    }

    @Test
    fun testExpiredCodeFails() {
        // Timestamp 6 minutes ago (validity is 5 minutes)
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000L)
        SelfSmsVerificationManager.setTestCode("482910", "+213660151526", sixMinutesAgo)

        val (isValid, errorMsg) = SelfSmsVerificationManager.verifyCode(null, "482910")

        assertFalse("Un code expiré (plus de 5 min) doit être rejeté même s'il est correct", isValid)
        assertTrue("L'erreur doit mentionner l'expiration", errorMsg?.contains("expiré") == true)
    }

    @Test
    fun testVerifyWithoutActiveSessionFails() {
        val (isValid, errorMsg) = SelfSmsVerificationManager.verifyCode(null, "123456")

        assertFalse("Vérifier sans code actif doit échouer", isValid)
        assertTrue("L'erreur doit indiquer code incorrect ou expiré", errorMsg?.contains("incorrect") == true)
    }

    @Test
    fun testExtractOtpFromMessage() {
        val msg1 = "OrbisNet: Verification code is 482910 [CHALLENGE:7A8F9B]"
        val extracted1 = SelfSmsVerificationManager.extractOtpFromMessage(msg1)
        assertEquals("482910", extracted1)

        val msg2 = "Votre code OrbisNet : 654321. Ne le partagez pas."
        val extracted2 = SelfSmsVerificationManager.extractOtpFromMessage(msg2)
        assertEquals("654321", extracted2)

        val msgInvalid = "Bonjour, bienvenue sur OrbisNet !"
        val extractedNull = SelfSmsVerificationManager.extractOtpFromMessage(msgInvalid)
        assertNull(extractedNull)
    }
}
