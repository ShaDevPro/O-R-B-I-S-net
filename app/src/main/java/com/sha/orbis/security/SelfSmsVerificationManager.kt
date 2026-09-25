package com.sha.orbis.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.sha.orbis.R
import java.security.SecureRandom
import java.util.Locale

/**
 * Sovereign local Self-SMS verification manager (Solution A).
 * - Zéro permission requise dans AndroidManifest (ni SEND_SMS, ni RECEIVE_SMS).
 * - Utilise simultanément :
 *   1. Google SMS Retriever API (0-tap, 100% automatique, zéro restriction de contact, hash d'app 11 car.)
 *   2. Google SMS User Consent API (1-tap avec dialogue système de secours)
 * - Jeton de sécurité cryptographique de 8 caractères hexadécimaux (respecte la norme Google OTP 4-10 car.)
 * - Émission loopback via ACTION_SENDTO.
 * - Supporte le Bi-SIM (envoi depuis SIM 2 vers SIM 1 cible même si la SIM 1 a 0 DA).
 */
object SelfSmsVerificationManager {

    private const val TAG = "SelfSmsVerification"
    private const val CODE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    private const val MAX_ATTEMPTS = 3

    private val secureRandom = SecureRandom()

    private var activeCode: String? = null
    private var activePhoneNumber: String? = null
    private var activeToken: String? = null
    private var codeTimestamp: Long = 0L
    private var attemptCount: Int = 0

    data class VerificationChallenge(
        val token: String,
        val intent: Intent,
        val messageBody: String,
        val appSignatureHash: String? = null
    )

    /**
     * Génère un jeton cryptographique de session de 8 caractères hexadécimaux (respecte la norme Google OTP 4-10 car.).
     */
    fun generateSessionToken(phoneNumber: String): String {
        val randomBytes = ByteArray(4).also { secureRandom.nextBytes(it) }
        val input = "$phoneNumber:${System.currentTimeMillis()}:${randomBytes.joinToString("") { "%02X".format(it) }}"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02X".format(it) } // 8 uppercase hex chars
    }

    /**
     * Prépare une session de défi et génère le corps du SMS avec le préfixe <#> et le hash d'application.
     */
    fun prepareChallenge(
        context: Context,
        phoneNumber: String,
        subscriptionId: Int = -1
    ): VerificationChallenge {
        val appContext = context.applicationContext
        val challengeToken = generateSessionToken(phoneNumber)

        activeToken = challengeToken
        activePhoneNumber = phoneNumber
        activeCode = challengeToken // for test compatibility
        codeTimestamp = System.currentTimeMillis()
        attemptCount = 0

        val appSignatureHash = try {
            AppSignatureHelper(appContext).getAppSignatureHash()
        } catch (_: Throwable) { null }

        val baseBody = appContext.getString(R.string.auth_self_sms_message_body, challengeToken)
        // Format Google SMS Retriever : préfixe <#> et terminaison par le hachage d'application à 11 caractères
        val smsBody = if (!appSignatureHash.isNullOrBlank()) {
            "<#> $baseBody\n$appSignatureHash"
        } else {
            "<#> $baseBody"
        }

        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", smsBody)
            putExtra(Intent.EXTRA_TEXT, smsBody)
            if (subscriptionId >= 0) {
                putExtra("android.telephony.extra.SUBSCRIPTION_ID", subscriptionId)
                putExtra("subscription_id", subscriptionId)
            }
        }

        // 1. Démarrer Google SMS Retriever API (0-tap automatique, sans restriction de contacts)
        startSmsRetriever(appContext)

        // 2. Démarrer Google SMS User Consent API en parallèle (secours)
        startSmsUserConsent(appContext)

