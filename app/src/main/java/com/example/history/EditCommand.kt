package com.example.history

import com.example.model.AiCutoutSettings
import com.example.model.AspectRatio
import com.example.model.AudioClip
import com.example.model.AudioSettings
import com.example.model.CanvasBackdrop
import com.example.model.CaptionClip
import com.example.model.ClipTransform
import com.example.model.ColorAdjustments
import com.example.model.FilterPreset
import com.example.model.FxLayer
import com.example.model.MainClip
import com.example.model.MaskSettings
import com.example.model.OverlayLayer
import com.example.model.PanZoomSpec
import com.example.model.SafeZoneOverlay
import com.example.model.TimelineDocument
import com.example.model.TransformKeyframe
import com.example.model.TransitionSpec

/**
 * Complete undoable document state mutated exclusively by [EditCommand] instances.
 */
data class EditorDocumentState(
    val projectTitle: String = "Untitled Edit",
    val aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val timeline: TimelineDocument = TimelineDocument()
)

enum class CommandCategory {
    CUT,
    MOVE,
    TRIM,
    MEDIA,
    FILTER,
    COLOR,
    AUDIO,
    TRANSITION,
    STYLE,
    CANVAS,
    MOTION,
    SPEED,
    CUTOUT,
    MASK,
    CAPTIONS,
    FX
}

/**
 * Command Pattern interface encapsulating a reversible editing action.
 */
sealed interface EditCommand {
    val label: String
    val category: CommandCategory
    val mergeKey: String?
    val timestampMs: Long

    fun execute(state: EditorDocumentState): EditorDocumentState
    fun undo(state: EditorDocumentState): EditorDocumentState

    /**
     * Optionally merges a rapid consecutive command (e.g., slider drag or timeline trim)
     * into a single atomic command preserving the original pre-edit state.
     */
    fun mergeWith(newer: EditCommand): EditCommand? = null
}

// --- 1. Media Import & Addition Commands ---

data class AddMainClipsCommand(
    val clips: List<MainClip>,
    override val label: String,
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.MEDIA
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(mainClips = state.timeline.mainClips + clips))

    override fun undo(state: EditorDocumentState): EditorDocumentState {
        val ids = clips.map { it.id }.toSet()
        return state.copy(
            timeline = state.timeline.copy(mainClips = state.timeline.mainClips.filterNot { it.id in ids })
        )
    }
}

data class AddOverlayCommand(
    val overlay: OverlayLayer,
    override val label: String,
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.MEDIA
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(overlays = state.timeline.overlays + overlay))

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(overlays = state.timeline.overlays.filterNot { it.id == overlay.id })
        )
}

data class AddAudioClipCommand(
    val clip: AudioClip,
    override val label: String = "Add Audio Track",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.AUDIO
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(audioClips = state.timeline.audioClips + clip))

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(audioClips = state.timeline.audioClips.filterNot { it.id == clip.id })
        )
}

// --- 2. Cut / Split Commands ---

data class SplitMainClipCommand(
    val clipIndex: Int,
    val originalClip: MainClip,
    val leftClip: MainClip,
    val rightClip: MainClip,
    override val label: String = "Split Clip",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CUT
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState {
        val list = state.timeline.mainClips.toMutableList()
        val idx = list.indexOfFirst { it.id == originalClip.id }.takeIf { it >= 0 } ?: clipIndex
        if (idx !in list.indices) return state
        list[idx] = leftClip
        list.add(idx + 1, rightClip)
        return state.copy(timeline = state.timeline.copy(mainClips = list))
    }

    override fun undo(state: EditorDocumentState): EditorDocumentState {
        val list = state.timeline.mainClips.toMutableList()
        val leftIdx = list.indexOfFirst { it.id == leftClip.id }
        val rightIdx = list.indexOfFirst { it.id == rightClip.id }
        if (leftIdx >= 0 && rightIdx >= 0) {
            list.removeAt(maxOf(leftIdx, rightIdx))
            list[minOf(leftIdx, rightIdx)] = originalClip
        }
        return state.copy(timeline = state.timeline.copy(mainClips = list))
    }
}

