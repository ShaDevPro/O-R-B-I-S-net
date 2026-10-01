package com.sha.orbis.ai.affinity

import android.content.Context
import com.sha.orbis.ai.core.OrbisAiStorage
import com.sha.orbis.ai.core.OrbisTokenizer
import java.util.Locale

/**
 * Moteur d'affinité et de détection active de la langue de l'interlocuteur.
 * Identifie la langue naturelle de chaque pair (Darija algérienne, Français, Arabe, Anglais)
 * à partir de ses messages entrants, de ses métadonnées (indicatif pays +213), et de l'historique d'échange.
 */
object OrbisPeerLanguageEngine {

    /**
     * Analyse un message entrant reçu d'un correspondant et met à jour son profil linguistique local.
     */
    fun onIncomingMessageReceived(context: Context, peerId: String, messageBody: String) {
        if (peerId.isBlank() || messageBody.isBlank()) return

        val cleanBody = messageBody.trim()
        // Ignorer les messages trop courts (ex: un seul emoji ou chiffre)
        if (cleanBody.length < 3) return

        val detectedLang = OrbisTokenizer.detectLanguage(cleanBody)
        val storage = OrbisAiStorage(context)
        storage.recordContactLanguage(peerId.trim(), detectedLang)
    }

    /**
     * Infère une langue par défaut basée sur l'indicatif téléphonique ou l'adresse souveraine.
     */
    fun inferInitialLanguage(peerPhoneOrAddress: String): String {
        val clean = peerPhoneOrAddress.trim().lowercase(Locale.ROOT)

        // Numéros algériens (+213, 00213, ou mobiles nationaux 05, 06, 07)
        if (clean.startsWith("+213") || clean.startsWith("00213") ||
            clean.startsWith("213") || (clean.length == 10 && (clean.startsWith("05") || clean.startsWith("06") || clean.startsWith("07")))
        ) {
            return "dz"
        }

        // Numéros français (+33, 0033)
        if (clean.startsWith("+33") || clean.startsWith("0033")) {
            return "fr"
        }

        // Numéros anglophones (+1, +44, etc.)
        if (clean.startsWith("+1") || clean.startsWith("+44")) {
            return "en"
        }

        // Fallback sur la locale système
        val defaultLocale = Locale.getDefault().language.lowercase(Locale.ROOT)
        return when (defaultLocale) {
            "ar" -> "ar"
            "en" -> "en"
            else -> "fr"
        }
    }

    /**
     * Résout la langue optimale pour un correspondant donné :
     * 1. Profil appris localement dans OrbisAiStorage
     * 2. Inférence géographique / préfixe téléphonique
     * 3. Locale du système
     */
    fun getPreferredLanguage(context: Context?, peerId: String?): String {
        if (peerId.isNullOrBlank()) {
            return inferInitialLanguage("")
        }

        val storage = OrbisAiStorage(context)
        val recordedLang = storage.getContactLanguage(peerId.trim())
        if (!recordedLang.isNullOrBlank()) {
            return recordedLang
        }

        return inferInitialLanguage(peerId)
    }

    /**
     * Force manuellement la langue préférée d'un correspondant avec haute priorité.
     */
    fun setManualLanguage(context: Context, peerId: String, lang: String) {
        if (peerId.isBlank() || lang.isBlank()) return
        val storage = OrbisAiStorage(context)
        storage.recordContactLanguage(peerId.trim(), lang.trim().lowercase(Locale.ROOT), forceManual = true)
    }

    /**
     * Retourne le libellé court avec drapeau pour l'affichage compact dans les badges.
     */
    fun getLanguageBadgeText(langCode: String?): String {
        return when (langCode?.lowercase(Locale.ROOT)) {
            "dz" -> "🇩🇿 DZ"
            "fr" -> "🇫🇷 FR"
            "en" -> "🇬🇧 EN"
            "ar" -> "🇸🇦 AR"
            else -> "🌐 ${langCode?.uppercase(Locale.ROOT) ?: "FR"}"
        }
    }
}
