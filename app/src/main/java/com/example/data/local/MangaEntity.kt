package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga_favorites")
data class MangaEntity(
    @PrimaryKey val url: String,
    val id: String,
    val title: String,
    val coverUrl: String,
    val description: String,
    val sourceId: String,
    val genres: String = "",
    val isFavorite: Boolean = true,
    val isNsfw: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val year: Int? = null,
    @ColumnInfo(defaultValue = "''") val originalLanguage: String = "",
    @ColumnInfo(defaultValue = "'Unknown'") val status: String = "Unknown"
)

/**
 * Standard alias for the Room database Manga model.
 */
typealias Manga = MangaEntity
