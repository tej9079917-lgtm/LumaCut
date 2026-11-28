package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.media.LocalVoiceCloneProfile
import com.example.media.OfflineLanguageCatalog
import com.example.media.OnlineVoiceCloneTtsEngine
import com.example.security.ClonedVoiceProfile
import com.example.security.OnlineVoiceCloneProvider
import com.example.security.SecureCredentialStore
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTtsStudioBottomSheet(
    initialScript: String,
    captionsScript: String,
    isSynthesizing: Boolean,
    errorMessage: String?,
    hasTimelineClipForVoiceClone: Boolean = false,
    onSynthesizeToTimeline: (
        script: String,
        language: String,
        profile: LocalVoiceCloneProfile,
        pitch: Float,
        speed: Float
    ) -> Unit,
    onSynthesizeOnlineVoiceClone: (
        provider: OnlineVoiceCloneProvider,
        voiceId: String,
        voiceLabel: String,
        script: String,
        language: String,
        stability: Float,
        similarity: Float,
        speed: Float
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onCloneVoiceFromTimelineClip: (
        provider: OnlineVoiceCloneProvider,
        voiceName: String,
        onResult: (Result<ClonedVoiceProfile>) -> Unit
    ) -> Unit = { _, _, cb -> cb(Result.failure(IllegalStateException("Select a clip on the timeline first."))) },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val credentialStore = remember(context) { SecureCredentialStore.getInstance(context) }
    val voiceCloneState by credentialStore.voiceCloneState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = On-Device Offline, 1 = Online AI Voice Clone
    var script by remember(initialScript) { mutableStateOf(initialScript) }
    var selectedLanguage by remember { mutableStateOf("English") }

    // Offline local TTS state
    var selectedProfile by remember { mutableStateOf(LocalVoiceCloneProfile.NATURAL) }
    var pitch by remember { mutableFloatStateOf(LocalVoiceCloneProfile.NATURAL.defaultPitch) }
    var speed by remember { mutableFloatStateOf(LocalVoiceCloneProfile.NATURAL.defaultSpeed) }

    // Online AI Voice Clone state
    var onlineProvider by remember(voiceCloneState.activeProvider) { mutableStateOf(voiceCloneState.activeProvider) }
    var apiKeyDraft by remember(onlineProvider) { mutableStateOf("") }
    var customUrlDraft by remember(voiceCloneState.customEndpointUrl) { mutableStateOf(voiceCloneState.customEndpointUrl) }
    var isVerifyingKey by remember { mutableStateOf(false) }
    var keyStatusMsg by remember { mutableStateOf<String?>(null) }
    var keyErrorMsg by remember { mutableStateOf<String?>(null) }

    var remoteVoices by remember { mutableStateOf<List<ClonedVoiceProfile>>(emptyList()) }
    var isLoadingVoices by remember { mutableStateOf(false) }
    var selectedRemoteVoice by remember { mutableStateOf<ClonedVoiceProfile?>(null) }
    var cloneVoiceName by remember { mutableStateOf("My Studio Voice") }
    var isCloningVoice by remember { mutableStateOf(false) }
    var cloneStatusMsg by remember { mutableStateOf<String?>(null) }
    var cloneErrorMsg by remember { mutableStateOf<String?>(null) }

    var stability by remember { mutableFloatStateOf(0.50f) }
    var similarity by remember { mutableFloatStateOf(0.78f) }
    var onlineSpeed by remember { mutableFloatStateOf(1.0f) }

    val languages = remember { OfflineLanguageCatalog.captionChipOptions }

    fun refreshVoicesForProvider(provider: OnlineVoiceCloneProvider) {
        scope.launch {
            isLoadingVoices = true
            val res = OnlineVoiceCloneTtsEngine.fetchAvailableVoices(context, provider)
            res.onSuccess { list ->
                remoteVoices = list
                if (selectedRemoteVoice == null || list.none { it.remoteVoiceId == selectedRemoteVoice?.remoteVoiceId }) {
                    selectedRemoteVoice = list.firstOrNull()
                }
            }.onFailure { err ->
                cloneErrorMsg = err.localizedMessage
            }
            isLoadingVoices = false
        }
    }

    LaunchedEffect(activeTab, onlineProvider, voiceCloneState.hasKeyFor(onlineProvider), voiceCloneState.savedClonedVoices.size) {
        if (activeTab == 1) {
            refreshVoicesForProvider(onlineProvider)
        }
    }

    val pickVoiceSampleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isCloningVoice = true
                cloneErrorMsg = null
                cloneStatusMsg = "Uploading sample to ${onlineProvider.shortBadge}…"
                val sampleRes = OnlineVoiceCloneTtsEngine.readAudioSampleBytes(context, uri)
                sampleRes.onSuccess { (bytes, fileName, mime) ->
                    val cloneRes = OnlineVoiceCloneTtsEngine.cloneVoiceFromAudioBytes(
                        context = context,
                        provider = onlineProvider,
                        voiceName = cloneVoiceName,
                        audioBytes = bytes,
                        fileName = fileName,
                        mimeType = mime
                    )
                    cloneRes.onSuccess { newProfile ->
                        selectedRemoteVoice = newProfile
                        cloneStatusMsg = "Cloned voice '${newProfile.name}' ready!"
                        refreshVoicesForProvider(onlineProvider)
                    }.onFailure { err ->
                        cloneErrorMsg = err.localizedMessage ?: "Voice cloning failed."
                        cloneStatusMsg = null
                    }
                }.onFailure { err ->
                    cloneErrorMsg = err.localizedMessage ?: "Could not read sample file."
                    cloneStatusMsg = null
                }
                isCloningVoice = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSynthesizing && !isCloningVoice) onDismiss() },
        sheetState = sheetState,
        containerColor = InspectorCharcoal,
        contentColor = ChalkWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .testTag("local_tts_studio_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = if (activeTab == 0) TrackOverlayEmerald else CinemaAmber,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Voice & Cloning Studio", style = MaterialTheme.typography.titleMedium, color = ChalkWhite)
                    Text(
                        text = if (activeTab == 0) {
                            "100% On-Device Offline TTS • Zero Cloud Upload"
                        } else {
                            "Online AI Voice Cloning & Neural TTS via Your API Keys"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeTab == 0) TrackOverlayEmerald else CinemaAmber
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Voice Studio", tint = SilverMuted)
                }
            }

            // Mode Tabs: On-Device Offline vs Online AI Voice Clone
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = ObsidianBg,
                contentColor = CinemaAmber,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = if (activeTab == 0) TrackOverlayEmerald else CinemaAmber
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier.testTag("tts_tab_offline"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Default.OfflineBolt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("On-Device Offline", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    modifier = Modifier.testTag("tts_tab_online_clone"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("Online Voice Clone", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
            }

            // Shared Script Input + Pull Captions Button
            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                label = { Text("Narration or Dubbing Script") },
                placeholder = { Text("Type dialogue or pull from timeline captions…") },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CinemaAmber,
                    unfocusedBorderColor = BorderHairline,
                    focusedTextColor = ChalkWhite,
                    unfocusedTextColor = ChalkWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_tts_script_input")
            )

            if (captionsScript.isNotBlank()) {
                OutlinedButton(
                    onClick = { script = captionsScript },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("local_tts_pull_captions_button")
                ) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pull Text From Timeline Captions", color = ChalkWhite, style = MaterialTheme.typography.labelMedium)
                }
            }

            if (activeTab == 0) {
                // ---- TAB 0: 100% ON-DEVICE OFFLINE TTS ----
                Text("Voice Style & Timbre Profile", style = MaterialTheme.typography.labelLarge, color = SilverMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LocalVoiceCloneProfile.entries.forEach { profile ->
                        val picked = selectedProfile == profile
                        Column(
                            modifier = Modifier
                                .width(128.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (picked) AmberContainer else StudioSlate)
                                .border(
                                    width = if (picked) 1.5.dp else 1.dp,
                                    color = if (picked) CinemaAmber else BorderHairline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedProfile = profile
                                    pitch = profile.defaultPitch
                                    speed = profile.defaultSpeed
                                }
                                .padding(10.dp)
                                .testTag("tts_profile_${profile.name.lowercase()}")
                        ) {
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (picked) CinemaAmber else ChalkWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = profile.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Text("Voice Language (On-Device)", style = MaterialTheme.typography.labelLarge, color = SilverMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languages.forEach { lang ->
                        val picked = selectedLanguage == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (picked) AmberContainer else ActiveRail)
                                .border(1.dp, if (picked) CinemaAmber else BorderHairline, RoundedCornerShape(7.dp))
                                .clickable { selectedLanguage = lang }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (picked) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg)
                        .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Voice Timbre Pitch", style = MaterialTheme.typography.labelMedium, color = SilverMuted)
                        Text("${(pitch * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = CinemaAmber)
                    }
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it },
                        valueRange = 0.55f..1.75f,
                        colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Speech Cadence Speed", style = MaterialTheme.typography.labelMedium, color = SilverMuted)
                        Text(String.format("%.2fx", speed), style = MaterialTheme.typography.labelMedium, color = CinemaAmber)
                    }
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.60f..1.65f,
                        colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage, style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                }

                Button(
                    enabled = !isSynthesizing && script.isNotBlank(),
                    onClick = {
                        onSynthesizeToTimeline(script, selectedLanguage, selectedProfile, pitch, speed)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrackOverlayEmerald, contentColor = ObsidianBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("synthesize_tts_to_timeline_button")
                ) {
                    if (isSynthesizing) {
                        CircularProgressIndicator(color = ObsidianBg, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synthesizing WAV Offline…", style = MaterialTheme.typography.titleSmall)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synthesize & Add to Audio Track", style = MaterialTheme.typography.titleSmall)
                    }
                }
            } else {
                // ---- TAB 1: ONLINE AI VOICE CLONE & NEURAL TTS ----
                Text("1. Online AI Voice Provider & API Key", style = MaterialTheme.typography.labelLarge, color = SilverMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OnlineVoiceCloneProvider.entries.forEach { provider ->
                        val picked = onlineProvider == provider
                        val hasKey = voiceCloneState.hasKeyFor(provider)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (picked) AmberContainer else ActiveRail)
                                .border(
                                    1.dp,
                                    if (picked) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onlineProvider = provider
                                    credentialStore.setActiveVoiceCloneProvider(provider)
                                    keyStatusMsg = null
                                    keyErrorMsg = null
                                    cloneErrorMsg = null
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .testTag("voice_clone_provider_${provider.name.lowercase()}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (hasKey) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TrackOverlayEmerald, modifier = Modifier.size(13.dp))
                                }
                                Text(
                                    text = provider.shortBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (picked) CinemaAmber else ChalkWhite
                                )
                            }
                        }
                    }
                }

                // API Key Box (Encrypted Android Keystore storage)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg)
                        .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(onlineProvider.description, style = MaterialTheme.typography.labelSmall, color = SilverMuted)

                    val hasProviderKey = voiceCloneState.hasKeyFor(onlineProvider)
                    if (hasProviderKey) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Encrypted key saved (${voiceCloneState.maskedActiveKey.ifBlank { "Active" }})",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrackOverlayEmerald
                            )
                            IconButton(
                                onClick = {
                                    credentialStore.clearVoiceCloneApiKey(onlineProvider)
                                    keyStatusMsg = "Cleared ${onlineProvider.shortBadge} key"
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove key", tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (onlineProvider == OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS) {
                        OutlinedTextField(
                            value = customUrlDraft,
                            onValueChange = { customUrlDraft = it },
                            label = { Text("HTTPS Endpoint URL") },
                            placeholder = { Text("https://api.example.com/v1/audio/speech") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        label = { Text("${onlineProvider.shortBadge} API Key") },
                        placeholder = { Text(onlineProvider.keyHint) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinemaAmber,
                            unfocusedBorderColor = BorderHairline,
                            focusedTextColor = ChalkWhite,
                            unfocusedTextColor = ChalkWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("online_voice_api_key_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = !isVerifyingKey && apiKeyDraft.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    isVerifyingKey = true
                                    keyErrorMsg = null
                                    keyStatusMsg = null
                                    val check = OnlineVoiceCloneTtsEngine.verifyApiKey(
                                        provider = onlineProvider,
                                        rawApiKey = apiKeyDraft,
                                        customEndpointUrl = customUrlDraft
                                    )
                                    check.onSuccess { msg ->
                                        credentialStore.saveVoiceCloneApiKey(
                                            provider = onlineProvider,
                                            rawKey = apiKeyDraft,
                                            customEndpointUrl = customUrlDraft
                                        )
                                        apiKeyDraft = ""
                                        keyStatusMsg = msg
                                        refreshVoicesForProvider(onlineProvider)
                                    }.onFailure { err ->
                                        keyErrorMsg = err.localizedMessage ?: "Verification failed"
                                    }
                                    isVerifyingKey = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_verify_save_voice_key")
                        ) {
                            if (isVerifyingKey) {
                                CircularProgressIndicator(color = OnAmber, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verifying…", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Verify & Save Key", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (onlineProvider.signupUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(onlineProvider.signupUrl))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Get Key", style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
                            }
                        }
                    }

                    if (!keyStatusMsg.isNullOrBlank()) {
                        Text(keyStatusMsg!!, style = MaterialTheme.typography.labelSmall, color = TrackOverlayEmerald)
                    }
                    if (!keyErrorMsg.isNullOrBlank()) {
                        Text(keyErrorMsg!!, style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                    }
                }

                // 2. Instant Voice Cloning from Audio Sample or Selected Timeline Clip
                if (onlineProvider.supportsInstantClone) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSlate)
                            .border(1.dp, CinemaAmber.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(17.dp))
                            Text(
                                text = "2. Instant Voice Clone (${onlineProvider.shortBadge})",
                                style = MaterialTheme.typography.labelLarge,
                                color = ChalkWhite
                            )
                        }
                        OutlinedTextField(
                            value = cloneVoiceName,
                            onValueChange = { cloneVoiceName = it },
                            label = { Text("New Cloned Voice Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clone_voice_name_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                enabled = !isCloningVoice && voiceCloneState.hasKeyFor(onlineProvider) && hasTimelineClipForVoiceClone,
                                onClick = {
                                    isCloningVoice = true
                                    cloneErrorMsg = null
                                    cloneStatusMsg = "Extracting clip audio & cloning voice…"
                                    onCloneVoiceFromTimelineClip(onlineProvider, cloneVoiceName) { res ->
                                        isCloningVoice = false
                                        res.onSuccess { profile ->
                                            selectedRemoteVoice = profile
                                            cloneStatusMsg = "Cloned '${profile.name}' from timeline clip!"
                                            refreshVoicesForProvider(onlineProvider)
                                        }.onFailure { err ->
                                            cloneStatusMsg = null
                                            cloneErrorMsg = err.localizedMessage ?: "Clone from clip failed."
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("btn_clone_from_timeline_clip")
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("From Clip", style = MaterialTheme.typography.labelSmall, color = ChalkWhite, maxLines = 1)
                            }

                            OutlinedButton(
                                enabled = !isCloningVoice && voiceCloneState.hasKeyFor(onlineProvider),
                                onClick = { pickVoiceSampleLauncher.launch("audio/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("btn_clone_from_audio_file")
                            ) {
                                Icon(Icons.Default.AudioFile, contentDescription = null, tint = TrackOverlayEmerald, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pick Sample", style = MaterialTheme.typography.labelSmall, color = ChalkWhite, maxLines = 1)
                            }
                        }

                        if (isCloningVoice) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(color = CinemaAmber, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Text(cloneStatusMsg ?: "Cloning voice…", style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                            }
                        } else if (!cloneStatusMsg.isNullOrBlank()) {
                            Text(cloneStatusMsg!!, style = MaterialTheme.typography.labelSmall, color = TrackOverlayEmerald)
                        }
                        if (!cloneErrorMsg.isNullOrBlank()) {
                            Text(cloneErrorMsg!!, style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                        }
                    }
                }

                // 3. Voice Selector (Cloned & Cloud Voices)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "3. Select Cloned or Cloud Voice (${remoteVoices.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = SilverMuted
                    )
                    IconButton(
                        onClick = { refreshVoicesForProvider(onlineProvider) },
                        enabled = !isLoadingVoices,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isLoadingVoices) {
                            CircularProgressIndicator(color = CinemaAmber, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh voices", tint = CinemaAmber, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (remoteVoices.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        remoteVoices.forEach { voice ->
                            val picked = selectedRemoteVoice?.remoteVoiceId == voice.remoteVoiceId
                            Column(
                                modifier = Modifier
                                    .width(136.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (picked) AmberContainer else StudioSlate)
                                    .border(
                                        width = if (picked) 1.5.dp else 1.dp,
                                        color = if (picked) CinemaAmber else BorderHairline,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedRemoteVoice = voice }
                                    .padding(10.dp)
                                    .testTag("online_voice_card_${voice.remoteVoiceId.take(12)}")
                            ) {
                                Text(
                                    text = voice.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (picked) CinemaAmber else ChalkWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (voice.isCloned) "CLONED • ${voice.subtitle}" else voice.subtitle.ifBlank { onlineProvider.shortBadge },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (voice.isCloned) TrackOverlayEmerald else SilverMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Save your ${onlineProvider.shortBadge} API key above, then refresh or clone a voice.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted
                    )
                }

                // Online Voice Controls (Stability, Similarity, Speed)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg)
                        .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onlineProvider == OnlineVoiceCloneProvider.ELEVENLABS) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Voice Stability", style = MaterialTheme.typography.labelMedium, color = SilverMuted)
                            Text("${(stability * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = CinemaAmber)
                        }
                        Slider(
                            value = stability,
                            onValueChange = { stability = it },
                            valueRange = 0.15f..0.95f,
                            colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Clone Similarity Boost", style = MaterialTheme.typography.labelMedium, color = SilverMuted)
                            Text("${(similarity * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = CinemaAmber)
                        }
                        Slider(
                            value = similarity,
                            onValueChange = { similarity = it },
                            valueRange = 0.25f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Speaking Speed", style = MaterialTheme.typography.labelMedium, color = SilverMuted)
                        Text(String.format("%.2fx", onlineSpeed), style = MaterialTheme.typography.labelMedium, color = CinemaAmber)
                    }
                    Slider(
                        value = onlineSpeed,
                        onValueChange = { onlineSpeed = it },
                        valueRange = 0.70f..1.20f,
                        colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage, style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                }

                val activeVoice = selectedRemoteVoice ?: remoteVoices.firstOrNull()
                Button(
                    enabled = !isSynthesizing && !isCloningVoice && script.isNotBlank() &&
                        voiceCloneState.hasKeyFor(onlineProvider) && activeVoice != null,
                    onClick = {
                        val pickedVoice = activeVoice ?: return@Button
                        onSynthesizeOnlineVoiceClone(
                            onlineProvider,
                            pickedVoice.remoteVoiceId,
                            pickedVoice.name,
                            script,
                            selectedLanguage,
                            stability,
                            similarity,
                            onlineSpeed
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("synthesize_online_voice_clone_button")
                ) {
                    if (isSynthesizing) {
                        CircularProgressIndicator(color = OnAmber, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating Cloned AI Audio…", style = MaterialTheme.typography.titleSmall)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Cloned Voice & Add to Track", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}
