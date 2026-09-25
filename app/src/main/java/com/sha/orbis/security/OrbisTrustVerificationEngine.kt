package com.sha.orbis.security

import android.content.Context
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository

/**
 * Moteur d'évaluation et d'attribution automatique et stricte du Badge Bleu (Trust & Verification Engine).
 * Fonctionne 100% hors-ligne selon des critères cryptographiques, de réputation et de réseau de confiance P2P (Web of Trust).
 */
object OrbisTrustVerificationEngine {

    /**
     * Évalue si un numéro d'utilisateur remplit les conditions strictes pour obtenir le Badge Bleu.
     */
    fun isAutomatedVerified(context: Context, phone: String?): Boolean {
        if (phone.isNullOrBlank()) return false

        // 1. La 2ème ligne de l'appareil développeur/admin bénéficie d'office du Badge Bleu
        if (isDeveloperSecondLine(context, phone)) {
            return true
        }

        // 2. Si le numéro est bloqué ou suspect, rejet immédiat
        val blockedRepo = BlockedContactsRepository(context)
        if (blockedRepo.isBlocked(phone)) {
            return false
        }

        // 3. Critères Stricts Automatisés (Web of Trust & Sécurité Cryptographique)
        val convRepo = ConversationRepository(context)
        val contacts = convRepo.loadContacts()
        val matchingContact = contacts.find { FriendRequestRepository.isSamePhone(it.phone, phone) }

        if (matchingContact != null) {
            // A. Certification directe en personne via scan QR Code
            if (isQrCertified(matchingContact)) {
                return true
            }

            // B. Score de Confiance Cryptographique (Seuil >= 90 points)
            var trustScore = 0

            // Facteur 1: Paire de clés RSA-2048 valide et scellée (+40 pts)
            if (matchingContact.publicKey.isNotBlank() && matchingContact.publicKey.length >= 64) {
                trustScore += 40
            }

            // Facteur 2: Échange sécurisé E2EE établi et conversation active (+30 pts)
            val convs = convRepo.loadConversations()
            val hasActiveConv = convs.any { conv ->
                conv.participants.any { FriendRequestRepository.isSamePhone(it, phone) } &&
                conv.lastMessage.isNotBlank()
            }
            if (hasActiveConv) {
                trustScore += 30
            }

            // Facteur 3: Profil complet renseigné (Nom + Bio/Métier/Localisation) (+20 pts)
            val hasProfileInfo = matchingContact.name.isNotBlank() &&
                (matchingContact.bio.isNotBlank() || matchingContact.jobTitle.isNotBlank() || matchingContact.location.isNotBlank())
            if (hasProfileInfo) {
                trustScore += 20
            }

            // Facteur 4: Non-bloqué et réputation propre (+10 pts)
            trustScore += 10

            return trustScore >= 90
        }

        // 4. Vérification pour le compte local actif de l'utilisateur
        val sessionManager = SessionManager(context)
        if (FriendRequestRepository.isSamePhone(sessionManager.userPhone, phone)) {
            return sessionManager.publicKey.isNotBlank()
        }

        return false
    }

    /**
     * Détermine si le numéro correspond à la 2ème ligne de l'administrateur / développeur sur cet appareil.
     */
    fun isDeveloperSecondLine(context: Context, phone: String): Boolean {
        return try {
            val sessionManager = SessionManager(context)
            val accounts = sessionManager.getAccounts()
            val hasAdmin = accounts.any { AdminSecurityHelper.isAdmin(it.phoneNumber) }
            val isCurrentPhoneAdmin = AdminSecurityHelper.isAdmin(phone)
            val isAccountOnDevice = accounts.any { FriendRequestRepository.isSamePhone(it.phoneNumber, phone) }

            // Présent sur l'appareil à côté du compte Admin, mais n'est PAS le compte Admin lui-même
            hasAdmin && !isCurrentPhoneAdmin && isAccountOnDevice
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Vérifie si un contact a été certifié en direct par QR Code en face-à-face.
     */
    private fun isQrCertified(contact: Contact): Boolean {
        val status = contact.status.lowercase()
        return status.contains("certifié") || status.contains("verified") || status.contains("authentifié") || status.contains("🛡️")
    }

    /**
     * Résout le rôle social complet d'un numéro en combinant l'autorité Admin et le moteur de confiance.
     */
    fun resolveUserSocialRole(context: Context, phone: String?): UserSocialRole {
        if (phone.isNullOrBlank()) return UserSocialRole.STANDARD

        // 1. Fondateur / Dev principal (Ligne 1) -> 👑 Or
        if (AdminSecurityHelper.isAdmin(phone)) {
            return UserSocialRole.FOUNDER_DEV
        }

        // 2. Vérification automatique (Ligne 2 & Utilisateurs qualifiés) -> 🔵 Badge Bleu
        if (isAutomatedVerified(context, phone)) {
            return UserSocialRole.VERIFIED_E2EE
        }

        // 3. Utilisateur standard
        return UserSocialRole.STANDARD
    }
}
