package com.example.source

import com.example.data.local.Chapter
import com.example.data.local.ChapterEntity
import com.example.data.local.Manga
import com.example.data.local.MangaEntity
import com.example.data.mapping.MangaDexMapper
import com.example.data.mapping.toChapter
import com.example.data.mapping.toChapterModel
import com.example.data.mapping.toManga
import com.example.data.mapping.toMangaModel
import com.example.data.network.MangaDexApiService
import com.example.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [MangaSource] communicating with the MangaDex REST API v5
 * using Retrofit and converting responses via [MangaDexMapper] into standard Room database
 * entities ([MangaEntity], [ChapterEntity]) and domain models ([MangaModel], [ChapterModel]).
 */
class MangaDexSource(
    private val apiService: MangaDexApiService = RetrofitClient.mangaDexApiService,
    private val mapper: MangaDexMapper = MangaDexMapper
) : MangaSource {

    override val id: String = "mangadex"
    override val name: String = "MangaDex (API Oficial)"
    override val baseUrl: String = "https://api.mangadex.org"
    override val isNsfw: Boolean = false
    override val lang: String = "pt-BR"

    // =========================================================================
    // MangaSource Contract Implementations
    // =========================================================================

    override suspend fun getPopularManga(): List<MangaModel> = withContext(Dispatchers.IO) {
        try {
            // First try fetching manga with translated chapters available in pt-br, sorted by popularity
            val responsePt = apiService.getMangaList(
                limit = 25,
                translatedLanguages = listOf("pt-br"),
                orderFollowedCount = "desc",
                includes = listOf("cover_art", "author")
            )

            val dtos = responsePt.data.orEmpty()
            if (dtos.isNotEmpty()) {
                return@withContext dtos.map { it.toMangaModel(baseUrl, id) }
            }

            // Fallback to top followed mangas globally
            val responseGlobal = apiService.getMangaList(
                limit = 25,
                orderFollowedCount = "desc",
                includes = listOf("cover_art", "author")
            )
            responseGlobal.data.orEmpty().map { it.toMangaModel(baseUrl, id) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun searchManga(query: String): List<MangaModel> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            // 1. Prioritize searches matching titles with Portuguese translations available
            val responsePt = apiService.getMangaList(
                title = trimmed,
                limit = 25,
                translatedLanguages = listOf("pt-br"),
                includes = listOf("cover_art", "author")
            )

            val ptResults = responsePt.data.orEmpty()
            if (ptResults.isNotEmpty()) {
                return@withContext ptResults.map { it.toMangaModel(baseUrl, id) }
            }

            // 2. Fall back to global title search (allowing English, Japanese, and Romanized titles)
            val responseGlobal = apiService.getMangaList(
                title = trimmed,
                limit = 25,
                includes = listOf("cover_art", "author")
            )
            responseGlobal.data.orEmpty().map { it.toMangaModel(baseUrl, id) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getMangaDetails(mangaUrl: String): MangaModel = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()
        try {
            val response = apiService.getMangaDetails(mangaId, includes = listOf("cover_art", "author"))
            val dto = response.data ?: return@withContext createFallbackDetails(mangaId)
            dto.toMangaModel(baseUrl, id)
        } catch (e: Exception) {
            e.printStackTrace()
            createFallbackDetails(mangaId)
        }
    }

    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()

        try {
            // 1. Try Portuguese translated chapters first
            var chapters = fetchAndFilterChapters(mangaId, listOf("pt-br"))

            // 2. If no Portuguese chapters, try English translations
            if (chapters.isEmpty()) {
                chapters = fetchAndFilterChapters(mangaId, listOf("en"))
            }

            // 3. If still empty, fetch latest chapters in any language
            if (chapters.isEmpty()) {
                chapters = fetchAndFilterChapters(mangaId, null)
            }

            chapters.mapIndexed { index, dto ->
                dto.toChapterModel(baseUrl, (chapters.size - index).toFloat())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getPages(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
        val chapterId = chapterUrl.substringAfterLast("/").trim()
        val pages = mutableListOf<String>()

        try {
            val response = apiService.getAtHomeServer(chapterId)
            val host = response.baseUrl.orEmpty()
            val chapterObj = response.chapter
            val hash = chapterObj?.hash.orEmpty()
            val dataArray = chapterObj?.data.orEmpty()

            if (host.isNotEmpty() && hash.isNotEmpty() && dataArray.isNotEmpty()) {
                for (fileName in dataArray) {
                    pages.add("$host/data/$hash/$fileName")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        pages
    }

    // =========================================================================
    // Direct Room Database Models Mapping API (Manga & Chapter Entities)
    // =========================================================================

    /**
     * Fetches popular mangas mapped directly into the Room database [Manga] ([MangaEntity]) model.
     */
    suspend fun getPopularMangaAsEntities(): List<Manga> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMangaList(
                limit = 25,
                translatedLanguages = listOf("pt-br"),
                orderFollowedCount = "desc",
                includes = listOf("cover_art", "author")
            )
            val dtos = response.data.orEmpty()
            val targetDtos = if (dtos.isNotEmpty()) {
                dtos
            } else {
                apiService.getMangaList(
                    limit = 25,
                    orderFollowedCount = "desc",
                    includes = listOf("cover_art", "author")
                ).data.orEmpty()
            }
            targetDtos.map { it.toManga(baseUrl, id) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Searches mangas mapped directly into the Room database [Manga] ([MangaEntity]) model.
     */
    suspend fun searchMangaAsEntities(query: String): List<Manga> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            val responsePt = apiService.getMangaList(
                title = trimmed,
                limit = 25,
                translatedLanguages = listOf("pt-br"),
                includes = listOf("cover_art", "author")
            )
            val ptResults = responsePt.data.orEmpty()
            val targetDtos = if (ptResults.isNotEmpty()) {
                ptResults
            } else {
                apiService.getMangaList(
                    title = trimmed,
                    limit = 25,
                    includes = listOf("cover_art", "author")
                ).data.orEmpty()
            }
            targetDtos.map { it.toManga(baseUrl, id) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetches details mapped directly into the Room database [Manga] ([MangaEntity]) model.
     */
    suspend fun getMangaDetailsAsEntity(mangaUrl: String): Manga = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()
        try {
            val response = apiService.getMangaDetails(mangaId, includes = listOf("cover_art", "author"))
            val dto = response.data
            dto?.toManga(baseUrl, id) ?: mapper.toMangaEntity(
                dto = com.example.data.network.model.MangaDexMangaDto(id = mangaId),
                baseUrl = baseUrl,
                sourceId = id
            )
        } catch (e: Exception) {
            mapper.toMangaEntity(
                dto = com.example.data.network.model.MangaDexMangaDto(id = mangaId),
                baseUrl = baseUrl,
                sourceId = id
            )
        }
    }

    /**
     * Fetches chapters mapped directly into the Room database [Chapter] ([ChapterEntity]) model.
     */
    suspend fun getChaptersAsEntities(mangaUrl: String): List<Chapter> = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()
        try {
            var rawChapters = fetchAndFilterChapters(mangaId, listOf("pt-br"))
            if (rawChapters.isEmpty()) {
                rawChapters = fetchAndFilterChapters(mangaId, listOf("en"))
            }
            if (rawChapters.isEmpty()) {
                rawChapters = fetchAndFilterChapters(mangaId, null)
            }

            rawChapters.mapIndexed { index, dto ->
                dto.toChapter(
                    mangaUrl = mangaUrl,
                    baseUrl = baseUrl,
                    fallbackNumber = (rawChapters.size - index).toFloat()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private suspend fun fetchAndFilterChapters(
        mangaId: String,
        languages: List<String>?
    ): List<com.example.data.network.model.MangaDexChapterDto> {
        val response = apiService.getChapterFeed(
            mangaId = mangaId,
            limit = 100,
            translatedLanguages = languages,
            orderChapter = "desc"
        )

        return response.data.orEmpty().filter { ch ->
            val attr = ch.attributes
            val externalUrl = attr?.externalUrl
            val isExternal = !externalUrl.isNullOrBlank() && externalUrl != "null"
            val pageCount = attr?.pages ?: 0
            !isExternal && pageCount > 0
        }
    }

    private fun createFallbackDetails(mangaId: String): MangaModel {
        return MangaModel(
            url = "$baseUrl/manga/$mangaId",
            id = mangaId,
            title = "Manga #$mangaId",
            coverUrl = "https://placehold.co/300x450/1e293b/ffffff.png?text=Manga",
            description = "Detalhes carregados via API MangaDex.",
            sourceId = id
        )
    }
}
