package com.example.source

import com.example.data.network.ConsumetApiService
import com.example.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [MangaSource] communicating with the public Consumet Manga API.
 * Aggregates free public manga providers (such as MangaKakalot and MangaDex).
 */
class ConsumetMangaSource(
    private val apiService: ConsumetApiService = RetrofitClient.consumetApiService,
    private val defaultProvider: String = "mangakakalot"
) : MangaSource {

    override val id: String = "consumet"
    override val name: String = "Consumet (Agregador)"
    override val baseUrl: String = "https://api.consumet.org"
    override val isNsfw: Boolean = false
    override val lang: String = "EN / Global"

    override suspend fun getPopularManga(): List<MangaModel> = getPopularManga("en")

    override suspend fun getPopularManga(language: String): List<MangaModel> = withContext(Dispatchers.IO) {
        try {
            // Searches popular titles through aggregator
            val response = apiService.searchManga(provider = defaultProvider, query = "popular", page = 1)
            val items = response.results.orEmpty()
            if (items.isNotEmpty()) {
                items.map { item ->
                    MangaModel(
                        url = "$baseUrl/manga/$defaultProvider/info?id=${item.id}",
                        id = item.id,
                        title = item.title ?: "Sem título",
                        coverUrl = item.image?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/300/450",
                        description = item.description.orEmpty(),
                        genres = listOf("Popular", "Manga"),
                        isNsfw = false,
                        sourceId = id,
                        status = item.status ?: "Ongoing"
                    )
                }
            } else {
                FALLBACK_POPULAR_MANGAS
            }
        } catch (e: Exception) {
            FALLBACK_POPULAR_MANGAS
        }
    }

    override suspend fun searchManga(query: String): List<MangaModel> = searchManga(query, "en")

    override suspend fun searchManga(query: String, language: String): List<MangaModel> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            val response = apiService.searchManga(provider = defaultProvider, query = trimmed, page = 1)
            val items = response.results.orEmpty()
            items.map { item ->
                MangaModel(
                    url = "$baseUrl/manga/$defaultProvider/info?id=${item.id}",
                    id = item.id,
                    title = item.title ?: trimmed,
                    coverUrl = item.image?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/300/450",
                    description = item.description.orEmpty(),
                    genres = listOf("Manga"),
                    isNsfw = false,
                    sourceId = id,
                    status = item.status ?: "Ongoing"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMangaDetails(mangaUrl: String): MangaModel = withContext(Dispatchers.IO) {
        val rawId = extractMangaId(mangaUrl)
        try {
            val response = apiService.getMangaInfo(provider = defaultProvider, id = rawId)
            MangaModel(
                url = mangaUrl,
                id = response.id,
                title = response.title ?: "Manga",
                coverUrl = response.image?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/300/450",
                description = response.description.orEmpty(),
                genres = response.genres ?: listOf("Manga"),
                isNsfw = false,
                sourceId = id,
                status = response.status ?: "Ongoing"
            )
        } catch (e: Exception) {
            MangaModel(
                url = mangaUrl,
                id = rawId,
                title = "Manga ($rawId)",
                coverUrl = "https://picsum.photos/300/450",
                description = "Detalhes disponíveis via leitor.",
                genres = listOf("Manga"),
                sourceId = id
            )
        }
    }

    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, "en")

    override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        if (!ChapterLanguage.matches("en", language)) return@withContext emptyList()
        val rawId = extractMangaId(mangaUrl)
        try {
            val response = apiService.getMangaInfo(provider = defaultProvider, id = rawId)
            val chapters = response.chapters.orEmpty()
            chapters.mapIndexed { index, ch ->
                val num = parseChapterNumber(ch.title, index + 1)
                ChapterModel(
                    id = ch.id,
                    title = ch.title ?: "Capítulo $num",
                    chapterUrl = ch.id,
                    chapterNumber = num,
                    dateUpload = ch.releaseDate.orEmpty(),
                    language = "en"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPages(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val pages = apiService.getChapterPages(provider = defaultProvider, chapterId = chapterUrl)
            pages.mapNotNull { it.img }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractMangaId(url: String): String {
        return when {
            url.contains("id=") -> url.substringAfter("id=").substringBefore("&").trim()
            url.contains("/") -> url.substringAfterLast("/").trim()
            else -> url.trim()
        }
    }

    private fun parseChapterNumber(title: String?, fallbackIndex: Int): Float {
        if (title.isNullOrBlank()) return fallbackIndex.toFloat()
        val regex = Regex("""(?i)(?:cap[ií]tulo|chapter|ch\.?)\s*(\d+(?:\.\d+)?)""")
        val match = regex.find(title)
        if (match != null) {
            return match.groupValues[1].toFloatOrNull() ?: fallbackIndex.toFloat()
        }
        val numberRegex = Regex("""(\d+(?:\.\d+)?)""")
        val numMatch = numberRegex.find(title)
        return numMatch?.groupValues?.get(1)?.toFloatOrNull() ?: fallbackIndex.toFloat()
    }

    companion object {
        private val FALLBACK_POPULAR_MANGAS = listOf(
            MangaModel(
                url = "https://api.consumet.org/manga/mangakakalot/info?id=manga-ox991523",
                id = "manga-ox991523",
                title = "Solo Leveling: Ragnarok",
                coverUrl = "https://picsum.photos/id/1025/300/450",
                description = "The sequel to Solo Leveling following Sung Suho.",
                genres = listOf("Ação", "Fantasia", "Webtoon"),
                sourceId = "consumet",
                status = "Ongoing"
            ),
            MangaModel(
                url = "https://api.consumet.org/manga/mangakakalot/info?id=manga-ng952689",
                id = "manga-ng952689",
                title = "The Beginning After The End",
                coverUrl = "https://picsum.photos/id/1062/300/450",
                description = "King Grey has unrivaled strength, wealth, and prestige in a world governed by martial ability.",
                genres = listOf("Ação", "Aventura", "Reencarnação"),
                sourceId = "consumet",
                status = "Ongoing"
            ),
            MangaModel(
                url = "https://api.consumet.org/manga/mangakakalot/info?id=manga-aa951409",
                id = "manga-aa951409",
                title = "Omniscient Reader's Viewpoint",
                coverUrl = "https://picsum.photos/id/1024/300/450",
                description = "Kim Dokja does not consider himself the protagonist of his own life.",
                genres = listOf("Ação", "Fantasia", "Sobrenatural"),
                sourceId = "consumet",
                status = "Ongoing"
            )
        )
    }
}
