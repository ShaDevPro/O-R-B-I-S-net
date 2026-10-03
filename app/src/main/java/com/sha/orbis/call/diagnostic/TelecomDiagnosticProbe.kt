package com.sha.orbis.call.diagnostic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.sha.orbis.call.telecom.OrbisConnectionService
import com.sha.orbis.call.telecom.OrbisTelecomHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sonde de diagnostic Telecom : enregistrement du PhoneAccount VoIP et permissions d'appel.
 */
object TelecomDiagnosticProbe {

    private const val TAG = "TelecomDiagnosticProbe"
    private const val ACCOUNT_ID = "OrbisVoIPAccount"

    suspend fun checkTelecom(context: Context): DiagnosticStepResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext DiagnosticStepResult(
                stepId = DiagnosticStepId.TELECOM,
                status = DiagnosticStatus.SUCCESS,
                detail = "Version Android antérieure à 8.0 — Mode In-App VoIP direct utilisé nativement."
            )
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return@withContext DiagnosticStepResult(
                stepId = DiagnosticStepId.TELECOM,
                status = DiagnosticStatus.FAILED,
                detail = "TelecomManager Android indisponible.",
                canAutoRepair = true
            )

        val componentName = ComponentName(context, OrbisConnectionService::class.java)
        val handle = PhoneAccountHandle(componentName, ACCOUNT_ID)

        val phoneAccount = try {
            telecomManager.getPhoneAccount(handle)
        } catch (e: Throwable) {
            null
        }

        if (phoneAccount == null) {
            return@withContext DiagnosticStepResult(
                stepId = DiagnosticStepId.TELECOM,
                status = DiagnosticStatus.WARNING,
                detail = "Le compte d'appel Orbis VoIP n'est pas encore enregistré dans Android Telecom.",
                canAutoRepair = true
            )
        }

        val isIncomingPermitted = try {
            telecomManager.isIncomingCallPermitted(handle)
        } catch (_: Throwable) {
            true
        }

        val isOutgoingPermitted = try {
            telecomManager.isOutgoingCallPermitted(handle)
        } catch (_: Throwable) {
            true
        }

        if (phoneAccount.isEnabled) {
            DiagnosticStepResult(
                stepId = DiagnosticStepId.TELECOM,
                status = DiagnosticStatus.SUCCESS,
                detail = "Compte VoIP Orbis activé (Entrant: $isIncomingPermitted, Sortant: $isOutgoingPermitted)."
            )
        } else {
            DiagnosticStepResult(
                stepId = DiagnosticStepId.TELECOM,
                status = DiagnosticStatus.WARNING,
                detail = "Compte VoIP enregistré mais désactivé dans les réglages système du téléphone.",
                canAutoRepair = true
            )
        }
    }

    /**
     * Enregistre ou réactive le PhoneAccount dans Android Telecom.
     */
    fun repairTelecom(context: Context): Boolean {
        return try {
            OrbisTelecomHelper.registerPhoneAccount(context)
            Log.i(TAG, "PhoneAccount ré-enregistré avec succès.")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Erreur réparation Telecom: ${e.message}")
            false
        }
    }

    /**
     * Ouvre les réglages système des comptes d'appels Android.
     */
    fun openPhoneAccountSettings(context: Context) {
        try {
            val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "Impossible d'ouvrir ACTION_CHANGE_PHONE_ACCOUNTS: ${e.message}")
        }
    }
}
