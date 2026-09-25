package com.sha.orbis.telemetry

/**
 * Types de fonctionnalités mesurées anonymement par OrbisNet.
 * Aucune donnée privée (numéro, message, contact) n'est jamais transmise.
 */
enum class FeatureType(val key: String) {
    VOICE_CALL("call_voice"),
    VIDEO_CALL("call_video"),
    STORY_VIEW("story_view"),
    STORY_POST("story_post"),
    FAMILY_CIRCLE("family_circle_action"),
    NOSTR_CHAT("nostr_dm"),
    SOS_DURESS("sos_duress"),
    APP_LAUNCH("app_launch")
}

data class FeatureCount(
    val feature: String,
    val count: Int
)
