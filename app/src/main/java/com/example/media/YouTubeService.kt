package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.ImportedVideoMeta
import com.example.data.LocalMediaStorage
import com.example.model.CaptionAnimationPreset
import com.example.model.CaptionClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class YouTubeChannelProfile(
    val channelId: String,
    val title: String,
    val handle: String,
    val avatarUrl: String,
    val subscriberCount: String
)

data class YouTubeVideoItem(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val publishedAt: String = "",
    val description: String = "",
    val directMediaUrl: String? = null
)

data class YouTubeImportedAsset(
    val title: String,
    val channelTitle: String,
    val thumbnailLocalUri: String?,
    val downloadedVideoMeta: ImportedVideoMeta?,
    val importedCaptions: List<CaptionClip>,
    val sourceVideoId: String?
)

enum class YouTubePrivacyStatus(val apiValue: String, val label: String) {
    PUBLIC("public", "Public"),
    UNLISTED("unlisted", "Unlisted"),
    PRIVATE("private", "Private")
}

/**
 * Pure-JVM URL parser and SSRF validator for YouTube links and public direct HTTPS video streams.
 */
object YouTubeUrlParser {

    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    fun extractVideoId(input: String): String? {
        val raw = input.trim()
        if (raw.isEmpty()) return null
        if (VIDEO_ID_REGEX.matches(raw)) return raw
        val normalized = if (!raw.startsWith("http://", true) && !raw.startsWith("https://", true)) {
            "https://$raw"
        } else raw

        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val path = uri.path.orEmpty()

        return when {
            host == "youtu.be" -> {
                path.trim('/').split('/').firstOrNull()?.takeIf { VIDEO_ID_REGEX.matches(it) }
            }
            host == "youtube.com" || host == "music.youtube.com" || host == "youtube-nocookie.com" -> {
                val query = uri.rawQuery.orEmpty()
                val vParam = query.split("&")
                    .mapNotNull { kv ->
                        val idx = kv.indexOf('=')
                        if (idx > 0) kv.substring(0, idx) to kv.substring(idx + 1) else null
                    }
                    .firstOrNull { it.first == "v" }
                    ?.second
                if (vParam != null && VIDEO_ID_REGEX.matches(vParam)) return vParam

                val segments = path.trim('/').split('/')
                if (segments.size >= 2 && segments[0] in setOf("shorts", "embed", "live", "v")) {
                    segments[1].takeIf { VIDEO_ID_REGEX.matches(it) }
                } else null
            }
            else -> null
        }
    }

    /**
     * Validates that a user-supplied URL is a public HTTPS endpoint and does not target
     * localhost, loopback, link-local metadata endpoints, or RFC1918 private networks (SSRF guard).
     */
    fun validatePublicHttpsUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid URL format."))
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Only secure https:// URLs are allowed."))
        }
        val host = uri.host?.lowercase(Locale.US)?.trim().orEmpty()
        if (host.isEmpty()) {
            return Result.failure(IllegalArgumentException("URL is missing a valid hostname."))
        }
        if (isBlockedHost(host)) {
            return Result.failure(IllegalArgumentException("Private or local network URLs are blocked for security."))
        }
        return Result.success(trimmed)
    }

    fun isBlockedHost(host: String): Boolean {
        val h = host.lowercase(Locale.US).trim('[', ']')
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".internal")) return true
        if (h == "::1" || h == "0.0.0.0" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) {
            return true
        }
        // Check dotted-quad IPv4 literals
        val parts = h.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            if (a == 0 || a == 10 || a == 127) return true
            if (a == 169 && b == 254) return true // AWS/GCP metadata link-local
            if (a == 172 && b in 16..31) return true
            if (a == 192 && b == 168) return true
            if (a == 100 && b in 64..127) return true // CGNAT
        }
        return false
    }

    fun looksLikeDirectVideoUrl(url: String): Boolean {
        val path = runCatching { URI(url.trim()).path.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".webm") || path.endsWith(".m4v")
    }
}

