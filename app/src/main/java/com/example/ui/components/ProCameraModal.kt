package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.FilterPreset
import com.example.model.formatTimecode
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
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

enum class CameraCropGuide(val label: String, val ratio: Float?) {
    OFF("Off", null),
    SCOPE_239("2.39:1", 2.39f),
    CINEMA_16_9("16:9", 16f / 9f),
    VERTICAL_9_16("9:16", 9f / 16f)
}

private enum class ProCamManualParam(val label: String) {
    ISO("ISO"),
    SHUTTER("SHUTTER"),
    WB("WB (K)"),
    FOCUS("FOCUS")
}

private data class LiveCameraTelemetry(
    val histR: FloatArray = FloatArray(32),
    val histG: FloatArray = FloatArray(32),
    val histB: FloatArray = FloatArray(32),
    val zebraCells: BooleanArray = BooleanArray(16 * 12), // 16x12 coarse overexposure grid
    val overexposedPct: Int = 0
)

/**
 * Full-screen Blackmagic Camera-style Pro Camera & Live Capture modal:
 * - CameraX 1.5 Preview + VideoCapture<Recorder> + ImageAnalysis
 * - Camera2Interop real-time manual ISO, Shutter Speed, White Balance (Kelvin), and Focus Distance
 * - Live Cinema Overlays: Rule-of-Thirds grid, Aspect Crop Guides (2.39:1, 16:9, 9:16),
 *   Live RGB Histogram, and Zebra Stripes overexposure warning
 * - Live LUT preview filter strip applied directly when importing captured clips to the timeline
 */
