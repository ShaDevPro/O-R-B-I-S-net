package com.sha.orbis.ai.core

import java.util.Locale

/**
 * Proprietary Multilingual Subword Tokenizer for ORBIS.
 * Supports French, English, Standard Arabic, and Franco-Arabe / Darija (Arabizi).
 */
object OrbisTokenizer {

    const val EMBEDDING_DIM = 64
    const val MAX_SEQ_LEN = 128

    // Special Token IDs
    const val TOKEN_PAD = 0
    const val TOKEN_UNK = 1
    const val TOKEN_CLS = 2
    const val TOKEN_SEP = 3
    const val TOKEN_URL = 4
    const val TOKEN_PHONE = 5
    const val TOKEN_OTP = 6
    const val TOKEN_MONEY = 7

    private val URL_REGEX = Regex("(?i)https?://\\S+|www\\.\\S+|[a-z0-9.-]+\\.(com|fr|net|org|dz|co|io|me|xyz|top|online|vip|app)\\b\\S*")
    private val PHONE_REGEX = Regex("(?<!\\w)(?:\\+?\\d{1,3}[- ]?)?\\d{8,10}(?!\\w)")
    private val OTP_REGEX = Regex("^\\d{4,8}$")
    private val MONEY_REGEX = Regex("(?i)(?:\\b\\d+[.,]?\\d*\\s*[€\$£¥]|\\b\\d+[.,]?\\d*\\s*(?:da|dzd|eur|usd|dinars?|euros?)\\b)")

