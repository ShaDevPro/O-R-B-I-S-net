package com.sha.orbis.ai.suggestions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Catégories d'intentions pour le refus poli et contextuel d'un appel entrant.
 */
enum class CallDeclineIntent(val icon: ImageVector) {
    IN_MEETING(Icons.Default.BusinessCenter),
    DRIVING(Icons.Default.DirectionsCar),
    BUSY_GENERAL(Icons.Default.HourglassBottom),
    CALL_BACK_SOON(Icons.Default.Schedule),
    TEXT_ME_INSTEAD(Icons.AutoMirrored.Filled.Chat),
    URGENT_CHECK(Icons.AutoMirrored.Filled.HelpOutline)
}

/**
 * Catégories d'intentions pour le suivi intelligent après un appel manqué.
 */
enum class MissedCallFollowUpIntent(val icon: ImageVector) {
    WHAT_HAPPENED(Icons.AutoMirrored.Filled.Message),
    CALL_BACK_NOW(Icons.Default.Call),
    RESCHEDULE_LATER(Icons.Default.AccessTime)
}

/**
 * Niveau de formalité / tonalité de la suggestion.
 */
enum class SuggestionTone {
    CASUAL,         // Familier, proche, amis
    PROFESSIONAL,   // Formel, collègues, travail
    CONCISE         // Court, direct, urgent
}

/**
 * Modèle de données unifié représentant une réponse rapide suggérée.
 */
data class QuickSuggestion(
    val id: String,
    val text: String,
    val declineIntent: CallDeclineIntent? = null,
    val followUpIntent: MissedCallFollowUpIntent? = null,
    val icon: ImageVector,
    val tone: SuggestionTone = SuggestionTone.CASUAL,
    val languageCode: String // "fr", "en", "ar", "dz"
)
