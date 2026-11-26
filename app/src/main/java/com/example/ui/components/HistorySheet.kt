package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.history.CommandCategory
import com.example.history.HistoryEntryUi
import com.example.history.HistorySnapshotUi
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SlateDim
import com.example.ui.theme.TrackAudioRose
import com.example.ui.theme.TrackOverlayEmerald
import com.example.ui.theme.TrackVideoBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHistoryBottomSheet(
    history: HistorySnapshotUi,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onJumpToIndex: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = InspectorCharcoal,
        contentColor = ChalkWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = CinemaAmber,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit History",
                        style = MaterialTheme.typography.titleLarge,
                        color = ChalkWhite
                    )
                    Text(
                        text = "${history.appliedCount} of ${history.totalCount} steps applied",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted
                    )
                }
                if (history.totalCount > 0) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("history_clear_button")
                    ) {
                        Text("Clear", color = SilverMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Undo / Redo Action Row with explicit Command labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onUndo,
                    enabled = history.canUndo,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ActiveRail,
                        contentColor = ChalkWhite,
                        disabledContainerColor = ActiveRail.copy(alpha = 0.4f),
                        disabledContentColor = SlateDim
                    ),
                    modifier = Modifier.weight(1f).testTag("history_sheet_undo")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = history.undoLabel?.let { "Undo: $it" } ?: "Undo",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
                FilledTonalButton(
                    onClick = onRedo,
                    enabled = history.canRedo,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ActiveRail,
                        contentColor = ChalkWhite,
                        disabledContainerColor = ActiveRail.copy(alpha = 0.4f),
                        disabledContentColor = SlateDim
                    ),
                    modifier = Modifier.weight(1f).testTag("history_sheet_redo")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = history.redoLabel?.let { "Redo: $it" } ?: "Redo",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (history.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ActiveRail.copy(alpha = 0.5f))
                        .padding(vertical = 28.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Every cut, move, trim, filter, and style edit will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Initial state row (index 0)
                    item(key = "initial_state") {
                        val isAtStart = history.appliedCount == 0
                        HistoryRowCard(
                            icon = Icons.Default.Restore,
                            accent = SilverMuted,
                            label = "Session Start",
                            stepText = "#0",
                            isApplied = true,
                            isCurrent = isAtStart,
                            onClick = { onJumpToIndex(0) },
                            testTag = "history_entry_0"
                        )
                    }

                    items(history.entries, key = { "${it.index}_${it.label}" }) { entry ->
                        HistoryRowCard(
                            icon = entry.category.icon(),
                            accent = entry.category.accentColor(),
                            label = entry.label,
                            stepText = "#${entry.index}",
                            isApplied = entry.isApplied,
                            isCurrent = entry.isCurrent,
                            onClick = { onJumpToIndex(entry.index) },
                            testTag = "history_entry_${entry.index}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRowCard(
    icon: ImageVector,
    accent: Color,
    label: String,
    stepText: String,
    isApplied: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isApplied) 1f else 0.5f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) AmberContainer else ActiveRail)
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = if (isCurrent) CinemaAmber else BorderHairline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isCurrent) CinemaAmber else accent,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrent) CinemaAmber else ChalkWhite,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isCurrent) "Active" else if (isApplied) stepText else "Redo $stepText",
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) CinemaAmber else SilverMuted
        )
        if (isCurrent) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Current state",
                tint = CinemaAmber,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun CommandCategory.icon(): ImageVector = when (this) {
    CommandCategory.CUT -> Icons.Default.ContentCut
    CommandCategory.MOVE -> Icons.Default.OpenWith
    CommandCategory.TRIM -> Icons.Default.Straighten
    CommandCategory.MEDIA -> Icons.Default.VideoLibrary
    CommandCategory.FILTER -> Icons.Default.AutoAwesome
    CommandCategory.COLOR -> Icons.Default.Tune
    CommandCategory.AUDIO -> Icons.Default.GraphicEq
    CommandCategory.TRANSITION -> Icons.Default.MovieFilter
    CommandCategory.STYLE -> Icons.Default.TextFields
    CommandCategory.CANVAS -> Icons.Default.AspectRatio
    CommandCategory.MOTION -> Icons.Default.Animation
    CommandCategory.SPEED -> Icons.Default.Speed
}

private fun CommandCategory.accentColor(): Color = when (this) {
    CommandCategory.CUT -> CinemaAmber
    CommandCategory.MOVE -> TrackOverlayEmerald
    CommandCategory.TRIM -> TrackVideoBlue
    CommandCategory.MEDIA -> TrackVideoBlue
    CommandCategory.FILTER -> CinemaAmber
    CommandCategory.COLOR -> CinemaAmber
    CommandCategory.AUDIO -> TrackAudioRose
    CommandCategory.TRANSITION -> CinemaAmber
    CommandCategory.STYLE -> CinemaAmber
    CommandCategory.CANVAS -> SilverMuted
    CommandCategory.MOTION -> TrackOverlayEmerald
    CommandCategory.SPEED -> TrackAudioRose
}
