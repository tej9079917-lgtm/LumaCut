package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AspectRatio
import com.example.model.ExportBitrateTier
import com.example.model.ExportConfig
import com.example.model.ExportResolution
import com.example.model.ExportState
import com.example.model.TimelineDocument
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
import com.example.ui.theme.TrackOverlayEmerald
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    projectTitle: String,
    aspectRatio: AspectRatio,
    timeline: TimelineDocument,
    config: ExportConfig,
    exportState: ExportState,
    onUpdateConfig: (ExportConfig) -> Unit,
    onStartExport: () -> Unit,
    onQueueBackgroundExport: () -> Unit = {},
    onUploadToYouTube: (outputUri: String, title: String) -> Unit = { _, _ -> },
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (exportW, exportH) = aspectRatio.exportDimensions(config.resolution)
    val estimatedMb = config.estimateFileSizeMb(timeline.totalDurationMs)

    ModalBottomSheet(
        onDismissRequest = {
            if (exportState !is ExportState.Exporting) onDismiss()
        },
        sheetState = sheetState,
        containerColor = InspectorCharcoal,
        contentColor = ChalkWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 16.dp)
                .testTag("export_settings_sheet")
        ) {
            Text(
                text = "Export MP4 Video",
                style = MaterialTheme.typography.headlineSmall,
                color = ChalkWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$projectTitle • ${aspectRatio.label} • ${formatTimecode(timeline.totalDurationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = SilverMuted
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (exportState) {
                is ExportState.Idle -> {
                    // Summary pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryBadge("${timeline.mainClips.size} Clips", Modifier.weight(1f))
                        SummaryBadge("${timeline.overlays.size} Overlays", Modifier.weight(1f))
                        SummaryBadge("${timeline.audioClips.size} Audio", Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Resolution Selector (720p, 1080p, 4K)
                    SectionHeaderRow(
                        icon = Icons.Default.HighQuality,
                        title = "Resolution",
                        trailing = "${exportW}×${exportH}"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExportResolution.entries.forEach { res ->
                            val picked = config.resolution == res
                            val (w, h) = aspectRatio.exportDimensions(res)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (picked) AmberContainer else ActiveRail)
                                    .border(
                                        width = if (picked) 2.dp else 1.dp,
                                        color = if (picked) CinemaAmber else BorderHairline,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onUpdateConfig(
                                            config.copy(
                                                resolution = res,
                                                bitrateMbps = res.recommendedBitrateMbps
                                            )
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("export_res_${res.name.lowercase()}"),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = res.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (picked) CinemaAmber else ChalkWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = res.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (picked) ChalkWhite else SilverMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${w}×${h}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Frame Rate Selector (24, 30, 60 fps)
                    SectionHeaderRow(
                        icon = Icons.Default.Speed,
                        title = "Frame Rate",
                        trailing = "${config.fps} fps"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val fpsOptions = listOf(
                        24 to "Cinema",
                        30 to "Standard",
                        60 to "Smooth"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fpsOptions.forEach { (fps, tag) ->
                            val picked = config.fps == fps
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (picked) AmberContainer else ActiveRail)
                                    .border(
                                        width = if (picked) 2.dp else 1.dp,
                                        color = if (picked) CinemaAmber else BorderHairline,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onUpdateConfig(config.copy(fps = fps)) }
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
                                    .testTag("export_fps_$fps"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "$fps fps",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (picked) CinemaAmber else ChalkWhite
                                )
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SilverMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Bitrate Selector (Presets + Continuous Mbps Slider)
                    SectionHeaderRow(
                        icon = Icons.Default.Tune,
                        title = "Video Bitrate",
                        trailing = "${config.bitrateMbps} Mbps"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExportBitrateTier.entries.forEach { tier ->
                            val picked = config.bitrateMbps == tier.mbps
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (picked) AmberContainer else ActiveRail)
                                    .border(
                                        width = if (picked) 1.5.dp else 1.dp,
                                        color = if (picked) CinemaAmber else BorderHairline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onUpdateConfig(config.copy(bitrateMbps = tier.mbps)) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                                    .testTag("export_bitrate_${tier.name.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = tier.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (picked) CinemaAmber else ChalkWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${tier.mbps}M",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SilverMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = config.bitrateMbps.toFloat().coerceIn(4f, 60f),
                        onValueChange = { value ->
                            onUpdateConfig(config.copy(bitrateMbps = value.roundToInt().coerceIn(4, 60)))
                        },
                        valueRange = 4f..60f,
                        colors = SliderDefaults.colors(
                            thumbColor = CinemaAmber,
                            activeTrackColor = CinemaAmber,
                            inactiveTrackColor = ActiveRail
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_bitrate_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("4 Mbps (Smaller)", style = MaterialTheme.typography.labelSmall, color = SlateDim)
                        Text("60 Mbps (Master)", style = MaterialTheme.typography.labelSmall, color = SlateDim)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Estimated Output Telemetry Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianBg)
                            .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Estimated output size",
                                tint = CinemaAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Estimated MP4 Size",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SilverMuted
                                )
                                Text(
                                    text = "${config.resolution.label} • ${config.fps}fps • H.264 ${config.bitrateMbps} Mbps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateDim
                                )
                            }
                        }
                        Text(
                            text = if (estimatedMb >= 1024f) {
                                String.format("~%.2f GB", estimatedMb / 1024f)
                            } else {
                                String.format("~%.1f MB", estimatedMb)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = CinemaAmber,
                            modifier = Modifier.testTag("export_estimated_size")
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onStartExport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinemaAmber,
                            contentColor = OnAmber
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_mp4_export_button")
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Render ${config.resolution.label} MP4 Now",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onQueueBackgroundExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("queue_background_export_button")
                    ) {
                        Icon(Icons.Default.QueuePlayNext, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add to Background Render Queue",
                            color = ChalkWhite,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }

                is ExportState.Exporting -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${exportState.progressPercent}%",
                            style = MaterialTheme.typography.displaySmall,
                            color = CinemaAmber
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = exportState.stageLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SilverMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { (exportState.progressPercent / 100f).coerceIn(0f, 1f) },
                            color = CinemaAmber,
                            trackColor = ActiveRail,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = onCancelExport,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Cancel Export")
                        }
                    }
                }

                is ExportState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Export succeeded",
                            tint = TrackOverlayEmerald,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (exportState.savedToGallery) "Saved to Gallery (Movies/LumaCut)"
                            else "MP4 Export Ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = ChalkWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${exportState.displayName} • ${String.format("%.2f MB", exportState.sizeMb)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { onUploadToYouTube(exportState.contentUri, projectTitle) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ErrorCrimson,
                                contentColor = ChalkWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("export_upload_youtube_button")
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload to YouTube", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(exportState.contentUri))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share MP4"))
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CinemaAmber,
                                    contentColor = OnAmber
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }

                is ExportState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Export error",
                            tint = ErrorCrimson,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Export Could Not Complete",
                            style = MaterialTheme.typography.titleMedium,
                            color = ChalkWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Close")
                            }
                            Button(
                                onClick = onStartExport,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CinemaAmber,
                                    contentColor = OnAmber
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CinemaAmber,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = SilverMuted
            )
        }
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelMedium,
            color = CinemaAmber
        )
    }
}

@Composable
private fun SummaryBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ActiveRail)
            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = ChalkWhite)
    }
}
