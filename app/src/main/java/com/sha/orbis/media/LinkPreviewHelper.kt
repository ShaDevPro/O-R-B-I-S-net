package com.sha.orbis.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import com.sha.orbis.cache.MediaMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class LinkContentType {
    VIDEO,
    REEL_OR_POST,
    POST,
    GENERIC
}

enum class LinkPlatform(
    val displayName: String,
    val hostKeywords: List<String>,
    val appPackages: List<String>,
    val brandColorHex: Long,
    val contentType: LinkContentType
) {
    TIKTOK(
        displayName = "TikTok",
        hostKeywords = listOf("tiktok.com", "vm.tiktok.com"),
        appPackages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        brandColorHex = 0xFF000000,
        contentType = LinkContentType.VIDEO
    ),
    INSTAGRAM(
        displayName = "Instagram",
        hostKeywords = listOf("instagram.com", "instagr.am"),
        appPackages = listOf("com.instagram.android"),
        brandColorHex = 0xFFE1306C,
        contentType = LinkContentType.REEL_OR_POST
    ),
    FACEBOOK(
        displayName = "Facebook",
        hostKeywords = listOf("facebook.com", "fb.watch", "fb.com", "m.facebook.com"),
        appPackages = listOf("com.facebook.katana", "com.facebook.lite"),
        brandColorHex = 0xFF1877F2,
        contentType = LinkContentType.POST
    ),
    YOUTUBE(
        displayName = "YouTube",
        hostKeywords = listOf("youtube.com", "youtu.be"),
        appPackages = listOf("com.google.android.youtube"),
        brandColorHex = 0xFFFF0000,
        contentType = LinkContentType.VIDEO
    ),
    TWITTER(
        displayName = "X (Twitter)",
        hostKeywords = listOf("twitter.com", "x.com"),
        appPackages = listOf("com.twitter.android"),
        brandColorHex = 0xFF0F1419,
        contentType = LinkContentType.POST
    ),
    GENERIC(
        displayName = "Web",
        hostKeywords = emptyList(),
        appPackages = emptyList(),
        brandColorHex = 0xFF2563EB,
        contentType = LinkContentType.GENERIC
    );

    companion object {
        fun fromUrl(url: String): LinkPlatform {
            val lower = url.lowercase()
            return entries.firstOrNull { platform ->
                platform.hostKeywords.any { kw -> lower.contains(kw) }
            } ?: GENERIC
        }
    }
}

