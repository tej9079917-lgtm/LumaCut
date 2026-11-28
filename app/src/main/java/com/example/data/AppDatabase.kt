package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, CustomFontEntity::class, RenderJobEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun customFontDao(): CustomFontDao
    abstract fun renderJobDao(): RenderJobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `render_jobs` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `projectTitle` TEXT NOT NULL,
                        `aspectRatioName` TEXT NOT NULL,
                        `resolutionName` TEXT NOT NULL,
                        `frameRate` INTEGER NOT NULL,
                        `statusName` TEXT NOT NULL,
                        `progress` REAL NOT NULL,
                        `stageMessage` TEXT NOT NULL,
                        `outputUri` TEXT,
                        `outputFileName` TEXT,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lumacut_studio.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
