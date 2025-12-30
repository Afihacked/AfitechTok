package com.afitech.afitechtok.data.model

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.afitech.afitechtok.utils.StoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _stories = MutableLiveData<List<StoryItem>>()
    val stories: LiveData<List<StoryItem>> get() = _stories

    // =========================
    // CACHE (RAM ONLY)
    // =========================
    private var cachedTreeUri: Uri? = null
    private var cachedStories: List<StoryItem>? = null

    /**
     * Load WhatsApp Status via SAF
     * Android 10+
     */
    fun loadStoriesFromUri(treeUri: Uri) {
        Log.d("StoryViewModel", "loadStoriesFromUri: $treeUri")

        // =========================
        // 1️⃣ Pakai cache jika masih sama
        // =========================
        if (cachedTreeUri == treeUri && !cachedStories.isNullOrEmpty()) {
            Log.d(
                "StoryViewModel",
                "Using cached stories: ${cachedStories!!.size}"
            )
            _stories.value = cachedStories!!
            return
        }

        // =========================
        // 2️⃣ Load ulang dari SAF
        // =========================
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext

            val freshStories = withContext(Dispatchers.IO) {
                StoryUtils
                    .getStoriesFromStatusesFolder(
                        context = context,
                        treeUri = treeUri
                    )
                    // 🔥 TERBARU DI ATAS
                    .sortedByDescending { it.lastModified }
            }

            // =========================
            // 3️⃣ Simpan ke cache
            // =========================
            cachedTreeUri = treeUri
            cachedStories = freshStories

            _stories.value = freshStories

            Log.d(
                "StoryViewModel",
                "Loaded ${freshStories.size} stories (fresh)"
            )
        }
    }

    /**
     * Optional: clear cache manual
     */
    fun clearCache() {
        cachedTreeUri = null
        cachedStories = null
        Log.d("StoryViewModel", "Cache cleared")
    }
}
