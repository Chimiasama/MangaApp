package com.example

import com.example.data.mapping.toMangaModel
import com.example.data.network.RetrofitClient
import com.example.data.network.model.MangaDexMangaListResponse
import org.junit.Assert.*
import org.junit.Test
import com.example.data.network.MangaDexApiService
import com.example.source.MangaDexSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class MangaSearchTest {
    @Test fun `current Dragon Ball response decodes using the production Moshi adapters`() {
        val json = javaClass.getResource("/mangadex-dragon-ball.json")!!.readText()
        val response = RetrofitClient.moshi.adapter(MangaDexMangaListResponse::class.java).fromJson(json)!!
        val manga = response.data!!.single().toMangaModel()
        assertEquals("Dragon Ball", manga.title)
        assertEquals("40bc649f-7b49-4645-859e-6cd94136e722", manga.id)
        assertFalse(manga.isNsfw)
        assertFalse(manga.genres.any { it.equals("ecchi", ignoreCase = true) })
        assertTrue(manga.coverUrl.startsWith("https://uploads.mangadex.org/covers/"))
    }
    @Test fun `Dragon Ball is found by the source through the production Retrofit converter`() = runBlocking {
        val source = MangaDexSource(service { 200 to fixture() })
        assertEquals("Dragon Ball", source.searchManga("dragon ball", "pt-br").single().title)
    }
    @Test fun `a missing Portuguese translation falls back to a global Dragon Ball search`() = runBlocking {
        val requests = AtomicInteger()
        val source = MangaDexSource(service {
            if (requests.getAndIncrement() == 0) 200 to "{\"result\":\"ok\",\"data\":[]}" else 200 to fixture()
        })
        assertEquals("Dragon Ball", source.searchManga("dragon ball", "pt-br").single().title)
        assertEquals(2, requests.get())
    }
    @Test fun `failed global search is not reported as no matches even if language request was empty`() = runBlocking {
        val requests = AtomicInteger()
        val source = MangaDexSource(service {
            if (requests.getAndIncrement() == 0) 200 to "{\"result\":\"ok\",\"data\":[]}" else 503 to "{}"
        })
        try {
            source.searchManga("dragon ball", "pt-br")
            fail("An unavailable API must not silently return an empty catalog")
        } catch (expected: IOException) { assertTrue(expected.message!!.contains("MangaDex")) }
    }
    @Test fun `successful empty searches remain distinct from API failure`() = runBlocking {
        val source = MangaDexSource(service { 200 to "{\"result\":\"ok\",\"data\":[]}" })
        assertTrue(source.searchManga("nonexistent-title-987654321", "pt-br").isEmpty())
    }
    @Test fun `popular API failure does not fabricate the 24 starter records`() = runBlocking {
        val source = MangaDexSource(service { 503 to "{}" })
        try { source.getPopularManga("pt-br"); fail("Must expose API failure") }
        catch (expected: retrofit2.HttpException) { assertEquals(503, expected.code()) }
    }
    @Test fun `successful empty popular catalog remains empty`() = runBlocking {
        assertTrue(MangaDexSource(service { 200 to "{\"result\":\"ok\",\"data\":[]}" }).getPopularManga("pt-br").isEmpty())
    }
    @Test fun `chapter API failure is not a successful zero chapter response`() = runBlocking {
        val source = MangaDexSource(service { 503 to "{}" })
        try { source.getChapters("https://api.mangadex.org/manga/id", "pt-br"); fail("Must expose chapter failure") }
        catch (expected: retrofit2.HttpException) { assertEquals(503, expected.code()) }
    }
    @Test fun `legacy demo cover is recognized without rejecting all MangaDex covers`() {
        assertTrue(MangaDexSource.isLegacyStarterRecord("mangadex", "https://uploads.mangadex.org/covers/ade0306c-f4b6-4890-9edb-1ddf04df2039/fe76445d-387f-4ff6-8340-f06403c20dbe.jpg.256.jpg", "", "Unknown"))
        assertFalse(MangaDexSource.isLegacyStarterRecord("mangadex", "https://uploads.mangadex.org/covers/real/current.jpg.256.jpg", "ja", "ongoing"))
        assertFalse(MangaDexSource.isLegacyStarterRecord("mangadex", "https://uploads.mangadex.org/covers/ade0306c-f4b6-4890-9edb-1ddf04df2039/fe76445d-387f-4ff6-8340-f06403c20dbe.jpg.256.jpg", "ko", "completed"))
        assertFalse(MangaDexSource.isLegacyStarterRecord("other", "https://uploads.mangadex.org/covers/ade0306c-f4b6-4890-9edb-1ddf04df2039/fe76445d-387f-4ff6-8340-f06403c20dbe.jpg.256.jpg", "", "Unknown"))
    }
    private fun fixture() = javaClass.getResource("/mangadex-dragon-ball.json")!!.readText()
    private fun service(response: () -> Pair<Int, String>): MangaDexApiService {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = response()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return Retrofit.Builder().baseUrl("https://api.mangadex.org/").client(client)
            .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi)).build().create(MangaDexApiService::class.java)
    }

}
