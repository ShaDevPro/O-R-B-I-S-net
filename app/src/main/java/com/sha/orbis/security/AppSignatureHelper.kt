package com.sha.orbis.security

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays

/**
 * AppSignatureHelper — Calcule dynamiquement le hachage d'application (11 caractères)
 * pour l'API Google SMS Retriever (SmsRetriever.getClient(context).startSmsRetriever()).
 *
 * Avantages décisifs par rapport à SMS User Consent :
 * 1. Zéro restriction de contact : Fonctionne même si l'expéditeur est dans les contacts (My Mobilis, 2ème SIM).
 * 2. 100% automatique et souverain : Réception directe sans dialogue d'autorisation bloquant.
 * 3. Robuste en double-SIM : Valide la réception réelle du message avec le bon hash.
 */
class AppSignatureHelper(context: Context) : ContextWrapper(context) {

    companion object {
        private const val TAG = "AppSignatureHelper"
        private const val HASH_TYPE = "SHA-256"
        private const val NUM_HASHED_BYTES = 9
        private const val NUM_BASE64_CHAR = 11
    }

    /**
     * Retourne la liste des hachages d'application pour les signatures de l'APK en cours d'exécution.
     */
    fun getAppSignatures(): List<String> {
        val appCodes = mutableListOf<String>()
        try {
            val packageName = packageName
            val packageManager = packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else emptyArray()
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                packageInfo.signatures ?: emptyArray()
            }

            if (signatures != null) {
                for (sig in signatures) {
                    val hash = hash(packageName, sig.toCharsString())
                    if (hash != null) {
                        appCodes.add(hash)
                    }
                }
            }
        } catch (e: Throwable) {
            logW(TAG, "Unable to get package signature: ${e.message}")
        }
        return appCodes
    }

    /**
     * Retourne le hachage d'application principal à 11 caractères, ou null.
     */
    fun getAppSignatureHash(): String? = getAppSignatures().firstOrNull()

    private fun hash(packageName: String, signature: String): String? {
        val appInfo = "$packageName $signature"
        return try {
            val messageDigest = MessageDigest.getInstance(HASH_TYPE)
            messageDigest.update(appInfo.toByteArray(StandardCharsets.UTF_8))
            var hashSignature = messageDigest.digest()

            // Tronqué à 9 octets selon la spécification Google SMS Retriever
            hashSignature = Arrays.copyOfRange(hashSignature, 0, NUM_HASHED_BYTES)
            // Encodé en Base64 sans padding
            var base64Hash = Base64.encodeToString(hashSignature, Base64.NO_PADDING or Base64.NO_WRAP)
            base64Hash = base64Hash.substring(0, NUM_BASE64_CHAR)

            base64Hash
        } catch (e: Throwable) {
            logW(TAG, "Hash generation failed: ${e.message}")
            null
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
