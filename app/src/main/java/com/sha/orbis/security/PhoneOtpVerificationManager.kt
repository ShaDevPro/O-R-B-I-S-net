package com.sha.orbis.security

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.sha.orbis.R
import java.util.concurrent.TimeUnit

object PhoneOtpVerificationManager {

    private const val TAG = "PhoneOtpVerification"
    private const val TIMEOUT_SECONDS = 60L

    interface OtpCallback {
        fun onCodeSent(verificationId: String, resendToken: PhoneAuthProvider.ForceResendingToken)
        fun onAutoVerified()
        fun onError(errorMessage: String)
    }

    fun sendVerificationCode(
        activity: Activity,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        callback: OtpCallback
    ) {
        try {
            val auth = FirebaseAuth.getInstance()
            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        // Instant auto-verification (Google Play Services SMS retrieval)
                        auth.signInWithCredential(credential)
                            .addOnCompleteListener(activity) { task ->
                                if (task.isSuccessful) {
                                    callback.onAutoVerified()
                                } else {
                                    callback.onError(task.exception?.localizedMessage ?: "Échec d'auto-vérification")
                                }
                            }
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        Log.e(TAG, "Firebase Phone Auth failed: ${e.message}", e)
                        val rawMsg = ((e.message ?: "") + " " + (e.localizedMessage ?: "")).lowercase()
                        val isBilling = rawMsg.contains("billing")
                        val isQuota = rawMsg.contains("quota_exceeded") || rawMsg.contains("quota exceeded") || rawMsg.contains("daily quota")

                        val displayMsg = when {
                            isBilling -> "L'envoi de SMS réels nécessite l'activation du forfait Blaze (Pay-as-you-go) dans la console Firebase, ou l'utilisation d'un numéro de test."
                            isQuota -> "Contingent d'activation journalier atteint."
                            else -> e.localizedMessage ?: e.message ?: "Erreur d'envoi du code de vérification."
                        }
                        callback.onError(displayMsg)
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        callback.onCodeSent(verificationId, token)
                    }
                })

            if (resendToken != null) {
                builder.setForceResendingToken(resendToken)
            }

            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        } catch (e: Exception) {
            callback.onError(e.localizedMessage ?: "Erreur d'initialisation du service SMS")
        }
    }

    fun verifyCode(
        verificationId: String,
        code: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        try {
            val auth = FirebaseAuth.getInstance()
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onResult(true, null)
                    } else {
                        onResult(false, task.exception?.localizedMessage ?: "Code de validation incorrect")
                    }
                }
        } catch (e: Exception) {
            onResult(false, e.localizedMessage ?: "Erreur de validation du code")
        }
    }
}
