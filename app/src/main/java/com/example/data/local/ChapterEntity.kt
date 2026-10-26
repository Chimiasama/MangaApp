package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    indices = [Index(value = ["mangaUrl"])]
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaUrl: String,
    val title: String,
    val chapterUrl: String,
    val chapterNumber: Float = 1.0f,
    val dateUpload: String = "",
    val isRead: Boolean = false,
    val lastPageRead: Int = 0,
    val totalPages: Int = 0,
    val fetchedTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val language: String = ""
)

/**
 * Standard alias for the Room database Chapter model.
 */
typealias Chapter = ChapterEntity
