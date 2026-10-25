package com.example.data.network

import com.example.data.network.model.MangaDexAtHomeResponse
import com.example.data.network.model.MangaDexChapterFeedResponse
import com.example.data.network.model.MangaDexMangaDetailsResponse
import com.example.data.network.model.MangaDexMangaListResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface defining the MangaDex REST API v5 endpoints.
 */
interface MangaDexApiService {

    @GET("manga")
    suspend fun getMangaList(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("title") title: String? = null,
        @Query("availableTranslatedLanguage[]") translatedLanguages: List<String>? = null,
        @Query("order[followedCount]") orderFollowedCount: String? = null,
        @Query("order[relevance]") orderRelevance: String? = null,
        @Query("includes[]") includes: List<String> = listOf("cover_art", "author"),
        @Query("contentRating[]") contentRatings: List<String> = listOf("safe", "suggestive", "erotica", "pornographic"),
        @Header("User-Agent") userAgent: String = "MangaReaderApp/1.1.0"
    ): MangaDexMangaListResponse

    @GET("manga")
    suspend fun getFilteredMangaList(
        @Query("title") title: String? = null,
        @Query("publicationDemographic[]") demographics: List<String>? = null,
        @Query("includedTags[]") includedTags: List<String>? = null,
        @Query("availableTranslatedLanguage[]") translatedLanguages: List<String>? = null,
        @Query("limit") limit: Int = 100,
        @Query("order[followedCount]") order: String = "desc",
        @Query("includes[]") includes: List<String> = listOf("cover_art", "author"),
        @Query("contentRating[]") ratings: List<String> = listOf("safe", "suggestive")
    ): MangaDexMangaListResponse

    @GET("manga/tag")
    suspend fun getMangaTags(): com.example.data.network.model.MangaDexTagListResponse

    @GET("manga/{id}")
    suspend fun getMangaDetails(
        @Path("id") mangaId: String,
        @Query("includes[]") includes: List<String> = listOf("cover_art", "author"),
        @Header("User-Agent") userAgent: String = "MangaReaderApp/1.1.0"
    ): MangaDexMangaDetailsResponse

    @GET("manga/{id}/feed")
    suspend fun getChapterFeed(
        @Path("id") mangaId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("translatedLanguage[]") translatedLanguages: List<String>? = null,
        @Query("order[chapter]") orderChapter: String = "desc",
        @Query("contentRating[]") contentRatings: List<String> = listOf("safe", "suggestive", "erotica", "pornographic"),
        @Header("User-Agent") userAgent: String = "MangaReaderApp/1.1.0"
    ): MangaDexChapterFeedResponse

    @GET("at-home/server/{chapterId}")
    suspend fun getAtHomeServer(
        @Path("chapterId") chapterId: String,
        @Header("User-Agent") userAgent: String = "MangaReaderApp/1.1.0"
    ): MangaDexAtHomeResponse
}