/**
 * YouTube Import & Export Studio Service:
 * - Official YouTube Data API v3 sign-in profile, channel uploads, and public video search
 * - Zero-key YouTube oEmbed + public timedtext captions + HD thumbnail import
 * - Safe direct HTTPS public video download (`.mp4`/`.webm`/`.mov`) straight to timeline
 * - Official YouTube Data API v3 Resumable MP4 Upload (with title, description, tags, privacy)
 * - Instant 1-tap share to the official YouTube Android app
 */
object YouTubeService {

    private const val YT_API_BASE = "https://www.googleapis.com/youtube/v3"
    private const val YT_UPLOAD_BASE = "https://www.googleapis.com/upload/youtube/v3/videos"
    private const val MAX_DIRECT_DOWNLOAD_BYTES = 180L * 1024L * 1024L // 180MB safety cap

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun fetchChannelProfile(accessToken: String): Result<YouTubeChannelProfile> =
        withContext(Dispatchers.IO) {
            val token = accessToken.trim()
            if (token.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Enter a YouTube OAuth access token first."))
            }
            try {
                val request = Request.Builder()
                    .url("$YT_API_BASE/channels?part=snippet,statistics&mine=true")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("YouTube sign-in failed (HTTP ${resp.code}). Verify OAuth scope youtube.readonly / youtube.upload.")
                        )
                    }
                    val root = JSONObject(body)
                    val item = root.optJSONArray("items")?.optJSONObject(0)
                        ?: return@withContext Result.failure(IllegalStateException("No YouTube channel found on this Google account."))
                    val snippet = item.optJSONObject("snippet")
                    val stats = item.optJSONObject("statistics")
                    val thumbs = snippet?.optJSONObject("thumbnails")
                    val avatar = thumbs?.optJSONObject("medium")?.optString("url")
                        ?: thumbs?.optJSONObject("default")?.optString("url")
                        ?: ""
                    Result.success(
                        YouTubeChannelProfile(
                            channelId = item.optString("id"),
                            title = snippet?.optString("title").orEmpty().ifBlank { "YouTube Creator" },
                            handle = snippet?.optString("customUrl").orEmpty(),
                            avatarUrl = avatar,
                            subscriberCount = stats?.optString("subscriberCount").orEmpty()
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(IllegalStateException("Could not connect to YouTube: ${e.localizedMessage}"))
            }
        }