data class LinkPreviewMetadata(
    val url: String,
    val platform: LinkPlatform,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val localThumbnailPath: String? = null,
    val authorName: String? = null,
    val siteName: String = platform.displayName,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("url", url)
            put("platform", platform.name)
            put("title", title)
            put("description", description ?: "")
            put("thumbnailUrl", thumbnailUrl ?: "")
            put("localThumbnailPath", localThumbnailPath ?: "")
            put("authorName", authorName ?: "")
            put("siteName", siteName)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): LinkPreviewMetadata? {
            return try {
                val url = json.getString("url")
                val platformName = json.optString("platform", LinkPlatform.GENERIC.name)
                val platform = try { LinkPlatform.valueOf(platformName) } catch (_: Exception) { LinkPlatform.GENERIC }
                LinkPreviewMetadata(
                    url = url,
                    platform = platform,
                    title = json.optString("title", ""),
                    description = json.optString("description", "").takeIf { it.isNotBlank() },
                    thumbnailUrl = json.optString("thumbnailUrl", "").takeIf { it.isNotBlank() },
                    localThumbnailPath = json.optString("localThumbnailPath", "").takeIf { it.isNotBlank() },
                    authorName = json.optString("authorName", "").takeIf { it.isNotBlank() },
                    siteName = json.optString("siteName", platform.displayName),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

object LinkPreviewHelper {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val memoryCache = ConcurrentHashMap<String, LinkPreviewMetadata>()
    private var isDiskCacheLoaded = false
    private val diskCacheLock = Any()

    /**
     * Extrait la première URL d'un texte.
     */
    fun extractFirstUrl(text: String): String? {
        val regex = Regex("""(https?://[^\s<>"'()]+|www\.[^\s<>"'()]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(text)?.value ?: return null
        return if (!match.startsWith("http://", ignoreCase = true) && !match.startsWith("https://", ignoreCase = true)) {
            "https://$match"
        } else {
            match
        }
    }

    /**
     * Ouvre l'URL directement dans l'application source (TikTok, Instagram, Facebook...)
     * avec repli fluide vers le navigateur par défaut.
     */
    fun openInSourceApp(context: Context, url: String) {
        val cleanUrl = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            "https://$url"
        } else {
            url
        }
        val uri = Uri.parse(cleanUrl)
        val platform = LinkPlatform.fromUrl(cleanUrl)
        val pm = context.packageManager

        var launched = false
        for (pkg in platform.appPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {}
        }

        if (!launched) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Impossible d'ouvrir le lien : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Vérifie si l'application officielle associée à la plateforme est installée sur l'appareil.
     */
    fun isSourceAppInstalled(context: Context, platform: LinkPlatform): Boolean {
        if (platform.appPackages.isEmpty()) return false
        val pm = context.packageManager
        return platform.appPackages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Récupère de façon synchrone depuis le cache ou asynchrone sur le réseau
     * les métadonnées et la miniature du lien.
     */
    suspend fun getPreview(context: Context, url: String): LinkPreviewMetadata? {
        val cleanUrl = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            "https://$url"
        } else {
            url
        }

        ensureDiskCacheLoaded(context)

        // 1. Vérification dans le cache mémoire
        memoryCache[cleanUrl]?.let { return it }

        // 2. Récupération asynchrone sur réseau
        return withContext(Dispatchers.IO) {
            try {
                val metadata = fetchFromNetwork(context, cleanUrl)
                if (metadata != null) {
                    memoryCache[cleanUrl] = metadata
                    saveToDiskCache(context, metadata)
                }
                metadata
            } catch (t: Throwable) {
                android.util.Log.w("LinkPreviewHelper", "Failed to fetch preview for $cleanUrl: ${t.message}")
                // Création d'un aperçu minimal par défaut pour ne pas laisser vide
                val platform = LinkPlatform.fromUrl(cleanUrl)
                val fallback = LinkPreviewMetadata(
                    url = cleanUrl,
                    platform = platform,
                    title = "${platform.displayName} Post",
                    siteName = platform.displayName
                )
                memoryCache[cleanUrl] = fallback
                fallback
            }
        }
    }

    private fun fetchFromNetwork(context: Context, url: String): LinkPreviewMetadata? {
        val platform = LinkPlatform.fromUrl(url)

        return when (platform) {
            LinkPlatform.TIKTOK -> fetchTikTokOEmbed(context, url)
            LinkPlatform.YOUTUBE -> fetchYouTubeOEmbed(context, url)
            else -> fetchOpenGraph(context, url, platform)
        }
    }

    /**
     * Récupération oEmbed officielle pour TikTok sans clé API.
     */
    private fun fetchTikTokOEmbed(context: Context, url: String): LinkPreviewMetadata? {
        val oEmbedUrl = "https://www.tiktok.com/oembed?url=${URLEncoder.encode(url, "UTF-8")}"
        val request = Request.Builder()
            .url(oEmbedUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) Chrome/120.0.0.0 Mobile")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return fetchOpenGraph(context, url, LinkPlatform.TIKTOK)
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)

                val title = json.optString("title", "Vidéo TikTok").ifBlank { "Vidéo TikTok" }
                val author = json.optString("author_name", "").takeIf { it.isNotBlank() }
                val thumbUrl = json.optString("thumbnail_url", "").takeIf { it.isNotBlank() }

                val localThumb = if (!thumbUrl.isNullOrBlank()) {
                    downloadThumbnail(context, thumbUrl, url)
                } else null

                LinkPreviewMetadata(
                    url = url,
                    platform = LinkPlatform.TIKTOK,
                    title = title,
                    description = if (author != null) "@$author sur TikTok" else null,
                    thumbnailUrl = thumbUrl,
                    localThumbnailPath = localThumb,
                    authorName = author,
                    siteName = "TikTok"
                )
            }
        } catch (_: Exception) {
            fetchOpenGraph(context, url, LinkPlatform.TIKTOK)
        }
    }

    /**
     * Récupération oEmbed officielle pour YouTube.
     */
    private fun fetchYouTubeOEmbed(context: Context, url: String): LinkPreviewMetadata? {
        val oEmbedUrl = "https://www.youtube.com/oembed?url=${URLEncoder.encode(url, "UTF-8")}&format=json"
        val request = Request.Builder().url(oEmbedUrl).build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return fetchOpenGraph(context, url, LinkPlatform.YOUTUBE)
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)

                val title = json.optString("title", "Vidéo YouTube")
                val author = json.optString("author_name", "").takeIf { it.isNotBlank() }
                val thumbUrl = json.optString("thumbnail_url", "").takeIf { it.isNotBlank() }

                val localThumb = if (!thumbUrl.isNullOrBlank()) {
                    downloadThumbnail(context, thumbUrl, url)
                } else null

                LinkPreviewMetadata(
                    url = url,
                    platform = LinkPlatform.YOUTUBE,
                    title = title,
                    description = author,
                    thumbnailUrl = thumbUrl,
                    localThumbnailPath = localThumb,
                    authorName = author,
                    siteName = "YouTube"
                )
            }
        } catch (_: Exception) {
            fetchOpenGraph(context, url, LinkPlatform.YOUTUBE)
        }
    }

    /**
     * Analyse générique OpenGraph (Instagram, Facebook, Twitter, Web).
     */
    private fun fetchOpenGraph(context: Context, url: String, platform: LinkPlatform): LinkPreviewMetadata? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: ""

                // Extract Open Graph & Meta tags
                val ogTitle = extractMetaProperty(html, "og:title")
                    ?: extractMetaName(html, "twitter:title")
                    ?: extractTagContent(html, "title")
                    ?: "${platform.displayName} Post"

                val ogDesc = extractMetaProperty(html, "og:description")
                    ?: extractMetaName(html, "twitter:description")
                    ?: extractMetaName(html, "description")

                val rawOgImage = extractMetaProperty(html, "og:image")
                    ?: extractMetaName(html, "twitter:image")

                val cleanOgImage = rawOgImage?.let { raw ->
                    var img = cleanHtmlEntities(raw).replace("&amp;", "&").trim()
                    if (img.startsWith("//")) {
                        img = "https:$img"
                    } else if (img.startsWith("/") && url.isNotBlank()) {
                        try {
                            val baseUri = Uri.parse(url)
                            img = "${baseUri.scheme}://${baseUri.host}$img"
                        } catch (_: Exception) {}
                    }
                    img
                }

                val ogSiteName = extractMetaProperty(html, "og:site_name")
                    ?: platform.displayName

                val localThumb = if (!cleanOgImage.isNullOrBlank()) {
                    downloadThumbnail(context, cleanOgImage, url)
                } else null

                LinkPreviewMetadata(
                    url = url,
                    platform = platform,
                    title = cleanHtmlEntities(ogTitle),
                    description = ogDesc?.let { cleanHtmlEntities(it) },
                    thumbnailUrl = cleanOgImage,
                    localThumbnailPath = localThumb,
                    siteName = ogSiteName
                )
            }
        } catch (_: Exception) {
            LinkPreviewMetadata(
                url = url,
                platform = platform,
                title = "${platform.displayName} Post",
                siteName = platform.displayName
            )
        }
    }

    private fun extractMetaProperty(html: String, prop: String): String? {
        val regex = Regex("""<meta[^>]*property=["']$prop["'][^>]*content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val match = regex.find(html)?.groupValues?.getOrNull(1)
        if (!match.isNullOrBlank()) return match

        // Inversé: content="..." property="..."
        val regexReverse = Regex("""<meta[^>]*content=["']([^"']*)["'][^>]*property=["']$prop["']""", RegexOption.IGNORE_CASE)
        return regexReverse.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun extractMetaName(html: String, name: String): String? {
        val regex = Regex("""<meta[^>]*name=["']$name["'][^>]*content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val match = regex.find(html)?.groupValues?.getOrNull(1)
        if (!match.isNullOrBlank()) return match

        val regexReverse = Regex("""<meta[^>]*content=["']([^"']*)["'][^>]*name=["']$name["']""", RegexOption.IGNORE_CASE)
        return regexReverse.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun extractTagContent(html: String, tag: String): String? {
        val regex = Regex("""<$tag[^>]*>(.*?)</$tag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun cleanHtmlEntities(text: String): String {
        if (text.isBlank()) return text
        return try {
            val decoded = androidx.core.text.HtmlCompat.fromHtml(
                text,
                androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
            ).toString()
            decodeNumericEntities(decoded)
        } catch (_: Throwable) {
            decodeNumericEntities(text)
        }.trim()
    }

    private fun decodeNumericEntities(input: String): String {
        val hexRegex = Regex("""&#x([0-9a-fA-F]+);""")
        val decRegex = Regex("""&#([0-9]+);""")
        var res = hexRegex.replace(input) { match ->
            try {
                val code = match.groupValues[1].toInt(16)
                Character.toChars(code).concatToString()
            } catch (_: Exception) { match.value }
        }
        res = decRegex.replace(res) { match ->
            try {
                val code = match.groupValues[1].toInt(10)
                Character.toChars(code).concatToString()
            } catch (_: Exception) { match.value }
        }
        return res
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    /**
     * Télécharge la miniature et l'enregistre sur le disque local dans `cache/link_thumbnails/`.
     */
    private fun downloadThumbnail(context: Context, imageUrl: String, originalUrl: String): String? {
        return try {
            val cleanUrl = cleanHtmlEntities(imageUrl).replace("&amp;", "&").trim()
            val md5 = md5(originalUrl)
            val dir = File(context.cacheDir, "link_thumbnails").apply { if (!exists()) mkdirs() }
            val destFile = File(dir, "thumb_$md5.jpg")

            if (destFile.exists() && destFile.length() > 0) {
                return destFile.absolutePath
            }

            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7,ar;q=0.6")
                .header("Referer", originalUrl)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Fallback avec User-Agent de crawler officiel en cas de refus CDN
                    val fbFallback = Request.Builder()
                        .url(cleanUrl)
                        .header("User-Agent", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
                        .header("Accept", "*/*")
                        .build()
                    val resp2 = httpClient.newCall(fbFallback).execute()
                    if (!resp2.isSuccessful) {
                        resp2.close()
                        return null
                    }
                    val bytes2 = resp2.body?.bytes() ?: return null
                    resp2.close()
                    saveThumbnailBitmap(bytes2, destFile)
                } else {
                    val bytes = response.body?.bytes() ?: return null
                    saveThumbnailBitmap(bytes, destFile)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("LinkPreviewHelper", "Failed to download thumbnail: ${t.message}")
            null
        }
    }

    private fun saveThumbnailBitmap(bytes: ByteArray, destFile: File): String? {
        return try {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            FileOutputStream(destFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            MediaMemoryCache.put(destFile.absolutePath, bmp)
            destFile.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // =========================================================================
    // Persistance Disk Cache
    // =========================================================================

    private fun ensureDiskCacheLoaded(context: Context) {
        if (isDiskCacheLoaded) return
        synchronized(diskCacheLock) {
            if (isDiskCacheLoaded) return
            try {
                val cacheFile = File(context.filesDir, "orbis_link_previews.json")
                if (cacheFile.exists()) {
                    val raw = cacheFile.readText()
                    if (raw.isNotBlank()) {
                        val json = JSONObject(raw)
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val itemObj = json.optJSONObject(key)
                            if (itemObj != null) {
                                val item = LinkPreviewMetadata.fromJson(itemObj)
                                if (item != null) {
                                    memoryCache[key] = item
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            isDiskCacheLoaded = true
        }
    }

    private fun saveToDiskCache(context: Context, metadata: LinkPreviewMetadata) {
        synchronized(diskCacheLock) {
            try {
                val cacheFile = File(context.filesDir, "orbis_link_previews.json")
                val rootJson = if (cacheFile.exists() && cacheFile.length() > 0) {
                    try { JSONObject(cacheFile.readText()) } catch (_: Exception) { JSONObject() }
                } else {
                    JSONObject()
                }

                rootJson.put(metadata.url, metadata.toJson())

                // Limiter la taille du cache à 200 entrées pour la légèreté
                if (rootJson.length() > 200) {
                    val firstKey = rootJson.keys().next()
                    rootJson.remove(firstKey)
                }

                cacheFile.writeText(rootJson.toString())
            } catch (_: Exception) {}
        }
    }
}
