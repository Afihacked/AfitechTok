package com.afitech.afitechtok.data.repository

import com.afitech.afitechtok.network.TikTokDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TiktokRepository {

    // Deteksi apakah link TikTok berupa slide
    suspend fun isTikTokSlide(url: String): Boolean = withContext(Dispatchers.IO) {
        TikTokDownloader.isTikTokSlide(url)
    }

    // Ambil semua URL gambar jika konten berupa slide
    suspend fun getImageUrlsIfSlide(url: String): List<String>? = withContext(Dispatchers.IO) {
        TikTokDownloader.getImageUrlsIfSlide(url)
    }

    // Validasi link TikTok
    fun validateLink(link: String): Boolean {
        val pattern = Regex(
            """^https?://(www\.|m\.)?(tiktok\.com|vt\.tiktok\.com)/.+""",
            RegexOption.IGNORE_CASE
        )
        return pattern.matches(link.trim())
    }
}
