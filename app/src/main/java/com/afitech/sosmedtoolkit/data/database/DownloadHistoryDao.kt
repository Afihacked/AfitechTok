package com.afitech.sosmedtoolkit.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import com.afitech.sosmedtoolkit.data.model.DownloadHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {

    @Insert
    suspend fun insertDownload(history: DownloadHistory)

    @Delete
    suspend fun deleteDownload(history: DownloadHistory)

    @Query("DELETE FROM download_history WHERE id IN (:ids)")
    suspend fun deleteMultipleById(ids: List<Int>)

    @Query("SELECT * FROM download_history ORDER BY downloadDate DESC")
    fun getAllDownloads(): Flow<List<DownloadHistory>>

    @Query("SELECT * FROM download_history WHERE fileType = :type ORDER BY downloadDate DESC")
    fun getDownloadsByType(type: String): Flow<List<DownloadHistory>>

    @Query("DELETE FROM download_history WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)



}


