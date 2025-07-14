package com.afitech.sosmedtoolkit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.afitech.sosmedtoolkit.data.repository.DownloadHistoryRepository

class HistoryListViewModelFactory(private val repository: DownloadHistoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryListViewModel::class.java)) {
            return HistoryListViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