data class SplitOverlayCommand(
    val originalOverlay: OverlayLayer,
    val leftOverlay: OverlayLayer,
    val rightOverlay: OverlayLayer,
    override val label: String = "Split Overlay",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CUT
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState {
        val updated = state.timeline.overlays.flatMap {
            if (it.id == originalOverlay.id) listOf(leftOverlay, rightOverlay) else listOf(it)
        }
        return state.copy(timeline = state.timeline.copy(overlays = updated))
    }

    override fun undo(state: EditorDocumentState): EditorDocumentState {
        val updated = state.timeline.overlays.mapNotNull {
            when (it.id) {
                leftOverlay.id -> originalOverlay
                rightOverlay.id -> null
                else -> it
            }
        }
        return state.copy(timeline = state.timeline.copy(overlays = updated))
    }
}

data class SplitAudioClipCommand(
    val originalClip: AudioClip,
    val leftClip: AudioClip,
    val rightClip: AudioClip,
    override val label: String = "Split Audio",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CUT
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState {
        val updated = state.timeline.audioClips.flatMap {
            if (it.id == originalClip.id) listOf(leftClip, rightClip) else listOf(it)
        }
        return state.copy(timeline = state.timeline.copy(audioClips = updated))
    }

    override fun undo(state: EditorDocumentState): EditorDocumentState {
        val updated = state.timeline.audioClips.mapNotNull {
            when (it.id) {
                leftClip.id -> originalClip
                rightClip.id -> null
                else -> it
            }
        }
        return state.copy(timeline = state.timeline.copy(audioClips = updated))
    }
}

// --- 3. Trim & Timeline Move Commands (Coalescable) ---

data class TrimMainClipCommand(
    val clipId: String,
    val beforeTrimStartMs: Long,
    val beforeTrimEndMs: Long,
    val afterTrimStartMs: Long,
    val afterTrimEndMs: Long,
    override val label: String = "Trim Clip",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.TRIM
    override val mergeKey: String = "trim_main:$clipId"

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.map {
                    if (it.id == clipId) it.copy(trimStartMs = afterTrimStartMs, trimEndMs = afterTrimEndMs) else it
                }
            )
        )

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.map {
                    if (it.id == clipId) it.copy(trimStartMs = beforeTrimStartMs, trimEndMs = beforeTrimEndMs) else it
                }
            )
        )

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is TrimMainClipCommand || newer.clipId != clipId) return null
        return copy(
            afterTrimStartMs = newer.afterTrimStartMs,
            afterTrimEndMs = newer.afterTrimEndMs,
            timestampMs = newer.timestampMs
        )
    }
}

data class TrimOverlayTimingCommand(
    val overlayId: String,
    val beforeStartMs: Long,
    val beforeDurationMs: Long,
    val afterStartMs: Long,
    val afterDurationMs: Long,
    override val label: String = "Adjust Overlay Timing",
    override val category: CommandCategory = CommandCategory.TRIM,
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val mergeKey: String = "trim_overlay:$overlayId"

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                overlays = state.timeline.overlays.map {
                    if (it.id == overlayId) it.copy(startTimeMs = afterStartMs, durationMs = afterDurationMs) else it
                }
            )
        )

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                overlays = state.timeline.overlays.map {
                    if (it.id == overlayId) it.copy(startTimeMs = beforeStartMs, durationMs = beforeDurationMs) else it
                }
            )
        )

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is TrimOverlayTimingCommand || newer.overlayId != overlayId) return null
        return copy(
            afterStartMs = newer.afterStartMs,
            afterDurationMs = newer.afterDurationMs,
            label = newer.label,
            category = newer.category,
            timestampMs = newer.timestampMs
        )
    }
}

