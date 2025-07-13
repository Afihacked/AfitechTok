package com.afitech.sosmedtoolkit.network

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TikTokDownloader {

    private const val BASE_API_URL = "https://www.tikwm.com/api/?url="

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
            Log.d("TikTokDownloader", "API Response: $response")

            JSONObject(response)
        } catch (e: Exception) {
            Log.e("TikTokDownloader", "Error mengambil data API: ${e.localizedMessage}")
            null
        }
    }

    private fun resolveShortLink(shortUrl: String): String? {
        return try {
            val url = URL(shortUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connect()
            val resolvedUrl = connection.getHeaderField("Location")
            connection.disconnect()
            resolvedUrl
        } catch (e: Exception) {
            Log.e("TikTokDownloader", "Gagal resolve short link: ${e.message}")
            null
        }
    }


    // Cek apakah URL TikTok adalah slide (bukan video biasa)
    fun isTikTokSlide(url: String): Boolean {
        val json = fetchApiData(url) ?: return false
        if (json.optInt("code", -1) != 0) return false
        val imagesArray = json.optJSONObject("data")?.optJSONArray("images")
        return imagesArray != null && imagesArray.length() > 0
    }

    // Ambil URL untuk download video, musik, atau gambar
    fun getDownloadUrl(tiktokUrl: String, format: String): String? {
        val json = fetchApiData(tiktokUrl) ?: return null
        if (json.optInt("code", -1) != 0) return null

        val data = json.optJSONObject("data") ?: return null

        val downloadUrl = when (format) {
            "Videos" -> data.optString("play").takeIf { it.isNotEmpty() } ?: data.optString("wmplay")
            "Music" -> data.optJSONObject("music_info")?.optString("play")
            "JPG", "Gambar" -> data.optString("cover").takeIf { it.isNotEmpty() } ?: data.optString("origin_cover")
            else -> null
        }

        if (downloadUrl.isNullOrEmpty()) {
            Log.e("TikTokDownloader", "URL unduhan kosong untuk format: $format")
            return null
        }

        return downloadUrl
    }

    // Ambil semua gambar slide dari video TikTok slide (baru digunakan di Service)
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


    // Ambil semua gambar slide (opsional, jika masih pakai struktur lama)
//    fun getSlideImages(tiktokUrl: String): List<String>? {
//        val json = fetchApiData(tiktokUrl) ?: return null
//        if (json.optInt("code", -1) != 0) {
//            Log.e("TikTokDownloader", "Gagal mendapatkan gambar slide dari URL: $tiktokUrl")
//            return null
//        }
//
//        val imagesArray = json.optJSONObject("data")?.optJSONArray("images") ?: return null
//        val slideImages = List(imagesArray.length()) { index -> imagesArray.optString(index) }
//
//        if (slideImages.isEmpty()) {
//            Log.e("TikTokDownloader", "Tidak ada gambar slide ditemukan di URL: $tiktokUrl")
//        }
//
//        return slideImages
//    }
}
