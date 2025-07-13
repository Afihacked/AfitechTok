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

                // Format tanggal, contoh: 2025-06-03
                val timeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

                // Ambil nama file tanpa ekstensi
                val baseNameWithoutExt = originalFileName.substringBeforeLast(".")

                // Sanitasi nama file asli supaya aman dipakai di filename
                val sanitizedBaseName = baseNameWithoutExt
                    .replace(Regex("[^a-zA-Z0-9\\-_ ]"), "_")
                    .replace(Regex("_+"), "_")
                    .trim('_')
                    .take(100)

                // Ambil ekstensi file asli (jika ada), kalau tidak pakai default sesuai mimeType
                val extFromFileName = originalFileName.substringAfterLast(".", "")
                val extension = if (extFromFileName.isNotEmpty()) extFromFileName else when {
                    mimeType.startsWith("video") -> "mp4"
                    mimeType.startsWith("image") -> "jpg"
                    mimeType.startsWith("audio") -> "mp3"
                    else -> "dat"
                }

                // Gabungkan nama file dengan tanggal dan ekstensi
                val fileNameWithDate = "$sanitizedBaseName$timeStamp.$extension".lowercase()

                // Generate nama file unik (jika file sama, tambahkan (1), (2), dst)
                val uniqueFileName = Downloader.generateUniqueFileName(context, fileNameWithDate, mimeType, "whatsapp")

                val savedUri = Downloader.saveToMediaStoreFromStream(
                    context, inputStream, uniqueFileName, mimeType, -1, onProgressUpdate, "whatsapp"
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
