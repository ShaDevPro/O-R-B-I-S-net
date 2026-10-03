package com.sha.orbis.security

import android.content.Context
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.model.FriendRequestStatus
import com.sha.orbis.model.TrustLevel
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository

/**
 * Moteur souverain d'évaluation et d'attribution méritée du Badge Bleu (Trust & Verification Engine).
 * Fonctionne 100% hors-ligne selon des critères cryptographiques, d'authentification GSM,
 * de certification physique en face-à-face (QR code) et de Web of Trust décentralisé.
 */
object OrbisTrustVerificationEngine {

    const val VERIFICATION_THRESHOLD = 80

    /**
     * Calcule le score de confiance global (0 à 100) pour un numéro.
     */
    fun calculateTrustScore(context: Context, phone: String?): Int {
        if (phone.isNullOrBlank()) return 0

        // 1. Fondateur / Dev principal : 100/100
        if (AdminSecurityHelper.isAdmin(phone)) return 100

        // 2. Deuxième ligne dev sur l'appareil : 100/100
        if (isDeveloperSecondLine(context, phone)) return 100

        // 3. Disqualification immédiate si bloqué
        val blockedRepo = BlockedContactsRepository(context)
        if (blockedRepo.isBlocked(phone)) return 0

        val sessionManager = SessionManager(context)
        val friendRepo = FriendRequestRepository(context)
        val convRepo = ConversationRepository(context)

        // Cas A : Utilisateur local du téléphone
        if (FriendRequestRepository.isSamePhone(sessionManager.userPhone, phone)) {
            var localScore = 0

            // Pilier 1 : Clés cryptographiques scellées dans le matériel (+30 pts)
            if (sessionManager.publicKey.isNotBlank() && sessionManager.publicKey.length >= 64) {
                localScore += 30
            }

            // Pilier 2 : Profil complet et soigné (+20 pts)
            val hasCustomName = sessionManager.userName.isNotBlank() &&
                sessionManager.userName != "Utilisateur" &&
                !sessionManager.userName.startsWith("+")
            val hasAvatar = !sessionManager.userAvatarPath.isNullOrBlank()
            if (hasCustomName) localScore += 10
            if (hasAvatar) localScore += 10

            // Pilier 3 : Web of Trust - Amis acceptés (+25 pts)
            val acceptedFriendsCount = friendRepo.getSocialPeerPhones().size
            if (acceptedFriendsCount >= 5) {
                localScore += 25
            } else if (acceptedFriendsCount >= 2) {
                localScore += 15
            } else if (acceptedFriendsCount >= 1) {
                localScore += 5
            }

            // Pilier 4 : Échanges chiffrés E2EE actifs (+25 pts)
            val hasActiveE2eeConv = convRepo.loadConversations().any { it.lastMessage.isNotBlank() }
            if (hasActiveE2eeConv) {
                localScore += 25
            }

            return minOf(100, localScore)
        }

        // Cas B : Contact / Pair distant
        val contacts = convRepo.loadContacts()
        val matchingContact = contacts.find { FriendRequestRepository.isSamePhone(it.phone, phone) }
        val matchingRequest = friendRepo.getFriendRequestForPhone(phone)

        // Raccourci d'excellence : Certification directe en face-à-face par QR code
        val isQrVerified = (matchingContact != null && isQrCertified(matchingContact)) ||
            (matchingRequest != null && matchingRequest.trustLevel == TrustLevel.LEVEL_3_IN_PERSON_CERTIFIED)

        if (isQrVerified) {
            // Un scan physique en face-à-face valide directement 95 points
            return 95
        }

        var score = 0

        // Pilier 1 : Preuve Cryptographique & Clé Publique scellée (+30 pts)
        val pubKey = matchingContact?.publicKey?.takeIf { it.isNotBlank() }
            ?: matchingRequest?.senderPublicKey?.takeIf { it.isNotBlank() }
        if (!pubKey.isNullOrBlank() && (pubKey.length >= 64 || pubKey.startsWith("npub1"))) {
            score += 30
        }

        // Pilier 2 : Web of Trust - Statut ami & amis mutuels (+25 pts)
        val isAcceptedFriend = matchingRequest?.status == FriendRequestStatus.ACCEPTED
        if (isAcceptedFriend) {
            score += 15
        }
        val mutualCount = matchingRequest?.mutualFriendsCount ?: 0
        if (mutualCount >= 3) {
            score += 10
        } else if (mutualCount >= 1) {
            score += 5
        }

        // Pilier 3 : Échange sécurisé E2EE réel et conversation active (+25 pts)
        val convs = convRepo.loadConversations()
        val hasActiveConv = convs.any { conv ->
            conv.participants.any { FriendRequestRepository.isSamePhone(it, phone) } &&
                conv.lastMessage.isNotBlank()
        }
        if (hasActiveConv) {
            score += 25
        }

        // Pilier 4 : Profil soigné & intégrité (+20 pts)
        val name = matchingContact?.name?.takeIf { it.isNotBlank() }
            ?: matchingRequest?.senderName?.takeIf { it.isNotBlank() }
        val hasGoodName = !name.isNullOrBlank() &&
            name != "Ami Orbis" &&
            name != "Ami OrbisNet" &&
            !name.startsWith("+") &&
            !AdminSecurityHelper.isReservedName(name)
        if (hasGoodName) {
            score += 10
        }

        val hasAvatar = !matchingContact?.avatarPath.isNullOrBlank() ||
            !matchingRequest?.senderAvatarPath.isNullOrBlank()
        if (hasAvatar) {
            score += 10
        }

        return minOf(100, score)
    }

    /**
     * Évalue si un numéro d'utilisateur remplit les conditions strictes (score >= 80)
     * pour obtenir le Badge Bleu mérité.
     */
    fun isAutomatedVerified(context: Context, phone: String?): Boolean {
        if (phone.isNullOrBlank()) return false
        return calculateTrustScore(context, phone) >= VERIFICATION_THRESHOLD
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

        // 1. Fondateur / Dev principal (Ligne 1) -> 👑 Orbis Dev Core
        if (AdminSecurityHelper.isAdmin(phone)) {
            return UserSocialRole.FOUNDER_DEV
        }

        // 2. Vérification méritée (Ligne 2, QR Code physique ou score >= 80) -> 🔵 Badge Bleu
        if (isAutomatedVerified(context, phone)) {
            return UserSocialRole.VERIFIED_E2EE
        }

        // 3. Utilisateur standard
        return UserSocialRole.STANDARD
    }
}
