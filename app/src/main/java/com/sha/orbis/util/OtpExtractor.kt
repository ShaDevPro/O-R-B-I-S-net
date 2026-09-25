package com.sha.orbis.util

import java.util.regex.Pattern

object OtpExtractor {

    private val OTP_KEYWORD_PATTERN = Pattern.compile(
        "(?i)(code|otp|verification|v[ée]rif|mot de passe|password|pin|authentification|auth|valider|validation|confirmation|confirmer|activation|activer|s[ée]curit[ée]|security|temporaire|temporary|passcode|token|google|github|whatsapp|telegram|uber|bank|banque|cib|cpa|baridi|djezzy|mobilis|ooredoo|algerie\\s*poste|paypal|tiktok|microsoft|apple|facebook|instagram|رمز|كود|تحقق|تأكيد|أمان|سري|تفعيل)"
    )

    // Prefix patterns: G-123456, FB-12345, GH-123456, etc.
    private val PREFIX_OTP_PATTERN = Pattern.compile("(?i)\\b([A-Z]{1,4}-\\d{4,8})\\b")

    // Explicit multilingual phrases (French, English, Arabic)
    private val EXPLICIT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:votre\\s+)?code(?:\\s+(?:d'|de\\s+)?(?:validation|confirmation|v[ée]rification|s[ée]curit[ée]|connexion|activation|d'accès))?(?:\\s+(?:est|is|pour|pour l'op[ée]ration\\s+\\w+))?\\s*[:=isestdeou\\s-]+\\s*([A-Z0-9-]{4,8})\\b"),
        Pattern.compile("(?i)(?:your\\s+)?(?:verification|security|confirmation|activation|login|access)?\\s*code\\s*(?:is)?\\s*[:=isestdeou\\s-]+\\s*([A-Z0-9-]{4,8})\\b"),
        Pattern.compile("(?i)(?:mot de passe\\s+temporaire|temporary\\s+password)\\s*(?:est|is)?\\s*[:=isestdeou\\s-]+\\s*([A-Z0-9-]{4,8})\\b"),
        Pattern.compile("(?i)(?:otp|pin|passcode)\\s*(?:code)?\\s*(?:est|is)?\\s*[:=isestdeou\\s-]+\\s*([A-Z0-9-]{4,8})\\b"),
        Pattern.compile("(?i)(?:رمز|كود)(?:\\s+(?:التحقق|التأكيد|الدخول|السري|الأمان|التفعيل))?(?:\\s+(?:هو|:|=))?\\s*[:=isestdeou\\s-]*\\s*([A-Z0-9-]{4,8})\\b"),
        Pattern.compile("(?i)\\b([A-Z0-9-]{4,8})\\s+(?:is your|est votre|est le code|is the code|هو رمز|هو كود)\\b")
    )

    // Split patterns (e.g. 123-456 or 123 456)
    private val SPLIT_OTP_PATTERN = Pattern.compile("\\b(\\d{3})[\\s-](\\d{3})\\b")

    // Standalone 4 to 8 digits
    private val NUMERIC_OTP_PATTERN = Pattern.compile("\\b(\\d{4,8})\\b")

    /**
     * Checks if a message text contains an OTP/verification code, and returns the extracted code if found.
     */
    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null

        // 1. Check for prefix codes like G-123456 or GH-123456
        val prefixMatcher = PREFIX_OTP_PATTERN.matcher(text)
        if (prefixMatcher.find()) {
            val code = prefixMatcher.group(1)?.trim()
            if (!code.isNullOrBlank()) {
                return code
            }
        }

        // 2. Try explicit keyword phrase patterns
        for (pattern in EXPLICIT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && candidate.length in 4..8 && candidate.any { it.isDigit() }) {
                    return candidate
                }
            }
        }

        // 3. If text contains any general OTP / Service keyword
        if (OTP_KEYWORD_PATTERN.matcher(text).find()) {
            // 3a. Check for split format (123-456 or 123 456)
            val splitMatcher = SPLIT_OTP_PATTERN.matcher(text)
            if (splitMatcher.find()) {
                val p1 = splitMatcher.group(1) ?: ""
                val p2 = splitMatcher.group(2) ?: ""
                if (p1.isNotBlank() && p2.isNotBlank()) {
                    return "$p1$p2"
                }
            }

            // 3b. Find all 4-8 digit standalone numbers
            val numericMatcher = NUMERIC_OTP_PATTERN.matcher(text)
            val candidates = mutableListOf<String>()
            while (numericMatcher.find()) {
                val num = numericMatcher.group(1)?.trim() ?: continue
                // Exclude phone numbers starting with Algerian prefixes or length >= 9
                if (num.length >= 9 || num.startsWith("05") || num.startsWith("06") || num.startsWith("07") || num.startsWith("213")) {
                    continue
                }
                candidates.add(num)
            }

            if (candidates.isNotEmpty()) {
                // Priority 1: 6-digit codes (standard TOTP/SMS OTP)
                val sixDigits = candidates.firstOrNull { it.length == 6 }
                if (sixDigits != null) return sixDigits

                // Priority 2: Non-year numbers (skip 1990..2035)
                val nonYears = candidates.firstOrNull { !(it.length == 4 && (it.startsWith("19") || it.startsWith("20"))) }
                if (nonYears != null) return nonYears

                return candidates.first()
            }
        }

        return null
    }

    /**
     * Returns true if the message is deemed an OTP / Service verification message.
     */
    fun isOtpMessage(text: String?): Boolean {
        return extract(text) != null
    }
}
