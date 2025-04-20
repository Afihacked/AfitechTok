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

    private fun saveToMediaStore(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        fileSize: Int,
        onProgressUpdate: (Int) -> Unit
    ): Uri? {
        // Cek apakah konten berasal dari YouTube
        val isYouTube = fileName.contains("youtube", ignoreCase = true)

        val mediaStoreResult = if (isYouTube) {
            getMediaStoreOutputStreamForYouTube(context, fileName, mimeType)
        } else {
            getMediaStoreOutputStream(context, fileName, mimeType)
        }

        return mediaStoreResult?.let { (uri, outputStream) ->
            copyStreamWithProgress(inputStream, outputStream, fileSize, onProgressUpdate)
            outputStream.flush()
            outputStream.close()
            uri
        }
    }

    // Untuk TikTok atau default folder
    private fun getMediaStoreOutputStream(
        context: Context,
        fileName: String,
        mimeType: String
    ): Pair<Uri, OutputStream>? {
        return try {
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

            val uri = context.contentResolver.insert(mediaUri, contentValues)
            val outputStream = uri?.let { context.contentResolver.openOutputStream(it) }

            if (uri != null && outputStream != null) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
                Pair(uri, outputStream)
            } else null
        } catch (e: Exception) {
            Log.e("Downloader", "Gagal MediaStore TikTok: ${e.message}", e)
            null
        }
    }

    // Untuk YouTube
    private fun getMediaStoreOutputStreamForYouTube(
        context: Context,
        fileName: String,
        mimeType: String
    ): Pair<Uri, OutputStream>? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (mimeType.contains("video")) "Movies/YouTubeDownloader" else "Music/YouTubeDownloader"
                )
            }

            val uri = context.contentResolver.insert(
                if (mimeType.contains("video"))
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            val outputStream = uri?.let { context.contentResolver.openOutputStream(it) }
            if (uri != null && outputStream != null) Pair(uri, outputStream) else null
        } catch (e: Exception) {
            Log.e("YouTubeDownloader", "Gagal MediaStore: ${e.message}", e)
            null
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
