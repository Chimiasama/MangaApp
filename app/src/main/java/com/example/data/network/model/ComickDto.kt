package com.example.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Objects (DTO) for the Comick API (https://api.comick.fun / https://api.comick.io).
 * Provides webtoon, manhwa, and manga catalog with multi-language scanlations.
 */

@JsonClass(generateAdapter = true)
data class ComickSearchItemDto(
    val hid: String? = null,
    val slug: String? = null,
    val title: String? = null,
    val desc: String? = null,
    @Json(name = "cover_url") val coverUrl: String? = null,
    @Json(name = "md_covers") val mdCovers: List<ComickCoverDto>? = emptyList(),
    val genres: List<String>? = emptyList(),
    @Json(name = "content_rating") val contentRating: String? = null
) {
    fun resolveCoverUrl(): String {
        val b2key = mdCovers?.firstOrNull()?.b2key
        return when {
            !b2key.isNullOrBlank() -> "https://meo.comick.pictures/$b2key"
            !coverUrl.isNullOrBlank() -> coverUrl
            else -> "https://picsum.photos/300/450"
        }
    }
}

@JsonClass(generateAdapter = true)
data class ComickCoverDto(
    val b2key: String? = null,
    val w: Int? = null,
    val h: Int? = null
)

@JsonClass(generateAdapter = true)
data class ComickComicDetailsResponse(
    val comic: ComickComicDto? = null,
    val artists: List<ComickPersonDto>? = emptyList(),
    val authors: List<ComickPersonDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ComickComicDto(
    val hid: String? = null,
    val slug: String? = null,
    val title: String? = null,
    val desc: String? = null,
    val status: Int? = 0,
    @Json(name = "md_covers") val mdCovers: List<ComickCoverDto>? = emptyList(),
    @Json(name = "md_comic_md_genres") val mdComicMdGenres: List<ComickGenreItemDto>? = emptyList(),
    val iso639_1: String? = null
) {
    fun resolveCoverUrl(): String {
        val b2key = mdCovers?.firstOrNull()?.b2key
        return if (!b2key.isNullOrBlank()) {
            "https://meo.comick.pictures/$b2key"
        } else {
            "https://picsum.photos/300/450"
        }
    }

    fun extractGenres(): List<String> {
        return mdComicMdGenres?.mapNotNull { it.mdGenres?.name } ?: emptyList()
    }
}

@JsonClass(generateAdapter = true)
data class ComickGenreItemDto(
    @Json(name = "md_genres") val mdGenres: ComickGenreNameDto? = null
)

@JsonClass(generateAdapter = true)
data class ComickGenreNameDto(
    val name: String? = null,
    val slug: String? = null
)

@JsonClass(generateAdapter = true)
data class ComickPersonDto(
    val name: String? = null,
    val slug: String? = null
)

@JsonClass(generateAdapter = true)
data class ComickChapterListResponse(
    val chapters: List<ComickChapterDto>? = emptyList(),
    val total: Int? = 0
)

@JsonClass(generateAdapter = true)
data class ComickChapterDto(
    val hid: String? = null,
    val chap: String? = null,
    val vol: String? = null,
    val title: String? = null,
    val lang: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "group_name") val groupName: List<String>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ComickChapterDetailResponse(
    val chapter: ComickChapterImagesContainerDto? = null
)

@JsonClass(generateAdapter = true)
data class ComickChapterImagesContainerDto(
    val hid: String? = null,
    val images: List<ComickPageImageDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ComickPageImageDto(
    val url: String? = null,
    val b2key: String? = null,
    val w: Int? = null,
    val h: Int? = null
) {
    fun resolveImageUrl(): String {
        return when {
            !url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://")) -> url
            !b2key.isNullOrBlank() -> "https://meo.comick.pictures/$b2key"
            else -> ""
        }
    }
}
