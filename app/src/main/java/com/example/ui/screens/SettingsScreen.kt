package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.media.OfflineLanguagePackState
import com.example.media.OfflinePackStatus
import com.example.media.OfflineSpeechModelManager
import com.example.media.OnlineVoiceCloneTtsEngine
import com.example.media.SpeechDiagnosticReport
import com.example.media.SpeechServicesDiagnosticEngine
import com.example.security.OnlineVoiceCloneProvider
import com.example.security.SecureCredentialStore
import com.example.ui.components.SpeechServicesDiagnosticCard
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

private enum class LanguageFilter(val label: String) {
    ALL("All"),
    INDIAN("Indian"),
    GLOBAL("Global")
}

private val INDIAN_ISO_CODES = setOf("te", "hi", "ta", "kn", "ml", "bn", "mr")

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val modelManager = remember(context) { OfflineSpeechModelManager.getInstance(context) }
    val credentialStore = remember(context) { SecureCredentialStore.getInstance(context) }

    val packs by modelManager.packs.collectAsStateWithLifecycle()
    val statusMessage by modelManager.statusMessage.collectAsStateWithLifecycle()
    val disabledTags by modelManager.disabledOfflineTags.collectAsStateWithLifecycle()
    val voiceCloneState by credentialStore.voiceCloneState.collectAsStateWithLifecycle()

    var diagnosticReport by remember { mutableStateOf(SpeechDiagnosticReport(isChecking = true)) }
    var languageFilter by remember { mutableStateOf(LanguageFilter.ALL) }

    // Voice Clone API key section state
    var selectedVoiceProvider by remember(voiceCloneState.activeProvider) {
        mutableStateOf(voiceCloneState.activeProvider)
    }
    var voiceApiKeyDraft by remember(selectedVoiceProvider) { mutableStateOf("") }
    var customTtsUrlDraft by remember(voiceCloneState.customEndpointUrl) {
        mutableStateOf(voiceCloneState.customEndpointUrl)
    }
    var isVerifyingVoiceKey by remember { mutableStateOf(false) }
    var voiceKeyFeedback by remember { mutableStateOf<String?>(null) }
    var voiceKeyError by remember { mutableStateOf<String?>(null) }

    fun triggerDiagnosticAndRefresh() {
        scope.launch {
            diagnosticReport = diagnosticReport.copy(isChecking = true)
            modelManager.refreshInstalledModels()
            diagnosticReport = SpeechServicesDiagnosticEngine.runFullDiagnostic(context)
        }
    }

    LaunchedEffect(Unit) {
        triggerDiagnosticAndRefresh()
    }

    // Prioritize Telugu & English right at the top of Language Management for instant access
    val filteredPacks = remember(packs, languageFilter) {
        val base = when (languageFilter) {
            LanguageFilter.ALL -> packs
            LanguageFilter.INDIAN -> packs.filter { it.spec.iso6391 in INDIAN_ISO_CODES }
            LanguageFilter.GLOBAL -> packs.filter { it.spec.iso6391 !in INDIAN_ISO_CODES }
        }
        base.sortedBy { pack ->
            when (pack.spec.iso6391) {
                "te" -> 0
                "en" -> 1
                "hi" -> 2
                "ta" -> 3
                else -> 10
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(color = StudioSlate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ChalkWhite
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Studio Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = ChalkWhite
                        )
                        Text(
                            text = "Language Management • Speech Diagnostics • AI Voice Keys",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { triggerDiagnosticAndRefresh() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_refresh_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh language models and diagnostics",
                            tint = CinemaAmber
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .testTag("settings_screen_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 14.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Google Speech Services & Telugu Registration Diagnostic Component
            item {
                SpeechServicesDiagnosticCard(
                    report = diagnosticReport,
                    onRunDiagnostic = { triggerDiagnosticAndRefresh() }
                )
            }

            // 2. Language Management Header + Filters
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InspectorCharcoal)
                        .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = TrackOverlayEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Language Management",
                                style = MaterialTheme.typography.titleSmall,
                                color = ChalkWhite
                            )
                            Text(
                                text = "Toggle offline speech models ON to download on-device packs (e.g., Telugu, English) with live progress.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverMuted
                            )
                        }
                    }

                    if (!statusMessage.isNullOrBlank()) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = CinemaAmber
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageFilter.entries.forEach { filter ->
                            val selected = languageFilter == filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AmberContainer else ActiveRail)
                                    .border(
                                        1.dp,
                                        if (selected) CinemaAmber else BorderHairline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { languageFilter = filter }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("lang_filter_${filter.name.lowercase()}")
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) CinemaAmber else ChalkWhite
                                )
                            }
                        }
                    }
                }
            }

            // Language Management Rows with Toggle Switches & Live Download Progress Indicators
            items(filteredPacks, key = { it.spec.bcp47 }) { pack ->
                val isToggleChecked = remember(pack, disabledTags) {
                    modelManager.isOfflineToggleOn(pack)
                }
                LanguageModelToggleRow(
                    pack = pack,
                    isToggleChecked = isToggleChecked,
                    onToggle = { enabled ->
                        modelManager.toggleLanguageDownload(pack.spec, enabled)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (enabled) {
                                    "Requesting ${pack.spec.displayName} offline model…"
                                } else {
                                    "${pack.spec.displayName} offline mode disabled"
                                }
                            )
                        }
                    }
                )
            }

            // 3. Online AI Voice Clone & TTS API Keys Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InspectorCharcoal)
                        .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                        .testTag("settings_voice_clone_section"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = CinemaAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Online AI Voice Clone & TTS Keys",
                                style = MaterialTheme.typography.titleSmall,
                                color = ChalkWhite
                            )
                            Text(
                                text = "Stored locally with Android Keystore AES-256-GCM encryption for cloud voice cloning & dubbing.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverMuted
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OnlineVoiceCloneProvider.entries.forEach { provider ->
                            val picked = selectedVoiceProvider == provider
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
                                        selectedVoiceProvider = provider
                                        credentialStore.setActiveVoiceCloneProvider(provider)
                                        voiceKeyFeedback = null
                                        voiceKeyError = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("settings_voice_provider_${provider.name.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (hasKey) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = TrackOverlayEmerald,
                                            modifier = Modifier.size(13.dp)
                                        )
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

                    Text(
                        text = selectedVoiceProvider.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted
                    )

                    if (voiceCloneState.hasKeyFor(selectedVoiceProvider)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianBg)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Saved key: ${voiceCloneState.maskedActiveKey.ifBlank { "Configured" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrackOverlayEmerald
                            )
                            IconButton(
                                onClick = {
                                    credentialStore.clearVoiceCloneApiKey(selectedVoiceProvider)
                                    voiceKeyFeedback = "Removed ${selectedVoiceProvider.shortBadge} key"
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Remove saved API key",
                                    tint = ErrorCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (selectedVoiceProvider == OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS) {
                        OutlinedTextField(
                            value = customTtsUrlDraft,
                            onValueChange = { customTtsUrlDraft = it },
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
                        value = voiceApiKeyDraft,
                        onValueChange = { voiceApiKeyDraft = it },
                        label = { Text("${selectedVoiceProvider.shortBadge} API Key") },
                        placeholder = { Text(selectedVoiceProvider.keyHint) },
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
                            .testTag("settings_voice_api_key_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = !isVerifyingVoiceKey && voiceApiKeyDraft.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    isVerifyingVoiceKey = true
                                    voiceKeyError = null
                                    voiceKeyFeedback = null
                                    val check = OnlineVoiceCloneTtsEngine.verifyApiKey(
                                        provider = selectedVoiceProvider,
                                        rawApiKey = voiceApiKeyDraft,
                                        customEndpointUrl = customTtsUrlDraft
                                    )
                                    check.onSuccess { msg ->
                                        credentialStore.saveVoiceCloneApiKey(
                                            provider = selectedVoiceProvider,
                                            rawKey = voiceApiKeyDraft,
                                            customEndpointUrl = customTtsUrlDraft
                                        )
                                        voiceApiKeyDraft = ""
                                        voiceKeyFeedback = msg
                                    }.onFailure { err ->
                                        voiceKeyError = err.localizedMessage ?: "Key verification failed"
                                    }
                                    isVerifyingVoiceKey = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("settings_save_voice_key_button")
                        ) {
                            if (isVerifyingVoiceKey) {
                                CircularProgressIndicator(color = OnAmber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verifying…", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Verify & Save Key", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (selectedVoiceProvider.signupUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(selectedVoiceProvider.signupUrl))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                modifier = Modifier.height(42.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Get Key", style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
                            }
                        }
                    }

                    if (!voiceKeyFeedback.isNullOrBlank()) {
                        Text(voiceKeyFeedback!!, style = MaterialTheme.typography.labelSmall, color = TrackOverlayEmerald)
                    }
                    if (!voiceKeyError.isNullOrBlank()) {
                        Text(voiceKeyError!!, style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageModelToggleRow(
    pack: OfflineLanguagePackState,
    isToggleChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val isDownloading = pack.status == OfflinePackStatus.DOWNLOADING
    val isInstalled = pack.status == OfflinePackStatus.INSTALLED
    val isOnlineOnly = pack.status == OfflinePackStatus.ONLINE_ONLY
    val isUnsupported = pack.status == OfflinePackStatus.NOT_SUPPORTED

    val statusColor = when (pack.status) {
        OfflinePackStatus.INSTALLED -> TrackOverlayEmerald
        OfflinePackStatus.DOWNLOADING -> CinemaAmber
        OfflinePackStatus.ONLINE_ONLY -> CinemaAmber
        OfflinePackStatus.NOT_SUPPORTED -> ErrorCrimson
        else -> SilverMuted
    }

    val statusLabel = when (pack.status) {
        OfflinePackStatus.INSTALLED -> if (isToggleChecked) "Installed • Offline Active" else "Installed • Offline Paused"
        OfflinePackStatus.DOWNLOADING -> "Downloading ${pack.downloadProgress.coerceIn(0, 100)}%"
        OfflinePackStatus.AVAILABLE_TO_DOWNLOAD -> "Available to Download"
        OfflinePackStatus.ONLINE_ONLY -> "Online-Only on this Phone (Device Speech ready)"
        OfflinePackStatus.NOT_SUPPORTED -> "Enable Google Speech Services"
        OfflinePackStatus.UNKNOWN -> "Checking status…"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioSlate)
            .border(
                width = if (isDownloading || (isInstalled && isToggleChecked)) 1.2.dp else 1.dp,
                color = when {
                    isDownloading -> CinemaAmber
                    isInstalled && isToggleChecked -> TrackOverlayEmerald.copy(alpha = 0.6f)
                    else -> BorderHairline
                },
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("lang_model_row_${pack.spec.iso6391}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isInstalled) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${pack.spec.displayName} (${pack.spec.nativeLabel})",
                    style = MaterialTheme.typography.titleSmall,
                    color = ChalkWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${pack.deviceLocaleTag ?: pack.spec.bcp47} • $statusLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!pack.note.isNullOrBlank() && (isOnlineOnly || isDownloading)) {
                    Text(
                        text = pack.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = isToggleChecked,
                onCheckedChange = { wantEnabled -> onToggle(wantEnabled) },
                enabled = !isUnsupported,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnAmber,
                    checkedTrackColor = if (isDownloading) CinemaAmber else TrackOverlayEmerald,
                    uncheckedThumbColor = SilverMuted,
                    uncheckedTrackColor = ObsidianBg
                ),
                modifier = Modifier.testTag("lang_toggle_${pack.spec.iso6391}")
            )
        }

        // Progress indicator shown whenever the language model is actively downloading
        if (isDownloading) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val progressFraction = pack.downloadProgress.coerceIn(0, 100) / 100f
                if (pack.downloadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = CinemaAmber,
                        trackColor = ObsidianBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("lang_progress_${pack.spec.iso6391}")
                    )
                } else {
                    LinearProgressIndicator(
                        color = CinemaAmber,
                        trackColor = ObsidianBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("lang_progress_${pack.spec.iso6391}")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Downloading ${pack.spec.displayName} offline pack…",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted
                    )
                    Text(
                        text = "${pack.downloadProgress.coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = CinemaAmber
                    )
                }
            }
        }
    }
}
