package com.afitech.tikdownloader.ui.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.network.YouTubeDownloader
import com.afitech.tikdownloader.network.YouTubeDownloader.VideoInfo
import com.afitech.tikdownloader.ui.MainActivity
import java.util.concurrent.Executors

class DownloadService : Service() {

    companion object {
        const val ACTION_PROGRESS   = "com.afitech.tikdownloader.ACTION_PROGRESS"
        const val ACTION_COMPLETE   = "com.afitech.tikdownloader.ACTION_COMPLETE"
        const val EXTRA_PROGRESS    = "com.afitech.tikdownloader.EXTRA_PROGRESS"
        const val EXTRA_SUCCESS     = "com.afitech.tikdownloader.EXTRA_SUCCESS"
        const val EXTRA_VIDEO_URL   = "video_url"
        const val NOTIF_CHANNEL_ID  = "download_channel"
        const val NOTIF_ID          = 1

        private var doneCallback: ((Boolean) -> Unit)? = null

        fun setDoneCallback(callback: (Boolean) -> Unit) {
            doneCallback = callback
        }
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var currentVideoInfo: VideoInfo
    private lateinit var videoUrlOriginal: String

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoUrl = intent?.getStringExtra(EXTRA_VIDEO_URL) ?: return START_NOT_STICKY
        val format   = intent.getStringExtra("format") ?: "mp4"
        videoUrlOriginal = videoUrl // Menyimpan URL asli

        Log.d("DownloadService", "Download started: $videoUrl ($format)")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // Ambil info video
        fetchVideoInfoInBackground(videoUrl, format)

        val lbm = LocalBroadcastManager.getInstance(applicationContext)

        val progressCb = object : YouTubeDownloader.ProgressCallback {
            override fun onProgressUpdate(progress: Int) {
                Log.d("DownloadService", "Broadcasting progress: $progress%")
                lbm.sendBroadcast(Intent(ACTION_PROGRESS).apply {
                    putExtra(EXTRA_PROGRESS, progress)
                })
                updateProgressNotification(currentVideoInfo, progress)
            }
        }

        val doneCb: (Boolean) -> Unit = { success ->
            Log.d("DownloadService", "Broadcasting complete: $success")
            lbm.sendBroadcast(Intent(ACTION_COMPLETE).apply {
                putExtra(EXTRA_SUCCESS, success)
            })

            // Callback untuk memberitahu UI selesai
            doneCallback?.invoke(success)
            doneCallback = null // Reset callback setelah dipanggil

            if (success) {
                showCompletedNotification(currentVideoInfo)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        if (format == "mp3") {
            YouTubeDownloader.downloadAudio(
                applicationContext,
                videoUrl,
                progressCallback = progressCb,
                callback = doneCb
            )
        } else {
            YouTubeDownloader.downloadVideo(
                applicationContext,
                videoUrl,
                progressCallback = progressCb,
                callback = doneCb
            )
        }

        return START_STICKY
    }

    private fun fetchVideoInfoInBackground(videoUrl: String, format: String) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val videoInfo = YouTubeDownloader.fetchVideoInfo(videoUrl, format)
                Handler(Looper.getMainLooper()).post {
                    val rawTitle = videoInfo.title
                    val filteredTitle = rawTitle.replace(Regex("#.*$"), "").trim()
                    currentVideoInfo = videoInfo.copy(title = filteredTitle)
                    startForeground(NOTIF_ID, buildInitialNotification(filteredTitle))
                }

            } catch (e: Exception) {
                Log.e("DownloadService", "Gagal ambil info video: ${e.message}", e)
                Handler(Looper.getMainLooper()).post {
                    currentVideoInfo = VideoInfo("Video Tidak Diketahui", 0L)
                    startForeground(NOTIF_ID, buildInitialNotification("Video Tidak Diketahui"))
                }
            }
        }
    }

    private fun buildInitialNotification(title: String): Notification {
        val pendingIntent = buildMainActivityIntent(videoUrlOriginal) // Gunakan URL asli di sini

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Menghubungkan ke server…")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateProgressNotification(videoInfo: VideoInfo, progress: Int) {
        val pendingIntent = buildMainActivityIntent(videoUrlOriginal) // Gunakan URL asli di sini

        val totalBytes = videoInfo.sizeInBytes
        val downloadedBytes = if (progress in 0..100 && totalBytes > 0)
            (progress * totalBytes / 100) else 0

        val downloadedStr = android.text.format.Formatter.formatShortFileSize(this, downloadedBytes)
        val totalStr = android.text.format.Formatter.formatShortFileSize(this, totalBytes)

        val notificationText = "Mengunduh… $downloadedStr dari $totalStr ($progress%)"

        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(videoInfo.title)
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIF_ID, notification)
    }

    private fun showCompletedNotification(videoInfo: VideoInfo) {
        val pendingIntent = buildMainActivityIntent(videoUrlOriginal) // Gunakan URL asli di sini

        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(videoInfo.title)
            .setContentText("Unduhan selesai")
            .setSmallIcon(R.drawable.ic_download)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIF_ID + 1, notification)
    }

    private fun buildMainActivityIntent(videoUrl: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FRAGMENT, "yt_downloader")
            putExtra(EXTRA_VIDEO_URL, videoUrl) // Menggunakan URL asli di sini
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Download Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Menampilkan progres unduhan"
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
