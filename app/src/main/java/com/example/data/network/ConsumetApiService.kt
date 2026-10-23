package com.example.data.network

import com.example.data.network.model.ConsumetMangaDetailsResponse
import com.example.data.network.model.ConsumetPageDto
import com.example.data.network.model.ConsumetSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API Service for interacting with the public Consumet Manga API endpoints.
 */
interface ConsumetApiService {

    @GET("manga/{provider}/{query}")
    suspend fun searchManga(
        @Path("provider") provider: String = "mangakakalot",
        @Path("query") query: String,
        @Query("page") page: Int = 1
    ): ConsumetSearchResponse

    @GET("manga/{provider}/info")
    suspend fun getMangaInfo(
        @Path("provider") provider: String = "mangakakalot",
        @Query("id") id: String
    ): ConsumetMangaDetailsResponse

    @GET("manga/{provider}/read")
    suspend fun getChapterPages(
        @Path("provider") provider: String = "mangakakalot",
        @Query("chapterId") chapterId: String
    ): List<ConsumetPageDto>
}
