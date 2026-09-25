package com.sha.orbis.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.sha.orbis.sms.BinarySmsCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

/**
 * Gestionnaire modulaire souverain pour le traitement, le stockage local,
 * la persistance et la suppression définitive des vidéos partagées dans Orbis
 * (Discussion, Feed, Story).
 */
object VideoMediaHelper {

    private const val TAG = "VideoMediaHelper"
    const val PAYLOAD_VIDEO_PREFIX = "[VIDEO:"
    private const val TOMBSTONES_FILE_NAME = "orbis_deleted_video_ids.json"
    const val MAX_VIDEO_SIZE_BYTES = 50 * 1024 * 1024L // 50 Mo max pour réseau P2P / Nostr

    data class VideoProcessResult(
        val id: String,
        val localFile: File,
        val thumbnailFile: File?,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val sizeBytes: Long
    )

    data class VideoPayload(
        val id: String,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val caption: String = "",
        val base64Data: String? = null,
        val url: String? = null
    )

    /**
     * Génère la miniature JPEG d'un fichier vidéo et l'enregistre sur le stockage local.
     */
    fun generateThumbnail(context: Context, videoFile: File, videoId: String): File? {
        if (!videoFile.exists() || videoFile.length() == 0L) return null
        val cleanId = extractCleanVideoId(videoId).ifBlank { "vid_${System.currentTimeMillis()}" }
        val thumb = File(getThumbnailDirectory(context), "${cleanId}_thumb.jpg")
        if (thumb.exists() && thumb.length() > 0L) return thumb

        return try {
            val retr = MediaMetadataRetriever()
            retr.setDataSource(videoFile.absolutePath)
            val frame = retr.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: retr.frameAtTime
            if (frame != null) {
                FileOutputStream(thumb).use { fos ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                }
                frame.recycle()
            }
            retr.release()
            thumb.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur génération miniature: ${e.message}")
            null
        }
    }

