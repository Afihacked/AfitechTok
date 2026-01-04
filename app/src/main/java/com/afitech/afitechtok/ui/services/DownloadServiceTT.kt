package com.afitech.afitechtok.ui.services

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.afitech.afitechtok.R
import com.afitech.afitechtok.data.database.AppDatabase
import com.afitech.afitechtok.data.model.DownloadHistory
import com.afitech.afitechtok.network.TikTokDownloader
import com.afitech.afitechtok.ui.helpers.AnalyticsLogger
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class DownloadServiceTT : Service() {

    companion object {
        const val ACTION_PROGRESS = "com.afitech.afitechtok.TIKTOK_PROGRESS"
        const val ACTION_COMPLETE = "com.afitech.afitechtok.TIKTOK_COMPLETE"

        const val EXTRA_PROGRESS = "com.afitech.afitechtok.EXTRA_PROGRESS"
        const val EXTRA_STATUS = "com.afitech.afitechtok.EXTRA_STATUS"
        const val EXTRA_SUCCESS = "com.afitech.afitechtok.EXTRA_SUCCESS"

        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_IS_SLIDE = "is_slide_download"
        const val EXTRA_IMAGE_URLS = "image_urls"

        const val NOTIF_CHANNEL_ID = "tiktok_download_channel"
        const val NOTIF_ID = 2
        private var doneCallback: ((Boolean) -> Unit)? = null
        fun setDoneCallback(callback: ((Boolean) -> Unit)?) {
            doneCallback = callback
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var isForegroundStarted = false

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val videoUrl = intent?.getStringExtra(EXTRA_VIDEO_URL)
            ?: return START_NOT_STICKY

        val format = intent.getStringExtra(EXTRA_FORMAT) ?: "Videos"
        val analytics = FirebaseAnalytics.getInstance(applicationContext)

        ensureForegroundStarted("Unduhan TikTok")

        serviceScope.launch {
            try {

                val notifTitle = "Unduhan TikTok"

                // ===============================
                // 1️⃣ HUBUNGKAN (NOTIF SAJA)
                // ===============================
                updateNotification(notifTitle, 0, "Menghubungkan ke TikTok…")

                val info = TikTokDownloader.getDownloadInfo(videoUrl, format)
                    ?: throw Exception("Download info null")

                val fileName = generateFileName(videoUrl, info.ext)
                val dao = AppDatabase.getDatabase(applicationContext).downloadHistoryDao()

                val tmpExt = if (info.ext.startsWith(".")) info.ext else ".${info.ext}"
                val tmpFile = File.createTempFile("afitech_tmp_", tmpExt, cacheDir)

                // ===============================
                // 2️⃣ DOWNLOAD
                // ===============================
                val downloadOk = downloadToFile(info.url, tmpFile) { percent, downloaded, total ->

                    val pct = percent.coerceIn(0, 100)

                    // 👉 STATUS UNTUK TOMBOL (TANPA SIZE)
                    val buttonStatus =
                        if (pct < 100) "Mengunduh… $pct%"
                        else "Memproses file…"

                    // 👉 STATUS UNTUK NOTIF (PAKAI SIZE)
                    val notifStatus =
                        if (pct < 100)
                            "Mengunduh… $pct% (${formatBytes(downloaded)} / ${formatBytes(total)})"
                        else
                            "Memproses file…"

                    sendProgress(pct, buttonStatus)
                    updateNotification(notifTitle, pct, notifStatus)
                }

                if (!downloadOk) {
                    broadcastResult(false)
                    return@launch
                }

                // ===============================
                // 3️⃣ SIMPAN
                // ===============================
                sendProgress(100, "Proses File")
                updateNotification(notifTitle, 100, "Proses File")

                val savedUri = saveFileToMediaStore(
                    applicationContext,
                    tmpFile,
                    fileName,
                    info.mime
                )

                if (savedUri == null) {
                    broadcastResult(false)
                    return@launch
                }

                // ===============================
                // 4️⃣ HISTORY
                // ===============================
                dao.insertDownload(
                    DownloadHistory(
                        id = 0L,
                        fileName = fileName,
                        savedUri = savedUri.toString(),
                        originalUrl = info.url,
                        mimeType = info.mime,
                        ext = info.ext,
                        fileType = "Video",
                        fileSize = tmpFile.length(),
                        durationMs = null,
                        isRemuxed = false,
                        downloadDate = System.currentTimeMillis(),
                        source = "tiktok"
                    )
                )

                // ===============================
                // 5️⃣ SELESAI
                // ===============================
                sendProgress(100, "Unduhan selesai")
                updateNotification(notifTitle, 100, "Unduhan selesai")

                AnalyticsLogger.logDownloadCompleted(analytics, "tiktok", format)
                broadcastResult(true)

            } catch (e: Exception) {
                Log.e("DownloadServiceTT", "Error: ${e.message}", e)
                broadcastResult(false)
            }
        }

        return START_STICKY
    }

    // =====================================================
    // PROGRESS
    // =====================================================
    private fun sendProgress(progress: Int, status: String) {
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(ACTION_PROGRESS).apply {
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_STATUS, status)
            }
        )
    }

    // =====================================================
    // NOTIFICATION
    // =====================================================
    private fun ensureForegroundStarted(title: String) {
        if (isForegroundStarted) return

        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Menyiapkan…")
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }

        isForegroundStarted = true
    }

    private fun updateNotification(title: String, progress: Int, text: String) {
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, progress, false)
            .setOngoing(progress < 100)
            .setAutoCancel(progress >= 100)
            .build()

        notificationManager.notify(NOTIF_ID, notif)
    }

    private fun broadcastResult(success: Boolean) {
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(ACTION_COMPLETE).putExtra(EXTRA_SUCCESS, success)
        )

        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    // =====================================================
    // HELPERS (ASLI)
    // =====================================================
    private suspend fun downloadToFile(
        urlStr: String,
        outFile: File,
        onProgress: (Int, Long, Long) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connect()

                val total = conn.contentLengthLong
                val input = conn.inputStream
                val output = outFile.outputStream()

                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        onProgress(((downloaded * 100) / total).toInt(), downloaded, total)
                    }
                }

                output.flush()
                input.close()
                output.close()
                true
            } catch (e: Exception) {
                false
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun saveFileToMediaStore(
        context: Context,
        srcFile: File,
        displayName: String,
        mime: String
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)

                when {
                    mime.startsWith("video/") ->
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AfitechTok")

                    mime.startsWith("audio/") ->
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/AfitechTok")

                    mime.startsWith("image/") ->
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AfitechTok")

                    else ->
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AfitechTok")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = when {
                mime.startsWith("video/") ->
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                mime.startsWith("audio/") ->
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                mime.startsWith("image/") ->
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                else ->
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return null

            resolver.openOutputStream(uri)?.use { out ->
                srcFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            uri
        } catch (e: Exception) {
            Log.e("DownloadServiceTT", "saveFileToMediaStore error: ${e.message}", e)
            null
        }
    }


    private fun generateFileName(url: String, ext: String): String {
        val date =
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        return "TikTok_$date$ext"
    }

    private fun formatBytes(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB",
            bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        NOTIF_CHANNEL_ID,
                        "TikTok Download",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
