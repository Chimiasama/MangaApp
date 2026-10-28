package com.example.data.mapping

import com.example.data.local.Chapter
import com.example.data.local.ChapterEntity
import com.example.data.local.Manga
import com.example.data.local.MangaEntity
import com.example.data.network.model.MangaDexChapterDto
import com.example.data.network.model.MangaDexMangaDto
import com.example.source.ChapterModel
import com.example.source.MangaModel

/**
 * Clean mapping layer to transform MangaDex API responses (DTOs) into:
 * 1. Standard Room database entities: [MangaEntity] ([Manga]) and [ChapterEntity] ([Chapter])
 * 2. MangaSource domain models: [MangaModel] and [ChapterModel]
 */
object MangaDexMapper {

    private const val DEFAULT_COVER_CDN = "https://uploads.mangadex.org/covers"
    private const val DEFAULT_BASE_API = "https://api.mangadex.org"

    /**
     * Extracts the best title prioritizing Portuguese, English, and Romanized Japanese,
     * checking primary title as well as alternative titles.
     */
    fun extractTitle(dto: MangaDexMangaDto): String {
        val attr = dto.attributes ?: return "Manga #${dto.id}"
        val titleMap = attr.title ?: emptyMap()

        // 1. Try Portuguese in primary title
        titleMap["pt-br"]?.takeIf { it.isNotBlank() }?.let { return it }
        titleMap["pt"]?.takeIf { it.isNotBlank() }?.let { return it }

        // 2. Try English
        titleMap["en"]?.takeIf { it.isNotBlank() }?.let { return it }

        // 3. Try Romanized Japanese or Japanese
        titleMap["ja-ro"]?.takeIf { it.isNotBlank() }?.let { return it }
        titleMap["ja"]?.takeIf { it.isNotBlank() }?.let { return it }

        // 4. Try Alternative Titles for Portuguese
        attr.altTitles?.forEach { altMap ->
            altMap["pt-br"]?.takeIf { it.isNotBlank() }?.let { return it }
            altMap["pt"]?.takeIf { it.isNotBlank() }?.let { return it }
        }

        // 5. Try Alternative Titles for English
        attr.altTitles?.forEach { altMap ->
            altMap["en"]?.takeIf { it.isNotBlank() }?.let { return it }
        }

        // 6. Any non-empty title in the map
        titleMap.values.firstOrNull { it.isNotBlank() }?.let { return it }

        // 7. Fallback ID
        return "Manga #${dto.id}"
    }

    /**
     * Extracts description prioritizing Portuguese, then English.
     */
    fun extractDescription(dto: MangaDexMangaDto): String {
        val attr = dto.attributes ?: return "Sem descrição disponível."
        val descMap = attr.description ?: emptyMap()

        descMap["pt-br"]?.takeIf { it.isNotBlank() }?.let { return it }
        descMap["pt"]?.takeIf { it.isNotBlank() }?.let { return it }
        descMap["en"]?.takeIf { it.isNotBlank() }?.let { return it }

        return descMap.values.firstOrNull { it.isNotBlank() } ?: "Sem descrição disponível."
    }

    /**
     * Extracts cover image URL via cover_art relationship attributes.
     */
    fun extractCoverUrl(dto: MangaDexMangaDto): String {
        val coverRel = dto.relationships?.firstOrNull { it.type == "cover_art" }
        val fileName = coverRel?.attributes?.fileName
        return if (!fileName.isNullOrBlank()) {
            "$DEFAULT_COVER_CDN/${dto.id}/$fileName.256.jpg"
        } else {
            "https://placehold.co/300x450/2b2d42/ffffff.png?text=Manga"
        }
    }

    /**
     * Extracts author name from relationships.
     */
    fun extractAuthor(dto: MangaDexMangaDto): String {
        val authorRel = dto.relationships?.firstOrNull { it.type == "author" }
        return authorRel?.attributes?.name ?: ""
    }

