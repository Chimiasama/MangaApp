package com.example.data.network

import com.example.data.network.model.JikanRecommendationsResponse
import com.example.data.network.model.JikanSearchMangaResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the public Jikan API v4 (https://api.jikan.moe/v4).
 * Fetches unauthenticated manga metadata and recommendations from MyAnimeList.
 */
interface JikanApiService {

    @GET("manga")
    suspend fun searchManga(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1
    ): JikanSearchMangaResponse

    @GET("manga/{id}/recommendations")
    suspend fun getRecommendations(
        @Path("id") malId: Int
    ): JikanRecommendationsResponse
}
