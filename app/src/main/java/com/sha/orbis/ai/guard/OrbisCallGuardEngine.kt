package com.sha.orbis.ai.guard

import android.content.Context
import com.sha.orbis.ai.core.OrbisAiPreferences
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Proprietary ORBIS VoIP Call Guard Engine (100% Internet / WebRTC / Nostr).
 * Evaluates caller trustworthiness, spoofing risks, ping-call harassment, and call bursts.
 */
object OrbisCallGuardEngine {

    // In-memory sliding window for flood/burst detection: key (phone or npub) -> list of call timestamps
    private val callAttemptsHistory = ConcurrentHashMap<String, MutableList<Long>>()

    // In-memory record of short missed calls (duration < 5s) for ping-call callback trap detection
    private val pingCallHistory = ConcurrentHashMap<String, Long>()

    // Known authority / institution spoofing patterns in display names
    private val SUSPICIOUS_IMPERSONATION_KEYWORDS = listOf(
        "support", "service client", "assistance", "service technique",
        "admin", "administration", "banque", "bank", "bna", "cpa", "badr",
        "algerie poste", "poste", "baridinet", "baridimob", "securite", "sécurité",
        "service commercial", "djezzy", "mobilis", "ooredoo", "operat",
        "lottery", "loterie", "gagnant", "recharge", "gratuit", "free",
        "urgent", "virement", "compte bloque", "compte bloqué"
    )

    /**
     * Evaluates an incoming VoIP call in real-time.
     */
    fun analyzeIncomingCall(
        context: Context? = null,
        callerPhone: String,
        callerName: String,
        peerNostrKey: String? = null,
        isSavedContact: Boolean
    ): CallGuardAnalysisResult {
        if (context != null && !OrbisAiPreferences(context).isGuardEnabled) {
            return CallGuardAnalysisResult(
                threatLevel = ThreatLevel.SAFE,
                threatType = if (isSavedContact) CallThreatType.VERIFIED_CONTACT else CallThreatType.UNKNOWN_CALLER,
                trustScore = 100,
                threatTitle = "Protection Guard inactive",
                explanation = "L'analyse IA des appels est désactivée dans les paramètres.",
                isVerifiedContact = isSavedContact,
                shouldWarnUser = false
            )
        }

        // 1. Contact vérifié dans le carnet d'adresses
        if (isSavedContact) {
            return CallGuardAnalysisResult(
                threatLevel = ThreatLevel.SAFE,
                threatType = CallThreatType.VERIFIED_CONTACT,
                trustScore = 100,
                threatTitle = "Contact vérifié",
                explanation = "Ce correspondant est enregistré dans votre carnet d'adresses sécurisé.",
                riskFactors = emptyList(),
                isVerifiedContact = true,
                shouldWarnUser = false
            )
        }

        val identifier = peerNostrKey?.takeIf { it.isNotBlank() } ?: callerPhone.trim()
        val now = System.currentTimeMillis()

        // 2. Détection de Burst / Flood (plus de 3 appels dans les 2 dernières minutes)
        val history = callAttemptsHistory.getOrPut(identifier) { mutableListOf() }
        synchronized(history) {
            // Nettoyage des appels > 5 minutes
            history.removeAll { now - it > 5 * 60 * 1000L }
            history.add(now)
            if (history.size >= 4) {
                return CallGuardAnalysisResult(
                    threatLevel = ThreatLevel.HIGH_RISK,
                    threatType = CallThreatType.RAPID_BURST_FLOODING,
                    trustScore = 20,
                    threatTitle = "Alerte Harcèlement d'appels",
                    explanation = "Ce correspondant tente de vous joindre de façon répétée et anormale (${history.size} appels récents).",
                    riskFactors = listOf("Rafale d'appels entrants (${history.size} tentatives)", "Correspondant non répertorié"),
                    isVerifiedContact = false,
                    shouldWarnUser = true
                )
            }
        }

        // 3. Détection de Ping-Call (appel très court précédent suivi d'un nouvel appel)
        val lastPingTimestamp = pingCallHistory[identifier]
        if (lastPingTimestamp != null && now - lastPingTimestamp < 15 * 60 * 1000L) {
            return CallGuardAnalysisResult(
                threatLevel = ThreatLevel.HIGH_RISK,
                threatType = CallThreatType.PING_CALL_HARASSMENT,
                trustScore = 30,
                threatTitle = "Suspicion de Ping-Call",
                explanation = "Ce numéro a récemment provoqué un appel court manqué suspect.",
                riskFactors = listOf("Historique de ping-call récent (< 15 min)", "Numéro inconnu"),
                isVerifiedContact = false,
                shouldWarnUser = true
            )
        }

        // 4. Usurpation d'identité dans le nom d'affichage
        val lowerName = callerName.lowercase(Locale.ROOT).trim()
        val matchedKeyword = SUSPICIOUS_IMPERSONATION_KEYWORDS.firstOrNull { lowerName.contains(it) }
        if (matchedKeyword != null) {
            return CallGuardAnalysisResult(
                threatLevel = ThreatLevel.CRITICAL,
                threatType = CallThreatType.SUSPICIOUS_IDENTITY,
                trustScore = 15,
                threatTitle = "Alerte Usurpation d'Identité",
                explanation = "Ce correspondant utilise un nom prétendant appartenir à un service officiel ou financier (\"$matchedKeyword\").",
                riskFactors = listOf("Nom usurpateur suspect : \"$matchedKeyword\"", "Identité non certifiée dans vos contacts"),
                isVerifiedContact = false,
                shouldWarnUser = true
            )
        }

        // 5. Analyse horaire nocturne (entre 01h00 et 05h30 du matin)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val minute = Calendar.getInstance().get(Calendar.MINUTE)
        val isOffHours = (hour == 1 && minute >= 0) || (hour in 2..4) || (hour == 5 && minute <= 30)

        if (isOffHours) {
            return CallGuardAnalysisResult(
                threatLevel = ThreatLevel.SUSPICIOUS,
                threatType = CallThreatType.ANOMALOUS_OFF_HOURS,
                trustScore = 50,
                threatTitle = "Appel Nocturne Inconnu",
                explanation = "Appel tardif reçu à une heure inhabituelle depuis un numéro non enregistré.",
                riskFactors = listOf("Horaire nocturne inhabituel", "Numéro inconnu"),
                isVerifiedContact = false,
                shouldWarnUser = true
            )
        }

        // 6. Numéro inconnu standard
        return CallGuardAnalysisResult(
            threatLevel = ThreatLevel.SUSPICIOUS,
            threatType = CallThreatType.UNKNOWN_CALLER,
            trustScore = 65,
            threatTitle = "Numéro Inconnu",
            explanation = "Ce correspondant n'est pas enregistré dans vos contacts.",
            riskFactors = listOf("Correspondant non présent dans le carnet d'adresses"),
            isVerifiedContact = false,
            shouldWarnUser = false
        )
    }

    /**
     * Enregistre un appel manqué court pour enrichir la détection anti ping-call.
     */
    fun recordMissedCallDuration(identifier: String, durationSeconds: Int) {
        if (durationSeconds in 1..4) {
            pingCallHistory[identifier] = System.currentTimeMillis()
        }
    }
}
