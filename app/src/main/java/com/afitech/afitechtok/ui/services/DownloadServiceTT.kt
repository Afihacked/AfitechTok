package com.afitech.afitechtok.ui.services

import android.app.*
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
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
import com.afitech.afitechtok.network.NetworkHelper
import com.afitech.afitechtok.network.TikTokDownloader
import com.afitech.afitechtok.ui.MainActivity
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class DownloadServiceTT : Service() {

    companion object {
        const val ACTION_PROGRESS = "com.afitech.afitechtok.TIKTOK_PROGRESS"
        const val ACTION_COMPLETE = "com.afitech.afitechtok.TIKTOK_COMPLETE"

        const val EXTRA_PROGRESS = "com.afitech.afitechtok.EXTRA_PROGRESS"
        const val EXTRA_STATUS = "com.afitech.afitechtok.EXTRA_STATUS"
        const val EXTRA_SUCCESS = "com.afitech.afitechtok.EXTRA_SUCCESS"

        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_FORMAT = "format"

        const val NOTIF_CHANNEL_ID = "tiktok_download_channel"
        const val NOTIF_ID = 2
        const val EXTRA_ERROR_REASON = "error_reason"
        const val ERROR_NO_INTERNET = "no_internet"

        @Volatile
        var slideTaskActive = false

        @Volatile
        var slideCompletedCount = 0

        @Volatile
        var slideTotalCount = 0

        const val EXTRA_SLIDE_TOTAL = "SLIDE_TOTAL"
    }

    private lateinit var notificationManager: NotificationManager
    private var isForegroundStarted = false

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!NetworkHelper.isInternetAvailable(this)) {
            broadcastResult(false)
            stopSelf()
            return START_NOT_STICKY
        }
        val videoUrl = intent?.getStringExtra(EXTRA_VIDEO_URL)
            ?: return START_NOT_STICKY
        val format = intent.getStringExtra(EXTRA_FORMAT) ?: "Videos"
        val slideTotal = intent.getIntExtra(EXTRA_SLIDE_TOTAL, 0)

        if (format == "Gambar" && slideTotal > 0) {

            if (!slideTaskActive) {
                slideTaskActive = true
                slideTotalCount = slideTotal
                slideCompletedCount = 0
            }
        }
        // ===============================
        // 🔥 DOWNLOAD SESSION
        // ===============================
        DownloadSession.lastVideoUrl = videoUrl
        DownloadSession.lastFormat = format
        DownloadSession.isDownloading = true
        DownloadSession.lastDownloadFinished = false
        DownloadSession.lastProgress = 0

        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 🔥 title sesuai format
        val notifTitle = getNotifTitleByFormat(format)

        ensureForegroundStarted(notifTitle)

        serviceScope.launch {
            try {
// tampilkan hanya sekali di awal task
                if (slideCompletedCount == 0) {
                    updateNotification(notifTitle, 0, "Menghubungkan ke TikTok…")
                }

                val info = if (format == "Gambar" && videoUrl.startsWith("http")) {
                    val meta = TikTokDownloader.probeRemote(videoUrl)
                    val (ext, mime) = TikTokDownloader.run {
                        val ct = meta.contentType
                        when {
                            ct?.startsWith("image/") == true -> {
                                if (ct.contains("png")) ".png" to "image/png"
                                else ".jpg" to "image/jpeg"
                            }
                            else -> throw IllegalStateException("Bukan image CDN")
                        }
                    }
                    TikTokDownloader.DownloadInfo(videoUrl, ext, mime)
                } else {
                    TikTokDownloader.getDownloadInfo(videoUrl, format)
                        ?: throw Exception("Download info null")
                }

                val fileName = generateFileName(info.ext)
                val dao = AppDatabase.getDatabase(applicationContext).downloadHistoryDao()
                val tmpFile = File.createTempFile("afitech_tmp_", info.ext, cacheDir)

                val downloadOk = downloadToFile(
                    info.url,
                    format,
                    tmpFile
                ) { percent, downloaded, total ->
                    val pct = percent.coerceIn(0, 100)

                    // 🔑 SIMPAN PROGRESS TERAKHIR
                    DownloadSession.lastProgress = pct

                    sendProgress(pct, "Mengunduh… $pct%")

                    // update notif hanya jika bukan slide mode
                    if (!slideTaskActive) {
                        updateNotification(
                            notifTitle,
                            pct,
                            "Mengunduh… $pct% (${formatBytes(downloaded)} / ${formatBytes(total)})"
                        )
                    }
                }

                if (!downloadOk) {
//                    broadcastResult(false)
                    return@launch
                }

                val savedUri = saveFileToMediaStore(
                    applicationContext,
                    tmpFile,
                    fileName,
                    info.mime
                ) ?: run {
                    broadcastResult(false)
                    return@launch
                }

                dao.insertDownload(

                    DownloadHistory(
                        0L,
                        fileName,
                        savedUri.toString(),
                        info.url,
                        info.mime,
                        info.ext,
                        if (info.mime.startsWith("audio/")) "Audio"
                        else if (info.mime.startsWith("image/")) "Image"
                        else "Video",
                        tmpFile.length(),
                        null,
                        false,
                        System.currentTimeMillis(),
                        "tiktok"
                    )

                )
// ===============================
// ⭐ UPDATE PROGRESS SLIDE MODE
// ===============================
                if (format == "Gambar" && slideTaskActive) {

                    slideCompletedCount++

                    val overall =
                        ((slideCompletedCount.toFloat() / slideTotalCount) * 100).roundToInt()

                    updateNotification(
                        "Mengunduh gambar…",
                        overall,
                        "$overall% (${slideCompletedCount}/${slideTotalCount})"
                    )

                    if (slideCompletedCount >= slideTotalCount) {
                        slideTaskActive = false

                        updateNotification(
                            "Gambar TikTok",
                            100,
                            "Semua gambar selesai"
                        )
                    }
                }
// hanya untuk video/music
                if (!slideTaskActive && format != "Gambar") {
                    updateNotification(notifTitle, 100, "Unduhan selesai")
                }
                broadcastResult(true)

            } catch (e: Exception) {
                Log.e("DownloadServiceTT", "Error: ${e.message}", e)
                broadcastResult(false)
            }
        }

        return START_STICKY
    }

    // =====================================================
    // DOWNLOAD + VALIDASI STREAM (INI FIX UTAMA)
    // =====================================================
    private suspend fun downloadToFile(
        urlStr: String,
        format: String,
        outFile: File,
        onProgress: (Int, Long, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {

        var conn: HttpURLConnection? = null
        try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connect()

            val contentType = conn.contentType ?: ""

            // 🔧 FIX: pastikan isi FILE SESUAI FORMAT (API-driven)
            when (format) {
                "Music" -> if (!contentType.startsWith("audio/"))
                    throw IllegalStateException("Bukan audio asli: $contentType")

                "Gambar" -> if (!contentType.startsWith("image/"))
                    throw IllegalStateException("Bukan image asli: $contentType")

                "Videos" -> if (!contentType.startsWith("video/"))
                    throw IllegalStateException("Bukan video asli: $contentType")
            }

            val total = conn.contentLengthLong
            val input = conn.inputStream
            val output = outFile.outputStream()

            val buffer = ByteArray(8192)
            var downloaded = 0L
            var read: Int

            while (input.read(buffer).also { read = it } != -1) {
                if (!NetworkHelper.isInternetAvailable(this@DownloadServiceTT)) {
                    // 🔥 TUTUP STREAM SEBELUM THROW
                    try {
                        input.close()
                        output.close()
                    } catch (_: Exception) {}

                    throw IOException("NO_INTERNET")
                }

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

            if (e.message == "NO_INTERNET") {
                Log.e("DownloadServiceTT", "Internet terputus saat download")

                // 🔔 Notifikasi sekali saja
                showNoInternetNotification(format)

                // 🔥 Broadcast sekali saja
                broadcastResult(
                    success = false,
                    errorReason = ERROR_NO_INTERNET
                )
            } else {
                Log.e(
                    "DownloadServiceTT",
                    "downloadToFile error: ${e.message}",
                    e
                )

                broadcastResult(success = false)
            }

            false
        } finally {
            conn?.disconnect()
        }
    }

    // =====================================================
    // MEDIASTORE (ASLI, TIDAK DIPAKSA)
    // =====================================================
    private fun saveFileToMediaStore(
        context: Context,
        srcFile: File,
        displayName: String,
        mime: String
    ): Uri? {
        return try {
            val resolver = context.contentResolver

            // 🔹 Tentukan koleksi MediaStore
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

            // 🔹 Pastikan nama file UNIK (support download dobel)
            val finalName = generateUniqueDisplayName(
                resolver,
                collection,
                displayName
            )

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
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

            val uri = insertWithRetry(resolver, collection, values) ?: return null

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

    private fun insertWithRetry(
        resolver: ContentResolver,
        collection: Uri,
        values: ContentValues,
        maxRetry: Int = 3
    ): Uri? {
        repeat(maxRetry) { attempt ->
            try {
                return resolver.insert(collection, values)
            } catch (e: Exception) {
                Log.w(
                    "DownloadServiceTT",
                    "Insert MediaStore gagal, retry ke-${attempt + 1}: ${e.message}"
                )

                // 🔧 ganti nama file agar path berubah
                val name = values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
                val dot = name.lastIndexOf('.')
                val base = if (dot > 0) name.substring(0, dot) else name
                val ext = if (dot > 0) name.substring(dot) else ""

                values.put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    "${base}_${System.nanoTime()}$ext"
                )
            }
        }
        return null
    }

    private fun generateUniqueDisplayName(
        resolver: ContentResolver,
        collection: Uri,
        originalName: String
    ): String {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        var name = originalName
        var index = 1

        while (true) {
            val cursor = resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(name),
                null
            )

            val exists = cursor?.use { it.moveToFirst() } ?: false
            if (!exists) break

            name = "$baseName ($index)$ext"
            index++
        }

        return name
    }


    // =====================================================
    // UTIL
    // =====================================================
    private fun sendProgress(progress: Int, status: String) {
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(ACTION_PROGRESS).apply {
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_STATUS, status)
            }
        )
    }

