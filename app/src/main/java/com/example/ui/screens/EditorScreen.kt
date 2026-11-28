package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
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
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
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
import androidx.compose.runtime.DisposableEffect
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
import com.example.model.AiCutoutSettings
import com.example.model.AudioSettings
import com.example.model.ClipTransform
import com.example.model.ColorAdjustments
import com.example.model.FilterPreset
import com.example.model.MainMediaType
import com.example.model.MaskSettings
import com.example.model.OverlayType
import com.example.model.TransitionSpec
import com.example.model.formatTimecode
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.rememberCoroutineScope
import com.example.automation.LocalMcpJsonRpcServer
import com.example.data.RenderJobStatus
import com.example.media.RenderQueueManager
import com.example.security.AiCaptionProvider
import com.example.security.FirebaseAuthManager
import com.example.security.SecureCredentialStore
import com.example.ui.components.AiCaptionSetupBottomSheet
import com.example.ui.components.AiCreativeStudioInspector
import com.example.ui.components.AudioMixerInspector
import com.example.ui.components.AutomationConsoleBottomSheet
import com.example.ui.components.CanvasAspectInspector
import com.example.ui.components.CaptionStudioInspector
import com.example.ui.components.LocalTtsStudioBottomSheet
import com.example.ui.components.ClipSeamInfo
import com.example.ui.components.ColorAdjustInspector
import com.example.ui.components.CutoutInspector
import com.example.ui.components.EditHistoryBottomSheet
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.FilterInspector
import com.example.ui.components.FontLibraryInspector
import com.example.ui.components.ImageOverlayInspector
import com.example.ui.components.KeyframeInspector
import com.example.ui.components.KineMasterLandscapeWorkspace
import com.example.ui.components.LowerThirdTemplatesInspector
import com.example.ui.components.MaskAndTransformInspector
import com.example.ui.components.MultilayerTimeline
import com.example.ui.components.PreviewStage
import com.example.ui.components.ProCameraModal
import com.example.ui.components.RenderQueueBottomSheet
import com.example.ui.components.SpeedInspector
import com.example.ui.components.TextStyleInspector
import com.example.ui.components.TransformCropZoomInspector
import com.example.ui.components.TransitionInspector
import com.example.ui.components.VideoOverlayChromaInspector
import com.example.ui.components.VisualFxInspector
import com.example.ui.components.YouTubeStudioBottomSheet
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
import com.example.ui.theme.TrackFxViolet
import com.example.ui.theme.TrackOverlayEmerald
import com.example.ui.theme.TrackVideoBlue
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.InspectorTab
import com.example.viewmodel.SelectedItem

private enum class WorkspaceLayoutMode(val label: String) {
    BALANCED("Balanced"),
    MONITOR_FOCUS("Monitor Focus"),
    TIMELINE_FOCUS("Timeline Focus"),
    TIMELINE_TOP("Timeline Top");

