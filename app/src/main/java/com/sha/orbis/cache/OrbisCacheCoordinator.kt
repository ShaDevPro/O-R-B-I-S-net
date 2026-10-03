package com.sha.orbis.cache

import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import com.sha.orbis.storage.SocialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Coordinateur central modulaire pour la gestion des caches mémoire (L1) et disque (L2).
 * Surveille la pression mémoire Android (onTrimMemory) et orchestre la purge des données obsolètes.
 */
object OrbisCacheCoordinator {

    private const val TAG = "OrbisCacheCoordinator"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Réduit la consommation RAM en fonction des avertissements système d'Android.
     */
    fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.d(TAG, "Pression mémoire critique ($level) : vidage complet des caches L1")
                AvatarMemoryCache.clear()
                MediaMemoryCache.clear()
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.d(TAG, "Pression mémoire modérée ($level) : réduction de 50% des caches L1")
                AvatarMemoryCache.trimToSize(AvatarMemoryCache.currentSizeBytes / 2)
                MediaMemoryCache.trimToSize(MediaMemoryCache.currentSizeBytes / 2)
            }
        }
    }

    /**
     * Nettoie tous les caches mémoire instantanément.
     */
    fun clearAllMemoryCaches() {
        AvatarMemoryCache.clear()
        MediaMemoryCache.clear()
    }

    /**
     * Supprime physiquement du disque dur les photos associées aux stories ayant dépassé 24h.
     */
    fun pruneExpiredStoriesMedia(context: Context): Int {
        var deletedCount = 0
        try {
            val socialRepo = SocialRepository(context)
            val allStories = socialRepo.loadStories()
            val now = System.currentTimeMillis()

            val activeStoryMediaPaths = allStories
                .filter { it.expiresAt > now }
                .mapNotNull { it.mediaPath }
                .toSet()

            val imagesDir = File(context.filesDir, "media/images")
            if (imagesDir.exists() && imagesDir.isDirectory) {
                imagesDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("story_") && file.absolutePath !in activeStoryMediaPaths) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors du nettoyage des médias de stories: ${e.message}")
        }
        return deletedCount
    }

    /**
     * Supprime les fichiers temporaires de l'appareil photo dans cacheDir/camera.
     */
    fun pruneCameraTemp(context: Context): Long {
        var freedBytes = 0L
        try {
            val cameraDir = File(context.cacheDir, "camera")
            if (cameraDir.exists() && cameraDir.isDirectory) {
                cameraDir.listFiles()?.forEach { file ->
                    val size = file.length()
                    if (file.delete()) {
                        freedBytes += size
                    }
                }
            }
        } catch (_: Exception) {}
        return freedBytes
    }

    /**
     * Déclenche une maintenance en tâche de fond (asynchrone).
     */
    fun triggerBackgroundMaintenance(context: Context) {
        scope.launch {
            val storiesPruned = pruneExpiredStoriesMedia(context)
            val cameraFreed = pruneCameraTemp(context)
            if (storiesPruned > 0 || cameraFreed > 0) {
                Log.d(TAG, "Maintenance terminée : $storiesPruned stories média purgées, ${cameraFreed / 1024} Ko caméra libérés")
            }
        }
    }
}
