package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaUrl: String,
    val mangaTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val genres: String = "",
    val pagesRead: Int = 1,
    val durationSeconds: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
