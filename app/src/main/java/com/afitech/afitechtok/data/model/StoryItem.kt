package com.afitech.afitechtok.data.model

import android.net.Uri

data class StoryItem(
    val uri: Uri,
    val type: String // "image" atau "video"
)
