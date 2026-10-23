package com.example.data.network.model

import com.squareup.moshi.JsonClass

/**
 * Data Transfer Objects (DTO) for Consumet Manga API integration.
 * Supported providers include MangaDex and MangaKakalot.
 */

@JsonClass(generateAdapter = true)
data class ConsumetSearchResponse(
    val currentPage: Int? = 1,
    val hasNextPage: Boolean? = false,
    val results: List<ConsumetMangaItemDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ConsumetMangaItemDto(
    val id: String,
    val title: String? = null,
    val image: String? = null,
    val description: String? = null,
    val status: String? = null,
    val releaseDate: String? = null
)

@JsonClass(generateAdapter = true)
data class ConsumetMangaDetailsResponse(
    val id: String,
    val title: String? = null,
    val image: String? = null,
    val description: String? = null,
    val genres: List<String>? = emptyList(),
    val status: String? = null,
    val chapters: List<ConsumetChapterDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ConsumetChapterDto(
    val id: String,
    val title: String? = null,
    val releaseDate: String? = null
)

@JsonClass(generateAdapter = true)
data class ConsumetPageDto(
    val page: Int? = null,
    val img: String? = null
)
