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
