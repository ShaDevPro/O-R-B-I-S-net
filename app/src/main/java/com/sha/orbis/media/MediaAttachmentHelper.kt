package com.sha.orbis.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.sha.orbis.cache.MediaMemoryCache
import com.sha.orbis.sms.BinarySmsCompressor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object MediaAttachmentHelper {

    const val PAYLOAD_IMAGE_PREFIX = "[IMAGE:"
    const val PAYLOAD_ALBUM_PREFIX = "[ALBUM:"
    const val PAYLOAD_DOC_PREFIX = "[DOC:"
    const val MAX_IMAGE_DIMENSION = 1024
    const val MAX_DOC_SIZE_BYTES = 1024 * 1024L // 1 MB limit

    // Nostr WebSocket Relay Limits (Standard public relay frame limit is 64 KB)
    const val MAX_NOSTR_PAYLOAD_SAFE_BYTES = 40 * 1024L // 40 KB unencrypted text payload limit
    const val TARGET_SINGLE_IMAGE_BYTES = 26 * 1024L    // 26 KB target for single photo
    const val TARGET_ALBUM_TOTAL_BYTES = 32 * 1024L     // 32 KB total budget for an entire album
    const val MAX_SINGLE_IMAGE_DIM = 800
    const val MAX_ALBUM_IMAGE_DIM = 540

    data class ImagePayload(
        val id: String,
        val base64Data: String,
        val caption: String = ""
    )

    data class AlbumPayload(
        val id: String,
        val images: List<String>,
        val caption: String = ""
    )

    data class DocPayload(
        val id: String,
        val fileName: String,
        val fileSizeFormatted: String,
        val base64Data: String,
        val caption: String = ""
    )

    fun isImagePayload(text: String): Boolean = text.startsWith(PAYLOAD_IMAGE_PREFIX) && text.endsWith("]")
    fun isAlbumPayload(text: String): Boolean = text.startsWith(PAYLOAD_ALBUM_PREFIX) && text.endsWith("]")
    fun isDocPayload(text: String): Boolean = text.startsWith(PAYLOAD_DOC_PREFIX) && text.endsWith("]")

    fun parseAlbumPayload(text: String): AlbumPayload? {
        if (!isAlbumPayload(text)) return null
        return try {
            val jsonStr = text.removePrefix(PAYLOAD_ALBUM_PREFIX).removeSuffix("]")
            val json = org.json.JSONObject(jsonStr)
            val id = json.optString("id", "album_${System.currentTimeMillis()}")
            val caption = json.optString("caption", "")
            val arr = json.optJSONArray("images") ?: return null
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            if (list.isEmpty()) return null
            AlbumPayload(id = id, images = list, caption = caption)
        } catch (_: Exception) {
            null
        }
    }

    fun buildAlbumPayload(id: String, images: List<String>, caption: String = ""): String {
        val json = org.json.JSONObject().apply {
            put("id", id)
            put("caption", caption.trim())
            val arr = org.json.JSONArray()
            images.forEach { arr.put(it) }
            put("images", arr)
        }
        return "$PAYLOAD_ALBUM_PREFIX$json]"
    }

    fun parseImagePayload(text: String): ImagePayload? {
        if (!isImagePayload(text)) return null
        val raw = text.removePrefix(PAYLOAD_IMAGE_PREFIX).removeSuffix("]")
        val parts = raw.split(":")
        return when {
            parts.size >= 3 -> {
                val id = parts[0]
                val base64 = parts[1]
                val caption = parts.drop(2).joinToString(":")
                ImagePayload(id = id, base64Data = base64, caption = caption)
            }
            parts.size == 2 -> {
                ImagePayload(id = parts[0], base64Data = parts[1], caption = "")
            }
            parts.size == 1 -> {
                ImagePayload(id = "img_${System.currentTimeMillis()}", base64Data = parts[0], caption = "")
            }
            else -> null
        }
    }

    fun buildImagePayload(id: String, base64: String, caption: String = ""): String {
        val cleanCaption = caption.replace(":", " ").replace("[", "(").replace("]", ")").trim()
        return if (cleanCaption.isNotEmpty()) {
            "$PAYLOAD_IMAGE_PREFIX$id:$base64:$cleanCaption]"
        } else {
            "$PAYLOAD_IMAGE_PREFIX$id:$base64]"
        }
    }

    fun parseDocPayload(text: String): DocPayload? {
        if (!isDocPayload(text)) return null
        val raw = text.removePrefix(PAYLOAD_DOC_PREFIX).removeSuffix("]")
        val parts = raw.split(":")
        return when {
            parts.size >= 5 -> {
                DocPayload(
                    id = parts[0],
                    fileName = parts[1],
                    fileSizeFormatted = parts[2],
                    base64Data = parts[3],
                    caption = parts.drop(4).joinToString(":")
                )
            }
            parts.size == 4 -> {
                DocPayload(
                    id = parts[0],
                    fileName = parts[1],
                    fileSizeFormatted = parts[2],
                    base64Data = parts[3],
                    caption = ""
                )
            }
            else -> null
        }
    }

    fun buildDocPayload(
        id: String,
        fileName: String,
        fileSizeFormatted: String,
        base64: String,
        caption: String = ""
    ): String {
        val cleanName = fileName.replace(":", "_").replace("[", "(").replace("]", ")")
        val cleanSize = fileSizeFormatted.replace(":", "_")
        val cleanCaption = caption.replace(":", " ").replace("[", "(").replace("]", ")").trim()
        return if (cleanCaption.isNotEmpty()) {
            "$PAYLOAD_DOC_PREFIX$id:$cleanName:$cleanSize:$base64:$cleanCaption]"
        } else {
            "$PAYLOAD_DOC_PREFIX$id:$cleanName:$cleanSize:$base64]"
        }
    }

    // =========================================================================
    // Image Processing & Caching
    // =========================================================================
    // Image Processing & Caching
    // =========================================================================

    fun processImageUri(
        context: Context,
        uri: Uri,
        customId: String? = null,
        targetMaxBytes: Long = TARGET_SINGLE_IMAGE_BYTES,
        maxDimension: Int = MAX_SINGLE_IMAGE_DIM
    ): Pair<File?, String?> {
        val tempFile = File(context.cacheDir, "temp_img_${System.currentTimeMillis()}.tmp")
        return try {
            val id = customId ?: "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

            // 1. Copy stream once to a temporary file to avoid stream-consumed or permission revocation errors
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return Pair(null, null)
            }

            processImageFromFile(context, tempFile, id, targetMaxBytes, maxDimension)
        } catch (t: Throwable) {
            android.util.Log.e("MediaAttachment", "Error processing image URI: ${t.message}", t)
            Pair(null, null)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun processImageFromFile(
        context: Context,
        sourceFile: File,
        customId: String? = null,
        targetMaxBytes: Long = TARGET_SINGLE_IMAGE_BYTES,
        maxDimension: Int = MAX_SINGLE_IMAGE_DIM
    ): Pair<File?, String?> {
        return try {
            val id = customId ?: "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            if (!sourceFile.exists() || sourceFile.length() == 0L) return Pair(null, null)

            // 1. Decode bounds only to prevent OutOfMemoryError on 12MP-108MP photos
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return Pair(null, null)

            // 2. Compute inSampleSize targeting maxDimension
            var inSampleSize = 1
            var w = origWidth
            var h = origHeight
            while (w > maxDimension * 1.5 || h > maxDimension * 1.5) {
                inSampleSize *= 2
                w /= 2
                h /= 2
            }

            // 3. Decode sampled bitmap into memory
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val sampledBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
                ?: return Pair(null, null)

            // 4. Correct EXIF orientation from file
            val rotatedBitmap = correctOrientationFromFile(sourceFile.absolutePath, sampledBitmap)

            // 5. Adaptively compress to target budget (Tuning quality and dimensions)
            val (jpegBytes, finalBitmap) = compressBitmapToBudget(rotatedBitmap, targetMaxBytes, maxDimension)

            // 6. Save to persistent local JPEG file
            val imagesDir = File(context.filesDir, "media/images").apply { if (!exists()) mkdirs() }
            val destFile = File(imagesDir, "$id.jpg")

            FileOutputStream(destFile).use { out ->
                out.write(jpegBytes)
            }

            // 7. Store in L1 memory cache (dual key, single bitmap in RAM)
            val compressed = BinarySmsCompressor.compress(jpegBytes)
            val base64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
            MediaMemoryCache.putDualKey(destFile.absolutePath, base64, finalBitmap)

            Pair(destFile, base64)
        } catch (t: Throwable) {
            android.util.Log.e("MediaAttachment", "Error processing image file: ${t.message}", t)
            Pair(null, null)
        }
    }

    /**
     * Compresses a bitmap to strictly fit within targetMaxBytes.
     * Iteratively tunes JPEG quality and dimensions to guarantee Nostr relay acceptance.
     */
    fun compressBitmapToBudget(
        bitmap: Bitmap,
        targetMaxBytes: Long,
        initialMaxDim: Int = MAX_SINGLE_IMAGE_DIM
    ): Pair<ByteArray, Bitmap> {
        val currentBitmap = scaleDownIfNeeded(bitmap, initialMaxDim)
        val qualities = intArrayOf(75, 65, 52, 42, 35, 28)
        var bestBytes: ByteArray? = null

        // Pass 1: Adjust JPEG quality at initial dimension
        for (q in qualities) {
            val byteOut = ByteArrayOutputStream()
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, q, byteOut)
            val bytes = byteOut.toByteArray()
            bestBytes = bytes
            if (bytes.size <= targetMaxBytes) {
                return Pair(bytes, currentBitmap)
            }
        }

        // Pass 2: Iteratively downscale dimension and compress
        val downscaleFactors = floatArrayOf(0.75f, 0.55f, 0.40f, 0.30f)
        for (factor in downscaleFactors) {
            val targetDim = (initialMaxDim * factor).toInt().coerceAtLeast(180)
            val scaled = scaleDownIfNeeded(currentBitmap, targetDim)
            for (q in intArrayOf(60, 48, 38, 25)) {
                val byteOut = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, byteOut)
                val bytes = byteOut.toByteArray()
                bestBytes = bytes
                if (bytes.size <= targetMaxBytes) {
                    return Pair(bytes, scaled)
                }
            }
        }

        // Fallback: Return best achieved compression
        val fallbackBytes = bestBytes ?: run {
            val byteOut = ByteArrayOutputStream()
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 25, byteOut)
            byteOut.toByteArray()
        }
        return Pair(fallbackBytes, currentBitmap)
    }

    /**
     * Checks if a base64 or unencrypted string payload fits safely in a Nostr Kind 4 event frame.
     */
    fun isPayloadWithinNostrLimit(payload: String): Boolean {
        return payload.length.toLong() <= MAX_NOSTR_PAYLOAD_SAFE_BYTES
    }

    private fun correctOrientationFromFile(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    fun base64ToImageFile(context: Context, base64: String, id: String): File? {
        return try {
            val clean = base64.trim()
            val decoded = Base64.decode(clean, Base64.DEFAULT)
            val rawBytes = try {
                BinarySmsCompressor.decompress(decoded)
            } catch (_: Exception) {
                decoded
            }

            val imagesDir = File(context.filesDir, "media/images").apply { if (!exists()) mkdirs() }
            val cleanId = id.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "img_${System.currentTimeMillis()}" }
            val file = File(imagesDir, "$cleanId.jpg")

            if (!file.exists() || file.length() == 0L) {
                FileOutputStream(file).use { out ->
                    out.write(rawBytes)
                }
            }

            // Populate cache if not cached
            if (MediaMemoryCache.get(file.absolutePath) == null) {
                val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                if (bitmap != null) {
                    MediaMemoryCache.putDualKey(file.absolutePath, base64, bitmap)
                }
            }

            file
        } catch (_: Exception) {
            null
        }
    }

    fun loadBitmap(pathOrBase64: String): Bitmap? {
        if (pathOrBase64.isBlank()) return null

        // 1. Check L1 memory cache (< 1ms)
        MediaMemoryCache.get(pathOrBase64)?.let { return it }

        // 2. Try file path if it starts with '/' or contains file separator
        val isLikelyFilePath = pathOrBase64.startsWith("/") ||
                pathOrBase64.startsWith("file://") ||
                pathOrBase64.contains(File.separator)

        if (isLikelyFilePath) {
            try {
                val cleanPath = pathOrBase64.removePrefix("file://")
                val file = File(cleanPath)
                if (file.exists() && file.isFile && file.length() > 0L) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                    val maxDim = 1080
                    var sampleSize = 1
                    if (bounds.outWidth > maxDim || bounds.outHeight > maxDim) {
                        val halfWidth = bounds.outWidth / 2
                        val halfHeight = bounds.outHeight / 2
                        while ((halfWidth / sampleSize) >= maxDim || (halfHeight / sampleSize) >= maxDim) {
                            sampleSize *= 2
                        }
                    }
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                    if (bitmap != null) {
                        val finalBitmap = scaleDownIfNeeded(bitmap, maxDim)
                        MediaMemoryCache.put(pathOrBase64, finalBitmap)
                        return finalBitmap
                    }
                }
            } catch (_: Throwable) {}
            // A file path is never a Base64 string — do NOT fall through to Base64 decoder
            return null
        }

        // 3. Try Base64 string
        try {
            val cleanBase64 = if (pathOrBase64.contains(",")) pathOrBase64.substringAfter(",") else pathOrBase64
            val decoded = Base64.decode(cleanBase64.trim(), Base64.DEFAULT)
            val raw = try { BinarySmsCompressor.decompress(decoded) } catch (_: Exception) { decoded }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxDim = 1080
            var sampleSize = 1
            if (bounds.outWidth > maxDim || bounds.outHeight > maxDim) {
                val halfWidth = bounds.outWidth / 2
                val halfHeight = bounds.outHeight / 2
                while ((halfWidth / sampleSize) >= maxDim || (halfHeight / sampleSize) >= maxDim) {
                    sampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, decodeOptions)
            if (bitmap != null) {
                val finalBitmap = scaleDownIfNeeded(bitmap, maxDim)
                MediaMemoryCache.put(pathOrBase64, finalBitmap)
                return finalBitmap
            }
        } catch (_: Throwable) {}

        return null
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width > height) {
            targetWidth = maxDim
            targetHeight = (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDim
            targetWidth = (maxDim * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    // =========================================================================
    // Document Processing & Opening
    // =========================================================================

    fun processDocumentUri(context: Context, uri: Uri): Triple<File?, String, Long>? {
        return try {
            var displayName = "document_${System.currentTimeMillis()}"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            if (fileSize > MAX_DOC_SIZE_BYTES) {
                return null // Exceeds 1 MB limit
            }

            val docsDir = File(context.filesDir, "media/docs").apply { if (!exists()) mkdirs() }
            val cleanName = displayName.filter { it.isLetterOrDigit() || it in "._-" }.ifBlank { "doc_${System.currentTimeMillis()}" }
            val destFile = File(docsDir, cleanName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val finalSize = if (fileSize > 0L) fileSize else destFile.length()
            Triple(destFile, displayName, finalSize)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeDocToBase64(file: File): String {
        return try {
            val bytes = file.readBytes()
            val compressed = BinarySmsCompressor.compress(bytes)
            Base64.encodeToString(compressed, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    fun base64ToDocFile(context: Context, base64: String, fileName: String, id: String): File? {
        return try {
            val clean = base64.trim()
            val decoded = Base64.decode(clean, Base64.DEFAULT)
            val raw = try { BinarySmsCompressor.decompress(decoded) } catch (_: Exception) { decoded }

            val docsDir = File(context.filesDir, "media/docs").apply { if (!exists()) mkdirs() }
            val cleanName = fileName.filter { it.isLetterOrDigit() || it in "._-" }.ifBlank { "${id}.bin" }
            val destFile = File(docsDir, "${id}_$cleanName")

            if (!destFile.exists() || destFile.length() == 0L) {
                FileOutputStream(destFile).use { out ->
                    out.write(raw)
                }
            }
            destFile
        } catch (_: Exception) {
            null
        }
    }

    fun openDocument(context: Context, file: File): Boolean {
        return try {
            if (!file.exists() || file.length() == 0L) return false

            val ext = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes.toFloat() / (1024 * 1024))
        }
    }
}
