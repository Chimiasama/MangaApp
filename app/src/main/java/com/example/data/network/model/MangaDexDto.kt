package com.example.data.network.model

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

/**
 * MangaDex API DTOs representing response payloads from the official MangaDex REST API v5.
 */

@JsonClass(generateAdapter = true)
data class MangaDexMangaListResponse(
    val result: String? = null,
    val response: String? = null,
    val data: List<MangaDexMangaDto>? = emptyList(),
    val limit: Int? = 0,
    val offset: Int? = 0,
    val total: Int? = 0
)

@JsonClass(generateAdapter = true)
data class MangaDexMangaDetailsResponse(
    val result: String? = null,
    val response: String? = null,
    val data: MangaDexMangaDto? = null
)

@JsonClass(generateAdapter = true)
data class MangaDexMangaDto(
    val id: String,
    val type: String? = null,
    val attributes: MangaDexMangaAttributesDto? = null,
    val relationships: List<MangaDexRelationshipDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class MangaDexMangaAttributesDto(
    val title: Map<String, String>? = emptyMap(),
    val altTitles: List<Map<String, String>>? = emptyList(),
    val description: Map<String, String>? = emptyMap(),
    val status: String? = null,
    val contentRating: String? = null,
    val tags: List<MangaDexTagDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class MangaDexTagDto(
    val id: String? = null,
    val type: String? = null,
    val attributes: MangaDexTagAttributesDto? = null
)

@JsonClass(generateAdapter = true)
data class MangaDexTagAttributesDto(
    val name: Map<String, String>? = emptyMap()
)

@JsonClass(generateAdapter = true)
data class MangaDexRelationshipDto(
    val id: String? = null,
    val type: String? = null,
    val attributes: MangaDexRelationshipAttributesDto? = null
)

@JsonClass(generateAdapter = true)
data class MangaDexRelationshipAttributesDto(
    val fileName: String? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class MangaDexChapterFeedResponse(
    val result: String? = null,
    val response: String? = null,
    val data: List<MangaDexChapterDto>? = emptyList(),
    val limit: Int? = 0,
    val offset: Int? = 0,
    val total: Int? = 0
)

@JsonClass(generateAdapter = true)
data class MangaDexChapterDto(
    val id: String,
    val type: String? = null,
    val attributes: MangaDexChapterAttributesDto? = null
)

@JsonClass(generateAdapter = true)
data class MangaDexChapterAttributesDto(
    val volume: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val translatedLanguage: String? = null,
    val externalUrl: String? = null,
    val publishAt: String? = null,
    val pages: Int? = 0
)

@JsonClass(generateAdapter = true)
data class MangaDexAtHomeResponse(
    val result: String? = null,
    val baseUrl: String? = null,
    val chapter: MangaDexAtHomeChapterDto? = null
)

@JsonClass(generateAdapter = true)
data class MangaDexAtHomeChapterDto(
    val hash: String? = null,
    val data: List<String>? = emptyList(),
    val dataSaver: List<String>? = emptyList()
)

/**
 * Lenient Moshi adapter for Map<String, String> that gracefully handles both
 * standard JSON objects (`{"en": "..."}`) and empty arrays (`[]`) which MangaDex API
 * occasionally returns for empty descriptions or localized string maps.
 */
class LenientStringMapAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Map<String, String> {
        val map = mutableMapOf<String, String>()
        when (reader.peek()) {
            JsonReader.Token.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    if (reader.peek() == JsonReader.Token.STRING) {
                        map[key] = reader.nextString()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
            JsonReader.Token.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.skipValue()
                }
                reader.endArray()
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
            }
            else -> {
                reader.skipValue()
            }
        }
        return map
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Map<String, String>?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        for ((k, v) in value) {
            writer.name(k).value(v)
        }
        writer.endObject()
    }
}
