package com.afitech.tikdownloader.ui.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.network.YouTubeDownloader
import com.afitech.tikdownloader.ui.MainActivity

class DownloadService : Service() {

    companion object {
        const val ACTION_PROGRESS   = "com.afitech.tikdownloader.ACTION_PROGRESS"
        const val ACTION_COMPLETE   = "com.afitech.tikdownloader.ACTION_COMPLETE"
        const val EXTRA_PROGRESS    = "com.afitech.tikdownloader.EXTRA_PROGRESS"
        const val EXTRA_SUCCESS     = "com.afitech.tikdownloader.EXTRA_SUCCESS"
        const val EXTRA_VIDEO_URL   = "video_url"              // kunci untuk video_url
        const val NOTIF_CHANNEL_ID  = "download_channel"
        const val NOTIF_ID          = 1
    }

    private lateinit var notificationManager: NotificationManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoUrl = intent?.getStringExtra(EXTRA_VIDEO_URL) ?: return START_NOT_STICKY
        val format   = intent.getStringExtra("format") ?: "mp4"

        Log.d("DownloadService", "Download started: $videoUrl ($format)")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildInitialNotification(videoUrl))

        val lbm = LocalBroadcastManager.getInstance(applicationContext)

        val progressCb = object : YouTubeDownloader.ProgressCallback {
            override fun onProgressUpdate(progress: Int) {
                Log.d("DownloadService", "Broadcasting progress: $progress%")
                lbm.sendBroadcast(Intent(ACTION_PROGRESS).apply {
                    putExtra(EXTRA_PROGRESS, progress)
                })
                updateProgressNotification(videoUrl, progress)
            }
        }

        val doneCb: (Boolean) -> Unit = { success ->
            Log.d("DownloadService", "Broadcasting complete: $success")
            lbm.sendBroadcast(Intent(ACTION_COMPLETE).apply {
                putExtra(EXTRA_SUCCESS, success)
            })
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

    private fun buildInitialNotification(videoUrl: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FRAGMENT, "yt_downloader")
            putExtra(EXTRA_VIDEO_URL, videoUrl) // sertakan URL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("YouTube Downloader")
            .setContentText("Menghubungkan ke server…")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateProgressNotification(videoUrl: String, progress: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FRAGMENT, "yt_downloader")
            putExtra(EXTRA_VIDEO_URL, videoUrl) // sertakan URL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("YouTube Downloader")
            .setContentText("Mengunduh… $progress%")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIF_ID, notification)
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
