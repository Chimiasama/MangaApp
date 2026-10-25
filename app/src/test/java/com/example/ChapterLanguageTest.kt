package com.example

import com.example.data.network.MangaDexApiService
import com.example.data.network.RetrofitClient
import com.example.source.MangaDexSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ChapterLanguageTest {
    private val requests = mutableListOf<List<String>>()
    private fun chapter(id: String, lang: String?, number: Int = 115): String =
        """{"id":"$id","attributes":{"chapter":"$number","title":"Final","translatedLanguage":${lang?.let { "\"$it\"" } ?: "null"},"pages":20}}"""
    private fun source(vararg chapters: String): MangaDexSource {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.queryParameterValues("translatedLanguage[]").filterNotNull()
            val body = """{"data":[${chapters.joinToString(",")}],"total":${chapters.size}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.mangadex.org/").client(client)
            .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi)).build().create(MangaDexApiService::class.java)
        return MangaDexSource(api)
    }
    @Test fun `Portuguese selection excludes Russian Spanish English and unknown metadata`() = runBlocking {
        val chapters = source(chapter("pt", "pt-br"), chapter("ru", "ru"), chapter("es", "es"), chapter("en", "en"), chapter("unknown", null))
            .getChapters("https://api.mangadex.org/manga/test", "pt-br")
        assertEquals(listOf("pt"), chapters.map { it.id })
        assertEquals("pt-br", chapters.single().language)
        assertEquals(listOf(listOf("pt-br")), requests)
    }
    @Test fun `missing Portuguese chapters stay empty without global or English requests`() = runBlocking {
        assertTrue(source().getChapters("test", "pt-br").isEmpty())
        assertEquals(listOf(listOf("pt-br")), requests)
    }
    @Test fun `all languages explicitly includes translations with the same chapter number`() = runBlocking {
        val chapters = source(chapter("pt", "pt-br"), chapter("ru", "ru"), chapter("en", "en")).getChapters("test", "all")
        assertEquals(setOf("pt-br", "ru", "en"), chapters.map { it.language }.toSet())
        assertEquals(listOf(emptyList<String>()), requests)
    }
    @Test fun `English selection never fills missing chapters from Russian`() = runBlocking {
        val chapters = source(chapter("ru", "ru"), chapter("en", "en", 114)).getChapters("test", "en")
        assertEquals(listOf("en"), chapters.map { it.id })
        assertEquals(listOf(listOf("en")), requests)
    }
    @Test fun `Brazilian Portuguese does not silently include European Portuguese`() = runBlocking {
        val chapters = source(chapter("pt", "pt"), chapter("br", "pt-br")).getChapters("test", "PT_BR")
        assertEquals(listOf("br"), chapters.map { it.id })
    }
}
