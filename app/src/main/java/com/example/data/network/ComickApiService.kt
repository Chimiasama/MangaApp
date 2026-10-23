package com.example.data.network

import com.example.data.network.model.ComickChapterDetailResponse
import com.example.data.network.model.ComickChapterListResponse
import com.example.data.network.model.ComickComicDetailsResponse
import com.example.data.network.model.ComickSearchItemDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API Service for Comick (https://api.comick.fun).
 * Headers include a custom User-Agent to avoid rate limiting and Cloudflare challenges.
 */
interface ComickApiService {

    @Headers("User-Agent: MangaApp/1.0 (Android; chimiasama@github)")
    @GET("v1.0/search")
    suspend fun searchManga(
        @Query("q") query: String? = null,
        @Query("sort") sort: String? = "trending",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): List<ComickSearchItemDto>

    @Headers("User-Agent: MangaApp/1.0 (Android; chimiasama@github)")
    @GET("comic/{slug}")
    suspend fun getComicDetails(
        @Path("slug") slug: String
    ): ComickComicDetailsResponse

    @Headers("User-Agent: MangaApp/1.0 (Android; chimiasama@github)")
    @GET("comic/{hid}/chapters")
    suspend fun getComicChapters(
        @Path("hid") hid: String,
        @Query("lang") lang: String? = "pt-br",
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 1
    ): ComickChapterListResponse

    @Headers("User-Agent: MangaApp/1.0 (Android; chimiasama@github)")
    @GET("chapter/{hid}")
    suspend fun getChapterDetail(
        @Path("hid") hid: String
    ): ComickChapterDetailResponse
}
