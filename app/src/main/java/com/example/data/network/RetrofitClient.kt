package com.example.data.network

import com.example.data.network.model.LenientStringMapAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit Client configured with [JsoupConverterFactory] for HTML web scraping
 * and [MoshiConverterFactory] for MangaDex REST API integration.
 */
object RetrofitClient {

    private const val DEFAULT_BASE_URL = "https://manga-pt-example.com/"
    const val MANGADEX_BASE_URL = "https://api.mangadex.org/"

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(LenientStringMapAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(JsoupConverterFactory.create(DEFAULT_BASE_URL))
            .build()
    }

    val apiService: RemoteMangaApiService by lazy {
        retrofit.create(RemoteMangaApiService::class.java)
    }

    val mangaDexRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(MANGADEX_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val mangaDexApiService: MangaDexApiService by lazy {
        mangaDexRetrofit.create(MangaDexApiService::class.java)
    }

    const val CONSUMET_BASE_URL = "https://api.consumet.org/"
    const val COMICK_BASE_URL = "https://api.comick.fun/"

    val consumetRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(CONSUMET_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val consumetApiService: ConsumetApiService by lazy {
        consumetRetrofit.create(ConsumetApiService::class.java)
    }

    val comickRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(COMICK_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val comickApiService: ComickApiService by lazy {
        comickRetrofit.create(ComickApiService::class.java)
    }

    const val ANILIST_BASE_URL = "https://graphql.anilist.co/"
    const val JIKAN_BASE_URL = "https://api.jikan.moe/v4/"

    val aniListRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ANILIST_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val aniListApiService: AniListApiService by lazy {
        aniListRetrofit.create(AniListApiService::class.java)
    }

    val jikanRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(JIKAN_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val jikanApiService: JikanApiService by lazy {
        jikanRetrofit.create(JikanApiService::class.java)
    }

    fun createServiceForBaseUrl(baseUrl: String): RemoteMangaApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(JsoupConverterFactory.create(formattedUrl))
            .build()
            .create(RemoteMangaApiService::class.java)
    }

    fun createMangaDexServiceForBaseUrl(baseUrl: String): MangaDexApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MangaDexApiService::class.java)
    }
}
