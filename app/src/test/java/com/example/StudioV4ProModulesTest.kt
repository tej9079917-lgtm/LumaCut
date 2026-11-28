package com.example

import com.example.ai.CaptionSegmentNormalizer
import com.example.ai.RawCaptionSegment
import com.example.data.RenderJobStatus
import com.example.media.AudioClipExtractor
import com.example.media.OfflineLanguageCatalog
import com.example.media.YouTubeUrlParser
import com.example.security.AiCaptionProvider
import com.example.security.SecureCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StudioV4ProModulesTest {

    @Test
    fun youtubeUrlParser_extractsVideoIdsFromAllFormats() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=12s"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ?feature=share"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("dQw4w9WgXcQ"))
        assertNull(YouTubeUrlParser.extractVideoId("https://example.com/video.mp4"))
    }

    @Test
    fun youtubeUrlParser_blocksSsrfPrivateAndNonHttpsUrls() {
        assertTrue(YouTubeUrlParser.validatePublicHttpsUrl("https://cdn.example.com/clip.mp4").isSuccess)
        assertTrue(YouTubeUrlParser.validatePublicHttpsUrl("http://cdn.example.com/clip.mp4").isFailure)
        assertTrue(YouTubeUrlParser.validatePublicHttpsUrl("https://localhost/video.mp4").isFailure)
        assertTrue(YouTubeUrlParser.validatePublicHttpsUrl("https://127.0.0.1/video.mp4").isFailure)
        assertTrue(YouTubeUrlParser.validatePublicHttpsUrl("https://169.254.169.254/latest/meta-data").isFailure)
        assertTrue(YouTubeUrlParser.validatePublicHttpsUrl("https://10.0.0.5/internal.mp4").isFailure)
        assertTrue(YouTubeUrlParser.validatePublicHttpsUrl("https://192.168.1.12/cam.mp4").isFailure)
        assertTrue(YouTubeUrlParser.validatePublicHttpsUrl("https://[::1]/local.mp4").isFailure)
    }

    @Test
    fun captionSegmentNormalizer_splitsLongLinesAndResolvesOverlaps() {
        val raw = listOf(
            RawCaptionSegment(startMs = 2000L, endMs = 4500L, text = "Second phrase arrives out of order"),
            RawCaptionSegment(
                startMs = 0L,
                endMs = 2400L,
                text = "This is a very long spoken sentence that should split into two clean subtitle lines"
            )
        )
        val normalized = CaptionSegmentNormalizer.normalize(
            rawSegments = raw,
            timelineOffsetMs = 1000L,
            clipDurationMs = 6000L,
            maxWordsPerLine = 5
        )
        assertTrue(normalized.size >= 3)
        for (i in 1 until normalized.size) {
            assertTrue(
                "Segments must be strictly non-overlapping",
                normalized[i].startTimeMs >= normalized[i - 1].endTimeMs
            )
        }
        assertEquals(1000L, normalized.first().startTimeMs)
    }

    @Test
    fun captionSegmentNormalizer_stripsMarkdownJsonFences() {
        val wrapped = "```json\n{\"segments\": []}\n```"
        assertEquals("{\"segments\": []}", CaptionSegmentNormalizer.stripJsonMarkdownFences(wrapped))
    }

    @Test
    fun offlineLanguageCatalog_mapsIndianAndGlobalLanguages() {
        assertTrue(OfflineLanguageCatalog.languages.size >= 18)
        assertEquals("te", OfflineLanguageCatalog.isoCodeForName("Telugu"))
        assertEquals("te-IN", OfflineLanguageCatalog.bcp47ForName("Telugu"))
        assertEquals("hi", OfflineLanguageCatalog.isoCodeForName("Hindi"))
        assertEquals("ta-IN", OfflineLanguageCatalog.bcp47ForName("Tamil"))
        assertEquals("es", OfflineLanguageCatalog.isoCodeForName("Spanish"))
        assertNull(OfflineLanguageCatalog.isoCodeForName("Auto"))
    }

    @Test
    fun audioClipExtractor_wrapsWavHeaderAndSlicesPcmSegments() {
        val sampleRate = 16_000
        val tenSecondsBytes = ByteArray(sampleRate * 2 * 10)
        val bb = ByteBuffer.wrap(tenSecondsBytes).order(ByteOrder.LITTLE_ENDIAN)
        // Put non-zero speech tone so RMS > 0, with a quiet pause around 5s
        for (i in 0 until sampleRate * 10) {
            val isPause = i in (sampleRate * 48 / 10)..(sampleRate * 52 / 10)
            bb.putShort(if (isPause) 0 else 2500)
        }
        val wav = AudioClipExtractor.wrapPcmAsWav(tenSecondsBytes, sampleRate, 1)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals(44 + tenSecondsBytes.size, wav.size)

        val segments = AudioClipExtractor.slicePcmIntoSpeechSegments(tenSecondsBytes, sampleRate)
        assertTrue("10s clip should slice into >= 2 segments", segments.size >= 2)
        assertEquals(0L, segments.first().startMs)
    }

    @Test
    fun renderStatusAndCredentialMasking_behaveSafely() {
        assertEquals(RenderJobStatus.QUEUED, RenderJobStatus.fromStored("UNKNOWN_STATUS"))
        assertEquals(RenderJobStatus.COMPLETED, RenderJobStatus.fromStored("COMPLETED"))
        assertEquals(AiCaptionProvider.FIREBASE_BUILTIN, AiCaptionProvider.fromStored("INVALID"))
        val masked = SecureCredentialStore.maskSecret("AIzaSySuperSecretKey1234")
        assertFalse(masked.contains("SuperSecret"))
        assertTrue(masked.startsWith("AIza") && masked.endsWith("1234"))
    }
}
