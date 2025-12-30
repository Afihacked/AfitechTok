package com.afitech.afitechtok.ui.services

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
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
import com.afitech.afitechtok.ui.MainActivity
import com.afitech.afitechtok.ui.helpers.AnalyticsLogger
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class DownloadServiceTT : Service() {

    companion object {
        const val ACTION_PROGRESS = "com.afitech.afitechtok.TIKTOK_PROGRESS"
        const val ACTION_COMPLETE = "com.afitech.afitechtok.TIKTOK_COMPLETE"
        const val EXTRA_PROGRESS = "com.afitech.afitechtok.EXTRA_PROGRESS"
        const val EXTRA_SUCCESS = "com.afitech.afitechtok.EXTRA_SUCCESS"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_IS_SLIDE = "is_slide_download"
        const val EXTRA_IMAGE_URLS = "image_urls"
        const val NOTIF_CHANNEL_ID = "tiktok_download_channel"
        const val NOTIF_ID = 2

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

        val analytics = FirebaseAnalytics.getInstance(applicationContext)
        AnalyticsLogger.logDownloadStarted(
            analytics = analytics,
            source = "tiktok",
            downloadType = format,
            from = "rewarded_ad"
        )

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
                            AnalyticsLogger.logDownloadFailed(
                                analytics,
                                "tiktok",
                                "image",
                                "Gambar kosong atau bukan slide"
                            )
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

                // --- gunakan TikTokDownloader.getDownloadInfo agar ext & mime sesuai konten asli ---
                val info = TikTokDownloader.getDownloadInfo(videoUrl, format)
                if (info == null) {
                    Log.e("DownloadServiceTT", "getDownloadInfo mengembalikan null")
                    AnalyticsLogger.logDownloadFailed(analytics, "tiktok", format, "getDownloadInfo null")
                    broadcastResult(false)
                    return@launch
                }

                Log.d("DownloadServiceTT", "DownloadInfo: url=${info.url}, ext=${info.ext}, mime=${info.mime}")

                // generate file name with correct extension
                val fileName = generateFileName(videoUrl, format, info.ext)
                val dao = AppDatabase.getDatabase(applicationContext).downloadHistoryDao()

                updateProgressNotification(notifTitle, 0, "Mengunduh file…")
                // unduh ke file sementara di cache (pastikan ext berawalan titik)
                val tmpExt = if (info.ext.startsWith(".")) info.ext else ".${info.ext}"
                val tmpFile = File.createTempFile("afitech_tmp_", tmpExt, cacheDir).apply { deleteOnExit() }

                val downloadOk = downloadToFile(info.url, tmpFile) { percent, downloadedBytes, totalBytes ->
                    val pct = percent.coerceIn(0, 100)
                    val downloaded = formatBytes(downloadedBytes)
                    val total = if (totalBytes > 0) formatBytes(totalBytes) else "?"
                    val contentText = if (pct >= 100) "Unduhan selesai" else "Mengunduh… $pct% ($downloaded / $total)"

                    lbm.sendBroadcast(Intent(ACTION_PROGRESS).apply { putExtra(EXTRA_PROGRESS, pct) })
                    updateProgressNotification(notifTitle, pct, contentText)
                }

                if (!downloadOk || !tmpFile.exists()) {
                    Log.e("DownloadServiceTT", "Download gagal atau file tidak ada")
                    AnalyticsLogger.logDownloadFailed(analytics, "tiktok", format, "Download failed or tmp file missing")
                    broadcastResult(false)
                    return@launch
                }

                // Jika video -> remux faststart untuk memastikan moov atom di awal
                var fileToSave = tmpFile
                var remuxedFlag = false
                if (info.mime.startsWith("video/") || info.ext.equals(".mp4", ignoreCase = true)) {
                    val finalTemp = File.createTempFile("afitech_final_", ".mp4", cacheDir)
                    val remuxed = TikTokDownloader.remuxToFastStart(tmpFile, finalTemp)
                    if (remuxed && finalTemp.exists()) {
                        fileToSave = finalTemp
                        remuxedFlag = true
                        // hapus tmpFile setelah remux sukses
                        try { tmpFile.delete() } catch (_: Exception) {}
                    } else {
                        Log.w("DownloadServiceTT", "Remux gagal atau tidak menghasilkan file; akan simpan file asli")
                        // keep tmpFile as fileToSave
                        try { finalTemp.delete() } catch (_: Exception) {}
                    }
                }

                // simpan ke MediaStore sesuai tipe
                val savedUri = saveFileToMediaStore(applicationContext, fileToSave, fileName, info.mime)
                if (savedUri != null) {
                    // hitung metadata: size & duration (jika ada)
                    val fileSize = try { fileToSave.length() } catch (_: Exception) { -1L }
                    val durationMs = try {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(fileToSave.absolutePath)
                        val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        mmr.release()
                        dur
                    } catch (e: Exception) {
                        Log.w("DownloadServiceTT", "Gagal ambil durasi: ${e.message}")
                        null
                    }

                    AnalyticsLogger.logDownloadCompleted(analytics, "tiktok", format)

                    // simpan history (sesuai model baru)
                    try {
                        val history = DownloadHistory(
                            id = 0L,
                            fileName = fileName,
                            savedUri = savedUri.toString(),
                            originalUrl = info.url,
                            mimeType = info.mime,
                            ext = info.ext,
                            fileType = when {
                                info.mime.startsWith("video") -> "Video"
                                info.mime.startsWith("audio") -> "Audio"
                                info.mime.startsWith("image") -> "Image"
                                else -> "Other"
                            },
                            fileSize = if (fileSize > 0) fileSize else null,
                            durationMs = durationMs,
                            isRemuxed = remuxedFlag,
                            downloadDate = System.currentTimeMillis(),
                            source = "tiktok"
                        )
                        // DAO insert adalah suspend, kita berada di coroutine
                        dao.insertDownload(history)
                    } catch (e: Exception) {
                        Log.w("DownloadServiceTT", "Gagal simpan history: ${e.message}")
                    }

                    broadcastResult(true)
                } else {
                    Log.e("DownloadServiceTT", "Gagal menyimpan ke MediaStore")
                    AnalyticsLogger.logDownloadFailed(analytics, "tiktok", format, "Save to MediaStore failed")
                    // cleanup temp if any
                    try { fileToSave.delete() } catch (_: Exception) {}
                    broadcastResult(false)
                }

                // cleanup potential temp files (tmpFile already deleted if remux succeeded)
                try { if (tmpFile.exists()) tmpFile.delete() } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e("DownloadServiceTT", "Gagal mengunduh: ${e.message}", e)
                AnalyticsLogger.logDownloadFailed(analytics, "tiktok", format, e.message ?: "Unknown error")
                broadcastResult(false)
            }
        }

        return START_STICKY
    }

    private suspend fun downloadToFile(urlStr: String, outFile: File, onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            var input: InputStream? = null
            var output: OutputStream? = null
            try {
                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    doInput = true
                    connect()
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                input = conn.inputStream
                output = outFile.outputStream()

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloaded: Long = 0
                var lastPercent = -1
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (total > 0) {
                        val percent = ((downloaded * 100) / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent, downloaded, total)
                        }
                    } else {
                        // unknown total -> report -1 or approximate via bytes
                        onProgress(0, downloaded, -1)
                    }
                }
                output.flush()
                onProgress(100, downloaded, total)
                true
            } catch (e: Exception) {
                Log.e("DownloadServiceTT", "downloadToFile error: ${e.message}", e)
                false
            } finally {
                try { input?.close() } catch (_: Exception) {}
                try { output?.close() } catch (_: Exception) {}
                conn?.disconnect()
            }
        }
    }

    private fun saveFileToMediaStore(context: Context, srcFile: File, displayName: String, mime: String): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                when {
                    mime.startsWith("video/") -> put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AfitechTok")
                    mime.startsWith("audio/") -> put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/AfitechTok")
                    mime.startsWith("image/") -> put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AfitechTok")
                    else -> put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AfitechTok")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = when {
                mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                mime.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                mime.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return null

            // tulis file
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
        } finally {
            // attempt delete srcFile (temp)
            try { srcFile.delete() } catch (_: Exception) {}
        }
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
        val analytics = FirebaseAnalytics.getInstance(applicationContext)

        serviceScope.launch {
            val timeStamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
            var successCount = 0

            for ((index, imageUrl) in images.withIndex()) {
                val fileName = "IMG_${timeStamp}_$index.jpg"

                try {
                    updateProgressNotification(notifTitle, 0, "Mengunduh gambar ${index + 1}/${images.size}…")

                    val tmp = File.createTempFile("afitech_img_${index}_", ".jpg", cacheDir)
                    val ok = downloadToFile(imageUrl, tmp) { progress, _, _ ->
                        val overallProgress = ((index.toFloat() / images.size) * 100 + (progress.toFloat() / images.size)).toInt()
                        lbm.sendBroadcast(Intent(ACTION_PROGRESS).apply { putExtra(EXTRA_PROGRESS, overallProgress) })
                        updateProgressNotification(notifTitle, overallProgress)
                    }
                    if (ok) {
                        val savedUri = saveFileToMediaStore(applicationContext, tmp, fileName, "image/jpeg")
                        if (savedUri != null) {
                            successCount++

                            try {
                                val history = DownloadHistory(
                                    id = 0L,
                                    fileName = fileName,
                                    savedUri = savedUri.toString(),
                                    originalUrl = imageUrl,
                                    mimeType = "image/jpeg",
                                    ext = ".jpg",
                                    fileType = "Image",
                                    fileSize = try { tmp.length() } catch (_: Exception) { null },
                                    durationMs = null,
                                    isRemuxed = false,
                                    downloadDate = System.currentTimeMillis(),
                                    source = "tiktok"
                                )
                                dao.insertDownload(history)
                            } catch (e: Exception) {
                                Log.w("DownloadServiceTT", "Gagal simpan history slide image: ${e.message}")
                            }
                        }
                    }
                    else {
                        Log.e("DownloadServiceTT", "Gagal unduh gambar index $index")
                    }
                } catch (e: Exception) {
                    Log.e("DownloadServiceTT", "Gagal unduh gambar ke-$index: ${e.message}", e)
                    AnalyticsLogger.logDownloadFailed(
                        analytics,
                        "tiktok",
                        "image",
                        "Gagal unduh gambar ke-$index: ${e.message}"
                    )
                }
            }

            val allSuccess = successCount == images.size
            if (allSuccess) {
                AnalyticsLogger.logDownloadCompleted(analytics, "tiktok", "image", images.size)
            }
            broadcastResult(allSuccess)
        }
    }

    private fun broadcastResult(success: Boolean) {
        Log.d("DownloadServiceTT", "📤 broadcastResult() - success: $success")
        val lbm = LocalBroadcastManager.getInstance(applicationContext)
        lbm.sendBroadcast(
            Intent(ACTION_COMPLETE).apply {
                putExtra(EXTRA_SUCCESS, success)
            }
        )
        doneCallback?.invoke(success)
        doneCallback = null
        stopForeground(STOP_FOREGROUND_DETACH)
        isForegroundStarted = false
        stopSelf()
    }

    private fun generateFileName(url: String, format: String, extWithDot: String): String {
        val username = extractUsernameFromUrl(url) ?: "unknown"
        val timeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val ext = if (extWithDot.startsWith(".")) extWithDot.substring(1) else extWithDot
        return "$username $timeStamp.$ext"
    }

    private fun extractUsernameFromUrl(url: String): String? {
        try {
            var finalUrl = url

            // Resolve short link jika perlu
            if (url.contains("vt.tiktok.com") || url.contains("vm.tiktok.com")) {
                resolveShortLink(url)?.let { resolved ->
                    finalUrl = resolved
                }
            }

            // Tangkap @username dari URL biasa TikTok
            val regex = Regex("""https?://www\.tiktok\.com/@([^/?]+)/?""")
            val match = regex.find(finalUrl)
            return match?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e("extractUsernameFromUrl", "Gagal ekstrak username: ${e.message}", e)
            return null
        }
    }

    private fun resolveShortLink(shortUrl: String): String? {
        return try {
            val url = java.net.URL(shortUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connect()
            val resolvedUrl = connection.getHeaderField("Location") ?: shortUrl
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
            this,
            0,
            intent,
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
