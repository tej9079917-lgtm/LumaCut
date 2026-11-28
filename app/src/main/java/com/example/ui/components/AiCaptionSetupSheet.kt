package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Login
import com.example.ai.MultiProviderTranscriptionEngine
import com.example.media.OfflineLanguagePackState
import com.example.media.OfflinePackStatus
import com.example.media.OfflineSpeechModelManager
import com.example.media.SpeechDiagnosticReport
import com.example.media.SpeechServicesDiagnosticEngine
import com.example.security.AiCaptionProvider
import com.example.security.FirebaseAuthManager
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
import com.example.ui.theme.TrackVideoBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCaptionSetupBottomSheet(
    initialTab: Int = 0, // 0 = AI Login / Providers, 1 = Offline All-Language Models
    onSelectLanguageAndTranscribeOffline: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val credentialStore = remember(context) { SecureCredentialStore.getInstance(context) }
    val offlineManager = remember(context) { OfflineSpeechModelManager.getInstance(context) }
    val authManager = remember(context) { FirebaseAuthManager.getInstance(context) }

    val aiCredState by credentialStore.aiState.collectAsStateWithLifecycle()
    val authState by authManager.authState.collectAsStateWithLifecycle()
    val offlinePacks by offlineManager.packs.collectAsStateWithLifecycle()
    val offlineStatusMsg by offlineManager.statusMessage.collectAsStateWithLifecycle()
    val isOfflineCapable by offlineManager.isOnDeviceSupported.collectAsStateWithLifecycle()
    val needsSpeechSetup by offlineManager.needsSpeechServicesSetup.collectAsStateWithLifecycle()

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var focusedProvider by remember(aiCredState.activeProvider) { mutableStateOf(aiCredState.activeProvider) }
    var apiKeyDraft by remember(focusedProvider) { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var verifyMessage by remember { mutableStateOf<String?>(null) }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var langSearch by remember { mutableStateOf("") }
    var speechDiagnostic by remember { mutableStateOf(SpeechDiagnosticReport(isChecking = true)) }

    LaunchedEffect(Unit) {
        offlineManager.refreshInstalledModels()
        speechDiagnostic = SpeechServicesDiagnosticEngine.runFullDiagnostic(context)
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
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Captions Engine & Models", style = MaterialTheme.typography.titleMedium, color = ChalkWhite)
                    Text(
                        text = "Active Cloud: ${aiCredState.activeProvider.shortBadge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CinemaAmber
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close AI setup", tint = SilverMuted)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = StudioSlate,
                contentColor = CinemaAmber,
                indicator = { positions ->
                    if (selectedTab < positions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                            color = CinemaAmber
                        )
                    }
                },
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("tab_ai_login"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("AI Login", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("tab_offline_models"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Offline Models", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Firebase Auth Google Sign-In Gate for Cloud Built-in Gemini AI
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianBg)
                            .border(
                                width = 1.dp,
                                color = if (authState.isSignedIn) TrackOverlayEmerald.copy(alpha = 0.6f) else CinemaAmber.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                            .testTag("firebase_google_auth_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Google Account",
                                tint = if (authState.isSignedIn) TrackOverlayEmerald else CinemaAmber,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (authState.isSignedIn) {
                                        authState.displayName ?: "Google Account Connected"
                                    } else {
                                        "Firebase Cloud AI Sign-In"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ChalkWhite
                                )
                                Text(
                                    text = if (authState.isSignedIn) {
                                        authState.email ?: "Cloud Gemini Auto-Captions unlocked"
                                    } else {
                                        "Required only for Cloud Gemini Auto-Captions. Offline AI stays 100% account-free."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SilverMuted
                                )
                            }
                        }

                        if (authState.isSignedIn) {
                            OutlinedButton(
                                enabled = !authState.isLoading,
                                onClick = { authManager.signOut(context, scope) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("firebase_google_signout_button")
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out of Google", color = ErrorCrimson, style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Button(
                                enabled = !authState.isLoading,
                                onClick = {
                                    val hostActivity = context.findAuthActivity()
                                    if (hostActivity != null) {
                                        authManager.signInWithGoogle(hostActivity, scope)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("firebase_google_signin_button")
                            ) {
                                if (authState.isLoading) {
                                    CircularProgressIndicator(
                                        color = OnAmber,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Signing in…", style = MaterialTheme.typography.labelLarge)
                                } else {
                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign in with Google", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        authState.statusMessage?.let { msg ->
                            Text(msg, style = MaterialTheme.typography.labelSmall, color = TrackOverlayEmerald)
                        }
                        authState.errorMessage?.let { err ->
                            Text(err, style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                        }
                    }

                    Text(
                        text = "Or connect a personal Gemini, Groq Whisper, or OpenAI key encrypted on-device with Android Keystore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMuted
                    )

                    AiCaptionProvider.entries.forEach { provider ->
                        val isSelected = focusedProvider == provider
                        val isActive = aiCredState.activeProvider == provider
                        val connected = aiCredState.hasKeyFor(provider)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AmberContainer.copy(alpha = 0.55f) else StudioSlate)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) CinemaAmber else BorderHairline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    focusedProvider = provider
                                    verifyMessage = null
                                    verifyError = null
                                    if (!provider.requiresUserKey || connected) {
                                        credentialStore.setActiveAiProvider(provider)
                                    }
                                }
                                .padding(12.dp)
                                .testTag("ai_provider_${provider.name.lowercase()}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(provider.displayName, style = MaterialTheme.typography.titleSmall, color = ChalkWhite)
                                    Text(provider.description, style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val badgeText = when {
                                    isActive && connected -> "ACTIVE"
                                    connected && provider.requiresUserKey -> "SAVED"
                                    !provider.requiresUserKey -> "BUILT-IN"
                                    else -> "LOGIN"
                                }
                                val badgeColor = when {
                                    isActive && connected -> TrackOverlayEmerald
                                    connected -> TrackVideoBlue
                                    else -> SilverMuted
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor.copy(alpha = 0.16f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeColor)
                                }
                            }

                            if (isSelected && provider.requiresUserKey) {
                                Spacer(modifier = Modifier.height(10.dp))
                                if (connected && aiCredState.activeProvider == provider && aiCredState.maskedActiveKey.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Connected key: ${aiCredState.maskedActiveKey}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TrackOverlayEmerald
                                        )
                                        TextButton(
                                            onClick = {
                                                credentialStore.clearAiApiKey(provider)
                                                apiKeyDraft = ""
                                                verifyMessage = "Signed out of ${provider.displayName}"
                                            }
                                        ) {
                                            Icon(Icons.Default.Logout, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sign Out", color = ErrorCrimson, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = apiKeyDraft,
                                    onValueChange = {
                                        apiKeyDraft = it
                                        verifyError = null
                                    },
                                    label = { Text("Paste ${provider.shortBadge} API Key") },
                                    placeholder = { Text(provider.keyHint) },
                                    singleLine = true,
                                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showSecret = !showSecret }) {
                                            Icon(
                                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle key visibility",
                                                tint = SilverMuted
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CinemaAmber,
                                        unfocusedBorderColor = BorderHairline,
                                        focusedTextColor = ChalkWhite,
                                        unfocusedTextColor = ChalkWhite
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_provider_key_input")
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.signupUrl)))
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Get Free Key", color = ChalkWhite, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        enabled = !verifying && apiKeyDraft.isNotBlank(),
                                        onClick = {
                                            verifying = true
                                            verifyError = null
                                            verifyMessage = null
                                            scope.launch {
                                                val res = MultiProviderTranscriptionEngine.verifyCredentials(provider, apiKeyDraft)
                                                verifying = false
                                                res.onSuccess { msg ->
                                                    credentialStore.saveAiApiKey(provider, apiKeyDraft)
                                                    apiKeyDraft = ""
                                                    verifyMessage = msg
                                                }.onFailure { err ->
                                                    verifyError = err.localizedMessage ?: "Verification failed."
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("verify_ai_key_button")
                                    ) {
                                        if (verifying) {
                                            CircularProgressIndicator(
                                                color = OnAmber,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Text("Verify & Connect", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (verifyMessage != null) {
                        Text(verifyMessage!!, style = MaterialTheme.typography.labelMedium, color = TrackOverlayEmerald)
                    }
                    if (verifyError != null) {
                        Text(verifyError!!, style = MaterialTheme.typography.labelMedium, color = ErrorCrimson)
                    }
                }
            } else {
                // Tab 2: Downloadable Offline All-Language Speech Models
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = offlineStatusMsg ?: if (isOfflineCapable) {
                                "Download any language pack for 100% offline Auto Captions directly from video clips."
                            } else {
                                "Requires Android 13+ with Google Speech Services."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOfflineCapable) SilverMuted else CinemaAmber,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { offlineManager.refreshInstalledModels() },
                            modifier = Modifier.testTag("refresh_offline_models_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh offline models", tint = CinemaAmber)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    SpeechServicesDiagnosticCard(
                        report = speechDiagnostic,
                        onRunDiagnostic = {
                            scope.launch {
                                speechDiagnostic = speechDiagnostic.copy(isChecking = true)
                                offlineManager.refreshInstalledModels()
                                speechDiagnostic = SpeechServicesDiagnosticEngine.runFullDiagnostic(context)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = langSearch,
                        onValueChange = { langSearch = it },
                        placeholder = { Text("Search Telugu, Hindi, Tamil, Spanish, Arabic…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SilverMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinemaAmber,
                            unfocusedBorderColor = BorderHairline,
                            focusedTextColor = ChalkWhite,
                            unfocusedTextColor = ChalkWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    val filteredPacks = remember(offlinePacks, langSearch) {
                        val q = langSearch.trim().lowercase()
                        if (q.isEmpty()) offlinePacks
                        else offlinePacks.filter {
                            it.spec.displayName.lowercase().contains(q) ||
                                it.spec.nativeLabel.lowercase().contains(q) ||
                                it.spec.bcp47.lowercase().contains(q)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPacks, key = { it.spec.bcp47 }) { pack ->
                            OfflineLanguagePackRow(
                                pack = pack,
                                enabled = isOfflineCapable,
                                onDownload = { offlineManager.downloadLanguagePack(pack.spec) },
                                onUseNow = onSelectLanguageAndTranscribeOffline?.let { cb ->
                                    {
                                        onDismiss()
                                        cb(pack.spec.displayName)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineLanguagePackRow(
    pack: OfflineLanguagePackState,
    enabled: Boolean,
    onDownload: () -> Unit,
    onUseNow: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioSlate)
            .border(
                width = 1.dp,
                color = if (pack.status == OfflinePackStatus.INSTALLED) TrackOverlayEmerald.copy(alpha = 0.45f) else BorderHairline,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("offline_lang_pack_${pack.spec.iso6391}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ObsidianBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pack.spec.iso6391.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaAmber
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${pack.spec.displayName} (${pack.spec.nativeLabel})",
                    style = MaterialTheme.typography.titleSmall,
                    color = ChalkWhite
                )
                Text(
                    text = pack.note ?: when (pack.status) {
                        OfflinePackStatus.INSTALLED -> "Installed • Ready offline"
                        OfflinePackStatus.DOWNLOADING -> "Downloading… ${pack.downloadProgress}%"
                        OfflinePackStatus.ONLINE_ONLY -> "No offline pack on this phone • Device Speech ready"
                        OfflinePackStatus.NOT_SUPPORTED -> "Android 13+ required"
                        else -> "On-device pack (${pack.deviceLocaleTag ?: pack.spec.bcp47})"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (pack.status) {
                        OfflinePackStatus.INSTALLED -> TrackOverlayEmerald
                        OfflinePackStatus.DOWNLOADING -> CinemaAmber
                        OfflinePackStatus.ONLINE_ONLY -> CinemaAmber
                        else -> SilverMuted
                    }
                )
            }

            when (pack.status) {
                OfflinePackStatus.INSTALLED -> {
                    if (onUseNow != null) {
                        Button(
                            onClick = onUseNow,
                            colors = ButtonDefaults.buttonColors(containerColor = TrackOverlayEmerald, contentColor = ObsidianBg),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Transcribe", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Installed", tint = TrackOverlayEmerald)
                    }
                }
                OfflinePackStatus.ONLINE_ONLY -> {
                    if (onUseNow != null) {
                        Button(
                            onClick = onUseNow,
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Transcribe", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        OutlinedButton(
                            enabled = enabled,
                            onClick = onDownload,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Retry", color = ChalkWhite, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                OfflinePackStatus.DOWNLOADING -> {
                    CircularProgressIndicator(
                        color = CinemaAmber,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
                else -> {
                    OutlinedButton(
                        enabled = enabled,
                        onClick = onDownload,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download", color = ChalkWhite, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (pack.status == OfflinePackStatus.DOWNLOADING) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (pack.downloadProgress / 100f).coerceIn(0.05f, 1f) },
                color = CinemaAmber,
                trackColor = ActiveRail,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun openAndroidSpeechSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openGoogleSpeechServicesApp(context: Context) {
    val pkg = "com.google.android.tts"
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val playMarket = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val playWeb = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val isInstalled = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    if (isInstalled && runCatching { context.startActivity(appDetails) }.isSuccess) return
    if (runCatching { context.startActivity(playMarket) }.isSuccess) return
    runCatching { context.startActivity(playWeb) }
}

private tailrec fun Context.findAuthActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findAuthActivity()
    else -> null
}
