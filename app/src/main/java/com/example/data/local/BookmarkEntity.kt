package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["chapterId", "pageIndex"], unique = true),
        Index(value = ["mangaUrl"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaUrl: String,
    val mangaTitle: String,
    val mangaCoverUrl: String = "",
    val chapterId: String,
    val chapterTitle: String,
    val pageIndex: Int,
    val pageUrl: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
