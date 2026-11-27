package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.ai.AiCreativeIdeasResult
import com.example.ai.GeminiColoristService
import com.example.data.StudioRepository
import com.example.history.AddAudioClipCommand
import com.example.history.AddCaptionsCommand
import com.example.history.AddFxLayerCommand
import com.example.history.AddMainClipsCommand
import com.example.history.AddOverlayCommand
import com.example.history.AspectRatioCommand
import com.example.history.AudioSettingsCommand
import com.example.history.BatchTransitionChangeCommand
import com.example.history.CanvasBackdropCommand
import com.example.history.ClipKeyframesCommand
import com.example.history.ClipSpeedCommand
import com.example.history.ClipSpeedSnapshot
import com.example.history.ColorGradeCommand
import com.example.history.CommandCategory
import com.example.history.DeleteAudioClipCommand
import com.example.history.DeleteCaptionCommand
import com.example.history.DeleteFxLayerCommand
import com.example.history.DeleteMainClipCommand
import com.example.history.DeleteOverlayCommand
import com.example.history.DuplicateMainClipCommand
import com.example.history.EditCommand
import com.example.history.EditHistoryManager
import com.example.history.EditorDocumentState
import com.example.history.ExtractAudioCommand
import com.example.history.FilterChangeCommand
import com.example.history.HistorySnapshotUi
import com.example.history.MainClipCutoutCommand
import com.example.history.MainClipMaskCommand
import com.example.history.MainClipPanZoomCommand
import com.example.history.OverlayPropertyCommand
import com.example.history.OverlayTransformSnapshot
import com.example.history.ProjectTitleCommand
import com.example.history.ReorderMainClipCommand
import com.example.history.ReorderOverlayZCommand
import com.example.history.ReplaceMainClipsCommand
import com.example.history.SafeZoneOverlayCommand
import com.example.history.SplitAudioClipCommand
import com.example.history.SplitMainClipCommand
import com.example.history.SplitOverlayCommand
import com.example.history.TransformOverlayCommand
import com.example.history.TransitionChangeCommand
import com.example.history.TrimAudioClipCommand
import com.example.history.TrimMainClipCommand
import com.example.history.TrimOverlayTimingCommand
import com.example.history.UpdateCaptionCommand
import com.example.history.UpdateFxLayerCommand
import com.example.media.AudioClipExtractor
import com.example.media.AudioWaveformExtractor
import com.example.media.CinemaSfxPreset
import com.example.media.FontResolver
import com.example.media.Media3ExportEngine
import com.example.media.OfflineSpeechCaptionEngine
import com.example.media.SpeedRampPreset
import com.example.media.SrtCodec
import com.example.media.StudioCreativeTools
import com.example.media.VoiceoverRecorder
import com.example.model.AiCutoutSettings
import com.example.model.AspectRatio
import com.example.model.AudioClip
import com.example.model.AudioSettings
import com.example.model.CanvasBackdrop
import com.example.model.CaptionAnimationPreset
import com.example.model.CaptionClip
import com.example.model.ClipTransform
import com.example.model.ColorAdjustments
import com.example.model.CropRect
import com.example.model.CutoutBackgroundMode
import com.example.model.ExportConfig
import com.example.model.ExportState
import com.example.model.FilterPreset
import com.example.model.FxLayer
import com.example.model.FxShape
import com.example.model.FxType
import com.example.model.KeyframeEasing
import com.example.model.KeyframeEvaluator
import com.example.model.LowerThirdTemplate
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.MaskKeyframe
import com.example.model.MaskSettings
import com.example.model.OverlayBlendMode
import com.example.model.OverlayLayer
import com.example.model.OverlayType
import com.example.model.PanZoomSpec
import com.example.model.SafeZoneOverlay
import com.example.model.StudioFont
import com.example.model.TextMotionPreset
import com.example.model.TimelineDocument
import com.example.model.TransformKeyframe
import com.example.model.TransitionSpec
import com.example.model.TransitionType
import com.example.model.formatTimecode
import com.example.ui.components.MotionPreset
import com.example.ui.components.VideoScopeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.abs

sealed interface SelectedItem {
    data object None : SelectedItem
    data class MainClipItem(val clipId: String) : SelectedItem
    data class OverlayItem(val overlayId: String) : SelectedItem
    data class AudioItem(val audioId: String) : SelectedItem
    data class TransitionItem(val afterClipId: String) : SelectedItem
    data class CaptionItem(val captionId: String) : SelectedItem
    data class FxItem(val fxId: String) : SelectedItem
}

enum class InspectorTab {
    NONE,
    FILTERS,
    ADJUST,
    AUDIO,
    TRANSITION,
    KEYFRAMES,
    SPEED,
    MASK,
    VISUAL_FX,
    TRANSFORM_CROP,
    CUTOUT,
    CAPTIONS,
    TEMPLATES,
    AI_STUDIO,
    TEXT_STYLE,
    IMAGE_OVERLAY,
    VIDEO_OVERLAY,
    CANVAS,
    FONTS
}

data class EditorUiState(
    val isLoading: Boolean = true,
    val projectId: String = "",
    val projectTitle: String = "Untitled Edit",
    val aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val timeline: TimelineDocument = TimelineDocument(),
    val playheadMs: Long = 0L,
    val seekNonce: Long = 0L,
    val isPlaying: Boolean = false,
    val timelineZoom: Float = 1f,
    val isTimelineExpanded: Boolean = false,
    val selection: SelectedItem = SelectedItem.None,
    val activeInspector: InspectorTab = InspectorTab.NONE,
    val history: HistorySnapshotUi = HistorySnapshotUi(),
    val showHistorySheet: Boolean = false,
    val isImporting: Boolean = false,
    val snapGuideX: Boolean = false,
    val snapGuideY: Boolean = false,
    val textMotionReplayNonce: Int = 0,
    val showProCamera: Boolean = false,
    val showVideoScopes: Boolean = false,
    val scopeMode: VideoScopeMode = VideoScopeMode.WAVEFORM,
    val isSamplingChromaColor: Boolean = false,
    val isRecordingVoiceover: Boolean = false,
    val voiceoverLevel: Float = 0f,
    val voiceoverElapsedMs: Long = 0L,
    val showExportSheet: Boolean = false,
    val exportConfig: ExportConfig = ExportConfig(),
    val exportState: ExportState = ExportState.Idle,
    val aiIdeasLoading: Boolean = false,
    val aiIdeasResult: AiCreativeIdeasResult? = null,
    val aiIdeasError: String? = null,
    val aiColoristLoading: Boolean = false,
    val aiColoristNote: String? = null,
    val aiColoristError: String? = null,
    val aiCaptionsLoading: Boolean = false,
    val aiCaptionsError: String? = null,
    val autoCaptionLoading: Boolean = false,
    val autoCaptionStatus: String? = null,
    val autoCaptionError: String? = null,
    val isLiveMicDictating: Boolean = false,
    val liveMicPartialText: String = ""
) {
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    val selectedMainClip: MainClip?
        get() = (selection as? SelectedItem.MainClipItem)?.let { sel ->
            timeline.mainClips.firstOrNull { it.id == sel.clipId }
        }

    val selectedOverlay: OverlayLayer?
        get() = (selection as? SelectedItem.OverlayItem)?.let { sel ->
            timeline.overlays.firstOrNull { it.id == sel.overlayId }
        }

    val selectedAudioClip: AudioClip?
        get() = (selection as? SelectedItem.AudioItem)?.let { sel ->
            timeline.audioClips.firstOrNull { it.id == sel.audioId }
        }

    val selectedTransitionClip: MainClip?
        get() = (selection as? SelectedItem.TransitionItem)?.let { sel ->
            timeline.mainClips.firstOrNull { it.id == sel.afterClipId }
        }

    val selectedCaption: CaptionClip?
        get() = (selection as? SelectedItem.CaptionItem)?.let { sel ->
            timeline.captions.firstOrNull { it.id == sel.captionId }
        }

    val selectedFxLayer: FxLayer?
        get() = (selection as? SelectedItem.FxItem)?.let { sel ->
            timeline.fxLayers.firstOrNull { it.id == sel.fxId }
        }

    fun toDocumentState(): EditorDocumentState = EditorDocumentState(
        projectTitle = projectTitle,
        aspectRatio = aspectRatio,
        timeline = timeline
    )
}

