package com.example

import com.example.history.ClipKeyframesCommand
import com.example.history.ClipSpeedCommand
import com.example.history.ClipSpeedSnapshot
import com.example.history.EditHistoryManager
import com.example.history.EditorDocumentState
import com.example.model.ClipTransform
import com.example.model.KeyframeEasing
import com.example.model.KeyframeEvaluator
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.TimelineDocument
import com.example.model.TransformKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeAndSpeedTest {

    @Test
    fun keyframeEvaluator_interpolatesLinearAndClampsOutsideBounds() {
        val kfStart = TransformKeyframe(
            id = "k1",
            timeOffsetMs = 1000L,
            offsetX = -0.2f,
            offsetY = 0.0f,
            scale = 1.0f,
            rotationDegrees = 0f,
            easing = KeyframeEasing.LINEAR
        )
        val kfEnd = TransformKeyframe(
            id = "k2",
            timeOffsetMs = 3000L,
            offsetX = 0.2f,
            offsetY = 0.4f,
            scale = 2.0f,
            rotationDegrees = 90f,
            easing = KeyframeEasing.LINEAR
        )
        val clip = MainClip(
            id = "c1",
            uri = "file:///clip.mp4",
            displayName = "Clip",
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = 5000L,
            keyframes = listOf(kfEnd, kfStart) // intentionally unsorted
        )

        // Before first keyframe clamps to first keyframe
        val before = KeyframeEvaluator.evaluate(clip, 200L)
        assertEquals(-0.2f, before.offsetX, 0.001f)
        assertEquals(1.0f, before.scale, 0.001f)

        // Midpoint at 2000ms is exact linear halfway
        val mid = KeyframeEvaluator.evaluate(clip, 2000L)
        assertEquals(0.0f, mid.offsetX, 0.001f)
        assertEquals(0.2f, mid.offsetY, 0.001f)
        assertEquals(1.5f, mid.scale, 0.001f)
        assertEquals(45f, mid.rotationDegrees, 0.001f)

        // After last keyframe clamps to last keyframe
        val after = KeyframeEvaluator.evaluate(clip, 4800L)
        assertEquals(0.2f, after.offsetX, 0.001f)
        assertEquals(2.0f, after.scale, 0.001f)
        assertEquals(90f, after.rotationDegrees, 0.001f)
    }

    @Test
    fun keyframeEvaluator_easingCurvesBehaveAsExpected() {
        val linear = KeyframeEvaluator.applyEasing(0.5f, KeyframeEasing.LINEAR)
        val easeIn = KeyframeEvaluator.applyEasing(0.5f, KeyframeEasing.EASE_IN)
        val easeOut = KeyframeEvaluator.applyEasing(0.5f, KeyframeEasing.EASE_OUT)
        val easeInOut = KeyframeEvaluator.applyEasing(0.5f, KeyframeEasing.EASE_IN_OUT)

        assertEquals(0.5f, linear, 0.001f)
        assertTrue("Expected EASE_IN < 0.5 at midpoint, got $easeIn", easeIn < 0.5f)
        assertTrue("Expected EASE_OUT > 0.5 at midpoint, got $easeOut", easeOut > 0.5f)
        assertEquals(0.5f, easeInOut, 0.001f)
        assertTrue(KeyframeEvaluator.applyEasing(0.25f, KeyframeEasing.EASE_IN_OUT) < 0.25f)
        assertTrue(KeyframeEvaluator.applyEasing(0.75f, KeyframeEasing.EASE_IN_OUT) > 0.75f)
    }

    @Test
    fun keyframeEvaluator_navigationFindsPreviousAndNextTimestamps() {
        val clip = MainClip(
            id = "c1",
            uri = "file:///clip.mp4",
            displayName = "Clip",
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = 6000L,
            keyframes = listOf(
                TransformKeyframe(id = "k1", timeOffsetMs = 500L),
                TransformKeyframe(id = "k2", timeOffsetMs = 2000L),
                TransformKeyframe(id = "k3", timeOffsetMs = 4500L)
            )
        )

        assertEquals(500L, KeyframeEvaluator.previousKeyframeTimeMs(clip, 2000L))
        assertEquals(4500L, KeyframeEvaluator.nextKeyframeTimeMs(clip, 2000L))
        assertNull(KeyframeEvaluator.previousKeyframeTimeMs(clip, 500L))
        assertNull(KeyframeEvaluator.nextKeyframeTimeMs(clip, 4500L))
    }

    @Test
    fun mainClip_playbackSpeedScalesDurationAccurately() {
        val base = MainClip(
            id = "c1",
            uri = "file:///clip.mp4",
            displayName = "Clip",
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = 8000L,
            trimStartMs = 0L,
            trimEndMs = 8000L
        )

        assertEquals(8000L, base.copy(playbackSpeed = 1.0f).durationMs)
        assertEquals(16000L, base.copy(playbackSpeed = 0.5f).durationMs)
        assertEquals(4000L, base.copy(playbackSpeed = 2.0f).durationMs)
        assertEquals(2000L, base.copy(playbackSpeed = 4.0f).durationMs)

        // Images ignore playbackSpeed and stay at rawTrimDurationMs
        val photo = base.copy(mediaType = MainMediaType.IMAGE, playbackSpeed = 2.0f)
        assertEquals(1.0f, photo.safeSpeed, 0.001f)
        assertEquals(8000L, photo.durationMs)
    }

    @Test
    fun mainClip_effectiveAudioPitchHandlesPreservePitchAndSemitones() {
        val base = MainClip(
            id = "c1",
            uri = "file:///clip.mp4",
            displayName = "Clip",
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = 6000L
        )

        // Preserve pitch = true keeps 1.0x pitch at 2x speed with 0 semitones
        assertEquals(1.0f, base.copy(playbackSpeed = 2.0f, preservePitch = true, pitchShiftSemitones = 0f).effectiveAudioPitch, 0.001f)

        // Preserve pitch = false scales pitch proportionally with speed
        assertEquals(2.0f, base.copy(playbackSpeed = 2.0f, preservePitch = false, pitchShiftSemitones = 0f).effectiveAudioPitch, 0.001f)
        assertEquals(0.5f, base.copy(playbackSpeed = 0.5f, preservePitch = false, pitchShiftSemitones = 0f).effectiveAudioPitch, 0.001f)

        // +12 semitones doubles pitch, -12 semitones halves pitch
        assertEquals(2.0f, base.copy(playbackSpeed = 1.0f, preservePitch = true, pitchShiftSemitones = 12f).effectiveAudioPitch, 0.001f)
        assertEquals(0.5f, base.copy(playbackSpeed = 1.0f, preservePitch = true, pitchShiftSemitones = -12f).effectiveAudioPitch, 0.001f)
    }

    @Test
    fun clipKeyframesAndSpeedCommands_coalesceUndoAndRedoCleanly() {
        val manager = EditHistoryManager()
        val clip = MainClip("c1", "file:///1.mp4", "Clip 1", MainMediaType.VIDEO, 4000L)
        var state = EditorDocumentState(timeline = TimelineDocument(mainClips = listOf(clip)))

        val kf1 = listOf(TransformKeyframe(id = "k1", timeOffsetMs = 0L, scale = 1.2f))
        val kf2 = listOf(TransformKeyframe(id = "k1", timeOffsetMs = 0L, scale = 1.6f))
        val now = 10_000L

        // Two rapid keyframe drags coalesce into a single undo step
        state = manager.execute(
            ClipKeyframesCommand(
                clipId = "c1",
                beforeBaseTransform = ClipTransform(),
                beforeKeyframes = emptyList(),
                afterBaseTransform = ClipTransform(),
                afterKeyframes = kf1,
                timestampMs = now
            ),
            state
        )!!.newState
        state = manager.execute(
            ClipKeyframesCommand(
                clipId = "c1",
                beforeBaseTransform = ClipTransform(),
                beforeKeyframes = kf1,
                afterBaseTransform = ClipTransform(),
                afterKeyframes = kf2,
                timestampMs = now + 100L
            ),
            state
        )!!.newState

        assertEquals(1, manager.snapshotUi().appliedCount)
        assertEquals(1.6f, state.timeline.mainClips[0].keyframes[0].scale, 0.001f)

        // Two rapid speed slider changes coalesce into one command
        val s0 = ClipSpeedSnapshot(1.0f, 0f, true)
        val s1 = ClipSpeedSnapshot(1.5f, 2f, true)
        val s2 = ClipSpeedSnapshot(2.0f, 4f, false)

        state = manager.execute(
            ClipSpeedCommand("c1", s0, s1, timestampMs = now + 200L),
            state
        )!!.newState
        state = manager.execute(
            ClipSpeedCommand("c1", s1, s2, timestampMs = now + 350L),
            state
        )!!.newState

        assertEquals(2, manager.snapshotUi().appliedCount)
        assertEquals(2.0f, state.timeline.mainClips[0].playbackSpeed, 0.001f)
        assertEquals(4f, state.timeline.mainClips[0].pitchShiftSemitones, 0.001f)
        assertEquals(false, state.timeline.mainClips[0].preservePitch)

        // Undo speed restores original 1.0x speed
        state = manager.undo(state)!!.newState
        assertEquals(1.0f, state.timeline.mainClips[0].playbackSpeed, 0.001f)
        assertEquals(0f, state.timeline.mainClips[0].pitchShiftSemitones, 0.001f)

        // Undo keyframes restores empty keyframes
        state = manager.undo(state)!!.newState
        assertTrue(state.timeline.mainClips[0].keyframes.isEmpty())

        // Redo restores keyframes then speed
        state = manager.redo(state)!!.newState
        assertEquals(1.6f, state.timeline.mainClips[0].keyframes[0].scale, 0.001f)
        state = manager.redo(state)!!.newState
        assertEquals(2.0f, state.timeline.mainClips[0].playbackSpeed, 0.001f)
    }
}
