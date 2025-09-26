package com.afitech.sosmedtoolkit.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.afitech.sosmedtoolkit.data.model.DownloadHistory

@Database(entities = [DownloadHistory::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "download_history_database"

                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Tambahkan kolom baru "source" dengan default 'tiktok'
                database.execSQL("ALTER TABLE download_history ADD COLUMN source TEXT NOT NULL DEFAULT 'tiktok'")
            }
        }
    }
}
