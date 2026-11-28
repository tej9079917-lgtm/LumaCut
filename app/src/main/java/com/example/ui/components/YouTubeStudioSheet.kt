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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.LocalMediaStorage
import com.example.media.YouTubeImportedAsset
import com.example.media.YouTubePrivacyStatus
import com.example.media.YouTubeService
import com.example.media.YouTubeVideoItem
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeStudioBottomSheet(
    initialTab: Int = 0, // 0 = Import, 1 = Export/Upload
    exportVideoUri: String? = null,
    defaultExportTitle: String = "",
    onImportedAsset: (YouTubeImportedAsset) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val credentialStore = remember(context) { SecureCredentialStore.getInstance(context) }
    val mediaStorage = remember(context) { LocalMediaStorage(context) }
    val ytAccount by credentialStore.youtubeState.collectAsStateWithLifecycle()

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var showAccountPanel by remember { mutableStateOf(false) }
    var oauthTokenDraft by remember { mutableStateOf("") }
    var dataApiKeyDraft by remember { mutableStateOf("") }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    // Import tab state
    var urlInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var videos by remember { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }
    var listLoading by remember { mutableStateOf(false) }
    var importLoading by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    // Export tab state
    var uploadTitle by remember(defaultExportTitle) { mutableStateOf(defaultExportTitle.ifBlank { "LumaCut Edit" }) }
    var uploadDesc by remember { mutableStateOf("Edited with LumaCut Pro Studio") }
    var uploadTags by remember { mutableStateOf("shorts, edit, lumacut") }
    var uploadPrivacy by remember { mutableStateOf(YouTubePrivacyStatus.UNLISTED) }
    var uploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var uploadStage by remember { mutableStateOf("") }
    var uploadedLiveUrl by remember { mutableStateOf<String?>(null) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ytAccount.isSignedIn) {
        if (ytAccount.isSignedIn && videos.isEmpty()) {
            val token = credentialStore.getYouTubeOAuthToken()
            if (!token.isNullOrBlank()) {
                listLoading = true
                YouTubeService.fetchMyChannelVideos(token).onSuccess { videos = it }
                listLoading = false
            }
        }
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
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ErrorCrimson.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("YouTube Studio Bridge", style = MaterialTheme.typography.titleMedium, color = ChalkWhite)
                    Text(
                        text = if (ytAccount.isSignedIn) "Signed in as ${ytAccount.channelTitle}" else "Import public content or upload MP4s",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ytAccount.isSignedIn) TrackOverlayEmerald else SilverMuted
                    )
                }
                TextButton(
                    onClick = { showAccountPanel = !showAccountPanel },
                    modifier = Modifier.testTag("youtube_account_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = if (ytAccount.isSignedIn) TrackOverlayEmerald else CinemaAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (ytAccount.isSignedIn) "Account" else "Sign In",
                        color = if (ytAccount.isSignedIn) TrackOverlayEmerald else CinemaAmber,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close YouTube Studio", tint = SilverMuted)
                }
            }

            if (showAccountPanel) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioSlate)
                        .border(1.dp, BorderHairline, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (ytAccount.isSignedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (ytAccount.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ytAccount.avatarUrl,
                                    contentDescription = ytAccount.channelTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(38.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ytAccount.channelTitle, style = MaterialTheme.typography.titleSmall, color = ChalkWhite)
                                Text(
                                    text = listOfNotNull(
                                        ytAccount.channelHandle.takeIf { it.isNotBlank() },
                                        ytAccount.subscriberCount.takeIf { it.isNotBlank() }?.let { "$it subscribers" }
                                    ).joinToString(" • ").ifBlank { "Connected via YouTube Data API v3" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SilverMuted
                                )
                            }
                            TextButton(onClick = {
                                credentialStore.signOutYouTube()
                                videos = emptyList()
                            }) {
                                Icon(Icons.Default.Logout, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sign Out", color = ErrorCrimson, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        Text(
                            text = "Sign in with a Google OAuth 2.0 Access Token (scopes: youtube.readonly, youtube.upload) to browse your channel uploads & publish directly.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                        OutlinedTextField(
                            value = oauthTokenDraft,
                            onValueChange = {
                                oauthTokenDraft = it
                                authError = null
                            },
                            label = { Text("YouTube OAuth 2.0 Access Token") },
                            placeholder = { Text("ya29.a0...") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("youtube_oauth_input")
                        )
                        OutlinedTextField(
                            value = dataApiKeyDraft,
                            onValueChange = { dataApiKeyDraft = it },
                            label = { Text("Optional YouTube Data API Key (for public search)") },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.google.com/oauthplayground/"))
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Get Token", color = ChalkWhite, style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                enabled = !authLoading && (oauthTokenDraft.isNotBlank() || dataApiKeyDraft.isNotBlank()),
                                onClick = {
                                    authLoading = true
                                    authError = null
                                    scope.launch {
                                        if (dataApiKeyDraft.isNotBlank()) {
                                            credentialStore.saveYouTubeDataApiKey(dataApiKeyDraft)
                                        }
                                        if (oauthTokenDraft.isNotBlank()) {
                                            val res = YouTubeService.fetchChannelProfile(oauthTokenDraft)
                                            res.onSuccess { profile ->
                                                credentialStore.saveYouTubeSession(
                                                    accessToken = oauthTokenDraft,
                                                    channelId = profile.channelId,
                                                    channelTitle = profile.title,
                                                    channelHandle = profile.handle,
                                                    avatarUrl = profile.avatarUrl,
                                                    subscriberCount = profile.subscriberCount
                                                )
                                                oauthTokenDraft = ""
                                                showAccountPanel = false
                                            }.onFailure { err ->
                                                authError = err.localizedMessage ?: "YouTube sign-in failed."
                                            }
                                        } else {
                                            dataApiKeyDraft = ""
                                            showAccountPanel = false
                                        }
                                        authLoading = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                                modifier = Modifier.weight(1f).height(42.dp).testTag("youtube_connect_button")
                            ) {
                                if (authLoading) {
                                    CircularProgressIndicator(color = OnAmber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                } else {
                                    Text("Connect", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        if (authError != null) {
                            Text(authError!!, style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                    modifier = Modifier.testTag("tab_youtube_import"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Import to Timeline", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("tab_youtube_export"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Upload to YouTube", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedTab == 0) {
                    Text(
                        text = "Paste any public YouTube URL (video/Shorts — pulls HD cover + public CC captions) or any direct public https:// .mp4/.webm video link to pull straight onto your timeline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                                importError = null
                            },
                            placeholder = { Text("https://youtu.be/... or https://.../video.mp4") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = CinemaAmber) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.weight(1f).testTag("youtube_url_input")
                        )
                        Button(
                            enabled = !importLoading && urlInput.isNotBlank(),
                            onClick = {
                                importLoading = true
                                importError = null
                                importStatus = "Resolving public link…"
                                scope.launch {
                                    val res = YouTubeService.importPublicUrlToProject(
                                        context = context,
                                        mediaStorage = mediaStorage,
                                        rawUrlOrId = urlInput,
                                        onProgress = { importStatus = it }
                                    )
                                    importLoading = false
                                    res.onSuccess { asset ->
                                        importStatus = null
                                        onImportedAsset(asset)
                                        onDismiss()
                                    }.onFailure { err ->
                                        importStatus = null
                                        importError = err.localizedMessage ?: "Import failed."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                            modifier = Modifier.height(54.dp).testTag("youtube_import_url_button")
                        ) {
                            if (importLoading) {
                                CircularProgressIndicator(color = OnAmber, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Text("Pull", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    if (importStatus != null) {
                        Text(importStatus!!, style = MaterialTheme.typography.labelMedium, color = CinemaAmber)
                    }
                    if (importError != null) {
                        Text(importError!!, style = MaterialTheme.typography.labelMedium, color = ErrorCrimson)
                    }

                    // Public YouTube Search / Channel Videos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search public YouTube videos…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SilverMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            enabled = !listLoading && searchQuery.isNotBlank(),
                            onClick = {
                                listLoading = true
                                importError = null
                                scope.launch {
                                    val res = YouTubeService.searchPublicVideos(
                                        query = searchQuery,
                                        accessToken = credentialStore.getYouTubeOAuthToken(),
                                        dataApiKey = credentialStore.getYouTubeDataApiKey()
                                    )
                                    listLoading = false
                                    res.onSuccess { videos = it }
                                        .onFailure { importError = it.localizedMessage }
                                }
                            },
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text("Search", color = ChalkWhite, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    if (listLoading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CinemaAmber)
                        }
                    } else if (videos.isNotEmpty()) {
                        Text(
                            text = if (searchQuery.isBlank() && ytAccount.isSignedIn) "Your YouTube Channel Uploads" else "YouTube Results",
                            style = MaterialTheme.typography.labelLarge,
                            color = SilverMuted
                        )
                        videos.forEach { vid ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(StudioSlate)
                                    .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                                    .clickable(enabled = !importLoading) {
                                        importLoading = true
                                        importError = null
                                        importStatus = "Pulling ${vid.title}…"
                                        scope.launch {
                                            val res = YouTubeService.importPublicUrlToProject(
                                                context = context,
                                                mediaStorage = mediaStorage,
                                                rawUrlOrId = vid.videoId,
                                                onProgress = { importStatus = it }
                                            )
                                            importLoading = false
                                            res.onSuccess { asset ->
                                                importStatus = null
                                                onImportedAsset(asset)
                                                onDismiss()
                                            }.onFailure { err ->
                                                importStatus = null
                                                importError = err.localizedMessage
                                            }
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = vid.thumbnailUrl,
                                    contentDescription = vid.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(84.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ObsidianBg)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(vid.title, style = MaterialTheme.typography.labelLarge, color = ChalkWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(vid.channelTitle, style = MaterialTheme.typography.labelSmall, color = SilverMuted, maxLines = 1)
                                }
                                Icon(Icons.Default.Download, contentDescription = "Import", tint = CinemaAmber)
                            }
                        }
                    }
                } else {
                    // Tab 2: Upload / Export to YouTube
                    if (exportVideoUri.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(StudioSlate)
                                .border(1.dp, BorderHairline, RoundedCornerShape(12.dp))
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(30.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Render an MP4 First", style = MaterialTheme.typography.titleSmall, color = ChalkWhite)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Export your project or queue it in the Background Render Queue, then tap 'YouTube' on the completed MP4 to publish directly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverMuted
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = uploadTitle,
                            onValueChange = { uploadTitle = it },
                            label = { Text("YouTube Video Title") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = uploadDesc,
                            onValueChange = { uploadDesc = it },
                            label = { Text("Description") },
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = uploadTags,
                            onValueChange = { uploadTags = it },
                            label = { Text("Tags (comma separated)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAmber,
                                unfocusedBorderColor = BorderHairline,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            YouTubePrivacyStatus.entries.forEach { priv ->
                                val picked = uploadPrivacy == priv
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (picked) AmberContainer else ActiveRail)
                                        .border(1.dp, if (picked) CinemaAmber else BorderHairline, RoundedCornerShape(8.dp))
                                        .clickable { uploadPrivacy = priv }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = priv.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (picked) CinemaAmber else ChalkWhite
                                    )
                                }
                            }
                        }

                        if (uploading) {
                            LinearProgressIndicator(
                                progress = { uploadProgress.coerceIn(0.05f, 1f) },
                                color = CinemaAmber,
                                trackColor = ActiveRail,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Text(uploadStage, style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                        }

                        if (uploadedLiveUrl != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TrackOverlayEmerald.copy(alpha = 0.14f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TrackOverlayEmerald)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Published to YouTube!", style = MaterialTheme.typography.labelLarge, color = TrackOverlayEmerald)
                                    Text(uploadedLiveUrl!!, style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
                                }
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uploadedLiveUrl!!)))
                                    }
                                }) {
                                    Text("Open", color = CinemaAmber)
                                }
                            }
                        }

                        if (uploadError != null) {
                            Text(uploadError!!, style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                        }

                        Button(
                            enabled = !uploading,
                            onClick = {
                                val token = credentialStore.getYouTubeOAuthToken()
                                if (token.isNullOrBlank()) {
                                    showAccountPanel = true
                                    uploadError = "Sign in with YouTube OAuth above for direct API upload, or tap 'Open in YouTube App' below."
                                    return@Button
                                }
                                uploading = true
                                uploadError = null
                                uploadedLiveUrl = null
                                scope.launch {
                                    val res = YouTubeService.uploadVideoToYouTube(
                                        context = context,
                                        videoUriString = exportVideoUri,
                                        title = uploadTitle,
                                        description = uploadDesc,
                                        tagsCsv = uploadTags,
                                        privacy = uploadPrivacy,
                                        accessToken = token,
                                        onProgress = { p, s ->
                                            uploadProgress = p
                                            uploadStage = s
                                        }
                                    )
                                    uploading = false
                                    res.onSuccess { uploadedLiveUrl = it }
                                        .onFailure { uploadError = it.localizedMessage }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorCrimson, contentColor = ChalkWhite),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("youtube_direct_upload_button")
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Direct Upload to YouTube Channel", style = MaterialTheme.typography.titleSmall)
                        }

                        OutlinedButton(
                            enabled = !uploading,
                            onClick = {
                                YouTubeService.launchYouTubeAppUpload(context, exportVideoUri, uploadTitle)
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("youtube_app_share_button")
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1-Tap Upload via YouTube App", color = ChalkWhite, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
