package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM chapter_downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM chapter_downloads WHERE chapterId = :chapterId LIMIT 1")
    fun getDownload(chapterId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM chapter_downloads WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getDownloadSync(chapterId: String): DownloadEntity?

    @Query("SELECT * FROM chapter_downloads WHERE downloadState = 'COMPLETED' ORDER BY timestamp DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Query("UPDATE chapter_downloads SET downloadedPagesCount = :downloadedPagesCount, downloadProgress = :progress, downloadState = :state, totalPages = :totalPages, localPath = :localPath WHERE chapterId = :chapterId")
    suspend fun updateDownloadStatus(
        chapterId: String,
        downloadedPagesCount: Int,
        progress: Float,
        state: String,
        totalPages: Int,
        localPath: String
    )

    @Query("DELETE FROM chapter_downloads WHERE chapterId = :chapterId")
    suspend fun deleteDownload(chapterId: String)
}
