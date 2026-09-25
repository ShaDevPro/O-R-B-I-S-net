package com.sha.orbis.ai.guard

/**
 * Severity level of the threat detected by ORBIS Guard-LLM.
 */
enum class ThreatLevel {
    SAFE,           // Legitimate message or standard personal SMS
    SUSPICIOUS,     // Potential unwanted advertising or unverified service
    HIGH_RISK,      // High probability of phishing or financial scam
    CRITICAL        // Urgent scam (credential harvesting, 2FA theft, impersonation)
}

/**
 * Category of threat identified by the neural classifier.
 */
enum class ThreatType {
    LEGITIMATE,             // Normal conversation or verified contact
    OTP_SECURITY,           // Authentic verification code / 2FA (Google, Bank, etc.)
    SPAM_COMMERCIAL,        // Unsolicited commercial spam or marketing
    PHISHING_BANK,          // Fake bank alert, suspended card, fraudulent payment link
    PHISHING_DELIVERY,      // Fake package delivery fee, parcel customs hold
    SCAM_EMERGENCY,         // Impersonation of family member or friend in distress
    SCAM_CRYPTO_LOTTERY     // Fake lottery winnings, crypto investment fraud
}

/**
 * Comprehensive analysis result returned by ORBIS Guard-LLM.
 */
data class GuardAnalysisResult(
    val threatLevel: ThreatLevel,
    val threatType: ThreatType,
    val confidenceScore: Float,       // Between 0.0 and 1.0 (e.g. 0.96 for 96%)
    val isSpam: Boolean,
    val threatTitle: String,
    val explanation: String,
    val riskFactors: List<String> = emptyList(),
    val isUserReinforced: Boolean = false
) {
    val isDangerous: Boolean
        get() = threatLevel == ThreatLevel.HIGH_RISK || threatLevel == ThreatLevel.CRITICAL
}
