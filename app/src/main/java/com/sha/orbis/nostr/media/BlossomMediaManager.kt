package com.sha.orbis.nostr.media

import android.content.Context
import android.util.Base64
import android.util.Log
import com.sha.orbis.media.VideoMediaHelper
import com.sha.orbis.nostr.crypto.Bech32
import com.sha.orbis.nostr.crypto.Secp256k1
import com.sha.orbis.nostr.identity.NostrIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import okio.sink
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire souverain de médias pour le réseau décentralisé Nostr,
 * conforme aux spécifications officielles Blossom (BUD-01, BUD-02, BUD-11, NIP-94).
 *
 * Permet l'envoi et la réception universelle de fichiers multimédias lourds (vidéos jusqu'à 50 Mo)
 * sans saturer ni déclencher les limites WebSocket des relais Nostr publics (64-128 Ko).
 */
object BlossomMediaManager {

    private const val TAG = "BlossomMediaManager"

    // Serveurs Blossom souverains avec basculement automatique
    val BLOSSOM_SERVERS = listOf(
        "https://cdn.nostrcheck.me/upload",
        "https://blossom.primal.net/upload"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class BlossomUploadResult(
        val url: String,
        val sha256: String,
        val sizeBytes: Long,
        val mimeType: String = "video/mp4"
    )

    /**
     * Calcule le hash SHA-256 d'un fichier en flux continu (sans saturer la mémoire RAM).
     */
    fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { fis ->
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return Bech32.bytesToHex(digest.digest())
    }

    /**
     * Crée l'en-tête d'autorisation Nostr conforme à la spécification BUD-11 (Kind 24242).
     */
    fun createBlossomAuthHeader(
        identityManager: NostrIdentityManager,
        sha256Hex: String,
        fileName: String = "video.mp4"
    ): String {
        val nowSec = System.currentTimeMillis() / 1000L
        val expSec = nowSec + 3600L // Valide pendant 1 heure

        val tags = listOf(
            listOf("t", "upload"),
            listOf("x", sha256Hex.lowercase()),
            listOf("expiration", expSec.toString())
        )

        val authEvent = identityManager.signEvent(
            kind = 24242,
            tags = tags,
            content = "Upload $fileName",
            createdAtSeconds = nowSec
        )

        val eventJson = authEvent.toJson().toString()
        val authBase64 = Base64.encodeToString(eventJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Nostr $authBase64"
    }

    /**
     * Téléverse un fichier vidéo sur les serveurs Blossom décentralisés avec suivi de progression.
     * En cas d'échec sur le premier serveur, bascule automatiquement sur le suivant.
     */
    suspend fun uploadVideo(
        context: Context,
        file: File,
        onProgress: ((Int) -> Unit)? = null
    ): BlossomUploadResult? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "uploadVideo: fichier inexistant ou vide (${file.absolutePath})")
            return@withContext null
        }

        onProgress?.invoke(5)
        val sha256Hex = try {
            computeFileSha256(file)
        } catch (e: Exception) {
            Log.e(TAG, "uploadVideo: erreur calcul SHA-256: ${e.message}")
            return@withContext null
        }
        onProgress?.invoke(10)

        val identityManager = NostrIdentityManager.getInstance(context)
        val authHeader = createBlossomAuthHeader(identityManager, sha256Hex, file.name)
        val mediaType = "video/mp4".toMediaType()

