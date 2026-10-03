package com.sha.orbis.call.fcm

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OrbisFirebasePushHelper — Envoie une impulsion de réveil FCM via Cloud Function.
 *
 * Déclenché en parallèle du signal Nostr OFFER lors d'un appel sortant.
 * Si le destinataire a un token FCM enregistré, un message de données haute priorité
 * réveille son système d'exploitation même en veille profonde.
 */
object OrbisFirebasePushHelper {

    private const val TAG = "OrbisFirebasePushHelper"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // URL par défaut de la Cloud Function (configurable dynamiquement)
    var cloudFunctionUrl: String = "https://europe-west1-orbisnet-63371.cloudfunctions.net/sendCallWakeup"

    /**
     * Envoie la notification de réveil FCM pour appel en tâche de fond (Dispatchers.IO).
     */
    fun sendWakeupPush(
        peerFcmToken: String,
        callId: String,
        callerPhone: String,
        callerName: String,
        isVideo: Boolean
    ) {
        if (peerFcmToken.isBlank()) {
            Log.d(TAG, "sendWakeupPush: peerFcmToken vide, réveil uniquement via Nostr.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("type", "call_wake")
                    put("token", peerFcmToken)
                    put("callId", callId)
                    put("callerPhone", callerPhone)
                    put("callerName", callerName)
                    put("isVideo", isVideo)
                }

                val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder()
                    .url(cloudFunctionUrl)
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Impulsion de réveil FCM appel délivrée avec succès pour callId=$callId")
                    } else {
                        Log.w(TAG, "sendWakeupPush HTTP ${response.code}: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "sendWakeupPush error: ${e.message}")
            }
        }
    }

    /**
     * Envoie la notification de réveil FCM pour message de chat en tâche de fond (Dispatchers.IO).
     */
    fun sendMessagePush(
        peerFcmToken: String,
        conversationId: String,
        senderPhone: String,
        senderName: String,
        textSnippet: String
    ) {
        if (peerFcmToken.isBlank()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("type", "message_wake")
                    put("token", peerFcmToken)
                    put("conversationId", conversationId)
                    put("senderPhone", senderPhone)
                    put("senderName", senderName)
                    put("textSnippet", textSnippet)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder()
                    .url(cloudFunctionUrl)
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Impulsion de réveil FCM message délivrée avec succès pour convId=$conversationId")
                    } else {
                        Log.w(TAG, "sendMessagePush HTTP ${response.code}: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "sendMessagePush error: ${e.message}")
            }
        }
    }
}
