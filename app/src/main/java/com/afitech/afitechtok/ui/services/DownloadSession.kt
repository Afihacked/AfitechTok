package com.afitech.afitechtok.ui.services

object DownloadSession {

    @Volatile
    var isDownloading: Boolean = false

    @Volatile
    var lastVideoUrl: String? = null

    @Volatile
    var lastFormat: String? = null

    @Volatile
    var lastDownloadFinished: Boolean = false

    @Volatile var lastProgress: Int = 0   // 🔥 INI


}

