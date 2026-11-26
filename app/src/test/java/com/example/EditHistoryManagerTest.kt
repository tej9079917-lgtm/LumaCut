package com.example

import com.example.history.AddMainClipsCommand
import com.example.history.AddOverlayCommand
import com.example.history.AspectRatioCommand
import com.example.history.AudioSettingsCommand
import com.example.history.ColorGradeCommand
import com.example.history.DeleteMainClipCommand
import com.example.history.EditHistoryManager
import com.example.history.EditorDocumentState
import com.example.history.FilterChangeCommand
import com.example.history.OverlayPropertyCommand
import com.example.history.OverlayTransformSnapshot
import com.example.history.ReorderMainClipCommand
import com.example.history.SplitMainClipCommand
import com.example.history.TransformOverlayCommand
import com.example.history.TransitionChangeCommand
import com.example.history.TrimMainClipCommand
import com.example.model.AspectRatio
import com.example.model.AudioSettings
import com.example.model.ColorAdjustments
import com.example.model.FilterPreset
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.OverlayLayer
import com.example.model.OverlayType
import com.example.model.TimelineDocument
import com.example.model.TransitionSpec
import com.example.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryManagerTest {

    private fun sampleClip(id: String, name: String = "Clip $id", dur: Long = 6000L): MainClip =
        MainClip(
            id = id,
            uri = "file:///tmp/$id.mp4",
            displayName = name,
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = dur,
            trimStartMs = 0L,
            trimEndMs = dur
        )

    @Test
    fun splitCommand_executesUndoesAndRedoesExactClip() {
        val manager = EditHistoryManager()
        val original = sampleClip("c1", "Intro", 8000L).copy(
            transitionToNext = TransitionSpec(TransitionType.CROSSFADE, 700L)
        )
        var state = EditorDocumentState(timeline = TimelineDocument(mainClips = listOf(original)))

        val left = original.copy(trimEndMs = 3000L, transitionToNext = TransitionSpec(TransitionType.NONE, 600L))
        val right = original.copy(id = "c2", trimStartMs = 3000L)

        val splitRes = manager.execute(SplitMainClipCommand(0, original, left, right), state)!!
        state = splitRes.newState
        assertEquals(2, state.timeline.mainClips.size)
        assertEquals(3000L, state.timeline.mainClips[0].durationMs)
        assertEquals(5000L, state.timeline.mainClips[1].durationMs)
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)

        val undoRes = manager.undo(state)!!
        state = undoRes.newState
        assertEquals(1, state.timeline.mainClips.size)
        assertEquals(original, state.timeline.mainClips[0])
        assertFalse(manager.canUndo)
        assertTrue(manager.canRedo)

        val redoRes = manager.redo(state)!!
        state = redoRes.newState
        assertEquals(2, state.timeline.mainClips.size)
        assertEquals("c2", state.timeline.mainClips[1].id)
    }

    @Test
    fun rapidPropertyEdits_coalesceIntoSingleUndoableCommand_untilBarrier() {
        val manager = EditHistoryManager(coalesceWindowMs = 1000L)
        val clip = sampleClip("c1")
        var state = EditorDocumentState(timeline = TimelineDocument(mainClips = listOf(clip)))

        // Three consecutive slider drag ticks within 300ms
        state = manager.execute(
            ColorGradeCommand(
                beforeByClipId = mapOf("c1" to ColorAdjustments(0f, 1f, 1f)),
                afterByClipId = mapOf("c1" to ColorAdjustments(0.1f, 1f, 1f)),
                mergeKey = "color_grade:c1",
                timestampMs = 1000L
            ),
            state
        )!!.newState

        state = manager.execute(
            ColorGradeCommand(
                beforeByClipId = mapOf("c1" to ColorAdjustments(0.1f, 1f, 1f)),
                afterByClipId = mapOf("c1" to ColorAdjustments(0.25f, 1.2f, 1f)),
                mergeKey = "color_grade:c1",
                timestampMs = 1150L
            ),
            state
        )!!.newState

        state = manager.execute(
            ColorGradeCommand(
                beforeByClipId = mapOf("c1" to ColorAdjustments(0.25f, 1.2f, 1f)),
                afterByClipId = mapOf("c1" to ColorAdjustments(0.4f, 1.3f, 1.5f)),
                mergeKey = "color_grade:c1",
                timestampMs = 1300L
            ),
            state
        )!!.newState

        assertEquals(1, manager.snapshotUi().appliedCount)
        assertEquals(0.4f, state.timeline.mainClips[0].colorAdjustments.brightness, 0.001f)

        // Single undo restores the original (0f, 1f, 1f) state
        state = manager.undo(state)!!.newState
        assertEquals(ColorAdjustments(0f, 1f, 1f), state.timeline.mainClips[0].colorAdjustments)

        // Redo restores the final coalesced state
        state = manager.redo(state)!!.newState
        assertEquals(0.4f, state.timeline.mainClips[0].colorAdjustments.brightness, 0.001f)

        // Breaking coalescing creates a second distinct step
        manager.breakCoalescing()
        state = manager.execute(
            ColorGradeCommand(
                beforeByClipId = mapOf("c1" to state.timeline.mainClips[0].colorAdjustments),
                afterByClipId = mapOf("c1" to ColorAdjustments(-0.2f, 1f, 0.5f)),
                mergeKey = "color_grade:c1",
                timestampMs = 1350L
            ),
            state
        )!!.newState
        assertEquals(2, manager.snapshotUi().appliedCount)
    }

    @Test
    fun movesTransformsAudioAndJumpToIndex_workDeterministically() {
        val manager = EditHistoryManager()
        val c1 = sampleClip("c1")
        val c2 = sampleClip("c2")
        val textLayer = OverlayLayer(id = "ov1", type = OverlayType.TEXT, text = "Intro")
        var state = EditorDocumentState(aspectRatio = AspectRatio.PORTRAIT_9_16)

        // Step 1: Add clips
        state = manager.execute(AddMainClipsCommand(listOf(c1, c2), "Import 2 Videos"), state)!!.newState
        // Step 2: Reorder clips
        state = manager.execute(ReorderMainClipCommand(0, 1, "Move Clip Right"), state)!!.newState
        assertEquals(listOf("c2", "c1"), state.timeline.mainClips.map { it.id })
        // Step 3: Add overlay
        state = manager.execute(AddOverlayCommand(textLayer, "Add Text Layer"), state)!!.newState
        // Step 4: Transform overlay
        state = manager.execute(
            TransformOverlayCommand(
                overlayId = "ov1",
                before = OverlayTransformSnapshot(0f, 0f, 1f, 0f),
                after = OverlayTransformSnapshot(0.2f, -0.15f, 1.5f, 12f)
            ),
            state
        )!!.newState
        // Step 5: Edit overlay text + audio + aspect ratio
        val updatedText = state.timeline.overlays[0].copy(text = "Cinema Cut")
        state = manager.execute(OverlayPropertyCommand("ov1", state.timeline.overlays[0], updatedText), state)!!.newState
        state = manager.execute(
            AudioSettingsCommand(
                "c2",
                false,
                AudioSettings(),
                AudioSettings(volume = 0.5f, isMuted = false, fadeInMs = 500L, fadeOutMs = 800L)
            ),
            state
        )!!.newState
        state = manager.execute(AspectRatioCommand(AspectRatio.PORTRAIT_9_16, AspectRatio.LANDSCAPE_16_9), state)!!.newState

        assertEquals(7, manager.snapshotUi().appliedCount)
        assertEquals(AspectRatio.LANDSCAPE_16_9, state.aspectRatio)

        // Jump straight back to step 2 (after reorder, before overlay)
        state = manager.jumpTo(2, state)!!.newState
        assertEquals(2, manager.snapshotUi().appliedCount)
        assertEquals(AspectRatio.PORTRAIT_9_16, state.aspectRatio)
        assertTrue(state.timeline.overlays.isEmpty())
        assertEquals(listOf("c2", "c1"), state.timeline.mainClips.map { it.id })
        assertTrue(manager.canRedo)

        // Jump forward to step 7
        state = manager.jumpTo(7, state)!!.newState
        assertEquals(7, manager.snapshotUi().appliedCount)
        assertEquals("Cinema Cut", state.timeline.overlays[0].text)
        assertEquals(500L, state.timeline.mainClips[0].audio.fadeInMs)
        assertEquals(AspectRatio.LANDSCAPE_16_9, state.aspectRatio)

        // Jump back to 0 (Session Start)
        state = manager.jumpTo(0, state)!!.newState
        assertTrue(state.timeline.mainClips.isEmpty())
        assertFalse(manager.canUndo)
        assertEquals(7, manager.snapshotUi().totalCount)
    }

    @Test
    fun newEditAfterUndo_clearsRedoStackAndRespectsCapacity() {
        val manager = EditHistoryManager(maxCapacity = 3)
        val c1 = sampleClip("c1")
        var state = EditorDocumentState(timeline = TimelineDocument(mainClips = listOf(c1)))

        state = manager.execute(TrimMainClipCommand("c1", 0L, 6000L, 500L, 6000L, timestampMs = 100L), state)!!.newState
        manager.breakCoalescing()
        state = manager.execute(
            FilterChangeCommand(
                mapOf("c1" to (FilterPreset.ORIGINAL to 1f)),
                mapOf("c1" to (FilterPreset.NOIR to 0.8f)),
                "Filter: Noir",
                timestampMs = 200L
            ),
            state
        )!!.newState
        state = manager.execute(
            TransitionChangeCommand("c1", TransitionSpec(), TransitionSpec(TransitionType.ZOOM_IN, 500L), "Zoom In", timestampMs = 300L),
            state
        )!!.newState
        state = manager.execute(DeleteMainClipCommand(0, state.timeline.mainClips[0], timestampMs = 400L), state)!!.newState

        // Capacity capped at 3
        assertEquals(3, manager.snapshotUi().appliedCount)

        // Undo Delete restores clip
        state = manager.undo(state)!!.newState
        assertEquals(1, state.timeline.mainClips.size)
        assertTrue(manager.canRedo)

        // Branching with a new command clears redo stack
        state = manager.execute(AspectRatioCommand(AspectRatio.PORTRAIT_9_16, AspectRatio.SQUARE_1_1), state)!!.newState
        assertFalse(manager.canRedo)
        assertNull(manager.redo(state))
    }
}
