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

    suspend fun searchManga(query: String, language: String, category: String): List<MangaModel> {
        val candidates = if (query.isBlank()) getPopularManga(language) else searchManga(query, language)
        return candidates.filter { MangaCategories.matches(it.genres, category) }
    }

    suspend fun searchManga(query: String, language: String, category: String, period: PublicationPeriod?): List<MangaModel> =
        searchManga(query, language, category).filter { period == null || period.matches(it.publicationYear) }

    suspend fun getMangaDetails(mangaUrl: String): MangaModel

    suspend fun getChapters(mangaUrl: String): List<ChapterModel>
    suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> = getChapters(mangaUrl)

    suspend fun getPages(chapterUrl: String): List<String>
}
