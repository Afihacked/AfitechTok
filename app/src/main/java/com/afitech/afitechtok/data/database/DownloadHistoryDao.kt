package com.afitech.afitechtok.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.afitech.afitechtok.data.model.DownloadHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {

    @Insert
    suspend fun insertDownload(history: DownloadHistory)

    @Delete
    suspend fun deleteDownload(history: DownloadHistory)

    // sesuaikan id menjadi Long
    @Query("DELETE FROM download_history WHERE id IN (:ids)")
    suspend fun deleteMultipleById(ids: List<Long>)

    @Query("SELECT * FROM download_history ORDER BY downloadDate DESC")
    fun getAllDownloads(): Flow<List<DownloadHistory>>

    @Query("SELECT * FROM download_history WHERE fileType = :type ORDER BY downloadDate DESC")
    fun getDownloadsByType(type: String): Flow<List<DownloadHistory>>

    // sekarang kita menyimpan URI hasil MediaStore di kolom `savedUri`
    @Query("DELETE FROM download_history WHERE savedUri = :savedUri")
    suspend fun deleteBySavedUri(savedUri: String)

    @Query("""
    SELECT originalUrl 
    FROM download_history 
    WHERE source = :source
""")
    suspend fun getAllDownloadedOriginalUrls(source: String): List<String>

}
