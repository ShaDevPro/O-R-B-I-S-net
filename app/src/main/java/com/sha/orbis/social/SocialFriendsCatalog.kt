package com.sha.orbis.social

import android.content.Context
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.FriendRequestStatus
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository

/**
 * Builds the canonical list of sovereign social peers (accepted friends + E2EE contacts).
 */
object SocialFriendsCatalog {

    data class Entry(
        val phone: String,
        val displayName: String,
        val avatarPath: String?,
        val isE2eeConnected: Boolean,
        val socialRole: UserSocialRole,
        val phoneShortLabel: String
    )

    fun load(context: Context, accountId: String? = null): List<Entry> {
        val targetAccountId = accountId?.takeIf { it.isNotBlank() } ?: try {
            SessionManager(context).activeAccountId
        } catch (_: Exception) { "" }

        val friendRepo = FriendRequestRepository(context, targetAccountId)
        val convRepo = ConversationRepository(context, targetAccountId)
        val blockedRepo = BlockedContactsRepository(context, targetAccountId)
        val contacts = convRepo.loadContacts()
        val requests = friendRepo.loadRequests()

        return friendRepo.getSocialPeerPhones()
            .filterNot { blockedRepo.isBlocked(it) }
            .map { phone ->
            val contact = contacts.find { FriendRequestRepository.isSamePhone(it.phone, phone) }
            val request = requests.find {
                FriendRequestRepository.isSamePhone(it.senderPhone, phone) &&
                    it.status == FriendRequestStatus.ACCEPTED
            } ?: requests.find { FriendRequestRepository.isSamePhone(it.senderPhone, phone) }

            val displayName = contact?.name?.takeIf { it.isNotBlank() }
                ?: request?.senderName?.takeIf { it.isNotBlank() }
                ?: phone

            val avatarPath = contact?.avatarPath ?: request?.senderAvatarPath
            val isE2ee = friendRepo.isConnectedContact(phone)
            val role = AdminSecurityHelper.getUserSocialRole(phone, context)
            val digits = phone.filter { it.isDigit() }
            val shortLabel = if (digits.length >= 6) "@${digits.takeLast(6)}" else phone

            Entry(
                phone = phone,
                displayName = displayName,
                avatarPath = avatarPath,
                isE2eeConnected = isE2ee,
                socialRole = role,
                phoneShortLabel = shortLabel
            )
        }.sortedBy { it.displayName.lowercase() }
    }
}
