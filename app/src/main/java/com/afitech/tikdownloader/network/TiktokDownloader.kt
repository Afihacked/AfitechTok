package com.afitech.tikdownloader.network

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
            Log.d("TikTokDownloader", "Memanggil API: $apiUrl") // Tambahkan log untuk debug

            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.doInput = true
            connection.connectTimeout = 20000
            connection.readTimeout = 306000
            connection.connect()

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("TikTokDownloader", "API Response: $response") // Log respons API

            JSONObject(response)
        } catch (e: Exception) {
            Log.e("TikTokDownloader", "Error mengambil data API: ${e.localizedMessage}")
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
        if (json.optInt("code", -1) != 0) {
            return null
        }

        val data = json.optJSONObject("data") ?: return null

        val downloadUrl = when (format) {
            "MP4" -> data.optString("play").takeIf { it.isNotEmpty() } ?: data.optString("wmplay")
            "MP3" -> data.optJSONObject("music_info")?.optString("play")
            "JPG" -> data.optString("cover").takeIf { it.isNotEmpty() } ?: data.optString("origin_cover")
            else -> null
        }

        if (downloadUrl.isNullOrEmpty()) {
            Log.e("TikTokDownloader", "URL unduhan kosong untuk format: $format")
            return null
        }

        return downloadUrl
    }

    // Ambil semua gambar slide dari video TikTok slide
    fun getSlideImages(tiktokUrl: String): List<String>? {
        val json = fetchApiData(tiktokUrl) ?: return null
        if (json.optInt("code", -1) != 0) {
            Log.e("TikTokDownloader", "Gagal mendapatkan gambar slide dari URL: $tiktokUrl")
            return null
        }

        val imagesArray = json.optJSONObject("data")?.optJSONArray("images") ?: return null
        val slideImages = List(imagesArray.length()) { index -> imagesArray.optString(index) }

        if (slideImages.isEmpty()) {
            Log.e("TikTokDownloader", "Tidak ada gambar slide ditemukan di URL: $tiktokUrl")
        }

        return slideImages
    }
}