    /**
     * Extracts genres / tags.
     */
    fun extractGenres(dto: MangaDexMangaDto): List<String> {
        val list = mutableListOf<String>()
        dto.attributes?.publicationDemographic?.takeIf { it.isNotBlank() }?.let { demo ->
            list.add(demo.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() })
        }
        val tags = dto.attributes?.tags.orEmpty()
        for (tag in tags) {
            val tagName = tag.attributes?.name?.get("en")
            if (!tagName.isNullOrBlank()) {
                list.add(tagName)
            }
        }
        return list
    }

    /**
     * Determines whether content is NSFW/adult based on MangaDex content rating.
     */
    fun isNsfw(dto: MangaDexMangaDto): Boolean {
        val rating = dto.attributes?.contentRating?.lowercase() ?: return false
        return rating == "erotica" || rating == "pornographic"
    }

    // =========================================================================
    // Manga API -> Room Database Entity (Manga / MangaEntity)
    // =========================================================================

    /**
     * Maps a MangaDex API DTO directly to the standard Room database [MangaEntity] ([Manga]).
     */
    fun toMangaEntity(
        dto: MangaDexMangaDto,
        baseUrl: String = DEFAULT_BASE_API,
        sourceId: String = "mangadex",
        isFavorite: Boolean = false
    ): MangaEntity {
        val mangaUrl = if (dto.id.startsWith("http")) dto.id else "$baseUrl/manga/${dto.id}"
        val genres = extractGenres(dto)
        return MangaEntity(
            url = mangaUrl,
            id = dto.id,
            title = extractTitle(dto),
            coverUrl = extractCoverUrl(dto),
            description = extractDescription(dto),
            sourceId = sourceId,
            status = dto.attributes?.status ?: "Unknown",
            originalLanguage = dto.attributes?.originalLanguage.orEmpty(),
            year = dto.attributes?.year,
            genres = genres.joinToString(", "),
            isFavorite = isFavorite,
            isNsfw = isNsfw(dto),
            addedTimestamp = System.currentTimeMillis()
        )
    }

    // =========================================================================
    // Manga API -> Domain Model (MangaModel)
    // =========================================================================

    /**
     * Maps a MangaDex API DTO to the domain [MangaModel].
     */
    fun toMangaModel(
        dto: MangaDexMangaDto,
        baseUrl: String = DEFAULT_BASE_API,
        sourceId: String = "mangadex"
    ): MangaModel {
        val mangaUrl = if (dto.id.startsWith("http")) dto.id else "$baseUrl/manga/${dto.id}"
        return MangaModel(
            url = mangaUrl,
            id = dto.id,
            title = extractTitle(dto),
            coverUrl = extractCoverUrl(dto),
            description = extractDescription(dto),
            genres = extractGenres(dto),
            isNsfw = isNsfw(dto),
            sourceId = sourceId,
            author = extractAuthor(dto),
            status = dto.attributes?.status ?: "Unknown",
            originalLanguage = dto.attributes?.originalLanguage.orEmpty(),
            year = dto.attributes?.year
        )
    }

    // =========================================================================
    // Chapter API -> Room Database Entity (Chapter / ChapterEntity)
    // =========================================================================

    /**
     * Maps a MangaDex Chapter DTO directly to the standard Room database [ChapterEntity] ([Chapter]).
     */
    fun toChapterEntity(
        dto: MangaDexChapterDto,
        mangaUrl: String,
        baseUrl: String = DEFAULT_BASE_API,
        fallbackNumber: Float = 1.0f
    ): ChapterEntity {
        val attr = dto.attributes
        val chNumStr = attr?.chapter ?: fallbackNumber.toString()
        val num = chNumStr.toFloatOrNull() ?: fallbackNumber
        val chapterTitle = attr?.title.orEmpty()
        val lang = attr?.translatedLanguage?.lowercase() ?: ""
        val langFlag = when (lang) {
            "pt-br", "pt", "" -> ""
            "en" -> " 🇺🇸"
            "es", "es-la" -> " 🇪🇸"
            else -> " [${lang.uppercase()}]"
        }
        val displayTitle = when {
            chapterTitle.isNotBlank() -> "Cap. $chNumStr - $chapterTitle$langFlag"
            else -> "Capítulo $chNumStr$langFlag"
        }

        val chapterUrl = "$baseUrl/chapter/${dto.id}"
        val dateUpload = attr?.publishAt?.take(10).orEmpty()
        val totalPages = attr?.pages ?: 0

        return ChapterEntity(
            id = dto.id,
            mangaUrl = mangaUrl,
            title = displayTitle,
            chapterUrl = chapterUrl,
            chapterNumber = num,
            dateUpload = dateUpload,
            isRead = false,
            lastPageRead = 0,
            totalPages = totalPages,
            language = lang,
            fetchedTimestamp = System.currentTimeMillis()
        )
    }

    // =========================================================================
    // Chapter API -> Domain Model (ChapterModel)
    // =========================================================================

    /**
     * Maps a MangaDex Chapter DTO to the domain [ChapterModel].
     */
    fun toChapterModel(
        dto: MangaDexChapterDto,
        baseUrl: String = DEFAULT_BASE_API,
        fallbackNumber: Float = 1.0f
    ): ChapterModel {
        val entity = toChapterEntity(dto, "", baseUrl, fallbackNumber)
        return ChapterModel(
            id = entity.id,
            title = entity.title,
            chapterUrl = entity.chapterUrl,
            chapterNumber = entity.chapterNumber,
            dateUpload = entity.dateUpload,
            language = entity.language
        )
    }

    // =========================================================================
    // Entity <-> Domain Model Conversions
    // =========================================================================

    fun MangaModel.toEntity(isFavorite: Boolean = false): MangaEntity {
        return MangaEntity(
            url = url,
            id = id,
            title = title,
            coverUrl = coverUrl,
            description = description,
            sourceId = sourceId,
            genres = genres.joinToString(", "),
            year = year,
            originalLanguage = originalLanguage,
            status = status,
            isFavorite = isFavorite,
            isNsfw = isNsfw,
            addedTimestamp = System.currentTimeMillis()
        )
    }

    fun MangaEntity.toModel(): MangaModel {
        val genreList = genres.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return MangaModel(
            url = url,
            id = id,
            title = title,
            coverUrl = coverUrl,
            description = description,
            genres = genreList,
            isNsfw = isNsfw,
            sourceId = sourceId,
            author = "",
            year = year,
            originalLanguage = originalLanguage,
            status = status
        )
    }

    fun ChapterModel.toEntity(mangaUrl: String): ChapterEntity {
        return ChapterEntity(
            id = id,
            mangaUrl = mangaUrl,
            title = title,
            chapterUrl = chapterUrl,
            chapterNumber = chapterNumber,
            dateUpload = dateUpload,
            language = language,
            isRead = false,
            lastPageRead = 0,
            totalPages = 0,
            fetchedTimestamp = System.currentTimeMillis()
        )
    }

    fun ChapterEntity.toModel(): ChapterModel {
        return ChapterModel(
            id = id,
            title = title,
            chapterUrl = chapterUrl,
            chapterNumber = chapterNumber,
            dateUpload = dateUpload,
            language = language
        )
    }
}

