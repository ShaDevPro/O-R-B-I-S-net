package com.sha.orbis.telemetry

import android.content.Context
import android.util.Log

/**
 * MessagingTelemetryTracker — Module autonome de métriques pour la messagerie OrbisNet.
 * Enregistre anonymement les volumes de transmission par type de média (texte, audio, photo, doc, vidéo).
 * Zéro contenu analysé ou conservé : uniquement des compteurs de types d'échanges.
 */
object MessagingTelemetryTracker {

    private const val TAG = "MessagingTelemetry"

    fun trackTextMessage(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.MESSAGE_TEXT)
            tm.recordEvent(FeatureType.NOSTR_CHAT)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking text message: ${e.message}")
        }
    }

    fun trackVoiceNote(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.MESSAGE_VOICE_NOTE)
            tm.recordEvent(FeatureType.NOSTR_CHAT)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking voice note: ${e.message}")
        }
    }

    fun trackPhoto(context: Context, count: Int = 1) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.MESSAGE_PHOTO, count.coerceAtLeast(1))
            tm.recordEvent(FeatureType.NOSTR_CHAT)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking photo: ${e.message}")
        }
    }

    fun trackDocument(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.MESSAGE_DOC)
            tm.recordEvent(FeatureType.NOSTR_CHAT)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking document: ${e.message}")
        }
    }

    fun trackVideo(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.MESSAGE_VIDEO)
            tm.recordEvent(FeatureType.NOSTR_CHAT)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking video: ${e.message}")
        }
    }

    /**
     * Analyse le format de charge utile du message pour classifier et enregistrer
     * automatiquement l'événement sans altérer le flux de la conversation.
     */
    fun trackMessagePayload(context: Context, payload: String) {
        if (payload.isBlank()) return
        when {
            payload.startsWith("[AUDIO:") -> trackVoiceNote(context)
            payload.startsWith("[IMAGE:") || payload.startsWith("[ALBUM:") -> trackPhoto(context)
            payload.startsWith("[DOC:") -> trackDocument(context)
            payload.startsWith("[VIDEO:") -> trackVideo(context)
            else -> trackTextMessage(context)
        }
    }
}