data class TrimAudioClipCommand(
    val audioId: String,
    val beforeStartTimeMs: Long,
    val beforeTrimStartMs: Long,
    val beforeTrimEndMs: Long,
    val afterStartTimeMs: Long,
    val afterTrimStartMs: Long,
    val afterTrimEndMs: Long,
    override val label: String = "Trim Audio Clip",
    override val category: CommandCategory = CommandCategory.TRIM,
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val mergeKey: String = "trim_audio:$audioId"

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                audioClips = state.timeline.audioClips.map {
                    if (it.id == audioId) it.copy(
                        startTimeMs = afterStartTimeMs,
                        trimStartMs = afterTrimStartMs,
                        trimEndMs = afterTrimEndMs
                    ) else it
                }
            )
        )

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                audioClips = state.timeline.audioClips.map {
                    if (it.id == audioId) it.copy(
                        startTimeMs = beforeStartTimeMs,
                        trimStartMs = beforeTrimStartMs,
                        trimEndMs = beforeTrimEndMs
                    ) else it
                }
            )
        )

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is TrimAudioClipCommand || newer.audioId != audioId) return null
        return copy(
            afterStartTimeMs = newer.afterStartTimeMs,
            afterTrimStartMs = newer.afterTrimStartMs,
            afterTrimEndMs = newer.afterTrimEndMs,
            label = newer.label,
            category = newer.category,
            timestampMs = newer.timestampMs
        )
    }
}

// --- 4. Reorder, Move, Transform, Duplicate & Delete Commands ---

data class ReorderMainClipCommand(
    val fromIndex: Int,
    val toIndex: Int,
    override val label: String = "Reorder Clip",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.MOVE
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        moveClip(state, fromIndex, toIndex)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        moveClip(state, toIndex, fromIndex)

    private fun moveClip(state: EditorDocumentState, src: Int, dst: Int): EditorDocumentState {
        val list = state.timeline.mainClips.toMutableList()
        if (src !in list.indices || dst !in list.indices) return state
        val item = list.removeAt(src)
        list.add(dst, item)
        return state.copy(timeline = state.timeline.copy(mainClips = list))
    }
}

data class ReorderOverlayZCommand(
    val beforeZMap: Map<String, Int>,
    val afterZMap: Map<String, Int>,
    override val label: String = "Reorder Overlay Layer",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.MOVE
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        applyZ(state, afterZMap)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        applyZ(state, beforeZMap)

    private fun applyZ(state: EditorDocumentState, map: Map<String, Int>): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                overlays = state.timeline.overlays.map { layer ->
                    map[layer.id]?.let { z -> layer.copy(zIndex = z) } ?: layer
                }
            )
        )
}

data class OverlayTransformSnapshot(
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float,
    val rotationDegrees: Float
)

data class TransformOverlayCommand(
    val overlayId: String,
    val before: OverlayTransformSnapshot,
    val after: OverlayTransformSnapshot,
    override val label: String = "Move & Transform Overlay",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.MOVE
    override val mergeKey: String = "transform_overlay:$overlayId"

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        applyTransform(state, after)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        applyTransform(state, before)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is TransformOverlayCommand || newer.overlayId != overlayId) return null
        return copy(after = newer.after, timestampMs = newer.timestampMs)
    }

    private fun applyTransform(state: EditorDocumentState, t: OverlayTransformSnapshot): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                overlays = state.timeline.overlays.map {
                    if (it.id == overlayId) {
                        it.copy(
                            offsetX = t.offsetX,
                            offsetY = t.offsetY,
                            scale = t.scale,
                            rotationDegrees = t.rotationDegrees
                        )
                    } else it
                }
            )
        )
}

data class DuplicateMainClipCommand(
    val insertIndex: Int,
    val duplicatedClip: MainClip,
    override val label: String = "Duplicate Clip",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.MEDIA
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState {
        val list = state.timeline.mainClips.toMutableList()
        list.add(insertIndex.coerceIn(0, list.size), duplicatedClip)
        return state.copy(timeline = state.timeline.copy(mainClips = list))
    }

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.filterNot { it.id == duplicatedClip.id }
            )
        )
}

data class DeleteMainClipCommand(
    val removedIndex: Int,
    val removedClip: MainClip,
    override val label: String = "Delete Clip",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CUT
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.filterNot { it.id == removedClip.id }
            )
        )

    override fun undo(state: EditorDocumentState): EditorDocumentState {
        val list = state.timeline.mainClips.toMutableList()
        list.add(removedIndex.coerceIn(0, list.size), removedClip)
        return state.copy(timeline = state.timeline.copy(mainClips = list))
    }
}

data class DeleteOverlayCommand(
    val removedIndex: Int,
    val removedOverlay: OverlayLayer,
    override val label: String = "Delete Overlay",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CUT
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                overlays = state.timeline.overlays.filterNot { it.id == removedOverlay.id }
            )
        )

    override fun undo(state: EditorDocumentState): EditorDocumentState {
        val list = state.timeline.overlays.toMutableList()
        list.add(removedIndex.coerceIn(0, list.size), removedOverlay)
        return state.copy(timeline = state.timeline.copy(overlays = list))
    }
}