// Convenient Extension Functions
fun MangaDexMangaDto.toManga(baseUrl: String = "https://api.mangadex.org", sourceId: String = "mangadex", isFavorite: Boolean = false): Manga =
    MangaDexMapper.toMangaEntity(this, baseUrl, sourceId, isFavorite)

fun MangaDexMangaDto.toMangaModel(baseUrl: String = "https://api.mangadex.org", sourceId: String = "mangadex"): MangaModel =
    MangaDexMapper.toMangaModel(this, baseUrl, sourceId)

fun MangaDexChapterDto.toChapter(mangaUrl: String, baseUrl: String = "https://api.mangadex.org", fallbackNumber: Float = 1.0f): Chapter =
    MangaDexMapper.toChapterEntity(this, mangaUrl, baseUrl, fallbackNumber)

fun MangaDexChapterDto.toChapterModel(baseUrl: String = "https://api.mangadex.org", fallbackNumber: Float = 1.0f): ChapterModel =
    MangaDexMapper.toChapterModel(this, baseUrl, fallbackNumber)

fun MangaModel.toMangaEntity(isFavorite: Boolean = false): MangaEntity =
    with(MangaDexMapper) { toEntity(isFavorite) }

fun MangaEntity.toDomainModel(): MangaModel =
    with(MangaDexMapper) { toModel() }

fun ChapterModel.toChapterEntity(mangaUrl: String): ChapterEntity =
    with(MangaDexMapper) { toEntity(mangaUrl) }

fun ChapterEntity.toDomainModel(): ChapterModel =
    with(MangaDexMapper) { toModel() }
