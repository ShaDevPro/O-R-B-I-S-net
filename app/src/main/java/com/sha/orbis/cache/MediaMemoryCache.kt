package com.sha.orbis.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Cache mémoire L1 haute performance dédié aux photos du fil d'actualité, stories et messages.
 * Capacité allouée : 25 Mo avec déduplication de clés et gestion stricte du cycle de vie RAM.
 */
object MediaMemoryCache {

    // Standard Android pattern: 1/8 of the app heap to avoid OOM
    private val MAX_CACHE_SIZE_BYTES: Int = (Runtime.getRuntime().maxMemory() / 8).coerceIn(
        16L * 1024 * 1024,  // minimum 16 MB
        64L * 1024 * 1024   // maximum 64 MB
    ).toInt()

    private val lruCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    private fun normalizeKey(rawKey: String): String {
        val trimmed = rawKey.trim()
        return if (trimmed.length > 120) {
            "media_b64_" + trimmed.take(32).hashCode() + "_" + trimmed.length
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

    /**
     * Enregistre le même Bitmap sous deux clés différentes (ex: chemin fichier et Base64)
     * sans dupliquer l'empreinte mémoire RAM.
     */
    @Synchronized
    fun putDualKey(path: String?, base64: String?, bitmap: Bitmap?) {
        if (bitmap == null) return
        if (!path.isNullOrBlank()) {
            lruCache.put(normalizeKey(path), bitmap)
        }
        if (!base64.isNullOrBlank()) {
            lruCache.put(normalizeKey(base64), bitmap)
        }
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
}
