package com.afitech.sosmedtoolkit.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.afitech.sosmedtoolkit.data.model.StoryItem
import java.io.File

object StoryUtils {

    private const val TAG = "StoryUtils"

    // Fungsi untuk mengambil file .Statuses menggunakan SAF (untuk Android 11 ke atas)
    fun getStoriesUsingSAFFromUri(context: Context, uri: Uri): List<StoryItem> {
        val storyItems = mutableListOf<StoryItem>()

        try {
            val treeUri = uri
            val docFile = DocumentFile.fromTreeUri(context, treeUri)

            // Pastikan dokumen itu valid
            docFile?.let { rootFile ->
                // Iterasi melalui file dalam folder .Statuses
                val files = rootFile.listFiles()
                files?.forEach { file ->
                    Log.d(TAG, "Found file: ${file.name}")

                    // Memeriksa ekstensi file (gambar atau video)
                    if (file.isFile && (file.name?.endsWith(".jpg") == true || file.name?.endsWith(".png") == true || file.name?.endsWith(".mp4") == true)) {
                        val type = if (file.name?.endsWith(".mp4") == true) "video" else "image"
                        storyItems.add(StoryItem(file.uri, type)) // Menggunakan file.uri untuk mendapatkan URI yang sesuai
                        Log.d(TAG, "Added story: ${file.uri}")
                    }
                }
            } ?: run {
                Log.e(TAG, "Error: DocumentFile is null.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing .Statuses folder via SAF", e)
        }

        Log.d(TAG, "Found ${storyItems.size} stories via SAF")
        return storyItems
    }

    // Fungsi untuk Android 10 ke bawah (legacy path)
    fun getStoriesLegacy(): List<StoryItem> {
        val storyItems = mutableListOf<StoryItem>()
        val storyFolder = File("/storage/emulated/0/WhatsApp/Media/.Statuses")

        Log.d(TAG, "Fetching stories from legacy path: ${storyFolder.absolutePath}")

        if (storyFolder.exists() && storyFolder.isDirectory) {
            storyFolder.listFiles()?.forEach { file ->
                Log.d(TAG, "Checking file: ${file.name}")
                if (file.exists() && (file.name.endsWith(".jpg") || file.name.endsWith(".png") || file.name.endsWith(".mp4"))) {
                    val type = if (file.name.endsWith(".mp4")) "video" else "image"
                    storyItems.add(StoryItem(Uri.fromFile(file), type))
                    Log.d(TAG, "Legacy story: ${file.name}")
                }
            }
        } else {
            Log.d(TAG, ".Statuses folder not found or is not a directory.")
        }

        return storyItems
    }
}
