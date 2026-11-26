package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.media.AudioWaveformExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ImportedVideoMeta(
    val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val thumbnailPath: String?
)

data class ImportedImageMeta(
    val uriString: String,
    val displayName: String,
    val thumbnailPath: String?
)

data class ImportedAudioMeta(
    val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val waveformPeaks: List<Float>,
    val beatPositionsMs: List<Long>
)

data class ImportedFontMeta(
    val id: String,
    val displayName: String,
    val filePath: String
)

class LocalMediaStorage(private val context: Context) {

    private val fontsDir: File
        get() = File(context.filesDir, "custom_fonts").apply { mkdirs() }

    private val thumbsDir: File
        get() = File(context.filesDir, "thumbs").apply { mkdirs() }

    private val importedImagesDir: File
        get() = File(context.filesDir, "imported_images").apply { mkdirs() }

    private val importedMediaDir: File
        get() = File(context.filesDir, "imported_media").apply { mkdirs() }

    suspend fun importFontFromUri(uri: Uri): Result<ImportedFontMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "Custom Font"
            val cleanBase = rawName
                .substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifBlank { "Custom Font" }
            val ext = rawName.substringAfterLast('.', "ttf").lowercase()
            val safeExt = if (ext == "otf") "otf" else "ttf"
            val id = "custom_${UUID.randomUUID().toString().take(8)}"
            val dest = File(fontsDir, "$id.$safeExt")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error("Unable to read selected font file.")

            // Validate the font file can actually be parsed by Android Typeface
            val parsed = runCatching { Typeface.createFromFile(dest) }.getOrNull()
            if (parsed == null || dest.length() < 128L) {
                dest.delete()
                error("Please pick a valid .ttf or .otf font file.")
            }

            ImportedFontMeta(
                id = id,
                displayName = cleanBase.take(28),
                filePath = dest.absolutePath
            )
        }
    }

    private val lutsDir: File
        get() = File(context.filesDir, "luts").apply { mkdirs() }

    private val voiceoverDir: File
        get() = File(context.filesDir, "voiceovers").apply { mkdirs() }

    private val cameraCapturesDir: File
        get() = File(context.filesDir, "camera_captures").apply { mkdirs() }

    fun createProCameraOutputFile(): File =
        File(cameraCapturesDir, "procam_${UUID.randomUUID().toString().take(8)}.mp4")

    fun createVoiceoverOutputFile(): File =
        File(voiceoverDir, "vo_${UUID.randomUUID().toString().take(8)}.m4a")

    suspend fun importCubeLutFromUri(uri: Uri): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "CustomLUT.cube"
            val cleanName = rawName.substringBeforeLast('.').ifBlank { "Custom 3D LUT" }.take(28)
            val dest = File(lutsDir, "lut_${UUID.randomUUID().toString().take(8)}.cube")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error("Unable to read selected .cube LUT file.")

            val parsed = com.example.model.CubeLutParser.parse(dest.readText(), fallbackTitle = cleanName)
            dest.absolutePath to parsed.title.take(28)
        }
    }

    suspend fun importImageFromUri(uri: Uri): Result<ImportedImageMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "Photo_${System.currentTimeMillis() % 10000}"
            val cleanName = rawName.substringBeforeLast('.').ifBlank { "Photo" }
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
            val rawExt = rawName.substringAfterLast('.', "").lowercase()
            val ext = when {
                rawExt == "png" || mime.contains("png") -> "png"
                rawExt == "webp" || mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val dest = File(importedImagesDir, "img_${UUID.randomUUID().toString().take(10)}.$ext")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error("Unable to read selected photo.")

            val localUri = Uri.fromFile(dest).toString()
            ImportedImageMeta(
                uriString = localUri,
                displayName = cleanName.take(28),
                thumbnailPath = dest.absolutePath
            )
        }
    }

    suspend fun importVideoFromUri(uri: Uri): Result<ImportedVideoMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "Clip_${System.currentTimeMillis() % 10000}"
            val cleanName = rawName.substringBeforeLast('.').ifBlank { "Video Clip" }

            // Try persistable read permission; if Photo Picker denies persistability, copy locally
            val resolvedUriString = tryPersistOrCopyMedia(uri, "vid", "mp4")

            var durationMs = 5000L
            var thumbPath: String? = null
            val retriever = MediaMetadataRetriever()
            try {
                val parsedUri = Uri.parse(resolvedUriString)
                if (parsedUri.scheme == "file" && parsedUri.path != null) {
                    retriever.setDataSource(parsedUri.path)
                } else {
                    retriever.setDataSource(context, parsedUri)
                }
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull()?.coerceAtLeast(500L) ?: 5000L

                val frame = retriever.getFrameAtTime(
                    (durationMs * 1000L / 4L).coerceAtMost(2_000_000L),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val thumbFile = File(thumbsDir, "th_${UUID.randomUUID().toString().take(10)}.jpg")
                    FileOutputStream(thumbFile).use { out ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 78, out)
                    }
                    frame.recycle()
                    thumbPath = thumbFile.absolutePath
                }
            } catch (_: Exception) {
                // Keep fallback duration if metadata fails
            } finally {
                runCatching { retriever.release() }
            }

            ImportedVideoMeta(
                uriString = resolvedUriString,
                displayName = cleanName.take(28),
                durationMs = durationMs,
                thumbnailPath = thumbPath
            )
        }
    }

    suspend fun importAudioFromUri(uri: Uri): Result<ImportedAudioMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "Audio_${System.currentTimeMillis() % 10000}"
            val cleanName = rawName.substringBeforeLast('.').ifBlank { "Audio Track" }
            val ext = rawName.substringAfterLast('.', "m4a").lowercase().take(4)

            val resolvedUriString = tryPersistOrCopyMedia(uri, "aud", ext)

            var durationMs = 15_000L
            val retriever = MediaMetadataRetriever()
            try {
                val parsedUri = Uri.parse(resolvedUriString)
                if (parsedUri.scheme == "file" && parsedUri.path != null) {
                    retriever.setDataSource(parsedUri.path)
                } else {
                    retriever.setDataSource(context, parsedUri)
                }
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull()?.coerceAtLeast(500L) ?: 15_000L
            } catch (_: Exception) {
                // Fallback duration
            } finally {
                runCatching { retriever.release() }
            }

            val decodedWaveform = AudioWaveformExtractor.extract(
                context = context,
                uri = Uri.parse(resolvedUriString),
                durationMs = durationMs,
                bucketCount = 140
            )
            ImportedAudioMeta(
                uriString = resolvedUriString,
                displayName = cleanName.take(28),
                durationMs = durationMs,
                waveformPeaks = decodedWaveform.peaks,
                beatPositionsMs = decodedWaveform.beatPositionsMs
            )
        }
    }

    fun deleteLocalFileIfInternal(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                file.delete()
            }
        }
    }

    private fun tryPersistOrCopyMedia(uri: Uri, prefix: String, fallbackExt: String): String {
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        }.getOrDefault(false)

        if (persisted) return uri.toString()

        // Copy to app-internal storage so offline projects remain playable across restarts
        return runCatching {
            val dest = File(importedMediaDir, "${prefix}_${UUID.randomUUID().toString().take(10)}.$fallbackExt")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            if (dest.exists() && dest.length() > 0L) Uri.fromFile(dest).toString() else uri.toString()
        }.getOrDefault(uri.toString())
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
