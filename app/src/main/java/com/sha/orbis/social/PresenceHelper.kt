package com.sha.orbis.social

import android.content.Context
import com.sha.orbis.data.SessionManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestionnaire de présence et de statut en ligne souverain (style WhatsApp).
 * Règle de réciprocité absolue : Si l'utilisateur masque son statut, il ne diffuse rien
 * et ne peut voir le statut d'aucun contact (aucune fuite, pastille verte désactivée).
 */
object PresenceHelper {

    private const val ONLINE_WINDOW_MS = 2 * 60 * 1000L // 2 minutes d'inactivité max

    // Registre thread-safe d'activité réseau temps réel (clés Nostr, numéros de téléphone)
    private val peerActivityMap = ConcurrentHashMap<String, Long>()

    /**
     * Enregistre un battement d'activité réseau pour un contact distant (DM reçu, accusé, signal, réaction).
     */
    fun recordPeerActivity(peerKeyOrPhone: String) {
        val clean = peerKeyOrPhone.trim()
        if (clean.isNotBlank()) {
            val now = System.currentTimeMillis()
            peerActivityMap[clean] = now
            peerActivityMap[clean.lowercase()] = now
            val digits = clean.filter { it.isDigit() }
            if (digits.length >= 6) {
                peerActivityMap[digits.takeLast(8)] = now
                peerActivityMap[digits] = now
            }
        }
    }

    /**
     * Supprime immédiatement un contact du registre de présence en ligne (signal OFFLINE ou blocage).
     */
    fun recordPeerOffline(peerKeyOrPhone: String) {
        val clean = peerKeyOrPhone.trim()
        if (clean.isNotBlank()) {
            peerActivityMap.remove(clean)
            peerActivityMap.remove(clean.lowercase())
            val digits = clean.filter { it.isDigit() }
            if (digits.length >= 6) {
                peerActivityMap.remove(digits.takeLast(8))
                peerActivityMap.remove(digits)
            }
        }
    }

    /**
     * Détermine si un contact est actuellement en ligne.
     * @param peerPhone Numéro de téléphone du contact
     * @param peerPubkey Clé publique Nostr (hex ou npub)
     * @param lastIncomingMessageTimestamp Horodatage du dernier message envoyé PAR LE CONTACT (jamais l'utilisateur local)
     * @param context Contexte applicatif
     */
    fun isContactOnline(
        peerPhone: String? = null,
        peerPubkey: String? = null,
        lastIncomingMessageTimestamp: Long? = null,
        context: Context
    ): Boolean {
        val sessionManager = SessionManager(context)
        // Règle 1 : Réciprocité stricte (Mode furtif activé -> Impossible de voir qui est en ligne)
        if (sessionManager.isPresenceHidden) {
            return false
        }

        val now = System.currentTimeMillis()

        // Règle 2 : Vérifier le dernier message entrant du contact
        if (lastIncomingMessageTimestamp != null && lastIncomingMessageTimestamp > 0L) {
            val elapsed = now - lastIncomingMessageTimestamp
            if (elapsed in 0..ONLINE_WINDOW_MS) {
                return true
            }
        }

        // Règle 3 : Vérifier le registre d'activité réseau en direct
        val keysToCheck = mutableListOf<String>()
        if (!peerPhone.isNullOrBlank()) {
            val p = peerPhone.trim()
            keysToCheck.add(p)
            val digits = p.filter { it.isDigit() }
            if (digits.length >= 6) {
                keysToCheck.add(digits.takeLast(8))
                keysToCheck.add(digits)
            }
        }
        if (!peerPubkey.isNullOrBlank()) {
            val pk = peerPubkey.trim()
            keysToCheck.add(pk)
            keysToCheck.add(pk.lowercase())
        }

        for (key in keysToCheck) {
            val lastSeen = peerActivityMap[key]
            if (lastSeen != null && (now - lastSeen) in 0..ONLINE_WINDOW_MS) {
                return true
            }
        }

        return false
    }

    /**
     * Version de compatibilité basée uniquement sur l'horodatage d'activité.
     */
    fun isContactOnline(lastActivityTimestamp: Long, context: Context): Boolean {
        val sessionManager = SessionManager(context)
        if (sessionManager.isPresenceHidden) {
            return false
        }
        if (lastActivityTimestamp <= 0L) {
            return false
        }
        val elapsed = System.currentTimeMillis() - lastActivityTimestamp
        return elapsed in 0..ONLINE_WINDOW_MS
    }
}

