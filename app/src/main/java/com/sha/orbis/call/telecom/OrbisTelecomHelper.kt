package com.sha.orbis.call.telecom

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.sha.orbis.R

/**
 * OrbisTelecomHelper — Gestionnaire central de l'intégration Android Telecom (Core-Telecom).
 *
 * Enregistre le PhoneAccount en mode SELF_MANAGED pour garantir :
 *   - Appels 100% VoIP (Internet / Wi-Fi / 4G/5G)
 *   - Aucun coût ni composant GSM cellulaire
 *   - Priorité maximale de l'OS contre les tueurs de processus d'arrière-plan
 */
object OrbisTelecomHelper {

    private const val TAG = "OrbisTelecomHelper"
    private const val ACCOUNT_ID = "OrbisVoIPAccount"

    private var phoneAccountHandle: PhoneAccountHandle? = null

    /**
     * Enregistre le PhoneAccount auprès du TelecomManager Android (Android 8.0+).
     */
    fun registerPhoneAccount(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
            val componentName = ComponentName(context, OrbisConnectionService::class.java)
            val handle = PhoneAccountHandle(componentName, ACCOUNT_ID)
            phoneAccountHandle = handle

            val appName = context.getString(R.string.app_name)
            val icon = Icon.createWithResource(context, R.drawable.ic_logo)

            val builder = PhoneAccount.builder(handle, appName)
                .setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED or
                            PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                )
                .setIcon(icon)
                .setShortDescription("OrbisNet VoIP E2EE")

            val phoneAccount = builder.build()
            telecomManager.registerPhoneAccount(phoneAccount)
            Log.i(TAG, "PhoneAccount self-managed enregistré avec succès dans Android Telecom")
        } catch (e: Exception) {
            Log.w(TAG, "registerPhoneAccount: ${e.message}")
        }
    }

    /**
     * Signale un appel entrant à Android Telecom.
     */
    fun reportIncomingCall(
        context: Context,
        callId: String,
        callerPhone: String,
        callerName: String,
        isVideo: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
            val handle = phoneAccountHandle ?: run {
                registerPhoneAccount(context)
                phoneAccountHandle
            } ?: return false

            val callInfoBundle = Bundle().apply {
                putString("com.sha.orbis.CALL_ID", callId)
                putString("com.sha.orbis.PEER_PHONE", callerPhone)
                putString("com.sha.orbis.PEER_NAME", callerName)
                putBoolean("com.sha.orbis.IS_VIDEO", isVideo)
            }
            val extras = Bundle().apply {
                putAll(callInfoBundle)
                putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, callInfoBundle)
                putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.fromParts("sip", callerPhone.filter { it.isDigit() || it == '+' }, null)
                )
            }

            telecomManager.addNewIncomingCall(handle, extras)
            Log.i(TAG, "addNewIncomingCall transmis à Android Telecom pour callId=$callId")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "reportIncomingCall failed: ${e.message} — fallback sur notification locale")
            return false
        }
    }

    /**
     * Signale un appel sortant à Android Telecom.
     */
    fun startOutgoingCall(
        context: Context,
        callId: String,
        peerPhone: String,
        peerName: String,
        isVideo: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

        try {
            // Nettoyage préventif : fermer immédiatement toute connexion Telecom précédente restée ouverte
            val prev = OrbisConnectionService.getActiveConnection()
            if (prev != null) {
                try {
                    prev.notifyTerminated(android.telecom.DisconnectCause.LOCAL)
                    OrbisConnectionService.clearActiveConnection(prev)
                    Log.i(TAG, "Connexion Telecom précédente fermée préventivement")
                } catch (_: Throwable) {}
            }

            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
            val handle = phoneAccountHandle ?: run {
                registerPhoneAccount(context)
                phoneAccountHandle
            } ?: return false

            val callInfoBundle = Bundle().apply {
                putString("com.sha.orbis.CALL_ID", callId)
                putString("com.sha.orbis.PEER_PHONE", peerPhone)
                putString("com.sha.orbis.PEER_NAME", peerName)
                putBoolean("com.sha.orbis.IS_VIDEO", isVideo)
            }

            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, isVideo)
                putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callInfoBundle)
                putAll(callInfoBundle)
            }

            val uri = Uri.fromParts("sip", peerPhone.filter { it.isDigit() || it == '+' }, null)
            telecomManager.placeCall(uri, extras)
            Log.i(TAG, "placeCall transmis à Android Telecom pour callId=$callId")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "startOutgoingCall failed: ${e.message}")
            return false
        }
    }

    /**
     * Marque l'appel comme connecté auprès d'Android Telecom.
     */
    fun onCallConnected(callId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val conn = OrbisConnectionService.getActiveConnection()
            if (conn != null) {
                conn.notifyConnected()
                Log.i(TAG, "Android Telecom connection marquée active pour callId=$callId")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "onCallConnected error: ${e.message}")
        }
    }

    /**
     * Change le routage audio haut-parleur / écouteur dans Android Telecom.
     */
    fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            OrbisConnectionService.getActiveConnection()?.setAudioRouteSpeaker(on)
            Log.i(TAG, "setSpeaker($on) relayé à Android Telecom activeConnection")
        } catch (e: Throwable) {
            Log.w(TAG, "OrbisTelecomHelper.setSpeaker error: ${e.message}")
        }
    }

    /**
     * Notifie la fin de l'appel à Android Telecom.
     */
    fun onCallEnded(callId: String = "") {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val conn = OrbisConnectionService.getActiveConnection()
            if (conn != null) {
                conn.notifyTerminated(android.telecom.DisconnectCause.LOCAL)
                OrbisConnectionService.clearActiveConnection(conn)
                Log.i(TAG, "Android Telecom connection terminée pour callId=$callId (conn=${conn.callId})")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "onCallEnded error: ${e.message}")
        }
    }
}
