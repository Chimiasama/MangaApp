package com.example.data.local

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
    val publicationYear: Int? = null
)

/**
 * Standard alias for the Room database Manga model.
 */
typealias Manga = MangaEntity
