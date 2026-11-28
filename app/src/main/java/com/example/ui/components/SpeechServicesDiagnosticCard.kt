package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.media.DiagnosticHealth
import com.example.media.SpeechDiagnosticReport
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TrackOverlayEmerald

/**
 * Diagnostic UI component that inspects Google Speech Services presence, package validity,
 * active RecognitionService registration, and specifically Telugu (`te-IN`) language pack registration.
 */
@Composable
fun SpeechServicesDiagnosticCard(
    report: SpeechDiagnosticReport,
    onRunDiagnostic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = when (report.overallHealth) {
        DiagnosticHealth.HEALTHY -> TrackOverlayEmerald
        DiagnosticHealth.WARNING -> CinemaAmber
        DiagnosticHealth.ACTION_REQUIRED -> ErrorCrimson
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StudioSlate)
            .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("speech_diagnostic_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                if (report.isChecking) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = when (report.overallHealth) {
                            DiagnosticHealth.HEALTHY -> Icons.Default.CheckCircle
                            DiagnosticHealth.WARNING -> Icons.Default.WarningAmber
                            DiagnosticHealth.ACTION_REQUIRED -> Icons.Default.ErrorOutline
                        },
                        contentDescription = "Google Speech diagnostic status",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Google Speech Services Diagnostics",
                    style = MaterialTheme.typography.titleSmall,
                    color = ChalkWhite
                )
                Text(
                    text = if (report.isChecking) "Inspecting device Speech Services & Telugu registration…" else report.statusHeadline,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
            OutlinedButton(
                onClick = onRunDiagnostic,
                enabled = !report.isChecking,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("btn_rerun_speech_diagnostic")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Check", style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
            }
        }

        // Dedicated Telugu (te-IN) status message banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianBg)
                .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                .padding(10.dp)
                .testTag("speech_diagnostic_telugu_status")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Telugu (te-IN) Registration Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaAmber
                )
                Text(
                    text = report.teluguStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkWhite
                )
            }
        }

        // Live checklist rows
        if (report.checks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.checks.forEach { item ->
                    val rowTint = when {
                        item.passed && !item.warningOnly -> TrackOverlayEmerald
                        item.warningOnly -> CinemaAmber
                        else -> ErrorCrimson
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBg.copy(alpha = 0.65f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                item.passed && !item.warningOnly -> Icons.Default.CheckCircle
                                item.warningOnly -> Icons.Default.WarningAmber
                                else -> Icons.Default.ErrorOutline
                            },
                            contentDescription = null,
                            tint = rowTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = ChalkWhite
                            )
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverMuted
                            )
                        }
                    }
                }
            }
        }

        // Actionable 1-tap system recovery buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { openVoiceInputSettings(context) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = ActiveRail,
                    contentColor = ChalkWhite
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("diag_btn_speech_settings")
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Speech Settings", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            FilledTonalButton(
                onClick = { openGoogleSpeechAppInfo(context) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AmberContainer,
                    contentColor = CinemaAmber
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("diag_btn_enable_google_speech")
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Enable Google Speech", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

private fun openVoiceInputSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openGoogleSpeechAppInfo(context: Context) {
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", "com.google.android.tts", null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(appDetails) }.isSuccess) return

    val playStore = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(playStore) }
}
