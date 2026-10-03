package com.sha.orbis.security

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.sha.orbis.model.CountryCode
import com.sha.orbis.storage.AccountStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneVerificationEngineTest {

    // =========================================================================
    // 1. TEST LISTE COMPLÈTE DES PAYS (248+ PAYS DANS AUTH)
    // =========================================================================

    @Test
    fun testCountryCodeListCompleteness() {
        val allCountries = CountryCode.ALL_COUNTRIES
        assertTrue("La liste doit contenir au moins 248 pays", allCountries.size >= 248)

        // Vérification des pays clés et de leurs indicatifs
        val algeria = CountryCode.findByIso("DZ")
        assertNotNull("L'Algérie doit être présente", algeria)
        assertEquals("+213", algeria?.dialCode)
        assertEquals("🇩🇿", algeria?.flagEmoji)
        assertEquals("Algérie", algeria?.nameFr)
        assertEquals("الجزائر", algeria?.nameAr)

        val france = CountryCode.findByIso("FR")
        assertNotNull("La France doit être présente", france)
        assertEquals("+33", france?.dialCode)
        assertEquals("🇫🇷", france?.flagEmoji)

        val palestine = CountryCode.findByIso("PS")
        assertNotNull("La Palestine doit être présente", palestine)
        assertEquals("+970", palestine?.dialCode)
        assertEquals("🇵🇸", palestine?.flagEmoji)
        assertEquals("فلسطين", palestine?.nameAr)

        val saudi = CountryCode.findByIso("SA")
        assertNotNull("L'Arabie Saoudite doit être présente", saudi)
        assertEquals("+966", saudi?.dialCode)

        val morocco = CountryCode.findByIso("MA")
        assertNotNull("Le Maroc doit être présent", morocco)
        assertEquals("+212", morocco?.dialCode)

        val tunisia = CountryCode.findByIso("TN")
        assertNotNull("La Tunisie doit être présente", tunisia)
        assertEquals("+216", tunisia?.dialCode)

        val us = CountryCode.findByIso("US")
        assertNotNull("Les USA doivent être présents", us)
        assertEquals("+1", us?.dialCode)

        // Test de recherche par code d'appel
        val foundByDial = CountryCode.findByDialCode("+213")
        assertEquals("DZ", foundByDial?.isoCode)

        val foundByDialWithoutPlus = CountryCode.findByDialCode("33")
        assertEquals("FR", foundByDialWithoutPlus?.isoCode)
    }

    // =========================================================================
    // 2. TEST COUCHE 1 : CONCORDANCE OPÉRATEUR & PRÉFIXE RÉSEAU
    // =========================================================================

    @Test
    fun testOperatorPrefixValidation() {
        // Algérie Mobilis (06xx)
        val mobilisOk = PhoneVerificationEngine.verifyOperatorPrefix("Mobilis ATM", "DZ", "661234567")
        assertTrue("Mobilis avec 06 doit être valide", mobilisOk.isValid)

        val mobilisFail = PhoneVerificationEngine.verifyOperatorPrefix("Mobilis", "DZ", "540123456")
        assertFalse("Mobilis avec 05 doit être rejeté", mobilisFail.isValid)

        // Algérie Djezzy (07xx)
        val djezzyOk = PhoneVerificationEngine.verifyOperatorPrefix("Djezzy OTA", "DZ", "770123456")
        assertTrue("Djezzy avec 07 doit être valide", djezzyOk.isValid)

        val djezzyFail = PhoneVerificationEngine.verifyOperatorPrefix("Djezzy", "DZ", "661234567")
        assertFalse("Djezzy avec 06 doit être rejeté", djezzyFail.isValid)

        // Algérie Ooredoo (05xx)
        val ooredooOk = PhoneVerificationEngine.verifyOperatorPrefix("Ooredoo Nedjma", "DZ", "550123456")
        assertTrue("Ooredoo avec 05 doit être valide", ooredooOk.isValid)

        val ooredooFail = PhoneVerificationEngine.verifyOperatorPrefix("Ooredoo", "DZ", "770123456")
        assertFalse("Ooredoo avec 07 doit être rejeté", ooredooFail.isValid)
    }

    // =========================================================================
    // 3. TEST COUCHE 2 : GOOGLE LIBPHONENUMBER (VALIDATION & TYPE MOBILE)
    // =========================================================================

    @Test
    fun testGoogleLibPhoneNumberLayer() {
        val phoneUtil = PhoneNumberUtil.getInstance()

        // 1. Mobile valide Algérie (Ooredoo)
        val dzNumber = phoneUtil.parse("+213550123456", "DZ")
        assertTrue("Le numéro DZ doit être syntaxiquement valide", phoneUtil.isValidNumber(dzNumber))
        assertTrue("Le numéro doit correspondre à la région DZ", phoneUtil.isValidNumberForRegion(dzNumber, "DZ"))
        val dzType = phoneUtil.getNumberType(dzNumber)
        assertTrue("Le type doit être MOBILE", dzType == PhoneNumberType.MOBILE || dzType == PhoneNumberType.FIXED_LINE_OR_MOBILE)
        assertEquals("+213550123456", phoneUtil.format(dzNumber, PhoneNumberUtil.PhoneNumberFormat.E164))

        // 2. Mobile valide France
        val frNumber = phoneUtil.parse("+33612345678", "FR")
        assertTrue("Le numéro FR doit être valide", phoneUtil.isValidNumber(frNumber))
        val frType = phoneUtil.getNumberType(frNumber)
        assertTrue("Le type doit être MOBILE", frType == PhoneNumberType.MOBILE || frType == PhoneNumberType.FIXED_LINE_OR_MOBILE)

        // 3. Ligne fixe Algérie (021 = Alger fixe) -> doit être rejetée car pas mobile pur
        val dzFixed = phoneUtil.parse("+21321234567", "DZ")
        val fixedType = phoneUtil.getNumberType(dzFixed)
        assertFalse("Une ligne fixe ne doit pas être acceptée comme MOBILE pur", fixedType == PhoneNumberType.MOBILE)

        // 4. Faux numéro non attribué / tronqué
        val invalidNum = phoneUtil.parse("+213000000", "DZ")
        assertFalse("Un faux numéro tronqué doit être rejeté", phoneUtil.isValidNumber(invalidNum))
    }

    // =========================================================================
    // 4. TEST COUCHE 3 : ANTI-SPOOFING HEURISTIQUE
    // =========================================================================

    @Test
    fun testAntiSpoofingHeuristics() {
        // Faux numéros composés d'un seul chiffre répété
        assertFalse("000000000 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("000000000").isValid)
        assertFalse("555555555 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("555555555").isValid)
        assertFalse("777777777 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("777777777").isValid)

        // Préfixe correct mais corps fictif à zéros ou répétés
        assertFalse("500000000 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("500000000").isValid)
        assertFalse("600000000 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("600000000").isValid)
        assertFalse("711111111 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("711111111").isValid)

        // Suites séquentielles ascendantes ou descendantes
        assertFalse("012345678 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("012345678").isValid)
        assertFalse("123456789 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("123456789").isValid)
        assertFalse("987654321 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("987654321").isValid)
        assertFalse("876543210 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("876543210").isValid)

        // Répétitions excessives d'un chiffre (6 fois d'affilée)
        assertFalse("540111111 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("540111111").isValid)
        assertFalse("770222222 doit être rejeté", PhoneVerificationEngine.auditAntiSpoofingPatterns("770222222").isValid)

        // Numéros réels légitimes
        assertTrue("550123456 doit être accepté", PhoneVerificationEngine.auditAntiSpoofingPatterns("550123456").isValid)
        assertTrue("661234567 doit être accepté", PhoneVerificationEngine.auditAntiSpoofingPatterns("661234567").isValid)
        assertTrue("770981243 doit être accepté", PhoneVerificationEngine.auditAntiSpoofingPatterns("770981243").isValid)
    }

    // =========================================================================
    // 5. TEST LIAISON CRYPTOGRAPHIQUE MATÉRIELLE
    // =========================================================================

    @Test
    fun testHardwareBindingSignature() {
        val sigSlot0 = PhoneVerificationEngine.computeHardwareBindingSignature("+213550123456", 0, 101, "iccid_abc")
        val sigSlot1 = PhoneVerificationEngine.computeHardwareBindingSignature("+213550123456", 1, 102, "iccid_xyz")

        assertTrue("La signature SHA-256 doit être de 64 caractères hex", sigSlot0.length == 64)
        assertNotEquals("Deux slots/cartes différents doivent produire deux signatures différentes", sigSlot0, sigSlot1)

        val sigRepeat = PhoneVerificationEngine.computeHardwareBindingSignature("+213550123456", 0, 101, "iccid_abc")
        assertEquals("La même puce doit produire la même signature de manière déterministe", sigSlot0, sigRepeat)
    }

    // =========================================================================
    // 6. TEST ISOLATION HERMÉTIQUE DES BASES DE DONNÉES PAR COMPTE
    // =========================================================================

    @Test
    fun testHermeticAccountStorageIsolation() {
        val line1Phone = "+213 550 12 34 56"
        val line2Phone = "+213 661 23 45 67"

        val cleanLine1 = AccountStorageManager.sanitizeAccountId(line1Phone)
        val cleanLine2 = AccountStorageManager.sanitizeAccountId(line2Phone)

        assertEquals("213550123456", cleanLine1)
        assertEquals("213661234567", cleanLine2)
        assertNotEquals("Les deux répertoires de comptes doivent être strictement disjoints", cleanLine1, cleanLine2)
    }
}
