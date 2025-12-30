package com.afitech.afitechtok.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.afitech.afitechtok.data.model.StoryItem

object StoryUtils {

    private const val TAG = "StoryUtils"

    /**
     * Universal WhatsApp Status loader (SAF-only)
     * Digunakan untuk Android 10+
     */
    fun getStoriesFromStatusesFolder(
        context: Context,
        treeUri: Uri
    ): List<StoryItem> {

        val storyItems = mutableListOf<StoryItem>()

        try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root == null || !root.isDirectory) {
                Log.e(TAG, "Invalid SAF treeUri or not a directory")
                return emptyList()
            }

            root.listFiles().forEach { file ->
                val name = file.name ?: return@forEach

                if (file.isFile && isSupportedStory(name)) {
                    val type = if (name.endsWith(".mp4", true)) "video" else "image"
                    storyItems.add(
                        StoryItem(
                            uri = file.uri,
                            type = type,
                            lastModified = file.lastModified() // 🔥 INI PENTING
                        )
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error reading WhatsApp Status via SAF", e)
        }

        Log.d(TAG, "SAF: found ${storyItems.size} status items")
        return storyItems
    }


    // ===============================
    // LAST MODIFIED (UNTUK SORTING)
    // ===============================
    fun getLastModified(context: Context, uri: Uri): Long {
        return try {
            val doc = DocumentFile.fromSingleUri(context, uri)
            doc?.lastModified() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed getLastModified: $uri", e)
            0L
        }
    }

    // ===============================
    // Helper
    // ===============================
    private fun isSupportedStory(name: String): Boolean {
        return name.endsWith(".jpg", true)
                || name.endsWith(".png", true)
                || name.endsWith(".mp4", true)
    }
}
