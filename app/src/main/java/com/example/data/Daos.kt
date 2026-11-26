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
