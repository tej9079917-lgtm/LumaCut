package com.example

import com.example.history.EditHistoryManager
import com.example.history.EditorDocumentState
import com.example.history.OverlayPropertyCommand
import com.example.media.ChromaKeyMath
import com.example.model.ChromaKeyColor
import com.example.model.ChromaKeySettings
import com.example.model.OverlayLayer
import com.example.model.OverlayType
import com.example.model.TextMotionEvaluator
import com.example.model.TextMotionPreset
import com.example.model.TimelineDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaKeyAndTextMotionTest {

    @Test
    fun chromaKeyMath_removesGreenScreenAndPreservesSubject() {
        val greenKey = ChromaKeySettings(
            enabled = true,
            keyColor = ChromaKeyColor.GREEN,
            similarity = 0.36f,
            smoothness = 0.14f,
            spillSuppression = 0.6f
        )

        // Bright studio green pixel should become fully transparent
        val bgAlpha = ChromaKeyMath.computeChromaAlpha(0.02f, 0.88f, 0.22f, greenKey)
        assertEquals(0f, bgAlpha, 0.02f)

        // Warm skin-tone / red-orange subject pixel should stay opaque
        val fgAlpha = ChromaKeyMath.computeChromaAlpha(0.85f, 0.52f, 0.38f, greenKey)
        assertEquals(1f, fgAlpha, 0.02f)

        // Disabled chroma key always returns 1.0 alpha
        val disabledAlpha = ChromaKeyMath.computeChromaAlpha(0.0f, 1.0f, 0.0f, greenKey.copy(enabled = false))
        assertEquals(1f, disabledAlpha, 0.001f)
    }

    @Test
    fun chromaKeyMath_removesBlueScreenAndSuppressesColorSpill() {
        val blueKey = ChromaKeySettings(
            enabled = true,
            keyColor = ChromaKeyColor.BLUE,
            similarity = 0.36f,
            smoothness = 0.14f,
            spillSuppression = 0.8f
        )

        // Studio blue screen pixel becomes transparent
        val bgAlpha = ChromaKeyMath.computeChromaAlpha(0.06f, 0.26f, 0.94f, blueKey)
        assertEquals(0f, bgAlpha, 0.02f)

        // Warm yellow/amber foreground remains fully opaque
        val fgAlpha = ChromaKeyMath.computeChromaAlpha(0.95f, 0.65f, 0.10f, blueKey)
        assertEquals(1f, fgAlpha, 0.02f)

        // Blue spill on a gray edge pixel (0.4, 0.4, 0.75) gets suppressed cleanly
        val (r, g, b) = ChromaKeyMath.suppressSpill(0.4f, 0.4f, 0.75f, blueKey)
        assertEquals(0.4f, r, 0.001f)
        assertEquals(0.4f, g, 0.001f)
        assertTrue("Expected blue spill reduced below 0.5, got $b", b < 0.5f)
    }

    @Test
    fun textMotionEvaluator_typewriterAndPresetsAnimateAndSettleCleanly() {
        val text = "CINEMA TITLE"

        // Typewriter reveals progressively and ends with full string & no cursor
        val twMid = TextMotionEvaluator.evaluate(TextMotionPreset.TYPEWRITER, text, 0.5f)
        assertEquals(6, twMid.visibleCharCount)
        val twEnd = TextMotionEvaluator.evaluate(TextMotionPreset.TYPEWRITER, text, 1.0f)
        assertEquals(text.length, twEnd.visibleCharCount)
        assertFalse(twEnd.showCursor)
        assertEquals(text, TextMotionEvaluator.sliceVisibleText(text, twEnd))

        // Fade In goes from 0 -> 1 alpha
        val fadeStart = TextMotionEvaluator.evaluate(TextMotionPreset.FADE_IN, text, 0f)
        val fadeEnd = TextMotionEvaluator.evaluate(TextMotionPreset.FADE_IN, text, 1f)
        assertEquals(0f, fadeStart.alpha, 0.001f)
        assertEquals(1f, fadeEnd.alpha, 0.001f)

        // Slide Up starts below (+offsetY) and settles at 0
        val slideStart = TextMotionEvaluator.evaluate(TextMotionPreset.SLIDE_UP, text, 0f)
        val slideEnd = TextMotionEvaluator.evaluate(TextMotionPreset.SLIDE_UP, text, 1f)
        assertTrue(slideStart.offsetYFraction > 0.10f)
        assertEquals(0f, slideEnd.offsetYFraction, 0.001f)

        // Tracking Expand starts wide and converges to 0sp
        val trackStart = TextMotionEvaluator.evaluate(TextMotionPreset.TRACKING_EXPAND, text, 0f)
        val trackEnd = TextMotionEvaluator.evaluate(TextMotionPreset.TRACKING_EXPAND, text, 1f)
        assertTrue(trackStart.letterSpacingSp > 10f)
        assertEquals(0f, trackEnd.letterSpacingSp, 0.001f)

        // Bounce Pop starts small and settles at 1.0 scale
        val popStart = TextMotionEvaluator.evaluate(TextMotionPreset.POP_SPRING, text, 0f)
        val popEnd = TextMotionEvaluator.evaluate(TextMotionPreset.POP_SPRING, text, 1f)
        assertTrue(popStart.scale < 0.4f)
        assertEquals(1f, popEnd.scale, 0.001f)
    }

    @Test
    fun textMotionEvaluator_timelineSyncRespectsStartAndDuration() {
        val overlay = OverlayLayer(
            id = "t1",
            type = OverlayType.TEXT,
            startTimeMs = 2000L,
            durationMs = 4000L,
            text = "HELLO WORLD",
            textMotionPreset = TextMotionPreset.SLIDE_LEFT,
            textMotionDurationMs = 1000L
        )

        val atStart = TextMotionEvaluator.evaluateAtTimelineMs(overlay, 2000L)
        val atHalf = TextMotionEvaluator.evaluateAtTimelineMs(overlay, 2500L)
        val afterEntrance = TextMotionEvaluator.evaluateAtTimelineMs(overlay, 3500L)

        assertTrue(atStart.offsetXFraction > atHalf.offsetXFraction)
        assertEquals(0f, afterEntrance.offsetXFraction, 0.001f)
        assertEquals(1f, afterEntrance.alpha, 0.001f)
    }

    @Test
    fun overlayPropertyCommand_undoesChromaKeyAndTextMotionChanges() {
        val manager = EditHistoryManager()
        val videoOverlay = OverlayLayer(
            id = "v1",
            type = OverlayType.VIDEO,
            videoUri = "file:///green.mp4",
            displayName = "GreenScreen.mp4"
        )
        val textOverlay = OverlayLayer(
            id = "t1",
            type = OverlayType.TEXT,
            text = "INTRO"
        )
        var state = EditorDocumentState(
            timeline = TimelineDocument(overlays = listOf(videoOverlay, textOverlay))
        )

        val keyed = videoOverlay.copy(
            chromaKey = ChromaKeySettings(enabled = true, keyColor = ChromaKeyColor.BLUE, similarity = 0.42f)
        )
        state = manager.execute(
            OverlayPropertyCommand("v1", videoOverlay, keyed, "Enable Chroma Key"),
            state
        )!!.newState
        assertTrue(state.timeline.overlays[0].chromaKey.enabled)
        assertEquals(ChromaKeyColor.BLUE, state.timeline.overlays[0].chromaKey.keyColor)

        val animated = textOverlay.copy(textMotionPreset = TextMotionPreset.TYPEWRITER)
        state = manager.execute(
            OverlayPropertyCommand("t1", textOverlay, animated, "Text Motion: Typewriter"),
            state
        )!!.newState
        assertEquals(TextMotionPreset.TYPEWRITER, state.timeline.overlays[1].textMotionPreset)

        // Undo text motion
        state = manager.undo(state)!!.newState
        assertEquals(TextMotionPreset.NONE, state.timeline.overlays[1].textMotionPreset)

        // Undo chroma key
        state = manager.undo(state)!!.newState
        assertFalse(state.timeline.overlays[0].chromaKey.enabled)

        // Redo chroma key
        state = manager.redo(state)!!.newState
        assertTrue(state.timeline.overlays[0].chromaKey.enabled)
    }
}
