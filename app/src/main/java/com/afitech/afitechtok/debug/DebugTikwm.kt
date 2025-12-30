package com.afitech.afitechtok.debug

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object DebugTikwm {

    private const val TAG = "DebugTikwm"
    private const val BASE_API = "https://www.tikwm.com/api/?url="

    data class TikwmDebugResult(
        val rawJson: JSONObject?,
        val hasPlay: Boolean,
        val playUrl: String?,
        val hasWmPlay: Boolean,
        val wmPlayUrl: String?,
        val hasMusicPlay: Boolean,
        val musicPlayUrl: String?,
        val imagesCount: Int
    )

    private suspend fun fetchApiJson(tiktokUrl: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encoded = try { URLEncoder.encode(tiktokUrl, "UTF-8") } catch (e: Exception) { tiktokUrl }
            val apiUrl = "$BASE_API$encoded"
            Log.d(TAG, "Requesting: $apiUrl")

            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.doInput = true
            conn.connect()

            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            Log.d(TAG, "Response code: $respCode")
            Log.d(TAG, "Response body: $response")

            conn.disconnect()
            return@withContext JSONObject(response)
        } catch (e: Exception) {
            Log.e(TAG, "fetchApiJson error: ${e.localizedMessage}")
            return@withContext null
        }
    }

    /**
     * Public debug function: call this from coroutine (e.g. lifecycleScope.launch).
     * Logs detailed info and shows a Toast summary on the main thread.
     */
    suspend fun checkTikwmFeatures(tiktokUrl: String, ctx: Context): TikwmDebugResult? {
        val json = fetchApiJson(tiktokUrl)
        if (json == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, "Debug: gagal memanggil API (cek log)", Toast.LENGTH_LONG).show()
            }
            return null
        }

        var playUrl: String? = null
        var wmPlayUrl: String? = null
        var musicPlayUrl: String? = null
        var imagesCount = 0

        try {
            val code = json.optInt("code", -1)
            Log.d(TAG, "API code: $code")
            val data = json.optJSONObject("data")
            if (data != null) {
                playUrl = data.optString("play").takeIf { it.isNotBlank() }
                wmPlayUrl = data.optString("wmplay").takeIf { it.isNotBlank() }
                val musicObj = data.optJSONObject("music_info")
                musicPlayUrl = musicObj?.optString("play")?.takeIf { it.isNotBlank() }
                val images = data.optJSONArray("images")
                imagesCount = images?.length() ?: 0
            } else {
                Log.w(TAG, "No data object in response")
            }

            val hasPlay = !playUrl.isNullOrBlank()
            val hasWmPlay = !wmPlayUrl.isNullOrBlank()
            val hasMusic = !musicPlayUrl.isNullOrBlank()

            // Log details
            Log.i(TAG, "DEBUG RESULT for: $tiktokUrl")
            Log.i(TAG, " - hasPlay: $hasPlay, playUrl: ${playUrl ?: "null"}")
            Log.i(TAG, " - hasWmPlay: $hasWmPlay, wmPlayUrl: ${wmPlayUrl ?: "null"}")
            Log.i(TAG, " - hasMusicPlay: $hasMusic, musicPlayUrl: ${musicPlayUrl ?: "null"}")
            Log.i(TAG, " - imagesCount: $imagesCount")
            Log.i(TAG, " - full JSON: ${json.toString(2)}")

            withContext(Dispatchers.Main) {
                val summary = buildString {
                    append(if (hasPlay) "play✓ " else "play✗ ")
                    append(if (hasWmPlay) "wmplay✓ " else "wmplay✗ ")
                    append(if (hasMusic) "music✓ " else "music✗ ")
                    append("images:$imagesCount")
                }
                Toast.makeText(ctx, "DebugTikwm: $summary", Toast.LENGTH_LONG).show()
            }

            return TikwmDebugResult(
                rawJson = json,
                hasPlay = hasPlay,
                playUrl = playUrl,
                hasWmPlay = hasWmPlay,
                wmPlayUrl = wmPlayUrl,
                hasMusicPlay = hasMusic,
                musicPlayUrl = musicPlayUrl,
                imagesCount = imagesCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "checkTikwmFeatures error: ${e.localizedMessage}")
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, "Debug: parsing error (cek log)", Toast.LENGTH_LONG).show()
            }
            return null
        }
    }
}