data class DeleteAudioClipCommand(
    val removedIndex: Int,
    val removedClip: AudioClip,
    override val label: String = "Delete Audio Track",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.AUDIO
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                audioClips = state.timeline.audioClips.filterNot { it.id == removedClip.id }
            )
        )

    override fun undo(state: EditorDocumentState): EditorDocumentState {
        val list = state.timeline.audioClips.toMutableList()
        list.add(removedIndex.coerceIn(0, list.size), removedClip)
        return state.copy(timeline = state.timeline.copy(audioClips = list))
    }
}

// --- 5. Property Change Commands (Filters, Color Grading, Transitions, Audio, Styles, Canvas) ---

data class FilterChangeCommand(
    val beforeByClipId: Map<String, Pair<FilterPreset, Float>>,
    val afterByClipId: Map<String, Pair<FilterPreset, Float>>,
    override val label: String,
    override val mergeKey: String? = null,
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.FILTER

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        applyFilters(state, afterByClipId)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        applyFilters(state, beforeByClipId)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is FilterChangeCommand || mergeKey == null || newer.mergeKey != mergeKey) return null
        return copy(
            afterByClipId = newer.afterByClipId,
            label = newer.label,
            timestampMs = newer.timestampMs
        )
    }

    private fun applyFilters(
        state: EditorDocumentState,
        map: Map<String, Pair<FilterPreset, Float>>
    ): EditorDocumentState = state.copy(
        timeline = state.timeline.copy(
            mainClips = state.timeline.mainClips.map { clip ->
                val spec = map[clip.id]
                if (spec != null) clip.copy(filterPreset = spec.first, filterIntensity = spec.second) else clip
            }
        )
    )
}

data class ColorGradeCommand(
    val beforeByClipId: Map<String, ColorAdjustments>,
    val afterByClipId: Map<String, ColorAdjustments>,
    override val label: String = "Adjust Color Grade",
    override val mergeKey: String? = null,
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.COLOR

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        applyAdjustments(state, afterByClipId)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        applyAdjustments(state, beforeByClipId)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is ColorGradeCommand || mergeKey == null || newer.mergeKey != mergeKey) return null
        return copy(
            afterByClipId = newer.afterByClipId,
            label = newer.label,
            timestampMs = newer.timestampMs
        )
    }

    private fun applyAdjustments(
        state: EditorDocumentState,
        map: Map<String, ColorAdjustments>
    ): EditorDocumentState = state.copy(
        timeline = state.timeline.copy(
            mainClips = state.timeline.mainClips.map { clip ->
                map[clip.id]?.let { adj -> clip.copy(colorAdjustments = adj) } ?: clip
            }
        )
    )
}

data class TransitionChangeCommand(
    val afterClipId: String,
    val beforeSpec: TransitionSpec,
    val afterSpec: TransitionSpec,
    override val label: String,
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.TRANSITION
    override val mergeKey: String = "transition:$afterClipId"

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        applySpec(state, afterSpec)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        applySpec(state, beforeSpec)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is TransitionChangeCommand || newer.afterClipId != afterClipId) return null
        return copy(afterSpec = newer.afterSpec, label = newer.label, timestampMs = newer.timestampMs)
    }

    private fun applySpec(state: EditorDocumentState, spec: TransitionSpec): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.map {
                    if (it.id == afterClipId) it.copy(transitionToNext = spec) else it
                }
            )
        )
}

data class BatchTransitionChangeCommand(
    val beforeByClipId: Map<String, TransitionSpec>,
    val afterByClipId: Map<String, TransitionSpec>,
    override val label: String,
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.TRANSITION
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        applySpecs(state, afterByClipId)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        applySpecs(state, beforeByClipId)

    private fun applySpecs(
        state: EditorDocumentState,
        map: Map<String, TransitionSpec>
    ): EditorDocumentState = state.copy(
        timeline = state.timeline.copy(
            mainClips = state.timeline.mainClips.map { clip ->
                map[clip.id]?.let { spec -> clip.copy(transitionToNext = spec) } ?: clip
            }
        )
    )
}

