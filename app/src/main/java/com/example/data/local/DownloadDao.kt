package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Transaction
    suspend fun markEnqueued(record: DownloadEntity) {
        val existing = getDownloadSync(record.chapterId)
        if (existing == null) insertDownload(record.copy(downloadState = DownloadEntity.STATE_QUEUED))
        else if (existing.downloadState == DownloadEntity.STATE_FAILED) {
            updateDownloadStatus(existing.chapterId, existing.downloadedPagesCount, existing.downloadProgress,
                DownloadEntity.STATE_QUEUED, existing.totalPages, existing.localPath)
        }
    }

    @Transaction
    suspend fun markPreparing(record: DownloadEntity) {
        val existing = getDownloadSync(record.chapterId)
        if (existing == null) insertDownload(record.copy(downloadState = DownloadEntity.STATE_PREPARING))
        else updateDownloadStatus(existing.chapterId, existing.downloadedPagesCount, existing.downloadProgress,
            DownloadEntity.STATE_PREPARING, existing.totalPages, existing.localPath)
    }

    @Transaction
    suspend fun markStateIfPresent(chapterId: String, state: String) {
        val existing = getDownloadSync(chapterId) ?: return
        if (existing.downloadState == DownloadEntity.STATE_COMPLETED) return
        updateDownloadStatus(chapterId, existing.downloadedPagesCount, existing.downloadProgress,
            state, existing.totalPages, existing.localPath)
    }

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
