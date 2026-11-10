package com.example

import com.example.data.mapping.toMangaModel
import com.example.data.network.MangaDexApiService
import com.example.data.network.model.*
import com.example.source.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AuthorSuggestionsTest {
    private val dragon = MangaModel("book:dragon", "dragon", "Dragon Ball", "", sourceId = "mangadex", author = "Toriyama Akira", authorIds = listOf("toriyama"))

    @Test fun `unknown authors stay hidden and duplicates open book and adult titles are removed`() {
        val slump = dragon.copy(url = "book:slump", id = "slump", title = "Dr. Slump")
        val items = listOf(dragon, slump, slump, slump.copy(url = "adult", id = "adult", isNsfw = true), slump.copy(url = "other", id = "other", sourceId = "other"))
        assertEquals(listOf(slump), authorSuggestions(dragon, items, false).items)
        assertEquals(2, authorSuggestions(dragon, items, true).items.size)
        for (name in listOf("", "  ", "Desconhecido", "Unknown", "Não informado")) {
            assertTrue(authorSuggestions(dragon.copy(author = name), items, false).items.isEmpty())
        }
    }

    @Test fun `MangaDex uses author identity and chapter language and rejects unrelated artist matches`() = runBlocking {
        val requests = mutableListOf<HttpUrl>()
        val body = """{"data":[{"id":"slump","attributes":{"title":{"en":"Dr. Slump"}},"relationships":[{"id":"toriyama","type":"author","attributes":{"name":"Toriyama Akira"}}]},{"id":"unrelated","relationships":[{"id":"toriyama","type":"artist"}]}]}"""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url
            Response.Builder().request(chain.request()).code(200).message("OK").protocol(Protocol.HTTP_1_1)
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .build().create(MangaDexApiService::class.java)
        val source = MangaDexSource(apiService = api)
        assertTrue(source.supportsAuthorRecommendations)
        assertEquals(listOf("slump"), source.getAuthorManga(dragon, "pt-br").map { it.id })
        assertEquals(listOf("toriyama"), requests.single().queryParameterValues("authors[]"))
        assertEquals(listOf("pt-br"), requests.single().queryParameterValues("availableTranslatedLanguage[]"))
        assertNull(requests.single().queryParameter("title"))
        source.getAuthorManga(dragon, "all")
        assertTrue(requests.last().queryParameterValues("availableTranslatedLanguage[]").isEmpty())
        source.getAuthorManga(dragon, "")
        assertEquals(listOf("pt-br"), requests.last().queryParameterValues("availableTranslatedLanguage[]"))
        assertTrue(source.getAuthorManga(dragon.copy(author = ""), "pt-br").isEmpty())
        assertEquals(3, requests.size)
        assertEquals(listOf("toriyama"), MangaDexMangaDto("dragon", relationships = listOf(
            MangaDexRelationshipDto("toriyama", "author", MangaDexRelationshipAttributesDto(name = "Toriyama Akira")),
            MangaDexRelationshipDto("artist", "artist"))).toMangaModel().authorIds)
    }

    @Test fun `MangaDash uses its author filter without an English or title search fallback`() = runBlocking {
        val requests = mutableListOf<String>()
        val source = MangaDashSource(fetch = { url -> requests += url
            """<article class="manga-card"><a href="/manga/2-dr-slump"><h3>Dr. Slump</h3><img src="/cover.jpg"></a></article>""" })
        assertEquals("Dr. Slump", source.getAuthorManga(dragon.copy(sourceId = "mangadash", author = "Akira Toriyama"), "pt-br").single().title)
        assertEquals("Akira Toriyama", requests.single().toHttpUrl().queryParameter("autor"))
        assertNull(requests.single().toHttpUrl().queryParameter("q"))
        assertTrue(source.getAuthorManga(dragon, "en").isEmpty())
        assertEquals(1, requests.size)
    }
}
