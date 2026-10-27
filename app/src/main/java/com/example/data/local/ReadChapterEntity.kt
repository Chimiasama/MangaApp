package com.example.data.local

import androidx.room.Entity

/** Completed chapters survive chapter-list refreshes and changes to the resume position. */
@Entity(tableName = "read_chapters", primaryKeys = ["mangaUrl", "sourceId", "chapterId"])
data class ReadChapterEntity(
    val mangaUrl: String,
    val sourceId: String,
    val chapterId: String,
    val completedAt: Long = System.currentTimeMillis()
)
