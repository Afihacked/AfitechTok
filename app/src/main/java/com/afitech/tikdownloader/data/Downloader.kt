package com.afitech.tikdownloader.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.afitech.tikdownloader.data.database.DownloadHistoryDao
import com.afitech.tikdownloader.data.model.DownloadHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object Downloader {
    suspend fun downloadFile(
        context: Context,
        fileUrl: String,
        fileName: String,
        mimeType: String,
        onProgressUpdate: (Int) -> Unit,
        downloadHistoryDao: DownloadHistoryDao

    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.doInput = true
                connection.connect()

                val fileSize = connection.contentLength.takeIf { it > 0 } ?: -1
                val inputStream = connection.inputStream

                val uniqueFileName = generateUniqueFileName(context, fileName, mimeType)
                val uri = saveToMediaStore(context, inputStream, uniqueFileName, mimeType, fileSize, onProgressUpdate)


                inputStream.close()
                connection.disconnect()

                if (uri != null) {
                    val downloadHistory = DownloadHistory(
                        fileName = fileName,
                        filePath = uri.toString(),
                        fileType = when {
                            mimeType.startsWith("video") -> "Video"
                            mimeType.startsWith("audio") -> "Audio"
                            mimeType.startsWith("image") -> "Image"
                            else -> "Other"
                        },
                        downloadDate = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.IO) {
                        downloadHistoryDao.insertDownload(downloadHistory)
                        Log.d("Downloader", "Riwayat unduhan berhasil disimpan: $downloadHistory")
                    }
                } else {
                    Log.e("Downloader", "Gagal menyimpan file ke MediaStore.")
                }
            } catch (e: Exception) {
                Log.e("Downloader", "Gagal mengunduh file: ${e.message}")
            }
        }
    }
    fun generateUniqueFileName(
        context: Context,
        fileName: String,
        mimeType: String
    ): String {
        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "")
        val relativeFolder = when {
            mimeType.startsWith("video") -> Environment.DIRECTORY_MOVIES + "/TikTokDownloads"
            mimeType.startsWith("audio") -> Environment.DIRECTORY_MUSIC + "/TikTokDownloads"
            mimeType.startsWith("image") -> Environment.DIRECTORY_PICTURES + "/TikTokImages"
            else -> Environment.DIRECTORY_DOWNLOADS + "/TikTokDownloads"
        }

        val dir = File(context.getExternalFilesDir(null), relativeFolder)
        if (!dir.exists()) dir.mkdirs()

        var finalName = "$baseName.$extension"
        var file = File(dir, finalName)
        var index = 1

        while (file.exists()) {
            finalName = "$baseName ($index).$extension"
            file = File(dir, finalName)
            index++
        }

        return finalName
    }



    private fun saveToMediaStore(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        fileSize: Int,
        onProgressUpdate: (Int) -> Unit
    ): Uri? {
        val contentResolver = context.contentResolver

        val relativePath = when {
            mimeType.startsWith("video") -> Environment.DIRECTORY_MOVIES + "/TikTokDownloads"
            mimeType.startsWith("audio") -> Environment.DIRECTORY_MUSIC + "/TikTokDownloads"
            mimeType.startsWith("image") -> Environment.DIRECTORY_PICTURES + "/TikTokImages"
            else -> Environment.DIRECTORY_DOWNLOADS + "/TikTokDownloads"
        }

        val mediaUri = when {
            mimeType.startsWith("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("audio") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("image") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri: Uri? = contentResolver.insert(mediaUri, contentValues)

        uri?.let {
            contentResolver.openOutputStream(it).use { outputStream ->
                if (outputStream != null) {
                    copyStreamWithProgress(inputStream, outputStream, fileSize, onProgressUpdate)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(it, contentValues, null, null)

            Log.d("MediaStore", "File berhasil disimpan: $uri")
        } ?: Log.e("MediaStore", "Gagal menyimpan file ke MediaStore.")

        return uri
    }

    private fun copyStreamWithProgress(
        inputStream: InputStream,
        outputStream: OutputStream,
        fileSize: Int,
        onProgressUpdate: (Int) -> Unit
    ) {
        val buffer = ByteArray(4096)
        var totalBytesRead = 0
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead

            val progress = if (fileSize > 0) ((totalBytesRead * 100L) / fileSize).toInt() else -1
            if (progress >= 0) onProgressUpdate(progress)
        }
    }
}
