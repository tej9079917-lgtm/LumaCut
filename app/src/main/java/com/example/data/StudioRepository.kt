package com.example.data

import android.net.Uri
import com.example.model.AspectRatio
import com.example.model.StudioFont
import com.example.model.TimelineDocument
import com.example.ui.theme.BundledStudioFonts
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

data class ProjectSummary(
    val id: String,
    val title: String,
    val aspectRatio: AspectRatio,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val durationMs: Long,
    val coverUri: String?
)

data class LoadedProject(
    val summary: ProjectSummary,
    val timeline: TimelineDocument
)

class StudioRepository(
    private val projectDao: ProjectDao,
    private val customFontDao: CustomFontDao,
    val mediaStorage: LocalMediaStorage
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val timelineAdapter = moshi.adapter(TimelineDocument::class.java)

    val projects: Flow<List<ProjectSummary>> = projectDao.observeAllProjects().map { entities ->
        entities.map { it.toSummary() }
    }

    val allFonts: Flow<List<StudioFont>> = customFontDao.observeCustomFonts().map { customEntities ->
        val validCustom = customEntities
            .filter { File(it.filePath).exists() }
            .map {
                StudioFont(
                    id = it.id,
                    displayName = it.displayName,
                    isCustom = true,
                    filePath = it.filePath
                )
            }
        val bundled = BundledStudioFonts.map {
            StudioFont(
                id = it.id,
                displayName = it.displayName,
                isCustom = false,
                resId = it.resId
            )
        }
        validCustom + bundled
    }

    suspend fun createProject(title: String, aspectRatio: AspectRatio): ProjectSummary {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = ProjectEntity(
            id = id,
            title = title.trim().ifBlank { "Untitled Edit" },
            aspectRatioName = aspectRatio.name,
            createdAtMs = now,
            updatedAtMs = now,
            durationMs = 0L,
            coverUri = null,
            timelineJson = timelineAdapter.toJson(TimelineDocument())
        )
        projectDao.upsertProject(entity)
        return entity.toSummary()
    }

    suspend fun loadProject(id: String): LoadedProject? {
        val entity = projectDao.getProjectById(id) ?: return null
        val timeline = runCatching {
            timelineAdapter.fromJson(entity.timelineJson)
        }.getOrNull() ?: TimelineDocument()
        return LoadedProject(entity.toSummary(), timeline)
    }

    suspend fun saveProjectState(
        id: String,
        title: String,
        aspectRatio: AspectRatio,
        timeline: TimelineDocument
    ) {
        val existing = projectDao.getProjectById(id)
        val now = System.currentTimeMillis()
        val cover = timeline.mainClips.firstOrNull()?.let { it.thumbnailPath ?: it.uri }
            ?: timeline.overlays.firstOrNull { it.imageUri != null }?.imageUri
        val entity = ProjectEntity(
            id = id,
            title = title.trim().ifBlank { "Untitled Edit" },
            aspectRatioName = aspectRatio.name,
            createdAtMs = existing?.createdAtMs ?: now,
            updatedAtMs = now,
            durationMs = timeline.totalDurationMs,
            coverUri = cover,
            timelineJson = timelineAdapter.toJson(timeline)
        )
        projectDao.upsertProject(entity)
    }

    suspend fun renameProject(id: String, newTitle: String) {
        val existing = projectDao.getProjectById(id) ?: return
        val clean = newTitle.trim().ifBlank { existing.title }
        projectDao.updateProject(
            existing.copy(title = clean, updatedAtMs = System.currentTimeMillis())
        )
    }

    suspend fun duplicateProject(id: String): ProjectSummary? {
        val existing = projectDao.getProjectById(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = existing.copy(
            id = UUID.randomUUID().toString(),
            title = "${existing.title} Copy",
            createdAtMs = now,
            updatedAtMs = now
        )
        projectDao.upsertProject(copy)
        return copy.toSummary()
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
    }

    suspend fun importCustomFont(uri: Uri): Result<StudioFont> {
        val metaResult = mediaStorage.importFontFromUri(uri)
        return metaResult.map { meta ->
            val entity = CustomFontEntity(
                id = meta.id,
                displayName = meta.displayName,
                filePath = meta.filePath,
                createdAtMs = System.currentTimeMillis()
            )
            customFontDao.insertFont(entity)
            StudioFont(
                id = meta.id,
                displayName = meta.displayName,
                isCustom = true,
                filePath = meta.filePath
            )
        }
    }

    suspend fun deleteCustomFont(font: StudioFont) {
        if (!font.isCustom) return
        customFontDao.deleteFontById(font.id)
        mediaStorage.deleteLocalFileIfInternal(font.filePath)
    }

    private fun ProjectEntity.toSummary(): ProjectSummary = ProjectSummary(
        id = id,
        title = title,
        aspectRatio = AspectRatio.fromNameOrDefault(aspectRatioName),
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        durationMs = durationMs,
        coverUri = coverUri
    )
}
