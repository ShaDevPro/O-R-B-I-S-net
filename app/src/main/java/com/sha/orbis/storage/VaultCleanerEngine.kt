package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.cache.MediaMemoryCache
import com.sha.orbis.cache.OrbisCacheCoordinator
import org.json.JSONArray
import java.io.File

/**
 * Sovereign Storage Optimizer & Auto-Cleaner Engine.
 * Scans, analyzes, and purges expired stories, orphaned SMS chunks, and temporary media.
 * Modular, accurate, and completely safe for encrypted user identity and keychains.
 */
object VaultCleanerEngine {

    data class StorageAnalysis(
        val totalVaultBytes: Long,
        val reclaimableBytes: Long,
        val mediaImagesBytes: Long,
        val voiceNotesBytes: Long,
        val databaseBytes: Long,
        val temporaryCacheBytes: Long,
        val expiredStoriesCount: Int,
        val orphanedChunksCount: Int,
        val voiceNotesCount: Int
    )

    data class PurgeResult(
        val bytesReclaimed: Long,
        val itemsDeletedCount: Int,
        val expiredStoriesPurged: Int,
        val orphanedChunksPurged: Int
    )

    /**
     * Analyzes disk usage across Orbis private directories with granular category breakdown.
     */
    fun analyzeStorage(context: Context): StorageAnalysis {
        val filesDir = context.filesDir
        val cacheDir = context.cacheDir

        // 1. Media Images (Feed, Stories, Chat attachments)
        val imagesDir = File(filesDir, "media/images")
        val mediaImagesBytes = getFolderSize(imagesDir)

        // 2. Voice Notes
        val voiceDir = File(filesDir, "voice_notes")
        val voiceNotesCount = voiceDir.listFiles()?.size ?: 0
        val voiceNotesBytes = getFolderSize(voiceDir)

        // 3. Database files (*.json)
        var databaseBytes = 0L
        filesDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.forEach {
            databaseBytes += it.length()
        }

        // 4. Temporary Caches (cacheDir, temp audio, camera temp, SMS chunks)
        val cacheBytes = getFolderSize(cacheDir)
        val chunksDir = File(filesDir, "sms_chunks")
        val chunksBytes = getFolderSize(chunksDir)
        val tempDir = File(filesDir, "temp")
        val tempBytes = getFolderSize(tempDir)
        val audioCacheDir = File(filesDir, "audio_cache")
        val audioCacheBytes = getFolderSize(audioCacheDir)
        val temporaryCacheBytes = cacheBytes + chunksBytes + tempBytes + audioCacheBytes

        // 5. Expired stories count
        var expiredStoriesCount = 0
        var expiredStoriesBytes = 0L
        val storiesFile = File(filesDir, "orbis_social_stories.json")
        if (storiesFile.exists()) {
            try {
                val array = JSONArray(storiesFile.readText())
                val now = System.currentTimeMillis()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val expiresAt = obj.optLong("expiresAt", 0L)
                    val createdAt = obj.optLong("createdAt", 0L)
                    if ((expiresAt in 1..now) || (now - createdAt > 24 * 3600 * 1000L)) {
                        expiredStoriesCount++
                        expiredStoriesBytes += obj.toString().toByteArray().size
                    }
                }
            } catch (_: Exception) {}
        }

        // 6. Orphaned chunks count (> 2 hours old)
        var orphanedChunksCount = 0
        if (chunksDir.exists() && chunksDir.isDirectory) {
            val chunks = chunksDir.listFiles() ?: emptyArray()
            val now = System.currentTimeMillis()
            for (f in chunks) {
                if (now - f.lastModified() > 2 * 3600 * 1000L) {
                    orphanedChunksCount++
                }
            }
        }

        val totalVaultBytes = mediaImagesBytes + voiceNotesBytes + databaseBytes + temporaryCacheBytes
        val reclaimableBytes = mediaImagesBytes + temporaryCacheBytes + expiredStoriesBytes

