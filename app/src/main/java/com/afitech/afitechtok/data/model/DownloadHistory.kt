package com.afitech.afitechtok.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val savedUri: String,        // URI yang dikembalikan MediaStore (lebih andal daripada path)
    val originalUrl: String?,    // URL sumber (opsional)
    val mimeType: String?,       // ex: "video/mp4", "audio/mpeg"
    val ext: String?,            // ex: ".mp4", ".m4a", ".mp3", ".jpg"
    val fileType: String,        // "Video", "Audio", atau "Image"
    val fileSize: Long?,         // ukuran file dalam byte (opsional)
    val durationMs: Long?,       // durasi media jika tersedia (video/audio) dalam ms
    val isRemuxed: Boolean = false, // true kalau kita sudah remux faststart
    val downloadDate: Long,
    val source: String           // Sumber: tiktok, youtube, whatsapp, dll
)