@OptIn(ExperimentalCamera2Interop::class)
@SuppressLint("MissingPermission")
@Composable
fun ProCameraModal(
    createOutputFile: () -> File,
    onCapturedVideo: (file: File, liveLut: FilterPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPerm = results[Manifest.permission.CAMERA] == true || hasCameraPerm
        hasAudioPerm = results[Manifest.permission.RECORD_AUDIO] == true || hasAudioPerm
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm || !hasAudioPerm) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    // Pro Camera Controls State
    var useBackCamera by remember { mutableStateOf(true) }
    var showGrid by remember { mutableStateOf(true) }
    var showZebra by remember { mutableStateOf(true) }
    var showHistogram by remember { mutableStateOf(true) }
    var cropGuide by remember { mutableStateOf(CameraCropGuide.SCOPE_239) }
    var selectedLut by remember { mutableStateOf(FilterPreset.ORIGINAL) }

    var manualMode by remember { mutableStateOf(false) }
    var activeParam by remember { mutableStateOf(ProCamManualParam.ISO) }
    var isoValue by remember { mutableIntStateOf(400) }
    var shutterDenom by remember { mutableIntStateOf(48) }      // 1/48s cinema 180-deg shutter
    var wbKelvin by remember { mutableIntStateOf(5600) }        // 2800K..7500K
    var focusDiopters by remember { mutableFloatStateOf(0f) }   // 0 = infinity .. 10 = macro

    var isRecording by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var telemetry by remember { mutableStateOf(LiveCameraTelemetry()) }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    BackHandler {
        if (isRecording) {
            runCatching { activeRecording?.stop() }
        } else {
            onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { activeRecording?.stop() }
            runCatching { analysisExecutor.shutdown() }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startedAt = System.currentTimeMillis()
            while (isRecording) {
                recordingElapsedMs = System.currentTimeMillis() - startedAt
                delay(50L)
            }
        } else {
            recordingElapsedMs = 0L
        }
    }

    // Push Camera2Interop Manual Exposure / WB / Focus controls whenever parameters change
    LaunchedEffect(boundCamera, manualMode, isoValue, shutterDenom, wbKelvin, focusDiopters) {
        val cam = boundCamera ?: return@LaunchedEffect
        runCatching {
            val c2 = Camera2CameraControl.from(cam.cameraControl)
            val optsBuilder = CaptureRequestOptions.Builder()
            if (manualMode) {
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    isoValue.coerceIn(100, 3200)
                )
                val exposureNs = (1_000_000_000L / shutterDenom.coerceIn(24, 2000).toLong())
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    exposureNs
                )
                // Map Kelvin (2800..7500) to RggbChannelVector gains
                val wbNorm = ((wbKelvin - 5200f) / 2400f).coerceIn(-1f, 1f)
                val rGain = (1.65f + wbNorm * 0.55f).coerceIn(1.0f, 2.4f)
                val bGain = (1.65f - wbNorm * 0.55f).coerceIn(1.0f, 2.4f)
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_OFF
                )
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
                )
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_GAINS,
                    RggbChannelVector(rGain, 1.0f, 1.0f, bGain)
                )
                if (focusDiopters > 0.05f) {
                    optsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF
                    )
                    optsBuilder.setCaptureRequestOption(
                        CaptureRequest.LENS_FOCUS_DISTANCE,
                        focusDiopters.coerceIn(0f, 10f)
                    )
                } else {
                    optsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
                }
            } else {
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO
                )
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
            }
            c2.setCaptureRequestOptions(optsBuilder.build())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("pro_camera_modal")
    ) {
        if (!hasCameraPerm) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = CinemaAmber,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Pro Cinema Camera Access",
                    style = MaterialTheme.typography.titleMedium,
                    color = ChalkWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Grant Camera & Microphone permissions to record directly into your LumaCut timeline with manual ISO, shutter, focus, and live scopes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                        modifier = Modifier.testTag("procam_grant_permission_btn")
                    ) {
                        Text("Allow Camera")
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveRail, contentColor = ChalkWhite)
                    ) {
                        Text("Close")
                    }
                }
            }
        } else {
            // 1. Live CameraX Preview Surface
            val previewView = remember {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            LaunchedEffect(useBackCamera) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    runCatching {
                        val provider = cameraProviderFuture.get()
                        provider.unbindAll()

                        val selector = if (useBackCamera) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val recorder = Recorder.Builder()
                            .setQualitySelector(
                                QualitySelector.from(
                                    Quality.FHD,
                                    FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
                                )
                            )
                            .build()
                        val vc = VideoCapture.withOutput(recorder)
                        videoCapture = vc

                        var lastAnalyzeNs = 0L
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(320, 240))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { image ->
                                    val nowNs = System.nanoTime()
                                    if (nowNs - lastAnalyzeNs > 120_000_000L) {
                                        lastAnalyzeNs = nowNs
                                        telemetry = analyzeYuvFrame(image)
                                    }
                                    image.close()
                                }
                            }

                        boundCamera = provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            vc,
                            imageAnalysis
                        )
                    }.onFailure { err ->
                        statusBanner = err.localizedMessage ?: "Camera unavailable on this device"
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Live LUT Preview Color Tint Layer
            if (selectedLut != FilterPreset.ORIGINAL) {
                val lutOverlay = when (selectedLut) {
                    FilterPreset.NOIR -> Color(0xFF1A1D24).copy(alpha = 0.42f)
                    FilterPreset.TEAL_ORANGE -> Color(0xFF0E7490).copy(alpha = 0.22f)
                    FilterPreset.VINTAGE_WARM -> Color(0xFFD97706).copy(alpha = 0.22f)
                    FilterPreset.KODAK_FADE -> Color(0xFFB45309).copy(alpha = 0.18f)
                    FilterPreset.CRISP_VIVID -> Color(0xFF2563EB).copy(alpha = 0.12f)
                    FilterPreset.MIDNIGHT_COOL -> Color(0xFF1E3A8A).copy(alpha = 0.28f)
                    FilterPreset.GOLDEN_HOUR -> Color(0xFFF59E0B).copy(alpha = 0.24f)
                    FilterPreset.MATTE_FILM -> Color(0xFF6B7280).copy(alpha = 0.22f)
                    else -> Color.Transparent
                }
                Box(modifier = Modifier.fillMaxSize().background(lutOverlay))
            }

            // 3. Secondary Cinema Overlay Canvas: Rule-of-Thirds, Crop Guides & Zebra Stripes
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Zebra Stripes on overexposed cells (>94% luma)
                if (showZebra && telemetry.overexposedPct > 0) {
                    val cols = 16
                    val rows = 12
                    val cellW = size.width / cols
                    val cellH = size.height / rows
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            if (telemetry.zebraCells[r * cols + c]) {
                                val x0 = c * cellW
                                val y0 = r * cellH
                                drawLine(
                                    color = ErrorCrimson.copy(alpha = 0.55f),
                                    start = Offset(x0, y0 + cellH),
                                    end = Offset(x0 + cellW, y0),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = CinemaAmber.copy(alpha = 0.40f),
                                    start = Offset(x0, y0 + cellH * 0.5f),
                                    end = Offset(x0 + cellW * 0.5f, y0),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                        }
                    }
                }

                // Rule-of-Thirds Grid
                if (showGrid) {
                    val gridColor = Color.White.copy(alpha = 0.34f)
                    for (i in 1..2) {
                        val x = size.width * (i / 3f)
                        val y = size.height * (i / 3f)
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }

                // Aspect Ratio Crop Guides (2.39:1, 16:9, 9:16)
                val targetRatio = cropGuide.ratio
                if (targetRatio != null && size.height > 1f) {
                    val viewRatio = size.width / size.height
                    val maskColor = Color.Black.copy(alpha = 0.52f)
                    val guideStroke = CinemaAmber.copy(alpha = 0.85f)
                    if (viewRatio < targetRatio) {
                        // Letterbox top & bottom
                        val boxH = size.width / targetRatio
                        val barH = ((size.height - boxH) / 2f).coerceAtLeast(0f)
                        drawRect(maskColor, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, barH))
                        drawRect(maskColor, Offset(0f, size.height - barH), androidx.compose.ui.geometry.Size(size.width, barH))
                        drawLine(guideStroke, Offset(0f, barH), Offset(size.width, barH), strokeWidth = 1.2.dp.toPx())
                        drawLine(guideStroke, Offset(0f, size.height - barH), Offset(size.width, size.height - barH), strokeWidth = 1.2.dp.toPx())
                    } else {
                        // Pillarbox left & right
                        val boxW = size.height * targetRatio
                        val barW = ((size.width - boxW) / 2f).coerceAtLeast(0f)
                        drawRect(maskColor, Offset(0f, 0f), androidx.compose.ui.geometry.Size(barW, size.height))
                        drawRect(maskColor, Offset(size.width - barW, 0f), androidx.compose.ui.geometry.Size(barW, size.height))
                        drawLine(guideStroke, Offset(barW, 0f), Offset(barW, size.height), strokeWidth = 1.2.dp.toPx())
                        drawLine(guideStroke, Offset(size.width - barW, 0f), Offset(size.width - barW, size.height), strokeWidth = 1.2.dp.toPx())
                    }
                }
            }

            // 4. Blackmagic-Style Top Telemetry HUD Bar
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isRecording) runCatching { activeRecording?.stop() }
                            onDismiss()
                        },
                        modifier = Modifier.size(40.dp).testTag("procam_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Pro Camera", tint = ChalkWhite)
                    }

                    // Live Cinema Readout Badges (FPS, Shutter, ISO, WB)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelemetryReadoutPill(
                            label = "MODE",
                            value = if (manualMode) "MANUAL" else "AUTO",
                            highlight = manualMode,
                            onClick = { manualMode = !manualMode },
                            testTag = "procam_manual_toggle"
                        )
                        TelemetryReadoutPill(
                            label = "SHUTTER",
                            value = "1/$shutterDenom",
                            highlight = manualMode && activeParam == ProCamManualParam.SHUTTER,
                            onClick = {
                                manualMode = true
                                activeParam = ProCamManualParam.SHUTTER
                            }
                        )
                        TelemetryReadoutPill(
                            label = "ISO",
                            value = "$isoValue",
                            highlight = manualMode && activeParam == ProCamManualParam.ISO,
                            onClick = {
                                manualMode = true
                                activeParam = ProCamManualParam.ISO
                            }
                        )
                        TelemetryReadoutPill(
                            label = "WB",
                            value = "${wbKelvin}K",
                            highlight = manualMode && activeParam == ProCamManualParam.WB,
                            onClick = {
                                manualMode = true
                                activeParam = ProCamManualParam.WB
                            }
                        )
                    }

                    // Recording Timecode Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRecording) ErrorCrimson.copy(alpha = 0.25f) else ActiveRail)
                            .border(
                                1.dp,
                                if (isRecording) ErrorCrimson else BorderHairline,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) ErrorCrimson else SilverMuted)
                        )
                        Text(
                            text = formatTimecode(recordingElapsedMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRecording) Color.White else SilverMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Overlay Toggle Strip + Live RGB Histogram Widget
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HudMiniToggle(
                            icon = Icons.Default.GridOn,
                            label = "3rds",
                            active = showGrid,
                            onClick = { showGrid = !showGrid },
                            testTag = "procam_grid_toggle"
                        )
                        HudMiniToggle(
                            icon = Icons.Default.Crop,
                            label = cropGuide.label,
                            active = cropGuide != CameraCropGuide.OFF,
                            onClick = {
                                val all = CameraCropGuide.entries
                                cropGuide = all[(cropGuide.ordinal + 1) % all.size]
                            },
                            testTag = "procam_crop_toggle"
                        )
                        HudMiniToggle(
                            icon = Icons.Default.WarningAmber,
                            label = if (telemetry.overexposedPct > 0) "Zebra ${telemetry.overexposedPct}%" else "Zebra",
                            active = showZebra,
                            onClick = { showZebra = !showZebra },
                            testTag = "procam_zebra_toggle"
                        )
                        HudMiniToggle(
                            icon = Icons.Default.ShowChart,
                            label = "Hist",
                            active = showHistogram,
                            onClick = { showHistogram = !showHistogram },
                            testTag = "procam_hist_toggle"
                        )
                    }

                    if (showHistogram) {
                        LiveRgbHistogramMini(
                            histR = telemetry.histR,
                            histG = telemetry.histG,
                            histB = telemetry.histB,
                            modifier = Modifier
                                .width(106.dp)
                                .height(42.dp)
                                .testTag("procam_live_histogram")
                        )
                    }
                }

                statusBanner?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorCrimson,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 5. Bottom Pro Cinema Deck: Manual Exposure/Focus Dial + Live LUT Strip + Record Trigger
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(ObsidianBg.copy(alpha = 0.88f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Manual Control Selector + Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProCamManualParam.entries.forEach { param ->
                        val picked = activeParam == param && manualMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (picked) AmberContainer else ActiveRail)
                                .border(
                                    1.dp,
                                    if (picked) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(7.dp)
                                )
                                .clickable {
                                    manualMode = true
                                    activeParam = param
                                }
                                .padding(vertical = 6.dp)
                                .testTag("procam_param_${param.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = param.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (picked) CinemaAmber else SilverMuted
                            )
                        }
                    }
                }

                // Active Manual Parameter Slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (manualMode) CinemaAmber else SilverMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    when (activeParam) {
                        ProCamManualParam.ISO -> {
                            Slider(
                                value = isoValue.toFloat(),
                                onValueChange = {
                                    manualMode = true
                                    isoValue = ((it / 50f).roundToInt() * 50).coerceIn(100, 3200)
                                },
                                valueRange = 100f..3200f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CinemaAmber,
                                    activeTrackColor = CinemaAmber,
                                    inactiveTrackColor = ActiveRail
                                ),
                                modifier = Modifier.weight(1f).testTag("procam_slider_iso")
                            )
                            Text("ISO $isoValue", style = MaterialTheme.typography.labelSmall, color = CinemaAmber, modifier = Modifier.width(64.dp))
                        }
                        ProCamManualParam.SHUTTER -> {
                            Slider(
                                value = shutterDenom.toFloat(),
                                onValueChange = {
                                    manualMode = true
                                    shutterDenom = it.roundToInt().coerceIn(24, 2000)
                                },
                                valueRange = 24f..2000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CinemaAmber,
                                    activeTrackColor = CinemaAmber,
                                    inactiveTrackColor = ActiveRail
                                ),
                                modifier = Modifier.weight(1f).testTag("procam_slider_shutter")
                            )
                            Text("1/${shutterDenom}s", style = MaterialTheme.typography.labelSmall, color = CinemaAmber, modifier = Modifier.width(64.dp))
                        }
                        ProCamManualParam.WB -> {
                            Slider(
                                value = wbKelvin.toFloat(),
                                onValueChange = {
                                    manualMode = true
                                    wbKelvin = ((it / 100f).roundToInt() * 100).coerceIn(2800, 7500)
                                },
                                valueRange = 2800f..7500f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CinemaAmber,
                                    activeTrackColor = CinemaAmber,
                                    inactiveTrackColor = ActiveRail
                                ),
                                modifier = Modifier.weight(1f).testTag("procam_slider_wb")
                            )
                            Text("${wbKelvin}K", style = MaterialTheme.typography.labelSmall, color = CinemaAmber, modifier = Modifier.width(64.dp))
                        }
                        ProCamManualParam.FOCUS -> {
                            Slider(
                                value = focusDiopters,
                                onValueChange = {
                                    manualMode = true
                                    focusDiopters = it.coerceIn(0f, 10f)
                                },
                                valueRange = 0f..10f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CinemaAmber,
                                    activeTrackColor = CinemaAmber,
                                    inactiveTrackColor = ActiveRail
                                ),
                                modifier = Modifier.weight(1f).testTag("procam_slider_focus")
                            )
                            val label = if (focusDiopters < 0.1f) "AF / ∞" else String.format("%.1f D", focusDiopters)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = CinemaAmber, modifier = Modifier.width(64.dp))
                        }
                    }
                }

                // Live LUT Preview Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterPreset.entries.forEach { preset ->
                        val picked = preset == selectedLut
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (picked) AmberContainer else InspectorCharcoal)
                                .border(
                                    1.dp,
                                    if (picked) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(7.dp)
                                )
                                .clickable { selectedLut = preset }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                .testTag("procam_lut_${preset.name.lowercase()}")
                        ) {
                            Text(
                                text = preset.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (picked) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Record Shutter + Camera Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (selectedLut == FilterPreset.ORIGINAL) "Rec.709 • 1080p" else "LUT: ${selectedLut.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted,
                        modifier = Modifier.width(108.dp)
                    )

                    // Big Cinema Record / Stop Button
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .border(3.dp, ChalkWhite, CircleShape)
                            .padding(5.dp)
                            .clickable {
                                val vc = videoCapture ?: return@clickable
                                if (isRecording) {
                                    activeRecording?.stop()
                                    activeRecording = null
                                } else {
                                    val outFile = createOutputFile()
                                    val fileOptions = FileOutputOptions.Builder(outFile).build()
                                    val pending = vc.output.prepareRecording(context, fileOptions).let {
                                        if (hasAudioPerm) it.withAudioEnabled() else it
                                    }
                                    statusBanner = null
                                    activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Start -> {
                                                isRecording = true
                                            }
                                            is VideoRecordEvent.Finalize -> {
                                                isRecording = false
                                                activeRecording = null
                                                if (!event.hasError() && outFile.exists() && outFile.length() > 1024L) {
                                                    onCapturedVideo(outFile, selectedLut)
                                                } else {
                                                    statusBanner = "Recording failed (${event.error})"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .testTag("procam_record_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isRecording) 26.dp else 48.dp)
                                .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                                .background(ErrorCrimson)
                        )
                    }

                    Row(
                        modifier = Modifier.width(108.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { if (!isRecording) useBackCamera = !useBackCamera },
                            enabled = !isRecording,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ActiveRail)
                                .testTag("procam_switch_lens_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch camera lens",
                                tint = ChalkWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryReadoutPill(
    label: String,
    value: String,
    highlight: Boolean,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlight) AmberContainer else ActiveRail)
            .border(1.dp, if (highlight) CinemaAmber else BorderHairline, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SilverMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) CinemaAmber else ChalkWhite,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HudMiniToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AmberContainer else ActiveRail.copy(alpha = 0.85f))
            .border(1.dp, if (active) CinemaAmber else BorderHairline, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) CinemaAmber else SilverMuted,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CinemaAmber else ChalkWhite
        )
    }
}

@Composable
private fun LiveRgbHistogramMini(
    histR: FloatArray,
    histG: FloatArray,
    histB: FloatArray,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ObsidianBg.copy(alpha = 0.82f))
            .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
            .padding(3.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            fun drawChannel(bins: FloatArray, color: Color) {
                if (bins.isEmpty()) return
                val path = Path()
                val stepX = size.width / (bins.size - 1).coerceAtLeast(1)
                bins.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val y = size.height * (1f - v.coerceIn(0f, 1f))
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = color, style = Stroke(width = 1.4.dp.toPx()))
            }
            drawChannel(histR, Color(0xFFEF4444).copy(alpha = 0.85f))
            drawChannel(histG, Color(0xFF22C55E).copy(alpha = 0.85f))
            drawChannel(histB, Color(0xFF3B82F6).copy(alpha = 0.85f))
        }
    }
}