        for (endpoint in BLOSSOM_SERVERS) {
            try {
                Log.i(TAG, "Tentative de téléversement vers $endpoint (taille=${file.length()} octets)...")

                val countingBody = ProgressRequestBody(file, mediaType) { bytesWritten, totalBytes ->
                    if (totalBytes > 0) {
                        // 10% -> 90% pour le transfert réseau
                        val pct = 10 + ((bytesWritten.toFloat() / totalBytes.toFloat()) * 80f).toInt().coerceIn(0, 80)
                        onProgress?.invoke(pct)
                    }
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .put(countingBody)
                    .header("Authorization", authHeader)
                    .header("X-SHA-256", sha256Hex)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    Log.d(TAG, "Réponse $endpoint: HTTP ${response.code} — $respBody")

                    if (response.isSuccessful || response.code == 200 || response.code == 201) {
                        val parsedUrl = parseUploadedUrl(respBody, endpoint, sha256Hex)
                        if (!parsedUrl.isNullOrBlank()) {
                            onProgress?.invoke(100)
                            Log.i(TAG, "✅ Vidéo téléversée avec succès sur Blossom: $parsedUrl")
                            return@withContext BlossomUploadResult(
                                url = parsedUrl,
                                sha256 = sha256Hex,
                                sizeBytes = file.length(),
                                mimeType = "video/mp4"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Échec téléversement sur $endpoint: ${e.message}")
            }
        }

        Log.e(TAG, "❌ Échec du téléversement sur tous les serveurs Blossom configurés")
        null
    }

    /**
     * Analyse la réponse JSON du serveur Blossom (BUD-02 Blob Descriptor).
     */
    private fun parseUploadedUrl(responseJson: String, endpoint: String, sha256Hex: String): String? {
        return try {
            val json = JSONObject(responseJson)
            val url = json.optString("url").takeIf { it.isNotBlank() }
            if (url != null) return url

            // Fallback si le serveur retourne juste le sha256
            val serverBase = endpoint.substringBeforeLast("/upload")
            "$serverBase/$sha256Hex.mp4"
        } catch (_: Exception) {
            val serverBase = endpoint.substringBeforeLast("/upload")
            "$serverBase/$sha256Hex.mp4"
        }
    }

    /**
     * Télécharge une vidéo depuis un serveur Blossom vers le stockage local de l'application
     * et génère la miniature associée.
     */
    suspend fun downloadVideo(
        context: Context,
        videoUrl: String,
        targetVideoId: String,
        onProgress: ((Int) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        val cleanId = VideoMediaHelper.extractCleanVideoId(targetVideoId).ifBlank { "vid_${System.currentTimeMillis()}" }
        val destFile = File(VideoMediaHelper.getVideoDirectory(context), "$cleanId.mp4")

        // Déjà téléchargé et valide
        if (destFile.exists() && destFile.length() > 0) {
            onProgress?.invoke(100)
            return@withContext destFile
        }

        val tempFile = File(VideoMediaHelper.getVideoDirectory(context), "$cleanId.temp")

        try {
            Log.d(TAG, "Téléchargement vidéo depuis $videoUrl vers ${destFile.name}...")
            val request = Request.Builder().url(videoUrl).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Échec HTTP téléchargement vidéo: ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val totalBytes = body.contentLength()

                FileOutputStream(tempFile).use { fos ->
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    var bytesReadTotal = 0L
                    var read: Int

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        bytesReadTotal += read
                        if (totalBytes > 0) {
                            val pct = ((bytesReadTotal.toFloat() / totalBytes.toFloat()) * 100f).toInt().coerceIn(0, 100)
                            onProgress?.invoke(pct)
                        }
                    }
                    fos.flush()
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (destFile.exists()) destFile.delete()
                    if (tempFile.renameTo(destFile)) {
                        Log.i(TAG, "✅ Vidéo téléchargée avec succès (${destFile.length()} octets): ${destFile.absolutePath}")
                        // Génère automatiquement la miniature locale
                        VideoMediaHelper.generateThumbnail(context, destFile, cleanId)
                        onProgress?.invoke(100)
                        return@withContext destFile
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur téléchargement vidéo Blossom ($videoUrl): ${e.message}")
            try { tempFile.delete() } catch (_: Exception) {}
        }

        null
    }

    /**
     * RequestBody personnalisé pour mesurer la progression d'envoi réseau en temps réel.
     */
    private class ProgressRequestBody(
        private val file: File,
        private val mediaType: okhttp3.MediaType,
        private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ) : RequestBody() {

        override fun contentType(): okhttp3.MediaType = mediaType

        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            val totalBytes = contentLength()
            var bytesWritten = 0L

            val countingSink = object : ForwardingSink(sink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    onProgress(bytesWritten, totalBytes)
                }
            }

            file.source().use { fileSource ->
                val bufferedSink = countingSink.buffer()
                bufferedSink.writeAll(fileSource)
                bufferedSink.flush()
            }
        }
    }
}
