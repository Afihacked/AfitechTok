package com.afitech.sosmedtoolkit.data.repository

import com.afitech.sosmedtoolkit.data.database.DownloadHistoryDao
import com.afitech.sosmedtoolkit.data.model.DownloadHistory
import kotlinx.coroutines.flow.Flow

class DownloadHistoryRepository(private val dao: DownloadHistoryDao) {

    fun getAllDownloads(): Flow<List<DownloadHistory>> = dao.getAllDownloads()

    fun getDownloadsByType(type: String): Flow<List<DownloadHistory>> = dao.getDownloadsByType(type)

    suspend fun deleteDownload(item: DownloadHistory) {
        dao.deleteDownload(item)
    }

    suspend fun deleteMultipleById(ids: List<Int>) {
        dao.deleteMultipleById(ids)
    }
}
