package com.example

import com.example.history.AddFxLayerCommand
import com.example.history.DeleteFxLayerCommand
import com.example.history.EditorDocumentState
import com.example.history.MainClipCutoutCommand
import com.example.history.MainClipPanZoomCommand
import com.example.history.UpdateFxLayerCommand
import com.example.media.VisualFxMath
import com.example.media.VoiceDspMath
import com.example.model.AiCutoutSettings
import com.example.model.AudioSettings
import com.example.model.ClipTransform
import com.example.model.CropRect
import com.example.model.CutoutBackgroundMode
import com.example.model.FxLayer
import com.example.model.FxShape
import com.example.model.FxType
import com.example.model.KeyframeEvaluator
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.PanZoomSpec
import com.example.model.TimelineDocument
import com.example.model.VoiceEffectPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KineMasterProV3Test {

    @Test
    fun visualFxMath_activeWindowAndRegionWeights() {
        val circleFx = FxLayer(
            id = "fx-1",
            type = FxType.MAGNIFIER,
            startTimeMs = 1000L,
            durationMs = 3000L,
            fullFrame = false,
            shape = FxShape.CIRCLE,
            centerX = 0.5f,
            centerY = 0.5f,
            width = 0.40f,
            height = 0.40f,
            feather = 0.02f,
            opacity = 0.9f
        )

        assertFalse(VisualFxMath.isLayerActiveAt(circleFx, 999L))
        assertTrue(VisualFxMath.isLayerActiveAt(circleFx, 1000L))
        assertTrue(VisualFxMath.isLayerActiveAt(circleFx, 2500L))
        assertTrue(VisualFxMath.isLayerActiveAt(circleFx, 4000L))
        assertFalse(VisualFxMath.isLayerActiveAt(circleFx, 4001L))

        val insideCircle = VisualFxMath.evaluateRegionWeight(circleFx, 0.5f, 0.5f)
        val outsideCircle = VisualFxMath.evaluateRegionWeight(circleFx, 0.05f, 0.05f)
        assertEquals(0.9f, insideCircle, 0.02f)
        assertEquals(0f, outsideCircle, 0.01f)

        val rectFx = circleFx.copy(shape = FxShape.ROUNDED_RECT, opacity = 1.0f)
        assertEquals(1.0f, VisualFxMath.evaluateRegionWeight(rectFx, 0.5f, 0.5f), 0.02f)
        assertEquals(0f, VisualFxMath.evaluateRegionWeight(rectFx, 0.95f, 0.95f), 0.01f)

        val fullFrameFx = circleFx.copy(fullFrame = true, opacity = 0.75f)
        assertEquals(0.75f, VisualFxMath.evaluateRegionWeight(fullFrameFx, 0.01f, 0.99f), 0.001f)
        assertEquals(0.75f, VisualFxMath.evaluateRegionWeight(fullFrameFx, 0.5f, 0.5f), 0.001f)
    }

    @Test
    fun visualFxMath_cropPresetsRespectTargetAspect() {
        val stage16x9 = 16f / 9f

        val free = VisualFxMath.cropPresetForRatio("Free", stage16x9)
        assertFalse(free.isActive)
        assertEquals("Free", free.aspectPreset)

        val crop9x16 = VisualFxMath.cropPresetForRatio("9:16", stage16x9)
        assertTrue(crop9x16.isActive)
        assertTrue(crop9x16.left > 0.25f && crop9x16.right > 0.25f)
        assertEquals(0f, crop9x16.top, 0.001f)
        assertEquals("9:16", crop9x16.aspectPreset)

        val cropSquare = VisualFxMath.cropPresetForRatio("1:1", stage16x9)
        assertTrue(cropSquare.left > 0.15f && cropSquare.right > 0.15f)
        assertEquals(0f, cropSquare.bottom, 0.001f)

        val crop4x5 = VisualFxMath.cropPresetForRatio("4:5", stage16x9)
        assertTrue(crop4x5.visibleWidthFraction < cropSquare.visibleWidthFraction)

        val crop16x9OnPortrait = VisualFxMath.cropPresetForRatio("16:9", 9f / 16f)
        assertTrue(crop16x9OnPortrait.top > 0.25f && crop16x9OnPortrait.bottom > 0.25f)
        assertEquals(0f, crop16x9OnPortrait.left, 0.001f)
    }

    @Test
    fun keyframeEvaluator_panZoomAndGeometryPreservation() {
        val baseCrop = CropRect(left = 0.1f, top = 0.05f, right = 0.1f, bottom = 0.05f, aspectPreset = "4:5")
        val clip = MainClip(
            id = "clip-1",
            uri = "content://media/1",
            displayName = "Intro.mp4",
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = 4000L,
            baseTransform = ClipTransform(
                scale = 1.0f,
                flipHorizontal = true,
                flipVertical = true,
                fullFrameFill = true,
                crop = baseCrop
            ),
            panZoom = PanZoomSpec(
                enabled = true,
                startScale = 1.0f,
                startOffsetX = -0.10f,
                startOffsetY = 0.0f,
                endScale = 1.6f,
                endOffsetX = 0.10f,
                endOffsetY = 0.20f,
                linked = false
            )
        )

        val startT = KeyframeEvaluator.evaluate(clip, 0L)
        val midT = KeyframeEvaluator.evaluate(clip, 2000L)
        val endT = KeyframeEvaluator.evaluate(clip, 4000L)

        assertEquals(1.0f, startT.scale, 0.01f)
        assertEquals(-0.10f, startT.offsetX, 0.01f)
        assertEquals(1.3f, midT.scale, 0.02f)
        assertEquals(0.0f, midT.offsetX, 0.02f)
        assertEquals(1.6f, endT.scale, 0.01f)
        assertEquals(0.10f, endT.offsetX, 0.01f)
        assertEquals(0.20f, endT.offsetY, 0.01f)

        // Preserves flip, full-frame fill, and crop
        assertTrue(midT.flipHorizontal)
        assertTrue(midT.flipVertical)
        assertTrue(midT.fullFrameFill)
        assertEquals(baseCrop, midT.crop)

        // Linked start == end locks motion across entire clip
        val linkedClip = clip.copy(panZoom = clip.panZoom.copy(linked = true))
        val linkedEnd = KeyframeEvaluator.evaluate(linkedClip, 4000L)
        assertEquals(1.0f, linkedEnd.scale, 0.01f)
        assertEquals(-0.10f, linkedEnd.offsetX, 0.01f)
        assertEquals(0.0f, linkedEnd.offsetY, 0.01f)
    }

    @Test
    fun voiceDspMath_denoiseGateAndAllVoiceEffectPresetsBounded() {
        assertEquals(-60f, VoiceDspMath.effectiveNoiseGateDb(-60f, 0f), 0.001f)
        val midDenoise = VoiceDspMath.effectiveNoiseGateDb(-60f, 0.5f)
        val maxDenoise = VoiceDspMath.effectiveNoiseGateDb(-60f, 1.0f)
        assertEquals(-39f, midDenoise, 0.01f)
        assertEquals(-26f, maxDenoise, 0.01f)
        // Never loosens an already stricter manual gate
        assertEquals(-20f, VoiceDspMath.effectiveNoiseGateDb(-20f, 0.5f), 0.01f)

        val testInputs = listOf(-0.95f, -0.4f, 0f, 0.35f, 0.92f)
        for (preset in VoiceEffectPreset.entries) {
            for ((idx, sample) in testInputs.withIndex()) {
                val out = VoiceDspMath.applyVoicePresetSample(
                    preset = preset,
                    sample = sample,
                    sampleIndex = (idx * 37L) + 11L,
                    sampleRate = 48_000,
                    delaySample = sample * 0.5f,
                    hpState = sample * 0.2f,
                    lpState = sample * 0.7f
                )
                assertTrue("Sample must be finite for $preset", out.isFinite())
                assertTrue("Sample must stay within [-1, 1] for $preset, got $out", out in -1.001f..1.001f)
            }
        }
    }

    @Test
    fun mainClipEffectiveAudioPitch_reflectsVoiceEffectPresets() {
        val baseClip = MainClip(
            id = "clip-pitch",
            uri = "content://media/2",
            displayName = "Voice.mp4",
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = 5000L
        )
        assertEquals(1.0f, baseClip.effectiveAudioPitch, 0.001f)

        val chipmunk = baseClip.copy(audio = AudioSettings(voiceEffect = VoiceEffectPreset.CHIPMUNK))
        val deepBass = baseClip.copy(audio = AudioSettings(voiceEffect = VoiceEffectPreset.DEEP_BASS))
        val monster = baseClip.copy(audio = AudioSettings(voiceEffect = VoiceEffectPreset.MONSTER))

        assertEquals(VoiceEffectPreset.CHIPMUNK.pitchFactor, chipmunk.effectiveAudioPitch, 0.001f)
        assertEquals(VoiceEffectPreset.DEEP_BASS.pitchFactor, deepBass.effectiveAudioPitch, 0.001f)
        assertEquals(VoiceEffectPreset.MONSTER.pitchFactor, monster.effectiveAudioPitch, 0.001f)
        assertTrue(chipmunk.effectiveAudioPitch > 1.4f)
        assertTrue(monster.effectiveAudioPitch < deepBass.effectiveAudioPitch)
    }

    @Test
    fun fxCommandsPanZoomAndCutoutCommands_executeAndUndoCleanly() {
        val clip = MainClip(
            id = "c1",
            uri = "content://media/c1",
            displayName = "Clip1.mp4",
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = 4000L
        )
        val initial = EditorDocumentState(
            projectTitle = "KineMaster Test",
            timeline = TimelineDocument(mainClips = listOf(clip))
        )

        val fx = FxLayer(
            id = "fx-mosaic",
            type = FxType.MOSAIC,
            startTimeMs = 1500L,
            durationMs = 5000L,
            fullFrame = false,
            intensity = 0.65f
        )

        // Add FX extends timeline totalDurationMs to 6500ms
        val addCmd = AddFxLayerCommand(fx)
        val afterAdd = addCmd.execute(initial)
        assertEquals(1, afterAdd.timeline.fxLayers.size)
        assertEquals(6500L, afterAdd.timeline.totalDurationMs)
        assertEquals(1, afterAdd.timeline.activeFxLayersAt(3000L).size)
        assertEquals(0, afterAdd.timeline.activeFxLayersAt(1000L).size)
        assertEquals(initial, addCmd.undo(afterAdd))

        // Update FX
        val updatedFx = fx.copy(fullFrame = true, intensity = 0.9f)
        val updateCmd = UpdateFxLayerCommand(fx.id, before = fx, after = updatedFx)
        val afterUpdate = updateCmd.execute(afterAdd)
        assertTrue(afterUpdate.timeline.fxLayers.first().fullFrame)
        assertEquals(0.9f, afterUpdate.timeline.fxLayers.first().intensity, 0.001f)
        assertEquals(afterAdd, updateCmd.undo(afterUpdate))

        // Delete FX
        val deleteCmd = DeleteFxLayerCommand(updatedFx)
        val afterDelete = deleteCmd.execute(afterUpdate)
        assertTrue(afterDelete.timeline.fxLayers.isEmpty())
        assertEquals(afterUpdate, deleteCmd.undo(afterDelete))

        // Pan & Zoom Command
        val newPz = PanZoomSpec(enabled = true, startScale = 1.0f, endScale = 1.45f, linked = false)
        val pzCmd = MainClipPanZoomCommand(clip.id, before = clip.panZoom, after = newPz)
        val afterPz = pzCmd.execute(initial)
        assertTrue(afterPz.timeline.mainClips.first().panZoom.enabled)
        assertEquals(1.45f, afterPz.timeline.mainClips.first().panZoom.endScale, 0.001f)
        assertEquals(initial, pzCmd.undo(afterPz))

        // AI Cutout Command
        val newCutout = AiCutoutSettings(
            enabled = true,
            threshold = 0.62f,
            featherPx = 6f,
            backgroundMode = CutoutBackgroundMode.GAUSSIAN_BLUR
        )
        val cutoutCmd = MainClipCutoutCommand(clip.id, before = clip.aiCutout, after = newCutout)
        val afterCutout = cutoutCmd.execute(initial)
        assertTrue(afterCutout.timeline.mainClips.first().aiCutout.enabled)
        assertEquals(CutoutBackgroundMode.GAUSSIAN_BLUR, afterCutout.timeline.mainClips.first().aiCutout.backgroundMode)
        assertEquals(initial, cutoutCmd.undo(afterCutout))
    }

    @Test
    fun safeZoneOverlay_includesYouTubePreset() {
        val labels = com.example.model.SafeZoneOverlay.entries.map { it.label }
        assertTrue(labels.contains("YouTube"))
        assertTrue(labels.contains("TikTok"))
        assertTrue(labels.contains("Reels"))
        assertTrue(labels.contains("Shorts"))
        assertEquals("YouTube", com.example.model.SafeZoneOverlay.YOUTUBE.label)
    }
}
