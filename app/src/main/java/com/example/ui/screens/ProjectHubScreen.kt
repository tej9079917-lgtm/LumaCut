package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ProjectSummary
import com.example.media.FontResolver
import com.example.model.AspectRatio
import com.example.model.StudioFont
import com.example.model.formatShortDuration
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
import com.example.viewmodel.ProjectsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectHubScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (String) -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val fonts by viewModel.fonts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFont(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 164.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
        ) {
            // Studio Hero Header Banner
            item(span = { GridItemSpan(maxLineSpan) }) {
                StudioHeroBanner(
                    projectCount = projects.size,
                    customFontCount = fonts.count { it.isCustom },
                    onNewProject = { showNewProjectDialog = true },
                    onOpenFontStudio = { showFontSheet = true }
                )
            }

            // Section Header
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recent_projects),
                        style = MaterialTheme.typography.titleLarge,
                        color = ChalkWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Offline Local Storage",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted
                    )
                }
            }

            if (projects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyProjectsCard(onNewProject = { showNewProjectDialog = true })
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { onOpenProject(project.id) },
                        onRename = { renameTarget = project },
                        onDuplicate = { viewModel.duplicateProject(project.id) },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { title, ratio ->
                showNewProjectDialog = false
                viewModel.createProject(title, ratio, onOpenProject)
            }
        )
    }

    renameTarget?.let { target ->
        RenameProjectDialog(
            initialTitle = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                viewModel.renameProject(target.id, newTitle)
                renameTarget = null
            }
        )
    }

    if (showFontSheet) {
        FontManagerModalSheet(
            fonts = fonts,
            onImportFont = {
                fontPickerLauncher.launch(
                    arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")
                )
            },
            onDeleteFont = { viewModel.deleteFont(it) },
            onDismiss = { showFontSheet = false }
        )
    }
}

