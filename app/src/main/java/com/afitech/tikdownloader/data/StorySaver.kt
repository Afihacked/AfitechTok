package com.afitech.tikdownloader.data

import android.content.Context
import android.net.Uri
import com.afitech.tikdownloader.data.database.DownloadHistoryDao
import com.afitech.tikdownloader.data.model.DownloadHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object StorySaver {

    lateinit var downloadHistoryDao: DownloadHistoryDao

    /**
     * Simpan WhatsApp Story dari Uri yang sudah ada di perangkat ke MediaStore,
     * dengan menggunakan Downloader untuk konsistensi dan riwayat unduhan.
     */
    fun saveToGallery(
        context: Context,
        sourceUri: Uri,
        originalFileName: String,
        mimeType: String,
        onProgressUpdate: (Int) -> Unit = {}
    ) {
        // Karena Downloader.downloadFile butuh URL, untuk file lokal (Uri)
        // kita bisa salin file dari Uri ke MediaStore via Downloader helper khusus,
        // atau buat overload/fungsi baru di Downloader.
        // Tapi untuk sederhana, kita buat coroutine untuk baca stream dan simpan ke MediaStore secara manual.

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw Exception("Gagal membuka input stream dari Uri")

                // Gunakan Downloader internal helper untuk generate nama unik dan simpan ke MediaStore
                val uniqueFileName = Downloader.generateUniqueFileName(context, originalFileName, mimeType, "whatsapp")

                // Panggil fungsi internal saveToMediaStore di Downloader (harus dibuat public/internal jika private)
                val savedUri = Downloader.saveToMediaStoreFromStream(
                    context, inputStream, uniqueFileName, mimeType, -1, onProgressUpdate, "whatsapp"
                )

                inputStream.close()

                if (savedUri != null) {
                    // Simpan riwayat unduhan ke DB
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
