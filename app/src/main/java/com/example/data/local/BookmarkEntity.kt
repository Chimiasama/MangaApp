package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["chapterId", "pageIndex"]),
        Index(value = ["mangaUrl", "isAutoBookmark"]),
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
    val scrollOffset: Int = 0,
    val note: String = "",
    val isAutoBookmark: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