@Composable
private fun StudioHeroBanner(
    projectCount: Int,
    customFontCount: Int,
    onNewProject: () -> Unit,
    onOpenFontStudio: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(212.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(18.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_hero_1795703943154),
            contentDescription = stringResource(R.string.cd_studio_hero),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBg.copy(alpha = 0.35f),
                            ObsidianBg.copy(alpha = 0.72f),
                            ObsidianBg.copy(alpha = 0.96f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = ChalkWhite
                    )
                    Text(
                        text = stringResource(R.string.studio_tagline),
                        style = MaterialTheme.typography.labelMedium,
                        color = CinemaAmber
                    )
                }

                FilledTonalButton(
                    onClick = onOpenFontStudio,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = InspectorCharcoal.copy(alpha = 0.85f),
                        contentColor = ChalkWhite
                    ),
                    modifier = Modifier.testTag("hub_font_studio_button")
                ) {
                    Icon(Icons.Default.FontDownload, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (customFontCount > 0) "Fonts (+$customFontCount)" else "Fonts",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Column {
                Text(
                    text = if (projectCount == 0) "Multilayer cuts, overlays, transitions & color grading"
                    else "$projectCount saved project${if (projectCount > 1) "s" else ""} ready offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNewProject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CinemaAmber,
                        contentColor = OnAmber
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("new_project_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.new_project), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectsCard(onNewProject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioSlate)
            .border(1.dp, BorderHairline, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AmberContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideoSettings,
                contentDescription = null,
                tint = CinemaAmber,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.empty_projects_title),
            style = MaterialTheme.typography.titleLarge,
            color = ChalkWhite
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_projects_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = SilverMuted
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onNewProject) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create First Edit")
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(project.updatedAtMs) {
        SimpleDateFormat("MMM d", Locale.US).format(Date(project.updatedAtMs))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StudioSlate)
            .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .testTag("project_card_${project.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(InspectorCharcoal),
            contentAlignment = Alignment.Center
        ) {
            if (project.coverUri != null) {
                AsyncImage(
                    model = project.coverUri,
                    contentDescription = project.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = SilverMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(34.dp)
                )
            }

            // Aspect Ratio Pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianBg.copy(alpha = 0.82f))
                    .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = project.aspectRatio.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = CinemaAmber
                )
            }

            // Duration Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianBg.copy(alpha = 0.82f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatShortDuration(project.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkWhite
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ChalkWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Edited $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = SilverMuted
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Project options",
                        tint = SilverMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = InspectorCharcoal
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = ChalkWhite) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = SilverMuted) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate", color = ChalkWhite) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = SilverMuted) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = ErrorCrimson) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorCrimson) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, AspectRatio) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedRatio by remember { mutableStateOf(AspectRatio.PORTRAIT_9_16) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InspectorCharcoal,
        titleContentColor = ChalkWhite,
        textContentColor = ChalkWhite,
        title = {
            Text("New Cinema Project", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("Summer Reel Cut") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CinemaAmber,
                        unfocusedBorderColor = BorderHairline,
                        focusedLabelColor = CinemaAmber,
                        focusedTextColor = ChalkWhite,
                        unfocusedTextColor = ChalkWhite
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_project_title_input")
                )

                Text(
                    text = "Canvas Aspect Ratio",
                    style = MaterialTheme.typography.labelLarge,
                    color = SilverMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AspectRatio.entries.forEach { ratio ->
                        val picked = selectedRatio == ratio
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (picked) AmberContainer else ActiveRail)
                                .border(
                                    width = if (picked) 2.dp else 1.dp,
                                    color = if (picked) CinemaAmber else BorderHairline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedRatio = ratio }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                                .testTag("dialog_ratio_${ratio.name.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .width(
                                        when (ratio) {
                                            AspectRatio.PORTRAIT_9_16 -> 16.dp
                                            AspectRatio.LANDSCAPE_16_9 -> 34.dp
                                            AspectRatio.SQUARE_1_1 -> 24.dp
                                        }
                                    )
                                    .border(
                                        1.5.dp,
                                        if (picked) CinemaAmber else SilverMuted,
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ratio.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (picked) CinemaAmber else ChalkWhite
                            )
                            Text(
                                text = ratio.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.ifBlank { "Cinema Edit" }, selectedRatio) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaAmber,
                    contentColor = OnAmber
                ),
                modifier = Modifier.testTag("confirm_create_project_button")
            ) {
                Text("Start Editing")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SilverMuted)
            }
        }
    )
}

@Composable
private fun RenameProjectDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InspectorCharcoal,
        title = { Text("Rename Project", color = ChalkWhite) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CinemaAmber,
                    focusedTextColor = ChalkWhite,
                    unfocusedTextColor = ChalkWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title) },
                colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SilverMuted) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontManagerModalSheet(
    fonts: List<StudioFont>,
    onImportFont: () -> Unit,
    onDeleteFont: (StudioFont) -> Unit,
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Studio Font Library", style = MaterialTheme.typography.headlineSmall, color = ChalkWhite)
                    Text("Import custom .ttf or .otf typefaces for title layers", style = MaterialTheme.typography.bodySmall, color = SilverMuted)
                }
                Button(
                    onClick = onImportFont,
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                    modifier = Modifier.testTag("sheet_import_font_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import")
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fonts, key = { it.id }) { font ->
                    val family = remember(font.id, fonts) { FontResolver.resolveComposeFont(font.id, fonts) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ActiveRail)
                            .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cinema Title Preview",
                                fontFamily = family,
                                style = MaterialTheme.typography.titleMedium,
                                color = ChalkWhite
                            )
                            Text(
                                text = "${font.displayName} • ${if (font.isCustom) "Imported Custom Font" else "Bundled Studio"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (font.isCustom) CinemaAmber else SilverMuted
                            )
                        }
                        if (font.isCustom) {
                            IconButton(onClick = { onDeleteFont(font) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete font", tint = ErrorCrimson)
                            }
                        }
                    }
                }
            }
        }
    }
}
