package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.ai.AiCreativeIdeasResult
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
import com.example.ui.theme.TrackOverlayEmerald

private enum class AiStudioTab(val label: String) {
    HOOKS("Hooks & Ideas"),
    COLORIST("AI Colorist"),
    CAPTIONS("AI Captions")
}

@Composable
fun AiCreativeStudioInspector(
    aiIdeasLoading: Boolean,
    aiIdeasResult: AiCreativeIdeasResult?,
    aiIdeasError: String?,
    aiColoristLoading: Boolean,
    aiColoristNote: String?,
    aiColoristError: String?,
    aiCaptionsLoading: Boolean,
    aiCaptionsError: String?,
    onGenerateIdeas: (String) -> Unit,
    onInsertHookOverlay: (String) -> Unit,
    onGenerateColorGrade: (String) -> Unit,
    onGenerateCaptionsFromScript: (String) -> Unit,
    onClose: () -> Unit
) {
    var activeTab by rememberSaveable { mutableStateOf(AiStudioTab.HOOKS) }
    var topicInput by rememberSaveable { mutableStateOf("") }
    var colorPrompt by rememberSaveable { mutableStateOf("") }
    var scriptInput by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_creative_studio_inspector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 350.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 14.dp)
        ) {
            InspectorHeader(
                title = "Gemini AI Studio",
                onClose = onClose
            )

            // Sub-tab switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiStudioTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    val icon = when (tab) {
                        AiStudioTab.HOOKS -> Icons.Default.AutoAwesome
                        AiStudioTab.COLORIST -> Icons.Default.Palette
                        AiStudioTab.CAPTIONS -> Icons.Default.Subtitles
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AmberContainer else ActiveRail)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { activeTab = tab }
                            .padding(vertical = 7.dp, horizontal = 6.dp)
                            .testTag("ai_tab_${tab.name.lowercase()}"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) CinemaAmber else SilverMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) CinemaAmber else ChalkWhite,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (activeTab) {
                AiStudioTab.HOOKS -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = topicInput,
                            onValueChange = { topicInput = it },
                            placeholder = { Text("Describe your video topic or leave blank to analyze frame…") },
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("ai_topic_input")
                        )

                        FilledTonalButton(
                            onClick = { onGenerateIdeas(topicInput) },
                            enabled = !aiIdeasLoading,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CinemaAmber,
                                contentColor = OnAmber
                            ),
                            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("btn_ai_generate_ideas")
                        ) {
                            if (aiIdeasLoading) {
                                CircularProgressIndicator(
                                    color = OnAmber,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing Frame & Timeline…", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate Hooks, Titles & Edit Ideas", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        if (!aiIdeasError.isNullOrBlank()) {
                            AiErrorBanner(aiIdeasError)
                        }

                        if (aiIdeasResult != null) {
                            if (aiIdeasResult.hooks.isNotEmpty()) {
                                Text("Opening Hooks (Tap + to place on timeline)", style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                                aiIdeasResult.hooks.forEachIndexed { idx, hook ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ObsidianBg)
                                            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = hook,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ChalkWhite,
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilledTonalButton(
                                            onClick = { onInsertHookOverlay(hook) },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = AmberContainer,
                                                contentColor = CinemaAmber
                                            ),
                                            modifier = Modifier.height(30.dp).testTag("btn_insert_ai_hook_$idx")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Insert hook", modifier = Modifier.size(14.dp))
                                            Text("Hook", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            if (aiIdeasResult.titles.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Title Ideas", style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(aiIdeasResult.titles.joinToString("\n")))
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy titles", tint = SilverMuted, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ObsidianBg)
                                        .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    aiIdeasResult.titles.forEach { title ->
                                        Text("• $title", style = MaterialTheme.typography.bodySmall, color = ChalkWhite)
                                    }
                                }
                            }

                            if (aiIdeasResult.hashtags.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Hashtags", style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(aiIdeasResult.hashtags.joinToString(" ")))
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy hashtags", tint = SilverMuted, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Text(
                                    text = aiIdeasResult.hashtags.joinToString(" "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TrackOverlayEmerald,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ObsidianBg)
                                        .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                )
                            }

                            if (aiIdeasResult.editTips.isNotEmpty()) {
                                Text("Smart Edit Recommendations", style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ObsidianBg)
                                        .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    aiIdeasResult.editTips.forEach { tip ->
                                        Text("→ $tip", style = MaterialTheme.typography.bodySmall, color = SilverMuted)
                                    }
                                }
                            }
                        }
                    }
                }

                AiStudioTab.COLORIST -> {
                    val quickLooks = remember {
                        listOf(
                            "Blade Runner Neon",
                            "Kodak 2383 Warm Film",
                            "Moody A24 Indie",
                            "Golden Hour Sunset",
                            "Crisp Commercial Clean"
                        )
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickLooks.forEach { look ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(ActiveRail)
                                        .border(1.dp, BorderHairline, RoundedCornerShape(7.dp))
                                        .clickable {
                                            colorPrompt = look
                                            onGenerateColorGrade(look)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(look, style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = colorPrompt,
                            onValueChange = { colorPrompt = it },
                            placeholder = { Text("e.g., Kodak Portra 400 with warm halation and soft shadows…") },
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("ai_color_prompt_input")
                        )

                        FilledTonalButton(
                            onClick = {
                                val prompt = colorPrompt.ifBlank { "Cinematic balanced film look" }
                                onGenerateColorGrade(prompt)
                            },
                            enabled = !aiColoristLoading,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CinemaAmber,
                                contentColor = OnAmber
                            ),
                            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("btn_ai_color_grade")
                        ) {
                            if (aiColoristLoading) {
                                CircularProgressIndicator(color = OnAmber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grading Frame with Gemini…", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply Gemini AI Color Grade", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        if (!aiColoristError.isNullOrBlank()) {
                            AiErrorBanner(aiColoristError)
                        }
                        if (!aiColoristNote.isNullOrBlank()) {
                            Text(
                                text = "Colorist Note: $aiColoristNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = TrackOverlayEmerald,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianBg)
                                    .border(1.dp, TrackOverlayEmerald.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            )
                        }
                    }
                }

                AiStudioTab.CAPTIONS -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = scriptInput,
                            onValueChange = { scriptInput = it },
                            placeholder = { Text("Paste voiceover script or narration lines to auto-split into timed karaoke captions…") },
                            minLines = 3,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("ai_script_input")
                        )

                        FilledTonalButton(
                            onClick = { onGenerateCaptionsFromScript(scriptInput) },
                            enabled = !aiCaptionsLoading && scriptInput.isNotBlank(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CinemaAmber,
                                contentColor = OnAmber
                            ),
                            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("btn_ai_generate_captions")
                        ) {
                            if (aiCaptionsLoading) {
                                CircularProgressIndicator(color = OnAmber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Timing Karaoke Captions…", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate Timed Karaoke Captions", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        if (!aiCaptionsError.isNullOrBlank()) {
                            AiErrorBanner(aiCaptionsError)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiErrorBanner(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = ErrorCrimson,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ErrorCrimson.copy(alpha = 0.14f))
            .border(1.dp, ErrorCrimson.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    )
}
