package com.example.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Objects (DTO) for the Jikan REST API v4 (MyAnimeList - https://api.jikan.moe/v4).
 */

@JsonClass(generateAdapter = true)
data class JikanSearchMangaResponse(
    val data: List<JikanMangaDataDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class JikanMangaDataDto(
    @Json(name = "mal_id") val malId: Int,
    val title: String? = null,
    val score: Double? = null,
    val synopsis: String? = null,
    val images: JikanImagesDto? = null
)

@JsonClass(generateAdapter = true)
data class JikanImagesDto(
    val jpg: JikanImageFormatDto? = null,
    val webp: JikanImageFormatDto? = null
) {
    fun resolveUrl(): String {
        return jpg?.largeImageUrl
            ?: jpg?.imageUrl
            ?: webp?.imageUrl
            ?: "https://picsum.photos/300/450"
    }
}

@JsonClass(generateAdapter = true)
data class JikanImageFormatDto(
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "small_image_url") val smallImageUrl: String? = null,
    @Json(name = "large_image_url") val largeImageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class JikanRecommendationsResponse(
    val data: List<JikanRecommendationDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class JikanRecommendationDto(
    val entry: JikanEntryDto? = null,
    val votes: Int? = 0
)

@JsonClass(generateAdapter = true)
data class JikanEntryDto(
    @Json(name = "mal_id") val malId: Int,
    val url: String? = null,
    val images: JikanImagesDto? = null,
    val title: String? = null
)

/**
 * Domain item used in the UI for MyAnimeList recommendations.
 */
data class MalRecommendationItem(
    val malId: Int,
    val title: String,
    val imageUrl: String,
    val votes: Int = 0
)
