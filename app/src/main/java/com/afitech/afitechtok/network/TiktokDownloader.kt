package com.afitech.afitechtok.network

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

object TikTokDownloader {

    private const val BASE_API_URL = "https://www.tikwm.com/api/?url="

    // ------------------------
    // Data classes
    // ------------------------
    data class RemoteMeta(
        val contentType: String?,
        val contentLength: Long?,
        val filenameFromServer: String?
    )

    data class DownloadInfo(
        val url: String,
        val ext: String,   // includes dot, e.g. ".mp4", ".m4a", ".mp3", ".jpg"
        val mime: String
    )

    // ------------------------
    // API fetch
    // ------------------------
    // Fungsi untuk mengambil data dari API TikTok
    private fun fetchApiData(url: String): JSONObject? {
        return try {
            val apiUrl = "$BASE_API_URL$url"
            Log.d("TikTokDownloader", "Memanggil API: $apiUrl")

            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.doInput = true
            connection.connectTimeout = 20000
            connection.readTimeout = 306000
            connection.connect()

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("TikTokDownloader", "========== RAW API RESPONSE ==========")
            Log.d("TikTokDownloader", response)
            Log.d("TikTokDownloader", "=====================================")

            try {
                val json = JSONObject(response)
                val data = json.optJSONObject("data")

                if (data != null) {
                    Log.d("TikTokDownloader", "===== FIELD CHECK =====")

                    Log.d("TikTokDownloader", "play: ${data.optString("play")}")
                    Log.d("TikTokDownloader", "wmplay: ${data.optString("wmplay")}")
                    Log.d("TikTokDownloader", "hdplay: ${data.optString("hdplay")}")
                    Log.d("TikTokDownloader", "play_hd: ${data.optString("play_hd")}")
                    Log.d("TikTokDownloader", "bitrate: ${data.optString("bitrate")}")
                    Log.d("TikTokDownloader", "size: ${data.optString("size")}")
                    Log.d("TikTokDownloader", "duration: ${data.optString("duration")}")
                    Log.d("TikTokDownloader", "music: ${data.optJSONObject("music_info")?.optString("play")}")

                    Log.d("TikTokDownloader", "=======================")
                }

                json
            } catch (e: Exception) {
                Log.e("TikTokDownloader", "JSON parse error: ${e.message}")
                null
            }

            JSONObject(response)
        } catch (e: Exception) {
            Log.e("TikTokDownloader", "Error mengambil data API: ${e.localizedMessage}")
            null
        }
    }

    // ------------------------
    // Resolve short link (follow redirect manually)
    // ------------------------
    private fun resolveShortLink(shortUrl: String): String? {
        return try {
            val url = URL(shortUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            val resolvedUrl = connection.getHeaderField("Location") ?: shortUrl
            connection.disconnect()
            resolvedUrl
        } catch (e: Exception) {
            Log.e("TikTokDownloader", "Gagal resolve short link: ${e.message}")
            null
        }
    }

    // ------------------------
    // Probe remote file metadata (HEAD)
    // ------------------------
    fun probeRemote(url: String): RemoteMeta {
        return try {
            val u = URL(url)
            val conn = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                connect()
            }
            val contentType = conn.contentType // ex: "video/mp4", "audio/mpeg", "audio/mp4"
            val contentLength = run {
                val cl = conn.getHeaderFieldLong("Content-Length", -1)
                if (cl >= 0) cl else null
            }
            val disposition = conn.getHeaderField("Content-Disposition")
            val filename = disposition?.let { dispo ->
                // common format: attachment; filename="name.mp4"
                val fnIndex = dispo.indexOf("filename=")
                if (fnIndex >= 0) {
                    val candidate = dispo.substring(fnIndex + 9).trim().trim('"')
                    try {
                        URLDecoder.decode(candidate, "UTF-8")
                    } catch (_: Exception) {
                        candidate
                    }
                } else null
            }
            conn.disconnect()
            RemoteMeta(contentType, contentLength, filename)
        } catch (e: Exception) {
            Log.w("TikTokDownloader", "probeRemote error: ${e.message}")
            RemoteMeta(null, null, null)
        }
    }

