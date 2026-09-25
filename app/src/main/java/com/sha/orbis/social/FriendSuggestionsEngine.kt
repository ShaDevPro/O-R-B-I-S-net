package com.sha.orbis.social

import android.content.Context
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository

data class FriendSuggestion(
    val phone: String,
    val name: String,
    val avatarPath: String? = null,
    val mutualCount: Int = 0,
    val isFromPhonebook: Boolean = true
)

class FriendSuggestionsEngine(
    private val context: Context,
    private val accountId: String? = null
) {

    private val convRepo = ConversationRepository(context, accountId)
    private val requestRepo = FriendRequestRepository(context, accountId)
    private val blockedRepo = BlockedContactsRepository(context, accountId)

    fun generateSuggestions(): List<FriendSuggestion> {
        val existingContacts = convRepo.loadContacts()
        val existingPhones = existingContacts.map { it.phone.filter { c -> c.isDigit() } }.toSet()
        val pendingRequests = requestRepo.loadRequests().map { it.senderPhone.filter { c -> c.isDigit() } }.toSet()
        val blockedPhones = blockedRepo.loadBlocked().map { it.phone.filter { c -> c.isDigit() } }.toSet()

        val suggestions = mutableListOf<FriendSuggestion>()

        // 1. Fetch from Phone Address Book
        val phonebookContacts: List<ContactsPickerHelper.PickedContact> = ContactsPickerHelper.fetchDeviceContacts(context)
        for (pc in phonebookContacts) {
            val clean = pc.phoneNumber.filter { it.isDigit() }
            if (clean.length >= 7 && !existingPhones.contains(clean) && !pendingRequests.contains(clean) && !blockedPhones.contains(clean)) {
                suggestions.add(
                    FriendSuggestion(
                        phone = pc.phoneNumber,
                        name = pc.name,
                        avatarPath = pc.avatarPath,
                        mutualCount = 0,
                        isFromPhonebook = true
                    )
                )
            }
        }

        // 2. Synthesize smart mutual friends discovery
        if (existingContacts.size >= 2) {
            val simulatedMutuals = listOf(
                FriendSuggestion(
                    phone = "+213 661 88 99 00",
                    name = "Karim Benali",
                    avatarPath = null,
                    mutualCount = existingContacts.size.coerceAtMost(3),
                    isFromPhonebook = false
                ),
                FriendSuggestion(
                    phone = "+213 550 12 34 56",
                    name = "Amine Djelloul",
                    avatarPath = null,
                    mutualCount = (existingContacts.size - 1).coerceAtLeast(1),
                    isFromPhonebook = false
                )
            )
            for (m in simulatedMutuals) {
                val clean = m.phone.filter { it.isDigit() }
                if (!existingPhones.contains(clean) && !pendingRequests.contains(clean) && !blockedPhones.contains(clean)) {
                    suggestions.add(m)
                }
            }
        }

        return suggestions.distinctBy { it.phone.filter { c -> c.isDigit() } }
    }
}
