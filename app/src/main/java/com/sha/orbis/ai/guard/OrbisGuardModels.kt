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

/**
 * Category of incoming VoIP call risk identified by ORBIS Guard-LLM.
 */
enum class CallThreatType {
    VERIFIED_CONTACT,        // Known address-book contact or established mutual peer
    TRUSTED_USER,            // User with verified Nostr identity and clean history
    UNKNOWN_CALLER,          // Peer not found in contacts, first interaction
    SUSPICIOUS_IDENTITY,     // Impersonates authority, bank, carrier or support in name/metadata
    RAPID_BURST_FLOODING,    // High-frequency incoming calls in short time window (Call Storm)
    PING_CALL_HARASSMENT,    // Short ring-and-drop pattern attempting callback trap
    ANOMALOUS_OFF_HOURS,     // Suspicious late-night unsolicited call from unknown peer
    POTENTIAL_SPAMMER        // Peer flagged in local heuristic blacklist or recurrent harassment
}

/**
 * Result of Call Guard evaluation on an incoming or missed VoIP call.
 */
data class CallGuardAnalysisResult(
    val threatLevel: ThreatLevel,
    val threatType: CallThreatType,
    val trustScore: Int,            // 0 (Dangerous) to 100 (Absolute Trust)
    val threatTitle: String,
    val explanation: String,
    val riskFactors: List<String> = emptyList(),
    val isVerifiedContact: Boolean = false,
    val shouldWarnUser: Boolean = false
) {
    val isSafe: Boolean
        get() = threatLevel == ThreatLevel.SAFE

    val isDangerous: Boolean
        get() = threatLevel == ThreatLevel.HIGH_RISK || threatLevel == ThreatLevel.CRITICAL
}

