package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAtMs DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface CustomFontDao {
    @Query("SELECT * FROM custom_fonts ORDER BY createdAtMs DESC")
    fun observeCustomFonts(): Flow<List<CustomFontEntity>>

    @Query("SELECT * FROM custom_fonts")
    suspend fun getAllCustomFonts(): List<CustomFontEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFont(font: CustomFontEntity)

    @Query("DELETE FROM custom_fonts WHERE id = :id")
    suspend fun deleteFontById(id: String)
}

@Dao
interface RenderJobDao {
    @Query("SELECT * FROM render_jobs ORDER BY createdAtMs DESC")
    fun observeAllJobs(): Flow<List<RenderJobEntity>>

    @Query("SELECT * FROM render_jobs WHERE statusName = 'QUEUED' ORDER BY createdAtMs ASC LIMIT 1")
    suspend fun getNextQueuedJob(): RenderJobEntity?

    @Query("SELECT * FROM render_jobs WHERE id = :id LIMIT 1")
    suspend fun getJobById(id: String): RenderJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(job: RenderJobEntity)

    @Query("UPDATE render_jobs SET progress = :progress, stageMessage = :stage, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, stage: String, nowMs: Long)

    @Query(
        "UPDATE render_jobs SET statusName = :statusName, progress = :progress, stageMessage = :stage, " +
            "outputUri = :outputUri, outputFileName = :outputFileName, fileSizeBytes = :fileSizeBytes, " +
            "errorMessage = :errorMessage, updatedAtMs = :nowMs WHERE id = :id"
    )
    suspend fun updateJobResult(
        id: String,
        statusName: String,
        progress: Float,
        stage: String,
        outputUri: String?,
        outputFileName: String?,
        fileSizeBytes: Long,
        errorMessage: String?,
        nowMs: Long
    )

    @Query("UPDATE render_jobs SET statusName = 'QUEUED', progress = 0.0, stageMessage = 'Queued for background render', errorMessage = NULL, updatedAtMs = :nowMs WHERE statusName = 'RENDERING'")
    suspend fun resetStaleRenderingJobs(nowMs: Long)

    @Query("DELETE FROM render_jobs WHERE id = :id")
    suspend fun deleteJobById(id: String)

    @Query("DELETE FROM render_jobs WHERE statusName IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinishedJobs()
}

