package com.sha.orbis.ai.reply

import android.content.Context
import com.sha.orbis.ai.core.OrbisAiPreferences
import com.sha.orbis.ai.core.OrbisAiStorage
import com.sha.orbis.ai.core.OrbisTokenizer
import com.sha.orbis.ai.core.OrbisVectorMath
import java.util.Locale

/**
 * Proprietary ORBIS Reply-LLM Inference Engine.
 * Ultra-fast native vectorized generator for smart, contextual 1-tap responses.
 * Adapts dynamically to the user's personal communication habits and preferred phrasing.
 */
object OrbisReplyEngine {

    /**
     * Generates intelligent 1-tap reply suggestions for a given incoming message.
     */
    fun generateReplies(
        context: Context? = null,
        incomingMessage: String,
        maxSuggestions: Int = 3
    ): SuggestedRepliesResult {
        if (context != null && !OrbisAiPreferences(context).isReplyEnabled) {
            return SuggestedRepliesResult(
                detectedIntent = MessageIntent.GENERIC,
                detectedLanguage = "fr",
                confidence = 0.0f,
                suggestions = emptyList()
            )
        }

        if (incomingMessage.isBlank()) {
            return SuggestedRepliesResult(
                detectedIntent = MessageIntent.GENERIC,
                detectedLanguage = "fr",
                confidence = 0.0f,
                suggestions = emptyList()
            )
        }

        // 1. Multilingual language detection
        val detectedLang = OrbisTokenizer.detectLanguage(incomingMessage)

        // 2. Vectorization of incoming text into 64-dim embedding
        val inputVector = OrbisTokenizer.encodeToEmbedding(incomingMessage)

        // 3. Cosine similarity against pre-trained intent centroids
        val simGreeting = OrbisVectorMath.cosineSimilarity(inputVector, OrbisReplyWeights.GREETING_CENTROID)
        val simHowAreYou = OrbisVectorMath.cosineSimilarity(inputVector, OrbisReplyWeights.HOW_ARE_YOU_CENTROID)
        val simLocation = OrbisVectorMath.cosineSimilarity(inputVector, OrbisReplyWeights.LOCATION_CENTROID)
        val simTime = OrbisVectorMath.cosineSimilarity(inputVector, OrbisReplyWeights.TIME_CENTROID)
        val simYesNo = OrbisVectorMath.cosineSimilarity(inputVector, OrbisReplyWeights.YES_NO_CENTROID)
        val simGratitude = OrbisVectorMath.cosineSimilarity(inputVector, OrbisReplyWeights.GRATITUDE_CENTROID)
        val simConfirmation = OrbisVectorMath.cosineSimilarity(inputVector, OrbisReplyWeights.CONFIRMATION_CENTROID)
        val simApology = OrbisVectorMath.cosineSimilarity(inputVector, OrbisReplyWeights.APOLOGY_CENTROID)
        val simUrgentCall = OrbisVectorMath.cosineSimilarity(inputVector, OrbisReplyWeights.URGENT_CALL_CENTROID)

        // 4. Keyword and syntactic boost
        val lower = incomingMessage.lowercase(Locale.ROOT)
        val isQuestion = lower.contains("?") || lower.contains("؟")

        var boostGreeting = 0f
        var boostHowAreYou = 0f
        var boostLocation = 0f
        var boostTime = 0f
        var boostYesNo = 0f
        var boostGratitude = 0f
        var boostUrgent = 0f

        if (lower.contains("bonjour") || lower.contains("salut") || lower.contains("hello") || lower.contains("salam") || lower.contains("coucou")) {
            boostGreeting += 0.25f
        }
        if (lower.contains("ça va") || lower.contains("ca va") || lower.contains("comment vas") || lower.contains("labas") || lower.contains("how are you")) {
            boostHowAreYou += 0.35f
        }
        if (lower.contains("où") || lower.contains("ou es") || lower.contains("t'es ou") || lower.contains("where") || lower.contains("fink") || lower.contains("fein")) {
            boostLocation += 0.35f
        }
        if (lower.contains("heure") || lower.contains("quand") || lower.contains("what time") || lower.contains("weqtach") || lower.contains("chhal f sa3a")) {
            boostTime += 0.35f
        }
        if (lower.contains("merci") || lower.contains("thanks") || lower.contains("thank") || lower.contains("choukran") || lower.contains("chokran") || lower.contains("شكرا")) {
            boostGratitude += 0.35f
        }
        if (lower.contains("appelle") || lower.contains("rappelle") || lower.contains("call me") || lower.contains("3ayet") || lower.contains("tasel") || lower.contains("اتصل")) {
            boostUrgent += 0.35f
        }
        if (isQuestion && boostLocation == 0f && boostTime == 0f && boostHowAreYou == 0f) {
            boostYesNo += 0.20f
        }

        val scores = listOf(
            MessageIntent.GREETING to (simGreeting + boostGreeting),
            MessageIntent.HOW_ARE_YOU to (simHowAreYou + boostHowAreYou),
            MessageIntent.LOCATION_QUERY to (simLocation + boostLocation),
            MessageIntent.TIME_QUERY to (simTime + boostTime),
            MessageIntent.YES_NO_QUERY to (simYesNo + boostYesNo),
            MessageIntent.GRATITUDE to (simGratitude + boostGratitude),
            MessageIntent.CONFIRMATION to simConfirmation,
            MessageIntent.APOLOGY to simApology,
            MessageIntent.URGENT_CALL to (simUrgentCall + boostUrgent)
        )

        val best = scores.maxByOrNull { it.second } ?: (MessageIntent.GENERIC to 0.4f)
        val bestIntent = if (best.second > 0.30f) best.first else MessageIntent.GENERIC
        val confidence = best.second.coerceIn(0.40f, 0.98f)

        // 5. Gather personalized replies learned on-device
        val storage = OrbisAiStorage(context)
        val userPrefs = storage.loadReplyPreferences().filter { it.intentName == bestIntent.name }

        val suggestions = mutableListOf<ReplySuggestion>()
        val seenTexts = mutableSetOf<String>()

        // Add top user-personalized suggestions first
        for (pref in userPrefs.take(2)) {
            val trimmed = pref.replyText.trim()
            if (trimmed.isNotBlank() && seenTexts.add(trimmed.lowercase(Locale.ROOT))) {
                suggestions.add(
                    ReplySuggestion(
                        text = trimmed,
                        intent = bestIntent,
                        confidenceScore = (confidence + 0.05f).coerceAtMost(0.99f),
                        isPersonalized = true
                    )
                )
            }
        }

        // Fill remaining slots with pre-trained bank in the detected language
        val langMap = OrbisReplyWeights.SMART_REPLIES[bestIntent] ?: OrbisReplyWeights.SMART_REPLIES[MessageIntent.GENERIC]!!
        val defaultReplies = langMap[detectedLang] ?: langMap["fr"] ?: emptyList()

        for (reply in defaultReplies) {
            if (suggestions.size >= maxSuggestions) break
            val trimmed = reply.trim()
            if (seenTexts.add(trimmed.lowercase(Locale.ROOT))) {
                suggestions.add(
                    ReplySuggestion(
                        text = trimmed,
                        intent = bestIntent,
                        confidenceScore = confidence,
                        isPersonalized = false
                    )
                )
            }
        }

        return SuggestedRepliesResult(
            detectedIntent = bestIntent,
            detectedLanguage = detectedLang,
            confidence = confidence,
            suggestions = suggestions
        )
    }

    /**
     * Auto-apprentissage continu : enregistre le choix ou la réponse de l'utilisateur
     * pour affiner et personnaliser les prochaines suggestions pour cette intention.
     */
    fun learnFromUserSentMessage(
        context: Context? = null,
        incomingMessage: String,
        replySent: String
    ) {
        if (incomingMessage.isBlank() || replySent.isBlank()) return
        val result = generateReplies(context, incomingMessage, maxSuggestions = 1)
        val storage = OrbisAiStorage(context)
        storage.recordReplyPreference(result.detectedIntent.name, replySent)
    }
}