@OptIn(UnstableApi::class)
class EditorViewModel(
    appContext: Context,
    private val projectId: String,
    private val repository: StudioRepository
) : ViewModel() {

    private val appContext: Context = appContext.applicationContext
    private val exportEngine = Media3ExportEngine(this.appContext)
    private val voiceoverRecorder = VoiceoverRecorder(this.appContext)
    private val offlineSpeechEngine = OfflineSpeechCaptionEngine(this.appContext)
    private val historyManager = EditHistoryManager(maxCapacity = 60, coalesceWindowMs = 900L)

    private val _uiState = MutableStateFlow(EditorUiState(projectId = projectId))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val availableFonts: StateFlow<List<StudioFont>> = repository.allFonts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val feedback: SharedFlow<String> = _feedback.asSharedFlow()

    private var activeGestureOverlayId: String? = null
    private var activeGestureBeforeTransform: OverlayTransformSnapshot? = null
    private var playbackJob: Job? = null
    private var autosaveJob: Job? = null
    private var voiceoverMeterJob: Job? = null

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            val loaded = repository.loadProject(projectId)
            if (loaded != null) {
                val cleanHistory = historyManager.clear()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        projectId = loaded.summary.id,
                        projectTitle = loaded.summary.title,
                        aspectRatio = loaded.summary.aspectRatio,
                        timeline = loaded.timeline,
                        history = cleanHistory
                    )
                }
            } else {
                val created = repository.createProject("New Cinema Edit", AspectRatio.PORTRAIT_9_16)
                val cleanHistory = historyManager.clear()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        projectId = created.id,
                        projectTitle = created.title,
                        aspectRatio = created.aspectRatio,
                        history = cleanHistory
                    )
                }
            }
        }
    }

    // --- Command Dispatch & History Controls ---

    private fun dispatchCommand(
        command: EditCommand,
        newSelection: SelectedItem? = null,
        newInspector: InspectorTab? = null
    ): Boolean {
        val current = _uiState.value
        val result = historyManager.execute(command, current.toDocumentState()) ?: return false
        val clampedPlayhead = current.playheadMs.coerceIn(0L, result.newState.timeline.totalDurationMs.coerceAtLeast(0L))
        _uiState.update {
            it.copy(
                projectTitle = result.newState.projectTitle,
                aspectRatio = result.newState.aspectRatio,
                timeline = result.newState.timeline,
                playheadMs = clampedPlayhead,
                selection = newSelection ?: it.selection,
                activeInspector = newInspector ?: it.activeInspector,
                history = result.summary
            )
        }
        scheduleAutosave()
        return true
    }

    fun undo() {
        pausePlayback()
        val current = _uiState.value
        val result = historyManager.undo(current.toDocumentState()) ?: return
        applyHistoryJumpState(result.newState, result.summary)
        _feedback.tryEmit("Undid: ${result.commandLabel}")
    }

    fun redo() {
        pausePlayback()
        val current = _uiState.value
        val result = historyManager.redo(current.toDocumentState()) ?: return
        applyHistoryJumpState(result.newState, result.summary)
        _feedback.tryEmit("Redid: ${result.commandLabel}")
    }

    fun jumpToHistoryIndex(targetAppliedCount: Int) {
        pausePlayback()
        val current = _uiState.value
        val result = historyManager.jumpTo(targetAppliedCount, current.toDocumentState()) ?: return
        applyHistoryJumpState(result.newState, result.summary)
        _feedback.tryEmit("Restored: ${result.commandLabel}")
    }

    fun clearHistory() {
        val summary = historyManager.clear()
        _uiState.update { it.copy(history = summary) }
        _feedback.tryEmit("History cleared")
    }

    fun openHistorySheet() {
        pausePlayback()
        _uiState.update { it.copy(showHistorySheet = true) }
    }

    fun closeHistorySheet() {
        _uiState.update { it.copy(showHistorySheet = false) }
    }

    private fun applyHistoryJumpState(
        docState: EditorDocumentState,
        summary: HistorySnapshotUi
    ) {
        _uiState.update { state ->
            val validatedSelection = validateSelection(state.selection, docState.timeline)
            val validatedInspector = if (validatedSelection is SelectedItem.None &&
                state.activeInspector in listOf(
                    InspectorTab.TEXT_STYLE,
                    InspectorTab.IMAGE_OVERLAY,
                    InspectorTab.VIDEO_OVERLAY,
                    InspectorTab.TRANSITION
                )
            ) {
                InspectorTab.NONE
            } else {
                state.activeInspector
            }
            state.copy(
                projectTitle = docState.projectTitle,
                aspectRatio = docState.aspectRatio,
                timeline = docState.timeline,
                playheadMs = state.playheadMs.coerceIn(0L, docState.timeline.totalDurationMs.coerceAtLeast(0L)),
                selection = validatedSelection,
                activeInspector = validatedInspector,
                history = summary
            )
        }
        scheduleAutosave()
    }

    private fun validateSelection(selection: SelectedItem, timeline: TimelineDocument): SelectedItem =
        when (selection) {
            is SelectedItem.MainClipItem ->
                if (timeline.mainClips.any { it.id == selection.clipId }) selection else SelectedItem.None
            is SelectedItem.OverlayItem ->
                if (timeline.overlays.any { it.id == selection.overlayId }) selection else SelectedItem.None
            is SelectedItem.AudioItem ->
                if (timeline.audioClips.any { it.id == selection.audioId }) selection else SelectedItem.None
            is SelectedItem.TransitionItem ->
                if (timeline.mainClips.any { it.id == selection.afterClipId }) selection else SelectedItem.None
            is SelectedItem.CaptionItem ->
                if (timeline.captions.any { it.id == selection.captionId }) selection else SelectedItem.None
            is SelectedItem.FxItem ->
                if (timeline.fxLayers.any { it.id == selection.fxId }) selection else SelectedItem.None
            SelectedItem.None -> SelectedItem.None
        }

    // --- Playback Clock ---

    fun togglePlayPause() {
        val state = _uiState.value
        val total = state.timeline.totalDurationMs
        if (total <= 0L) {
            _feedback.tryEmit("Import a video, photo, or audio clip to preview")
            return
        }
        if (state.isPlaying) {
            pausePlayback()
        } else {
            val wrapped = state.playheadMs >= total - 50L
            val startFrom = if (wrapped) 0L else state.playheadMs
            _uiState.update {
                it.copy(
                    isPlaying = true,
                    playheadMs = startFrom,
                    seekNonce = if (wrapped) it.seekNonce + 1L else it.seekNonce
                )
            }
            startPlaybackTicker()
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    private fun startPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastWallMs = android.os.SystemClock.elapsedRealtime()
            while (isActive && _uiState.value.isPlaying) {
                delay(16L)
                val now = android.os.SystemClock.elapsedRealtime()
                val delta = (now - lastWallMs).coerceIn(1L, 80L)
                lastWallMs = now
                val total = _uiState.value.timeline.totalDurationMs
                if (total <= 0L) {
                    _uiState.update { it.copy(isPlaying = false, playheadMs = 0L) }
                    break
                }
                val nextMs = _uiState.value.playheadMs + delta
                if (nextMs >= total) {
                    _uiState.update { it.copy(isPlaying = false, playheadMs = total) }
                    break
                } else {
                    _uiState.update { it.copy(playheadMs = nextMs) }
                }
            }
        }
    }

    fun seekTo(ms: Long) {
        val total = _uiState.value.timeline.totalDurationMs.coerceAtLeast(0L)
        val clamped = ms.coerceIn(0L, total)
        _uiState.update {
            it.copy(
                playheadMs = clamped,
                seekNonce = it.seekNonce + 1L
            )
        }
    }

    fun scrubTo(ms: Long) {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        }
        seekTo(ms)
    }

    fun stepFrame(deltaMs: Long) {
        pausePlayback()
        seekTo(_uiState.value.playheadMs + deltaMs)
    }

    fun snapPlayheadToBeat(next: Boolean) {
        pausePlayback()
        val state = _uiState.value
        val beats = state.timeline.audioClips
            .flatMap { it.timelineBeatPositionsMs() }
            .distinct()
            .sorted()
        if (beats.isEmpty()) {
            _feedback.tryEmit("Import an audio track to snap to beats")
            return
        }
        val toleranceMs = 40L
        val target = if (next) {
            beats.firstOrNull { it > state.playheadMs + toleranceMs }
        } else {
            beats.lastOrNull { it < state.playheadMs - toleranceMs }
        }
        if (target != null) {
            seekTo(target)
            _feedback.tryEmit("Snapped to beat ${formatTimecode(target)}")
        } else {
            _feedback.tryEmit(if (next) "At last detected beat" else "At first detected beat")
        }
    }

    fun setTimelineZoom(zoom: Float) {
        _uiState.update { it.copy(timelineZoom = zoom.coerceIn(0.45f, 3.0f)) }
    }

    fun toggleTimelineExpanded() {
        _uiState.update { it.copy(isTimelineExpanded = !it.isTimelineExpanded) }
    }

    // --- Selection & Inspector ---

    fun selectItem(selection: SelectedItem, openInspector: InspectorTab? = null) {
        historyManager.breakCoalescing()
        _uiState.update { current ->
            val resolvedInspector = openInspector ?: when (selection) {
                is SelectedItem.TransitionItem -> InspectorTab.TRANSITION
                is SelectedItem.CaptionItem -> InspectorTab.CAPTIONS
                is SelectedItem.FxItem -> InspectorTab.VISUAL_FX
                is SelectedItem.AudioItem -> if (current.activeInspector == InspectorTab.AUDIO) InspectorTab.AUDIO else InspectorTab.NONE
                is SelectedItem.OverlayItem -> {
                    val overlay = current.timeline.overlays.firstOrNull { it.id == selection.overlayId }
                    if (current.activeInspector != InspectorTab.NONE) {
                        when (overlay?.type) {
                            OverlayType.TEXT -> InspectorTab.TEXT_STYLE
                            OverlayType.VIDEO -> InspectorTab.VIDEO_OVERLAY
                            else -> InspectorTab.IMAGE_OVERLAY
                        }
                    } else InspectorTab.NONE
                }
                else -> if (current.activeInspector == InspectorTab.TRANSITION) InspectorTab.NONE else current.activeInspector
            }
            current.copy(selection = selection, activeInspector = resolvedInspector)
        }
    }

    fun openInspector(tab: InspectorTab) {
        historyManager.breakCoalescing()
        _uiState.update { current ->
            var sel = current.selection
            if (sel is SelectedItem.None && tab in listOf(
                    InspectorTab.FILTERS,
                    InspectorTab.ADJUST,
                    InspectorTab.AUDIO,
                    InspectorTab.KEYFRAMES,
                    InspectorTab.SPEED,
                    InspectorTab.MASK,
                    InspectorTab.TRANSFORM_CROP,
                    InspectorTab.CUTOUT
                )
            ) {
                val underPlayhead = current.timeline.mainClipAt(current.playheadMs)
                if (underPlayhead != null) {
                    sel = SelectedItem.MainClipItem(underPlayhead.clip.id)
                }
            }
            current.copy(
                selection = sel,
                activeInspector = if (current.activeInspector == tab) InspectorTab.NONE else tab
            )
        }
    }

    fun closeInspector() {
        historyManager.breakCoalescing()
        _uiState.update { it.copy(activeInspector = InspectorTab.NONE, isSamplingChromaColor = false) }
    }

    // --- Pro Camera, Video Scopes, .cube 3D LUT, Chroma Eyedropper & Fairlight Voiceover ---

    fun openProCamera() {
        pausePlayback()
        if (_uiState.value.isRecordingVoiceover) stopVoiceoverRecording()
        _uiState.update { it.copy(showProCamera = true, isSamplingChromaColor = false) }
    }

    fun closeProCamera() {
        _uiState.update { it.copy(showProCamera = false) }
    }

    fun createProCameraOutputFile(): File = repository.mediaStorage.createProCameraOutputFile()

    fun importProCameraCapture(file: File, liveLut: FilterPreset) {
        _uiState.update { it.copy(showProCamera = false, isImporting = true) }
        viewModelScope.launch {
            val result = repository.mediaStorage.importVideoFromUri(Uri.fromFile(file))
            _uiState.update { it.copy(isImporting = false) }
            result.onSuccess { meta ->
                val clip = MainClip(
                    uri = meta.uriString,
                    displayName = meta.displayName,
                    mediaType = MainMediaType.VIDEO,
                    sourceDurationMs = meta.durationMs,
                    trimStartMs = 0L,
                    trimEndMs = meta.durationMs,
                    thumbnailPath = meta.thumbnailPath,
                    filterPreset = liveLut,
                    filterIntensity = if (liveLut == FilterPreset.ORIGINAL) 1f else 0.9f
                )
                dispatchCommand(
                    command = AddMainClipsCommand(listOf(clip), "Pro Camera Capture"),
                    newSelection = SelectedItem.MainClipItem(clip.id)
                )
                val lutNote = if (liveLut != FilterPreset.ORIGINAL) " with ${liveLut.displayName} LUT" else ""
                _feedback.tryEmit("Captured clip added to V1$lutNote")
            }.onFailure {
                _feedback.tryEmit("Could not import camera recording")
            }
        }
    }

    fun toggleVideoScopes() {
        _uiState.update { it.copy(showVideoScopes = !it.showVideoScopes) }
    }

    fun setScopeMode(mode: VideoScopeMode) {
        _uiState.update { it.copy(scopeMode = mode) }
    }

    fun importCubeLut(uri: Uri) {
        val targetId = resolveTargetMainClipId()
        if (targetId == null) {
            _feedback.tryEmit("Select or add a video clip before importing a .cube LUT")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = repository.mediaStorage.importCubeLutFromUri(uri)
            _uiState.update { it.copy(isImporting = false) }
            result.onSuccess { (lutPath, lutName) ->
                val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == targetId } ?: return@onSuccess
                val updated = clip.colorAdjustments.copy(
                    cubeLutPath = lutPath,
                    cubeLutName = lutName,
                    cubeLutIntensity = 1.0f
                )
                historyManager.breakCoalescing()
                dispatchCommand(
                    ColorGradeCommand(
                        beforeByClipId = mapOf(targetId to clip.colorAdjustments),
                        afterByClipId = mapOf(targetId to updated),
                        label = "Load 3D LUT: $lutName",
                        mergeKey = null
                    )
                )
                _feedback.tryEmit("Loaded 3D LUT: $lutName")
            }.onFailure { err ->
                _feedback.tryEmit(err.message ?: "Invalid .cube 3D LUT file")
            }
        }
    }

    fun clearSelectedCubeLut() {
        val targetId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        if (clip.colorAdjustments.cubeLutPath == null) return
        val updated = clip.colorAdjustments.copy(
            cubeLutPath = null,
            cubeLutName = null,
            cubeLutIntensity = 1.0f
        )
        historyManager.breakCoalescing()
        dispatchCommand(
            ColorGradeCommand(
                beforeByClipId = mapOf(targetId to clip.colorAdjustments),
                afterByClipId = mapOf(targetId to updated),
                label = "Remove 3D LUT",
                mergeKey = null
            )
        )
        _feedback.tryEmit("Removed 3D LUT")
    }

    fun toggleChromaColorSampling() {
        pausePlayback()
        _uiState.update { it.copy(isSamplingChromaColor = !it.isSamplingChromaColor) }
    }

    fun cancelChromaColorSampling() {
        _uiState.update { it.copy(isSamplingChromaColor = false) }
    }

    fun applySampledChromaColor(argb: Long) {
        _uiState.update { it.copy(isSamplingChromaColor = false) }
        val selId = (_uiState.value.selection as? SelectedItem.OverlayItem)?.overlayId
            ?: _uiState.value.timeline.overlays.firstOrNull { it.type == OverlayType.VIDEO }?.id
            ?: return
        val before = _uiState.value.timeline.overlays.firstOrNull { it.id == selId } ?: return
        val after = before.copy(
            chromaKey = before.chromaKey.copy(
                enabled = true,
                customColorArgb = argb
            )
        )
        historyManager.breakCoalescing()
        dispatchCommand(
            OverlayPropertyCommand(
                overlayId = selId,
                before = before,
                after = after,
                label = "Pick Chroma Key Color"
            )
        )
        val hex = String.format("#%06X", argb and 0xFFFFFFL)
        _feedback.tryEmit("Sampled chroma key color $hex")
    }

    fun toggleVoiceoverRecording() {
        if (_uiState.value.isRecordingVoiceover) {
            stopVoiceoverRecording()
        } else {
            startVoiceoverRecording()
        }
    }

    fun startVoiceoverRecording() {
        if (_uiState.value.isRecordingVoiceover) return
        pausePlayback()
        val outFile = repository.mediaStorage.createVoiceoverOutputFile()
        val startResult = voiceoverRecorder.start(outFile)
        if (startResult.isFailure) {
            _feedback.tryEmit("Microphone unavailable for voiceover recording")
            return
        }
        val startedAt = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isRecordingVoiceover = true,
                voiceoverLevel = 0.05f,
                voiceoverElapsedMs = 0L
            )
        }
        voiceoverMeterJob?.cancel()
        voiceoverMeterJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRecordingVoiceover) {
                delay(60L)
                val level = voiceoverRecorder.pollNormalizedLevel()
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                _uiState.update {
                    it.copy(
                        voiceoverLevel = level,
                        voiceoverElapsedMs = elapsed
                    )
                }
            }
        }
    }

    fun stopVoiceoverRecording() {
        if (!_uiState.value.isRecordingVoiceover) return
        voiceoverMeterJob?.cancel()
        voiceoverMeterJob = null
        val recordedFile = voiceoverRecorder.stopAndFinalize()
        _uiState.update {
            it.copy(
                isRecordingVoiceover = false,
                voiceoverLevel = 0f,
                voiceoverElapsedMs = 0L
            )
        }
        if (recordedFile == null) {
            _feedback.tryEmit("Voiceover recording was too short")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = repository.mediaStorage.importAudioFromUri(Uri.fromFile(recordedFile))
            _uiState.update { it.copy(isImporting = false) }
            result.onSuccess { meta ->
                val state = _uiState.value
                val targetLane = findOpenAudioLane(state.timeline.audioClips, state.playheadMs)
                val audioClip = AudioClip(
                    laneIndex = targetLane,
                    displayName = "Voiceover ${state.timeline.audioClips.size + 1}",
                    uri = meta.uriString,
                    startTimeMs = state.playheadMs,
                    sourceDurationMs = meta.durationMs,
                    trimStartMs = 0L,
                    trimEndMs = meta.durationMs.coerceAtLeast(500L),
                    waveformPeaks = meta.waveformPeaks,
                    beatPositionsMs = meta.beatPositionsMs
                )
                dispatchCommand(
                    command = AddAudioClipCommand(audioClip, "Record Voiceover"),
                    newSelection = SelectedItem.AudioItem(audioClip.id),
                    newInspector = InspectorTab.AUDIO
                )
                _feedback.tryEmit("Voiceover added to track A${targetLane + 1}")
            }.onFailure {
                _feedback.tryEmit("Failed to save voiceover clip")
            }
        }
    }

    fun openTransitionsPicker() {
        historyManager.breakCoalescing()
        val state = _uiState.value
        if (state.activeInspector == InspectorTab.TRANSITION) {
            closeInspector()
            return
        }
        val clips = state.timeline.mainClips
        if (clips.size < 2) {
            _uiState.update { it.copy(activeInspector = InspectorTab.TRANSITION) }
            return
        }
        val seamClips = clips.dropLast(1)
        val targetAfterId = when (val sel = state.selection) {
            is SelectedItem.TransitionItem ->
                seamClips.firstOrNull { it.id == sel.afterClipId }?.id
            is SelectedItem.MainClipItem ->
                seamClips.firstOrNull { it.id == sel.clipId }?.id
            else -> null
        } ?: run {
            val ranges = state.timeline.mainClipRanges().dropLast(1)
            ranges.minByOrNull { abs(it.endMs - state.playheadMs) }?.clip?.id ?: seamClips.first().id
        }
        _uiState.update {
            it.copy(
                selection = SelectedItem.TransitionItem(targetAfterId),
                activeInspector = InspectorTab.TRANSITION
            )
        }
    }

    // --- Imports (Videos, Photos, Overlays, Audio, Custom Fonts) ---

    fun importMainVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pausePlayback()
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val imported = mutableListOf<MainClip>()
            for (uri in uris) {
                repository.mediaStorage.importVideoFromUri(uri).onSuccess { meta ->
                    imported.add(
                        MainClip(
                            uri = meta.uriString,
                            displayName = meta.displayName,
                            mediaType = MainMediaType.VIDEO,
                            sourceDurationMs = meta.durationMs,
                            trimStartMs = 0L,
                            trimEndMs = meta.durationMs,
                            thumbnailPath = meta.thumbnailPath
                        )
                    )
                }
            }
            _uiState.update { it.copy(isImporting = false) }
            if (imported.isNotEmpty()) {
                val label = if (imported.size == 1) "Import Video" else "Import ${imported.size} Videos"
                dispatchCommand(
                    command = AddMainClipsCommand(imported, label),
                    newSelection = SelectedItem.MainClipItem(imported.last().id)
                )
                _feedback.tryEmit("Added ${imported.size} video clip${if (imported.size > 1) "s" else ""}")
            } else {
                _feedback.tryEmit("Could not import selected video")
            }
        }
    }

    fun importMainPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pausePlayback()
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val imported = mutableListOf<MainClip>()
            for (uri in uris) {
                repository.mediaStorage.importImageFromUri(uri).onSuccess { meta ->
                    imported.add(
                        MainClip(
                            uri = meta.uriString,
                            displayName = meta.displayName,
                            mediaType = MainMediaType.IMAGE,
                            sourceDurationMs = 15_000L,
                            trimStartMs = 0L,
                            trimEndMs = 4_000L,
                            thumbnailPath = meta.thumbnailPath
                        )
                    )
                }
            }
            _uiState.update { it.copy(isImporting = false) }
            if (imported.isNotEmpty()) {
                val label = if (imported.size == 1) "Import Photo" else "Import ${imported.size} Photos"
                dispatchCommand(
                    command = AddMainClipsCommand(imported, label),
                    newSelection = SelectedItem.MainClipItem(imported.last().id)
                )
                _feedback.tryEmit("Added ${imported.size} photo clip${if (imported.size > 1) "s" else ""}")
            } else {
                _feedback.tryEmit("Could not import selected photo")
            }
        }
    }

    fun importImageOverlay(uri: Uri) {
        pausePlayback()
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = repository.mediaStorage.importImageFromUri(uri)
            _uiState.update { it.copy(isImporting = false) }
            result.onSuccess { meta ->
                val state = _uiState.value
                val nextZ = (state.timeline.overlays.maxOfOrNull { it.zIndex } ?: 0) + 1
                val layer = OverlayLayer(
                    type = OverlayType.IMAGE,
                    zIndex = nextZ,
                    startTimeMs = state.playheadMs,
                    durationMs = 4000L,
                    imageUri = meta.uriString,
                    scale = 0.85f
                )
                dispatchCommand(
                    command = AddOverlayCommand(layer, "Add Image Overlay"),
                    newSelection = SelectedItem.OverlayItem(layer.id),
                    newInspector = InspectorTab.IMAGE_OVERLAY
                )
                _feedback.tryEmit("Added image overlay layer")
            }.onFailure {
                _feedback.tryEmit("Could not import overlay photo")
            }
        }
    }

    fun importVideoOverlay(uri: Uri) {
        pausePlayback()
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = repository.mediaStorage.importVideoFromUri(uri)
            _uiState.update { it.copy(isImporting = false) }
            result.onSuccess { meta ->
                val state = _uiState.value
                val nextZ = (state.timeline.overlays.maxOfOrNull { it.zIndex } ?: 0) + 1
                val clipDur = meta.durationMs.coerceIn(500L, 120_000L)
                val layer = OverlayLayer(
                    type = OverlayType.VIDEO,
                    zIndex = nextZ,
                    startTimeMs = state.playheadMs,
                    durationMs = clipDur,
                    videoUri = meta.uriString,
                    displayName = meta.displayName,
                    sourceDurationMs = meta.durationMs,
                    trimStartMs = 0L,
                    scale = 1.0f
                )
                dispatchCommand(
                    command = AddOverlayCommand(layer, "Add Video Overlay"),
                    newSelection = SelectedItem.OverlayItem(layer.id),
                    newInspector = InspectorTab.VIDEO_OVERLAY
                )
                _feedback.tryEmit("Added video overlay • Toggle Chroma Key to remove background")
            }.onFailure {
                _feedback.tryEmit("Could not import overlay video")
            }
        }
    }

    fun addTextOverlay() {
        pausePlayback()
        val state = _uiState.value
        val nextZ = (state.timeline.overlays.maxOfOrNull { it.zIndex } ?: 0) + 1
        val defaultFontId = availableFonts.value.firstOrNull()?.id ?: "bundled_bebas_neue"
        val layer = OverlayLayer(
            type = OverlayType.TEXT,
            zIndex = nextZ,
            startTimeMs = state.playheadMs,
            durationMs = 4000L,
            text = "CINEMA TITLE",
            fontId = defaultFontId,
            fontSizeSp = 34f,
            textColorArgb = 0xFFF4F5F7,
            hasStroke = true,
            hasBackground = false
        )
        dispatchCommand(
            command = AddOverlayCommand(layer, "Add Text Layer"),
            newSelection = SelectedItem.OverlayItem(layer.id),
            newInspector = InspectorTab.TEXT_STYLE
        )
    }

    fun importAudioTrack(uri: Uri) {
        pausePlayback()
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = repository.mediaStorage.importAudioFromUri(uri)
            _uiState.update { it.copy(isImporting = false) }
            result.onSuccess { meta ->
                val state = _uiState.value
                val targetLane = findOpenAudioLane(state.timeline.audioClips, state.playheadMs)
                val maxClipDur = if (state.timeline.mainTrackDurationMs > 0L) {
                    minOf(meta.durationMs, (state.timeline.mainTrackDurationMs - state.playheadMs).coerceAtLeast(3000L))
                } else meta.durationMs
                val audioClip = AudioClip(
                    laneIndex = targetLane,
                    displayName = meta.displayName,
                    uri = meta.uriString,
                    startTimeMs = state.playheadMs,
                    sourceDurationMs = meta.durationMs,
                    trimStartMs = 0L,
                    trimEndMs = maxClipDur.coerceAtLeast(500L),
                    waveformPeaks = meta.waveformPeaks,
                    beatPositionsMs = meta.beatPositionsMs
                )
                dispatchCommand(
                    command = AddAudioClipCommand(audioClip, "Import Audio: ${meta.displayName}"),
                    newSelection = SelectedItem.AudioItem(audioClip.id),
                    newInspector = InspectorTab.AUDIO
                )
                val beatSuffix = if (meta.beatPositionsMs.isNotEmpty()) {
                    " • ${meta.beatPositionsMs.size} beats"
                } else ""
                _feedback.tryEmit("Added audio: ${meta.displayName}$beatSuffix")
            }.onFailure {
                _feedback.tryEmit("Could not import audio track")
            }
        }
    }

    fun importCustomFont(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importCustomFont(uri)
            result.onSuccess { font ->
                _feedback.tryEmit("Imported font: ${font.displayName}")
                val selOverlay = _uiState.value.selectedOverlay
                if (selOverlay != null && selOverlay.type == OverlayType.TEXT) {
                    updateSelectedTextOverlay { it.copy(fontId = font.id) }
                }
            }.onFailure { err ->
                _feedback.tryEmit(err.message ?: "Failed to import font file")
            }
        }
    }

    fun deleteCustomFont(font: StudioFont) {
        viewModelScope.launch {
            repository.deleteCustomFont(font)
            FontResolver.clearEntry(font.id)
            _feedback.tryEmit("Deleted font: ${font.displayName}")
        }
    }

    // --- Core Multilayer Cutting, Trimming, Reordering, Duplicate, Delete ---

    fun splitSelectedAtPlayhead() {
        pausePlayback()
        val state = _uiState.value
        val playhead = state.playheadMs
        val minPiece = 250L

        when (val sel = state.selection) {
            is SelectedItem.MainClipItem -> {
                val range = state.timeline.mainClipRanges().firstOrNull { it.clip.id == sel.clipId }
                if (range == null || playhead <= range.startMs + minPiece || playhead >= range.endMs - minPiece) {
                    _feedback.tryEmit("Move playhead inside the clip to split")
                    return
                }
                splitMainClipRange(range.index, playhead - range.startMs)
            }
            is SelectedItem.OverlayItem -> {
                val overlay = state.timeline.overlays.firstOrNull { it.id == sel.overlayId }
                if (overlay == null || playhead <= overlay.startTimeMs + minPiece || playhead >= overlay.endTimeMs - minPiece) {
                    _feedback.tryEmit("Move playhead inside the overlay to split")
                    return
                }
                val offsetIntoOverlay = playhead - overlay.startTimeMs
                val left = overlay.copy(durationMs = offsetIntoOverlay)
                val right = overlay.copy(
                    id = UUID.randomUUID().toString(),
                    startTimeMs = playhead,
                    durationMs = overlay.endTimeMs - playhead,
                    trimStartMs = if (overlay.type == OverlayType.VIDEO) {
                        (overlay.trimStartMs + offsetIntoOverlay).coerceAtMost(overlay.sourceDurationMs)
                    } else overlay.trimStartMs
                )
                dispatchCommand(
                    command = SplitOverlayCommand(overlay, left, right),
                    newSelection = SelectedItem.OverlayItem(right.id)
                )
                _feedback.tryEmit("Split overlay layer")
            }
            is SelectedItem.AudioItem -> {
                val clip = state.timeline.audioClips.firstOrNull { it.id == sel.audioId }
                if (clip == null || playhead <= clip.startTimeMs + minPiece || playhead >= clip.endTimeMs - minPiece) {
                    _feedback.tryEmit("Move playhead inside the audio clip to split")
                    return
                }
                val cutSource = clip.trimStartMs + (playhead - clip.startTimeMs)
                val left = clip.copy(trimEndMs = cutSource)
                val right = clip.copy(
                    id = UUID.randomUUID().toString(),
                    startTimeMs = playhead,
                    trimStartMs = cutSource
                )
                dispatchCommand(
                    command = SplitAudioClipCommand(clip, left, right),
                    newSelection = SelectedItem.AudioItem(right.id)
                )
                _feedback.tryEmit("Split audio track")
            }
            else -> {
                val range = state.timeline.mainClipAt(playhead)
                if (range == null || playhead <= range.startMs + minPiece || playhead >= range.endMs - minPiece) {
                    _feedback.tryEmit("Position playhead inside a clip to cut")
                    return
                }
                splitMainClipRange(range.index, playhead - range.startMs)
            }
        }
    }

    private fun splitMainClipRange(index: Int, offsetIntoClipMs: Long) {
        val clip = _uiState.value.timeline.mainClips.getOrNull(index) ?: return
        val cutSourceMs = (clip.trimStartMs + (offsetIntoClipMs * clip.safeSpeed).toLong())
            .coerceIn(clip.trimStartMs + 100L, (clip.trimEndMs - 100L).coerceAtLeast(clip.trimStartMs + 100L))
        val leftKfs = clip.keyframes.filter { it.timeOffsetMs <= offsetIntoClipMs }
        val rightKfs = clip.keyframes
            .filter { it.timeOffsetMs > offsetIntoClipMs }
            .map { it.copy(id = UUID.randomUUID().toString(), timeOffsetMs = it.timeOffsetMs - offsetIntoClipMs) }
        val left = clip.copy(
            trimEndMs = cutSourceMs,
            transitionToNext = TransitionSpec(TransitionType.NONE, 600L),
            keyframes = leftKfs
        )
        val right = clip.copy(
            id = UUID.randomUUID().toString(),
            trimStartMs = cutSourceMs,
            transitionToNext = clip.transitionToNext,
            keyframes = rightKfs
        )
        dispatchCommand(
            command = SplitMainClipCommand(
                clipIndex = index,
                originalClip = clip,
                leftClip = left,
                rightClip = right,
                label = "Split ${clip.displayName}"
            ),
            newSelection = SelectedItem.MainClipItem(right.id)
        )
        _feedback.tryEmit("Clip cut at playhead")
    }

    fun trimMainClip(clipId: String, deltaStartMs: Long, deltaEndMs: Long) {
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == clipId } ?: return
        val maxEnd = if (clip.mediaType == MainMediaType.IMAGE) 30_000L else clip.sourceDurationMs
        val newStart = (clip.trimStartMs + deltaStartMs)
            .coerceIn(0L, (clip.trimEndMs - 300L).coerceAtLeast(0L))
        val newEnd = (clip.trimEndMs + deltaEndMs)
            .coerceIn((newStart + 300L).coerceAtMost(maxEnd), maxEnd)
        dispatchCommand(
            TrimMainClipCommand(
                clipId = clipId,
                beforeTrimStartMs = clip.trimStartMs,
                beforeTrimEndMs = clip.trimEndMs,
                afterTrimStartMs = newStart,
                afterTrimEndMs = newEnd,
                label = "Trim ${clip.displayName}"
            )
        )
    }

    fun trimOverlayTiming(overlayId: String, newStartMs: Long, newDurationMs: Long) {
        val layer = _uiState.value.timeline.overlays.firstOrNull { it.id == overlayId } ?: return
        val clampedStart = newStartMs.coerceAtLeast(0L)
        val clampedDur = newDurationMs.coerceIn(300L, 120_000L)
        val isPureMove = clampedDur == layer.durationMs && clampedStart != layer.startTimeMs
        dispatchCommand(
            TrimOverlayTimingCommand(
                overlayId = overlayId,
                beforeStartMs = layer.startTimeMs,
                beforeDurationMs = layer.durationMs,
                afterStartMs = clampedStart,
                afterDurationMs = clampedDur,
                label = if (isPureMove) "Move Overlay on Timeline" else "Trim Overlay Timing",
                category = if (isPureMove) CommandCategory.MOVE else CommandCategory.TRIM
            )
        )
    }

    fun trimAudioClip(audioId: String, deltaTimelineStartMs: Long, deltaTrimStartMs: Long, deltaTrimEndMs: Long) {
        val clip = _uiState.value.timeline.audioClips.firstOrNull { it.id == audioId } ?: return
        val shiftedStart = (clip.startTimeMs + deltaTimelineStartMs).coerceAtLeast(0L)
        val newTrimStart = (clip.trimStartMs + deltaTrimStartMs)
            .coerceIn(0L, (clip.trimEndMs - 300L).coerceAtLeast(0L))
        val newTrimEnd = (clip.trimEndMs + deltaTrimEndMs)
            .coerceIn((newTrimStart + 300L).coerceAtMost(clip.sourceDurationMs), clip.sourceDurationMs)
        val isPureMove = deltaTrimStartMs == 0L && deltaTrimEndMs == 0L
        dispatchCommand(
            TrimAudioClipCommand(
                audioId = audioId,
                beforeStartTimeMs = clip.startTimeMs,
                beforeTrimStartMs = clip.trimStartMs,
                beforeTrimEndMs = clip.trimEndMs,
                afterStartTimeMs = shiftedStart,
                afterTrimStartMs = newTrimStart,
                afterTrimEndMs = newTrimEnd,
                label = if (isPureMove) "Move Audio Track" else "Trim Audio Track",
                category = if (isPureMove) CommandCategory.MOVE else CommandCategory.TRIM
            )
        )
    }

    fun duplicateSelected() {
        when (val sel = _uiState.value.selection) {
            is SelectedItem.MainClipItem -> {
                val clips = _uiState.value.timeline.mainClips
                val idx = clips.indexOfFirst { it.id == sel.clipId }
                if (idx < 0) return
                val dup = clips[idx].copy(id = UUID.randomUUID().toString())
                dispatchCommand(
                    command = DuplicateMainClipCommand(idx + 1, dup, "Duplicate ${dup.displayName}"),
                    newSelection = SelectedItem.MainClipItem(dup.id)
                )
                _feedback.tryEmit("Duplicated clip")
            }
            is SelectedItem.OverlayItem -> {
                val layer = _uiState.value.timeline.overlays.firstOrNull { it.id == sel.overlayId } ?: return
                val nextZ = (_uiState.value.timeline.overlays.maxOfOrNull { it.zIndex } ?: 0) + 1
                val dup = layer.copy(
                    id = UUID.randomUUID().toString(),
                    zIndex = nextZ,
                    offsetX = (layer.offsetX + 0.04f).coerceIn(-0.45f, 0.45f),
                    offsetY = (layer.offsetY + 0.04f).coerceIn(-0.45f, 0.45f)
                )
                dispatchCommand(
                    command = AddOverlayCommand(dup, "Duplicate Overlay"),
                    newSelection = SelectedItem.OverlayItem(dup.id)
                )
                _feedback.tryEmit("Duplicated overlay")
            }
            is SelectedItem.AudioItem -> {
                val clip = _uiState.value.timeline.audioClips.firstOrNull { it.id == sel.audioId } ?: return
                val dup = clip.copy(
                    id = UUID.randomUUID().toString(),
                    startTimeMs = clip.endTimeMs
                )
                dispatchCommand(
                    command = AddAudioClipCommand(dup, "Duplicate Audio"),
                    newSelection = SelectedItem.AudioItem(dup.id)
                )
                _feedback.tryEmit("Duplicated audio clip")
            }
            is SelectedItem.FxItem -> {
                val fx = _uiState.value.timeline.fxLayers.firstOrNull { it.id == sel.fxId } ?: return
                val dup = fx.copy(
                    id = UUID.randomUUID().toString(),
                    centerX = (fx.centerX + 0.06f).coerceIn(0.15f, 0.85f),
                    centerY = (fx.centerY + 0.06f).coerceIn(0.15f, 0.85f)
                )
                dispatchCommand(
                    command = AddFxLayerCommand(dup, "Duplicate ${fx.type.displayName} FX"),
                    newSelection = SelectedItem.FxItem(dup.id),
                    newInspector = InspectorTab.VISUAL_FX
                )
                _feedback.tryEmit("Duplicated ${fx.type.displayName} FX")
            }
            else -> Unit
        }
    }

    fun deleteSelected() {
        pausePlayback()
        when (val sel = _uiState.value.selection) {
            is SelectedItem.MainClipItem -> {
                val clips = _uiState.value.timeline.mainClips
                val idx = clips.indexOfFirst { it.id == sel.clipId }
                if (idx < 0) return
                dispatchCommand(
                    command = DeleteMainClipCommand(idx, clips[idx], "Delete ${clips[idx].displayName}"),
                    newSelection = SelectedItem.None,
                    newInspector = InspectorTab.NONE
                )
                _feedback.tryEmit("Removed clip")
            }
            is SelectedItem.OverlayItem -> {
                val overlays = _uiState.value.timeline.overlays
                val idx = overlays.indexOfFirst { it.id == sel.overlayId }
                if (idx < 0) return
                dispatchCommand(
                    command = DeleteOverlayCommand(idx, overlays[idx], "Delete Overlay"),
                    newSelection = SelectedItem.None,
                    newInspector = InspectorTab.NONE
                )
                _feedback.tryEmit("Removed overlay")
            }
            is SelectedItem.AudioItem -> {
                val audios = _uiState.value.timeline.audioClips
                val idx = audios.indexOfFirst { it.id == sel.audioId }
                if (idx < 0) return
                dispatchCommand(
                    command = DeleteAudioClipCommand(idx, audios[idx], "Delete ${audios[idx].displayName}"),
                    newSelection = SelectedItem.None,
                    newInspector = InspectorTab.NONE
                )
                _feedback.tryEmit("Removed audio track")
            }
            is SelectedItem.CaptionItem -> {
                val caption = _uiState.value.timeline.captions.firstOrNull { it.id == sel.captionId } ?: return
                dispatchCommand(
                    command = DeleteCaptionCommand(caption),
                    newSelection = SelectedItem.None,
                    newInspector = InspectorTab.CAPTIONS
                )
                _feedback.tryEmit("Removed caption")
            }
            is SelectedItem.FxItem -> {
                val fx = _uiState.value.timeline.fxLayers.firstOrNull { it.id == sel.fxId } ?: return
                dispatchCommand(
                    command = DeleteFxLayerCommand(fx),
                    newSelection = SelectedItem.None,
                    newInspector = InspectorTab.VISUAL_FX
                )
                _feedback.tryEmit("Removed ${fx.type.displayName} FX")
            }
            else -> Unit
        }
    }

    fun reorderMainClip(clipId: String, moveLeft: Boolean) {
        val clips = _uiState.value.timeline.mainClips
        val idx = clips.indexOfFirst { it.id == clipId }
        val target = if (moveLeft) idx - 1 else idx + 1
        if (idx < 0 || target !in clips.indices) return
        dispatchCommand(
            ReorderMainClipCommand(
                fromIndex = idx,
                toIndex = target,
                label = if (moveLeft) "Move Clip Left" else "Move Clip Right"
            )
        )
    }

    fun reorderOverlayZ(overlayId: String, bringForward: Boolean) {
        val sorted = _uiState.value.timeline.overlays.sortedBy { it.zIndex }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == overlayId }
        val target = if (bringForward) idx + 1 else idx - 1
        if (idx < 0 || target !in sorted.indices) return
        val beforeMap = sorted.associate { it.id to it.zIndex }
        val item = sorted.removeAt(idx)
        sorted.add(target, item)
        val afterMap = sorted.mapIndexed { i, layer -> layer.id to (i + 1) }.toMap()
        dispatchCommand(
            ReorderOverlayZCommand(
                beforeZMap = beforeMap,
                afterZMap = afterMap,
                label = if (bringForward) "Bring Overlay Forward" else "Send Overlay Backward"
            )
        )
    }

    // --- Interactive Touch Transform for Overlays (Drag, Pinch-Scale, Rotate) ---

    fun beginOverlayGesture(overlayId: String? = null) {
        val id = overlayId ?: (_uiState.value.selection as? SelectedItem.OverlayItem)?.overlayId ?: return
        if (activeGestureOverlayId == null) {
            val layer = _uiState.value.timeline.overlays.firstOrNull { it.id == id } ?: return
            activeGestureOverlayId = id
            activeGestureBeforeTransform = OverlayTransformSnapshot(
                offsetX = layer.offsetX,
                offsetY = layer.offsetY,
                scale = layer.scale,
                rotationDegrees = layer.rotationDegrees
            )
        }
    }

    fun transformOverlayLive(
        overlayId: String,
        deltaNormalizedX: Float,
        deltaNormalizedY: Float,
        scaleFactor: Float,
        deltaRotationDeg: Float
    ) {
        if (activeGestureOverlayId == null) {
            beginOverlayGesture(overlayId)
        }
        val beforeLayer = _uiState.value.timeline.overlays.firstOrNull { it.id == overlayId } ?: return
        val beforeSnap = OverlayTransformSnapshot(
            beforeLayer.offsetX, beforeLayer.offsetY, beforeLayer.scale, beforeLayer.rotationDegrees
        )

        var snapX = false
        var snapY = false
        // Free placement allowing partial off-screen framing up to ±95% of stage dimensions
        var nextX = (beforeLayer.offsetX + deltaNormalizedX).coerceIn(-0.95f, 0.95f)
        var nextY = (beforeLayer.offsetY + deltaNormalizedY).coerceIn(-0.95f, 0.95f)
        if (abs(nextX) < 0.018f && abs(deltaNormalizedX) > 0.0002f) {
            nextX = 0f
            snapX = true
        }
        if (abs(nextY) < 0.018f && abs(deltaNormalizedY) > 0.0002f) {
            nextY = 0f
            snapY = true
        }
        val nextScale = (beforeLayer.scale * scaleFactor).coerceIn(0.25f, 4.0f)
        var nextRot = (beforeLayer.rotationDegrees + deltaRotationDeg) % 360f
        if (nextRot > 180f) nextRot -= 360f
        if (nextRot < -180f) nextRot += 360f
        if (abs(nextRot) < 2.5f && abs(deltaRotationDeg) > 0.01f) {
            nextRot = 0f
        }

        val afterSnap = OverlayTransformSnapshot(nextX, nextY, nextScale, nextRot)
        val cmd = TransformOverlayCommand(
            overlayId = overlayId,
            before = beforeSnap,
            after = afterSnap,
            label = "Transform Overlay"
        )
        // Coalescable TransformOverlayCommand ensures smooth multi-touch gestures produce ONE undo step
        val result = historyManager.execute(cmd, _uiState.value.toDocumentState())
        if (result != null) {
            _uiState.update { state ->
                state.copy(
                    timeline = result.newState.timeline,
                    snapGuideX = snapX,
                    snapGuideY = snapY,
                    history = result.summary
                )
            }
            scheduleAutosave()
        }
    }

    fun endOverlayGesture() {
        activeGestureOverlayId = null
        activeGestureBeforeTransform = null
        historyManager.breakCoalescing()
        _uiState.update { it.copy(snapGuideX = false, snapGuideY = false) }
    }

    fun resetOverlayTransform(overlayId: String) {
        val layer = _uiState.value.timeline.overlays.firstOrNull { it.id == overlayId } ?: return
        historyManager.breakCoalescing()
        dispatchCommand(
            TransformOverlayCommand(
                overlayId = overlayId,
                before = OverlayTransformSnapshot(layer.offsetX, layer.offsetY, layer.scale, layer.rotationDegrees),
                after = OverlayTransformSnapshot(0f, 0f, 1f, 0f),
                label = "Reset Overlay Transform"
            )
        )
        historyManager.breakCoalescing()
    }

    // --- Filters, Color Grading, Transitions, Audio Mixer, Text/Image Style ---

    fun updateSelectedFilter(preset: FilterPreset, intensity: Float) {
        val targetId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        dispatchCommand(
            FilterChangeCommand(
                beforeByClipId = mapOf(targetId to (clip.filterPreset to clip.filterIntensity)),
                afterByClipId = mapOf(targetId to (preset to clampedIntensity)),
                label = "Filter: ${preset.displayName}",
                mergeKey = "filter:$targetId"
            )
        )
    }

    fun applyFilterToAllClips() {
        val source = _uiState.value.selectedMainClip
            ?: _uiState.value.timeline.mainClipAt(_uiState.value.playheadMs)?.clip
            ?: return
        val clips = _uiState.value.timeline.mainClips
        if (clips.isEmpty()) return
        historyManager.breakCoalescing()
        dispatchCommand(
            FilterChangeCommand(
                beforeByClipId = clips.associate { it.id to (it.filterPreset to it.filterIntensity) },
                afterByClipId = clips.associate { it.id to (source.filterPreset to source.filterIntensity) },
                label = "Apply ${source.filterPreset.displayName} to All",
                mergeKey = null
            )
        )
        _feedback.tryEmit("Applied ${source.filterPreset.displayName} to all clips")
    }

    fun updateSelectedColorAdjustments(adjustments: ColorAdjustments) {
        val targetId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        dispatchCommand(
            ColorGradeCommand(
                beforeByClipId = mapOf(targetId to clip.colorAdjustments),
                afterByClipId = mapOf(targetId to adjustments),
                label = "Adjust Color Grade",
                mergeKey = "color_grade:$targetId"
            )
        )
    }

    fun resetSelectedColorAdjustments() {
        val targetId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        historyManager.breakCoalescing()
        dispatchCommand(
            ColorGradeCommand(
                beforeByClipId = mapOf(targetId to clip.colorAdjustments),
                afterByClipId = mapOf(targetId to ColorAdjustments()),
                label = "Reset Color Grade",
                mergeKey = null
            )
        )
    }

    fun applyColorAdjustmentsToAllClips() {
        val source = _uiState.value.selectedMainClip
            ?: _uiState.value.timeline.mainClipAt(_uiState.value.playheadMs)?.clip
            ?: return
        val clips = _uiState.value.timeline.mainClips
        if (clips.isEmpty()) return
        historyManager.breakCoalescing()
        dispatchCommand(
            ColorGradeCommand(
                beforeByClipId = clips.associate { it.id to it.colorAdjustments },
                afterByClipId = clips.associate { it.id to source.colorAdjustments },
                label = "Apply Color Grade to All",
                mergeKey = null
            )
        )
        _feedback.tryEmit("Applied color grade to all clips")
    }

    fun updateTransition(afterClipId: String, type: TransitionType, durationMs: Long) {
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == afterClipId } ?: return
        val nextSpec = TransitionSpec(type = type, durationMs = durationMs.coerceIn(200L, 2000L))
        dispatchCommand(
            TransitionChangeCommand(
                afterClipId = afterClipId,
                beforeSpec = clip.transitionToNext,
                afterSpec = nextSpec,
                label = "Transition: ${type.displayName}"
            )
        )
    }

    fun applyTransitionToAllCuts(type: TransitionType, durationMs: Long) {
        val seamClips = _uiState.value.timeline.mainClips.dropLast(1)
        if (seamClips.isEmpty()) return
        val nextSpec = TransitionSpec(type = type, durationMs = durationMs.coerceIn(200L, 2000L))
        historyManager.breakCoalescing()
        dispatchCommand(
            BatchTransitionChangeCommand(
                beforeByClipId = seamClips.associate { it.id to it.transitionToNext },
                afterByClipId = seamClips.associate { it.id to nextSpec },
                label = "Apply ${type.displayName} to All Cuts"
            )
        )
        _feedback.tryEmit("Applied ${type.displayName} to all ${seamClips.size} cuts")
    }

    fun previewTransitionSeam(afterClipId: String) {
        val state = _uiState.value
        val range = state.timeline.mainClipRanges().firstOrNull { it.clip.id == afterClipId } ?: return
        val halfDur = (range.clip.transitionToNext.durationMs / 2L).coerceAtLeast(250L)
        val startMs = (range.endMs - halfDur - 350L).coerceAtLeast(0L)
        _uiState.update {
            it.copy(
                playheadMs = startMs,
                seekNonce = it.seekNonce + 1L,
                isPlaying = true
            )
        }
        startPlaybackTicker()
    }

    // --- Keyframe Motion Animation (Position, Scale, Rotation) ---

    fun updateClipTransformAtPlayhead(transform: ClipTransform) {
        val state = _uiState.value
        val targetId = resolveTargetMainClipId() ?: return
        val range = state.timeline.mainClipRanges().firstOrNull { it.clip.id == targetId } ?: return
        val clip = range.clip
        val localMs = (state.playheadMs - range.startMs).coerceIn(0L, clip.durationMs)
        val clamped = ClipTransform(
            offsetX = transform.offsetX.coerceIn(-1f, 1f),
            offsetY = transform.offsetY.coerceIn(-1f, 1f),
            scale = transform.scale.coerceIn(0.25f, 3.0f),
            rotationDegrees = transform.rotationDegrees.coerceIn(-180f, 180f),
            opacity = transform.opacity.coerceIn(0f, 1f)
        )

        if (clip.keyframes.isEmpty()) {
            dispatchCommand(
                ClipKeyframesCommand(
                    clipId = clip.id,
                    beforeBaseTransform = clip.baseTransform,
                    beforeKeyframes = clip.keyframes,
                    afterBaseTransform = clamped,
                    afterKeyframes = clip.keyframes,
                    label = "Transform ${clip.displayName}",
                    mergeKey = "keyframes_base:${clip.id}"
                )
            )
        } else {
            val near = KeyframeEvaluator.findKeyframeNear(clip, localMs, toleranceMs = 90L)
            val updatedKfs = if (near != null) {
                clip.keyframes.map {
                    if (it.id == near.id) {
                        it.copy(
                            offsetX = clamped.offsetX,
                            offsetY = clamped.offsetY,
                            scale = clamped.scale,
                            rotationDegrees = clamped.rotationDegrees,
                            opacity = clamped.opacity
                        )
                    } else it
                }
            } else {
                (clip.keyframes + TransformKeyframe(
                    timeOffsetMs = localMs,
                    offsetX = clamped.offsetX,
                    offsetY = clamped.offsetY,
                    scale = clamped.scale,
                    rotationDegrees = clamped.rotationDegrees,
                    opacity = clamped.opacity
                )).sortedBy { it.timeOffsetMs }
            }
            dispatchCommand(
                ClipKeyframesCommand(
                    clipId = clip.id,
                    beforeBaseTransform = clip.baseTransform,
                    beforeKeyframes = clip.keyframes,
                    afterBaseTransform = clip.baseTransform,
                    afterKeyframes = updatedKfs,
                    label = "Animate Keyframe",
                    mergeKey = "keyframes_at:${clip.id}:${near?.id ?: localMs}"
                )
            )
        }
    }

    fun toggleKeyframeAtPlayhead() {
        pausePlayback()
        val state = _uiState.value
        val targetId = resolveTargetMainClipId() ?: return
        val range = state.timeline.mainClipRanges().firstOrNull { it.clip.id == targetId } ?: return
        val clip = range.clip
        val localMs = (state.playheadMs - range.startMs).coerceIn(0L, clip.durationMs)
        val existing = KeyframeEvaluator.findKeyframeNear(clip, localMs, toleranceMs = 90L)
        historyManager.breakCoalescing()

        if (existing != null) {
            val remaining = clip.keyframes.filterNot { it.id == existing.id }
            dispatchCommand(
                ClipKeyframesCommand(
                    clipId = clip.id,
                    beforeBaseTransform = clip.baseTransform,
                    beforeKeyframes = clip.keyframes,
                    afterBaseTransform = clip.baseTransform,
                    afterKeyframes = remaining,
                    label = "Remove Keyframe",
                    mergeKey = null
                )
            )
            _feedback.tryEmit("Removed keyframe")
        } else {
            val eval = KeyframeEvaluator.evaluate(clip, localMs)
            val added = TransformKeyframe(
                timeOffsetMs = localMs,
                offsetX = eval.offsetX,
                offsetY = eval.offsetY,
                scale = eval.scale,
                rotationDegrees = eval.rotationDegrees,
                opacity = eval.opacity
            )
            val nextKfs = (clip.keyframes + added).sortedBy { it.timeOffsetMs }
            dispatchCommand(
                ClipKeyframesCommand(
                    clipId = clip.id,
                    beforeBaseTransform = clip.baseTransform,
                    beforeKeyframes = clip.keyframes,
                    afterBaseTransform = clip.baseTransform,
                    afterKeyframes = nextKfs,
                    label = "Add Keyframe (${String.format("%.2fs", localMs / 1000f)})",
                    mergeKey = null
                )
            )
            _feedback.tryEmit("Added keyframe at ${String.format("%.2fs", localMs / 1000f)}")
        }
    }

    fun updateActiveKeyframeEasing(easing: KeyframeEasing) {
        val state = _uiState.value
        val targetId = resolveTargetMainClipId() ?: return
        val range = state.timeline.mainClipRanges().firstOrNull { it.clip.id == targetId } ?: return
        val clip = range.clip
        if (clip.keyframes.isEmpty()) return
        val localMs = (state.playheadMs - range.startMs).coerceIn(0L, clip.durationMs)
        val targetKf = KeyframeEvaluator.findKeyframeNear(clip, localMs, toleranceMs = 90L)
            ?: clip.keyframes.sortedBy { it.timeOffsetMs }.lastOrNull { it.timeOffsetMs <= localMs }
            ?: clip.keyframes.first()
        val updated = clip.keyframes.map { if (it.id == targetKf.id) it.copy(easing = easing) else it }
        historyManager.breakCoalescing()
        dispatchCommand(
            ClipKeyframesCommand(
                clipId = clip.id,
                beforeBaseTransform = clip.baseTransform,
                beforeKeyframes = clip.keyframes,
                afterBaseTransform = clip.baseTransform,
                afterKeyframes = updated,
                label = "Easing: ${easing.displayName}",
                mergeKey = null
            )
        )
    }

    fun jumpToRelativeKeyframe(next: Boolean) {
        pausePlayback()
        val state = _uiState.value
        val targetId = resolveTargetMainClipId() ?: return
        val range = state.timeline.mainClipRanges().firstOrNull { it.clip.id == targetId } ?: return
        val localMs = (state.playheadMs - range.startMs).coerceIn(0L, range.clip.durationMs)
        val targetLocal = if (next) {
            KeyframeEvaluator.nextKeyframeTimeMs(range.clip, localMs)
        } else {
            KeyframeEvaluator.previousKeyframeTimeMs(range.clip, localMs)
        }
        if (targetLocal != null) {
            seekTo(range.startMs + targetLocal)
        }
    }

    fun applyMotionPreset(preset: MotionPreset) {
        pausePlayback()
        val state = _uiState.value
        val targetId = resolveTargetMainClipId() ?: return
        val range = state.timeline.mainClipRanges().firstOrNull { it.clip.id == targetId } ?: return
        val clip = range.clip
        val endMs = clip.durationMs.coerceAtLeast(300L)
        val kfs = when (preset) {
            MotionPreset.KEN_BURNS_IN -> listOf(
                TransformKeyframe(timeOffsetMs = 0L, scale = 1.0f, easing = KeyframeEasing.EASE_IN_OUT),
                TransformKeyframe(timeOffsetMs = endMs, scale = 1.28f, easing = KeyframeEasing.EASE_IN_OUT)
            )
            MotionPreset.PAN_LEFT_RIGHT -> listOf(
                TransformKeyframe(timeOffsetMs = 0L, offsetX = -0.14f, scale = 1.24f, easing = KeyframeEasing.EASE_IN_OUT),
                TransformKeyframe(timeOffsetMs = endMs, offsetX = 0.14f, scale = 1.24f, easing = KeyframeEasing.EASE_IN_OUT)
            )
            MotionPreset.SPIN_POP -> listOf(
                TransformKeyframe(timeOffsetMs = 0L, scale = 0.72f, rotationDegrees = -18f, easing = KeyframeEasing.EASE_OUT),
                TransformKeyframe(timeOffsetMs = (endMs * 0.45f).toLong().coerceAtLeast(200L), scale = 1.08f, rotationDegrees = 0f, easing = KeyframeEasing.EASE_OUT),
                TransformKeyframe(timeOffsetMs = endMs, scale = 1.0f, rotationDegrees = 0f, easing = KeyframeEasing.EASE_IN_OUT)
            )
        }
        historyManager.breakCoalescing()
        dispatchCommand(
            ClipKeyframesCommand(
                clipId = clip.id,
                beforeBaseTransform = clip.baseTransform,
                beforeKeyframes = clip.keyframes,
                afterBaseTransform = ClipTransform(),
                afterKeyframes = kfs,
                label = "Motion: ${preset.label}",
                mergeKey = null
            )
        )
        seekTo(range.startMs)
        _feedback.tryEmit("Applied ${preset.label} keyframes")
    }

    fun resetClipMotion() {
        val targetId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        historyManager.breakCoalescing()
        dispatchCommand(
            ClipKeyframesCommand(
                clipId = clip.id,
                beforeBaseTransform = clip.baseTransform,
                beforeKeyframes = clip.keyframes,
                afterBaseTransform = ClipTransform(),
                afterKeyframes = emptyList(),
                label = "Reset Motion",
                mergeKey = null
            )
        )
        _feedback.tryEmit("Reset clip motion & keyframes")
    }

    // --- Clip Speed & Audio Pitch Shifting (0.25x .. 4.0x) ---

    fun updateSelectedClipSpeed(speed: Float, pitchSemitones: Float, preservePitch: Boolean) {
        val targetId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        if (clip.mediaType != MainMediaType.VIDEO) {
            _feedback.tryEmit("Speed adjustment applies to video clips")
            return
        }
        val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
        val clampedPitch = pitchSemitones.coerceIn(-12f, 12f)
        val before = ClipSpeedSnapshot(clip.safeSpeed, clip.pitchShiftSemitones, clip.preservePitch)
        val after = ClipSpeedSnapshot(clampedSpeed, clampedPitch, preservePitch)
        if (before == after) return
        val label = if (abs(clampedSpeed - clip.safeSpeed) > 0.01f) {
            String.format("Speed %.2gx", clampedSpeed)
        } else {
            "Audio Pitch Shift"
        }
        dispatchCommand(
            ClipSpeedCommand(
                clipId = clip.id,
                before = before,
                after = after,
                label = label,
                mergeKey = "speed:${clip.id}"
            )
        )
    }

    fun resetSelectedClipSpeed() {
        val targetId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        val before = ClipSpeedSnapshot(clip.safeSpeed, clip.pitchShiftSemitones, clip.preservePitch)
        val after = ClipSpeedSnapshot(1.0f, 0f, true)
        if (before == after) return
        historyManager.breakCoalescing()
        dispatchCommand(
            ClipSpeedCommand(
                clipId = clip.id,
                before = before,
                after = after,
                label = "Reset Speed & Pitch",
                mergeKey = null
            )
        )
        _feedback.tryEmit("Reset speed to 1.0x & natural pitch")
    }

    fun updateSelectedAudioSettings(settings: AudioSettings) {
        val state = _uiState.value
        when (val sel = state.selection) {
            is SelectedItem.AudioItem -> {
                val clip = state.timeline.audioClips.firstOrNull { it.id == sel.audioId } ?: return
                dispatchCommand(
                    AudioSettingsCommand(
                        targetId = clip.id,
                        isAudioTrack = true,
                        before = clip.audio,
                        after = settings,
                        label = if (settings.isMuted != clip.audio.isMuted) {
                            if (settings.isMuted) "Mute Audio Track" else "Unmute Audio Track"
                        } else "Audio Mixer: ${clip.displayName}"
                    )
                )
            }
            is SelectedItem.MainClipItem -> {
                val clip = state.timeline.mainClips.firstOrNull { it.id == sel.clipId } ?: return
                dispatchCommand(
                    AudioSettingsCommand(
                        targetId = clip.id,
                        isAudioTrack = false,
                        before = clip.audio,
                        after = settings,
                        label = if (settings.isMuted != clip.audio.isMuted) {
                            if (settings.isMuted) "Mute Video Clip" else "Unmute Video Clip"
                        } else "Clip Audio: ${clip.displayName}"
                    )
                )
            }
            else -> {
                val clip = state.timeline.mainClipAt(state.playheadMs)?.clip ?: return
                dispatchCommand(
                    AudioSettingsCommand(
                        targetId = clip.id,
                        isAudioTrack = false,
                        before = clip.audio,
                        after = settings,
                        label = "Clip Audio: ${clip.displayName}"
                    )
                )
            }
        }
    }

    fun updateSelectedTextOverlay(transform: (OverlayLayer) -> OverlayLayer) {
        val sel = _uiState.value.selection as? SelectedItem.OverlayItem ?: return
        val before = _uiState.value.timeline.overlays.firstOrNull { it.id == sel.overlayId } ?: return
        val after = transform(before)
        if (after == before) return
        val label = when {
            after.text != before.text -> "Edit Text"
            after.fontId != before.fontId -> "Change Font"
            after.textColorArgb != before.textColorArgb -> "Change Text Color"
            after.textMotionPreset != before.textMotionPreset -> "Text Motion: ${after.textMotionPreset.displayName}"
            after.textMotionDurationMs != before.textMotionDurationMs -> "Text Motion Speed"
            else -> "Edit Text Style"
        }
        dispatchCommand(
            OverlayPropertyCommand(
                overlayId = sel.overlayId,
                before = before,
                after = after,
                label = label
            )
        )
    }

    fun replaySelectedTextMotion() {
        pausePlayback()
        _uiState.update { it.copy(textMotionReplayNonce = it.textMotionReplayNonce + 1) }
    }

    fun updateSelectedImageOverlay(
        opacity: Float,
        cornerRadiusDp: Float,
        blendMode: OverlayBlendMode = OverlayBlendMode.NORMAL
    ) {
        val sel = _uiState.value.selection as? SelectedItem.OverlayItem ?: return
        val before = _uiState.value.timeline.overlays.firstOrNull { it.id == sel.overlayId } ?: return
        val after = before.copy(
            opacity = opacity.coerceIn(0.05f, 1f),
            cornerRadiusDp = cornerRadiusDp.coerceIn(0f, 48f),
            blendMode = blendMode
        )
        dispatchCommand(
            OverlayPropertyCommand(
                overlayId = sel.overlayId,
                before = before,
                after = after,
                label = if (blendMode != before.blendMode) "Blend Mode: ${blendMode.displayName}" else "Edit Image Overlay Style"
            )
        )
    }

    fun updateSelectedVideoOverlay(transform: (OverlayLayer) -> OverlayLayer) {
        val sel = _uiState.value.selection as? SelectedItem.OverlayItem ?: return
        val before = _uiState.value.timeline.overlays.firstOrNull { it.id == sel.overlayId } ?: return
        val after = transform(before)
        if (after == before) return
        val label = when {
            after.chromaKey.enabled != before.chromaKey.enabled ->
                if (after.chromaKey.enabled) "Enable Chroma Key" else "Disable Chroma Key"
            after.chromaKey.keyColor != before.chromaKey.keyColor ->
                "Chroma Key: ${after.chromaKey.keyColor.displayName}"
            after.chromaKey != before.chromaKey -> "Adjust Chroma Key"
            else -> "Edit Video Overlay"
        }
        dispatchCommand(
            OverlayPropertyCommand(
                overlayId = sel.overlayId,
                before = before,
                after = after,
                label = label
            )
        )
    }

    // --- Project Settings (Aspect Ratio & Title) ---

    fun updateAspectRatio(aspectRatio: AspectRatio) {
        val before = _uiState.value.aspectRatio
        if (before == aspectRatio) return
        if (dispatchCommand(AspectRatioCommand(before, aspectRatio))) {
            _feedback.tryEmit("Canvas set to ${aspectRatio.label}")
        }
    }

    fun updateCanvasBackdrop(backdrop: CanvasBackdrop) {
        val before = _uiState.value.timeline.canvasBackdrop
        if (before == backdrop) return
        dispatchCommand(CanvasBackdropCommand(before, backdrop))
    }

    fun updateSafeZoneOverlay(safeZone: SafeZoneOverlay) {
        val before = _uiState.value.timeline.safeZoneOverlay
        if (before == safeZone) return
        if (dispatchCommand(SafeZoneOverlayCommand(before, safeZone))) {
            _feedback.tryEmit("Safe Zone: ${safeZone.label}")
        }
    }

    // --- Masking, Flip & 90° Rotate ---

    fun updateSelectedMask(mask: MaskSettings) {
        val sel = _uiState.value.selection
        if (sel is SelectedItem.OverlayItem) {
            val before = _uiState.value.timeline.overlays.firstOrNull { it.id == sel.overlayId } ?: return
            if (before.mask == mask) return
            dispatchCommand(
                OverlayPropertyCommand(
                    overlayId = sel.overlayId,
                    before = before,
                    after = before.copy(mask = mask),
                    label = "Mask: ${mask.shape.displayName}"
                )
            )
            return
        }
        val clipId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == clipId } ?: return
        if (clip.mask == mask) return
        dispatchCommand(
            MainClipMaskCommand(
                clipId = clipId,
                before = clip.mask,
                after = mask,
                label = "Mask: ${mask.shape.displayName}"
            )
        )
    }

    fun toggleSelectedMaskKeyframe() {
        val clipId = resolveTargetMainClipId() ?: return
        val range = _uiState.value.timeline.mainClipRanges().firstOrNull { it.clip.id == clipId } ?: return
        val clip = range.clip
        val localMs = (_uiState.value.playheadMs - range.startMs).coerceIn(0L, clip.durationMs)
        val currentMask = clip.mask
        val existingIdx = currentMask.keyframes.indexOfFirst { abs(it.timeOffsetMs - localMs) <= 120L }
        val updatedKeys = if (existingIdx >= 0) {
            currentMask.keyframes.toMutableList().apply { removeAt(existingIdx) }
        } else {
            (currentMask.keyframes + MaskKeyframe(
                timeOffsetMs = localMs,
                centerX = currentMask.centerX,
                centerY = currentMask.centerY,
                width = currentMask.width,
                height = currentMask.height,
                rotationDegrees = currentMask.rotationDegrees
            )).sortedBy { it.timeOffsetMs }
        }
        updateSelectedMask(currentMask.copy(keyframes = updatedKeys))
        _feedback.tryEmit(if (existingIdx >= 0) "Removed mask keyframe" else "Added mask keyframe")
    }

    fun toggleClipFlip(horizontal: Boolean) {
        val clipId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == clipId } ?: return
        val before = clip.baseTransform
        val after = if (horizontal) {
            before.copy(flipHorizontal = !before.flipHorizontal)
        } else {
            before.copy(flipVertical = !before.flipVertical)
        }
        dispatchCommand(
            ClipKeyframesCommand(
                clipId = clipId,
                beforeBaseTransform = before,
                afterBaseTransform = after,
                beforeKeyframes = clip.keyframes,
                afterKeyframes = clip.keyframes,
                label = if (horizontal) "Flip Horizontal" else "Flip Vertical",
                mergeKey = null
            )
        )
        _feedback.tryEmit(if (horizontal) "Flipped horizontally" else "Flipped vertically")
    }

    fun rotateClip90() {
        val clipId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == clipId } ?: return
        val before = clip.baseTransform
        val rawRot = (before.rotationDegrees + 90f) % 360f
        val normalized = if (rawRot > 180f) rawRot - 360f else rawRot
        val after = before.copy(rotationDegrees = normalized)
        dispatchCommand(
            ClipKeyframesCommand(
                clipId = clipId,
                beforeBaseTransform = before,
                afterBaseTransform = after,
                beforeKeyframes = clip.keyframes,
                afterKeyframes = clip.keyframes,
                label = "Rotate 90°",
                mergeKey = null
            )
        )
        _feedback.tryEmit("Rotated clip 90°")
    }

    // --- Freeze Frame & Speed Ramping ---

    fun insertFreezeFrameAtPlayhead() {
        pausePlayback()
        val state = _uiState.value
        val range = state.timeline.mainClipAt(state.playheadMs) ?: run {
            _feedback.tryEmit("Move playhead over a video clip first")
            return
        }
        val clip = range.clip
        if (clip.mediaType != MainMediaType.VIDEO) {
            _feedback.tryEmit("Freeze frame works on video clips")
            return
        }
        val localMs = (state.playheadMs - range.startMs).coerceIn(0L, clip.durationMs)
        val sourceTimeMs = (clip.trimStartMs + (localMs * clip.safeSpeed).toLong())
            .coerceIn(clip.trimStartMs, clip.trimEndMs)

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val bmp = StudioCreativeTools.extractFrameBitmap(appContext, clip.uri, sourceTimeMs)
                if (bmp == null) {
                    _feedback.tryEmit("Could not decode frame at current timecode")
                    return@launch
                }
                val file = StudioCreativeTools.saveFreezeFramePng(appContext, bmp)
                bmp.recycle()

                val freezeClip = MainClip(
                    uri = Uri.fromFile(file).toString(),
                    displayName = "${clip.displayName} (Freeze)",
                    mediaType = MainMediaType.IMAGE,
                    sourceDurationMs = 2000L,
                    trimStartMs = 0L,
                    trimEndMs = 2000L,
                    filterPreset = clip.filterPreset,
                    filterIntensity = clip.filterIntensity,
                    colorAdjustments = clip.colorAdjustments,
                    thumbnailPath = file.absolutePath,
                    baseTransform = clip.baseTransform,
                    mask = clip.mask
                )

                val beforeClips = _uiState.value.timeline.mainClips
                val idx = beforeClips.indexOfFirst { it.id == clip.id }
                if (idx < 0) return@launch

                val newClips = beforeClips.toMutableList()
                val canSplitBothSides = (sourceTimeMs - clip.trimStartMs >= 300L) && (clip.trimEndMs - sourceTimeMs >= 300L)
                if (canSplitBothSides) {
                    val left = clip.copy(
                        trimEndMs = sourceTimeMs,
                        transitionToNext = TransitionSpec()
                    )
                    val right = clip.copy(
                        id = UUID.randomUUID().toString(),
                        trimStartMs = sourceTimeMs,
                        keyframes = emptyList()
                    )
                    newClips[idx] = left
                    newClips.add(idx + 1, freezeClip)
                    newClips.add(idx + 2, right)
                } else {
                    newClips.add(idx + 1, freezeClip)
                }

                dispatchCommand(
                    command = ReplaceMainClipsCommand(
                        beforeClips = beforeClips,
                        afterClips = newClips,
                        label = "Insert Freeze Frame",
                        category = CommandCategory.CUT
                    ),
                    newSelection = SelectedItem.MainClipItem(freezeClip.id)
                )
                _feedback.tryEmit("Inserted 2.0s Freeze Frame")
            } catch (e: Exception) {
                _feedback.tryEmit(e.localizedMessage ?: "Freeze frame failed")
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun applySpeedRampPreset(preset: SpeedRampPreset) {
        pausePlayback()
        val clipId = resolveTargetMainClipId() ?: return
        val beforeClips = _uiState.value.timeline.mainClips
        val idx = beforeClips.indexOfFirst { it.id == clipId }
        if (idx < 0) return
        val target = beforeClips[idx]
        if (target.mediaType != MainMediaType.VIDEO) {
            _feedback.tryEmit("Speed ramps apply to video clips")
            return
        }
        val segments = preset.splitClip(target)
        val afterClips = beforeClips.toMutableList().apply {
            removeAt(idx)
            addAll(idx, segments)
        }
        dispatchCommand(
            command = ReplaceMainClipsCommand(
                beforeClips = beforeClips,
                afterClips = afterClips,
                label = "Speed Ramp: ${preset.displayName}",
                category = CommandCategory.SPEED
            ),
            newSelection = SelectedItem.MainClipItem(segments.first().id)
        )
        _feedback.tryEmit("Applied ${preset.displayName} (${segments.size} segments)")
    }

    // --- Audio Pro: Extract Audio & Offline Cinema SFX ---

    fun extractAudioFromSelectedClip() {
        pausePlayback()
        val clipId = resolveTargetMainClipId() ?: run {
            _feedback.tryEmit("Select a video clip first")
            return
        }
        val range = _uiState.value.timeline.mainClipRanges().firstOrNull { it.clip.id == clipId } ?: return
        val clip = range.clip
        if (clip.mediaType != MainMediaType.VIDEO) {
            _feedback.tryEmit("Only video clips have embedded audio")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val uri = Uri.parse(clip.uri)
                val analysis = withContext(Dispatchers.IO) {
                    AudioWaveformExtractor.extract(appContext, uri, clip.sourceDurationMs)
                }
                val lane = findOpenAudioLane(_uiState.value.timeline.audioClips, range.startMs)
                val extracted = AudioClip(
                    laneIndex = lane,
                    displayName = "${clip.displayName} (Audio)",
                    uri = clip.uri,
                    startTimeMs = range.startMs,
                    sourceDurationMs = clip.sourceDurationMs,
                    trimStartMs = clip.trimStartMs,
                    trimEndMs = clip.trimEndMs,
                    audio = clip.audio.copy(isMuted = false),
                    waveformPeaks = analysis.peaks,
                    beatPositionsMs = analysis.beatPositionsMs
                )
                dispatchCommand(
                    command = ExtractAudioCommand(
                        sourceClipId = clip.id,
                        beforeClipAudio = clip.audio,
                        extractedAudioClip = extracted
                    ),
                    newSelection = SelectedItem.AudioItem(extracted.id),
                    newInspector = InspectorTab.AUDIO
                )
                _feedback.tryEmit("Extracted audio to Track ${lane + 1}")
            } catch (e: Exception) {
                _feedback.tryEmit(e.localizedMessage ?: "Audio extraction failed")
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun previewCinemaSfx(preset: CinemaSfxPreset) {
        viewModelScope.launch {
            runCatching {
                val file = StudioCreativeTools.ensureCinemaSfxFile(appContext, preset)
                StudioCreativeTools.auditionWavFile(file)
            }
        }
    }

    fun insertCinemaSfxAtPlayhead(preset: CinemaSfxPreset) {
        pausePlayback()
        viewModelScope.launch {
            try {
                val file = StudioCreativeTools.ensureCinemaSfxFile(appContext, preset)
                val uri = Uri.fromFile(file)
                val analysis = withContext(Dispatchers.IO) {
                    AudioWaveformExtractor.extract(appContext, uri, preset.durationMs)
                }
                val startMs = _uiState.value.playheadMs.coerceAtLeast(0L)
                val lane = findOpenAudioLane(_uiState.value.timeline.audioClips, startMs)
                val sfxClip = AudioClip(
                    laneIndex = lane,
                    displayName = "SFX • ${preset.displayName}",
                    uri = uri.toString(),
                    startTimeMs = startMs,
                    sourceDurationMs = preset.durationMs,
                    trimStartMs = 0L,
                    trimEndMs = preset.durationMs,
                    audio = AudioSettings(volume = 1.0f),
                    waveformPeaks = analysis.peaks,
                    beatPositionsMs = analysis.beatPositionsMs
                )
                dispatchCommand(
                    command = AddAudioClipCommand(sfxClip, "Add SFX: ${preset.displayName}"),
                    newSelection = SelectedItem.AudioItem(sfxClip.id)
                )
                StudioCreativeTools.auditionWavFile(file)
                _feedback.tryEmit("Added ${preset.displayName} at ${formatTimecode(startMs)}")
            } catch (e: Exception) {
                _feedback.tryEmit(e.localizedMessage ?: "Could not insert SFX")
            }
        }
    }

    // --- Karaoke Captions & SRT Studio ---

    fun addCaptionAtPlayhead(text: String = "Tap to edit caption") {
        pausePlayback()
        val start = _uiState.value.playheadMs.coerceAtLeast(0L)
        val caption = CaptionClip(
            text = text,
            startTimeMs = start,
            endTimeMs = start + 2400L,
            animationPreset = CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT
        )
        dispatchCommand(
            command = AddCaptionsCommand(listOf(caption), "Add Caption"),
            newSelection = SelectedItem.CaptionItem(caption.id),
            newInspector = InspectorTab.CAPTIONS
        )
        _feedback.tryEmit("Added caption at ${formatTimecode(start)}")
    }

    fun updateSelectedCaption(transform: (CaptionClip) -> CaptionClip) {
        val sel = _uiState.value.selection as? SelectedItem.CaptionItem ?: return
        val before = _uiState.value.timeline.captions.firstOrNull { it.id == sel.captionId } ?: return
        val after = transform(before)
        if (after == before) return
        dispatchCommand(UpdateCaptionCommand(before.id, before, after))
    }

    fun moveOrTrimCaption(captionId: String, newStartMs: Long, newEndMs: Long) {
        val before = _uiState.value.timeline.captions.firstOrNull { it.id == captionId } ?: return
        val clampedStart = newStartMs.coerceAtLeast(0L)
        val clampedEnd = newEndMs.coerceAtLeast(clampedStart + 350L)
        val after = before.copy(startTimeMs = clampedStart, endTimeMs = clampedEnd)
        if (after == before) return
        dispatchCommand(UpdateCaptionCommand(before.id, before, after, label = "Retiming Caption"))
    }

    fun importSrtFromUri(uri: Uri) {
        pausePlayback()
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: run {
                    _feedback.tryEmit("Could not read selected .srt file")
                    return@launch
                }
                val parsed = SrtCodec.parseSrt(raw)
                if (parsed.isEmpty()) {
                    _feedback.tryEmit("No valid subtitle cues found in .srt file")
                    return@launch
                }
                dispatchCommand(
                    command = AddCaptionsCommand(parsed, "Import SRT (${parsed.size} cues)"),
                    newSelection = SelectedItem.CaptionItem(parsed.first().id),
                    newInspector = InspectorTab.CAPTIONS
                )
                _feedback.tryEmit("Imported ${parsed.size} captions from SRT")
            } catch (e: Exception) {
                _feedback.tryEmit(e.localizedMessage ?: "SRT import failed")
            }
        }
    }

    fun exportSrtToUri(uri: Uri) {
        val captions = _uiState.value.timeline.captions
        if (captions.isEmpty()) {
            _feedback.tryEmit("Add captions before exporting .srt")
            return
        }
        viewModelScope.launch {
            try {
                val srtText = SrtCodec.formatSrt(captions)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(srtText) }
                        ?: error("Could not open output file")
                }
                _feedback.tryEmit("Exported ${captions.size} captions to .srt")
            } catch (e: Exception) {
                _feedback.tryEmit(e.localizedMessage ?: "SRT export failed")
            }
        }
    }

    // --- Animated Lower Third Templates ---

    fun insertLowerThirdTemplate(template: LowerThirdTemplate) {
        pausePlayback()
        val state = _uiState.value
        val nextZ = (state.timeline.overlays.maxOfOrNull { it.zIndex } ?: 0) + 1
        val overlay = template.toOverlayLayer(startTimeMs = state.playheadMs.coerceAtLeast(0L), zIndex = nextZ)
        dispatchCommand(
            command = AddOverlayCommand(overlay, "Add ${template.displayName}"),
            newSelection = SelectedItem.OverlayItem(overlay.id),
            newInspector = InspectorTab.TEXT_STYLE
        )
        _feedback.tryEmit("Inserted ${template.displayName}")
    }

    // --- Gemini AI Creative Studio (Firebase AI Logic + App Check) ---

    private suspend fun captureCurrentPlayheadFrame(): android.graphics.Bitmap? {
        val state = _uiState.value
        val range = state.timeline.mainClipAt(state.playheadMs) ?: return null
        val clip = range.clip
        return if (clip.mediaType == MainMediaType.VIDEO) {
            val localMs = (state.playheadMs - range.startMs).coerceIn(0L, clip.durationMs)
            val srcMs = (clip.trimStartMs + (localMs * clip.safeSpeed).toLong()).coerceIn(clip.trimStartMs, clip.trimEndMs)
            StudioCreativeTools.extractFrameBitmap(appContext, clip.uri, srcMs)
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(clip.uri)
                    val input = if (uri.scheme == "file" && uri.path != null) {
                        File(uri.path!!).inputStream()
                    } else {
                        appContext.contentResolver.openInputStream(uri)
                    }
                    input?.use { android.graphics.BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
        }
    }

    fun generateAiHooksAndIdeas(topic: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(aiIdeasLoading = true, aiIdeasError = null) }
            val frame = captureCurrentPlayheadFrame()
            val tl = _uiState.value.timeline
            val result = GeminiColoristService.generateHooksAndEditIdeas(
                context = appContext,
                frameBitmap = frame,
                topic = topic,
                clipCount = tl.mainClips.size,
                durationMs = tl.totalDurationMs
            )
            frame?.recycle()
            result.onSuccess { ideas ->
                _uiState.update {
                    it.copy(aiIdeasLoading = false, aiIdeasResult = ideas, aiIdeasError = null)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        aiIdeasLoading = false,
                        aiIdeasError = err.localizedMessage ?: "Gemini request failed. Check connection and try again."
                    )
                }
            }
        }
    }

    fun insertAiHookAsOverlay(hookText: String) {
        val clean = hookText.trim()
        if (clean.isEmpty()) return
        pausePlayback()
        val state = _uiState.value
        val nextZ = (state.timeline.overlays.maxOfOrNull { it.zIndex } ?: 0) + 1
        val overlay = OverlayLayer(
            type = OverlayType.TEXT,
            zIndex = nextZ,
            startTimeMs = state.playheadMs.coerceAtLeast(0L),
            durationMs = 3000L,
            offsetX = 0f,
            offsetY = -0.18f,
            scale = 1f,
            displayName = "AI Hook",
            text = clean,
            fontId = "bundled_anton",
            fontSizeSp = 34f,
            textColorArgb = 0xFFFFFFFF,
            strokeColorArgb = 0xFF000000,
            hasStroke = true,
            hasDropShadow = true,
            hasBackground = true,
            backgroundColorArgb = 0xCC0A0C10,
            textMotionPreset = TextMotionPreset.POP_SPRING,
            textMotionDurationMs = 750L
        )
        dispatchCommand(
            command = AddOverlayCommand(overlay, "Add AI Hook"),
            newSelection = SelectedItem.OverlayItem(overlay.id),
            newInspector = InspectorTab.TEXT_STYLE
        )
        _feedback.tryEmit("Added AI hook to timeline")
    }

    fun generateAiColorGrade(prompt: String) {
        val clipId = resolveTargetMainClipId() ?: run {
            _uiState.update { it.copy(aiColoristError = "Add or select a video/photo clip first.") }
            return
        }
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == clipId } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(aiColoristLoading = true, aiColoristError = null, aiColoristNote = null) }
            val frame = captureCurrentPlayheadFrame()
            val result = GeminiColoristService.generateColorGrade(
                context = appContext,
                frameBitmap = frame,
                prompt = prompt,
                currentAdjustments = clip.colorAdjustments
            )
            frame?.recycle()
            result.onSuccess { grade ->
                updateSelectedColorAdjustments(grade.adjustments)
                _uiState.update {
                    it.copy(
                        aiColoristLoading = false,
                        aiColoristNote = grade.coloristNote,
                        aiColoristError = null
                    )
                }
                _feedback.tryEmit("Applied Gemini AI color grade")
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        aiColoristLoading = false,
                        aiColoristError = err.localizedMessage ?: "AI Colorist failed. Please retry."
                    )
                }
            }
        }
    }

    fun generateAiCaptionsFromScript(script: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(aiCaptionsLoading = true, aiCaptionsError = null) }
            val state = _uiState.value
            val startAt = state.playheadMs.coerceAtLeast(0L)
            val targetDur = (state.timeline.totalDurationMs - startAt).coerceAtLeast(4000L)
            val result = GeminiColoristService.generateTimedCaptionsFromScript(
                context = appContext,
                script = script,
                startAtMs = startAt,
                targetDurationMs = targetDur
            )
            result.onSuccess { blocks ->
                dispatchCommand(
                    command = AddCaptionsCommand(blocks, "AI Captions (${blocks.size})"),
                    newSelection = SelectedItem.CaptionItem(blocks.first().id),
                    newInspector = InspectorTab.CAPTIONS
                )
                _uiState.update { it.copy(aiCaptionsLoading = false, aiCaptionsError = null) }
                _feedback.tryEmit("Generated ${blocks.size} karaoke captions")
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        aiCaptionsLoading = false,
                        aiCaptionsError = err.localizedMessage ?: "AI caption generation failed."
                    )
                }
            }
        }
    }

    fun updateProjectTitle(title: String) {
        val before = _uiState.value.projectTitle
        val clean = title.trim().ifBlank { "Untitled Edit" }
        if (before == clean) return
        dispatchCommand(ProjectTitleCommand(before, clean))
    }

    // --- MP4 Export ---

    fun openExportSheet() {
        pausePlayback()
        _uiState.update { it.copy(showExportSheet = true) }
    }

    fun closeExportSheet() {
        val state = _uiState.value.exportState
        if (state is ExportState.Exporting) return
        _uiState.update { it.copy(showExportSheet = false, exportState = ExportState.Idle) }
    }

    fun updateExportConfig(config: ExportConfig) {
        _uiState.update { it.copy(exportConfig = config) }
    }

    fun startMp4Export() {
        pausePlayback()
        val state = _uiState.value
        exportEngine.startExport(
            scope = viewModelScope,
            projectTitle = state.projectTitle,
            aspectRatio = state.aspectRatio,
            timeline = state.timeline,
            availableFonts = availableFonts.value,
            config = state.exportConfig
        ) { newExportState ->
            _uiState.update { it.copy(exportState = newExportState) }
        }
    }

    fun cancelMp4Export() {
        exportEngine.cancelExport()
        _uiState.update { it.copy(exportState = ExportState.Idle) }
        _feedback.tryEmit("Export cancelled")
    }

    fun saveNow() {
        val state = _uiState.value
        if (state.projectId.isBlank()) return
        viewModelScope.launch {
            repository.saveProjectState(
                id = state.projectId,
                title = state.projectTitle,
                aspectRatio = state.aspectRatio,
                timeline = state.timeline
            )
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(350L)
            val state = _uiState.value
            if (state.projectId.isNotBlank()) {
                repository.saveProjectState(
                    id = state.projectId,
                    title = state.projectTitle,
                    aspectRatio = state.aspectRatio,
                    timeline = state.timeline
                )
            }
        }
    }

    private fun resolveTargetMainClipId(): String? {
        val state = _uiState.value
        return (state.selection as? SelectedItem.MainClipItem)?.clipId
            ?: state.timeline.mainClipAt(state.playheadMs)?.clip?.id
    }

    private fun findOpenAudioLane(existing: List<AudioClip>, startMs: Long): Int {
        for (lane in 0..3) {
            val conflict = existing.any { it.laneIndex == lane && startMs in it.startTimeMs..it.endTimeMs }
            if (!conflict) return lane
        }
        return (existing.maxOfOrNull { it.laneIndex } ?: 0) + 1
    }

    // --- KineMaster Pro v3.0: Visual FX Layers (Magnifier, Mosaic, Blur), Transform/Crop/PanZoom, AI Cutout & Dual-Engine Auto Captions ---

    fun addFxLayer(type: FxType, fullFrame: Boolean = false) {
        pausePlayback()
        val state = _uiState.value
        val activeRange = state.timeline.mainClipAt(state.playheadMs)
        val startMs = if (fullFrame && activeRange != null) activeRange.startMs else state.playheadMs.coerceAtLeast(0L)
        val durMs = if (fullFrame && activeRange != null) {
            activeRange.clip.durationMs.coerceAtLeast(500L)
        } else {
            minOf(4000L, (state.timeline.totalDurationMs - startMs).coerceAtLeast(3000L))
        }
        val fx = FxLayer(
            type = type,
            startTimeMs = startMs,
            durationMs = durMs,
            fullFrame = fullFrame,
            shape = if (type == FxType.MAGNIFIER) FxShape.CIRCLE else FxShape.ROUNDED_RECT,
            centerX = 0.5f,
            centerY = 0.5f,
            width = if (type == FxType.MAGNIFIER) 0.34f else 0.38f,
            height = if (type == FxType.MAGNIFIER) 0.34f else 0.28f,
            intensity = when (type) {
                FxType.MAGNIFIER -> 0.55f
                FxType.MOSAIC -> 0.50f
                FxType.BLUR -> 0.55f
            },
            magnification = 2.0f,
            feather = 0.14f,
            opacity = 1.0f,
            showBorderRing = type == FxType.MAGNIFIER
        )
        dispatchCommand(
            command = AddFxLayerCommand(fx),
            newSelection = SelectedItem.FxItem(fx.id),
            newInspector = InspectorTab.VISUAL_FX
        )
        val modeTxt = if (fullFrame) "Full Clip" else "Movable Region"
        _feedback.tryEmit("Added ${type.displayName} FX ($modeTxt)")
    }

    fun selectFxLayer(fxId: String) {
        selectItem(SelectedItem.FxItem(fxId), InspectorTab.VISUAL_FX)
    }

    fun updateSelectedFxLayer(transform: (FxLayer) -> FxLayer) {
        val state = _uiState.value
        val targetId = (state.selection as? SelectedItem.FxItem)?.fxId
            ?: state.timeline.fxLayers.firstOrNull()?.id
            ?: return
        val before = state.timeline.fxLayers.firstOrNull { it.id == targetId } ?: return
        val after = transform(before)
        if (after == before) return
        dispatchCommand(UpdateFxLayerCommand(targetId, before, after))
    }

    fun moveOrTrimFxLayer(fxId: String, newStartMs: Long, newDurationMs: Long) {
        val before = _uiState.value.timeline.fxLayers.firstOrNull { it.id == fxId } ?: return
        val after = before.copy(
            startTimeMs = newStartMs.coerceAtLeast(0L),
            durationMs = newDurationMs.coerceIn(300L, 180_000L)
        )
        if (after == before) return
        dispatchCommand(UpdateFxLayerCommand(fxId, before, after, label = "Retiming ${before.type.displayName} FX"))
    }

    fun updateFxRegionLive(
        fxId: String,
        centerX: Float,
        centerY: Float,
        width: Float? = null,
        height: Float? = null
    ) {
        val before = _uiState.value.timeline.fxLayers.firstOrNull { it.id == fxId } ?: return
        val after = before.copy(
            centerX = centerX.coerceIn(0.05f, 0.95f),
            centerY = centerY.coerceIn(0.05f, 0.95f),
            width = (width ?: before.width).coerceIn(0.10f, 1.0f),
            height = (height ?: before.height).coerceIn(0.10f, 1.0f)
        )
        if (after == before) return
        dispatchCommand(UpdateFxLayerCommand(fxId, before, after, label = "Position ${before.type.displayName} FX"))
    }

    fun updateSelectedGeometry(transform: ClipTransform) {
        val state = _uiState.value
        val clamped = ClipTransform(
            offsetX = transform.offsetX.coerceIn(-1f, 1f),
            offsetY = transform.offsetY.coerceIn(-1f, 1f),
            scale = transform.scale.coerceIn(0.25f, 4.0f),
            rotationDegrees = transform.rotationDegrees.coerceIn(-180f, 180f),
            opacity = transform.opacity.coerceIn(0f, 1f),
            flipHorizontal = transform.flipHorizontal,
            flipVertical = transform.flipVertical,
            fullFrameFill = transform.fullFrameFill,
            crop = transform.crop.sanitized()
        )
        val sel = state.selection
        if (sel is SelectedItem.OverlayItem) {
            val before = state.timeline.overlays.firstOrNull { it.id == sel.overlayId } ?: return
            val after = before.copy(
                offsetX = clamped.offsetX,
                offsetY = clamped.offsetY,
                scale = clamped.scale,
                rotationDegrees = clamped.rotationDegrees,
                opacity = clamped.opacity,
                flipHorizontal = clamped.flipHorizontal,
                flipVertical = clamped.flipVertical,
                fullFrameFill = clamped.fullFrameFill,
                crop = clamped.crop
            )
            if (after == before) return
            dispatchCommand(
                OverlayPropertyCommand(
                    overlayId = sel.overlayId,
                    before = before,
                    after = after,
                    label = "Transform Overlay"
                )
            )
            return
        }
        val targetId = resolveTargetMainClipId() ?: return
        val clip = state.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        if (clip.baseTransform == clamped) return
        dispatchCommand(
            ClipKeyframesCommand(
                clipId = clip.id,
                beforeBaseTransform = clip.baseTransform,
                beforeKeyframes = clip.keyframes,
                afterBaseTransform = clamped,
                afterKeyframes = clip.keyframes,
                label = "Transform & Crop ${clip.displayName}",
                mergeKey = "geometry:${clip.id}"
            )
        )
    }

    fun resetSelectedGeometry() {
        val state = _uiState.value
        historyManager.breakCoalescing()
        val sel = state.selection
        if (sel is SelectedItem.OverlayItem) {
            val before = state.timeline.overlays.firstOrNull { it.id == sel.overlayId } ?: return
            val after = before.copy(
                offsetX = 0f,
                offsetY = 0f,
                scale = 1f,
                rotationDegrees = 0f,
                opacity = 1f,
                flipHorizontal = false,
                flipVertical = false,
                fullFrameFill = false,
                crop = CropRect()
            )
            dispatchCommand(
                OverlayPropertyCommand(
                    overlayId = sel.overlayId,
                    before = before,
                    after = after,
                    label = "Reset Overlay Transform"
                )
            )
            _feedback.tryEmit("Reset overlay geometry")
            return
        }
        val targetId = resolveTargetMainClipId() ?: return
        val clip = state.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        dispatchCommand(
            ClipKeyframesCommand(
                clipId = clip.id,
                beforeBaseTransform = clip.baseTransform,
                beforeKeyframes = clip.keyframes,
                afterBaseTransform = ClipTransform(),
                afterKeyframes = clip.keyframes,
                label = "Reset Clip Transform",
                mergeKey = null
            )
        )
        if (clip.panZoom.enabled) {
            dispatchCommand(
                MainClipPanZoomCommand(
                    clipId = clip.id,
                    before = clip.panZoom,
                    after = PanZoomSpec(),
                    label = "Reset Pan & Zoom"
                )
            )
        }
        _feedback.tryEmit("Reset transform, crop & zoom")
    }

    fun updateSelectedClipPanZoom(spec: PanZoomSpec) {
        val targetId = resolveTargetMainClipId() ?: return
        val clip = _uiState.value.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        if (clip.panZoom == spec) return
        dispatchCommand(
            MainClipPanZoomCommand(
                clipId = clip.id,
                before = clip.panZoom,
                after = spec,
                label = if (spec.enabled) "Pan & Zoom" else "Disable Pan & Zoom"
            )
        )
    }

    fun updateSelectedCutout(settings: AiCutoutSettings) {
        val state = _uiState.value
        val sel = state.selection
        if (sel is SelectedItem.OverlayItem) {
            val before = state.timeline.overlays.firstOrNull { it.id == sel.overlayId } ?: return
            if (before.aiCutout == settings) return
            dispatchCommand(
                OverlayPropertyCommand(
                    overlayId = sel.overlayId,
                    before = before,
                    after = before.copy(aiCutout = settings),
                    label = if (settings.enabled) "AI Cutout Overlay" else "Disable Cutout"
                )
            )
            return
        }
        val targetId = resolveTargetMainClipId() ?: return
        val clip = state.timeline.mainClips.firstOrNull { it.id == targetId } ?: return
        if (clip.aiCutout == settings) return
        dispatchCommand(
            MainClipCutoutCommand(
                clipId = targetId,
                before = clip.aiCutout,
                after = settings,
                label = if (settings.enabled) "AI Subject Cutout" else "Disable AI Cutout"
            )
        )
    }

    fun importCustomCutoutBackground(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = repository.mediaStorage.importImageFromUri(uri)
            _uiState.update { it.copy(isImporting = false) }
            result.onSuccess { meta ->
                val state = _uiState.value
                val currentCutout = state.selectedOverlay?.aiCutout
                    ?: (state.selectedMainClip ?: state.timeline.mainClipAt(state.playheadMs)?.clip)?.aiCutout
                    ?: AiCutoutSettings()
                updateSelectedCutout(
                    currentCutout.copy(
                        enabled = true,
                        backgroundMode = CutoutBackgroundMode.CUSTOM_IMAGE,
                        customBackgroundUri = meta.uriString
                    )
                )
                _feedback.tryEmit("Applied custom cutout background")
            }.onFailure {
                _feedback.tryEmit("Could not load background photo")
            }
        }
    }

    fun runGeminiAutoCaptions(languageHint: String) {
        pausePlayback()
        val state = _uiState.value

        // Resolve target clip with audio: selected audio -> selected video -> clip at playhead -> first audio/video clip
        data class AudioSourceTarget(
            val uri: String,
            val trimStartMs: Long,
            val trimEndMs: Long,
            val timelineStartMs: Long,
            val label: String
        )

        val target: AudioSourceTarget? = when (val sel = state.selection) {
            is SelectedItem.AudioItem -> state.timeline.audioClips.firstOrNull { it.id == sel.audioId }?.let {
                AudioSourceTarget(it.uri, it.trimStartMs, it.trimEndMs, it.startTimeMs, it.displayName)
            }
            is SelectedItem.MainClipItem -> state.timeline.mainClipRanges()
                .firstOrNull { it.clip.id == sel.clipId && it.clip.mediaType == MainMediaType.VIDEO }
                ?.let { AudioSourceTarget(it.clip.uri, it.clip.trimStartMs, it.clip.trimEndMs, it.startMs, it.clip.displayName) }
            else -> null
        } ?: state.timeline.mainClipAt(state.playheadMs)
            ?.takeIf { it.clip.mediaType == MainMediaType.VIDEO }
            ?.let { AudioSourceTarget(it.clip.uri, it.clip.trimStartMs, it.clip.trimEndMs, it.startMs, it.clip.displayName) }
            ?: state.timeline.audioClips.firstOrNull()?.let {
                AudioSourceTarget(it.uri, it.trimStartMs, it.trimEndMs, it.startTimeMs, it.displayName)
            }
            ?: state.timeline.mainClipRanges().firstOrNull { it.clip.mediaType == MainMediaType.VIDEO }?.let {
                AudioSourceTarget(it.clip.uri, it.clip.trimStartMs, it.clip.trimEndMs, it.startMs, it.clip.displayName)
            }

        if (target == null) {
            _uiState.update {
                it.copy(autoCaptionError = "Add a video or audio clip first to transcribe speech.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    autoCaptionLoading = true,
                    autoCaptionStatus = "Extracting audio from ${target.label}…",
                    autoCaptionError = null
                )
            }
            try {
                val extracted = AudioClipExtractor.extractForTranscription(
                    context = appContext,
                    uriString = target.uri,
                    trimStartMs = target.trimStartMs,
                    trimEndMs = target.trimEndMs
                ).getOrElse { err ->
                    _uiState.update {
                        it.copy(
                            autoCaptionLoading = false,
                            autoCaptionStatus = null,
                            autoCaptionError = err.localizedMessage ?: "No audio track found in clip."
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(autoCaptionStatus = "Transcribing speech with Gemini AI ($languageHint)…")
                }

                val result = GeminiColoristService.transcribeAudioToCaptions(
                    context = appContext,
                    audioBytes = extracted.bytes,
                    mimeType = extracted.mimeType,
                    clipStartOnTimelineMs = target.timelineStartMs,
                    clipDurationMs = extracted.durationMs,
                    languageHint = languageHint
                )

                result.onSuccess { captions ->
                    dispatchCommand(
                        command = AddCaptionsCommand(captions, "AI Auto Captions (${captions.size})"),
                        newSelection = SelectedItem.CaptionItem(captions.first().id),
                        newInspector = InspectorTab.CAPTIONS
                    )
                    _uiState.update {
                        it.copy(
                            autoCaptionLoading = false,
                            autoCaptionStatus = "Added ${captions.size} synced captions",
                            autoCaptionError = null
                        )
                    }
                    _feedback.tryEmit("Transcribed ${captions.size} timed captions")
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            autoCaptionLoading = false,
                            autoCaptionStatus = null,
                            autoCaptionError = err.localizedMessage ?: "Gemini transcription failed. Try again or use Offline Mic."
                        )
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        autoCaptionLoading = false,
                        autoCaptionStatus = null,
                        autoCaptionError = e.localizedMessage ?: "Auto caption transcription failed."
                    )
                }
            }
        }
    }

    fun startOfflineMicDictation() {
        if (_uiState.value.isLiveMicDictating) return
        if (_uiState.value.isRecordingVoiceover) stopVoiceoverRecording()
        _uiState.update {
            it.copy(
                autoCaptionError = null,
                autoCaptionStatus = "Listening via device mic…"
            )
        }
        offlineSpeechEngine.start(
            currentPlayheadMs = { _uiState.value.playheadMs },
            onPartialText = { partial ->
                _uiState.update { it.copy(liveMicPartialText = partial) }
            },
            onFinalPhrase = { phrase, startMs, endMs ->
                val clean = phrase.trim()
                if (clean.isNotEmpty()) {
                    val caption = CaptionClip(
                        text = clean,
                        startTimeMs = startMs.coerceAtLeast(0L),
                        endTimeMs = endMs.coerceAtLeast(startMs + 800L),
                        animationPreset = CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT
                    )
                    dispatchCommand(
                        command = AddCaptionsCommand(listOf(caption), "Mic Caption"),
                        newSelection = SelectedItem.CaptionItem(caption.id),
                        newInspector = InspectorTab.CAPTIONS
                    )
                    _uiState.update { it.copy(liveMicPartialText = "") }
                }
            },
            onError = { errMsg ->
                _uiState.update {
                    it.copy(
                        isLiveMicDictating = false,
                        liveMicPartialText = "",
                        autoCaptionStatus = null,
                        autoCaptionError = errMsg
                    )
                }
            },
            onListeningChanged = { active ->
                _uiState.update {
                    it.copy(
                        isLiveMicDictating = active,
                        autoCaptionStatus = if (active) "Listening offline… speak now" else null
                    )
                }
            }
        )
    }

    fun stopOfflineMicDictation() {
        offlineSpeechEngine.stop()
        _uiState.update {
            it.copy(
                isLiveMicDictating = false,
                liveMicPartialText = "",
                autoCaptionStatus = null
            )
        }
    }

    override fun onCleared() {
        pausePlayback()
        voiceoverMeterJob?.cancel()
        voiceoverRecorder.stopAndDiscard()
        offlineSpeechEngine.release()
        StudioCreativeTools.stopAudition()
        exportEngine.cancelExport()
        super.onCleared()
    }

    companion object {
        fun factory(
            context: Context,
            projectId: String,
            repository: StudioRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(context, projectId, repository) as T
            }
        }
    }
}
