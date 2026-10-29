package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapter_downloads")
data class DownloadEntity(
    @PrimaryKey val chapterId: String,
    val chapterTitle: String,
    val chapterUrl: String,
    val mangaUrl: String,
    val mangaTitle: String,
    val mangaCoverUrl: String,
    val sourceId: String,
    val totalPages: Int = 0,
    val downloadedPagesCount: Int = 0,
    val localPath: String = "",
    val downloadState: String = STATE_DOWNLOADING,
    val downloadProgress: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATE_QUEUED = "QUEUED"
        const val STATE_PREPARING = "PREPARING"
        const val STATE_RETRYING = "RETRYING"
        const val STATE_DOWNLOADING = "DOWNLOADING"
        const val STATE_COMPLETED = "COMPLETED"
        const val STATE_FAILED = "FAILED"
    }
}
