package com.sha.orbis.telemetry

import android.content.Context
import android.util.Log

/**
 * FeedTelemetryTracker — Module autonome de métriques pour le fil d'actualité et les posts sociaux.
 * Enregistre anonymement la création de publications (Orbis vs Extra-Orbis), consultations,
 * commentaires, réactions, médias, sondages et cercles/groupes.
 * Zero-knowledge : aucun contenu textuel ou image n'est inspecté ni conservé.
 */
object FeedTelemetryTracker {

    private const val TAG = "FeedTelemetry"

    fun trackPostCreated(context: Context, isExtraOrbis: Boolean = false, hasMedia: Boolean = false) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.FEED_POST_CREATE)
            tm.recordEvent(FeatureType.WALL_POST_CREATE)
            if (isExtraOrbis) {
                tm.recordEvent(FeatureType.FEED_POST_EXTRA)
            } else {
                tm.recordEvent(FeatureType.FEED_POST_ORBIS)
            }
            if (hasMedia) {
                tm.recordEvent(FeatureType.FEED_MEDIA_VIEW)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking post created: ${e.message}")
        }
    }

    fun trackPostViewed(context: Context, count: Int = 1) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.FEED_POST_VIEW, count.coerceAtLeast(1))
            tm.recordEvent(FeatureType.WALL_POST_VIEW, count.coerceAtLeast(1))
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking post viewed: ${e.message}")
        }
    }

    fun trackReaction(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.FEED_REACTION)
            tm.recordEvent(FeatureType.WALL_REACTION)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking reaction: ${e.message}")
        }
    }

    fun trackComment(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.FEED_COMMENT)
            tm.recordEvent(FeatureType.WALL_COMMENT)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking comment: ${e.message}")
        }
    }

    fun trackMediaViewed(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.FEED_MEDIA_VIEW)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking media viewed: ${e.message}")
        }
    }

    fun trackPollVote(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.FEED_POLL_VOTE)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking poll vote: ${e.message}")
        }
    }

    fun trackCircleAction(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.FEED_CIRCLE_ACTION)
            tm.recordEvent(FeatureType.FAMILY_CIRCLE)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking circle action: ${e.message}")
        }
    }

    fun trackGroupAction(context: Context) {
        try {
            val tm = TelemetryManager.getInstance(context)
            tm.recordEvent(FeatureType.FEED_GROUP_ACTION)
        } catch (e: Exception) {
            Log.d(TAG, "Error tracking group action: ${e.message}")
        }
    }
}
