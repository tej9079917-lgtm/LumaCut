package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FxType
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
import com.example.ui.theme.SlateDim
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TrackFxViolet
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.InspectorTab
import com.example.viewmodel.SelectedItem

/**
 * KineMaster-inspired Horizontal Landscape Studio Layout:
 * - Slim Left Utility Rail (Back, Undo/Redo, Split, Delete, Timeline Expand, Portrait Switch)
 * - Center-Left Preview Stage + Transport Bar
 * - Right Signature Radial Action Wheel (Media, Layer, Audio, Rec + Center Camera) that morphs
 *   smoothly into a Docked Tool Inspector whenever a tool/clip inspector is open
 * - Wide Full-Width Multilayer Timeline across the bottom
 */
@Composable
fun KineMasterLandscapeWorkspace(
    state: EditorUiState,
    isTimelineExpanded: Boolean,
    onToggleTimelineExpanded: () -> Unit,
    onSwitchToPortrait: () -> Unit,
    onNavigateBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSplit: () -> Unit,
    onDeleteSelected: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenExport: () -> Unit,
    onPickMainMedia: () -> Unit,
    onPickOverlayImage: () -> Unit,
    onPickOverlayVideo: () -> Unit,
    onAddTextOverlay: () -> Unit,
    onAddFx: (FxType) -> Unit,
    onOpenCutout: () -> Unit,
    onOpenCaptions: () -> Unit,
    onPickAudio: () -> Unit,
    onToggleVoiceover: () -> Unit,
    onOpenCamera: () -> Unit,
    onSelectInspectorTab: (InspectorTab) -> Unit,
    onReturnToWheel: () -> Unit,
    previewContent: @Composable (Modifier) -> Unit,
    dockedInspectorContent: @Composable () -> Unit,
    timelineContent: @Composable (Modifier) -> Unit
) {
    var showLayerMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("kinemaster_landscape_workspace")
    ) {
        // Upper Workspace Row: Left Utility Rail | Preview Stage | Right KineMaster Wheel / Docked Inspector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isTimelineExpanded) 0.34f else 0.57f)
        ) {
            // 1. Left Slim KineMaster Utility Rail
            Column(
                modifier = Modifier
                    .width(50.dp)
                    .fillMaxHeight()
                    .background(StudioSlate)
                    .border(0.5.dp, BorderHairline)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                LeftRailIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    desc = "Back to projects",
                    tint = ChalkWhite,
                    onClick = onNavigateBack,
                    testTag = "km_back_btn"
                )
                LeftRailIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    desc = "Undo",
                    enabled = state.canUndo,
                    tint = if (state.canUndo) ChalkWhite else SlateDim,
                    onClick = onUndo,
                    testTag = "km_undo_btn"
                )
                LeftRailIconButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    desc = "Redo",
                    enabled = state.canRedo,
                    tint = if (state.canRedo) ChalkWhite else SlateDim,
                    onClick = onRedo,
                    testTag = "km_redo_btn"
                )
                LeftRailIconButton(
                    icon = Icons.Default.ContentCut,
                    desc = "Split at playhead",
                    tint = CinemaAmber,
                    onClick = onSplit,
                    testTag = "km_split_btn"
                )
                if (state.selection != SelectedItem.None) {
                    LeftRailIconButton(
                        icon = Icons.Default.DeleteOutline,
                        desc = "Delete selected",
                        tint = ErrorCrimson,
                        onClick = onDeleteSelected,
                        testTag = "km_delete_btn"
                    )
                }
                Spacer(modifier = Modifier.weight(1f, fill = false))
                LeftRailIconButton(
                    icon = if (isTimelineExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    desc = if (isTimelineExpanded) "Restore preview size" else "Expand timeline",
                    tint = if (isTimelineExpanded) CinemaAmber else SilverMuted,
                    onClick = onToggleTimelineExpanded,
                    testTag = "km_expand_timeline_btn"
                )
                LeftRailIconButton(
                    icon = Icons.Default.ScreenRotation,
                    desc = "Switch to Vertical Mode",
                    tint = CinemaAmber,
                    onClick = onSwitchToPortrait,
                    testTag = "km_switch_portrait_btn"
                )
            }

            // 2. Center-Left Live Preview Stage + Compact Transport Strip
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(ObsidianBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    previewContent(Modifier.fillMaxSize())
                }

                // Compact Landscape Transport & Quick Tool Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(StudioSlate)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CinemaAmber)
                                .clickable { onTogglePlayPause() }
                                .testTag("km_play_pause_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = OnAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "${formatTimecode(state.playheadMs)} / ${formatTimecode(state.timeline.totalDurationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChalkWhite
                        )
                    }

                    // Contextual quick tool triggers when an item is selected
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        QuickLandscapePill("Transform", Icons.Default.Crop) {
                            onSelectInspectorTab(InspectorTab.TRANSFORM_CROP)
                        }
                        QuickLandscapePill("FX", Icons.Default.BlurOn) {
                            onSelectInspectorTab(InspectorTab.VISUAL_FX)
                        }
                        QuickLandscapePill("Cutout", Icons.Default.AutoFixHigh) {
                            onOpenCutout()
                        }
                        QuickLandscapePill("Audio", Icons.Default.GraphicEq) {
                            onSelectInspectorTab(InspectorTab.AUDIO)
                        }
                        QuickLandscapePill("Captions", Icons.Default.Subtitles) {
                            onOpenCaptions()
                        }
                    }
                }
            }

            // 3. Right Panel: KineMaster Radial Action Wheel OR Docked Tool Inspector
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .background(InspectorCharcoal)
                    .border(0.5.dp, BorderHairline)
            ) {
                // Top Mini Action Bar (Export + Wheel/Tools switcher)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(StudioSlate)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (state.activeInspector != InspectorTab.NONE) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ActiveRail)
                                .clickable { onReturnToWheel() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("km_back_to_wheel_btn")
                        ) {
                            Text("◎ Wheel", style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                        }
                    } else {
                        Text(
                            text = "STUDIO DIAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CinemaAmber)
                            .clickable { onOpenExport() }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("km_export_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = "Export MP4", tint = OnAmber, modifier = Modifier.size(14.dp))
                        Text("Export", style = MaterialTheme.typography.labelSmall, color = OnAmber, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)

                // Animated swap between KineMaster Radial Action Wheel and Docked Inspector
                AnimatedContent(
                    targetState = state.activeInspector != InspectorTab.NONE,
                    label = "km_right_pane",
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { showingInspector ->
                    if (showingInspector) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            dockedInspectorContent()
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            KineMasterRadialWheel(
                                isRecordingVoiceover = state.isRecordingVoiceover,
                                onMediaClick = onPickMainMedia,
                                onLayerClick = { showLayerMenu = true },
                                onAudioClick = onPickAudio,
                                onRecClick = onToggleVoiceover,
                                onCameraClick = onOpenCamera
                            )

                            // Layer Popup Menu (Image, Video, Text, Magnifier, Mosaic, Blur, Cutout, Captions)
                            DropdownMenu(
                                expanded = showLayerMenu,
                                onDismissRequest = { showLayerMenu = false },
                                modifier = Modifier.background(StudioSlate)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Image Overlay", color = ChalkWhite) },
                                    leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, null, tint = CinemaAmber) },
                                    onClick = { showLayerMenu = false; onPickOverlayImage() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Video Overlay (PIP)", color = ChalkWhite) },
                                    leadingIcon = { Icon(Icons.Default.VideoLibrary, null, tint = CinemaAmber) },
                                    onClick = { showLayerMenu = false; onPickOverlayVideo() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Text & Title", color = ChalkWhite) },
                                    leadingIcon = { Icon(Icons.Default.TextFields, null, tint = CinemaAmber) },
                                    onClick = { showLayerMenu = false; onAddTextOverlay() }
                                )
                                DropdownMenuItem(
                                    text = { Text("FX • Magnifier Lens", color = ChalkWhite) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TrackFxViolet) },
                                    onClick = { showLayerMenu = false; onAddFx(FxType.MAGNIFIER) }
                                )
                                DropdownMenuItem(
                                    text = { Text("FX • Mosaic Pixelate", color = ChalkWhite) },
                                    leadingIcon = { Icon(Icons.Default.GridOn, null, tint = TrackFxViolet) },
                                    onClick = { showLayerMenu = false; onAddFx(FxType.MOSAIC) }
                                )
                                DropdownMenuItem(
                                    text = { Text("FX • Gaussian Blur", color = ChalkWhite) },
                                    leadingIcon = { Icon(Icons.Default.BlurOn, null, tint = TrackFxViolet) },
                                    onClick = { showLayerMenu = false; onAddFx(FxType.BLUR) }
                                )
                                DropdownMenuItem(
                                    text = { Text("AI Subject Cutout", color = ChalkWhite) },
                                    leadingIcon = { Icon(Icons.Default.AutoFixHigh, null, tint = CinemaAmber) },
                                    onClick = { showLayerMenu = false; onOpenCutout() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Auto Captions (AI + Mic)", color = ChalkWhite) },
                                    leadingIcon = { Icon(Icons.Default.Subtitles, null, tint = CinemaAmber) },
                                    onClick = { showLayerMenu = false; onOpenCaptions() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Color, Speed & AI Studio", color = ChalkWhite) },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = CinemaAmber) },
                                    onClick = { showLayerMenu = false; onSelectInspectorTab(InspectorTab.AI_STUDIO) }
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = BorderHairline, thickness = 1.dp)

        // Bottom Wide Multilayer Timeline
        timelineContent(
            Modifier
                .fillMaxWidth()
                .weight(if (isTimelineExpanded) 0.66f else 0.43f)
        )
    }
}

@Composable
private fun KineMasterRadialWheel(
    isRecordingVoiceover: Boolean,
    onMediaClick: () -> Unit,
    onLayerClick: () -> Unit,
    onAudioClick: () -> Unit,
    onRecClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(188.dp)
            .testTag("kinemaster_radial_wheel"),
        contentAlignment = Alignment.Center
    ) {
        // Metallic Dark Outer Dial Ring + Quadrant Hairlines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF232732), Color(0xFF13151B)),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )
            drawCircle(
                color = Color(0xFF3A4152),
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )
            // Subtle inner amber ring
            drawCircle(
                color = CinemaAmber.copy(alpha = 0.25f),
                radius = radius * 0.42f,
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Diagonal quadrant dividers
            val diag = radius * 0.707f
            drawLine(
                color = Color(0xFF2E3340),
                start = Offset(center.x - diag, center.y - diag),
                end = Offset(center.x + diag, center.y + diag),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF2E3340),
                start = Offset(center.x + diag, center.y - diag),
                end = Offset(center.x - diag, center.y + diag),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Top Quadrant: MEDIA
        WheelQuadrantButton(
            icon = Icons.Default.VideoLibrary,
            label = "Media",
            tint = ChalkWhite,
            onClick = onMediaClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 10.dp)
                .testTag("km_wheel_media")
        )

        // Left Quadrant: LAYER (Overlay, Text, Magnifier, Mosaic, Blur, Cutout, Captions)
        WheelQuadrantButton(
            icon = Icons.Default.Layers,
            label = "Layer",
            tint = CinemaAmber,
            onClick = onLayerClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 8.dp)
                .testTag("km_wheel_layer")
        )

        // Right Quadrant: AUDIO
        WheelQuadrantButton(
            icon = Icons.Default.LibraryMusic,
            label = "Audio",
            tint = ChalkWhite,
            onClick = onAudioClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-8).dp)
                .testTag("km_wheel_audio")
        )

        // Bottom Quadrant: REC (Voiceover)
        WheelQuadrantButton(
            icon = Icons.Default.Mic,
            label = if (isRecordingVoiceover) "Stop" else "Rec",
            tint = if (isRecordingVoiceover) ErrorCrimson else ChalkWhite,
            onClick = onRecClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-10).dp)
                .testTag("km_wheel_rec")
        )

        // Center Glowing Action Hub: CAMERA
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(CinemaAmber, Color(0xFFD97706)))
                )
                .border(2.5.dp, ObsidianBg, CircleShape)
                .clickable { onCameraClick() }
                .testTag("km_wheel_camera"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Pro Camera",
                tint = OnAmber,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun WheelQuadrantButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun LeftRailIconButton(
    icon: ImageVector,
    desc: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).testTag(testTag)
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun QuickLandscapePill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ActiveRail)
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = label, tint = CinemaAmber, modifier = Modifier.size(13.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
    }
}
