package com.afitech.afitechtok.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.afitech.afitechtok.data.database.DownloadHistoryDao
import com.afitech.afitechtok.data.model.DownloadHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.ByteBuffer

object Downloader {

    private const val TAG = "Downloader"

    data class RemoteMeta(val contentType: String?, val contentLength: Long?, val filenameFromServer: String?)

    // =============================
    // Public: downloadFile (suspend)
    // =============================
    /**
     * Mengunduh file dari URL dan menyimpan ke MediaStore.
     * Param `mimeType` lama tetap diterima tetapi akan di-override bila server memberikan Content-Type via HEAD.
     * Mengembalikan File lokal bila path fisik ditemukan (getPathFromUri), atau null bila tidak ditemukan.
     */
    suspend fun downloadFile(
        context: Context,
        fileUrl: String,
        fileName: String,
        mimeType: String,
        onProgressUpdate: (progress: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
        downloadHistoryDao: DownloadHistoryDao,
        source: String = "tiktok"
    ): File? = withContext(Dispatchers.IO) {
        var tmpFile: File? = null
        try {
            // Probe metadata (HEAD) untuk menentukan content-type dan filename jika server menyediakan
            val meta = probeRemote(fileUrl)
            val effectiveMime = meta.contentType ?: mimeType
            val guess = guessExtensionAndMime(effectiveMime, meta.filenameFromServer ?: fileUrl)
            val ext = guess.first // includes dot like ".mp4"
            val finalMime = guess.second

            // siapkan nama file yang unik dengan ekstensi yang benar
            val uniqueFileName = generateUniqueFileName(context, stripExtension(fileName) + ext, finalMime, source)

            // download ke file sementara di cache
            tmpFile = File.createTempFile("afitech_dl_", ext, context.cacheDir).apply { deleteOnExit() }

            val downloadOk = downloadToFileInternal(fileUrl, tmpFile, onProgressUpdate)
            if (!downloadOk || !tmpFile.exists()) {
                Log.e(TAG, "downloadFile: download gagal atau tmp file tidak ada")
                tmpFile?.delete()
                return@withContext null
            }

            // Jika video mp4 -> remux faststart untuk memastikan moov atom di awal
            var fileToSave = tmpFile
            var isRemuxed = false
            if (finalMime.startsWith("video/") || ext.equals(".mp4", ignoreCase = true)) {
                try {
                    val remuxed = File.createTempFile("afitech_remux_", ".mp4", context.cacheDir)
                    val ok = remuxToFastStart(tmpFile, remuxed)
                    if (ok && remuxed.exists()) {
                        fileToSave = remuxed
                        isRemuxed = true
                        tmpFile.delete()
                    } else {
                        Log.w(TAG, "remuxToFastStart gagal, akan simpan file asli tanpa remux")
                        remuxed.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Remux exception: ${e.message}")
                }
            }

            // Ambil metadata sebelum menyimpan (karena saveFileToMediaStore akan menghapus temp)
            val fileSize = try { fileToSave?.length() ?: -1L } catch (_: Exception) { -1L }
            val durationMs: Long? = try {
                if (fileToSave != null && (finalMime.startsWith("video/") || finalMime.startsWith("audio/"))) {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(fileToSave.absolutePath)
                    val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    mmr.release()
                    dur
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Gagal ambil durasi: ${e.message}")
                null
            }

            // Simpan fileToSave ke MediaStore sesuai tipe (video/audio/image)
            val savedUri = saveFileToMediaStore(context, fileToSave!!, uniqueFileName, finalMime)
            if (savedUri == null) {
                Log.e(TAG, "Gagal menyimpan file ke MediaStore")
                fileToSave.delete()
                return@withContext null
            }

            // insert history sesuai model baru
            try {
                val history = DownloadHistory(
                    id = 0L,
                    fileName = uniqueFileName,
                    savedUri = savedUri.toString(),
                    originalUrl = fileUrl,
                    mimeType = finalMime,
                    ext = ext,
                    fileType = when {
                        finalMime.startsWith("video") -> "Video"
                        finalMime.startsWith("audio") -> "Audio"
                        finalMime.startsWith("image") -> "Image"
                        else -> "Other"
                    },
                    fileSize = if (fileSize > 0) fileSize else null,
                    durationMs = durationMs,
                    isRemuxed = isRemuxed,
                    downloadDate = System.currentTimeMillis(),
                    source = source
                )
                downloadHistoryDao.insertDownload(history)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menyimpan riwayat unduhan: ${e.message}")
            }

            // Usahakan kembalikan File fisik (jika tersedia)
            val realPath = getPathFromUri(context, savedUri)
            if (realPath != null) {
                File(realPath)
            } else {
                // Tidak dapat path langsung dari MediaStore, kembalikan file sementara yang telah disimpan (jika masih ada)
                // Catatan: caller sebaiknya mengandalkan MediaStore URI, bukan path file.
                fileToSave
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengunduh file: ${e.message}", e)
            tmpFile?.delete()
            null
        }
    }

    // =============================
    // Probe remote (HEAD)
    // =============================
    fun probeRemote(urlStr: String): RemoteMeta {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                connect()
            }
            val type = conn.contentType // ex: "video/mp4"
            val lenHeader = conn.getHeaderFieldLong("Content-Length", -1)
            val len = if (lenHeader > 0) lenHeader else null
            val disposition = conn.getHeaderField("Content-Disposition")
            val filename = disposition?.let { dispo ->
                val idx = dispo.indexOf("filename=")
                if (idx >= 0) {
                    var candidate = dispo.substring(idx + 9).trim().trim('"')
                    try { URLDecoder.decode(candidate, "UTF-8") } catch (_: Exception) { candidate }
                } else null
            }
            conn.disconnect()
            RemoteMeta(type, len, filename)
        } catch (e: Exception) {
            Log.w(TAG, "probeRemote error: ${e.message}")
            RemoteMeta(null, null, null)
        }
    }

    // =============================
    // Download helper (streams) — internal
    // =============================
    private suspend fun downloadToFileInternal(
        urlStr: String,
        outFile: File,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
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

            val buffer = ByteArray(8 * 1024)
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
                    // unknown total -> approximate progress
                    onProgress(0, downloaded, -1)
                }
            }
            output.flush()
            onProgress(100, downloaded, total)
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadToFileInternal error: ${e.message}", e)
            false
        } finally {
            try { input?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
            conn?.disconnect()
        }
    }