    /**
     * Normalizes and cleans input text while preserving Arabic and Arabizi diacritics / numerals.
     */
    fun normalize(text: String): String {
        return text
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun maskEntities(rawText: String): String {
        var processed = normalize(rawText)
        processed = URL_REGEX.replace(processed, " [URL] ")
        processed = MONEY_REGEX.replace(processed, " [MONEY] ")
        processed = PHONE_REGEX.replace(processed, " [PHONE] ")
        return processed
    }

    /**
     * Extracts token representations and replaces sensitive / structural patterns (URL, phone, OTP, money).
     */
    fun tokenize(rawText: String): List<String> {
        var processed = normalize(rawText)

        // Replace structural entities with special tokens
        processed = URL_REGEX.replace(processed, " [URL] ")
        processed = MONEY_REGEX.replace(processed, " [MONEY] ")
        processed = PHONE_REGEX.replace(processed, " [PHONE] ")

        val tokens = mutableListOf<String>()
        val words = processed.split(' ')

        for (word in words) {
            val clean = word.trim()
            if (clean.isBlank()) continue

            when (clean) {
                "[URL]" -> tokens.add("[URL]")
                "[MONEY]" -> tokens.add("[MONEY]")
                "[PHONE]" -> tokens.add("[PHONE]")
                else -> {
                    if (clean.matches(OTP_REGEX)) {
                        tokens.add("[OTP]")
                    } else {
                        // Subword split for Arabic / French / English punctuation
                        val subwords = clean.lowercase(Locale.ROOT)
                            .replace(Regex("([.,!?;:()\"\\[\\]{}])"), " $1 ")
                            .split(' ')
                            .filter { it.isNotBlank() }
                        tokens.addAll(subwords)
                    }
                }
            }
        }
        return tokens.take(MAX_SEQ_LEN)
    }

    private val STOPWORDS = setOf(
        "le", "la", "les", "un", "une", "des", "du", "de", "d", "l", "et", "en", "a", "à", "dans", "par", "pour", "sur",
        "ce", "cet", "cette", "ces", "mon", "ton", "son", "notre", "votre", "leur", "mes", "tes", "ses",
        "qui", "que", "quoi", "dont", "ou", "où", "est", "sont", "ont", "avec", "sans", "sous", "au", "aux",
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was", "were",
        "it", "this", "that", "my", "your", "his", "her", "its", "our", "their",
        "في", "من", "على", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "هو", "هي", "هم", "هن", "نحن", "أنا", "أنت",
        "كان", "يكون", "أن", "إن", "ما", "لا", "لم", "لن", "قد", "كل", "بعض", "غير", "بين"
    )

    /**
     * Computes a dense semantic embedding vector (64 dimensions) from a text.
     * Uses feature hashing with sign bits and positional attention decay.
     */
    fun encodeToEmbedding(text: String): FloatArray {
        val tokens = tokenize(text)
        val embedding = FloatArray(EMBEDDING_DIM)

        if (tokens.isEmpty()) return embedding

        for ((idx, token) in tokens.withIndex()) {
            val positionWeight = 1.0f / (1.0f + 0.005f * idx)
            val isStop = STOPWORDS.contains(token.lowercase(Locale.ROOT))
            val weight = if (isStop) positionWeight * 0.1f else positionWeight

            // Token feature hash
            val h1 = (token.hashCode() and 0x7FFFFFFF) % EMBEDDING_DIM
            val h2 = ((token.hashCode() * 31 + 17) and 0x7FFFFFFF) % EMBEDDING_DIM
            val sign1 = if ((token.hashCode() and 1) == 0) 1.0f else -1.0f
            val sign2 = if (((token.hashCode() ushr 1) and 1) == 0) 1.0f else -1.0f

            embedding[h1] += sign1 * weight
            embedding[h2] += sign2 * weight * 0.7f

            // Character n-gram hashing for typos and slang / Arabizi resilience
            if (token.length >= 3 && !token.startsWith("[")) {
                for (i in 0..token.length - 3) {
                    val tri = token.substring(i, i + 3)
                    val triHash = (tri.hashCode() and 0x7FFFFFFF) % EMBEDDING_DIM
                    val triSign = if ((tri.hashCode() and 1) == 0) 0.3f else -0.3f
                    embedding[triHash] += triSign * weight
                }
            }
        }

        return OrbisVectorMath.normalizeL2(embedding)
    }

    /**
     * Checks if text contains Arabic script characters.
     */
    fun hasArabicScript(text: String): Boolean {
        for (ch in text) {
            val block = Character.UnicodeBlock.of(ch)
            if (block == Character.UnicodeBlock.ARABIC ||
                block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
                block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B ||
                block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Detects dominant language: "ar" (Arabic), "dz" (Darija algérienne / Arabizi), "fr" (French), or "en" (English).
     */
    fun detectLanguage(text: String): String {
        if (hasArabicScript(text)) return "ar"

        val lower = text.lowercase(Locale.ROOT)

        val darijaKeywords = listOf(
            "salam", "khoya", "khouya", "khti", "labas", "wach", "rak", "raki",
            "kifech", "kifah", "dork", "dorka", "win", "winek", "khedma", "n3awed",
            "mlih", "sahit", "bessah", "hak", "baraka", "nchalah", "yakhi",
            "3lah", "3lach", "wlh", "rani", "rahi", "gaa", "ga3", "hna",
            "smahli", "choukran", "bezzaf", "yatik", "sahha", "khatr", "kayen",
            "wahed", "chhal", "3andek", "3andi", "matkhafch", "nroh", "nji"
        )
        val hasArabiziNumbers = lower.contains(Regex("\\b\\w*[379]\\w*\\b"))

        var darijaScore = if (hasArabiziNumbers) 3 else 0
        val frKeywords = listOf("le", "la", "les", "un", "une", "des", "vous", "nous", "merci", "bonjour", "salut", "livraison", "compte", "urgent", "comment", "vas", "est", "ce", "que", "pour", "dans", "aujourd'hui")
        val enKeywords = listOf("the", "a", "an", "is", "are", "you", "your", "thanks", "hello", "hi", "delivery", "account", "urgent", "what", "time", "where", "how", "doing")

        var frScore = 0
        var enScore = 0
        val words = lower.split(Regex("\\W+"))
        for (w in words) {
            if (w in darijaKeywords) darijaScore += 2
            if (w in frKeywords) frScore++
            if (w in enKeywords) enScore++
        }

        return when {
            darijaScore >= 2 && darijaScore > frScore && darijaScore > enScore -> "dz"
            enScore > frScore && enScore > darijaScore -> "en"
            frScore > enScore && frScore > darijaScore -> "fr"
            darijaScore >= 2 -> "dz"
            else -> "fr"
        }
    }
}