//    private fun broadcastResult(
//        success: Boolean,
//        errorReason: String? = null
//    ) {
//        DownloadSession.isDownloading = false
//        DownloadSession.lastDownloadFinished = success
//        DownloadSession.lastProgress = 0
//
//        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
//            Intent(ACTION_COMPLETE).apply {
//                putExtra(EXTRA_SUCCESS, success)
//                errorReason?.let {
//                    putExtra(EXTRA_ERROR_REASON, it)
//                }
//            }
//        )
//
//        if (!slideTaskActive) {
//            stopForeground(STOP_FOREGROUND_DETACH)
//        }
////        stopSelf()
//    }
private fun broadcastResult(
    success: Boolean,
    errorReason: String? = null
) {
    DownloadSession.isDownloading = false
    DownloadSession.lastDownloadFinished = success
    DownloadSession.lastProgress = 0

    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
        Intent(ACTION_COMPLETE).apply {
            putExtra(EXTRA_SUCCESS, success)
            errorReason?.let { putExtra(EXTRA_ERROR_REASON, it) }
        }
    )

    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf() // 🔥 WAJIB
}
//    private fun showNoInternetNotification(format: String) {
//        stopForeground(true)
//        val errorNotif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
//            .setContentTitle(getNotifTitleByFormat(format))
//            .setContentText("Koneksi terputus")
//            .setSmallIcon(R.drawable.ic_download)
//            .setOngoing(false)
//            .setAutoCancel(true)
//            .setOnlyAlertOnce(false)
//            .setContentIntent(createNotificationIntent())
//            .build()
//
//        // 🔔 PASANG NOTIF BARU (BUKAN FOREGROUND)
//        notificationManager.notify(NOTIF_ID, errorNotif)
//    }
private fun showNoInternetNotification(format: String) {

    val errorNotif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
        .setContentTitle(getNotifTitleByFormat(format))
        .setContentText("Koneksi terputus")
        .setSmallIcon(R.drawable.ic_download)
        .setOngoing(false)
        .setAutoCancel(true)
        .setContentIntent(createNotificationIntent())
        .build()

    notificationManager.notify(NOTIF_ID, errorNotif)
}

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        isForegroundStarted = false
    }
    private fun createNotificationIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "OPEN_DOWNLOAD_TT"
            flags = FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureForegroundStarted(title: String) {

        // ⭐ JIKA slide aktif → notif sudah ada → jangan sentuh lagi
        if (slideTaskActive && isForegroundStarted) {
            return
        }

        // ⭐ jika sudah pernah start → jangan start ulang
        if (isForegroundStarted) return

        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Menghubungkan ke TikTok…")
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, 0, true)
            .setContentIntent(createNotificationIntent())
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
            .setOnlyAlertOnce(true)
            .setContentIntent(createNotificationIntent())
            .build()

        notificationManager.notify(NOTIF_ID, notif)
    }

    private fun generateFileName(ext: String): String {
        val timestamp = SimpleDateFormat(
            "dd-MM-yyyy_HH-mm-ss_SSS",
            Locale.getDefault()
        ).format(Date())

        val random = UUID.randomUUID()
            .toString()
            .substring(0, 4) // pendek tapi aman

        return "TikTok_${timestamp}_$random$ext"
    }

    private fun formatBytes(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(
            "%.1f %sB",
            bytes / Math.pow(unit.toDouble(), exp.toDouble()),
            pre
        )
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

    private fun getNotifTitleByFormat(format: String): String {
        return when (format) {
            "Videos" -> "Video TikTok"
            "Music" -> "Music TikTok"
            "Gambar" -> "Gambar TikTok"
            else -> "Unduhan TikTok"
        }
    }


    override fun onBind(intent: Intent?): IBinder? = null
}