    suspend fun fetchMyChannelVideos(accessToken: String, maxResults: Int = 15): Result<List<YouTubeVideoItem>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$YT_API_BASE/search?part=snippet&forMine=true&type=video&order=date&maxResults=${maxResults.coerceIn(1, 25)}"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${accessToken.trim()}")
                    .get()
                    .build()
                parseSearchListResponse(request)
            } catch (e: Exception) {
                Result.failure(IllegalStateException("Failed to load channel videos: ${e.localizedMessage}"))
            }
        }

    suspend fun searchPublicVideos(
        query: String,
        accessToken: String?,
        dataApiKey: String?,
        maxResults: Int = 12
    ): Result<List<YouTubeVideoItem>> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext Result.success(emptyList())
        if (accessToken.isNullOrBlank() && dataApiKey.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Sign in with YouTube OAuth or enter a YouTube Data API key to search videos. You can also paste any public YouTube URL directly!")
            )
        }
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val keyQuery = if (accessToken.isNullOrBlank() && !dataApiKey.isNullOrBlank()) {
                "&key=${URLEncoder.encode(dataApiKey.trim(), "UTF-8")}"
            } else ""
            val reqBuilder = Request.Builder()
                .url("$YT_API_BASE/search?part=snippet&type=video&q=$encoded&maxResults=${maxResults.coerceIn(1, 20)}$keyQuery")
                .get()
            if (!accessToken.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer ${accessToken.trim()}")
            }
            parseSearchListResponse(reqBuilder.build())
        } catch (e: Exception) {
            Result.failure(IllegalStateException("YouTube search failed: ${e.localizedMessage}"))
        }
    }

    private fun parseSearchListResponse(request: Request): Result<List<YouTubeVideoItem>> {
        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(IllegalStateException("YouTube API returned HTTP ${resp.code}."))
            }
            val items = JSONObject(body).optJSONArray("items") ?: return Result.success(emptyList())
            val list = mutableListOf<YouTubeVideoItem>()
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val vidId = obj.optJSONObject("id")?.optString("videoId").orEmpty()
                if (vidId.isEmpty()) continue
                val snippet = obj.optJSONObject("snippet") ?: continue
                val thumbs = snippet.optJSONObject("thumbnails")
                val thumbUrl = thumbs?.optJSONObject("high")?.optString("url")
                    ?: thumbs?.optJSONObject("medium")?.optString("url")
                    ?: "https://i.ytimg.com/vi/$vidId/hqdefault.jpg"
                list.add(
                    YouTubeVideoItem(
                        videoId = vidId,
                        title = snippet.optString("title", "YouTube Video"),
                        channelTitle = snippet.optString("channelTitle", "YouTube"),
                        thumbnailUrl = thumbUrl,
                        publishedAt = snippet.optString("publishedAt", "").take(10),
                        description = snippet.optString("description", "")
                    )
                )
            }
            return Result.success(list)
        }
    }

    /**
     * Resolves a public YouTube URL (zero-key via official oEmbed + HD thumbnail + public captions)
     * OR downloads a public direct HTTPS video file (.mp4/.webm/.mov) straight into LocalMediaStorage.
     */
    suspend fun importPublicUrlToProject(
        context: Context,
        mediaStorage: LocalMediaStorage,
        rawUrlOrId: String,
        onProgress: (String) -> Unit
    ): Result<YouTubeImportedAsset> = withContext(Dispatchers.IO) {
        val trimmed = rawUrlOrId.trim()
        val ytVideoId = YouTubeUrlParser.extractVideoId(trimmed)

        if (ytVideoId != null) {
            onProgress("Fetching YouTube metadata & HD cover…")
            val watchUrl = "https://www.youtube.com/watch?v=$ytVideoId"
            val oembedUrl = "https://www.youtube.com/oembed?url=${URLEncoder.encode(watchUrl, "UTF-8")}&format=json"

            var title = "YouTube Clip ($ytVideoId)"
            var channel = "YouTube"
            var thumbUrl = "https://i.ytimg.com/vi/$ytVideoId/hqdefault.jpg"

            runCatching {
                val req = Request.Builder().url(oembedUrl).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        title = json.optString("title", title).ifBlank { title }
                        channel = json.optString("author_name", channel).ifBlank { channel }
                        thumbUrl = json.optString("thumbnail_url", thumbUrl).ifBlank { thumbUrl }
                    }
                }
            }

            // Download HD thumbnail into local storage so it can be placed onto timeline immediately
            val thumbLocalUri = downloadImageIntoStorage(context, mediaStorage, thumbUrl, "yt_${ytVideoId}_cover")

            // Attempt to pull public CC/timedtext captions for instant timeline subtitles
            onProgress("Checking public captions…")
            val captions = fetchPublicTimedTextCaptions(ytVideoId)

            return@withContext Result.success(
                YouTubeImportedAsset(
                    title = title,
                    channelTitle = channel,
                    thumbnailLocalUri = thumbLocalUri,
                    downloadedVideoMeta = null,
                    importedCaptions = captions,
                    sourceVideoId = ytVideoId
                )
            )
        }

        // Otherwise validate as a public direct HTTPS video stream (.mp4 / .webm / .mov)
        val validUrl = YouTubeUrlParser.validatePublicHttpsUrl(trimmed)
            .getOrElse { return@withContext Result.failure(it) }

        // Resolve DNS and verify resolved IP is not private/loopback (defense-in-depth SSRF protection)
        val host = runCatching { URI(validUrl).host.orEmpty() }.getOrDefault("")
        val resolvedBlocked = runCatching {
            InetAddress.getAllByName(host).any { addr ->
                addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress ||
                    addr.isSiteLocalAddress || YouTubeUrlParser.isBlockedHost(addr.hostAddress.orEmpty())
            }
        }.getOrDefault(true)
        if (resolvedBlocked) {
            return@withContext Result.failure(
                IllegalArgumentException("Resolved address belongs to a private network and is blocked.")
            )
        }

        onProgress("Downloading public video stream…")
        val tempFile = File(context.cacheDir, "yt_stream_${System.currentTimeMillis()}.mp4")
        try {
            val req = Request.Builder().url(validUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Video download failed (HTTP ${resp.code}).")
                    )
                }
                val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.US)
                if (contentType.isNotEmpty() && !contentType.startsWith("video/") && !contentType.contains("octet-stream")) {
                    return@withContext Result.failure(
                        IllegalArgumentException("URL did not return a direct video stream ($contentType). Paste a YouTube URL or direct .mp4 HTTPS link.")
                    )
                }
                val body = resp.body ?: return@withContext Result.failure(IllegalStateException("Empty video response."))
                val totalLen = body.contentLength()
                if (totalLen > MAX_DIRECT_DOWNLOAD_BYTES) {
                    return@withContext Result.failure(IllegalArgumentException("Video exceeds 180MB direct download limit."))
                }
                var downloaded = 0L
                body.byteStream().use { input ->
                    tempFile.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read < 0) break
                            downloaded += read
                            if (downloaded > MAX_DIRECT_DOWNLOAD_BYTES) {
                                return@withContext Result.failure(IllegalArgumentException("Video exceeds 180MB limit."))
                            }
                            out.write(buf, 0, read)
                            if (totalLen > 0) {
                                val pct = ((downloaded * 100L) / totalLen).toInt().coerceIn(1, 99)
                                onProgress("Downloading public video… $pct%")
                            }
                        }
                    }
                }
            }

            onProgress("Importing downloaded video into timeline…")
            val importedMeta = mediaStorage.importVideoFromUri(Uri.fromFile(tempFile))
                .getOrElse { return@withContext Result.failure(it) }

            val cleanTitle = runCatching {
                URI(validUrl).path.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Web Video" }
            }.getOrDefault("Web Video")

            Result.success(
                YouTubeImportedAsset(
                    title = cleanTitle,
                    channelTitle = host,
                    thumbnailLocalUri = importedMeta.thumbnailPath?.let { Uri.fromFile(File(it)).toString() },
                    downloadedVideoMeta = importedMeta,
                    importedCaptions = emptyList(),
                    sourceVideoId = null
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not download video: ${e.localizedMessage}"))
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private suspend fun downloadImageIntoStorage(
        context: Context,
        mediaStorage: LocalMediaStorage,
        imageUrl: String,
        namePrefix: String
    ): String? {
        if (YouTubeUrlParser.validatePublicHttpsUrl(imageUrl).isFailure) return null
        val tmp = File(context.cacheDir, "${namePrefix}_${System.currentTimeMillis()}.jpg")
        return try {
            val req = Request.Builder().url(imageUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.size > 15 * 1024 * 1024) return null
                tmp.writeBytes(bytes)
            }
            mediaStorage.importImageFromUri(Uri.fromFile(tmp)).getOrNull()?.uriString
        } catch (_: Exception) {
            null
        } finally {
            runCatching { tmp.delete() }
        }
    }

    private fun fetchPublicTimedTextCaptions(videoId: String): List<CaptionClip> = runCatching {
        val url = "https://video.google.com/timedtext?lang=en&v=$videoId&fmt=json3"
        val req = Request.Builder().url(url).get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val text = resp.body?.string().orEmpty()
            if (!text.startsWith("{")) return emptyList()
            val events = JSONObject(text).optJSONArray("events") ?: return emptyList()
            val clips = mutableListOf<CaptionClip>()
            for (i in 0 until events.length()) {
                val ev = events.optJSONObject(i) ?: continue
                val startMs = ev.optLong("tStartMs", -1L)
                val durMs = ev.optLong("dDurationMs", 2000L).coerceAtLeast(600L)
                if (startMs < 0L) continue
                val segs = ev.optJSONArray("segs") ?: continue
                val phrase = buildString {
                    for (j in 0 until segs.length()) {
                        append(segs.optJSONObject(j)?.optString("utf8").orEmpty())
                    }
                }.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                if (phrase.isNotEmpty()) {
                    clips.add(
                        CaptionClip(
                            text = phrase,
                            startTimeMs = startMs,
                            endTimeMs = startMs + durMs,
                            animationPreset = CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT
                        )
                    )
                }
            }
            clips
        }
    }.getOrDefault(emptyList())

    /**
     * Uploads a rendered MP4 directly to the user's YouTube channel via the official
     * YouTube Data API v3 Resumable Upload protocol.
     */
    suspend fun uploadVideoToYouTube(
        context: Context,
        videoUriString: String,
        title: String,
        description: String,
        tagsCsv: String,
        privacy: YouTubePrivacyStatus,
        accessToken: String,
        onProgress: (Float, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = accessToken.trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Sign in with YouTube OAuth first."))
        }
        try {
            onProgress(0.08f, "Reading rendered MP4…")
            val uri = Uri.parse(videoUriString)
            val videoBytes = if (uri.scheme == "file" && uri.path != null) {
                File(uri.path!!).readBytes()
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(IllegalStateException("Could not open rendered MP4 file."))
            }
            if (videoBytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Rendered video file is empty."))
            }

            onProgress(0.22f, "Creating YouTube upload session…")
            val tagsArray = JSONArray()
            tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tagsArray.put(it) }
            val metadataJson = JSONObject()
                .put(
                    "snippet",
                    JSONObject()
                        .put("title", title.trim().ifBlank { "LumaCut Edit" }.take(100))
                        .put("description", description.trim())
                        .put("tags", tagsArray)
                        .put("categoryId", "22")
                )
                .put(
                    "status",
                    JSONObject()
                        .put("privacyStatus", privacy.apiValue)
                        .put("selfDeclaredMadeForKids", false)
                )

            val initRequest = Request.Builder()
                .url("$YT_UPLOAD_BASE?uploadType=resumable&part=snippet,status")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("X-Upload-Content-Length", videoBytes.size.toString())
                .addHeader("X-Upload-Content-Type", "video/mp4")
                .post(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val uploadSessionUrl = httpClient.newCall(initRequest).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("YouTube upload init failed (HTTP ${resp.code}). Ensure OAuth token has youtube.upload scope.")
                    )
                }
                resp.header("Location")
                    ?: return@withContext Result.failure(IllegalStateException("YouTube did not return an upload session URL."))
            }

            onProgress(0.55f, "Uploading MP4 (${videoBytes.size / (1024 * 1024).coerceAtLeast(1)} MB) to YouTube…")
            val putRequest = Request.Builder()
                .url(uploadSessionUrl)
                .addHeader("Authorization", "Bearer $token")
                .put(videoBytes.toRequestBody("video/mp4".toMediaType()))
                .build()

            httpClient.newCall(putRequest).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("YouTube video upload failed (HTTP ${resp.code}).")
                    )
                }
                val uploadedId = runCatching { JSONObject(respBody).optString("id") }.getOrDefault("")
                onProgress(1f, "Uploaded to YouTube!")
                val liveUrl = if (uploadedId.isNotEmpty()) "https://youtu.be/$uploadedId" else "https://studio.youtube.com"
                Result.success(liveUrl)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("YouTube upload failed: ${e.localizedMessage}"))
        }
    }

    /**
     * Launches the official YouTube Android app directly with the rendered MP4 ready to publish,
     * gracefully falling back to Android's system share sheet if YouTube isn't installed.
     */
    fun launchYouTubeAppUpload(context: Context, videoUriString: String, title: String) {
        val uri = Uri.parse(videoUriString)
        val targeted = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, title)
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(targeted)
            true
        }.getOrDefault(false)
        if (!launched) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(fallback, "Upload to YouTube").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