data class AudioSettingsCommand(
    val targetId: String,
    val isAudioTrack: Boolean,
    val before: AudioSettings,
    val after: AudioSettings,
    override val label: String = "Adjust Audio Mixer",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.AUDIO
    override val mergeKey: String = "audio_${if (isAudioTrack) "track" else "clip"}:$targetId"

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        applyAudio(state, after)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        applyAudio(state, before)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is AudioSettingsCommand ||
            newer.targetId != targetId ||
            newer.isAudioTrack != isAudioTrack
        ) return null
        return copy(after = newer.after, label = newer.label, timestampMs = newer.timestampMs)
    }

    private fun applyAudio(state: EditorDocumentState, settings: AudioSettings): EditorDocumentState =
        if (isAudioTrack) {
            state.copy(
                timeline = state.timeline.copy(
                    audioClips = state.timeline.audioClips.map {
                        if (it.id == targetId) it.copy(audio = settings) else it
                    }
                )
            )
        } else {
            state.copy(
                timeline = state.timeline.copy(
                    mainClips = state.timeline.mainClips.map {
                        if (it.id == targetId) it.copy(audio = settings) else it
                    }
                )
            )
        }
}

data class OverlayPropertyCommand(
    val overlayId: String,
    val before: OverlayLayer,
    val after: OverlayLayer,
    override val label: String = "Edit Overlay Style",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.STYLE
    override val mergeKey: String = "overlay_style:$overlayId"

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        replaceOverlay(state, after)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        replaceOverlay(state, before)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is OverlayPropertyCommand || newer.overlayId != overlayId) return null
        return copy(after = newer.after, label = newer.label, timestampMs = newer.timestampMs)
    }

    private fun replaceOverlay(state: EditorDocumentState, target: OverlayLayer): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                overlays = state.timeline.overlays.map { if (it.id == overlayId) target else it }
            )
        )
}

data class AspectRatioCommand(
    val before: AspectRatio,
    val after: AspectRatio,
    override val label: String = "Canvas ${after.label}",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CANVAS
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(aspectRatio = after)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(aspectRatio = before)
}

data class ProjectTitleCommand(
    val before: String,
    val after: String,
    override val label: String = "Rename Project",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CANVAS
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(projectTitle = after)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(projectTitle = before)
}

data class ClipKeyframesCommand(
    val clipId: String,
    val beforeBaseTransform: ClipTransform,
    val beforeKeyframes: List<TransformKeyframe>,
    val afterBaseTransform: ClipTransform,
    val afterKeyframes: List<TransformKeyframe>,
    override val label: String = "Edit Keyframes",
    override val mergeKey: String? = "keyframes:$clipId",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.MOTION

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        applyMotion(state, afterBaseTransform, afterKeyframes)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        applyMotion(state, beforeBaseTransform, beforeKeyframes)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is ClipKeyframesCommand || mergeKey == null || newer.mergeKey != mergeKey) return null
        return copy(
            afterBaseTransform = newer.afterBaseTransform,
            afterKeyframes = newer.afterKeyframes,
            label = newer.label,
            timestampMs = newer.timestampMs
        )
    }

    private fun applyMotion(
        state: EditorDocumentState,
        transform: ClipTransform,
        keyframes: List<TransformKeyframe>
    ): EditorDocumentState = state.copy(
        timeline = state.timeline.copy(
            mainClips = state.timeline.mainClips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(baseTransform = transform, keyframes = keyframes)
                } else clip
            }
        )
    )
}

data class ClipSpeedSnapshot(
    val playbackSpeed: Float,
    val pitchShiftSemitones: Float,
    val preservePitch: Boolean
)

data class ClipSpeedCommand(
    val clipId: String,
    val before: ClipSpeedSnapshot,
    val after: ClipSpeedSnapshot,
    override val label: String = "Adjust Clip Speed",
    override val mergeKey: String? = "speed:$clipId",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.SPEED

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        applySpeed(state, after)

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        applySpeed(state, before)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is ClipSpeedCommand || mergeKey == null || newer.mergeKey != mergeKey) return null
        return copy(
            after = newer.after,
            label = newer.label,
            timestampMs = newer.timestampMs
        )
    }

    private fun applySpeed(
        state: EditorDocumentState,
        snap: ClipSpeedSnapshot
    ): EditorDocumentState = state.copy(
        timeline = state.timeline.copy(
            mainClips = state.timeline.mainClips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(
                        playbackSpeed = snap.playbackSpeed,
                        pitchShiftSemitones = snap.pitchShiftSemitones,
                        preservePitch = snap.preservePitch
                    )
                } else clip
            }
        )
    )
}

