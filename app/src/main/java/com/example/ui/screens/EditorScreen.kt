package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AudioSettings
import com.example.model.ColorAdjustments
import com.example.model.FilterPreset
import com.example.model.OverlayType
import com.example.model.TransitionSpec
import com.example.model.formatTimecode
import com.example.ui.components.AudioMixerInspector
import com.example.ui.components.CanvasAspectInspector
import com.example.ui.components.ClipSeamInfo
import com.example.ui.components.ColorAdjustInspector
import com.example.ui.components.EditHistoryBottomSheet
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.FilterInspector
import com.example.ui.components.FontLibraryInspector
import com.example.ui.components.ImageOverlayInspector
import com.example.ui.components.KeyframeInspector
import com.example.ui.components.MultilayerTimeline
import com.example.ui.components.PreviewStage
import com.example.ui.components.ProCameraModal
import com.example.ui.components.SpeedInspector
import com.example.ui.components.TextStyleInspector
import com.example.ui.components.TransitionInspector
import com.example.ui.components.VideoOverlayChromaInspector
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SlateDim
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TrackAudioRose
import com.example.ui.theme.TrackOverlayEmerald
import com.example.ui.theme.TrackVideoBlue
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.InspectorTab
import com.example.viewmodel.SelectedItem

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fonts by viewModel.availableFonts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Play-Policy Compliant Zero-Permission Pickers
    val pickMainVideos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMainVideos(uris)
    }

    val pickMainPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMainPhotos(uris)
    }

    val pickOverlayPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.importImageOverlay(it) }
    }

    val pickOverlayVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.importVideoOverlay(it) }
    }

    val pickAudioFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importAudioTrack(it) }
    }

    val pickFontFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importCustomFont(it) }
    }

    val pickCubeLutFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importCubeLut(it) }
    }

    val requestVoiceoverMicPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceoverRecording()
        }
    }

    val onToggleVoiceover: () -> Unit = {
        if (state.isRecordingVoiceover) {
            viewModel.stopVoiceoverRecording()
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                viewModel.startVoiceoverRecording()
            } else {
                requestVoiceoverMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.feedback.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler {
        when {
            state.showProCamera -> viewModel.closeProCamera()
            state.isSamplingChromaColor -> viewModel.cancelChromaColorSampling()
            state.activeInspector != InspectorTab.NONE -> viewModel.closeInspector()
            else -> {
                viewModel.pausePlayback()
                viewModel.saveNow()
                onNavigateBack()
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
        ) {
            // 1. Studio Top Bar
            StudioTopBar(
                title = state.projectTitle,
                aspectRatioLabel = state.aspectRatio.label,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                undoLabel = state.history.undoLabel,
                redoLabel = state.history.redoLabel,
                historyCount = state.history.totalCount,
                isImporting = state.isImporting,
                onBack = {
                    viewModel.pausePlayback()
                    viewModel.saveNow()
                    onNavigateBack()
                },
                onEditTitle = { showRenameDialog = true },
                onToggleAspect = { viewModel.openInspector(InspectorTab.CANVAS) },
                onOpenProCamera = { viewModel.openProCamera() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onOpenHistory = { viewModel.openHistorySheet() },
                onExport = { viewModel.openExportSheet() }
            )

            // 2. Adaptive Workspace (Portrait stacked vs Wide/Landscape split monitor)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val isExpandedWide = maxWidth > 720.dp

                if (isExpandedWide) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(0.54f).fillMaxWidth()) {
                            PreviewStage(
                                aspectRatio = state.aspectRatio,
                                timeline = state.timeline,
                                playheadMs = state.playheadMs,
                                isPlaying = state.isPlaying,
                                selection = state.selection,
                                availableFonts = fonts,
                                snapGuideX = state.snapGuideX,
                                snapGuideY = state.snapGuideY,
                                textMotionReplayNonce = state.textMotionReplayNonce,
                                showScopes = state.showVideoScopes,
                                scopeMode = state.scopeMode,
                                onToggleScopeMode = { viewModel.setScopeMode(it) },
                                isSamplingChromaColor = state.isSamplingChromaColor,
                                onSampledChromaColor = { viewModel.applySampledChromaColor(it) },
                                onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                                onClearSelection = { viewModel.selectItem(SelectedItem.None) },
                                onBeginOverlayGesture = { viewModel.beginOverlayGesture() },
                                onTransformOverlay = { id, dx, dy, s, r -> viewModel.transformOverlayLive(id, dx, dy, s, r) },
                                onEndOverlayGesture = { viewModel.endOverlayGesture() },
                                onDeleteOverlay = {
                                    viewModel.selectItem(SelectedItem.OverlayItem(it))
                                    viewModel.deleteSelected()
                                },
                                onQuickImportVideo = {
                                    pickMainVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
                                onQuickImportPhoto = {
                                    pickMainPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (state.activeInspector != InspectorTab.NONE) {
                                Box(
                                    modifier = Modifier
                                        .width(340.dp)
                                        .fillMaxHeight()
                                        .background(InspectorCharcoal)
                                        .border(1.dp, BorderHairline)
                                ) {
                                    ActiveInspectorHost(
                                        state = state,
                                        fonts = fonts,
                                        viewModel = viewModel,
                                        onImportFont = {
                                            pickFontFile.launch(
                                                arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")
                                            )
                                        },
                                        onImportCubeLut = {
                                            pickCubeLutFile.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                                        },
                                        onToggleVoiceover = onToggleVoiceover
                                    )
                                }
                            }
                        }

                        TransportControlBar(
                            playheadMs = state.playheadMs,
                            totalDurationMs = state.timeline.totalDurationMs,
                            isPlaying = state.isPlaying,
                            zoom = state.timelineZoom,
                            showScopes = state.showVideoScopes,
                            hasAudioBeats = state.timeline.audioClips.any { it.timelineBeatPositionsMs().isNotEmpty() },
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onStepBack = { viewModel.stepFrame(-100L) },
                            onStepForward = { viewModel.stepFrame(100L) },
                            onSnapPrevBeat = { viewModel.snapPlayheadToBeat(next = false) },
                            onSnapNextBeat = { viewModel.snapPlayheadToBeat(next = true) },
                            onToggleScopes = { viewModel.toggleVideoScopes() },
                            onQuickSplit = { viewModel.splitSelectedAtPlayhead() },
                            onZoomChange = { viewModel.setTimelineZoom(it) }
                        )

                        MultilayerTimeline(
                            timeline = state.timeline,
                            playheadMs = state.playheadMs,
                            isPlaying = state.isPlaying,
                            zoom = state.timelineZoom,
                            selection = state.selection,
                            onSeek = { viewModel.seekTo(it) },
                            onSelectMainClip = { viewModel.selectItem(SelectedItem.MainClipItem(it)) },
                            onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                            onSelectAudio = { viewModel.selectItem(SelectedItem.AudioItem(it)) },
                            onSelectTransition = { viewModel.selectItem(SelectedItem.TransitionItem(it), InspectorTab.TRANSITION) },
                            onTrimMainClip = { id, ds, de -> viewModel.trimMainClip(id, ds, de) },
                            onTrimOverlay = { id, s, d -> viewModel.trimOverlayTiming(id, s, d) },
                            onTrimAudio = { id, dt, ts, te -> viewModel.trimAudioClip(id, dt, ts, te) },
                            modifier = Modifier.weight(0.46f)
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PreviewStage(
                            aspectRatio = state.aspectRatio,
                            timeline = state.timeline,
                            playheadMs = state.playheadMs,
                            isPlaying = state.isPlaying,
                            selection = state.selection,
                            availableFonts = fonts,
                            snapGuideX = state.snapGuideX,
                            snapGuideY = state.snapGuideY,
                            textMotionReplayNonce = state.textMotionReplayNonce,
                            showScopes = state.showVideoScopes,
                            scopeMode = state.scopeMode,
                            onToggleScopeMode = { viewModel.setScopeMode(it) },
                            isSamplingChromaColor = state.isSamplingChromaColor,
                            onSampledChromaColor = { viewModel.applySampledChromaColor(it) },
                            onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                            onClearSelection = { viewModel.selectItem(SelectedItem.None) },
                            onBeginOverlayGesture = { viewModel.beginOverlayGesture() },
                            onTransformOverlay = { id, dx, dy, s, r -> viewModel.transformOverlayLive(id, dx, dy, s, r) },
                            onEndOverlayGesture = { viewModel.endOverlayGesture() },
                            onDeleteOverlay = {
                                viewModel.selectItem(SelectedItem.OverlayItem(it))
                                viewModel.deleteSelected()
                            },
                            onQuickImportVideo = {
                                pickMainVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            onQuickImportPhoto = {
                                pickMainPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(if (state.activeInspector == InspectorTab.NONE) 0.48f else 0.38f)
                        )

                        TransportControlBar(
                            playheadMs = state.playheadMs,
                            totalDurationMs = state.timeline.totalDurationMs,
                            isPlaying = state.isPlaying,
                            zoom = state.timelineZoom,
                            showScopes = state.showVideoScopes,
                            hasAudioBeats = state.timeline.audioClips.any { it.timelineBeatPositionsMs().isNotEmpty() },
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onStepBack = { viewModel.stepFrame(-100L) },
                            onStepForward = { viewModel.stepFrame(100L) },
                            onSnapPrevBeat = { viewModel.snapPlayheadToBeat(next = false) },
                            onSnapNextBeat = { viewModel.snapPlayheadToBeat(next = true) },
                            onToggleScopes = { viewModel.toggleVideoScopes() },
                            onQuickSplit = { viewModel.splitSelectedAtPlayhead() },
                            onZoomChange = { viewModel.setTimelineZoom(it) }
                        )

                        MultilayerTimeline(
                            timeline = state.timeline,
                            playheadMs = state.playheadMs,
                            isPlaying = state.isPlaying,
                            zoom = state.timelineZoom,
                            selection = state.selection,
                            onSeek = { viewModel.seekTo(it) },
                            onSelectMainClip = { viewModel.selectItem(SelectedItem.MainClipItem(it)) },
                            onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                            onSelectAudio = { viewModel.selectItem(SelectedItem.AudioItem(it)) },
                            onSelectTransition = { viewModel.selectItem(SelectedItem.TransitionItem(it), InspectorTab.TRANSITION) },
                            onTrimMainClip = { id, ds, de -> viewModel.trimMainClip(id, ds, de) },
                            onTrimOverlay = { id, s, d -> viewModel.trimOverlayTiming(id, s, d) },
                            onTrimAudio = { id, dt, ts, te -> viewModel.trimAudioClip(id, dt, ts, te) },
                            modifier = Modifier.weight(if (state.activeInspector == InspectorTab.NONE) 0.52f else 0.34f)
                        )

                        AnimatedVisibility(
                            visible = state.activeInspector != InspectorTab.NONE,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                HorizontalDivider(color = BorderHairline)
                                ActiveInspectorHost(
                                    state = state,
                                    fonts = fonts,
                                    viewModel = viewModel,
                                    onImportFont = {
                                        pickFontFile.launch(
                                            arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")
                                        )
                                    },
                                    onImportCubeLut = {
                                        pickCubeLutFile.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                                    },
                                    onToggleVoiceover = onToggleVoiceover
                                )
                            }
                        }
                    }
                }
            }

            // 3. Contextual Studio Bottom Tool Rail
            HorizontalDivider(color = BorderHairline)
            ContextualToolRail(
                state = state,
                onOpenProCamera = { viewModel.openProCamera() },
                onImportVideo = {
                    pickMainVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                onImportPhoto = {
                    pickMainPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImportOverlayPhoto = {
                    pickOverlayPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImportOverlayVideo = {
                    pickOverlayVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                onAddText = { viewModel.addTextOverlay() },
                onImportAudio = { pickAudioFile.launch(arrayOf("audio/*")) },
                onOpenInspector = { viewModel.openInspector(it) },
                onOpenTransitions = { viewModel.openTransitionsPicker() },
                onSplit = { viewModel.splitSelectedAtPlayhead() },
                onDuplicate = { viewModel.duplicateSelected() },
                onDelete = { viewModel.deleteSelected() },
                onReorderMainClip = { id, left -> viewModel.reorderMainClip(id, left) },
                onReorderOverlay = { id, up -> viewModel.reorderOverlayZ(id, up) },
                onClearSelection = { viewModel.selectItem(SelectedItem.None, InspectorTab.NONE) }
            )
        }
    }

    if (state.showHistorySheet) {
        EditHistoryBottomSheet(
            history = state.history,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onJumpToIndex = { idx -> viewModel.jumpToHistoryIndex(idx) },
            onClearHistory = { viewModel.clearHistory() },
            onDismiss = { viewModel.closeHistorySheet() }
        )
    }

    if (state.showExportSheet) {
        ExportBottomSheet(
            projectTitle = state.projectTitle,
            aspectRatio = state.aspectRatio,
            timeline = state.timeline,
            config = state.exportConfig,
            exportState = state.exportState,
            onUpdateConfig = { viewModel.updateExportConfig(it) },
            onStartExport = { viewModel.startMp4Export() },
            onCancelExport = { viewModel.cancelMp4Export() },
            onDismiss = { viewModel.closeExportSheet() }
        )
    }

    if (state.showProCamera) {
        ProCameraModal(
            createOutputFile = { viewModel.createProCameraOutputFile() },
            onCapturedVideo = { file, lut -> viewModel.importProCameraCapture(file, lut) },
            onDismiss = { viewModel.closeProCamera() }
        )
    }

    if (showRenameDialog) {
        var draft by remember(state.projectTitle) { mutableStateOf(state.projectTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = InspectorCharcoal,
            title = { Text("Project Title", color = ChalkWhite) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CinemaAmber,
                        focusedTextColor = ChalkWhite,
                        unfocusedTextColor = ChalkWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProjectTitle(draft)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = SilverMuted)
                }
            }
        )
    }
}

@Composable
private fun StudioTopBar(
    title: String,
    aspectRatioLabel: String,
    canUndo: Boolean,
    canRedo: Boolean,
    undoLabel: String?,
    redoLabel: String?,
    historyCount: Int,
    isImporting: Boolean,
    onBack: () -> Unit,
    onEditTitle: () -> Unit,
    onToggleAspect: () -> Unit,
    onOpenProCamera: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenHistory: () -> Unit,
    onExport: () -> Unit
) {
    Surface(color = StudioSlate) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(54.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp).testTag("editor_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to projects", tint = ChalkWhite)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onEditTitle() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ChalkWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isImporting) {
                CircularProgressIndicator(
                    color = CinemaAmber,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            IconButton(
                onClick = onOpenProCamera,
                modifier = Modifier.size(36.dp).testTag("pro_camera_topbar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VideoCameraBack,
                    contentDescription = "Open Pro Cinema Camera",
                    tint = CinemaAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(ActiveRail)
                    .border(1.dp, BorderHairline, RoundedCornerShape(7.dp))
                    .clickable { onToggleAspect() }
                    .padding(horizontal = 7.dp, vertical = 5.dp)
                    .testTag("aspect_ratio_pill")
            ) {
                Text(text = aspectRatioLabel, style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
            }

            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(36.dp).testTag("undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = undoLabel?.let { "Undo $it" } ?: "Undo",
                    tint = if (canUndo) ChalkWhite else SlateDim,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(36.dp).testTag("redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = redoLabel?.let { "Redo $it" } ?: "Redo",
                    tint = if (canRedo) ChalkWhite else SlateDim,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier.size(36.dp).testTag("history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Open edit history",
                    tint = if (historyCount > 0) CinemaAmber else SilverMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaAmber,
                    contentColor = OnAmber
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp).testTag("export_button")
            ) {
                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun TransportControlBar(
    playheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    zoom: Float,
    showScopes: Boolean,
    hasAudioBeats: Boolean,
    onTogglePlay: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onSnapPrevBeat: () -> Unit,
    onSnapNextBeat: () -> Unit,
    onToggleScopes: () -> Unit,
    onQuickSplit: () -> Unit,
    onZoomChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(InspectorCharcoal)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timecode readout
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = formatTimecode(playheadMs),
                style = MaterialTheme.typography.labelSmall,
                color = CinemaAmber
            )
            Text(
                text = " / ${formatTimecode(totalDurationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = SilverMuted
            )
        }

        // Playback controls
        IconButton(onClick = onStepBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.FastRewind, contentDescription = "Step back", tint = SilverMuted, modifier = Modifier.size(18.dp))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CinemaAmber)
                .clickable { onTogglePlay() }
                .testTag("play_pause_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = OnAmber,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onStepForward, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.FastForward, contentDescription = "Step forward", tint = SilverMuted, modifier = Modifier.size(18.dp))
        }

        // Beat-Sync Navigation, Quick Split & Zoom
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasAudioBeats) {
                IconButton(
                    onClick = onSnapPrevBeat,
                    modifier = Modifier.size(32.dp).testTag("snap_prev_beat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Snap to previous audio beat",
                        tint = TrackAudioRose,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onSnapNextBeat,
                    modifier = Modifier.size(32.dp).testTag("snap_next_beat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Snap to next audio beat",
                        tint = TrackAudioRose,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(
                onClick = onToggleScopes,
                modifier = Modifier.size(32.dp).testTag("toggle_scopes_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    contentDescription = "Toggle Video Scopes",
                    tint = if (showScopes) CinemaAmber else SilverMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onQuickSplit,
                modifier = Modifier.size(36.dp).testTag("transport_split_button")
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = "Split at playhead", tint = CinemaAmber, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onZoomChange(zoom - 0.3f) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out timeline", tint = SilverMuted, modifier = Modifier.size(17.dp))
            }
            IconButton(
                onClick = { onZoomChange(zoom + 0.3f) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in timeline", tint = SilverMuted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun ActiveInspectorHost(
    state: EditorUiState,
    fonts: List<com.example.model.StudioFont>,
    viewModel: EditorViewModel,
    onImportFont: () -> Unit,
    onImportCubeLut: () -> Unit,
    onToggleVoiceover: () -> Unit
) {
    when (state.activeInspector) {
        InspectorTab.FILTERS -> {
            val clip = state.selectedMainClip ?: state.timeline.mainClipAt(state.playheadMs)?.clip
            FilterInspector(
                currentPreset = clip?.filterPreset ?: FilterPreset.ORIGINAL,
                intensity = clip?.filterIntensity ?: 1f,
                onSelectFilter = { p, i -> viewModel.updateSelectedFilter(p, i) },
                onApplyAll = { viewModel.applyFilterToAllClips() },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.ADJUST -> {
            val clip = state.selectedMainClip ?: state.timeline.mainClipAt(state.playheadMs)?.clip
            ColorAdjustInspector(
                clipName = clip?.displayName,
                adjustments = clip?.colorAdjustments ?: ColorAdjustments(),
                showScopes = state.showVideoScopes,
                onToggleScopes = { viewModel.toggleVideoScopes() },
                onImportCubeLut = onImportCubeLut,
                onClearCubeLut = { viewModel.clearSelectedCubeLut() },
                onChange = { viewModel.updateSelectedColorAdjustments(it) },
                onReset = { viewModel.resetSelectedColorAdjustments() },
                onApplyAll = { viewModel.applyColorAdjustmentsToAllClips() },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.TRANSITION -> {
            val mainClips = state.timeline.mainClips
            val seams = remember(mainClips) {
                if (mainClips.size < 2) emptyList()
                else (0 until mainClips.lastIndex).map { idx ->
                    ClipSeamInfo(idx, mainClips[idx], mainClips[idx + 1])
                }
            }
            val transClip = state.selectedTransitionClip ?: seams.firstOrNull()?.fromClip
            TransitionInspector(
                seams = seams,
                selectedAfterClipId = transClip?.id,
                spec = transClip?.transitionToNext ?: TransitionSpec(),
                onSelectSeam = { afterId ->
                    viewModel.selectItem(SelectedItem.TransitionItem(afterId), InspectorTab.TRANSITION)
                },
                onUpdate = { type, dur ->
                    transClip?.let { viewModel.updateTransition(it.id, type, dur) }
                },
                onPreviewSeam = {
                    transClip?.let { viewModel.previewTransitionSeam(it.id) }
                },
                onApplyAll = {
                    transClip?.let {
                        viewModel.applyTransitionToAllCuts(
                            it.transitionToNext.type,
                            it.transitionToNext.durationMs
                        )
                    }
                },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.KEYFRAMES -> {
            val targetId = (state.selection as? SelectedItem.MainClipItem)?.clipId
                ?: state.timeline.mainClipAt(state.playheadMs)?.clip?.id
            val range = state.timeline.mainClipRanges().firstOrNull { it.clip.id == targetId }
            KeyframeInspector(
                clip = range?.clip,
                clipStartMs = range?.startMs ?: 0L,
                playheadMs = state.playheadMs,
                onUpdateTransformAtPlayhead = { viewModel.updateClipTransformAtPlayhead(it) },
                onAddOrRemoveKeyframeAtPlayhead = { viewModel.toggleKeyframeAtPlayhead() },
                onUpdateActiveEasing = { viewModel.updateActiveKeyframeEasing(it) },
                onJumpToKeyframe = { viewModel.seekTo(it) },
                onJumpPrevKeyframe = { viewModel.jumpToRelativeKeyframe(next = false) },
                onJumpNextKeyframe = { viewModel.jumpToRelativeKeyframe(next = true) },
                onApplyPreset = { viewModel.applyMotionPreset(it) },
                onResetAllMotion = { viewModel.resetClipMotion() },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.SPEED -> {
            val clip = state.selectedMainClip ?: state.timeline.mainClipAt(state.playheadMs)?.clip
            SpeedInspector(
                clip = clip,
                onUpdateSpeed = { spd, pitch, keep -> viewModel.updateSelectedClipSpeed(spd, pitch, keep) },
                onReset = { viewModel.resetSelectedClipSpeed() },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.AUDIO -> {
            val audioClip = state.selectedAudioClip
            val mainClip = state.selectedMainClip ?: state.timeline.mainClipAt(state.playheadMs)?.clip
            val label = audioClip?.displayName ?: mainClip?.displayName ?: "Track"
            val settings = audioClip?.audio ?: mainClip?.audio ?: AudioSettings()
            AudioMixerInspector(
                trackLabel = label,
                settings = settings,
                isRecordingVoiceover = state.isRecordingVoiceover,
                voiceoverLevel = state.voiceoverLevel,
                voiceoverElapsedMs = state.voiceoverElapsedMs,
                onToggleVoiceoverRecording = onToggleVoiceover,
                onChange = { viewModel.updateSelectedAudioSettings(it) },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.TEXT_STYLE -> {
            val overlay = state.selectedOverlay
            if (overlay != null && overlay.type == OverlayType.TEXT) {
                TextStyleInspector(
                    overlay = overlay,
                    availableFonts = fonts,
                    onUpdate = { viewModel.updateSelectedTextOverlay(it) },
                    onReplayTextMotion = { viewModel.replaySelectedTextMotion() },
                    onImportFont = onImportFont,
                    onMoveLayerZ = { up -> viewModel.reorderOverlayZ(overlay.id, up) },
                    onResetTransform = { viewModel.resetOverlayTransform(overlay.id) },
                    onClose = { viewModel.closeInspector() }
                )
            }
        }
        InspectorTab.IMAGE_OVERLAY -> {
            val overlay = state.selectedOverlay
            if (overlay != null && overlay.type == OverlayType.IMAGE) {
                ImageOverlayInspector(
                    overlay = overlay,
                    onUpdate = { op, cr, bm -> viewModel.updateSelectedImageOverlay(op, cr, bm) },
                    onMoveLayerZ = { up -> viewModel.reorderOverlayZ(overlay.id, up) },
                    onResetTransform = { viewModel.resetOverlayTransform(overlay.id) },
                    onClose = { viewModel.closeInspector() }
                )
            }
        }
        InspectorTab.VIDEO_OVERLAY -> {
            val overlay = state.selectedOverlay
            if (overlay != null && overlay.type == OverlayType.VIDEO) {
                VideoOverlayChromaInspector(
                    overlay = overlay,
                    isSamplingColor = state.isSamplingChromaColor,
                    onToggleColorPicker = { viewModel.toggleChromaColorSampling() },
                    onUpdateOverlay = { viewModel.updateSelectedVideoOverlay(it) },
                    onMoveLayerZ = { up -> viewModel.reorderOverlayZ(overlay.id, up) },
                    onResetTransform = { viewModel.resetOverlayTransform(overlay.id) },
                    onClose = { viewModel.closeInspector() }
                )
            }
        }
        InspectorTab.CANVAS -> {
            CanvasAspectInspector(
                current = state.aspectRatio,
                onSelect = { viewModel.updateAspectRatio(it) },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.FONTS -> {
            FontLibraryInspector(
                fonts = fonts,
                onImportFont = onImportFont,
                onDeleteFont = { viewModel.deleteCustomFont(it) },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.NONE -> Unit
    }
}

@Composable
private fun ContextualToolRail(
    state: EditorUiState,
    onOpenProCamera: () -> Unit,
    onImportVideo: () -> Unit,
    onImportPhoto: () -> Unit,
    onImportOverlayPhoto: () -> Unit,
    onImportOverlayVideo: () -> Unit,
    onAddText: () -> Unit,
    onImportAudio: () -> Unit,
    onOpenInspector: (InspectorTab) -> Unit,
    onOpenTransitions: () -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onReorderMainClip: (String, Boolean) -> Unit,
    onReorderOverlay: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit
) {
    Surface(color = StudioSlate) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (val sel = state.selection) {
                is SelectedItem.None, is SelectedItem.TransitionItem -> {
                    ToolRailButton(
                        icon = Icons.Default.VideoCameraBack,
                        label = "Pro Cam",
                        tint = CinemaAmber,
                        onClick = onOpenProCamera,
                        testTag = "tool_pro_camera"
                    )
                    ToolRailButton(
                        icon = Icons.Default.VideoLibrary,
                        label = "+Video",
                        tint = TrackVideoBlue,
                        onClick = onImportVideo,
                        testTag = "tool_import_video"
                    )
                    ToolRailButton(
                        icon = Icons.Default.AddPhotoAlternate,
                        label = "+Photo",
                        tint = TrackVideoBlue,
                        onClick = onImportPhoto,
                        testTag = "tool_import_photo"
                    )
                    ToolRailButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Vid Layer",
                        tint = TrackOverlayEmerald,
                        onClick = onImportOverlayVideo,
                        testTag = "tool_add_video_overlay"
                    )
                    ToolRailButton(
                        icon = Icons.Default.Layers,
                        label = "PNG Layer",
                        tint = TrackOverlayEmerald,
                        onClick = onImportOverlayPhoto,
                        testTag = "tool_add_image_overlay"
                    )
                    ToolRailButton(
                        icon = Icons.Default.TextFields,
                        label = "Text",
                        tint = CinemaAmber,
                        onClick = onAddText,
                        testTag = "tool_add_text"
                    )
                    ToolRailButton(
                        icon = Icons.Default.LibraryMusic,
                        label = "Audio",
                        tint = TrackAudioRose,
                        onClick = onImportAudio,
                        testTag = "tool_import_audio"
                    )
                    ToolRailButton(
                        icon = Icons.Default.Compare,
                        label = "Transitions",
                        tint = CinemaAmber,
                        isActive = state.activeInspector == InspectorTab.TRANSITION,
                        onClick = onOpenTransitions,
                        testTag = "tool_transitions"
                    )
                    ToolRailButton(
                        icon = Icons.Default.Animation,
                        label = "Motion",
                        tint = TrackOverlayEmerald,
                        isActive = state.activeInspector == InspectorTab.KEYFRAMES,
                        onClick = { onOpenInspector(InspectorTab.KEYFRAMES) },
                        testTag = "tool_keyframes"
                    )
                    ToolRailButton(
                        icon = Icons.Default.Speed,
                        label = "Speed",
                        tint = TrackAudioRose,
                        isActive = state.activeInspector == InspectorTab.SPEED,
                        onClick = { onOpenInspector(InspectorTab.SPEED) },
                        testTag = "tool_speed"
                    )
                    ToolRailButton(
                        icon = Icons.Default.AutoAwesome,
                        label = "Filters",
                        isActive = state.activeInspector == InspectorTab.FILTERS,
                        onClick = { onOpenInspector(InspectorTab.FILTERS) },
                        testTag = "tool_filters"
                    )
                    ToolRailButton(
                        icon = Icons.Default.Tune,
                        label = "Color",
                        isActive = state.activeInspector == InspectorTab.ADJUST,
                        onClick = { onOpenInspector(InspectorTab.ADJUST) },
                        testTag = "tool_adjust"
                    )
                    ToolRailButton(
                        icon = Icons.Default.FontDownload,
                        label = "Fonts",
                        isActive = state.activeInspector == InspectorTab.FONTS,
                        onClick = { onOpenInspector(InspectorTab.FONTS) },
                        testTag = "tool_fonts"
                    )
                    ToolRailButton(
                        icon = Icons.Default.AspectRatio,
                        label = "Canvas",
                        isActive = state.activeInspector == InspectorTab.CANVAS,
                        onClick = { onOpenInspector(InspectorTab.CANVAS) },
                        testTag = "tool_canvas"
                    )
                }

                is SelectedItem.MainClipItem -> {
                    ToolRailButton(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Done", onClick = onClearSelection)
                    ToolRailButton(icon = Icons.Default.ContentCut, label = "Split", tint = CinemaAmber, onClick = onSplit, testTag = "clip_split_button")
                    ToolRailButton(icon = Icons.Default.Animation, label = "Motion", tint = TrackOverlayEmerald, isActive = state.activeInspector == InspectorTab.KEYFRAMES, onClick = { onOpenInspector(InspectorTab.KEYFRAMES) }, testTag = "clip_keyframes_button")
                    ToolRailButton(icon = Icons.Default.Speed, label = "Speed", tint = TrackAudioRose, isActive = state.activeInspector == InspectorTab.SPEED, onClick = { onOpenInspector(InspectorTab.SPEED) }, testTag = "clip_speed_button")
                    ToolRailButton(icon = Icons.Default.Compare, label = "Transition", tint = CinemaAmber, isActive = state.activeInspector == InspectorTab.TRANSITION, onClick = onOpenTransitions, testTag = "clip_transition_button")
                    ToolRailButton(icon = Icons.Default.AutoAwesome, label = "Filters", isActive = state.activeInspector == InspectorTab.FILTERS, onClick = { onOpenInspector(InspectorTab.FILTERS) })
                    ToolRailButton(icon = Icons.Default.Tune, label = "Color", isActive = state.activeInspector == InspectorTab.ADJUST, onClick = { onOpenInspector(InspectorTab.ADJUST) }, testTag = "clip_color_button")
                    ToolRailButton(icon = Icons.Default.GraphicEq, label = "Audio", isActive = state.activeInspector == InspectorTab.AUDIO, onClick = { onOpenInspector(InspectorTab.AUDIO) })
                    ToolRailButton(icon = Icons.Default.ChevronLeft, label = "Move ◀", onClick = { onReorderMainClip(sel.clipId, true) })
                    ToolRailButton(icon = Icons.Default.ChevronRight, label = "Move ▶", onClick = { onReorderMainClip(sel.clipId, false) })
                    ToolRailButton(icon = Icons.Default.ContentCopy, label = "Copy", onClick = onDuplicate)
                    ToolRailButton(icon = Icons.Default.DeleteOutline, label = "Delete", tint = ErrorCrimson, onClick = onDelete, testTag = "clip_delete_button")
                }

                is SelectedItem.OverlayItem -> {
                    val overlayType = state.selectedOverlay?.type ?: OverlayType.IMAGE
                    val styleTab = when (overlayType) {
                        OverlayType.TEXT -> InspectorTab.TEXT_STYLE
                        OverlayType.VIDEO -> InspectorTab.VIDEO_OVERLAY
                        OverlayType.IMAGE -> InspectorTab.IMAGE_OVERLAY
                    }
                    ToolRailButton(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Done", onClick = onClearSelection)
                    ToolRailButton(icon = Icons.Default.ContentCut, label = "Split", tint = CinemaAmber, onClick = onSplit, testTag = "overlay_split_button")
                    ToolRailButton(
                        icon = when (overlayType) {
                            OverlayType.TEXT -> Icons.Default.TextFields
                            OverlayType.VIDEO -> Icons.Default.AutoFixHigh
                            OverlayType.IMAGE -> Icons.Default.Layers
                        },
                        label = when (overlayType) {
                            OverlayType.TEXT -> "Text/FX"
                            OverlayType.VIDEO -> "Chroma"
                            OverlayType.IMAGE -> "Layer"
                        },
                        tint = if (overlayType == OverlayType.VIDEO) TrackOverlayEmerald else ChalkWhite,
                        isActive = state.activeInspector == styleTab,
                        onClick = { onOpenInspector(styleTab) },
                        testTag = when (overlayType) {
                            OverlayType.TEXT -> "overlay_text_style_button"
                            OverlayType.VIDEO -> "overlay_chroma_key_button"
                            OverlayType.IMAGE -> "overlay_image_style_button"
                        }
                    )
                    ToolRailButton(icon = Icons.Default.ChevronLeft, label = "Back Z", onClick = { onReorderOverlay(sel.overlayId, false) })
                    ToolRailButton(icon = Icons.Default.ChevronRight, label = "Front Z", onClick = { onReorderOverlay(sel.overlayId, true) })
                    ToolRailButton(icon = Icons.Default.ContentCopy, label = "Copy", onClick = onDuplicate)
                    ToolRailButton(icon = Icons.Default.DeleteOutline, label = "Delete", tint = ErrorCrimson, onClick = onDelete)
                }

                is SelectedItem.AudioItem -> {
                    ToolRailButton(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Done", onClick = onClearSelection)
                    ToolRailButton(icon = Icons.Default.ContentCut, label = "Split", tint = CinemaAmber, onClick = onSplit, testTag = "audio_split_button")
                    ToolRailButton(
                        icon = Icons.Default.GraphicEq,
                        label = "Mixer",
                        tint = TrackAudioRose,
                        isActive = state.activeInspector == InspectorTab.AUDIO,
                        onClick = { onOpenInspector(InspectorTab.AUDIO) }
                    )
                    ToolRailButton(icon = Icons.Default.ContentCopy, label = "Copy", onClick = onDuplicate)
                    ToolRailButton(icon = Icons.Default.DeleteOutline, label = "Delete", tint = ErrorCrimson, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun ToolRailButton(
    icon: ImageVector,
    label: String,
    tint: Color = ChalkWhite,
    isActive: Boolean = false,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Column(
        modifier = Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) AmberContainer else ActiveRail)
            .border(
                width = 1.dp,
                color = if (isActive) CinemaAmber else BorderHairline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) CinemaAmber else tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) CinemaAmber else ChalkWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