        return StorageAnalysis(
            totalVaultBytes = totalVaultBytes,
            reclaimableBytes = reclaimableBytes,
            mediaImagesBytes = mediaImagesBytes,
            voiceNotesBytes = voiceNotesBytes,
            databaseBytes = databaseBytes,
            temporaryCacheBytes = temporaryCacheBytes,
            expiredStoriesCount = expiredStoriesCount,
            orphanedChunksCount = orphanedChunksCount,
            voiceNotesCount = voiceNotesCount
        )
    }

    /**
     * Purges cached media photos from disk without touching text messages, contacts, or keys.
     */
    fun purgeMediaPhotos(context: Context): Long {
        var freedBytes = 0L
        try {
            MediaMemoryCache.clear()
            val imagesDir = File(context.filesDir, "media/images")
            if (imagesDir.exists() && imagesDir.isDirectory) {
                imagesDir.listFiles()?.forEach { f ->
                    val size = f.length()
                    if (f.delete()) {
                        freedBytes += size
                    }
                }
            }
        } catch (_: Exception) {}
        return freedBytes
    }

    /**
     * Purges all temporary files (camera captures, SMS chunks, cacheDir).
     */
    fun purgeTemporaryCaches(context: Context): Long {
        var freedBytes = 0L
        try {
            // Android cacheDir
            val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
            for (f in cacheFiles) {
                val size = getFolderSize(f)
                if (f.deleteRecursively()) {
                    freedBytes += size
                }
            }

            // External cache if available
            context.externalCacheDir?.listFiles()?.forEach { f ->
                val size = getFolderSize(f)
                if (f.deleteRecursively()) {
                    freedBytes += size
                }
            }

            // Orphaned SMS chunks
            val chunksDir = File(context.filesDir, "sms_chunks")
            if (chunksDir.exists()) {
                chunksDir.listFiles()?.forEach { f ->
                    val size = f.length()
                    if (f.delete()) freedBytes += size
                }
            }

            // Temp files
            val tempDir = File(context.filesDir, "temp")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { f ->
                    val size = f.length()
                    if (f.delete()) freedBytes += size
                }
            }

            // Audio cache
            val audioCache = File(context.filesDir, "audio_cache")
            if (audioCache.exists()) {
                audioCache.listFiles()?.forEach { f ->
                    val size = f.length()
                    if (f.delete()) freedBytes += size
                }
            }

            // Camera temporary folder
            freedBytes += OrbisCacheCoordinator.pruneCameraTemp(context)
        } catch (_: Exception) {}
        return freedBytes
    }

    /**
     * Safely purges expired stories, dead SMS chunks, and cache files (Full Auto Purge).
     */
    fun purgeAll(context: Context): PurgeResult {
        var bytesReclaimed = 0L
        var itemsDeletedCount = 0
        var expiredStoriesPurged = 0
        var orphanedChunksPurged = 0

        val filesDir = context.filesDir

        // 1. Purge Expired Stories & their disk photos
        val storiesFile = File(filesDir, "orbis_social_stories.json")
        if (storiesFile.exists()) {
            try {
                val array = JSONArray(storiesFile.readText())
                val cleanArray = JSONArray()
                val now = System.currentTimeMillis()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val expiresAt = obj.optLong("expiresAt", 0L)
                    val createdAt = obj.optLong("createdAt", 0L)
                    val durationHours = obj.optLong("durationHours", 24L)
                    val isExpired = (expiresAt in 1..now) || (now - createdAt > durationHours * 3600 * 1000L)
                    if (!isExpired) {
                        cleanArray.put(obj)
                    } else {
                        expiredStoriesPurged++
                        bytesReclaimed += obj.toString().toByteArray().size
                        // Delete associated image file
                        val mediaPath = obj.optString("mediaPath")
                        if (mediaPath.isNotBlank()) {
                            try {
                                val imgFile = File(mediaPath)
                                val size = imgFile.length()
                                if (imgFile.delete()) bytesReclaimed += size
                            } catch (_: Exception) {}
                        }
                    }
                }
                if (expiredStoriesPurged > 0) {
                    storiesFile.writeText(cleanArray.toString(2))
                    itemsDeletedCount += expiredStoriesPurged
                }
            } catch (_: Exception) {}
        }

        // 2. Purge orphaned SMS chunks
        val chunksDir = File(filesDir, "sms_chunks")
        if (chunksDir.exists() && chunksDir.isDirectory) {
            val chunks = chunksDir.listFiles() ?: emptyArray()
            val now = System.currentTimeMillis()
            for (f in chunks) {
                if (now - f.lastModified() > 2 * 3600 * 1000L) {
                    val size = f.length()
                    if (f.delete()) {
                        orphanedChunksPurged++
                        itemsDeletedCount++
                        bytesReclaimed += size
                    }
                }
            }
        }

        // 3. Purge Android app cache & temporary files
        val tempFreed = purgeTemporaryCaches(context)
        bytesReclaimed += tempFreed

        // 4. Purge media photos & clear memory caches
        val mediaFreed = purgeMediaPhotos(context)
        bytesReclaimed += mediaFreed

        OrbisCacheCoordinator.clearAllMemoryCaches()
        SocialRepository.invalidateCache()

        return PurgeResult(
            bytesReclaimed = bytesReclaimed,
            itemsDeletedCount = itemsDeletedCount,
            expiredStoriesPurged = expiredStoriesPurged,
            orphanedChunksPurged = orphanedChunksPurged
        )
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f Go", bytes.toFloat() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f Mo", bytes.toFloat() / (1024 * 1024))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f Ko", bytes.toFloat() / 1024)
            else -> "$bytes o"
        }
    }

    fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            size += getFolderSize(child)
        }
        return size
    }
}
