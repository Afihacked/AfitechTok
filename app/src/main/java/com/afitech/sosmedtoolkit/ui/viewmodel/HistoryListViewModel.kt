package com.afitech.sosmedtoolkit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afitech.sosmedtoolkit.data.model.DownloadHistory
import com.afitech.sosmedtoolkit.data.repository.DownloadHistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryListViewModel(private val repository: DownloadHistoryRepository) : ViewModel() {

    private val _historyList = MutableStateFlow<List<DownloadHistory>>(emptyList())
    val historyList: StateFlow<List<DownloadHistory>> = _historyList

    private var loadJob: Job? = null

    fun loadHistory(filterType: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val flow = if (filterType == "All") {
                repository.getAllDownloads()
            } else {
                repository.getDownloadsByType(filterType)
            }
            flow.collectLatest { _historyList.value = it }
        }
    }

    fun deleteMultiple(histories: List<DownloadHistory>, onFinish: (() -> Unit)? = null) {
        viewModelScope.launch {
            val ids = histories.map { it.id }.filter { it != 0 }
            repository.deleteMultipleById(ids)
            onFinish?.invoke()
        }
    }
}
