package com.afitech.tikdownloader.network

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.afitech.tikdownloader.ui.components.LoadingDialogYT
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

object YouTubeDownloader {
    private val client = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.SECONDS) // 3 menit
        .connectTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    interface ProgressCallback {
        fun onProgressUpdate(progress: Int)
    }

    fun downloadVideo(
        context: Context,
        videoUrl: String,
        quality: String? = "highest", // default ke "highest"
        progressCallback: ProgressCallback? = null,
        callback: (Boolean) -> Unit
    ) {
        download(context, videoUrl, isAudio = false, quality, progressCallback, callback)
    }

    fun downloadAudio(
        context: Context,
        videoUrl: String,
        quality: String? = "best", // opsional
        progressCallback: ProgressCallback? = null,
        callback: (Boolean) -> Unit
    ) {
        download(context, videoUrl, isAudio = true, quality, progressCallback, callback)
    }

    private fun download(
        context: Context,
        videoUrl: String,
        isAudio: Boolean,
        quality: String? = null,
        progressCallback: ProgressCallback? = null,
        callback: (Boolean) -> Unit
    ) {
        Thread {
            runOnUiThread(context) {
                LoadingDialogYT.show(context) // Menampilkan saat menghubungi server
            }

            try {
                val format = if (isAudio) "mp3" else "mp4"
                if (format != "mp3" && format != "mp4") throw IllegalArgumentException("Format tidak didukung")

                val urlBuilder = HttpUrl.Builder()
                    .scheme("https")
                    .host("youtubedownloaderapi-afitech.up.railway.app")
                    .addPathSegment("download")
                    .addQueryParameter("url", videoUrl)
                    .addQueryParameter("format", format)

                if (!isAudio && quality != null) {
                    urlBuilder.addQueryParameter("quality", quality)
                }

                val httpUrl = urlBuilder.build()
                Log.d("YouTubeDownloader", "Meminta: $httpUrl")

                val youtubeRegex = Regex("^(https://(www\\.)?youtube\\.com/(watch\\?v=\\S+|shorts/\\S+|clip/\\S+)|https://youtu\\.be/\\S+)$")
                if (!youtubeRegex.matches(videoUrl)) {
                    throw IllegalArgumentException("Link YouTube tidak valid")
                }

                val request = Request.Builder().url(httpUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e("YouTubeDownloader", "Gagal: Respon tidak berhasil. Body: $errorBody")
                    throw Exception("Gagal mengunduh dari server")
                }

                runOnUiThread(context) {
                    LoadingDialogYT.dismiss()
                }

                val total = response.body?.contentLength() ?: -1L
                if (total <= 0 || total < 1000) { // Tambahkan ambang minimal ukuran file
                    throw Exception("Link tidak valid atau file tidak tersedia")
                }

                // Ambil informasi video untuk mendapatkan judul file
                val videoInfo = fetchVideoInfo(videoUrl, format)
                val sanitizedTitle = videoInfo.title.replace(Regex("[^a-zA-Z0-9\\-_ ]"), "_").take(100) // Sanitasi judul untuk nama file
                val fileName = "Afitech-SaveAll - $sanitizedTitle.$format" // Gunakan judul video untuk nama file

                val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
                val result = getMediaStoreOutputStream(context, fileName, mimeType)
                    ?: throw Exception("Gagal membuka MediaStore output")

                val outputUri = result.first
                val outputStream = result.second

                val inputStream: InputStream = response.body?.byteStream()
                    ?: throw Exception("Stream kosong")

                var bytesCopied = 0L
                val buffer = ByteArray(8 * 1024)

                inputStream.use { input ->
                    outputStream.use { output ->
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            val progress = if (total > 0) (bytesCopied * 100 / total).toInt() else -1
                            progressCallback?.onProgressUpdate(progress)
                            bytes = input.read(buffer)
                        }
                        output.flush()
                    }
                }

                // Pindai file agar metadata muncul di perangkat
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outputUri.toString()),
                    arrayOf(mimeType),
                    null
                )

                runOnUiThread(context) {
                    Toast.makeText(context, "Unduhan selesai: $fileName", Toast.LENGTH_SHORT).show()
                }

                callback(true)

            } catch (e: Exception) {
                Log.e("YouTubeDownloader", "Gagal: ${e.message}", e)
                runOnUiThread(context) {
                    LoadingDialogYT.dismiss()
                    Toast.makeText(context, "Gagal mengunduh: ${e.message}", Toast.LENGTH_LONG).show()
                }
                callback(false)
            }
        }.start()
    }

    // Ambil informasi video (judul dan ukuran file)
    data class VideoInfo(val title: String, val sizeInBytes: Long)

    fun fetchVideoInfo(url: String, format: String): VideoInfo {
        val request = Request.Builder()
            .url("https://youtubedownloaderapi-afitech.up.railway.app/info?url=$url&format=$format")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Gagal mengambil info video")

        val responseBody = response.body?.string() ?: "{}"
        Log.d("VideoInfo", "Response JSON: $responseBody")  // Log response untuk debugging

        val json = JSONObject(responseBody)
        val title = json.optString("title", "Video Tidak Diketahui")
        val size = json.optLong("filesize", 0L)

        Log.d("VideoInfo", "Judul: $title, Ukuran: $size")  // Log judul dan ukuran file

        return VideoInfo(title, size)
    }

    private fun getMediaStoreOutputStream(context: Context, fileName: String, mimeType: String): Pair<Uri, OutputStream>? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (mimeType.contains("video")) "Movies/Afitech-Youtube" else "Music/Afitech-Youtube"
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

    private fun runOnUiThread(context: Context, action: () -> Unit) {
        (context as? Activity)?.runOnUiThread(action)
    }
}