    fun saveToMediaStoreFromStream(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        fileSize: Long? = null,
        onProgressUpdate: (progress: Int, writtenBytes: Long, totalBytes: Long?) -> Unit,
        source: String
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

                when (source.lowercase()) {
                    "whatsapp" -> when {
                        mimeType.startsWith("video") ->
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Afitech-Whatsapp")
                        mimeType.startsWith("image") ->
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Afitech-Whatsapp")
                        mimeType.startsWith("audio") ->
                            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Afitech-Whatsapp")
                        else ->
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WhatsappDownloads")
                    }
                    else -> when {
                        mimeType.startsWith("video") ->
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Afitech-Tiktok")
                        mimeType.startsWith("image") ->
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Afitech-Tiktok")
                        mimeType.startsWith("audio") ->
                            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Afitech-Tiktok")
                        else ->
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TikTokDownloads")
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = when {
                mimeType.startsWith("video") ->
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                mimeType.startsWith("audio") ->
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                mimeType.startsWith("image") ->
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else ->
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return null

            resolver.openOutputStream(uri)?.use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalWritten = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalWritten += bytesRead

                    val progress = fileSize?.let {
                        ((totalWritten * 100) / it).toInt()
                    } ?: -1

                    onProgressUpdate(progress, totalWritten, fileSize)
                }
                output.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            uri
        } catch (e: Exception) {
            Log.e(TAG, "saveToMediaStoreFromStream error: ${e.message}", e)
            null
        }
    }

    // =============================
    // Save file to MediaStore (from File)
    // =============================
    private fun saveFileToMediaStore(context: Context, srcFile: File, displayName: String, mime: String): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                when {
                    mime.startsWith("video/") -> put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Afitech-Tiktok")
                    mime.startsWith("audio/") -> put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Afitech-Tiktok")
                    mime.startsWith("image/") -> put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Afitech-Tiktok")
                    else -> put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TikTokDownloads")
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

            resolver.openOutputStream(uri)?.use { out ->
                srcFile.inputStream().use { input -> input.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            uri
        } catch (e: Exception) {
            Log.e(TAG, "saveFileToMediaStore error: ${e.message}", e)
            null
        } finally {
            try { srcFile.delete() } catch (_: Exception) {}
        }
    }

    // =============================
    // Remux faststart (move moov atom to front) — tanpa re-encode
    // =============================

    fun remuxToFastStart(inputFile: File, outputFile: File): Boolean {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)
            val trackCount = extractor.trackCount

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val indexMap = IntArray(trackCount) { -1 }

            // Tambahkan semua track tanpa memilih dulu
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                indexMap[i] = muxer.addTrack(format)
            }
            muxer.start()

            val bufferSize = 1 shl 20 // 1MB
            val byteBuffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()

            for (i in 0 until trackCount) {
                // pastikan track yang akan dibaca diseleksi
                extractor.unselectTrack(i)
                extractor.selectTrack(i)

                while (true) {
                    // siapkan buffer
                    byteBuffer.clear()

                    // baca sample data ke byteBuffer
                    val sampleSize = extractor.readSampleData(byteBuffer, 0)
                    if (sampleSize < 0) {
                        // tidak ada lagi sample di track ini
                        extractor.unselectTrack(i)
                        break
                    }

                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = extractor.sampleTime

                    // mapping flag: SAMPLE_FLAG_SYNC -> BUFFER_FLAG_SYNC_FRAME
                    val sampleFlags = extractor.sampleFlags
                    val mappedFlags = if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_SYNC_FRAME
                    } else {
                        0
                    }
                    info.flags = mappedFlags

                    // perlu set limit sesuai ukuran sample sebelum tulis
                    byteBuffer.position(0)
                    byteBuffer.limit(info.size)

                    // tulis ke muxer
                    muxer.writeSampleData(indexMap[i], byteBuffer, info)

                    extractor.advance()
                }
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            true
        } catch (e: Exception) {
            android.util.Log.e("remuxToFastStart", "gagal remux: ${e.message}", e)
            false
        }
    }


    // =============================
    // Utilities
    // =============================
    fun getPathFromUri(context: Context, uri: Uri): String? {
        // Note: MediaStore.MediaColumns.DATA is deprecated on newer Android; may return null.
        // Best practice: rely on Uri for accessing file, not absolute path.
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    return cursor.getString(idx)
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "getPathFromUri failed: ${e.message}")
            null
        }
    }

    private fun stripExtension(name: String): String {
        return if (name.contains(".")) name.substringBeforeLast(".") else name
    }

    fun generateUniqueFileName(
        context: Context,
        fileName: String,
        mimeType: String,
        source: String = "tiktok"
    ): String {
        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "")
        val relativeFolder = when (source.lowercase()) {
            "whatsapp" -> when {
                mimeType.startsWith("video") -> Environment.DIRECTORY_MOVIES + "/Afitech-Whatsapp"
                mimeType.startsWith("image") -> Environment.DIRECTORY_PICTURES + "/Afitech-Whatsapp"
                else -> Environment.DIRECTORY_DOWNLOADS + "/WhatsappDownloads"
            }
            else -> when {
                mimeType.startsWith("video") -> Environment.DIRECTORY_MOVIES + "/Afitech-Tiktok"
                mimeType.startsWith("audio") -> Environment.DIRECTORY_MUSIC + "/Afitech-Tiktok"
                mimeType.startsWith("image") -> Environment.DIRECTORY_PICTURES + "/Afitech-Tiktok"
                else -> Environment.DIRECTORY_DOWNLOADS + "/TikTokDownloads"
            }
        }

        val dir = File(context.getExternalFilesDir(null), relativeFolder)
        if (!dir.exists()) dir.mkdirs()

        var newFileName = if (extension.isNotEmpty()) "$baseName.$extension" else baseName
        var counter = 1
        while (File(dir, newFileName).exists()) {
            newFileName = if (extension.isNotEmpty()) "$baseName($counter).$extension" else "$baseName($counter)"
            counter++
        }

        return newFileName
    }

    private fun guessExtensionAndMime(contentType: String?, fallbackUrl: String): Pair<String, String> {
        val ct = contentType?.lowercase()
        return when {
            ct == "video/mp4" || ct == "application/mp4" -> ".mp4" to "video/mp4"
            ct == "audio/mpeg" || ct == "audio/mp3" -> ".mp3" to "audio/mpeg"
            ct == "audio/mp4" || ct == "audio/aac" || ct == "audio/x-m4a" -> ".m4a" to "audio/mp4"
            ct?.startsWith("image/") == true -> {
                when {
                    ct.contains("jpeg") || ct.contains("jpg") -> ".jpg" to "image/jpeg"
                    ct.contains("png") -> ".png" to "image/png"
                    else -> ".jpg" to "image/jpeg"
                }
            }
            else -> {
                val urlLower = fallbackUrl.lowercase()
                when {
                    urlLower.contains(".mp4") -> ".mp4" to "video/mp4"
                    urlLower.contains(".m4a") -> ".m4a" to "audio/mp4"
                    urlLower.contains(".mp3") -> ".mp3" to "audio/mpeg"
                    urlLower.contains(".jpg") || urlLower.contains(".jpeg") -> ".jpg" to "image/jpeg"
                    urlLower.contains(".png") -> ".png" to "image/png"
                    else -> ".mp4" to "video/mp4"
                }
            }
        }
    }

}
