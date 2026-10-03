package com.sha.orbis.model

import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.abs

data class MessageRevocationTarget(
    val messageId: String? = null,
    val networkEventId: String? = null,
    val mediaId: String? = null,
    val contentHash: String? = null,
    val timestamp: Long? = null
)

object MessageRevocation {
    private const val PREFIX_JSON = "[REVOKE_JSON:"
    private const val PREFIX_LEGACY = "[REVOKE:"
    private const val SUFFIX = "]"
    private const val CONTENT_MATCH_WINDOW_MS = 10 * 60 * 1000L

    fun fromMessage(message: Message): MessageRevocationTarget =
        MessageRevocationTarget(
            messageId = message.id.takeIf { it.isNotBlank() },
            networkEventId = message.networkEventId?.takeIf { it.isNotBlank() },
            mediaId = extractMediaId(message.text),
            contentHash = contentHash(message.text),
            timestamp = message.timestamp.takeIf { it > 0L }
        )

    fun encode(message: Message): String {
        val target = fromMessage(message)
        val json = JSONObject().apply {
            target.messageId?.let { put("messageId", it) }
            target.networkEventId?.let { put("networkEventId", it) }
            target.mediaId?.let { put("mediaId", it) }
            target.contentHash?.let { put("contentHash", it) }
            target.timestamp?.let { put("timestamp", it) }
        }
        return "$PREFIX_JSON$json$SUFFIX"
    }

    fun parse(packetText: String): MessageRevocationTarget? {
        val text = packetText.trim()
        if (text.startsWith(PREFIX_JSON) && text.endsWith(SUFFIX)) {
            return try {
                val json = JSONObject(text.removePrefix(PREFIX_JSON).removeSuffix(SUFFIX))
                MessageRevocationTarget(
                    messageId = json.optString("messageId").ifBlank { null },
                    networkEventId = json.optString("networkEventId").ifBlank { null },
                    mediaId = json.optString("mediaId").ifBlank { null },
                    contentHash = json.optString("contentHash").ifBlank { null },
                    timestamp = json.optLong("timestamp", 0L).takeIf { it > 0L }
                )
            } catch (_: Exception) {
                null
            }
        }

        if (text.startsWith(PREFIX_LEGACY) && text.endsWith(SUFFIX)) {
            val legacyId = text.removePrefix(PREFIX_LEGACY).removeSuffix(SUFFIX).trim()
            if (legacyId.isBlank()) return null
            return MessageRevocationTarget(messageId = legacyId)
        }

        return null
    }

    fun findMatchIndex(messages: List<Message>, target: MessageRevocationTarget): Int {
        fun same(a: String?, b: String?): Boolean =
            !a.isNullOrBlank() && !b.isNullOrBlank() && a.equals(b, ignoreCase = true)

        val exactIndex = messages.indexOfFirst { message ->
            same(message.id, target.messageId) ||
                same(message.networkEventId, target.networkEventId) ||
                same(message.networkEventId, target.messageId) ||
                same(message.id, target.networkEventId)
        }
        if (exactIndex >= 0) return exactIndex

        val mediaIndex = messages.indexOfFirst { message ->
            same(extractMediaId(message.text), target.mediaId) ||
                same(extractMediaId(message.text), target.messageId)
        }
        if (mediaIndex >= 0) return mediaIndex

        val targetHash = target.contentHash ?: return -1
        val targetTime = target.timestamp ?: return -1
        return messages
            .mapIndexedNotNull { index, message ->
                val isSameContent = contentHash(message.text).equals(targetHash, ignoreCase = true)
                val delta = abs(message.timestamp - targetTime)
                if (isSameContent && delta <= CONTENT_MATCH_WINDOW_MS) index to delta else null
            }
            .minByOrNull { it.second }
            ?.first ?: -1
    }

    fun extractMediaId(text: String): String? {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("[VIDEO:") && trimmed.endsWith("]") -> {
                val raw = trimmed.removePrefix("[VIDEO:").removeSuffix("]")
                raw.substringBefore("|").substringBefore(":").takeIf { it.isNotBlank() }
            }
            trimmed.startsWith("[IMAGE:") && trimmed.endsWith("]") -> {
                trimmed.removePrefix("[IMAGE:").removeSuffix("]").substringBefore(":").takeIf { it.isNotBlank() }
            }
            trimmed.startsWith("[DOC:") && trimmed.endsWith("]") -> {
                trimmed.removePrefix("[DOC:").removeSuffix("]").substringBefore(":").takeIf { it.isNotBlank() }
            }
            trimmed.startsWith("[ALBUM:") && trimmed.endsWith("]") -> {
                try {
                    JSONObject(trimmed.removePrefix("[ALBUM:").removeSuffix("]"))
                        .optString("id")
                        .ifBlank { null }
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    private fun contentHash(text: String): String? {
        val normalized = text.trim()
        if (normalized.isBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