data class MainClipOpticalFlowCommand(
    val clipId: String,
    val before: Boolean,
    val after: Boolean,
    override val label: String = if (after) "Enable Optical Flow" else "Disable Optical Flow",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.SPEED
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState = apply(state, after)
    override fun undo(state: EditorDocumentState): EditorDocumentState = apply(state, before)

    private fun apply(state: EditorDocumentState, enabled: Boolean): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.map {
                    if (it.id == clipId) it.copy(opticalFlowSlowMo = enabled) else it
                }
            )
        )
}

data class MainClipCutoutCommand(
    val clipId: String,
    val before: AiCutoutSettings,
    val after: AiCutoutSettings,
    override val label: String = "Update AI Cutout",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CUTOUT
    override val mergeKey: String = "main_cutout:$clipId"

    override fun execute(state: EditorDocumentState): EditorDocumentState = apply(state, after)
    override fun undo(state: EditorDocumentState): EditorDocumentState = apply(state, before)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is MainClipCutoutCommand || newer.clipId != clipId) return null
        return copy(after = newer.after, label = newer.label, timestampMs = newer.timestampMs)
    }

    private fun apply(state: EditorDocumentState, cutout: AiCutoutSettings): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.map {
                    if (it.id == clipId) it.copy(aiCutout = cutout) else it
                }
            )
        )
}

data class MainClipMaskCommand(
    val clipId: String,
    val before: MaskSettings,
    val after: MaskSettings,
    override val label: String = "Edit Clip Mask",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.MASK
    override val mergeKey: String = "main_mask:$clipId"

    override fun execute(state: EditorDocumentState): EditorDocumentState = apply(state, after)
    override fun undo(state: EditorDocumentState): EditorDocumentState = apply(state, before)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is MainClipMaskCommand || newer.clipId != clipId) return null
        return copy(after = newer.after, label = newer.label, timestampMs = newer.timestampMs)
    }

    private fun apply(state: EditorDocumentState, mask: MaskSettings): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.map {
                    if (it.id == clipId) it.copy(mask = mask) else it
                }
            )
        )
}

data class AddCaptionsCommand(
    val addedCaptions: List<CaptionClip>,
    override val label: String = "Add Captions",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CAPTIONS
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                captions = (state.timeline.captions + addedCaptions).sortedBy { it.startTimeMs }
            )
        )

    override fun undo(state: EditorDocumentState): EditorDocumentState {
        val ids = addedCaptions.map { it.id }.toSet()
        return state.copy(
            timeline = state.timeline.copy(
                captions = state.timeline.captions.filterNot { it.id in ids }
            )
        )
    }
}

data class UpdateCaptionCommand(
    val captionId: String,
    val before: CaptionClip,
    val after: CaptionClip,
    override val label: String = "Edit Caption",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CAPTIONS
    override val mergeKey: String = "caption_edit:$captionId"

    override fun execute(state: EditorDocumentState): EditorDocumentState = apply(state, after)
    override fun undo(state: EditorDocumentState): EditorDocumentState = apply(state, before)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is UpdateCaptionCommand || newer.captionId != captionId) return null
        return copy(after = newer.after, label = newer.label, timestampMs = newer.timestampMs)
    }

    private fun apply(state: EditorDocumentState, target: CaptionClip): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                captions = state.timeline.captions.map { if (it.id == captionId) target else it }
            )
        )
}

data class DeleteCaptionCommand(
    val removedCaption: CaptionClip,
    override val label: String = "Delete Caption",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CAPTIONS
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                captions = state.timeline.captions.filterNot { it.id == removedCaption.id }
            )
        )

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                captions = (state.timeline.captions + removedCaption).sortedBy { it.startTimeMs }
            )
        )
}

data class ReplaceMainClipsCommand(
    val beforeClips: List<MainClip>,
    val afterClips: List<MainClip>,
    override val label: String,
    override val category: CommandCategory = CommandCategory.SPEED,
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(mainClips = afterClips))

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(mainClips = beforeClips))
}

