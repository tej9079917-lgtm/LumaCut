package com.example

import com.example.media.AudioWaveformExtractor
import com.example.model.AspectRatio
import com.example.model.AudioClip
import com.example.model.ExportConfig
import com.example.model.ExportResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWaveformAndExportTest {

    @Test
    fun detectBeats_identifiesPeriodicTransientsWithSpacing() {
        // 100 buckets across 10,000 ms (100 ms per bucket) with clear spikes every 10 buckets (1000 ms)
        val peaks = MutableList(100) { 0.22f }
        val expectedBuckets = listOf(10, 20, 30, 50, 70, 90)
        for (idx in expectedBuckets) {
            peaks[idx] = 0.96f
        }

        val detected = AudioWaveformExtractor.detectBeats(peaks, durationMs = 10_000L)

        assertEquals(expectedBuckets.size, detected.size)
        expectedBuckets.forEachIndexed { i, bucket ->
            assertEquals(bucket * 100L, detected[i])
        }
    }

    @Test
    fun audioClip_timelineBeatPositions_respectsTrimAndOffset() {
        val clip = AudioClip(
            displayName = "Beat Track",
            uri = "file:///tmp/beat.mp3",
            startTimeMs = 5_000L,
            sourceDurationMs = 10_000L,
            trimStartMs = 2_000L,
            trimEndMs = 6_000L,
            beatPositionsMs = listOf(500L, 2_000L, 3_500L, 5_800L, 8_000L)
        )

        // Beats inside [2000..6000] are 2000, 3500, 5800 -> shifted by startTimeMs(5000) - trimStartMs(2000) = +3000
        assertEquals(
            listOf(5_000L, 6_500L, 8_800L),
            clip.timelineBeatPositionsMs()
        )
    }

    @Test
    fun aspectRatio_exportDimensions_supports1080pAnd4K() {
        assertEquals(1080 to 1920, AspectRatio.PORTRAIT_9_16.exportDimensions(ExportResolution.FHD_1080P))
        assertEquals(2160 to 3840, AspectRatio.PORTRAIT_9_16.exportDimensions(ExportResolution.UHD_4K))
        assertEquals(3840 to 2160, AspectRatio.LANDSCAPE_16_9.exportDimensions(ExportResolution.UHD_4K))
        assertEquals(2160 to 2160, AspectRatio.SQUARE_1_1.exportDimensions(ExportResolution.UHD_4K))
    }

    @Test
    fun exportConfig_estimateFileSize_scalesWithBitrateAndDuration() {
        val standard = ExportConfig(resolution = ExportResolution.FHD_1080P, fps = 30, bitrateMbps = 10)
        val master = ExportConfig(resolution = ExportResolution.UHD_4K, fps = 60, bitrateMbps = 40)

        val size10sStandard = standard.estimateFileSizeMb(10_000L)
        val size10sMaster = master.estimateFileSizeMb(10_000L)

        // 10 Mbps for 10s ~= 12.1 MB
        assertTrue("Expected ~12MB, got $size10sStandard", size10sStandard in 11.0f..13.5f)
        // 40 Mbps should be roughly 4x larger than 10 Mbps
        assertTrue("Expected master > 3.5x standard", size10sMaster > size10sStandard * 3.5f)
    }
}
