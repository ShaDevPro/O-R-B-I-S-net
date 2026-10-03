package com.sha.orbis.admin

import android.content.Context
import com.sha.orbis.telemetry.TelemetryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.TimeUnit

object AdminSecurityHelper {

    private const val PREFS_NAME = "orbis_admin_security_prefs"
    private const val KEY_VERIFIED_ADMIN_HASH = "verified_admin_phone_hash"

    // Empreinte cryptographique SHA-256 officielle du compte Fondateur / Dev (irréversible)
    private const val OFFICIAL_FOUNDER_DEV_PHONE_HASH = "ce9dc01159b9ea2abd62b50c89cff38e5ff494dfb40a5cf38100dd6ae6469a66"

    private var appContext: Context? = null
    private val verifiedAdminHashes = Collections.synchronizedSet(mutableSetOf<String>())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        appContext = context.applicationContext
        loadSavedHashes(context)
    }

    private fun loadSavedHashes(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_VERIFIED_ADMIN_HASH, null)
            if (!saved.isNullOrBlank()) {
                verifiedAdminHashes.add(saved)
            }
        } catch (_: Exception) {}
    }

    fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.startsWith("0") && digits.length >= 9 -> "213" + digits.substring(1)
            digits.length == 9 && (digits.startsWith("5") || digits.startsWith("6") || digits.startsWith("7")) -> "213" + digits
            digits.startsWith("213") -> digits
            else -> digits
        }
    }

    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isAdmin(phone: String?, context: Context? = null): Boolean {
        if (phone.isNullOrBlank()) return false
        val ctx = context ?: appContext
        val normalized = normalizePhone(phone)
        val hash = sha256(normalized)

        if (hash == OFFICIAL_FOUNDER_DEV_PHONE_HASH || verifiedAdminHashes.contains(hash)) return true

        if (ctx != null) {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_VERIFIED_ADMIN_HASH, null)
            if (saved != null && saved == hash) {
                verifiedAdminHashes.add(hash)
                return true
            }
        }
        return false
    }

    fun clearCache() {
        verifiedAdminHashes.clear()
        appContext?.let { loadSavedHashes(it) }
    }

    fun saveVerifiedAdmin(context: Context, normalizedPhone: String) {
        val hash = sha256(normalizedPhone)
        verifiedAdminHashes.add(hash)
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_VERIFIED_ADMIN_HASH, hash).apply()
        } catch (_: Exception) {}
    }

    fun verifyWithBackendSync(context: Context, phone: String, timeoutMs: Long = 3000): Boolean {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return false
        if (isAdmin(normalized, context)) return true

        return try {
            val backendUrl = TelemetryManager.DEFAULT_BACKEND_URL.removeSuffix("/")
            val url = "$backendUrl/api/admin/verify"
            val json = JSONObject().apply {
                put("phone", normalized)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val respBody = response.body?.string() ?: ""
                val respJson = JSONObject(respBody)
                val isAdm = respJson.optBoolean("isAdmin", false)
                if (isAdm) {
                    saveVerifiedAdmin(context, normalized)
                    return true
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.d("AdminSecurityHelper", "Backend admin verify failed: ${e.message}")
            false
        }
    }

    fun verifyWithBackendAsync(
        context: Context,
        phone: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) {
            onResult?.invoke(false)
            return
        }

        if (isAdmin(normalized, context)) {
            onResult?.invoke(true)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val isAdm = verifyWithBackendSync(context, normalized)
            withContext(Dispatchers.Main) {
                onResult?.invoke(isAdm)
            }
        }
    }

    /**
     * Asynchronously verifies a contact's phone against backend admin registry and caches the result.
     */
    fun verifyContactWithBackendAsync(phone: String, context: Context? = null) {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank() || isAdmin(normalized, context)) return
        val ctx = context ?: appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            verifyWithBackendSync(ctx, normalized)
        }
    }

    fun getUserSocialRole(phone: String?, context: Context? = null): com.sha.orbis.social.UserSocialRole {
        if (isAdmin(phone, context)) {
            return com.sha.orbis.social.UserSocialRole.FOUNDER_DEV
        }
        if (context != null) {
            return com.sha.orbis.security.OrbisTrustVerificationEngine.resolveUserSocialRole(context, phone)
        }
        return com.sha.orbis.social.UserSocialRole.STANDARD
    }

    /**
     * Checks if a display name matches "ORBIS", "O R B I S", "Orbis Officiel", or any reserved variation.
     */
    fun isReservedName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val clean = name.trim().lowercase().replace(" ", "").replace("_", "").replace("-", "").replace(".", "")
        return clean.contains("orbis")
    }

    /**
     * Ensures only the authorized dev/admin phone can use reserved Orbis names.
     */
    fun canUseName(phone: String?, name: String, context: Context? = null): Boolean {
        if (!isReservedName(name)) return true
        return isAdmin(phone, context)
    }

    fun isConfidentialIdentity(phone: String?, context: Context? = null): Boolean {
        return isAdmin(phone, context)
    }

    fun getDeveloperChannelPhone(context: Context? = null): String {
        val ctx = context ?: appContext
        return if (ctx != null) {
            try {
                com.sha.orbis.data.SessionManager(ctx).userPhone.ifBlank { "ORBIS_OFFICIAL" }
            } catch (_: Exception) {
                "ORBIS_OFFICIAL"
            }
        } else {
            "ORBIS_OFFICIAL"
        }
    }
}
