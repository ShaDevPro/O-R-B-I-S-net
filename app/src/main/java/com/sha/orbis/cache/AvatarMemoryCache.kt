package com.sha.orbis.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Cache mémoire L1 haute performance dédié aux avatars utilisateurs.
 * Élimine le décodage répétitif des fichiers et chaînes Base64 sur le thread UI.
 * Thread-safe avec dimensionnement basé sur la consommation mémoire réelle en octets.
 */
object AvatarMemoryCache {

    // Capacité maximale allouée : 12 Mo de Bitmaps en mémoire vive
    private const val MAX_CACHE_SIZE_BYTES = 12 * 1024 * 1024

    private val lruCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    /**
     * Normalise une clé de cache (chemin ou Base64) pour un accès rapide.
     */
    private fun normalizeKey(rawKey: String): String {
        val trimmed = rawKey.trim()
        return if (trimmed.length > 120) {
            // Pour les longues chaînes Base64, utiliser un hash déterministe
            "b64_" + trimmed.take(32).hashCode() + "_" + trimmed.length
        } else {
            trimmed
        }
    }

    @Synchronized
    fun get(key: String?): Bitmap? {
        if (key.isNullOrBlank()) return null
        return lruCache.get(normalizeKey(key))
    }

    @Synchronized
    fun put(key: String?, bitmap: Bitmap?) {
        if (key.isNullOrBlank() || bitmap == null) return
        lruCache.put(normalizeKey(key), bitmap)
    }

    @Synchronized
    fun remove(key: String?) {
        if (key.isNullOrBlank()) return
        lruCache.remove(normalizeKey(key))
    }

    @Synchronized
    fun clear() {
        lruCache.evictAll()
    }

    @Synchronized
    fun trimToSize(maxSize: Int) {
        lruCache.trimToSize(maxSize)
    }

    val currentSizeBytes: Int
        @Synchronized get() = lruCache.size()

    val hitCount: Int
        @Synchronized get() = lruCache.hitCount()

    val missCount: Int
        @Synchronized get() = lruCache.missCount()
}
