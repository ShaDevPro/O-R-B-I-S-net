package com.sha.orbis.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.sha.orbis.R
import com.sha.orbis.sms.BinarySmsCompressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire modulaire de téléchargement et d'exportation de médias et documents
 * vers le stockage public du téléphone (Galerie / Pictures et Téléchargements / Downloads).
 * 
 * Entièrement conforme aux spécifications Scoped Storage d'Android 10+ (API 29+) et rétrocompatible.
 */
object MediaDownloadManager {

    private const val ORBIS_FOLDER = "Orbis"
    private const val TAG = "MediaDownloadManager"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Sauvegarde de manière synchrone/suspendue une image dans la galerie publique de l'appareil (Dossier Pictures/Orbis).
     * 
     * @param context Contexte Android
     * @param imageSource Chemin de fichier local ou chaîne encodée en Base64
     * @param customName Nom personnalisé optionnel (sans extension)
     * @param showToast Afficher un toast de confirmation ou d'erreur
     */
    suspend fun saveImageToGallery(
        context: Context,
        imageSource: String,
        customName: String? = null,
        showToast: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (imageSource.isBlank()) return@withContext false

            val bitmap = MediaAttachmentHelper.loadBitmap(imageSource) ?: return@withContext false
            val cleanBaseName = customName?.filter { it.isLetterOrDigit() || it in "_-" }?.take(30)?.ifBlank { null }
                ?: "IMG_${System.currentTimeMillis()}"
            val fileName = "$cleanBaseName.jpg"

            var success = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ORBIS_FOLDER")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output: OutputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                        success = true
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    ORBIS_FOLDER
                ).apply { if (!exists()) mkdirs() }