private fun analyzeYuvFrame(image: androidx.camera.core.ImageProxy): LiveCameraTelemetry {
    val planes = image.planes
    if (planes.size < 3) return LiveCameraTelemetry()
    val yBuf = planes[0].buffer
    val uBuf = planes[1].buffer
    val vBuf = planes[2].buffer
    val yStride = planes[0].rowStride
    val uvStride = planes[1].rowStride
    val uvPixelStride = planes[1].pixelStride
    val w = image.width
    val h = image.height

    val binsR = IntArray(32)
    val binsG = IntArray(32)
    val binsB = IntArray(32)
    val zebraCounts = IntArray(16 * 12)
    val zebraTotals = IntArray(16 * 12)
    var totalSamples = 0
    var overCount = 0

    val step = 6
    var y = 0
    while (y < h) {
        val rowY = y * yStride
        val rowUV = (y / 2) * uvStride
        val cellR = ((y * 12) / h).coerceIn(0, 11)
        var x = 0
        while (x < w) {
            val yIdx = rowY + x
            val uvIdx = rowUV + (x / 2) * uvPixelStride
            if (yIdx < yBuf.limit() && uvIdx < uBuf.limit() && uvIdx < vBuf.limit()) {
                val yVal = (yBuf.get(yIdx).toInt() and 0xFF)
                val uVal = (uBuf.get(uvIdx).toInt() and 0xFF) - 128
                val vVal = (vBuf.get(uvIdx).toInt() and 0xFF) - 128

                val r = (yVal + (1.402f * vVal)).toInt().coerceIn(0, 255)
                val g = (yVal - (0.344136f * uVal) - (0.714136f * vVal)).toInt().coerceIn(0, 255)
                val b = (yVal + (1.772f * uVal)).toInt().coerceIn(0, 255)

                binsR[r shr 3]++
                binsG[g shr 3]++
                binsB[b shr 3]++

                val cellC = ((x * 16) / w).coerceIn(0, 15)
                val cellIdx = cellR * 16 + cellC
                zebraTotals[cellIdx]++
                if (yVal >= 240) {
                    zebraCounts[cellIdx]++
                    overCount++
                }
                totalSamples++
            }
            x += step
        }
        y += step
    }

    val maxBin = maxOf(binsR.maxOrNull() ?: 1, binsG.maxOrNull() ?: 1, binsB.maxOrNull() ?: 1).coerceAtLeast(1)
    val normR = FloatArray(32) { (binsR[it].toFloat() / maxBin).coerceIn(0f, 1f) }
    val normG = FloatArray(32) { (binsG[it].toFloat() / maxBin).coerceIn(0f, 1f) }
    val normB = FloatArray(32) { (binsB[it].toFloat() / maxBin).coerceIn(0f, 1f) }
    val zebraMask = BooleanArray(16 * 12) { idx ->
        zebraTotals[idx] > 0 && (zebraCounts[idx].toFloat() / zebraTotals[idx].toFloat()) > 0.25f
    }
    val pct = if (totalSamples > 0) ((overCount * 100f) / totalSamples).roundToInt() else 0
    return LiveCameraTelemetry(normR, normG, normB, zebraMask, pct)
}
