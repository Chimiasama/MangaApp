package com.example.data.network

import org.jsoup.nodes.Document
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * Retrofit API interface for fetching remote web pages parsed into Jsoup [Document] objects.
 */
interface RemoteMangaApiService {

    @GET
    suspend fun getPageDocument(
        @Url url: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    ): Document
}