    fun next(): WorkspaceLayoutMode {
        val list = entries
        return list[(ordinal + 1) % list.size]
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fonts by viewModel.availableFonts.collectAsStateWithLifecycle()
    val renderQueueManager = remember(context) { RenderQueueManager.getInstance(context) }
    val renderJobs by renderQueueManager.jobs.collectAsStateWithLifecycle()
    val credentialStore = remember(context) { SecureCredentialStore.getInstance(context) }
    val aiCredState by credentialStore.aiState.collectAsStateWithLifecycle()
    val authManager = remember(context) { FirebaseAuthManager.getInstance(context) }
    val authState by authManager.authState.collectAsStateWithLifecycle()
    val mcpServer = remember { LocalMcpJsonRpcServer.getSharedInstance() }
    val mcpStatus by mcpServer.status.collectAsStateWithLifecycle()
    val activeQueueCount = renderJobs.count {
        it.status == RenderJobStatus.RENDERING || it.status == RenderJobStatus.QUEUED
    }
    var workspaceLayout by remember { mutableStateOf(WorkspaceLayoutMode.BALANCED) }
    var aiSetupSheetTab by remember { mutableStateOf<Int?>(null) }
    var showRenderQueueSheet by remember { mutableStateOf(false) }
    var showLocalTtsSheet by remember { mutableStateOf(false) }
    var showAutomationConsole by remember { mutableStateOf(false) }
    var youtubeSheetTab by remember { mutableStateOf<Int?>(null) }
    var youtubeUploadUri by remember { mutableStateOf<String?>(null) }
    var youtubeUploadTitle by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var showRenameDialog by remember { mutableStateOf(false) }

    val requiresGoogleSignInForCloud =
        aiCredState.activeProvider == AiCaptionProvider.FIREBASE_BUILTIN && !authState.isSignedIn

    DisposableEffect(viewModel) {
        mcpServer.attachBridge(viewModel.automationBridge)
        onDispose {
            mcpServer.detachBridge(viewModel.automationBridge)
        }
    }

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

    val pickSrtFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importSrtFromUri(it) }
    }

    val createSrtFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        uri?.let { viewModel.exportSrtToUri(it) }
    }

    val pickCutoutBackgroundPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.importCustomCutoutBackground(it) }
    }

    val requestVoiceoverMicPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceoverRecording()
        }
    }

    val requestOfflineMicPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startOfflineMicDictation()
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

    val onToggleOfflineMic: () -> Unit = {
        if (state.isLiveMicDictating) {
            viewModel.stopOfflineMicDictation()
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                viewModel.startOfflineMicDictation()
            } else {
                requestOfflineMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val onToggleOrientation: (Boolean) -> Unit = { toLandscape ->
        context.findHostActivity()?.requestedOrientation = if (toLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            context.findHostActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    val onImportFontAction: () -> Unit = {
        pickFontFile.launch(
            arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")
        )
    }
    val onImportCubeLutAction: () -> Unit = {
        pickCubeLutFile.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
    }
    val onImportSrtAction: () -> Unit = {
        pickSrtFile.launch(arrayOf("application/x-subrip", "text/plain", "*/*"))
    }
    val onExportSrtAction: () -> Unit = {
        val safeName = state.projectTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "lumacut" }
        createSrtFile.launch("${safeName}_captions.srt")
    }
    val onPickCutoutBgAction: () -> Unit = {
        pickCutoutBackgroundPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
        ) {
            val isHorizontalKineMaster = maxWidth > maxHeight

            if (isHorizontalKineMaster) {
                KineMasterLandscapeWorkspace(
                    state = state,
                    isTimelineExpanded = state.isTimelineExpanded,
                    onToggleTimelineExpanded = { viewModel.toggleTimelineExpanded() },
                    onSwitchToPortrait = { onToggleOrientation(false) },
                    onNavigateBack = {
                        viewModel.pausePlayback()
                        viewModel.saveNow()
                        onNavigateBack()
                    },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onSplit = { viewModel.splitSelectedAtPlayhead() },
                    onDeleteSelected = { viewModel.deleteSelected() },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onOpenExport = { viewModel.openExportSheet() },
                    onPickMainMedia = {
                        pickMainVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onPickOverlayImage = {
                        pickOverlayPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onPickOverlayVideo = {
                        pickOverlayVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    onAddTextOverlay = { viewModel.addTextOverlay() },
                    onAddFx = { type -> viewModel.addFxLayer(type, fullFrame = false) },
                    onOpenCutout = { viewModel.openInspector(InspectorTab.CUTOUT) },
                    onOpenCaptions = { viewModel.openInspector(InspectorTab.CAPTIONS) },
                    onPickAudio = { pickAudioFile.launch(arrayOf("audio/*")) },
                    onToggleVoiceover = onToggleVoiceover,
                    onOpenCamera = { viewModel.openProCamera() },
                    onSelectInspectorTab = { tab -> viewModel.openInspector(tab) },
                    onReturnToWheel = { viewModel.closeInspector() },
                    previewContent = { mod ->
                        PreviewStage(
                            aspectRatio = state.aspectRatio,
                            timeline = state.timeline,
                            playheadMs = state.playheadMs,
                            isPlaying = state.isPlaying,
                            seekNonce = state.seekNonce,
                            selection = state.selection,
                            availableFonts = fonts,
                            snapGuideX = state.snapGuideX,
                            snapGuideY = state.snapGuideY,
                            textMotionReplayNonce = state.textMotionReplayNonce,
                            showScopes = state.showVideoScopes,
                            scopeMode = state.scopeMode,
                            onToggleScopeMode = { viewModel.setScopeMode(it) },
                            isSamplingChromaColor = state.isSamplingChromaColor,
                            showCropGuide = state.activeInspector == InspectorTab.TRANSFORM_CROP,
                            onSampledChromaColor = { viewModel.applySampledChromaColor(it) },
                            onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                            onSelectCaption = { viewModel.selectItem(SelectedItem.CaptionItem(it), InspectorTab.CAPTIONS) },
                            onSelectFx = { viewModel.selectFxLayer(it) },
                            onUpdateFxRegion = { id, cx, cy, w, h -> viewModel.updateFxRegionLive(id, cx, cy, w, h) },
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
                            modifier = mod
                        )
                    },
                    dockedInspectorContent = {
                        ActiveInspectorHost(
                            state = state,
                            fonts = fonts,
                            viewModel = viewModel,
                            compactDocked = true,
                            onImportFont = onImportFontAction,
                            onImportCubeLut = onImportCubeLutAction,
                            onImportSrt = onImportSrtAction,
                            onExportSrt = onExportSrtAction,
                            onPickCutoutBackground = onPickCutoutBgAction,
                            onToggleVoiceover = onToggleVoiceover,
                            onToggleOfflineMic = onToggleOfflineMic,
                            activeAiProviderBadge = aiCredState.activeProvider.shortBadge,
                            requiresGoogleSignInForCloud = requiresGoogleSignInForCloud,
                            onGoogleSignIn = {
                                val act = context.findHostActivity()
                                if (act != null) authManager.signInWithGoogle(act, scope)
                                else aiSetupSheetTab = 0
                            },
                            onOpenLocalTtsStudio = { showLocalTtsSheet = true },
                            onOpenAiLoginSetup = { aiSetupSheetTab = 0 },
                            onOpenOfflineModelsSetup = { aiSetupSheetTab = 1 }
                        )
                    },
                    timelineContent = { mod ->
                        MultilayerTimeline(
                            timeline = state.timeline,
                            playheadMs = state.playheadMs,
                            isPlaying = state.isPlaying,
                            zoom = state.timelineZoom,
                            selection = state.selection,
                            onSeek = { viewModel.scrubTo(it) },
                            onPauseForScrub = { viewModel.pausePlayback() },
                            onSelectMainClip = { viewModel.selectItem(SelectedItem.MainClipItem(it)) },
                            onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                            onSelectAudio = { viewModel.selectItem(SelectedItem.AudioItem(it)) },
                            onSelectTransition = { viewModel.selectItem(SelectedItem.TransitionItem(it), InspectorTab.TRANSITION) },
                            onSelectCaption = { viewModel.selectItem(SelectedItem.CaptionItem(it), InspectorTab.CAPTIONS) },
                            onSelectFx = { viewModel.selectFxLayer(it) },
                            onTrimMainClip = { id, ds, de -> viewModel.trimMainClip(id, ds, de) },
                            onTrimOverlay = { id, s, d -> viewModel.trimOverlayTiming(id, s, d) },
                            onTrimAudio = { id, dt, ts, te -> viewModel.trimAudioClip(id, dt, ts, te) },
                            onMoveCaption = { id, s, e -> viewModel.moveOrTrimCaption(id, s, e) },
                            onMoveFx = { id, s, d -> viewModel.moveOrTrimFxLayer(id, s, d) },
                            modifier = mod
                        )
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg)
                ) {
                    // 1. Studio Top Bar (with 1-Tap Horizontal KineMaster Mode Switch)
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
                        onSwitchToLandscape = { onToggleOrientation(true) },
                        onOpenProCamera = { viewModel.openProCamera() },
                        activeQueueCount = activeQueueCount,
                        onOpenRenderQueue = { showRenderQueueSheet = true },
                        onOpenYouTube = {
                            youtubeUploadUri = null
                            youtubeUploadTitle = state.projectTitle
                            youtubeSheetTab = 0
                        },
                        workspaceLayoutLabel = workspaceLayout.label,
                        onCycleWorkspaceLayout = { workspaceLayout = workspaceLayout.next() },
                        isMcpRunning = mcpStatus.isRunning,
                        onOpenAutomationConsole = { showAutomationConsole = true },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onOpenHistory = { viewModel.openHistorySheet() },
                        onExport = { viewModel.openExportSheet() }
                    )

                    // 2. Customizable Vertical Workspace (Swappable & Resizable Monitor / Multi-Track Timeline)
                    val monitorWeight = when (workspaceLayout) {
                        WorkspaceLayoutMode.BALANCED -> if (state.activeInspector == InspectorTab.NONE) 0.48f else 0.36f
                        WorkspaceLayoutMode.MONITOR_FOCUS -> if (state.activeInspector == InspectorTab.NONE) 0.62f else 0.44f
                        WorkspaceLayoutMode.TIMELINE_FOCUS -> if (state.activeInspector == InspectorTab.NONE) 0.32f else 0.26f
                        WorkspaceLayoutMode.TIMELINE_TOP -> if (state.activeInspector == InspectorTab.NONE) 0.46f else 0.34f
                    }
                    val timelineWeight = when (workspaceLayout) {
                        WorkspaceLayoutMode.BALANCED -> if (state.activeInspector == InspectorTab.NONE) 0.52f else 0.34f
                        WorkspaceLayoutMode.MONITOR_FOCUS -> if (state.activeInspector == InspectorTab.NONE) 0.38f else 0.28f
                        WorkspaceLayoutMode.TIMELINE_FOCUS -> if (state.activeInspector == InspectorTab.NONE) 0.68f else 0.46f
                        WorkspaceLayoutMode.TIMELINE_TOP -> if (state.activeInspector == InspectorTab.NONE) 0.54f else 0.36f
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (workspaceLayout == WorkspaceLayoutMode.TIMELINE_TOP) {
                            MultilayerTimeline(
                                timeline = state.timeline,
                                playheadMs = state.playheadMs,
                                isPlaying = state.isPlaying,
                                zoom = state.timelineZoom,
                                selection = state.selection,
                                onSeek = { viewModel.scrubTo(it) },
                                onPauseForScrub = { viewModel.pausePlayback() },
                                onSelectMainClip = { viewModel.selectItem(SelectedItem.MainClipItem(it)) },
                                onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                                onSelectAudio = { viewModel.selectItem(SelectedItem.AudioItem(it)) },
                                onSelectTransition = { viewModel.selectItem(SelectedItem.TransitionItem(it), InspectorTab.TRANSITION) },
                                onSelectCaption = { viewModel.selectItem(SelectedItem.CaptionItem(it), InspectorTab.CAPTIONS) },
                                onSelectFx = { viewModel.selectFxLayer(it) },
                                onTrimMainClip = { id, ds, de -> viewModel.trimMainClip(id, ds, de) },
                                onTrimOverlay = { id, s, d -> viewModel.trimOverlayTiming(id, s, d) },
                                onTrimAudio = { id, dt, ts, te -> viewModel.trimAudioClip(id, dt, ts, te) },
                                onMoveCaption = { id, s, e -> viewModel.moveOrTrimCaption(id, s, e) },
                                onMoveFx = { id, s, d -> viewModel.moveOrTrimFxLayer(id, s, d) },
                                modifier = Modifier.weight(timelineWeight)
                            )
                        } else {
                            PreviewStage(
                                aspectRatio = state.aspectRatio,
                                timeline = state.timeline,
                                playheadMs = state.playheadMs,
                                isPlaying = state.isPlaying,
                                seekNonce = state.seekNonce,
                                selection = state.selection,
                                availableFonts = fonts,
                                snapGuideX = state.snapGuideX,
                                snapGuideY = state.snapGuideY,
                                textMotionReplayNonce = state.textMotionReplayNonce,
                                showScopes = state.showVideoScopes,
                                scopeMode = state.scopeMode,
                                onToggleScopeMode = { viewModel.setScopeMode(it) },
                                isSamplingChromaColor = state.isSamplingChromaColor,
                                showCropGuide = state.activeInspector == InspectorTab.TRANSFORM_CROP,
                                onSampledChromaColor = { viewModel.applySampledChromaColor(it) },
                                onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                                onSelectCaption = { viewModel.selectItem(SelectedItem.CaptionItem(it), InspectorTab.CAPTIONS) },
                                onSelectFx = { viewModel.selectFxLayer(it) },
                                onUpdateFxRegion = { id, cx, cy, w, h -> viewModel.updateFxRegionLive(id, cx, cy, w, h) },
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
                                modifier = Modifier.weight(monitorWeight)
                            )
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

                        if (workspaceLayout == WorkspaceLayoutMode.TIMELINE_TOP) {
                            PreviewStage(
                                aspectRatio = state.aspectRatio,
                                timeline = state.timeline,
                                playheadMs = state.playheadMs,
                                isPlaying = state.isPlaying,
                                seekNonce = state.seekNonce,
                                selection = state.selection,
                                availableFonts = fonts,
                                snapGuideX = state.snapGuideX,
                                snapGuideY = state.snapGuideY,
                                textMotionReplayNonce = state.textMotionReplayNonce,
                                showScopes = state.showVideoScopes,
                                scopeMode = state.scopeMode,
                                onToggleScopeMode = { viewModel.setScopeMode(it) },
                                isSamplingChromaColor = state.isSamplingChromaColor,
                                showCropGuide = state.activeInspector == InspectorTab.TRANSFORM_CROP,
                                onSampledChromaColor = { viewModel.applySampledChromaColor(it) },
                                onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                                onSelectCaption = { viewModel.selectItem(SelectedItem.CaptionItem(it), InspectorTab.CAPTIONS) },
                                onSelectFx = { viewModel.selectFxLayer(it) },
                                onUpdateFxRegion = { id, cx, cy, w, h -> viewModel.updateFxRegionLive(id, cx, cy, w, h) },
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
                                modifier = Modifier.weight(monitorWeight)
                            )
                        } else {
                            MultilayerTimeline(
                                timeline = state.timeline,
                                playheadMs = state.playheadMs,
                                isPlaying = state.isPlaying,
                                zoom = state.timelineZoom,
                                selection = state.selection,
                                onSeek = { viewModel.scrubTo(it) },
                                onPauseForScrub = { viewModel.pausePlayback() },
                                onSelectMainClip = { viewModel.selectItem(SelectedItem.MainClipItem(it)) },
                                onSelectOverlay = { viewModel.selectItem(SelectedItem.OverlayItem(it)) },
                                onSelectAudio = { viewModel.selectItem(SelectedItem.AudioItem(it)) },
                                onSelectTransition = { viewModel.selectItem(SelectedItem.TransitionItem(it), InspectorTab.TRANSITION) },
                                onSelectCaption = { viewModel.selectItem(SelectedItem.CaptionItem(it), InspectorTab.CAPTIONS) },
                                onSelectFx = { viewModel.selectFxLayer(it) },
                                onTrimMainClip = { id, ds, de -> viewModel.trimMainClip(id, ds, de) },
                                onTrimOverlay = { id, s, d -> viewModel.trimOverlayTiming(id, s, d) },
                                onTrimAudio = { id, dt, ts, te -> viewModel.trimAudioClip(id, dt, ts, te) },
                                onMoveCaption = { id, s, e -> viewModel.moveOrTrimCaption(id, s, e) },
                                onMoveFx = { id, s, d -> viewModel.moveOrTrimFxLayer(id, s, d) },
                                modifier = Modifier.weight(timelineWeight)
                            )
                        }

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
                                    compactDocked = false,
                                    onImportFont = onImportFontAction,
                                    onImportCubeLut = onImportCubeLutAction,
                                    onImportSrt = onImportSrtAction,
                                    onExportSrt = onExportSrtAction,
                                    onPickCutoutBackground = onPickCutoutBgAction,
                                    onToggleVoiceover = onToggleVoiceover,
                                    onToggleOfflineMic = onToggleOfflineMic,
                                    activeAiProviderBadge = aiCredState.activeProvider.shortBadge,
                                    requiresGoogleSignInForCloud = requiresGoogleSignInForCloud,
                                    onGoogleSignIn = {
                                        val act = context.findHostActivity()
                                        if (act != null) authManager.signInWithGoogle(act, scope)
                                        else aiSetupSheetTab = 0
                                    },
                                    onOpenLocalTtsStudio = { showLocalTtsSheet = true },
                                    onOpenAiLoginSetup = { aiSetupSheetTab = 0 },
                                    onOpenOfflineModelsSetup = { aiSetupSheetTab = 1 }
                                )
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
                        onInsertFreezeFrame = { viewModel.insertFreezeFrameAtPlayhead() },
                        onSplit = { viewModel.splitSelectedAtPlayhead() },
                        onDuplicate = { viewModel.duplicateSelected() },
                        onDelete = { viewModel.deleteSelected() },
                        onReorderMainClip = { id, left -> viewModel.reorderMainClip(id, left) },
                        onReorderOverlay = { id, up -> viewModel.reorderOverlayZ(id, up) },
                        onOpenLocalTts = { showLocalTtsSheet = true },
                        onOpenAutomationConsole = { showAutomationConsole = true },
                        onClearSelection = { viewModel.selectItem(SelectedItem.None, InspectorTab.NONE) }
                    )
                }
            }
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
            onQueueBackgroundExport = { viewModel.queueCurrentProjectForBackgroundExport() },
            onUploadToYouTube = { uri, title ->
                viewModel.closeExportSheet()
                youtubeUploadUri = uri
                youtubeUploadTitle = title
                youtubeSheetTab = 1
            },
            onCancelExport = { viewModel.cancelMp4Export() },
            onDismiss = { viewModel.closeExportSheet() }
        )
    }

    if (showRenderQueueSheet) {
        RenderQueueBottomSheet(
            jobs = renderJobs,
            onCancelJob = { renderQueueManager.cancelJob(it) },
            onRetryJob = { renderQueueManager.retryJob(it) },
            onRemoveJob = { renderQueueManager.removeJob(it) },
            onClearFinished = { renderQueueManager.clearFinished() },
            onUploadToYouTube = { uri, title ->
                showRenderQueueSheet = false
                youtubeUploadUri = uri
                youtubeUploadTitle = title
                youtubeSheetTab = 1
            },
            onDismiss = { showRenderQueueSheet = false }
        )
    }

    aiSetupSheetTab?.let { tabIdx ->
        AiCaptionSetupBottomSheet(
            initialTab = tabIdx,
            onSelectLanguageAndTranscribeOffline = { lang ->
                viewModel.runOfflineVideoAutoCaptions(lang)
            },
            onDismiss = { aiSetupSheetTab = null }
        )
    }

    youtubeSheetTab?.let { tabIdx ->
        YouTubeStudioBottomSheet(
            initialTab = tabIdx,
            exportVideoUri = youtubeUploadUri,
            defaultExportTitle = youtubeUploadTitle.ifBlank { state.projectTitle },
            onImportedAsset = { asset -> viewModel.importYouTubeAssetToTimeline(asset) },
            onDismiss = { youtubeSheetTab = null }
        )
    }

    if (showLocalTtsSheet) {
        val captionsScript = remember(state.timeline.captions) {
            state.timeline.captions.sortedBy { it.startTimeMs }.joinToString(". ") { it.text.trim() }
        }
        LocalTtsStudioBottomSheet(
            initialScript = captionsScript,
            captionsScript = captionsScript,
            isSynthesizing = state.localTtsSynthesizing,
            errorMessage = state.localTtsError,
            onSynthesizeToTimeline = { script, lang, profile, pitch, speed ->
                viewModel.synthesizeLocalTtsToTimeline(
                    script = script,
                    languageDisplayName = lang,
                    profile = profile,
                    pitch = pitch,
                    speed = speed,
                    onComplete = { showLocalTtsSheet = false }
                )
            },
            onDismiss = { showLocalTtsSheet = false }
        )
    }

    if (showAutomationConsole) {
        AutomationConsoleBottomSheet(
            server = mcpServer,
            onDismiss = { showAutomationConsole = false }
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
    onSwitchToLandscape: () -> Unit,
    onOpenProCamera: () -> Unit,
    activeQueueCount: Int = 0,
    onOpenRenderQueue: () -> Unit = {},
    onOpenYouTube: () -> Unit = {},
    workspaceLayoutLabel: String = "Balanced",
    onCycleWorkspaceLayout: () -> Unit = {},
    isMcpRunning: Boolean = false,
    onOpenAutomationConsole: () -> Unit = {},
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
                onClick = onSwitchToLandscape,
                modifier = Modifier.size(34.dp).testTag("btn_toggle_orientation")
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = "Switch to Horizontal KineMaster Mode",
                    tint = CinemaAmber,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onOpenYouTube,
                modifier = Modifier.size(34.dp).testTag("youtube_studio_topbar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "YouTube Import and Export",
                    tint = ErrorCrimson,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onCycleWorkspaceLayout,
                modifier = Modifier.size(34.dp).testTag("workspace_layout_topbar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = "Cycle workspace layout ($workspaceLayoutLabel)",
                    tint = SilverMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onOpenAutomationConsole,
                modifier = Modifier.size(34.dp).testTag("mcp_console_topbar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "MCP and JSON-RPC Automation Console",
                    tint = if (isMcpRunning) TrackOverlayEmerald else SilverMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onOpenRenderQueue,
                modifier = Modifier.size(34.dp).testTag("render_queue_topbar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.QueuePlayNext,
                    contentDescription = "Background Render Queue",
                    tint = if (activeQueueCount > 0) TrackOverlayEmerald else SilverMuted,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onOpenProCamera,
                modifier = Modifier.size(34.dp).testTag("pro_camera_topbar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VideoCameraBack,
                    contentDescription = "Open Pro Cinema Camera",
                    tint = CinemaAmber,
                    modifier = Modifier.size(19.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(ActiveRail)
                    .border(1.dp, BorderHairline, RoundedCornerShape(7.dp))
                    .clickable { onToggleAspect() }
                    .padding(horizontal = 6.dp, vertical = 5.dp)
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
    compactDocked: Boolean = false,
    onImportFont: () -> Unit,
    onImportCubeLut: () -> Unit,
    onImportSrt: () -> Unit,
    onExportSrt: () -> Unit,
    onPickCutoutBackground: () -> Unit,
    onToggleVoiceover: () -> Unit,
    onToggleOfflineMic: () -> Unit,
    activeAiProviderBadge: String,
    requiresGoogleSignInForCloud: Boolean = false,
    onGoogleSignIn: () -> Unit = {},
    onOpenLocalTtsStudio: () -> Unit = {},
    onOpenAiLoginSetup: () -> Unit,
    onOpenOfflineModelsSetup: () -> Unit
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
                onInsertFreezeFrame = { viewModel.insertFreezeFrameAtPlayhead() },
                onApplySpeedRamp = { viewModel.applySpeedRampPreset(it) },
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
                canExtractAudio = audioClip == null && mainClip?.mediaType == MainMediaType.VIDEO,
                isRecordingVoiceover = state.isRecordingVoiceover,
                voiceoverLevel = state.voiceoverLevel,
                voiceoverElapsedMs = state.voiceoverElapsedMs,
                onToggleVoiceoverRecording = onToggleVoiceover,
                onExtractAudio = { viewModel.extractAudioFromSelectedClip() },
                onPreviewSfx = { viewModel.previewCinemaSfx(it) },
                onInsertSfx = { viewModel.insertCinemaSfxAtPlayhead(it) },
                onChange = { viewModel.updateSelectedAudioSettings(it) },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.MASK -> {
            val overlay = state.selectedOverlay
            val mainClip = state.selectedMainClip ?: state.timeline.mainClipAt(state.playheadMs)?.clip
            val mask = overlay?.mask ?: mainClip?.mask ?: MaskSettings()
            val label = overlay?.displayName?.ifBlank { "Overlay" } ?: mainClip?.displayName ?: "Clip"
            MaskAndTransformInspector(
                targetLabel = label,
                mask = mask,
                showClipTransformActions = overlay == null && mainClip != null,
                clipTransform = mainClip?.baseTransform,
                onUpdateMask = { viewModel.updateSelectedMask(it) },
                onToggleMaskKeyframe = { viewModel.toggleSelectedMaskKeyframe() },
                onFlipHorizontal = { viewModel.toggleClipFlip(horizontal = true) },
                onFlipVertical = { viewModel.toggleClipFlip(horizontal = false) },
                onRotate90 = { viewModel.rotateClip90() },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.VISUAL_FX -> {
            VisualFxInspector(
                fxLayers = state.timeline.fxLayers,
                selectedFx = state.selectedFxLayer ?: state.timeline.fxLayers.firstOrNull(),
                onAddFx = { type, fullFrame -> viewModel.addFxLayer(type, fullFrame) },
                onSelectFx = { id -> viewModel.selectFxLayer(id) },
                onUpdateFx = { transform -> viewModel.updateSelectedFxLayer(transform) },
                onDeleteSelectedFx = { viewModel.deleteSelected() },
                onClose = { viewModel.closeInspector() },
                compactDocked = compactDocked
            )
        }
        InspectorTab.TRANSFORM_CROP -> {
            val overlay = state.selectedOverlay
            val mainClip = state.selectedMainClip ?: state.timeline.mainClipAt(state.playheadMs)?.clip
            val targetLabel = overlay?.displayName?.ifBlank { "Overlay" } ?: mainClip?.displayName ?: "Clip"
            val currentTransform = if (overlay != null) {
                ClipTransform(
                    offsetX = overlay.offsetX,
                    offsetY = overlay.offsetY,
                    scale = overlay.scale,
                    rotationDegrees = overlay.rotationDegrees,
                    opacity = overlay.opacity,
                    flipHorizontal = overlay.flipHorizontal,
                    flipVertical = overlay.flipVertical,
                    fullFrameFill = overlay.fullFrameFill,
                    crop = overlay.crop
                )
            } else {
                mainClip?.baseTransform ?: ClipTransform()
            }
            TransformCropZoomInspector(
                targetLabel = targetLabel,
                stageAspectRatio = state.aspectRatio.ratio,
                transform = currentTransform,
                panZoom = if (overlay == null) mainClip?.panZoom else null,
                onUpdateTransform = { viewModel.updateSelectedGeometry(it) },
                onUpdatePanZoom = if (overlay == null) { spec -> viewModel.updateSelectedClipPanZoom(spec) } else null,
                onResetAll = { viewModel.resetSelectedGeometry() },
                onClose = { viewModel.closeInspector() },
                compactDocked = compactDocked
            )
        }
        InspectorTab.CUTOUT -> {
            val overlay = state.selectedOverlay
            val mainClip = state.selectedMainClip ?: state.timeline.mainClipAt(state.playheadMs)?.clip
            val targetLabel = overlay?.displayName?.ifBlank { "Overlay" } ?: mainClip?.displayName ?: "Clip"
            val currentCutout = overlay?.aiCutout ?: mainClip?.aiCutout ?: AiCutoutSettings()
            CutoutInspector(
                targetLabel = targetLabel,
                settings = currentCutout,
                onUpdate = { viewModel.updateSelectedCutout(it) },
                onPickCustomBackground = onPickCutoutBackground,
                onClose = { viewModel.closeInspector() },
                compactDocked = compactDocked
            )
        }
        InspectorTab.CAPTIONS -> {
            CaptionStudioInspector(
                captions = state.timeline.captions,
                selectedCaption = state.selectedCaption ?: state.timeline.captions.firstOrNull(),
                safeZone = state.timeline.safeZoneOverlay,
                availableFonts = fonts,
                activeAiProviderBadge = activeAiProviderBadge,
                requiresGoogleSignInForCloud = requiresGoogleSignInForCloud,
                autoCaptionLoading = state.autoCaptionLoading,
                autoCaptionStatus = state.autoCaptionStatus,
                autoCaptionError = state.autoCaptionError,
                isLiveMicDictating = state.isLiveMicDictating,
                liveMicPartialText = state.liveMicPartialText,
                onRunGeminiAutoCaptions = { lang -> viewModel.runGeminiAutoCaptions(lang) },
                onRunOfflineVideoAutoCaptions = { lang -> viewModel.runOfflineVideoAutoCaptions(lang) },
                onToggleOfflineMicDictation = onToggleOfflineMic,
                onOpenAiLoginSetup = onOpenAiLoginSetup,
                onOpenOfflineModelsSetup = onOpenOfflineModelsSetup,
                onOpenLocalTtsStudio = onOpenLocalTtsStudio,
                onGoogleSignIn = onGoogleSignIn,
                onAddCaption = { viewModel.addCaptionAtPlayhead() },
                onSelectCaption = { id ->
                    viewModel.selectItem(SelectedItem.CaptionItem(id), InspectorTab.CAPTIONS)
                    state.timeline.captions.firstOrNull { it.id == id }?.let { viewModel.seekTo(it.startTimeMs) }
                },
                onUpdateCaption = { viewModel.updateSelectedCaption(it) },
                onDeleteSelectedCaption = { viewModel.deleteSelected() },
                onImportSrt = onImportSrt,
                onExportSrt = onExportSrt,
                onUpdateSafeZone = { viewModel.updateSafeZoneOverlay(it) },
                onClose = { viewModel.closeInspector() },
                compactDocked = compactDocked
            )
        }
        InspectorTab.TEMPLATES -> {
            LowerThirdTemplatesInspector(
                availableFonts = fonts,
                onInsertTemplate = { viewModel.insertLowerThirdTemplate(it) },
                onClose = { viewModel.closeInspector() }
            )
        }
        InspectorTab.AI_STUDIO -> {
            AiCreativeStudioInspector(
                aiIdeasLoading = state.aiIdeasLoading,
                aiIdeasResult = state.aiIdeasResult,
                aiIdeasError = state.aiIdeasError,
                aiColoristLoading = state.aiColoristLoading,
                aiColoristNote = state.aiColoristNote,
                aiColoristError = state.aiColoristError,
                aiCaptionsLoading = state.aiCaptionsLoading,
                aiCaptionsError = state.aiCaptionsError,
                onGenerateIdeas = { viewModel.generateAiHooksAndIdeas(it) },
                onInsertHookOverlay = { viewModel.insertAiHookAsOverlay(it) },
                onGenerateColorGrade = { viewModel.generateAiColorGrade(it) },
                onGenerateCaptionsFromScript = { viewModel.generateAiCaptionsFromScript(it) },
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
                backdrop = state.timeline.canvasBackdrop,
                safeZone = state.timeline.safeZoneOverlay,
                onSelect = { viewModel.updateAspectRatio(it) },
                onUpdateBackdrop = { viewModel.updateCanvasBackdrop(it) },
                onUpdateSafeZone = { viewModel.updateSafeZoneOverlay(it) },
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
    onInsertFreezeFrame: () -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onReorderMainClip: (String, Boolean) -> Unit,
    onReorderOverlay: (String, Boolean) -> Unit,
    onOpenLocalTts: () -> Unit = {},
    onOpenAutomationConsole: () -> Unit = {},
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
                        icon = Icons.Default.AutoAwesome,
                        label = "AI Studio",
                        tint = CinemaAmber,
                        isActive = state.activeInspector == InspectorTab.AI_STUDIO,
                        onClick = { onOpenInspector(InspectorTab.AI_STUDIO) },
                        testTag = "tool_ai_studio"
                    )
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
                        icon = Icons.Default.Subtitles,
                        label = "Auto CC",
                        tint = CinemaAmber,
                        isActive = state.activeInspector == InspectorTab.CAPTIONS,
                        onClick = { onOpenInspector(InspectorTab.CAPTIONS) },
                        testTag = "tool_captions"
                    )
                    ToolRailButton(
                        icon = Icons.Default.BlurOn,
                        label = "FX Studio",
                        tint = TrackFxViolet,
                        isActive = state.activeInspector == InspectorTab.VISUAL_FX,
                        onClick = { onOpenInspector(InspectorTab.VISUAL_FX) },
                        testTag = "tool_visual_fx"
                    )
                    ToolRailButton(
                        icon = Icons.Default.Transform,
                        label = "Transform",
                        tint = CinemaAmber,
                        isActive = state.activeInspector == InspectorTab.TRANSFORM_CROP,
                        onClick = { onOpenInspector(InspectorTab.TRANSFORM_CROP) },
                        testTag = "tool_transform_crop"
                    )
                    ToolRailButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Cutout",
                        tint = TrackOverlayEmerald,
                        isActive = state.activeInspector == InspectorTab.CUTOUT,
                        onClick = { onOpenInspector(InspectorTab.CUTOUT) },
                        testTag = "tool_cutout"
                    )
                    ToolRailButton(
                        icon = Icons.Default.Style,
                        label = "Templates",
                        tint = TrackOverlayEmerald,
                        isActive = state.activeInspector == InspectorTab.TEMPLATES,
                        onClick = { onOpenInspector(InspectorTab.TEMPLATES) },
                        testTag = "tool_templates"
                    )
                    ToolRailButton(
                        icon = Icons.Default.Crop,
                        label = "Mask",
                        tint = TrackVideoBlue,
                        isActive = state.activeInspector == InspectorTab.MASK,
                        onClick = { onOpenInspector(InspectorTab.MASK) },
                        testTag = "tool_mask"
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
                        icon = Icons.Default.GraphicEq,
                        label = "Voice/Mix",
                        tint = TrackAudioRose,
                        isActive = state.activeInspector == InspectorTab.AUDIO,
                        onClick = { onOpenInspector(InspectorTab.AUDIO) },
                        testTag = "tool_audio_mixer"
                    )
                    ToolRailButton(
                        icon = Icons.Default.RecordVoiceOver,
                        label = "AI Voice",
                        tint = TrackOverlayEmerald,
                        onClick = onOpenLocalTts,
                        testTag = "tool_local_tts_voice"
                    )
                    ToolRailButton(
                        icon = Icons.Default.Terminal,
                        label = "MCP/RPC",
                        tint = CinemaAmber,
                        onClick = onOpenAutomationConsole,
                        testTag = "tool_automation_mcp"
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
                    ToolRailButton(icon = Icons.Default.Transform, label = "Crop/Zoom", tint = CinemaAmber, isActive = state.activeInspector == InspectorTab.TRANSFORM_CROP, onClick = { onOpenInspector(InspectorTab.TRANSFORM_CROP) }, testTag = "clip_transform_crop_button")
                    ToolRailButton(icon = Icons.Default.BlurOn, label = "FX", tint = TrackFxViolet, isActive = state.activeInspector == InspectorTab.VISUAL_FX, onClick = { onOpenInspector(InspectorTab.VISUAL_FX) }, testTag = "clip_fx_button")
                    ToolRailButton(icon = Icons.Default.AutoFixHigh, label = "Cutout", tint = TrackOverlayEmerald, isActive = state.activeInspector == InspectorTab.CUTOUT, onClick = { onOpenInspector(InspectorTab.CUTOUT) }, testTag = "clip_cutout_button")
                    ToolRailButton(icon = Icons.Default.GraphicEq, label = "Voice/Mix", tint = TrackAudioRose, isActive = state.activeInspector == InspectorTab.AUDIO, onClick = { onOpenInspector(InspectorTab.AUDIO) }, testTag = "clip_audio_button")
                    ToolRailButton(icon = Icons.Default.Subtitles, label = "Auto CC", tint = CinemaAmber, isActive = state.activeInspector == InspectorTab.CAPTIONS, onClick = { onOpenInspector(InspectorTab.CAPTIONS) }, testTag = "clip_captions_button")
                    ToolRailButton(icon = Icons.Default.Crop, label = "Mask", tint = TrackVideoBlue, isActive = state.activeInspector == InspectorTab.MASK, onClick = { onOpenInspector(InspectorTab.MASK) }, testTag = "clip_mask_button")
                    ToolRailButton(icon = Icons.Default.AcUnit, label = "Freeze", tint = CinemaAmber, onClick = onInsertFreezeFrame, testTag = "clip_freeze_button")
                    ToolRailButton(icon = Icons.Default.Animation, label = "Motion", tint = TrackOverlayEmerald, isActive = state.activeInspector == InspectorTab.KEYFRAMES, onClick = { onOpenInspector(InspectorTab.KEYFRAMES) }, testTag = "clip_keyframes_button")
                    ToolRailButton(icon = Icons.Default.Speed, label = "Speed", tint = TrackAudioRose, isActive = state.activeInspector == InspectorTab.SPEED, onClick = { onOpenInspector(InspectorTab.SPEED) }, testTag = "clip_speed_button")
                    ToolRailButton(icon = Icons.Default.Compare, label = "Transition", tint = CinemaAmber, isActive = state.activeInspector == InspectorTab.TRANSITION, onClick = onOpenTransitions, testTag = "clip_transition_button")
                    ToolRailButton(icon = Icons.Default.AutoAwesome, label = "Filters", isActive = state.activeInspector == InspectorTab.FILTERS, onClick = { onOpenInspector(InspectorTab.FILTERS) })
                    ToolRailButton(icon = Icons.Default.Tune, label = "Color", isActive = state.activeInspector == InspectorTab.ADJUST, onClick = { onOpenInspector(InspectorTab.ADJUST) }, testTag = "clip_color_button")
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
                    ToolRailButton(icon = Icons.Default.Transform, label = "Transform", tint = CinemaAmber, isActive = state.activeInspector == InspectorTab.TRANSFORM_CROP, onClick = { onOpenInspector(InspectorTab.TRANSFORM_CROP) }, testTag = "overlay_transform_button")
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
                    ToolRailButton(icon = Icons.Default.AutoFixHigh, label = "Cutout", tint = TrackOverlayEmerald, isActive = state.activeInspector == InspectorTab.CUTOUT, onClick = { onOpenInspector(InspectorTab.CUTOUT) }, testTag = "overlay_cutout_button")
                    ToolRailButton(icon = Icons.Default.Crop, label = "Mask", tint = TrackVideoBlue, isActive = state.activeInspector == InspectorTab.MASK, onClick = { onOpenInspector(InspectorTab.MASK) }, testTag = "overlay_mask_button")
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
                        label = "Voice/Mix",
                        tint = TrackAudioRose,
                        isActive = state.activeInspector == InspectorTab.AUDIO,
                        onClick = { onOpenInspector(InspectorTab.AUDIO) }
                    )
                    ToolRailButton(
                        icon = Icons.Default.Subtitles,
                        label = "Auto CC",
                        tint = CinemaAmber,
                        isActive = state.activeInspector == InspectorTab.CAPTIONS,
                        onClick = { onOpenInspector(InspectorTab.CAPTIONS) }
                    )
                    ToolRailButton(icon = Icons.Default.ContentCopy, label = "Copy", onClick = onDuplicate)
                    ToolRailButton(icon = Icons.Default.DeleteOutline, label = "Delete", tint = ErrorCrimson, onClick = onDelete)
                }

                is SelectedItem.CaptionItem -> {
                    ToolRailButton(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Done", onClick = onClearSelection)
                    ToolRailButton(
                        icon = Icons.Default.Subtitles,
                        label = "Edit CC",
                        tint = CinemaAmber,
                        isActive = state.activeInspector == InspectorTab.CAPTIONS,
                        onClick = { onOpenInspector(InspectorTab.CAPTIONS) },
                        testTag = "caption_edit_button"
                    )
                    ToolRailButton(
                        icon = Icons.Default.AutoAwesome,
                        label = "AI Studio",
                        tint = CinemaAmber,
                        isActive = state.activeInspector == InspectorTab.AI_STUDIO,
                        onClick = { onOpenInspector(InspectorTab.AI_STUDIO) }
                    )
                    ToolRailButton(icon = Icons.Default.DeleteOutline, label = "Delete", tint = ErrorCrimson, onClick = onDelete, testTag = "caption_delete_button")
                }

                is SelectedItem.FxItem -> {
                    ToolRailButton(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Done", onClick = onClearSelection)
                    ToolRailButton(
                        icon = Icons.Default.BlurOn,
                        label = "Edit FX",
                        tint = TrackFxViolet,
                        isActive = state.activeInspector == InspectorTab.VISUAL_FX,
                        onClick = { onOpenInspector(InspectorTab.VISUAL_FX) },
                        testTag = "fx_edit_button"
                    )
                    ToolRailButton(icon = Icons.Default.ContentCopy, label = "Copy", onClick = onDuplicate, testTag = "fx_duplicate_button")
                    ToolRailButton(icon = Icons.Default.DeleteOutline, label = "Delete", tint = ErrorCrimson, onClick = onDelete, testTag = "fx_delete_button")
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
