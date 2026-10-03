package com.sha.orbis.ai.reply

/**
 * Message intent detected by ORBIS Reply-LLM.
 */
enum class MessageIntent {
    GREETING,           // "Bonjour", "Salam", "Hey"
    HOW_ARE_YOU,        // "Comment ça va ?", "Labas ?", "How are you?"
    LOCATION_QUERY,     // "Tu es où ?", "Fink ?", "Where are you?"
    TIME_QUERY,         // "À quelle heure ?", "What time?", "Weqtach ?"
    YES_NO_QUERY,       // "Tu viens ?", "Are you coming?", "Gha tji ?"
    GRATITUDE,          // "Merci beaucoup", "Choukrane", "Thanks"
    CONFIRMATION,       // "C'est bon", "OK", "Safie"
    APOLOGY,            // "Désolé pour le retard", "Smehli"
    URGENT_CALL,        // "Appelle-moi vite", "3ayet lia"
    GENERIC             // Standard statement or general chat
}

/**
 * Individual smart reply suggestion generated for the user.
 */
data class ReplySuggestion(
    val text: String,
    val intent: MessageIntent,
    val confidenceScore: Float,
    val isPersonalized: Boolean = false // True if reinforced or learned from user on-device history
)

/**
 * Result returned by ORBIS Reply-LLM inference.
 */
data class SuggestedRepliesResult(
    val detectedIntent: MessageIntent,
    val detectedLanguage: String,
    val confidence: Float,
    val suggestions: List<ReplySuggestion>
)
