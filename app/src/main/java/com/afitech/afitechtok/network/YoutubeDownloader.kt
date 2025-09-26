package com.afitech.afitechtok.network

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.afitech.afitechtok.data.Downloader
import com.afitech.afitechtok.data.database.AppDatabase
import com.afitech.afitechtok.ui.components.LoadingDialogYT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object YouTubeDownloader {
    private val client = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.SECONDS)
        .connectTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .build()

    interface ProgressCallback {
        fun onProgressUpdate(progress: Int)
    }

    suspend fun downloadVideo(
        context: Context,
        videoUrl: String,
        quality: String? = "highest",
        progressCallback: ProgressCallback? = null,
    ): Pair<Boolean, Long> {
        return download(context, videoUrl, isAudio = false, quality, progressCallback)
    }

    suspend fun downloadAudio(
        context: Context,
        videoUrl: String,
        quality: String? = "best",
        progressCallback: ProgressCallback? = null,
    ): Pair<Boolean, Long> {
        return download(context, videoUrl, isAudio = true, quality, progressCallback)
    }

    private suspend fun download(
        context: Context,
        videoUrl: String,
        isAudio: Boolean,
        quality: String? = null,
        progressCallback: ProgressCallback? = null,
    ): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        try {
            runOnUiThread(context) { LoadingDialogYT.show(context) }

            val format = if (isAudio) "mp3" else "mp4"
            if (format != "mp3" && format != "mp4") throw IllegalArgumentException("Format tidak didukung")

            val urlBuilder = HttpUrl.Builder()
                .scheme("https")
                .host("afitech.fun")
                .addPathSegment("download")
                .addQueryParameter("url", videoUrl)
                .addQueryParameter("format", format)

            if (!isAudio && quality != null) {
                urlBuilder.addQueryParameter("quality", quality)
            }

            val httpUrl = urlBuilder.build()
            Log.d("YouTubeDownloader", "Meminta: $httpUrl")

            val youtubeRegex =
                Regex(
                    "^(https://(www\\.)?youtube\\.com/(watch\\?v=\\S+|shorts/\\S+|clip/\\S+)|https://youtu\\.be/\\S+)$"
                )
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

            val total = response.body?.contentLength() ?: -1L
            if (total <= 0 || total < 1000) {
                throw Exception("Link tidak valid atau file tidak tersedia")
            }

            val videoInfo = fetchVideoInfo(videoUrl, format)
            val sanitizedTitle = videoInfo.title
                .replace(Regex("[^a-zA-Z0-9\\-_ ]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .take(100)
            val timeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val fileName = "$sanitizedTitle$timeStamp.$format".lowercase()
            val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"

            // Unduh dan simpan file
            val savedFile: File? = Downloader.downloadFile(
                context = context,
                fileUrl = httpUrl.toString(),
                fileName = fileName,
                mimeType = mimeType,
                onProgressUpdate = { progress, _, _ ->
                    progressCallback?.onProgressUpdate(progress)
                },
                downloadHistoryDao = AppDatabase.getDatabase(context).downloadHistoryDao(),
                source = "youtube"
            )

            val actualSize = savedFile?.length() ?: 0L

            runOnUiThread(context) {
                LoadingDialogYT.dismiss()
                if (savedFile != null) {
                    Toast.makeText(context, "Unduhan selesai: $fileName", Toast.LENGTH_SHORT).show()
                }
            }

            return@withContext Pair(savedFile != null, actualSize)
        } catch (e: Exception) {
            Log.e("YouTubeDownloader", "Gagal: ${e.message}", e)
            runOnUiThread(context) {
                LoadingDialogYT.dismiss()
                Toast.makeText(context, "Gagal mengunduh: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return@withContext Pair(false, 0L)
        }
    }

    data class VideoInfo(val title: String, val sizeInBytes: Long)

    fun fetchVideoInfo(url: String, format: String): VideoInfo {
        val request = Request.Builder()
            .url("https://afitech.fun/info?url=$url&format=$format")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Gagal mengambil info video")

        val responseBody = response.body?.string() ?: "{}"
        Log.d("VideoInfo", "Response JSON: $responseBody")

        val json = JSONObject(responseBody)
        val title = json.optString("title", "Video Tidak Diketahui")
        val size = json.optLong("filesize", 0L)

        Log.d("VideoInfo", "Judul: $title, Ukuran: $size")

        return VideoInfo(title, size)
    }

    private fun runOnUiThread(context: Context, action: () -> Unit) {
        (context as? Activity)?.runOnUiThread(action)
    }
}
