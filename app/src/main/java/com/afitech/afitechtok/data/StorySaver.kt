package com.afitech.afitechtok.data

import android.content.Context
import android.net.Uri
import com.afitech.afitechtok.data.database.DownloadHistoryDao
import com.afitech.afitechtok.data.model.DownloadHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object StorySaver {

    lateinit var downloadHistoryDao: DownloadHistoryDao

    fun saveToGallery(
        context: Context,
        sourceUri: Uri,
        originalFileName: String,
        mimeType: String,
        originalUrl: String? = null,
        source: String = "whatsapp",
        onProgressUpdate: (Int) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw Exception("Gagal membuka input stream")

                val timeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                    .format(Date())

                val baseName = originalFileName.substringBeforeLast(".")
                    .replace(Regex("[^a-zA-Z0-9\\-_ ]"), "_")
                    .replace(Regex("_+"), "_")
                    .trim('_')
                    .take(100)

                val ext = originalFileName.substringAfterLast(".", "")
                    .ifEmpty {
                        when {
                            mimeType.startsWith("video") -> "mp4"
                            mimeType.startsWith("image") -> "jpg"
                            mimeType.startsWith("audio") -> "mp3"
                            else -> "dat"
                        }
                    }

                val finalFileName = "${baseName}_$timeStamp.$ext".lowercase()

                val uniqueFileName = Downloader.generateUniqueFileName(
                    context = context,
                    fileName = finalFileName,
                    mimeType = mimeType,
                    source = source
                )

                val savedUri = Downloader.saveToMediaStoreFromStream(
                    context = context,
                    inputStream = inputStream,
                    fileName = uniqueFileName,
                    mimeType = mimeType,
                    fileSize = null,
                    onProgressUpdate = { progress, _, _ ->
                        onProgressUpdate(progress)
                    },
                    source = source
                )

                inputStream.close()

                if (savedUri != null) {
                    val fileType = when {
                        mimeType.startsWith("video") -> "Video"
                        mimeType.startsWith("image") -> "Image"
                        mimeType.startsWith("audio") -> "Audio"
                        else -> "Other"
                    }

                    val history = DownloadHistory(
                        fileName = uniqueFileName,
                        savedUri = savedUri.toString(),
                        originalUrl = originalUrl,
                        mimeType = mimeType,
                        ext = ".$ext",
                        fileType = fileType,
                        fileSize = null,
                        durationMs = null,
                        isRemuxed = false,
                        downloadDate = System.currentTimeMillis(),
                        source = source
                    )

                    downloadHistoryDao.insertDownload(history)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