        logI(TAG, "Challenge prepared for $phoneNumber (Token=$challengeToken, AppHash=$appSignatureHash)")
        return VerificationChallenge(
            token = challengeToken,
            intent = smsIntent,
            messageBody = smsBody,
            appSignatureHash = appSignatureHash
        )
    }

    /**
     * Démarre Google SMS Retriever API (automatique, 0 clic, compatible même si le contact est sauvegardé).
     */
    fun startSmsRetriever(context: Context) {
        try {
            SmsRetriever.getClient(context).startSmsRetriever()
                .addOnSuccessListener {
                    logI(TAG, "SmsRetriever client started successfully")
                }
                .addOnFailureListener { e ->
                    logW(TAG, "SmsRetriever client failed: ${e.message}")
                }
        } catch (e: Throwable) {
            logW(TAG, "SmsRetriever unavailable: ${e.message}")
        }
    }

    /**
     * Démarre Google SMS User Consent API.
     */
    fun startSmsUserConsent(context: Context) {
        try {
            SmsRetriever.getClient(context).startSmsUserConsent(null)
                .addOnSuccessListener {
                    logI(TAG, "SmsUserConsent listener started successfully")
                }
                .addOnFailureListener { e ->
                    logW(TAG, "SmsUserConsent listener failed: ${e.message}")
                }
        } catch (e: Throwable) {
            logW(TAG, "SmsRetriever UserConsent unavailable: ${e.message}")
        }
    }

    /**
     * Vérifie le message SMS reçu depuis le réseau GSM via Google SMS Retriever ou User Consent.
     */
    fun verifyIncomingMessage(message: String): Boolean {
        val currentToken = activeToken
        if (currentToken.isNullOrBlank()) {
            logW(TAG, "verifyIncomingMessage: no active challenge token")
            return false
        }

        if (System.currentTimeMillis() - codeTimestamp > CODE_VALIDITY_MS) {
            logW(TAG, "verifyIncomingMessage: challenge expired")
            clear()
            return false
        }

        val isMatch = message.contains(currentToken, ignoreCase = true)
        logI(TAG, "verifyIncomingMessage match=$isMatch (token=$currentToken)")
        if (isMatch) {
            clear()
            return true
        }
        return false
    }

    /**
     * Vérifie directement un jeton (ex: issu du presse-papier ou saisi par l'utilisateur).
     */
    fun verifyToken(input: String): Boolean {
        val currentToken = activeToken
        if (currentToken.isNullOrBlank()) return false
        if (System.currentTimeMillis() - codeTimestamp > CODE_VALIDITY_MS) {
            clear()
            return false
        }

        val clean = input.trim()
        val isMatch = clean.contains(currentToken, ignoreCase = true) ||
                clean.equals(currentToken, ignoreCase = true)
        logI(TAG, "verifyToken match=$isMatch")
        if (isMatch) {
            clear()
            return true
        }
        return false
    }

    /**
     * Extrait le jeton ou code d'un message SMS.
     */
    fun extractOtpFromMessage(message: String): String? {
        val hashRegex = Regex("""\[HASH:([A-Fa-f0-9]{6,16})\]""", RegexOption.IGNORE_CASE)
        val hashMatch = hashRegex.find(message)
        if (hashMatch != null) {
            return hashMatch.groupValues[1].uppercase(Locale.ROOT)
        }
        val token = activeToken
        if (token != null && message.contains(token, ignoreCase = true)) {
            return token
        }
        val regex = Regex("""\b([A-Fa-f0-9]{6,8})\b""")
        val match = regex.find(message)
        return match?.value
    }

    fun extractTokenFromMessage(message: String): String? = extractOtpFromMessage(message)

    fun isChallengePending(): Boolean =
        activeToken != null && (System.currentTimeMillis() - codeTimestamp <= CODE_VALIDITY_MS)

    fun verifyCode(context: Context? = null, enteredCode: String): Pair<Boolean, String?> {
        val current = activeCode ?: activeToken
        if (current == null) {
            return Pair(false, "Code de vérification incorrect ou expiré.")
        }
        if (System.currentTimeMillis() - codeTimestamp > CODE_VALIDITY_MS) {
            clear()
            return Pair(false, "Le code de vérification a expiré (délai 5 min).")
        }
        if (attemptCount >= MAX_ATTEMPTS) {
            clear()
            return Pair(false, "Nombre maximal de tentatives dépassé.")
        }
        if (enteredCode.trim().equals(current.trim(), ignoreCase = true)) {
            clear()
            return Pair(true, null)
        } else {
            attemptCount++
            val remaining = MAX_ATTEMPTS - attemptCount
            val msg = if (remaining > 0) "Code incorrect. Tentatives restantes : $remaining" else "Nombre maximal de tentatives dépassé."
            if (remaining <= 0) clear()
            return Pair(false, msg)
        }
    }

    fun setTestCode(code: String, phoneNumber: String, timestamp: Long = System.currentTimeMillis()) {
        activeCode = code
        activePhoneNumber = phoneNumber
        activeToken = code
        codeTimestamp = timestamp
        attemptCount = 0
    }

    fun getActiveToken(): String? = activeToken

    fun clear() {
        activeCode = null
        activePhoneNumber = null
        activeToken = null
        codeTimestamp = 0L
        attemptCount = 0
    }

    private fun logI(tag: String, msg: String) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logW(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] WARN: $msg")
        }
    }
}
