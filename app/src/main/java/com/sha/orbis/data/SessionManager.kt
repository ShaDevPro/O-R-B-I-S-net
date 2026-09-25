package com.sha.orbis.data

import android.content.Context
import androidx.core.content.edit
import com.sha.orbis.model.AccountProfile
import com.sha.orbis.security.CryptoKeyPair
import com.sha.orbis.security.Identity
import com.sha.orbis.security.RsaSigner
import org.json.JSONArray
import org.json.JSONObject

class SessionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "orbis_user_session"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_AUTHENTICATED = "authenticated"
        private const val KEY_THEME_MODE = "theme_mode" // "system", "dark", "light"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

        // Multi-Account Dual-SIM keys
        private const val KEY_ACCOUNTS_JSON = "accounts_profiles_json"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"

        // Legacy single-account keys (for automatic migration)
        private const val KEY_LEGACY_USER_NAME = "user_name"
        private const val KEY_LEGACY_USER_PHONE = "user_phone"
        private const val KEY_LEGACY_PUBLIC_KEY = "user_public_key"
        private const val KEY_LEGACY_PRIVATE_KEY = "user_private_key"
    }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_BIOMETRIC_ENABLED, value) }

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, value) }

    var isAuthenticated: Boolean
        get() = prefs.getBoolean(KEY_AUTHENTICATED, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTHENTICATED, value) }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        set(value) = prefs.edit { putString(KEY_THEME_MODE, value) }

    var activeAccountId: String
        get() {
            val current = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, "") ?: ""
            if (current.isNotBlank()) return current
            val accounts = getAccounts()
            val first = accounts.firstOrNull()?.id ?: ""
            if (first.isNotBlank()) {
                prefs.edit { putString(KEY_ACTIVE_ACCOUNT_ID, first) }
            }
            return first
        }
        set(value) = prefs.edit { putString(KEY_ACTIVE_ACCOUNT_ID, value) }

    val activeAccount: AccountProfile?
        get() {
            val accounts = getAccounts()
            val currentId = activeAccountId
            return accounts.firstOrNull { it.id == currentId } ?: accounts.firstOrNull()
        }

    val userName: String
        get() {
            val phone = userPhone
            if (com.sha.orbis.admin.AdminSecurityHelper.isAdmin(phone)) {
                return "O R B I S net"
            }
            return activeAccount?.name ?: prefs.getString(KEY_LEGACY_USER_NAME, "Utilisateur") ?: "Utilisateur"
        }

    val userPhone: String
        get() = activeAccount?.phoneNumber ?: prefs.getString(KEY_LEGACY_USER_PHONE, "") ?: ""

    val userSimSlotIndex: Int
        get() = activeAccount?.simSlotIndex ?: 0

    val userSubscriptionId: Int
        get() = activeAccount?.subscriptionId ?: -1

    val userOperatorName: String
        get() = activeAccount?.operatorName ?: "Opérateur GSM"

    val publicKey: String
        get() = activeAccount?.publicKeyBase64 ?: prefs.getString(KEY_LEGACY_PUBLIC_KEY, "") ?: ""

    val privateKey: String
        get() = activeAccount?.privateKeyBase64 ?: prefs.getString(KEY_LEGACY_PRIVATE_KEY, "") ?: ""

    val userAvatarPath: String?
        get() {
            if (com.sha.orbis.admin.AdminSecurityHelper.isAdmin(userPhone)) {
                return com.sha.orbis.ui.components.AvatarManager.ensureOfficialAppAvatar(context)
            }
            val custom = activeAccount?.avatarPath
            if (!custom.isNullOrBlank()) return custom
            return null
        }

    val userBio: String
        get() = activeAccount?.bio ?: ""

    val userJobTitle: String
        get() = activeAccount?.jobTitle ?: ""

    val userLocation: String
        get() = activeAccount?.location ?: ""

    val userInterests: List<String>
        get() = activeAccount?.interests ?: emptyList()

    // 1. Get All Stored Accounts (with legacy auto-migration)
    fun getAccounts(): List<AccountProfile> {
        val rawJson = prefs.getString(KEY_ACCOUNTS_JSON, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(rawJson)
                val list = mutableListOf<AccountProfile>()
                for (i in 0 until jsonArray.length()) {
                    list.add(AccountProfile.fromJson(jsonArray.getJSONObject(i)))
                }
                return list
            } catch (_: Exception) {
                // Ignore parse errors, fallback to legacy
            }
        }

        // Automatic legacy account migration
        val legacyPhone = prefs.getString(KEY_LEGACY_USER_PHONE, "") ?: ""
        val legacyPub = prefs.getString(KEY_LEGACY_PUBLIC_KEY, "") ?: ""
        val legacyPriv = prefs.getString(KEY_LEGACY_PRIVATE_KEY, "") ?: ""
        val legacyName = prefs.getString(KEY_LEGACY_USER_NAME, "Utilisateur") ?: "Utilisateur"

        if (legacyPhone.isNotBlank() && legacyPub.isNotBlank()) {
            val legacyAccount = AccountProfile(
                id = "acc_${legacyPhone.filter { it.isDigit() }}",
                name = legacyName,
                phoneNumber = legacyPhone,
                simSlotIndex = 0,
                subscriptionId = -1,
                operatorName = "SIM 1",
                countryIso = "DZ",
                publicKeyBase64 = legacyPub,
                privateKeyBase64 = legacyPriv,
                isDefault = true
            )
            val list = listOf(legacyAccount)
            saveAccounts(list)
            activeAccountId = legacyAccount.id
            return list
        }

        return emptyList()
    }

    // 2. Save Accounts List
    fun saveAccounts(accounts: List<AccountProfile>) {
        val jsonArray = JSONArray()
        accounts.forEach { jsonArray.put(it.toJson()) }
        prefs.edit { putString(KEY_ACCOUNTS_JSON, jsonArray.toString()) }
    }

    // 3. Add or Update an Account (Hermetic profile creation)
    fun addAccount(account: AccountProfile, setAsActive: Boolean = true) {
        val existing = getAccounts().toMutableList()
        val index = existing.indexOfFirst { it.id == account.id || it.phoneNumber == account.phoneNumber }
        if (index >= 0) {
            existing[index] = account
        } else {
            existing.add(account)
        }
        saveAccounts(existing)
        if (setAsActive || existing.size == 1) {
            activeAccountId = account.id
        }
        isAuthenticated = true
        isOnboardingCompleted = true
    }

    // 4. Switch Active Account Profile (WhatsApp / Truecaller style instant switch)
    fun switchAccount(accountId: String): Boolean {
        val accounts = getAccounts()
        if (accounts.any { it.id == accountId }) {
            activeAccountId = accountId
            com.sha.orbis.storage.SocialRepository.invalidateCache()
            try {
                com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).reconnect(force = true)
            } catch (_: Exception) {}
            try {
                com.sha.orbis.data.OrbisBadgeHub.refresh(context, accountId)
            } catch (_: Exception) {}
            return true
        }
        return false
    }

    // 5. Remove an Account Profile
    fun removeAccount(accountId: String) {
        val existing = getAccounts().filterNot { it.id == accountId }
        saveAccounts(existing)
        if (activeAccountId == accountId) {
            activeAccountId = existing.firstOrNull()?.id ?: ""
        }
        if (existing.isEmpty()) {
            isAuthenticated = false
        }
    }

    // 6. Setup Identity during initial Auth or Line Addition
    fun setupIdentity(
        name: String,
        phone: String,
        simSlotIndex: Int = 0,
        subscriptionId: Int = -1,
        operatorName: String = "SIM 1",
        countryIso: String = "DZ",
        avatarPath: String? = null,
        bio: String = "",
        jobTitle: String = "",
        location: String = "",
        interests: List<String> = emptyList(),
        cardId: Int = -1,
        iccIdHash: String? = null
    ): AccountProfile {
        val identity = Identity.create(phone)
        val accountId = "acc_${phone.filter { it.isDigit() }}"

        val validatedName = when {
            com.sha.orbis.admin.AdminSecurityHelper.isAdmin(phone) -> "O R B I S net"
            com.sha.orbis.admin.AdminSecurityHelper.isReservedName(name) -> "Utilisateur"
            else -> name.trim().ifBlank { "Utilisateur" }
        }

        val profile = AccountProfile(
            id = accountId,
            name = validatedName,
            phoneNumber = phone,
            simSlotIndex = simSlotIndex,
            subscriptionId = subscriptionId,
            operatorName = operatorName,
            countryIso = countryIso,
            publicKeyBase64 = identity.publicKeyBase64,
            privateKeyBase64 = identity.privateKeyBase64,
            avatarPath = avatarPath,
            bio = bio,
            jobTitle = jobTitle,
            location = location,
            interests = interests,
            isDefault = getAccounts().isEmpty(),
            cardId = cardId,
            iccIdHash = iccIdHash
        )

        addAccount(profile, setAsActive = true)
        return profile
    }

    fun updateActiveAccountAvatar(avatarPath: String) {
        val current = activeAccount ?: return
        val updated = current.copy(avatarPath = avatarPath)
        addAccount(updated, setAsActive = true)
    }

    fun updateActiveAccountName(newName: String) {
        val current = activeAccount ?: return
        val validatedName = when {
            com.sha.orbis.admin.AdminSecurityHelper.isAdmin(current.phoneNumber) -> "O R B I S net"
            com.sha.orbis.admin.AdminSecurityHelper.isReservedName(newName) -> current.name
            else -> newName.trim().ifBlank { current.name }
        }
        val updated = current.copy(name = validatedName)
        addAccount(updated, setAsActive = true)
    }

    fun updateProfileDetails(
        name: String,
        bio: String,
        jobTitle: String,
        location: String,
        interests: List<String>,
        avatarPath: String? = null
    ) {
        val current = activeAccount ?: return
        val validatedName = when {
            com.sha.orbis.admin.AdminSecurityHelper.isAdmin(current.phoneNumber) -> "O R B I S net"
            com.sha.orbis.admin.AdminSecurityHelper.isReservedName(name) -> current.name
            else -> name.trim().ifBlank { current.name }
        }
        val updated = current.copy(
            name = validatedName,
            bio = bio.trim(),
            jobTitle = jobTitle.trim(),
            location = location.trim(),
            interests = interests,
            avatarPath = avatarPath ?: current.avatarPath
        )
        addAccount(updated, setAsActive = true)
    }

    fun deleteAccountCompletely(accountId: String) {
        val current = getAccounts().filterNot { it.id == accountId }
        saveAccounts(current)
        if (current.isEmpty()) {
            clearSession()
            isAuthenticated = false
            isOnboardingCompleted = false
        } else {
            activeAccountId = current.first().id
        }
    }

    // 7. Get Dedicated Identity for Active Account Profile
    fun getOrCreateIdentity(): Identity {
        val currentAccount = activeAccount
        if (currentAccount != null) {
            return Identity(
                phoneNumber = currentAccount.phoneNumber,
                publicKeyBase64 = currentAccount.publicKeyBase64,
                privateKeyBase64 = currentAccount.privateKeyBase64,
                keyPair = CryptoKeyPair(
                    publicKey = RsaSigner.publicKeyFromBase64(currentAccount.publicKeyBase64),
                    privateKey = RsaSigner.privateKeyFromBase64(currentAccount.privateKeyBase64),
                    publicKeyBase64 = currentAccount.publicKeyBase64,
                    privateKeyBase64 = currentAccount.privateKeyBase64
                )
            )
        }

        val dummyPhone = "+0000000000"
        val newIdentity = Identity.create(dummyPhone)
        val defaultAccount = AccountProfile(
            id = "acc_default",
            name = "Utilisateur",
            phoneNumber = dummyPhone,
            simSlotIndex = 0,
            subscriptionId = -1,
            operatorName = "SIM 1",
            countryIso = "DZ",
            publicKeyBase64 = newIdentity.publicKeyBase64,
            privateKeyBase64 = newIdentity.privateKeyBase64,
            isDefault = true
        )
        addAccount(defaultAccount, setAsActive = true)
        return newIdentity
    }

    var isPresenceHidden: Boolean
        get() = prefs.getBoolean("presence_hidden", false)
        set(value) = prefs.edit { putBoolean("presence_hidden", value) }

    fun recordImplicitInterestInteraction(category: String, delta: Float = 1.0f) {
        if (category.isBlank()) return
        val current = getImplicitInterestWeights().toMutableMap()
        val existing = current[category] ?: 0f
        current[category] = (existing + delta).coerceAtMost(50f)
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v.toDouble()) }
        prefs.edit { putString("implicit_interest_weights", json.toString()) }
    }

    fun getImplicitInterestWeights(): Map<String, Float> {
        val raw = prefs.getString("implicit_interest_weights", null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, Float>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.optDouble(k, 0.0).toFloat()
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun clearSession() {
        prefs.edit { clear() }
    }
}
