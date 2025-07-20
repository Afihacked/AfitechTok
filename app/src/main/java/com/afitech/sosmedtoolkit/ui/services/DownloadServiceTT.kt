package com.afitech.sosmedtoolkit.ui.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.data.Downloader
import com.afitech.sosmedtoolkit.data.database.AppDatabase
import com.afitech.sosmedtoolkit.network.TikTokDownloader
import com.afitech.sosmedtoolkit.ui.MainActivity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class DownloadServiceTT : Service() {

    companion object {
        const val ACTION_PROGRESS   = "com.afitech.sosmedtoolkit.TIKTOK_PROGRESS"
        const val ACTION_COMPLETE   = "com.afitech.sosmedtoolkit.TIKTOK_COMPLETE"
        const val EXTRA_PROGRESS    = "com.afitech.sosmedtoolkit.EXTRA_PROGRESS"
        const val EXTRA_SUCCESS     = "com.afitech.sosmedtoolkit.EXTRA_SUCCESS"
        const val EXTRA_VIDEO_URL   = "video_url"
        const val EXTRA_FORMAT      = "format"
        const val EXTRA_IS_SLIDE    = "is_slide_download"
        const val EXTRA_IMAGE_URLS  = "image_urls"
        const val NOTIF_CHANNEL_ID  = "tiktok_download_channel"
        const val NOTIF_ID          = 2

        private var doneCallback: ((Boolean) -> Unit)? = null
        fun setDoneCallback(callback: ((Boolean) -> Unit)?) { doneCallback = callback }
    }

    private var isForegroundStarted = false
    private lateinit var notificationManager: NotificationManager
    private lateinit var videoUrlOriginal: String
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d("DownloadServiceTT", "✅ onStartCommand() dipanggil")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val videoUrl = intent?.getStringExtra(EXTRA_VIDEO_URL) ?: return START_NOT_STICKY
        val format = intent.getStringExtra(EXTRA_FORMAT) ?: "Videos"
        val isSlide = intent.getBooleanExtra(EXTRA_IS_SLIDE, false)
        val selectedImageUrls = intent.getStringArrayListExtra(EXTRA_IMAGE_URLS)
        videoUrlOriginal = videoUrl

        val lbm = LocalBroadcastManager.getInstance(applicationContext)
        Log.d("DownloadServiceTT", "▶ Memanggil ensureForegroundStarted...")
        ensureForegroundStarted("Menyiapkan unduhan TikTok")

        serviceScope.launch {
            try {
                val notifTitle = when {
                    format == "Gambar" && isSlide -> {
                        val imageUrls = selectedImageUrls
                        if (imageUrls.isNullOrEmpty()) {
                            Log.e("DownloadServiceTT", "Gambar kosong atau bukan slide")
                            broadcastResult(false)
                            return@launch
                        }
                        val title = "Slide TikTok (${imageUrls.size})"
                        updateProgressNotification(title, 0)
                        downloadSlideImages(imageUrls, title)
                        return@launch
                    }

                    format == "Videos" -> extractUsernameFromUrl(videoUrlOriginal)?.plus(" - Video TikTok") ?: "Unduhan Video TikTok"
                    format == "Music" -> extractUsernameFromUrl(videoUrlOriginal)?.plus(" - Musik TikTok") ?: "Unduhan Musik TikTok"
                    else -> "Unduhan TikTok"
                }

                updateProgressNotification(notifTitle, 0, "Menghubungkan ke server TikTok…")

                val downloadUrl = TikTokDownloader.getDownloadUrl(videoUrl, format)
                if (downloadUrl.isNullOrEmpty()) {
                    Log.e("DownloadServiceTT", "URL unduhan kosong")
                    broadcastResult(false)
                    return@launch
                }

                val mimeType = when (format) {
                    "Videos" -> "video/mp4"
                    "Music" -> "audio/mp3"
                    "JPG", "Gambar" -> "image/jpeg"
                    else -> "application/octet-stream"
                }

                val fileName = generateFileName(videoUrl, format)
                val dao = AppDatabase.getDatabase(applicationContext).downloadHistoryDao()

                val success = Downloader.downloadFile(
                    context = applicationContext,
                    fileUrl = downloadUrl,
                    fileName = fileName,
                    mimeType = mimeType,
                    onProgressUpdate = { progress: Int, downloadedBytes: Long, totalBytes: Long ->
                        val percent = progress.coerceIn(0, 100)
                        val contentText = if (percent >= 100) {
                            "Unduhan selesai"
                        } else {
                            val downloaded = formatBytes(downloadedBytes)
                            val total = formatBytes(totalBytes)
                            "Mengunduh… $percent% ($downloaded / $total)"
                        }
                        lbm.sendBroadcast(Intent(ACTION_PROGRESS).apply {
                            putExtra(EXTRA_PROGRESS, percent)
                        })
                        updateProgressNotification(notifTitle, percent, contentText)
                    },
                    downloadHistoryDao = dao,
                    source = "tiktok"
                )

                broadcastResult(success)
            } catch (e: Exception) {
                Log.e("DownloadServiceTT", "Gagal mengunduh: ${e.message}", e)
                broadcastResult(false)
            }
        }

        return START_STICKY
    }

    private fun ensureForegroundStarted(title: String) {
        if (!isForegroundStarted) {
            Log.d("DownloadServiceTT", "📌 Memulai startForeground()...")
            val notif = buildInitialNotification(title)
            CoroutineScope(Dispatchers.Main).launch {
                startForeground(NOTIF_ID, notif)
                isForegroundStarted = true
                Log.d("DownloadServiceTT", "✔️ startForeground() berhasil")
            }
        } else {
            Log.d("DownloadServiceTT", "ℹ️ Service sudah dalam mode foreground")
        }
    }


    private fun downloadSlideImages(images: List<String>, notifTitle: String) {
        val lbm = LocalBroadcastManager.getInstance(applicationContext)
        val dao = AppDatabase.getDatabase(applicationContext).downloadHistoryDao()

        serviceScope.launch {
            val timeStamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
            var successCount = 0

            for ((index, imageUrl) in images.withIndex()) {
                val fileName = "IMG_${timeStamp}${index}.jpg"

                try {
                    Downloader.downloadFile(
                        context = applicationContext,
                        fileUrl = imageUrl,
                        fileName = fileName,
                        mimeType = "image/jpeg",
                        onProgressUpdate = { progress, _, _ ->
                            val overallProgress = ((index * 100) + progress) / images.size
                            lbm.sendBroadcast(Intent(ACTION_PROGRESS).apply {
                                putExtra(EXTRA_PROGRESS, overallProgress)
                            })
                            updateProgressNotification(notifTitle, overallProgress)
                        },
                        downloadHistoryDao = dao,
                        source = "tiktok"
                    )
                    successCount++
                } catch (e: Exception) {
                    Log.e("DownloadServiceTT", "Gagal unduh gambar ke-$index: ${e.message}", e)
                }
            }

            val allSuccess = successCount == images.size
            broadcastResult(allSuccess)
        }
    }

    private fun broadcastResult(success: Boolean) {
        Log.d("DownloadServiceTT", "📤 broadcastResult() - success: $success")
        val lbm = LocalBroadcastManager.getInstance(applicationContext)
        lbm.sendBroadcast(Intent(ACTION_COMPLETE).apply {
            putExtra(EXTRA_SUCCESS, success)
        })
        doneCallback?.invoke(success)
        doneCallback = null
        stopForeground(STOP_FOREGROUND_DETACH)
        isForegroundStarted = false
        stopSelf()
    }

    private fun generateFileName(url: String, format: String): String {
        val username = extractUsernameFromUrl(url) ?: "unknown"
        val timeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val extension = when (format) {
            "Videos" -> "mp4"
            "Music" -> "mp3"
            "JPG", "Gambar" -> "jpg"
            else -> "dat"
        }
        return "$username $timeStamp.$extension"
    }

    private fun extractUsernameFromUrl(url: String): String? {
        if (url.contains("vt.tiktok.com")) {
            val resolvedUrl = resolveShortLink(url)
            return resolvedUrl?.let { extractUsernameFromUrl(it) }
        }
        val regex = Regex("https?://(?:www\\.|m\\.)?tiktok\\.com/@([^/?]+)")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun resolveShortLink(shortUrl: String): String? {
        return try {
            val url = java.net.URL(shortUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connect()
            val resolvedUrl = connection.getHeaderField("Location")
            connection.disconnect()
            resolvedUrl
        } catch (e: Exception) {
            null
        }
    }

    private fun buildInitialNotification(title: String): Notification {
        Log.d("DownloadServiceTT", "🔔 buildInitialNotification() dipanggil")
        val pendingIntent = buildMainActivityIntent()
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Menghubungkan ke server TikTok…")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun updateProgressNotification(title: String, progress: Int, contentText: String = "Mengunduh… $progress%") {
        Log.d("DownloadServiceTT", "📶 updateProgressNotification() - $progress%")
        val pendingIntent = buildMainActivityIntent()
        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(progress < 100)
            .setAutoCancel(progress >= 100)
            .build()

        notificationManager.notify(NOTIF_ID, notification)
    }

    private fun formatBytes(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "TikTok Download Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Menampilkan progres unduhan TikTok" }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildMainActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FRAGMENT, "history")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        isForegroundStarted = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        isForegroundStarted = false
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
