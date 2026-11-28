package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.RenderJobEntity
import com.example.data.RenderJobStatus
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
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TrackOverlayEmerald
import com.example.ui.theme.TrackVideoBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderQueueBottomSheet(
    jobs: List<RenderJobEntity>,
    onCancelJob: (String) -> Unit,
    onRetryJob: (String) -> Unit,
    onRemoveJob: (String) -> Unit,
    onClearFinished: () -> Unit,
    onUploadToYouTube: (outputUri: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeCount = jobs.count { it.status == RenderJobStatus.RENDERING || it.status == RenderJobStatus.QUEUED }
    val hasFinished = jobs.any {
        it.status == RenderJobStatus.COMPLETED || it.status == RenderJobStatus.FAILED || it.status == RenderJobStatus.CANCELLED
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = InspectorCharcoal,
        contentColor = ChalkWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AmberContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QueuePlayNext,
                        contentDescription = null,
                        tint = CinemaAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background Render Queue",
                        style = MaterialTheme.typography.titleMedium,
                        color = ChalkWhite
                    )
                    Text(
                        text = if (activeCount > 0) "$activeCount active/queued • UI stays responsive"
                        else "Queue multiple projects for sequential MP4 export",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted
                    )
                }
                if (hasFinished) {
                    TextButton(
                        onClick = onClearFinished,
                        modifier = Modifier.testTag("clear_finished_renders_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = SilverMuted, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", color = SilverMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close render queue", tint = SilverMuted)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (jobs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioSlate)
                        .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.QueuePlayNext,
                        contentDescription = null,
                        tint = SilverMuted.copy(alpha = 0.6f),
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Render Queue is Empty", style = MaterialTheme.typography.titleSmall, color = ChalkWhite)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Add to Queue' inside Export or use a project card's menu to batch-render edits in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(jobs, key = { it.id }) { job ->
                        RenderJobRowCard(
                            job = job,
                            onCancel = { onCancelJob(job.id) },
                            onRetry = { onRetryJob(job.id) },
                            onRemove = { onRemoveJob(job.id) },
                            onShare = { uriStr ->
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share Rendered MP4"))
                            },
                            onUploadYouTube = { uriStr -> onUploadToYouTube(uriStr, job.projectTitle) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderJobRowCard(
    job: RenderJobEntity,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onShare: (String) -> Unit,
    onUploadYouTube: (String) -> Unit
) {
    val status = job.status
    val accent = when (status) {
        RenderJobStatus.RENDERING -> CinemaAmber
        RenderJobStatus.QUEUED -> TrackVideoBlue
        RenderJobStatus.COMPLETED -> TrackOverlayEmerald
        RenderJobStatus.FAILED -> ErrorCrimson
        RenderJobStatus.CANCELLED -> SilverMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StudioSlate)
            .border(
                width = if (status == RenderJobStatus.RENDERING) 1.5.dp else 1.dp,
                color = if (status == RenderJobStatus.RENDERING) CinemaAmber.copy(alpha = 0.7f) else BorderHairline,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
            .testTag("render_job_card_${job.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (status) {
                    RenderJobStatus.RENDERING -> Icons.Default.PlayArrow
                    RenderJobStatus.QUEUED -> Icons.Default.HourglassTop
                    RenderJobStatus.COMPLETED -> Icons.Default.CheckCircle
                    RenderJobStatus.FAILED -> Icons.Default.ErrorOutline
                    RenderJobStatus.CANCELLED -> Icons.Default.Cancel
                },
                contentDescription = status.name,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.projectTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = ChalkWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${job.resolutionName.replace('_', ' ')} • ${job.frameRate}fps • ${job.stageMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SilverMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (status == RenderJobStatus.RENDERING) {
                Text(
                    text = "${(job.progress * 100f).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = CinemaAmber
                )
            }
        }

        if (status == RenderJobStatus.RENDERING) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { job.progress.coerceIn(0f, 1f) },
                color = CinemaAmber,
                trackColor = ActiveRail,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }

        if (status == RenderJobStatus.FAILED && !job.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = job.errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = ErrorCrimson
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (status) {
                RenderJobStatus.RENDERING, RenderJobStatus.QUEUED -> {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", color = ErrorCrimson, style = MaterialTheme.typography.labelMedium)
                    }
                }
                RenderJobStatus.COMPLETED -> {
                    val uriStr = job.outputUri
                    if (!uriStr.isNullOrBlank()) {
                        Button(
                            onClick = { onUploadYouTube(uriStr) },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("YouTube", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onShare(uriStr) },
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = ChalkWhite, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share MP4", color = ChalkWhite, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove job", tint = SilverMuted, modifier = Modifier.size(16.dp))
                    }
                }
                RenderJobStatus.FAILED, RenderJobStatus.CANCELLED -> {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveRail, contentColor = ChalkWhite),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onRemove, modifier = Modifier.height(36.dp)) {
                        Text("Remove", color = SilverMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
