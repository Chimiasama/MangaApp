package com.example.source

import com.example.data.network.ComickApiService
import com.example.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [MangaSource] communicating with the Comick REST API (https://api.comick.fun).
 * Features a rich collection of manhwas, webtoons, and mangas with multi-language scanlation support (PT-BR, EN, ES).
 */
class ComickSource(
    private val apiService: ComickApiService = RetrofitClient.comickApiService
) : MangaSource {

    override val id: String = "comick"
    override val name: String = "Comick (Webtoons & Manhuas)"
    override val baseUrl: String = "https://api.comick.fun"
    override val isNsfw: Boolean = false
    override val lang: String = "PT-BR / Multi"

    override suspend fun getPopularManga(): List<MangaModel> = getPopularManga("pt-br")

    override suspend fun getPopularManga(language: String): List<MangaModel> = withContext(Dispatchers.IO) {
        try {
            val searchResults = apiService.searchManga(sort = "trending", limit = 40)
            if (searchResults.isNotEmpty()) {
                searchResults.map { item ->
                    val slug = item.slug ?: item.hid ?: ""
                    MangaModel(
                        url = "https://comick.io/comic/$slug",
                        id = item.hid ?: slug,
                        title = item.title ?: "Sem título",
                        coverUrl = item.resolveCoverUrl(),
                        description = item.desc.orEmpty(),
                        genres = item.genres ?: listOf("Webtoon"),
                        isNsfw = item.contentRating?.lowercase() in listOf("erotica", "pornographic"),
                        sourceId = id,
                        status = "Ongoing"
                    )
                }
            } else {
                FALLBACK_COMICK_MANGAS
            }
        } catch (e: Exception) {
            FALLBACK_COMICK_MANGAS
        }
    }

    override suspend fun searchManga(query: String): List<MangaModel> = searchManga(query, "pt-br")

    override suspend fun searchManga(query: String, language: String): List<MangaModel> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            val results = apiService.searchManga(query = trimmed, limit = 30)
            results.map { item ->
                val slug = item.slug ?: item.hid ?: ""
                MangaModel(
                    url = "https://comick.io/comic/$slug",
                    id = item.hid ?: slug,
                    title = item.title ?: trimmed,
                    coverUrl = item.resolveCoverUrl(),
                    description = item.desc.orEmpty(),
                    genres = item.genres ?: listOf("Webtoon"),
                    isNsfw = item.contentRating?.lowercase() in listOf("erotica", "pornographic"),
                    sourceId = id,
                    status = "Ongoing"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMangaDetails(mangaUrl: String): MangaModel = withContext(Dispatchers.IO) {
        val slug = extractSlug(mangaUrl)
        try {
            val response = apiService.getComicDetails(slug)
            val comic = response.comic
            val hid = comic?.hid ?: slug
            val title = comic?.title ?: "Manga"
            val cover = comic?.resolveCoverUrl() ?: "https://picsum.photos/300/450"
            val desc = comic?.desc.orEmpty()
            val genres = comic?.extractGenres() ?: listOf("Webtoon")
            val authors = response.authors?.mapNotNull { it.name }?.joinToString(", ").orEmpty()

            MangaModel(
                url = mangaUrl,
                id = hid,
                title = title,
                coverUrl = cover,
                description = desc,
                genres = genres,
                isNsfw = false,
                sourceId = id,
                author = authors,
                status = if (comic?.status == 1) "Ongoing" else "Completed"
            )
        } catch (e: Exception) {
            MangaModel(
                url = mangaUrl,
                id = slug,
                title = "Comick Comic ($slug)",
                coverUrl = "https://picsum.photos/300/450",
                description = "Detalhes disponíveis através do leitor.",
                genres = listOf("Webtoon"),
                sourceId = id
            )
        }
    }

    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, "pt-br")

    override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        val slug = extractSlug(mangaUrl)
        try {
            // First retrieve comic details to obtain hid
            val detailsResponse = apiService.getComicDetails(slug)
            val hid = detailsResponse.comic?.hid ?: slug

            val targetLang = when (language.lowercase()) {
                "pt-br", "pt" -> "pt-br"
                "en" -> "en"
                "es" -> "es"
                else -> null
            }

            var page = 1
            var hasMore = true
            val rawChapters = mutableListOf<com.example.data.network.model.ComickChapterDto>()
            val pageSize = 300

            while (hasMore) {
                val chapterResponse = apiService.getComicChapters(hid = hid, lang = targetLang, limit = pageSize, page = page)
                val pageChapters = chapterResponse.chapters.orEmpty()
                if (pageChapters.isEmpty()) {
                    hasMore = false
                } else {
                    rawChapters.addAll(pageChapters)
                    if (pageChapters.size < pageSize) {
                        hasMore = false
                    } else {
                        page++
                    }
                }
            }

            // If selected language had no chapters and wasn't English, fallback to English or all chapters
            if (rawChapters.isEmpty() && targetLang != null) {
                page = 1
                hasMore = true
                while (hasMore) {
                    val chapterResponse = apiService.getComicChapters(hid = hid, lang = null, limit = pageSize, page = page)
                    val pageChapters = chapterResponse.chapters.orEmpty()
                    if (pageChapters.isEmpty()) {
                        hasMore = false
                    } else {
                        rawChapters.addAll(pageChapters)
                        if (pageChapters.size < pageSize) {
                            hasMore = false
                        } else {
                            page++
                        }
                    }
                }
            }

            rawChapters.mapIndexed { index, ch ->
                val chapNum = ch.chap?.toFloatOrNull() ?: (index + 1).toFloat()
                val title = buildString {
                    append("Cap. ")
                    append(ch.chap ?: "$chapNum")
                    if (!ch.title.isNullOrBlank()) {
                        append(" - ")
                        append(ch.title)
                    }
                    if (!ch.lang.isNullOrBlank()) {
                        append(" [${ch.lang.uppercase()}]")
                    }
                }
                ChapterModel(
                    id = ch.hid ?: "ch-$index",
                    title = title,
                    chapterUrl = "https://comick.io/chapter/${ch.hid}",
                    chapterNumber = chapNum,
                    dateUpload = ch.createdAt.orEmpty()
                )
            }
            .distinctBy { it.chapterNumber }
            .sortedByDescending { it.chapterNumber }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPages(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
        val hid = extractSlug(chapterUrl)
        try {
            val response = apiService.getChapterDetail(hid)
            val images = response.chapter?.images.orEmpty()
            images.map { it.resolveImageUrl() }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractSlug(url: String): String {
        return when {
            url.contains("/comic/") -> url.substringAfter("/comic/").substringBefore("/").substringBefore("?").trim()
            url.contains("/chapter/") -> url.substringAfter("/chapter/").substringBefore("/").substringBefore("?").trim()
            url.contains("/") -> url.substringAfterLast("/").substringBefore("?").trim()
            else -> url.trim()
        }
    }

    companion object {
        private val FALLBACK_COMICK_MANGAS = listOf(
            MangaModel(
                url = "https://comick.io/comic/00-solo-leveling",
                id = "solo-leveling",
                title = "Solo Leveling",
                coverUrl = "https://picsum.photos/id/1025/300/450",
                description = "10 years ago, after 'the Gate' that connected the real world with the monster world opened, some ordinary people received powers.",
                genres = listOf("Ação", "Fantasia", "Manhwa", "Webtoon"),
                sourceId = "comick",
                status = "Completed"
            ),
            MangaModel(
                url = "https://comick.io/comic/01-nano-machine",
                id = "nano-machine",
                title = "Nano Machine",
                coverUrl = "https://picsum.photos/id/1062/300/450",
                description = "Cheon Yeo-Woon, an illegitimate son of the Demonic Cult leader, is injected with a nanomachine from his descendant in the future.",
                genres = listOf("Artes Marciais", "Ação", "Sci-Fi"),
                sourceId = "comick",
                status = "Ongoing"
            ),
            MangaModel(
                url = "https://comick.io/comic/02-return-of-the-mount-hua-sect",
                id = "return-of-the-mount-hua-sect",
                title = "Return of the Mount Hua Sect",
                coverUrl = "https://picsum.photos/id/1024/300/450",
                description = "Chung Myung wakes up 100 years in the future to find his beloved Mount Hua Sect in ruins.",
                genres = listOf("Comédia", "Artes Marciais", "Reencarnação"),
                sourceId = "comick",
                status = "Ongoing"
            )
        )
    }
}
