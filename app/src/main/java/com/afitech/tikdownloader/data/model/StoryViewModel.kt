package com.afitech.tikdownloader.data.model

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.afitech.tikdownloader.utils.StoryUtils
import android.net.Uri

class StoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _stories = MutableLiveData<List<StoryItem>>()
    val stories: LiveData<List<StoryItem>> get() = _stories

    fun loadStoriesFromUri(uri: Uri) {
        Log.d("StoryViewModel", "loadStoriesFromUri called with URI: $uri")

        // Run the loading process on a background thread to avoid blocking the main thread
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext

            val storiesData = withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Log.d("StoryViewModel", "Using SAF for Android 11+")
                    StoryUtils.getStoriesUsingSAFFromUri(context, uri)
                } else {
                    Log.d("StoryViewModel", "Using legacy method for Android 10 and below")
                    StoryUtils.getStoriesLegacy()
                }
            }

            // Set the stories to the LiveData
            _stories.value = storiesData
            Log.d("StoryViewModel", "Loaded stories: ${storiesData.size} items")
        }
    }
}
