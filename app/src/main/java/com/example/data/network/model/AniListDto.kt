package com.example.data.network.model

import com.squareup.moshi.JsonClass

/**
 * Data Transfer Objects (DTO) for the AniList GraphQL API (https://graphql.anilist.co).
 */

@JsonClass(generateAdapter = true)
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?>? = emptyMap()
)

@JsonClass(generateAdapter = true)
data class AniListUserResponse(
    val data: AniListUserData? = null
)

@JsonClass(generateAdapter = true)
data class AniListUserData(
    val Viewer: AniListViewer? = null
)

@JsonClass(generateAdapter = true)
data class AniListViewer(
    val id: Int,
    val name: String,
    val avatar: AniListAvatar? = null
)

@JsonClass(generateAdapter = true)
data class AniListAvatar(
    val large: String? = null,
    val medium: String? = null
)

@JsonClass(generateAdapter = true)
data class AniListMediaResponse(
    val data: AniListMediaData? = null
)

@JsonClass(generateAdapter = true)
data class AniListMediaData(
    val Media: AniListMedia? = null
)

@JsonClass(generateAdapter = true)
data class AniListMedia(
    val id: Int,
    val title: AniListTitle? = null,
    val chapters: Int? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null
)

@JsonClass(generateAdapter = true)
data class AniListSaveEntryResponse(
    val data: AniListSaveEntryData? = null
)

@JsonClass(generateAdapter = true)
data class AniListSaveEntryData(
    val SaveMediaListEntry: AniListMediaListEntry? = null
)

@JsonClass(generateAdapter = true)
data class AniListMediaListEntry(
    val id: Int? = null,
    val mediaId: Int? = null,
    val status: String? = null,
    val progress: Int? = null
)
