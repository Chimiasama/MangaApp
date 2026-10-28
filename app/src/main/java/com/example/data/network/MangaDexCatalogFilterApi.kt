package com.example.data.network

import com.example.data.network.model.MangaDexMangaListResponse
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.Query

/** Catalog filtering is separate from title search and never requires a nonempty title. */
interface MangaDexCatalogFilterApi {
    @GET("manga")
    suspend fun getFilteredCatalog(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("title") title: String? = null,
        @Query("year") year: Int? = null,
        @Query("availableTranslatedLanguage[]") languages: List<String>? = null,
        @Query("includedTags[]") includedTags: List<String>? = null,
        @Query("includedTagsMode") includedMode: String = "AND",
        @Query("excludedTags[]") excludedTags: List<String>? = null,
        @Query("excludedTagsMode") excludedMode: String = "OR",
        @Query("publicationDemographic[]") demographics: List<String>? = null,
        @Query("status[]") statuses: List<String>? = null,
        @Query("originalLanguage[]") originalLanguages: List<String>? = null,
        @Query("includes[]") includes: List<String> = listOf("cover_art", "author"),
        @Query("contentRating[]") ratings: List<String> = listOf("safe", "suggestive"),
        @Query("order[followedCount]") popularity: String = "desc",
        @Header("User-Agent") userAgent: String = "MangaReaderApp/1.1.0"
    ): MangaDexMangaListResponse
}
