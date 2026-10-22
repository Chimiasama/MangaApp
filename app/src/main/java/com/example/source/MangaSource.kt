package com.example.source

interface MangaSource {
    val id: String
    val name: String
    val baseUrl: String
    val isNsfw: Boolean
    val lang: String

    suspend fun getPopularManga(): List<MangaModel>
    suspend fun getPopularManga(language: String): List<MangaModel> = getPopularManga()

    suspend fun searchManga(query: String): List<MangaModel>
    suspend fun searchManga(query: String, language: String): List<MangaModel> = searchManga(query)

    suspend fun getMangaDetails(mangaUrl: String): MangaModel

    suspend fun getChapters(mangaUrl: String): List<ChapterModel>
    suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> = getChapters(mangaUrl)

    suspend fun getPages(chapterUrl: String): List<String>
}
