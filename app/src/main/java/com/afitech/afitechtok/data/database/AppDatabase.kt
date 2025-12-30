package com.afitech.afitechtok.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.afitech.afitechtok.data.model.DownloadHistory

@Database(entities = [DownloadHistory::class], version = 3, exportSchema = false)
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
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Migrasi 1 -> 2 (sudah ada di projectmu): menambahkan kolom "source"
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE download_history ADD COLUMN source TEXT NOT NULL DEFAULT 'tiktok'"
                )
            }
        }

        /**
         * Migrasi 2 -> 3: menambahkan kolom-kolom metadata baru sesuai model yang direkomendasikan:
         * - savedUri (TEXT)
         * - originalUrl (TEXT)
         * - mimeType (TEXT)
         * - ext (TEXT)
         * - fileSize (INTEGER)
         * - durationMs (INTEGER)
         * - isRemuxed (INTEGER NOT NULL DEFAULT 0)
         *
         * Kolom-kolom baru selain isRemuxed dibuat nullable sehingga migrasi tidak memaksa nilai default.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Menambahkan kolom baru — nullable kecuali isRemuxed
                database.execSQL("ALTER TABLE download_history ADD COLUMN savedUri TEXT")
                database.execSQL("ALTER TABLE download_history ADD COLUMN originalUrl TEXT")
                database.execSQL("ALTER TABLE download_history ADD COLUMN mimeType TEXT")
                database.execSQL("ALTER TABLE download_history ADD COLUMN ext TEXT")
                database.execSQL("ALTER TABLE download_history ADD COLUMN fileSize INTEGER")
                database.execSQL("ALTER TABLE download_history ADD COLUMN durationMs INTEGER")
                database.execSQL("ALTER TABLE download_history ADD COLUMN isRemuxed INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