                val destFile = File(picturesDir, fileName)
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    success = true
                }
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("image/jpeg"), null)
            }

            if (showToast) {
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.media_saved_to_gallery),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.media_download_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            success
        } catch (e: Exception) {
            android.util.Log.e("MediaDownloadManager", "Erreur lors de la sauvegarde de l'image: ${e.message}", e)
            if (showToast) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.media_download_error), Toast.LENGTH_SHORT).show()
                }
            }
            false
        }
    }

    /**
     * Lance la sauvegarde d'une image en arrière-plan (non-bloquant) sans nécessiter de CoroutineScope Compose.
     */
    fun saveImageAsync(
        context: Context,
        imageSource: String,
        customName: String? = null,
        showToast: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val ok = saveImageToGallery(context, imageSource, customName, showToast)
            if (onComplete != null) {
                withContext(Dispatchers.Main) {
                    onComplete(ok)
                }
            }
        }
    }

    /**
     * Sauvegarde une vidéo dans la galerie publique de l'appareil (Movies/Orbis).
     *
     * La source peut être un chemin local, un identifiant vidéo Orbis, une URL HTTPS, un Base64,
     * un File ou un ByteArray. Les flux sont copiés sans charger les vidéos réseau en mémoire.
     */
    suspend fun saveVideoToGallery(
        context: Context,
        videoSource: Any,
        customName: String? = null,
        showToast: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = buildVideoFileName(customName, videoSource)
            val success = when (videoSource) {
                is File -> {
                    if (!videoSource.exists() || !videoSource.isFile) {
                        false
                    } else {
                        videoSource.inputStream().use { input ->
                            writeVideoToGallery(context, input, fileName, inferMimeType(fileName, "video/mp4"))
                        }
                    }
                }
                is ByteArray -> {
                    if (videoSource.isEmpty()) {
                        false
                    } else {
                        ByteArrayInputStream(videoSource).use { input ->
                            writeVideoToGallery(context, input, fileName, inferMimeType(fileName, "video/mp4"))
                        }
                    }
                }
                is String -> saveVideoStringSource(context, videoSource, fileName)
                else -> false
            }

            showVideoSaveToast(context, showToast, success)
            success
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la sauvegarde de la vidéo: ${e.message}", e)
            showVideoSaveToast(context, showToast, false)
            false
        }
    }

    /**
     * Lance la sauvegarde d'une vidéo en arrière-plan (non-bloquant).
     */
    fun saveVideoAsync(
        context: Context,
        videoSource: Any,
        customName: String? = null,
        showToast: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val ok = saveVideoToGallery(context, videoSource, customName, showToast)
            if (onComplete != null) {
                withContext(Dispatchers.Main) {
                    onComplete(ok)
                }
            }
        }
    }

    /**
     * Sauvegarde de manière synchrone/suspendue un document dans le dossier public Téléchargements (Downloads/Orbis).
     * 
     * @param context Contexte Android
     * @param base64OrSourceFile Données Base64 ou fichier File source
     * @param displayName Nom du fichier avec extension
     * @param showToast Afficher un toast de confirmation ou d'erreur
     */
    suspend fun saveDocToDownloads(
        context: Context,
        base64OrSourceFile: Any,
        displayName: String,
        showToast: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes: ByteArray = when (base64OrSourceFile) {
                is String -> {
                    val clean = base64OrSourceFile.trim()
                    val decoded = Base64.decode(clean, Base64.DEFAULT)
                    try { BinarySmsCompressor.decompress(decoded) } catch (_: Exception) { decoded }
                }
                is File -> base64OrSourceFile.readBytes()
                is ByteArray -> base64OrSourceFile
                else -> return@withContext false
            }

            if (bytes.isEmpty()) return@withContext false

            val cleanName = displayName.filter { it.isLetterOrDigit() || it in "._- ()" }
                .ifBlank { "document_${System.currentTimeMillis()}" }

            val extension = cleanName.substringAfterLast('.', "").lowercase()
            val mimeType = if (extension.isNotBlank()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
            } else {
                "application/octet-stream"
            }

            var success = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$ORBIS_FOLDER")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output: OutputStream ->
                        output.write(bytes)
                        success = true
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    ORBIS_FOLDER
                ).apply { if (!exists()) mkdirs() }

                val destFile = File(downloadsDir, cleanName)
                FileOutputStream(destFile).use { out ->
                    out.write(bytes)
                    success = true
                }
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            }

            if (showToast) {
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.media_saved_to_downloads, cleanName),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.media_download_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            success
        } catch (e: Exception) {
            android.util.Log.e("MediaDownloadManager", "Erreur lors de la sauvegarde du document: ${e.message}", e)
            if (showToast) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.media_download_error), Toast.LENGTH_SHORT).show()
                }
            }
            false
        }
    }

    /**
     * Lance la sauvegarde d'un document en arrière-plan (non-bloquant).
     */
    fun saveDocAsync(
        context: Context,
        base64OrSourceFile: Any,
        displayName: String,
        showToast: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val ok = saveDocToDownloads(context, base64OrSourceFile, displayName, showToast)
            if (onComplete != null) {
                withContext(Dispatchers.Main) {
                    onComplete(ok)
                }
            }
        }
    }

    private fun saveVideoStringSource(
        context: Context,
        source: String,
        fileName: String
    ): Boolean {
        val cleanSource = source.trim()
        if (cleanSource.isBlank()) return false

        if (cleanSource.isNetworkUrl()) {
            val request = Request.Builder().url(cleanSource).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false

                val body = response.body ?: return false
                val mimeType = body.contentType()?.toString()
                    ?.takeIf { it.startsWith("video/", ignoreCase = true) }
                    ?: inferMimeType(fileName, "video/mp4")

                return body.byteStream().use { input ->
                    writeVideoToGallery(context, input, fileName, mimeType)
                }
            }
        }

        resolveLocalVideoFile(context, cleanSource)?.let { file ->
            return file.inputStream().use { input ->
                writeVideoToGallery(context, input, fileName, inferMimeType(file.name, "video/mp4"))
            }
        }

        val cleanBase64 = cleanSource.substringAfter(",", cleanSource)
        val decoded = Base64.decode(cleanBase64, Base64.DEFAULT)
        val bytes = try {
            BinarySmsCompressor.decompress(decoded)
        } catch (_: Exception) {
            decoded
        }

        if (bytes.isEmpty()) return false

        return ByteArrayInputStream(bytes).use { input ->
            writeVideoToGallery(context, input, fileName, inferMimeType(fileName, "video/mp4"))
        }
    }

    private fun writeVideoToGallery(
        context: Context,
        input: InputStream,
        fileName: String,
        mimeType: String
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ORBIS_FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false

            return try {
                val outputStream = resolver.openOutputStream(uri)
                if (outputStream == null) {
                    resolver.delete(uri, null, null)
                    return false
                }

                outputStream.use { output ->
                    input.copyTo(output)
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }

        @Suppress("DEPRECATION")
        val moviesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            ORBIS_FOLDER
        ).apply { if (!exists()) mkdirs() }

        val destFile = File(moviesDir, fileName)
        FileOutputStream(destFile).use { output ->
            input.copyTo(output)
        }
        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
        return true
    }

    private fun resolveLocalVideoFile(context: Context, source: String): File? {
        val directFile = if (source.length <= 512) {
            File(source).takeIf { it.exists() && it.isFile }
        } else {
            null
        }

        return directFile
            ?: VideoMediaHelper.getVideoFile(context, source)?.takeIf { it.exists() && it.isFile }
    }

    private suspend fun showVideoSaveToast(context: Context, showToast: Boolean, success: Boolean) {
        if (!showToast) return

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.video_saved_to_gallery else R.string.video_download_error
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun buildVideoFileName(customName: String?, source: Any): String {
        val rawName = customName?.takeIf { it.isNotBlank() }
            ?: when (source) {
                is File -> source.name
                is String -> source.extractSourceDisplayName()
                else -> null
            }
            ?: "VID_${System.currentTimeMillis()}"

        val cleanName = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBefore('?')
            .substringBefore('#')
            .filter { it.isLetterOrDigit() || it in "._- ()" }
            .take(80)
            .trim('.', ' ', '_')
            .ifBlank { "VID_${System.currentTimeMillis()}" }

        val extension = cleanName.substringAfterLast('.', "").lowercase()
        val videoExtensions = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")
        return if (extension in videoExtensions) cleanName else "$cleanName.mp4"
    }

    private fun inferMimeType(fileName: String, fallback: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return if (extension.isNotBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: fallback
        } else {
            fallback
        }
    }

    private fun String.isNetworkUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun String.extractSourceDisplayName(): String? {
        if (!isNetworkUrl()) return takeIf { it.length <= 160 }

        return runCatching {
            Uri.parse(this).lastPathSegment?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
