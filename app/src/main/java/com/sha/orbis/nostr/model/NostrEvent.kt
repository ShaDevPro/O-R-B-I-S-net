package com.sha.orbis.nostr.model

import com.sha.orbis.nostr.crypto.Bech32
import com.sha.orbis.nostr.crypto.Secp256k1
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Modèle d'Événement Nostr standard (conforme NIP-01).
 * Représente tout message, publication, réaction ou métadonnée sur le réseau OrbisNet.
 */
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
) {
    companion object {
        const val KIND_METADATA = 0
        const val KIND_TEXT_NOTE = 1
        const val KIND_CONTACT_LIST = 3
        const val KIND_ENCRYPTED_DIRECT_MESSAGE = 4
        const val KIND_DELETION = 5
        const val KIND_REACTION = 7
        const val KIND_EPHEMERAL_CALL_SIGNAL = 20001
        const val KIND_EPHEMERAL_TYPING = 20002
        const val KIND_EPHEMERAL_RECEIPT = 20003

        /**
         * Crée et signe un nouvel événement Nostr.
         */
        fun createAndSign(
            pubkeyHex: String,
            privkey32: ByteArray,
            kind: Int,
            tags: List<List<String>> = emptyList(),
            content: String = "",
            createdAtSeconds: Long = System.currentTimeMillis() / 1000L
        ): NostrEvent {
            val eventId = computeEventId(pubkeyHex, createdAtSeconds, kind, tags, content)
            val eventIdBytes = Bech32.hexToBytes(eventId)
            val sigHex = Secp256k1.sign(eventIdBytes, privkey32)

            return NostrEvent(
                id = eventId,
                pubkey = pubkeyHex,
                createdAt = createdAtSeconds,
                kind = kind,
                tags = tags,
                content = content,
                sig = sigHex
            )
        }

        /**
         * Échappe une chaîne selon les règles strictes RFC 8259 et Nostr NIP-01 :
         * - Ne jamais échapper le caractère '/' (reste '/')
         * - Échapper '"' en '\"'
         * - Échapper '\' en '\\'
         * - Échapper les caractères de contrôle 0x00..0x1F (\b, \f, \n, \r, \t, ou \u00XX)
         * - Conserver tous les caractères Unicode UTF-8 sans échappement \uXXXX
         */
        fun escapeJson(s: String): String {
            val sb = StringBuilder(s.length + 16)
            sb.append('"')
            for (i in 0 until s.length) {
                val c = s[i]
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (c.code < 0x20) {
                            val hex = Integer.toHexString(c.code)
                            sb.append("\\u")
                            for (k in 0 until 4 - hex.length) sb.append('0')
                            sb.append(hex)
                        } else {
                            sb.append(c)
                        }
                    }
                }
            }
            sb.append('"')
            return sb.toString()
        }

        /**
         * Calcule la sérialisation canonique NIP-01 de l'événement :
         * Format : [0,"<pubkey>",<created_at>,<kind>,<tags>,"<content>"]
         */
        fun serializeForId(
            pubkeyHex: String,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String
        ): String {
            val sb = StringBuilder()
            sb.append("[0,")
            sb.append(escapeJson(pubkeyHex.lowercase()))
            sb.append(',')
            sb.append(createdAt)
            sb.append(',')
            sb.append(kind)
            sb.append(',')
            sb.append('[')
            for (i in tags.indices) {
                if (i > 0) sb.append(',')
                val tag = tags[i]
                sb.append('[')
                for (j in tag.indices) {
                    if (j > 0) sb.append(',')
                    sb.append(escapeJson(tag[j]))
                }
                sb.append(']')
            }
            sb.append("],")
            sb.append(escapeJson(content))
            sb.append(']')
            return sb.toString()
        }

        fun computeEventId(
            pubkeyHex: String,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String
        ): String {
            val serialized = serializeForId(pubkeyHex, createdAt, kind, tags, content)
            val hashBytes = Secp256k1.sha256(serialized.toByteArray(StandardCharsets.UTF_8))
            return Bech32.bytesToHex(hashBytes)
        }

        fun fromJson(jsonStr: String): NostrEvent {
            val obj = JSONObject(jsonStr)
            val id = obj.getString("id")
            val pubkey = obj.getString("pubkey")
            val createdAt = obj.getLong("created_at")
            val kind = obj.getInt("kind")
            val content = obj.optString("content", "")
            val sig = obj.optString("sig", "")

            val tagsList = mutableListOf<List<String>>()
            val tagsArr = obj.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    val innerArr = tagsArr.getJSONArray(i)
                    val innerList = mutableListOf<String>()
                    for (j in 0 until innerArr.length()) {
                        innerList.add(innerArr.getString(j))
                    }
                    tagsList.add(innerList)
                }
            }

            return NostrEvent(
                id = id,
                pubkey = pubkey,
                createdAt = createdAt,
                kind = kind,
                tags = tagsList,
                content = content,
                sig = sig
            )
        }
    }

    /**
     * Vérifie la validité mathématique de la signature BIP-340 de cet événement.
     */
    fun verifySignature(): Boolean {
        if (id.length != 64 || pubkey.length != 64 || sig.length != 128) return false
        val expectedId = computeEventId(pubkey, createdAt, kind, tags, content)
        if (!expectedId.equals(id, ignoreCase = true)) return false

        val idBytes = Bech32.hexToBytes(id)
        val pubkeyBytes = Bech32.hexToBytes(pubkey)
        val sigBytes = Bech32.hexToBytes(sig)

        return Secp256k1.verify(idBytes, pubkeyBytes, sigBytes)
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("pubkey", pubkey)
        obj.put("created_at", createdAt)
        obj.put("kind", kind)

        val tagsArray = JSONArray()
        for (tag in tags) {
            val tagInner = JSONArray()
            for (item in tag) {
                tagInner.put(item)
            }
            tagsArray.put(tagInner)
        }
        obj.put("tags", tagsArray)
        obj.put("content", content)
        obj.put("sig", sig)
        return obj
    }

    fun toJsonString(): String = toJson().toString()

    /**
     * Sérialisation JSON propre et canonique pour le protocole WebSocket NIP-01.
     */
    fun toCanonicalJson(): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"id\":").append(escapeJson(id)).append(',')
        sb.append("\"pubkey\":").append(escapeJson(pubkey.lowercase())).append(',')
        sb.append("\"created_at\":").append(createdAt).append(',')
        sb.append("\"kind\":").append(kind).append(',')
        sb.append("\"tags\":[")
        for (i in tags.indices) {
            if (i > 0) sb.append(',')
            val tag = tags[i]
            sb.append('[')
            for (j in tag.indices) {
                if (j > 0) sb.append(',')
                sb.append(escapeJson(tag[j]))
            }
            sb.append(']')
        }
        sb.append("],")
        sb.append("\"content\":").append(escapeJson(content)).append(',')
        sb.append("\"sig\":").append(escapeJson(sig))
        sb.append('}')
        return sb.toString()
    }
}
