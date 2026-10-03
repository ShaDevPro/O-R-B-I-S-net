package com.sha.orbis.telemetry

/**
 * Types de fonctionnalités mesurées anonymement par OrbisNet.
 * Aucune donnée privée (numéro, message, contact) n'est jamais transmise.
 */
enum class FeatureType(val key: String) {
    VOICE_CALL("call_voice"),
    VOICE_CALL_SUCCESS("call_voice_success"),
    VOICE_CALL_FAIL("call_voice_fail"),
    VIDEO_CALL("call_video"),
    VIDEO_CALL_SUCCESS("call_video_success"),
    VIDEO_CALL_FAIL("call_video_fail"),
    STORY_VIEW("story_view"),
    STORY_POST("story_post"),
    FAMILY_CIRCLE("family_circle_action"),
    NOSTR_CHAT("nostr_dm"),
    SOS_DURESS("sos_duress"),
    APP_LAUNCH("app_launch"),
    LINK_PREVIEW("link_preview_fetch"),
    WALL_POST_VIEW("wall_post_view"),
    WALL_POST_CREATE("wall_post_create"),
    WALL_COMMENT("wall_comment"),
    WALL_REACTION("wall_reaction"),

    // Messagerie Sécurisée E2EE (Zero-Knowledge)
    MESSAGE_TEXT("msg_text"),
    MESSAGE_VOICE_NOTE("msg_voice_note"),
    MESSAGE_PHOTO("msg_photo"),
    MESSAGE_DOC("msg_doc"),
    MESSAGE_VIDEO("msg_video"),

    // Fil d'Actualité & Social (Feed)
    FEED_POST_VIEW("feed_post_view"),
    FEED_POST_CREATE("feed_post_create"),
    FEED_POST_ORBIS("feed_post_orbis"),
    FEED_POST_EXTRA("feed_post_extra"),
    FEED_COMMENT("feed_comment"),
    FEED_REACTION("feed_reaction"),
    FEED_MEDIA_VIEW("feed_media_view"),
    FEED_POLL_VOTE("feed_poll_vote"),
    FEED_CIRCLE_ACTION("feed_circle_action"),
    FEED_GROUP_ACTION("feed_group_action")
}

data class FeatureCount(
    val feature: String,
    val count: Int
)

data class TechnicalErrorCount(
    val category: String,
    val count: Int
)
