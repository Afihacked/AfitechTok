package com.afitech.afitechtok.data.repository

import com.afitech.afitechtok.data.database.DownloadHistoryDao
import com.afitech.afitechtok.data.model.DownloadHistory
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
