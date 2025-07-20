package com.afitech.sosmedtoolkit.data

import android.content.Context
import android.net.Uri
import com.afitech.sosmedtoolkit.data.database.DownloadHistoryDao
import com.afitech.sosmedtoolkit.data.model.DownloadHistory
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
        onProgressUpdate: (Int) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw Exception("Gagal membuka input stream dari Uri")

                val timeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

                val baseNameWithoutExt = originalFileName.substringBeforeLast(".")

                val sanitizedBaseName = baseNameWithoutExt
                    .replace(Regex("[^a-zA-Z0-9\\-_ ]"), "_")
                    .replace(Regex("_+"), "_")
                    .trim('_')
                    .take(100)

                val extFromFileName = originalFileName.substringAfterLast(".", "")
                val extension = if (extFromFileName.isNotEmpty()) extFromFileName else when {
                    mimeType.startsWith("video") -> "mp4"
                    mimeType.startsWith("image") -> "jpg"
                    mimeType.startsWith("audio") -> "mp3"
                    else -> "dat"
                }

                val fileNameWithDate = "$sanitizedBaseName$timeStamp.$extension".lowercase()

                val uniqueFileName = Downloader.generateUniqueFileName(
                    context = context,
                    fileName = fileNameWithDate,
                    mimeType = mimeType,
                    source = "whatsapp"
                )

                val savedUri = Downloader.saveToMediaStoreFromStream(
                    context = context,
                    inputStream = inputStream,
                    fileName = uniqueFileName,
                    mimeType = mimeType,
                    fileSize = -1,
                    onProgressUpdate = { progress, _, _ -> onProgressUpdate(progress) }, // ✅ Wrap ke versi lama
                    source = "whatsapp"
                )

                inputStream.close()

                if (savedUri != null) {
                    val fileType = when {
                        mimeType.startsWith("video") -> "Video"
                        mimeType.startsWith("image") -> "Image"
                        else -> "Other"
                    }

                    val downloadHistory = DownloadHistory(
                        fileName = uniqueFileName,
                        filePath = savedUri.toString(),
                        fileType = fileType,
                        downloadDate = System.currentTimeMillis()
                    )
                    downloadHistoryDao.insertDownload(downloadHistory)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
