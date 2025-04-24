package com.afitech.tikdownloader.data.model

import android.net.Uri

data class DownloadedMedia(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val date: Long,
    val size: Long // Pastikan ini ada!
)
