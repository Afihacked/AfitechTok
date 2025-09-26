package com.afitech.afitechtok.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val fileType: String, // "Video", "Audio", atau "Image"
    val downloadDate: Long,
    val source: String // Sumber: tiktok, youtube, whatsapp, dll
)
