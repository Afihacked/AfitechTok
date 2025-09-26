package com.afitech.afitechtok.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afitech.afitechtok.data.repository.TiktokRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TiktokViewModel(
    private val repo: TiktokRepository = TiktokRepository()
) : ViewModel() {

    fun validateLink(link: String): Boolean = repo.validateLink(link)

    suspend fun isTikTokSlide(url: String): Boolean =
        withContext(viewModelScope.coroutineContext + Dispatchers.IO) {
            repo.isTikTokSlide(url)
        }

    suspend fun getImageUrlsIfSlide(url: String): List<String>? =
        withContext(viewModelScope.coroutineContext + Dispatchers.IO) {
            repo.getImageUrlsIfSlide(url)
        }
}
