package com.example

import android.graphics.BitmapFactory
import com.example.data.network.MangaDexApiService
import com.example.data.network.MangaDexCallFactory
import com.example.data.network.RetrofitClient
import com.example.source.MangaDexSource
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

/** Explicit remote check. The cloud proxy is test infrastructure, never an app setting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveMangaDexReadFlowCheck {
    @Test fun realCatalogCoversChaptersAndSecondPage() = runBlocking {
        val client = RetrofitClient.okHttpClient.newBuilder().apply {
            System.getenv("HTTPS_PROXY")?.takeIf { it.isNotBlank() }?.let {
                val uri = URI(it)
                proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port)))
            }
        }.build()
        val calls = MangaDexCallFactory(client)
        val api = Retrofit.Builder().baseUrl(RetrofitClient.MANGADEX_BASE_URL).callFactory(calls)
            .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi)).build()
            .create(MangaDexApiService::class.java)
        val source = MangaDexSource(apiService = api)
        fun image(url: String) {
            calls.newCall(Request.Builder().url(url).header("User-Agent", com.example.source.SourceHttp.USER_AGENT).build()).execute().use { response ->
                assertEquals("Image: $url", 200, response.code)
                val bytes = response.body!!.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                assertNotNull("Decodable image: $url", bitmap)
                bitmap!!.recycle()
            }
        }
        try {
            val popular = source.getPopularManga("pt-br")
            assertTrue(popular.isNotEmpty())
            assertFalse(popular.any { MangaDexSource.isLegacyStarterRecord(it.sourceId, it.coverUrl, it.originalLanguage, it.status) })
            popular.take(3).forEach { image(it.coverUrl) }
            val manga = source.getMangaDetails("https://api.mangadex.org/manga/af0527c1-8734-4498-b128-7090340bc10d")
            image(manga.coverUrl)
            val chapters = source.getChapters(manga.url, "pt-br")
            assertTrue("Real Portuguese chapter feed", chapters.isNotEmpty())
            val chapter = chapters.first { it.id == "cb8e39ff-fcdc-495d-807a-e4cd56c09adb" }
            val pages = source.getPages(chapter.chapterUrl)
            assertEquals(14, pages.size)
            image(pages[1])
            println("LIVE MANGADEX: catalog=${popular.size}; covers=4; ptBR chapters=${chapters.size}; chapter471 pages=${pages.size}; page2 decoded")
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