    /**
     * Répertoire de stockage persistant des vidéos locales :
     * context.filesDir/media/videos
     */
    fun getVideoDirectory(context: Context): File {
        val dir = File(context.filesDir, "media/videos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Répertoire de stockage des miniatures vidéo :
     * context.filesDir/media/video_thumbs
     */
    fun getThumbnailDirectory(context: Context): File {
        val dir = File(context.filesDir, "media/video_thumbs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Fichier de suivi des vidéos supprimées (tombstones) pour éviter toute réimportation.
     */
    private fun getTombstonesFile(context: Context): File {
        return File(context.filesDir, TOMBSTONES_FILE_NAME)
    }

    /**
     * Vérifie si une vidéo a été marquée comme supprimée définitivement.
     */
    @Synchronized
    fun isDeleted(context: Context, videoId: String): Boolean {
        return try {
            val file = getTombstonesFile(context)
            if (!file.exists()) return false
            val content = file.readText()
            val jsonArray = JSONArray(content)
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.optString(i) == videoId) return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture tombstones: ${e.message}")
            false
        }
    }

    /**
     * Enregistre un identifiant dans les tombstones.
     */
    @Synchronized
    private fun markAsTombstoned(context: Context, videoId: String) {
        try {
            val file = getTombstonesFile(context)
            val jsonArray = if (file.exists()) {
                try { JSONArray(file.readText()) } catch (_: Exception) { JSONArray() }
            } else {
                JSONArray()
            }
            var exists = false
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.optString(i) == videoId) {
                    exists = true
                    break
                }
            }
            if (!exists) {
                jsonArray.put(videoId)
                file.writeText(jsonArray.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur écriture tombstone: ${e.message}")
        }
    }

    /**
     * Résout le fichier vidéo physique à partir d'un identifiant ou d'un chemin d'accès.
     */
    fun getVideoFile(context: Context, videoIdOrPath: String): File? {
        if (videoIdOrPath.isBlank()) return null

        // 1. Chemin direct absolu
        val directFile = File(videoIdOrPath)
        if (directFile.isAbsolute && directFile.exists() && directFile.length() > 0) {
            return directFile
        }

        // 2. Recherche dans le répertoire dédié
        val cleanId = extractCleanVideoId(videoIdOrPath)
        val candidate = File(getVideoDirectory(context), "$cleanId.mp4")
        if (candidate.exists() && candidate.length() > 0) {
            return candidate
        }

        // 3. Fallback sans suffixe forcé
        val candidateAlt = File(getVideoDirectory(context), videoIdOrPath)
        if (candidateAlt.exists() && candidateAlt.length() > 0) {
            return candidateAlt
        }

        return if (directFile.exists()) directFile else null
    }

    /**
     * Résout le fichier miniature associé.
     */
    fun getVideoThumbnailFile(context: Context, videoIdOrPath: String): File? {
        if (videoIdOrPath.isBlank()) return null
        val cleanId = extractCleanVideoId(videoIdOrPath)
        val thumb = File(getThumbnailDirectory(context), "${cleanId}_thumb.jpg")
        return if (thumb.exists() && thumb.length() > 0) thumb else null
    }

    /**
     * Charge le bitmap de la miniature en mémoire (avec cache et décodage sécurisé).
     */
    fun loadThumbnailBitmap(context: Context, videoIdOrPath: String): Bitmap? {
        val thumbFile = getVideoThumbnailFile(context, videoIdOrPath) ?: return null
        return try {
            BitmapFactory.decodeFile(thumbFile.absolutePath)
        } catch (e: OutOfMemoryError) {
            System.gc()
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(thumbFile.absolutePath, opts)
            } catch (_: Throwable) { null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extrait l'identifiant propre sans extension ni chemin.
     */
    fun extractCleanVideoId(pathOrId: String): String {
        val fileName = File(pathOrId).name
        return fileName.removeSuffix(".mp4").removeSuffix(".MP4")
    }

    /**
     * Supprime physiquement et définitivement la vidéo, sa miniature et marque le tombstone.
     */
    @Synchronized
    fun deleteVideoPhysical(context: Context, videoPathOrId: String): Boolean {
        if (videoPathOrId.isBlank()) return false
        var deletedAny = false
        val cleanId = extractCleanVideoId(videoPathOrId)

        // 1. Suppression du fichier vidéo dans context.filesDir/media/videos
        try {
            val videoInDir = File(getVideoDirectory(context), "$cleanId.mp4")
            if (videoInDir.exists()) {
                val ok = videoInDir.delete()
                if (ok) deletedAny = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur suppression vidéo dossier: ${e.message}")
        }

        // 2. Suppression si chemin direct absolu
        try {
            val directFile = File(videoPathOrId)
            if (directFile.isAbsolute && directFile.exists()) {
                val ok = directFile.delete()
                if (ok) deletedAny = true
            }
        } catch (_: Exception) {}

        // 3. Suppression de la miniature associée
        try {
            val thumb = File(getThumbnailDirectory(context), "${cleanId}_thumb.jpg")
            if (thumb.exists()) {
                thumb.delete()
                deletedAny = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur suppression miniature: ${e.message}")
        }

        // 4. Enregistrement dans les tombstones
        markAsTombstoned(context, cleanId)

        Log.i(TAG, "Purge physique définitive effectuée pour videoId=$cleanId (succès=$deletedAny)")
        return deletedAny
    }

    /**
     * Traite un Uri vidéo (Galerie / Caméra) :
     * - Copie dans le stockage local pérenne (context.filesDir/media/videos/)
     * - Extraction de la durée, des dimensions et de l'orientation
     * - Extraction d'une miniature JPEG haute qualité
     */
    suspend fun processVideoUri(
        context: Context,
        uri: Uri,
        maxDurationSec: Int = 180
    ): VideoProcessResult? = withContext(Dispatchers.IO) {
        try {
            val videoId = "vid_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val destFile = File(getVideoDirectory(context), "$videoId.mp4")

            // Copie du flux
            context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (!destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return@withContext null
            }

            processVideoFileInternal(context, destFile, videoId, maxDurationSec)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur traitement vidéo Uri: ${e.message}", e)
            null
        }
    }

    /**
     * Traite un fichier vidéo local existant (ex: enregistré via intent caméra).
     */
    suspend fun processVideoFile(
        context: Context,
        sourceFile: File,
        maxDurationSec: Int = 180
    ): VideoProcessResult? = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext null

            val videoId = "vid_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val destFile = File(getVideoDirectory(context), "$videoId.mp4")

            sourceFile.copyTo(destFile, overwrite = true)
            processVideoFileInternal(context, destFile, videoId, maxDurationSec)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur traitement vidéo fichier: ${e.message}", e)
            null
        }
    }

    private fun processVideoFileInternal(
        context: Context,
        videoFile: File,
        videoId: String,
        maxDurationSec: Int
    ): VideoProcessResult? {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 0
        var height = 0
        var thumbFile: File? = null

        try {
            retriever.setDataSource(videoFile.absolutePath)

            // Durée
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L

            // Limitation de durée si spécifiée
            val maxMs = maxDurationSec * 1000L
            if (maxDurationSec > 0 && durationMs > maxMs) {
                Log.w(TAG, "Vidéo trop longue: ${durationMs}ms > max ${maxMs}ms")
            }

            // Dimensions et rotation
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            val rawW = wStr?.toIntOrNull() ?: 720
            val rawH = hStr?.toIntOrNull() ?: 1280
            val rotation = rotationStr?.toIntOrNull() ?: 0

            if (rotation == 90 || rotation == 270) {
                width = rawH
                height = rawW
            } else {
                width = rawW
                height = rawH
            }

            // Extraction frame miniature à t=0.5 seconde (500 000 micros)
            val targetTimeUs = if (durationMs > 1000L) 500_000L else 0L
            val frameBitmap = retriever.getFrameAtTime(
                targetTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime

            if (frameBitmap != null) {
                val targetThumb = File(getThumbnailDirectory(context), "${videoId}_thumb.jpg")
                FileOutputStream(targetThumb).use { fos ->
                    // Compression modérée 80% pour économiser la mémoire et le stockage
                    frameBitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                }
                thumbFile = targetThumb
                frameBitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur extraction métadonnées/miniature vidéo: ${e.message}", e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        return VideoProcessResult(
            id = videoId,
            localFile = videoFile,
            thumbnailFile = thumbFile,
            durationMs = durationMs,
            width = if (width > 0) width else 720,
            height = if (height > 0) height else 1280,
            sizeBytes = videoFile.length()
        )
    }

    /**
     * Formate une durée en millisecondes en chaîne lisible MM:SS ou HH:MM:SS.
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSec = durationMs / 1000L
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Lit un fichier vidéo, le compresse avec BinarySmsCompressor et l'encode en Base64.
     * Notifie la progression de 0 à 100%.
     */
    fun videoFileToBase64(file: File, onProgress: ((Int) -> Unit)? = null): String? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val totalBytes = file.length()
            val byteOut = ByteArrayOutputStream(totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val buffer = ByteArray(64 * 1024) // 64 KB
            var bytesReadTotal = 0L

            onProgress?.invoke(0)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    byteOut.write(buffer, 0, read)
                    bytesReadTotal += read
                    if (totalBytes > 0) {
                        // 0% -> 40% : Lecture du fichier
                        val pct = ((bytesReadTotal.toFloat() / totalBytes.toFloat()) * 40f).toInt().coerceIn(0, 40)
                        onProgress?.invoke(pct)
                    }
                }
            }

            val rawBytes = byteOut.toByteArray()
            onProgress?.invoke(45)

            // 45% -> 80% : Compression BinarySmsCompressor
            val compressed = BinarySmsCompressor.compress(rawBytes)
            onProgress?.invoke(80)

            // 80% -> 100% : Encodage Base64
            val base64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
            onProgress?.invoke(100)

            base64
        } catch (e: Exception) {
            Log.e(TAG, "Erreur encodage vidéo en Base64: ${e.message}", e)
            null
        }
    }

    /**
     * Décode une vidéo reçue en Base64 compressé et l'enregistre sur le disque local.
     * Génère automatiquement la miniature associée si nécessaire.
     * Notifie la progression de 0 à 100%.
     */
    fun base64ToVideoFile(
        context: Context,
        base64: String,
        videoId: String,
        onProgress: ((Int) -> Unit)? = null
    ): File? {
        if (base64.isBlank()) return null
        return try {
            val cleanId = extractCleanVideoId(videoId).ifBlank { "vid_${System.currentTimeMillis()}" }
            val destFile = File(getVideoDirectory(context), "$cleanId.mp4")

            if (destFile.exists() && destFile.length() > 0) {
                onProgress?.invoke(100)
                return destFile
            }

            onProgress?.invoke(10)
            val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
            val decoded = Base64.decode(cleanBase64.trim(), Base64.DEFAULT)
            onProgress?.invoke(35)

            val rawBytes = try {
                BinarySmsCompressor.decompress(decoded)
            } catch (_: Exception) {
                decoded
            }
            onProgress?.invoke(60)

            val tempFile = File(getVideoDirectory(context), "${cleanId}_temp.mp4")
            val totalSize = rawBytes.size
            val chunkSize = 64 * 1024
            var written = 0

            FileOutputStream(tempFile).use { fos ->
                while (written < totalSize) {
                    val count = (totalSize - written).coerceAtMost(chunkSize)
                    fos.write(rawBytes, written, count)
                    written += count
                    val pct = 60 + ((written.toFloat() / totalSize.toFloat()) * 35f).toInt()
                    onProgress?.invoke(pct.coerceIn(60, 95))
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.renameTo(destFile)
            }

            // Génération de la miniature si inexistante
            val thumb = File(getThumbnailDirectory(context), "${cleanId}_thumb.jpg")
            if (!thumb.exists() || thumb.length() == 0L) {
                try {
                    val retr = MediaMetadataRetriever()
                    retr.setDataSource(destFile.absolutePath)
                    val frame = retr.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: retr.frameAtTime
                    if (frame != null) {
                        FileOutputStream(thumb).use { fos ->
                            frame.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                        }
                        frame.recycle()
                    }
                    retr.release()
                } catch (_: Exception) {}
            }

            onProgress?.invoke(100)
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Erreur restauration vidéo Base64: ${e.message}", e)
            null
        }
    }

    /**
     * Construit le payload pour les messages de conversation ou posts.
     * Format moderne souverain avec délimiteur '|' (évite les conflits d'URLs HTTPS) :
     * [VIDEO:id|durationMs|width|height|urlOrBase64|caption]
     */
    fun buildVideoPayload(
        id: String,
        durationMs: Long,
        width: Int,
        height: Int,
        caption: String = "",
        base64Data: String? = null,
        url: String? = null
    ): String {
        val cleanCaption = caption
            .replace("|", "/")
            .replace("[", "(")
            .replace("]", ")")
            .trim()

        val mediaRef = when {
            !url.isNullOrBlank() -> url.trim()
            !base64Data.isNullOrBlank() -> base64Data.trim()
            else -> ""
        }

        return when {
            mediaRef.isNotEmpty() && cleanCaption.isNotEmpty() ->
                "$PAYLOAD_VIDEO_PREFIX$id|$durationMs|$width|$height|$mediaRef|$cleanCaption]"
            mediaRef.isNotEmpty() ->
                "$PAYLOAD_VIDEO_PREFIX$id|$durationMs|$width|$height|$mediaRef]"
            cleanCaption.isNotEmpty() ->
                "$PAYLOAD_VIDEO_PREFIX$id|$durationMs|$width|$height||$cleanCaption]"
            else ->
                "$PAYLOAD_VIDEO_PREFIX$id|$durationMs|$width|$height]"
        }
    }

    /**
     * Vérifie si une chaîne est un payload vidéo.
     */
    fun isVideoPayload(text: String): Boolean {
        return text.startsWith(PAYLOAD_VIDEO_PREFIX) && text.endsWith("]")
    }

    /**
     * Décode le payload vidéo structuré (gère le nouveau délimiteur '|' et l'ancien ':').
     */
    fun parseVideoPayload(text: String): VideoPayload? {
        if (!isVideoPayload(text)) return null
        val raw = text.removePrefix(PAYLOAD_VIDEO_PREFIX).removeSuffix("]")

        if (raw.contains("|")) {
            val parts = raw.split("|")
            val id = parts.getOrNull(0) ?: return null
            val duration = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val width = parts.getOrNull(2)?.toIntOrNull() ?: 720
            val height = parts.getOrNull(3)?.toIntOrNull() ?: 1280
            val ref = parts.getOrNull(4)?.trim().orEmpty()
            val caption = parts.drop(5).joinToString("|")

            val isUrl = ref.startsWith("http://", ignoreCase = true) || ref.startsWith("https://", ignoreCase = true)
            val url = if (isUrl) ref else null
            val b64 = if (!isUrl && ref.isNotBlank()) ref else null

            return VideoPayload(id, duration, width, height, caption, b64, url)
        }

        // Format historique avec ":"
        val parts = raw.split(":")
        return when {
            parts.size >= 6 -> {
                val id = parts[0]
                val duration = parts[1].toLongOrNull() ?: 0L
                val width = parts[2].toIntOrNull() ?: 720
                val height = parts[3].toIntOrNull() ?: 1280

                val isHttp = parts[4].equals("http", true) || parts[4].equals("https", true)
                val url = if (isHttp) "${parts[4]}:${parts[5]}" else null
                val b64 = if (!isHttp && parts[4].isNotBlank()) parts[4] else null
                val caption = if (isHttp) parts.drop(6).joinToString(":") else parts.drop(5).joinToString(":")

                VideoPayload(id, duration, width, height, caption, b64, url)
            }
            parts.size == 5 -> {
                val id = parts[0]
                val duration = parts[1].toLongOrNull() ?: 0L
                val width = parts[2].toIntOrNull() ?: 720
                val height = parts[3].toIntOrNull() ?: 1280
                val fifth = parts[4]
                if (fifth.length > 80 || isProbableBase64(fifth)) {
                    VideoPayload(id, duration, width, height, "", fifth, null)
                } else {
                    VideoPayload(id, duration, width, height, fifth, null, null)
                }
            }
            parts.size == 4 -> {
                val id = parts[0]
                val duration = parts[1].toLongOrNull() ?: 0L
                val width = parts[2].toIntOrNull() ?: 720
                val height = parts[3].toIntOrNull() ?: 1280
                VideoPayload(id, duration, width, height, "", null, null)
            }
            parts.size >= 1 -> {
                val id = parts[0]
                VideoPayload(id, 0L, 720, 1280, "", null, null)
            }
            else -> null
        }
    }

    private fun isProbableBase64(str: String): Boolean {
        if (str.length < 32) return false
        return str.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun compressVideo(
        context: Context,
        sourceFile: File,
        onProgress: ((Int) -> Unit)? = null
    ): File = withContext(Dispatchers.Main) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext sourceFile

        val retriever = MediaMetadataRetriever()
        var isPortrait = false
        var origWidth = 720
        var origHeight = 1280
        try {
            retriever.setDataSource(sourceFile.absolutePath)
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rawW = wStr?.toIntOrNull() ?: 720
            val rawH = hStr?.toIntOrNull() ?: 1280
            val rot = rotStr?.toIntOrNull() ?: 0
            val (w, h) = if (rot == 90 || rot == 270) Pair(rawH, rawW) else Pair(rawW, rawH)
            origWidth = w
            origHeight = h
            isPortrait = h > w
        } catch (e: Exception) {
            Log.w(TAG, "Extraction dimensions pour compression: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // Si la vidéo est déjà <= 10 Mo ET <= 720p HD, pas besoin de réencoder
        if (sourceFile.length() <= 10 * 1024 * 1024L && maxOf(origWidth, origHeight) <= 1280) {
            onProgress?.invoke(100)
            return@withContext sourceFile
        }

        val outputFile = File(getVideoDirectory(context), "comp_${System.currentTimeMillis()}_${sourceFile.name}")
        if (outputFile.exists()) outputFile.delete()

        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            var isFinished = false
            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main + kotlinx.coroutines.Job())
            var progressJob: kotlinx.coroutines.Job? = null
            var transformerInstance: androidx.media3.transformer.Transformer? = null

            try {
                val mediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(sourceFile))
                // 720p : 1280 en hauteur pour portrait (720x1280) ou 720 pour paysage (1280x720)
                val targetHeight = if (isPortrait) 1280 else 720
                val presentation = androidx.media3.effect.Presentation.createForHeight(targetHeight)
                val editedMediaItem = androidx.media3.transformer.EditedMediaItem.Builder(mediaItem)
                    .setEffects(androidx.media3.transformer.Effects(emptyList(), listOf(presentation)))
                    .build()

                val progressHolder = androidx.media3.transformer.ProgressHolder()

                val transformer = androidx.media3.transformer.Transformer.Builder(context)
                    .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                    .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                    .addListener(object : androidx.media3.transformer.Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                            progressJob?.cancel()
                            if (!isFinished) {
                                isFinished = true
                                onProgress?.invoke(100)
                                if (outputFile.exists() && outputFile.length() > 0) {
                                    Log.i(TAG, "Compression vidéo réussie : ${sourceFile.length() / 1024} Ko -> ${outputFile.length() / 1024} Ko")
                                    if (continuation.isActive) continuation.resume(outputFile) {}
                                } else {
                                    if (continuation.isActive) continuation.resume(sourceFile) {}
                                }
                            }
                        }

                        override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                            progressJob?.cancel()
                            if (!isFinished) {
                                isFinished = true
                                Log.e(TAG, "Erreur compression Transformer (${exportException.message}) -> fallback fichier source")
                                if (continuation.isActive) continuation.resume(sourceFile) {}
                            }
                        }
                    })
                    .build()

                transformerInstance = transformer

                continuation.invokeOnCancellation {
                    progressJob?.cancel()
                    try { transformer.cancel() } catch (_: Exception) {}
                    if (outputFile.exists()) outputFile.delete()
                }

                transformer.start(editedMediaItem, outputFile.absolutePath)

                // Polling du progrès de compression
                progressJob = scope.launch {
                    while (!isFinished && continuation.isActive) {
                        kotlinx.coroutines.delay(150)
                        if (!isFinished) {
                            val progressState = transformer.getProgress(progressHolder)
                            if (progressState == androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE) {
                                val p = progressHolder.progress.coerceIn(0, 99)
                                onProgress?.invoke(p)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                progressJob?.cancel()
                Log.e(TAG, "Exception lancement compression Transformer: ${e.message} -> fallback source", e)
                if (continuation.isActive) continuation.resume(sourceFile) {}
            }
        }
    }
}