data class ExtractAudioCommand(
    val sourceClipId: String,
    val beforeClipAudio: AudioSettings,
    val extractedAudioClip: AudioClip,
    override val label: String = "Extract Audio",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.AUDIO
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.map {
                    if (it.id == sourceClipId) it.copy(audio = beforeClipAudio.copy(isMuted = true)) else it
                },
                audioClips = state.timeline.audioClips + extractedAudioClip
            )
        )

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.map {
                    if (it.id == sourceClipId) it.copy(audio = beforeClipAudio) else it
                },
                audioClips = state.timeline.audioClips.filterNot { it.id == extractedAudioClip.id }
            )
        )
}

data class CanvasBackdropCommand(
    val before: CanvasBackdrop,
    val after: CanvasBackdrop,
    override val label: String = "Canvas Backdrop",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CANVAS
    override val mergeKey: String = "canvas_backdrop"

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(canvasBackdrop = after))

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(canvasBackdrop = before))

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is CanvasBackdropCommand) return null
        return copy(after = newer.after, timestampMs = newer.timestampMs)
    }
}

data class SafeZoneOverlayCommand(
    val before: SafeZoneOverlay,
    val after: SafeZoneOverlay,
    override val label: String = "Safe Zone: ${after.label}",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.CANVAS
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(safeZoneOverlay = after))

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(safeZoneOverlay = before))
}

data class AddFxLayerCommand(
    val fxLayer: FxLayer,
    override val label: String = "Add ${fxLayer.type.displayName} FX",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.FX
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(fxLayers = state.timeline.fxLayers + fxLayer))

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(fxLayers = state.timeline.fxLayers.filterNot { it.id == fxLayer.id }))
}

data class UpdateFxLayerCommand(
    val fxId: String,
    val before: FxLayer,
    val after: FxLayer,
    override val label: String = "Edit ${after.type.displayName} FX",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.FX
    override val mergeKey: String = "fx_edit:$fxId"

    override fun execute(state: EditorDocumentState): EditorDocumentState = apply(state, after)
    override fun undo(state: EditorDocumentState): EditorDocumentState = apply(state, before)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is UpdateFxLayerCommand || newer.fxId != fxId) return null
        return copy(after = newer.after, label = newer.label, timestampMs = newer.timestampMs)
    }

    private fun apply(state: EditorDocumentState, target: FxLayer): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                fxLayers = state.timeline.fxLayers.map { if (it.id == fxId) target else it }
            )
        )
}

data class DeleteFxLayerCommand(
    val removedFx: FxLayer,
    override val label: String = "Delete ${removedFx.type.displayName} FX",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.FX
    override val mergeKey: String? = null

    override fun execute(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(fxLayers = state.timeline.fxLayers.filterNot { it.id == removedFx.id }))

    override fun undo(state: EditorDocumentState): EditorDocumentState =
        state.copy(timeline = state.timeline.copy(fxLayers = (state.timeline.fxLayers + removedFx).sortedBy { it.startTimeMs }))
}

data class MainClipPanZoomCommand(
    val clipId: String,
    val before: PanZoomSpec,
    val after: PanZoomSpec,
    override val label: String = "Edit Pan & Zoom",
    override val timestampMs: Long = System.currentTimeMillis()
) : EditCommand {
    override val category: CommandCategory = CommandCategory.MOTION
    override val mergeKey: String = "pan_zoom:$clipId"

    override fun execute(state: EditorDocumentState): EditorDocumentState = apply(state, after)
    override fun undo(state: EditorDocumentState): EditorDocumentState = apply(state, before)

    override fun mergeWith(newer: EditCommand): EditCommand? {
        if (newer !is MainClipPanZoomCommand || newer.clipId != clipId) return null
        return copy(after = newer.after, label = newer.label, timestampMs = newer.timestampMs)
    }

    private fun apply(state: EditorDocumentState, spec: PanZoomSpec): EditorDocumentState =
        state.copy(
            timeline = state.timeline.copy(
                mainClips = state.timeline.mainClips.map {
                    if (it.id == clipId) it.copy(panZoom = spec) else it
                }
            )
        )
}



