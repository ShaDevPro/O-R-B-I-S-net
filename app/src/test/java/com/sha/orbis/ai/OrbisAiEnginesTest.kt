package com.sha.orbis.ai

import com.sha.orbis.ai.core.OrbisTokenizer
import com.sha.orbis.ai.core.OrbisVectorMath
import com.sha.orbis.ai.guard.OrbisGuardEngine
import com.sha.orbis.ai.guard.ThreatLevel
import com.sha.orbis.ai.guard.ThreatType
import com.sha.orbis.ai.reply.MessageIntent
import com.sha.orbis.ai.reply.OrbisReplyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbisAiEnginesTest {

    @Test
    fun vectorMath_dotProduct_and_cosineSimilarity_behaveCorrectly() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        val c = floatArrayOf(0f, 1f, 0f)

        val simIdentical = OrbisVectorMath.cosineSimilarity(a, b)
        val simOrthogonal = OrbisVectorMath.cosineSimilarity(a, c)

        assertEquals(1.0f, simIdentical, 0.001f)
        assertEquals(0.0f, simOrthogonal, 0.001f)
    }

    @Test
    fun vectorMath_quantization_and_dequantization_maintains_high_fidelity() {
        val original = floatArrayOf(0.12f, -0.45f, 0.88f, -0.05f)
        val quantized = OrbisVectorMath.quantizeToInt8(original)
        val dequantized = OrbisVectorMath.dequantizeFromInt8(quantized)

        for (i in original.indices) {
            assertEquals(original[i], dequantized[i], 0.02f)
        }
    }

    @Test
    fun tokenizer_detects_all_required_languages() {
        assertEquals("fr", OrbisTokenizer.detectLanguage("Bonjour comment vas-tu aujourd'hui ?"))
        assertEquals("en", OrbisTokenizer.detectLanguage("Hey what time are you coming over?"))
        assertEquals("ar", OrbisTokenizer.detectLanguage("السلام عليكم كيف الحال والأحوال ؟"))
        assertTrue(OrbisTokenizer.detectLanguage("Salam khoya labas 3lik kidayr ?") in listOf("dz", "darija"))
    }

    @Test
    fun tokenizer_masks_sensitive_entities() {
        val raw = "Payez 45€ sur http://bad-link.com ou appelez le 0612345678 avec code 4820"
        val tokens = OrbisTokenizer.tokenize(raw)

        assertTrue(tokens.contains("[MONEY]"))
        assertTrue(tokens.contains("[URL]"))
        assertTrue(tokens.contains("[PHONE]"))
        assertTrue(tokens.contains("[OTP]"))
    }

    @Test
    fun guard_detects_banking_phishing_as_dangerous_threat() {
        val msg = "URGENT Société Générale: Votre carte bancaire a été bloquée. Confirmez votre identité sur http://suspicious-bank-auth.cc"
        val result = OrbisGuardEngine.analyzeMessage(null, "+33600000000", msg)

        assertTrue(result.isSpam)
        assertTrue(result.isDangerous)
        assertEquals(ThreatType.PHISHING_BANK, result.threatType)
        assertTrue(result.confidenceScore >= 0.80f)
    }

    @Test
    fun guard_detects_delivery_phishing_as_threat() {
        val msg = "Chronopost: Votre colis 492021 ne peut être remis. Réglez les frais de douane de 2,99€ sur http://tinyurl.com/douane"
        val result = OrbisGuardEngine.analyzeMessage(null, "+33611111111", msg)

        assertTrue(result.isSpam)
        assertTrue(result.isDangerous)
        assertEquals(ThreatType.PHISHING_DELIVERY, result.threatType)
    }

    @Test
    fun guard_detects_emergency_family_impersonation() {
        val msg = "Coucou maman, mon téléphone est tombé dans l'eau. C'est mon nouveau numéro temporaire, écris-moi sur WhatsApp en urgence !"
        val result = OrbisGuardEngine.analyzeMessage(null, "+33622222222", msg)

        assertTrue(result.isSpam)
        assertEquals(ThreatLevel.CRITICAL, result.threatLevel)
        assertEquals(ThreatType.SCAM_EMERGENCY, result.threatType)
    }

    @Test
    fun guard_classifies_authentic_otp_as_safe() {
        val msg = "Votre code de vérification Google est 481902. Ne le partagez avec personne."
        val result = OrbisGuardEngine.analyzeMessage(null, "Google", msg)

        assertFalse(result.isSpam)
        assertEquals(ThreatLevel.SAFE, result.threatLevel)
        assertEquals(ThreatType.OTP_SECURITY, result.threatType)
    }

    @Test
    fun guard_classifies_legitimate_personal_sms_as_safe() {
        val msg = "Salut Pierre, est-ce que tu es toujours disponible pour le déjeuner à 13h ?"
        val result = OrbisGuardEngine.analyzeMessage(null, "+33633333333", msg)

        assertFalse(result.isSpam)
        assertEquals(ThreatLevel.SAFE, result.threatLevel)
        assertEquals(ThreatType.LEGITIMATE, result.threatType)
    }

    @Test
    fun reply_suggests_appropriate_responses_for_greetings() {
        val result = OrbisReplyEngine.generateReplies(null, "Bonjour comment vas-tu ?")

        assertTrue(result.suggestions.isNotEmpty())
        assertEquals(MessageIntent.HOW_ARE_YOU, result.detectedIntent)
        assertTrue(result.suggestions.any { it.text.contains("bien", ignoreCase = true) || it.text.contains("merci", ignoreCase = true) })
    }

    @Test
    fun reply_suggests_location_responses() {
        val result = OrbisReplyEngine.generateReplies(null, "Tu es où actuellement ?")

        assertTrue(result.suggestions.isNotEmpty())
        assertEquals(MessageIntent.LOCATION_QUERY, result.detectedIntent)
        assertTrue(result.suggestions.any { it.text.contains("route", ignoreCase = true) || it.text.contains("maison", ignoreCase = true) || it.text.contains("arrive", ignoreCase = true) })
    }

    @Test
    fun reply_suggests_darija_responses_for_darija_messages() {
        val result = OrbisReplyEngine.generateReplies(null, "Fink daba wach f dar ?")

        assertTrue(result.detectedLanguage in listOf("dz", "darija"))
        assertEquals(MessageIntent.LOCATION_QUERY, result.detectedIntent)
        assertTrue(result.suggestions.isNotEmpty())
    }

    @Test
    fun reply_adapts_and_personalizes_from_user_training() {
        val incoming = "À quelle heure on se rejoint ?"
        val customUserReply = "Je serai là vers 20h pile !"

        // Teach the model user's personalized reply
        OrbisReplyEngine.learnFromUserSentMessage(null, incoming, customUserReply)

        // Generate replies again
        val afterLearning = OrbisReplyEngine.generateReplies(null, incoming)

        assertTrue(afterLearning.suggestions.isNotEmpty())
        val firstSuggestion = afterLearning.suggestions.first()
        assertEquals(customUserReply, firstSuggestion.text)
        assertTrue(firstSuggestion.isPersonalized)
    }

    @Test
    fun guard_classifies_saved_contacts_and_greetings_as_safe() {
        val result = OrbisGuardEngine.analyzeMessage(
            context = null,
            sender = "+213550000000",
            messageBody = "السلام عليكم الحاج احمد",
            contactName = "Ali"
        )
        assertFalse(result.isSpam)
        assertEquals(ThreatLevel.SAFE, result.threatLevel)
    }

    @Test
    fun guard_classifies_operator_missed_call_and_reachability_as_safe() {
        val missedCall = OrbisGuardEngine.analyzeMessage(
            context = null,
            sender = "644",
            messageBody = "Le +213672197773 a essayé de vous joindre 1 fois le 07/09 à 18:24."
        )
        assertFalse(missedCall.isSpam)
        assertEquals(ThreatLevel.SAFE, missedCall.threatLevel)

        val reachability = OrbisGuardEngine.analyzeMessage(
            context = null,
            sender = "+213600000000",
            messageBody = "Le +213672197773 est de nouveau joignable.",
            contactName = "Frangine"
        )
        assertFalse(reachability.isSpam)
        assertEquals(ThreatLevel.SAFE, reachability.threatLevel)
    }

    @Test
    fun guard_classifies_operator_recharge_and_institutional_messages_as_safe() {
        val recharge = OrbisGuardEngine.analyzeMessage(
            context = null,
            sender = "Mobilis",
            messageBody = "Votre rechargement de 1500 DA a été effectué avec succès. Votre solde est..."
        )
        assertFalse(recharge.isSpam)
        assertEquals(ThreatLevel.SAFE, recharge.threatLevel)

        val mdn = OrbisGuardEngine.analyzeMessage(
            context = null,
            sender = "MDN",
            messageBody = "ندعو مديرية الخدمة الوطنية المواطنين المولودين بين 01 جانفي و 31 ديسمبر لتسوية وضعيتهم."
        )
        assertFalse(mdn.isSpam)
        assertEquals(ThreatLevel.SAFE, mdn.threatLevel)
    }

    @Test
    fun guard_does_not_flag_contact_with_financial_name() {
        val contactWithBankName = OrbisGuardEngine.analyzeMessage(
            context = null,
            sender = "+213770000000",
            messageBody = "Salam khoya choufli hadik la commande stp",
            contactName = "Nasrou Paysera"
        )
        assertFalse(contactWithBankName.isSpam)
        assertEquals(ThreatLevel.SAFE, contactWithBankName.threatLevel)
    }
}