    // ------------------------
    // Guess extension & mime from Content-Type or URL fallback
    // ------------------------
    private fun guessExtensionAndMime(
        contentType: String?,
        fallbackUrl: String
    ): Pair<String, String> {
        // Normalize
        val ct = contentType?.lowercase()
        return when {
            ct == "video/mp4" || ct == "application/mp4" -> ".mp4" to "video/mp4"
            ct == "audio/mpeg" || ct == "audio/mp3" -> ".mp3" to "audio/mpeg"
            ct == "audio/mp4" || ct == "audio/aac" || ct == "audio/x-m4a" ->
                ".m4a" to "audio/mp4"

            ct?.startsWith("image/") == true -> {
                when {
                    ct.contains("jpeg") || ct.contains("jpg") -> ".jpg" to "image/jpeg"
                    ct.contains("png") -> ".png" to "image/png"
                    else -> ".jpg" to "image/jpeg"
                }
            }

            else -> {
                // fallback by checking URL
                val urlLower = fallbackUrl.lowercase()
                when {
                    urlLower.contains(".mp4") -> ".mp4" to "video/mp4"
                    urlLower.contains(".m4a") -> ".m4a" to "audio/mp4"
                    urlLower.contains(".mp3") -> ".mp3" to "audio/mpeg"
                    urlLower.contains(".jpg") || urlLower.contains(".jpeg") -> ".jpg" to "image/jpeg"
                    urlLower.contains(".png") -> ".png" to "image/png"
                    else -> ".mp4" to "video/mp4" // safe default for video
                }
            }
        }
    }

    // ------------------------
    // Public: mendapatkan info download (url + ext + mime) sesuai konten asli
    // ------------------------
    fun getDownloadInfo(tiktokUrl: String, format: String): DownloadInfo? {
        // Resolve short link if needed (slides use vt.tiktok.com short links)
        val resolvedUrl = if (tiktokUrl.contains("vt.tiktok.com")) {
            resolveShortLink(tiktokUrl) ?: tiktokUrl
        } else {
            tiktokUrl
        }

        val json = fetchApiData(resolvedUrl) ?: return null
        if (json.optInt("code", -1) != 0) return null
        val data = json.optJSONObject("data") ?: return null

        val rawUrl = when (format) {
            "Videos" -> data.optString("play").takeIf { it.isNotEmpty() }
                ?: data.optString("wmplay")

            "Music" -> data.optJSONObject("music_info")?.optString("play")
            "JPG", "Gambar" -> data.optString("cover").takeIf { it.isNotEmpty() } ?: data.optString(
                "origin_cover"
            )

            else -> null
        } ?: return null

        // probe remote to figure content-type and possibly filename
        val meta = probeRemote(rawUrl)
        val (ext, mime) = guessExtensionAndMime(meta.contentType, meta.filenameFromServer ?: rawUrl)

        Log.d(
            "TikTokDownloader",
            "getDownloadInfo -> url: $rawUrl | ext: $ext | mime: $mime | contentTypeProbe: ${meta.contentType}"
        )

        return DownloadInfo(rawUrl, ext, mime)
    }
    // ------------------------
    // Ambil semua gambar slide dari video TikTok slide (baru digunakan di Service)
    // ------------------------
    fun getImageUrlsIfSlide(tiktokUrl: String): List<String>? {
        val resolvedUrl = if (tiktokUrl.contains("vt.tiktok.com")) {
            resolveShortLink(tiktokUrl) ?: return null
        } else {
            tiktokUrl
        }

        val json = fetchApiData(resolvedUrl) ?: return null
        if (json.optInt("code", -1) != 0) return null

        val imagesArray = json.optJSONObject("data")?.optJSONArray("images") ?: return null
        val slideImages = List(imagesArray.length()) { index -> imagesArray.optString(index) }

        if (slideImages.isEmpty()) {
            Log.e("TikTokDownloader", "Tidak ada gambar slide ditemukan dari URL: $resolvedUrl")
        }

        return slideImages
    }

    fun isTikTokSlide(tiktokUrl: String): Boolean {
        return try {
            val resolvedUrl = if (tiktokUrl.contains("vt.tiktok.com")) {
                resolveShortLink(tiktokUrl) ?: return false
            } else {
                tiktokUrl
            }

            val json = fetchApiData(resolvedUrl) ?: return false
            if (json.optInt("code", -1) != 0) return false

            val imagesArray = json
                .optJSONObject("data")
                ?.optJSONArray("images")

            imagesArray != null && imagesArray.length() > 0
        } catch (e: Exception) {
            Log.e("TikTokDownloader", "isTikTokSlide error: ${e.message}")
            false
        }
    }
}
