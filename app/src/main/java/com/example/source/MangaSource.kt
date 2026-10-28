package com.example.source

interface MangaSource {
    val id: String
    val name: String
    val baseUrl: String
    val isNsfw: Boolean
    fun supportsLanguage(language: String): Boolean = true
    val canAggregateChapters: Boolean get() = true
    val lang: String

    suspend fun getPopularManga(): List<MangaModel>
    suspend fun getPopularManga(language: String): List<MangaModel> = getPopularManga()

    suspend fun searchManga(query: String): List<MangaModel>
    suspend fun searchManga(query: String, language: String): List<MangaModel> = searchManga(query)

    /** A blank title browses the catalog; publication filters must not depend on popular titles. */
    suspend fun getCatalogManga(query: String, language: String, yearRange: String): List<MangaModel> =
        if (query.isBlank()) getPopularManga(language) else searchManga(query, language)

    /** Sources without a catalog API can enrich missing classification from their detail pages. */
    suspend fun getFilteredCatalog(query: String, language: String, yearRange: String, filters: CatalogFilters): List<MangaModel> {
        val candidates = getCatalogManga(query, language, yearRange)
        if (!filters.isActive) return candidates
        return candidates.map { manga ->
            try {
                val details = getMangaDetails(manga.url)
                details.copy(id = manga.id, url = manga.url, sourceId = manga.sourceId,
                    year = details.year ?: manga.year, coverUrl = details.coverUrl.ifBlank { manga.coverUrl },
                    genres = details.genres.ifEmpty { manga.genres }, originalLanguage = details.originalLanguage.ifBlank { manga.originalLanguage },
                    status = details.status.takeUnless { it.isBlank() || it.equals("Unknown", true) } ?: manga.status)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { manga }
        }
    }

    val filterSupportNote: String get() = "A classificação depende dos dados disponíveis nesta fonte. Dados ausentes não são deduzidos do título."

    suspend fun getMangaDetails(mangaUrl: String): MangaModel

    suspend fun getChapters(mangaUrl: String): List<ChapterModel>
    suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> = getChapters(mangaUrl)

    suspend fun getPages(chapterUrl: String): List<String>
}
