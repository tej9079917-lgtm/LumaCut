package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val aspectRatioName: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val durationMs: Long,
    val coverUri: String?,
    val timelineJson: String
)

@Entity(tableName = "custom_fonts")
data class CustomFontEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val filePath: String,
    val createdAtMs: Long
)

enum class RenderJobStatus {
    QUEUED,
    RENDERING,
    COMPLETED,
    FAILED,
    CANCELLED;

    companion object {
        fun fromStored(name: String?): RenderJobStatus =
            entries.firstOrNull { it.name == name } ?: QUEUED
    }
}

@Entity(tableName = "render_jobs")
data class RenderJobEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectTitle: String,
    val aspectRatioName: String,
    val resolutionName: String,
    val frameRate: Int,
    val statusName: String,
    val progress: Float,
    val stageMessage: String,
    val outputUri: String?,
    val outputFileName: String?,
    val fileSizeBytes: Long,
    val errorMessage: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    val status: RenderJobStatus get() = RenderJobStatus.fromStored(statusName)
}

