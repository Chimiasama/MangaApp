package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ProgressEntity(
    @PrimaryKey val mangaUrl: String,
    val chapterId: String,
    val chapterTitle: String,
    val chapterUrl: String,
    val lastPageRead: Int,
    val totalPages: Int,
    val scrollOffset: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
