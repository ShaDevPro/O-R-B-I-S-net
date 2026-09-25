package com.sha.orbis.social

import android.content.Context
import android.util.Log
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.FriendRequestRepository

object SocialRecipientResolver {

    private const val TAG = "SocialRecipientResolver"

    /**
     * Resolves distinct peer phone numbers that should receive a social action
     * (Post, Story, Comment, Reaction, Poll vote, Deletion, Repost).
     *
     * In the Sovereign Friend-to-Friend Model:
     * 1. Direct explicit thread participants (Post author, comment authors, reaction authors).
     * 2. If targetCircleId is provided: Members of that specific circle.
     * 3. Otherwise (General Wall/Timeline): **Accepted Friends ONLY** from FriendRequestRepository.
     *
     * Automatically and strictly filters out:
     * - The active account user phone.
     * - All other local account phone numbers configured on this physical device.
     * - Blank or invalid/short numbers (< 8 digits).
     */
    fun resolveRecipients(
        context: Context,
        sessionManager: SessionManager,
        targetCircleId: String? = null,
        extraPhones: List<String> = emptyList(),
        excludedCircleIds: List<String> = emptyList(),
        excludedPhones: List<String> = emptyList()
    ): List<String> {
        val currentPhone = sessionManager.userPhone
        val allLocalPhones = mutableSetOf<String>()
        if (currentPhone.isNotBlank()) {
            allLocalPhones.add(currentPhone)
        }

        // Add all local account numbers on this device
        try {
            sessionManager.getAccounts().forEach { acc ->
                if (acc.phoneNumber.isNotBlank()) {
                    allLocalPhones.add(acc.phoneNumber)
                }
            }
        } catch (_: Exception) {}

        // Collect all explicitly excluded phone numbers
        val allExcludedPhones = mutableSetOf<String>()
        excludedPhones.forEach { p ->
            if (p.isNotBlank()) allExcludedPhones.add(ContactsPickerHelper.normalizePhoneNumber(p))
        }

        if (excludedCircleIds.isNotEmpty()) {
            try {
                val circleRepo = FriendCircleRepository(context)
                val circles = circleRepo.loadCircles()
                for (excludedId in excludedCircleIds) {
                    val circle = circles.find { it.id == excludedId }
                    circle?.memberPhones?.forEach { p ->
                        if (p.isNotBlank()) allExcludedPhones.add(ContactsPickerHelper.normalizePhoneNumber(p))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving excluded circles: ${e.message}")
            }
        }

        fun isLocalOrInvalid(phone: String): Boolean {
            val clean = phone.trim().filter { it.isDigit() || it == '+' }
            val digits = clean.filter { it.isDigit() }
            if (digits.length < 8) return true
            return allLocalPhones.any { local -> FriendRequestRepository.isSamePhone(clean, local) }
        }

        fun isExcluded(phone: String): Boolean {
            return allExcludedPhones.any { excluded -> FriendRequestRepository.isSamePhone(phone, excluded) }
        }

        val rawCandidates = mutableSetOf<String>()

        // 1. Add extra explicit thread participants (e.g. post author, comment authors, react authors)
        extraPhones.forEach { p ->
            if (p.isNotBlank()) {
                rawCandidates.add(p.trim())
            }
        }

        // 2. If restricted to a circle, add circle members only
        val targetAccountId = sessionManager.activeAccountId
        if (targetCircleId != null) {
            try {
                val circleRepo = FriendCircleRepository(context, targetAccountId)
                val circle = circleRepo.loadCircles().find { it.id == targetCircleId }
                circle?.memberPhones?.forEach { p ->
                    if (p.isNotBlank()) {
                        rawCandidates.add(p.trim())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading circle $targetCircleId: ${e.message}")
            }
        } else {
            // 3. Accepted friends + E2EE-connected contacts (same graph as chat/timeline)
            try {
                FriendRequestRepository(context, targetAccountId).getSocialPeerPhones().forEach { phone ->
                    rawCandidates.add(phone)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading social peers: ${e.message}")
            }
        }

        // Deduplicate and filter out local user numbers, short numbers, and all excluded peers
        val finalResult = mutableListOf<String>()
        for (candidate in rawCandidates) {
            val normalized = ContactsPickerHelper.normalizePhoneNumber(candidate)
            if (!isLocalOrInvalid(normalized) && !isExcluded(normalized)) {
                if (finalResult.none { FriendRequestRepository.isSamePhone(it, normalized) }) {
                    finalResult.add(normalized)
                }
            }
        }

        Log.i(TAG, "SocialRecipientResolver (Friends-Only): resolved ${finalResult.size} peer(s) for user ($currentPhone). Excluded: ${allExcludedPhones.size}. Recipients: $finalResult")
        return finalResult
    }
}
