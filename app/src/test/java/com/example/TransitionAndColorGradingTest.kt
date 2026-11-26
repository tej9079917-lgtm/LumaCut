package com.example

import com.example.history.BatchTransitionChangeCommand
import com.example.history.EditHistoryManager
import com.example.history.EditorDocumentState
import com.example.model.ColorAdjustments
import com.example.model.ColorMatrixEngine
import com.example.model.FilterPreset
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.TimelineDocument
import com.example.model.TransitionSpec
import com.example.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionAndColorGradingTest {

    @Test
    fun colorMatrix_temperatureWarmBoostsRedAndCoolBoostsBlue() {
        val neutral = ColorMatrixEngine.buildCombinedMatrix(
            FilterPreset.ORIGINAL,
            1f,
            ColorAdjustments()
        )
        val warm = ColorMatrixEngine.buildCombinedMatrix(
            FilterPreset.ORIGINAL,
            1f,
            ColorAdjustments(temperature = 0.8f)
        )
        val cool = ColorMatrixEngine.buildCombinedMatrix(
            FilterPreset.ORIGINAL,
            1f,
            ColorAdjustments(temperature = -0.8f)
        )

        // Neutral stays identity diagonal (1, 1, 1)
        assertEquals(1f, neutral[0], 0.001f)
        assertEquals(1f, neutral[6], 0.001f)
        assertEquals(1f, neutral[12], 0.001f)

        // Warm boosts Red diagonal [0] and lowers Blue diagonal [12]
        assertTrue("Expected warm Red > 1.0, got ${warm[0]}", warm[0] > 1.08f)
        assertTrue("Expected warm Blue < 1.0, got ${warm[12]}", warm[12] < 0.92f)

        // Cool lowers Red diagonal [0] and boosts Blue diagonal [12]
        assertTrue("Expected cool Red < 1.0, got ${cool[0]}", cool[0] < 0.92f)
        assertTrue("Expected cool Blue > 1.0, got ${cool[12]}", cool[12] > 1.08f)
    }

    @Test
    fun colorAdjustments_isDefaultReflectsTemperature() {
        assertTrue(ColorAdjustments().isDefault)
        assertFalse(ColorAdjustments(temperature = 0.25f).isDefault)
        assertFalse(ColorAdjustments(temperature = -0.25f).isDefault)
    }

    @Test
    fun batchTransitionCommand_appliesAndUndoesAcrossAllCuts() {
        val manager = EditHistoryManager()
        val c1 = MainClip("c1", "file:///1.mp4", "Clip 1", MainMediaType.VIDEO, 4000L)
        val c2 = MainClip("c2", "file:///2.mp4", "Clip 2", MainMediaType.VIDEO, 4000L)
        val c3 = MainClip("c3", "file:///3.mp4", "Clip 3", MainMediaType.VIDEO, 4000L)
        var state = EditorDocumentState(timeline = TimelineDocument(mainClips = listOf(c1, c2, c3)))

        val target = TransitionSpec(TransitionType.WIPE_LEFT, 800L)
        val cmd = BatchTransitionChangeCommand(
            beforeByClipId = mapOf("c1" to c1.transitionToNext, "c2" to c2.transitionToNext),
            afterByClipId = mapOf("c1" to target, "c2" to target),
            label = "Apply Wipe Left to All Cuts"
        )

        state = manager.execute(cmd, state)!!.newState
        assertEquals(TransitionType.WIPE_LEFT, state.timeline.mainClips[0].transitionToNext.type)
        assertEquals(800L, state.timeline.mainClips[1].transitionToNext.durationMs)
        assertEquals(TransitionType.NONE, state.timeline.mainClips[2].transitionToNext.type)

        state = manager.undo(state)!!.newState
        assertEquals(TransitionType.NONE, state.timeline.mainClips[0].transitionToNext.type)
        assertEquals(TransitionType.NONE, state.timeline.mainClips[1].transitionToNext.type)

        state = manager.redo(state)!!.newState
        assertEquals(TransitionType.WIPE_LEFT, state.timeline.mainClips[0].transitionToNext.type)
    }
}
