package com.example

import com.example.data.network.MangaDexApiService
import com.example.data.network.RetrofitClient
import com.example.source.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MangaCategoryTest {
    @Test fun `categories normalize aliases without guessing demographics from themes`() {
        assertTrue(MangaCategories.matches(listOf("Shonen", "Action"), "Shounen"))
        assertTrue(MangaCategories.matches(listOf("Girls' Love"), "Yuri"))
        assertEquals(listOf("Shounen", "Ação"), MangaCategories.display(listOf("Shonen", "Shounen", "Action")))
        assertFalse(MangaCategories.matches(listOf("Shounen Ai"), "Shounen"))
        assertFalse(MangaCategories.matches(listOf("Action", "Martial Arts"), "Shounen"))
        assertFalse(MangaCategories.matches(emptyList(), "Shounen"))
    }
    private fun dex(respond: (Request) -> String): MangaDexSource {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(respond(chain.request()).toResponseBody("application/json".toMediaType())).build()
        }.build()
        return MangaDexSource(Retrofit.Builder().baseUrl("https://api.mangadex.org/").client(client)
            .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi)).build().create(MangaDexApiService::class.java))
    }
    private fun books() = """{"data":[
        {"id":"naruto","attributes":{"title":{"en":"Naruto"},"publicationDemographic":"shounen"}},
        {"id":"dragon-ball","attributes":{"title":{"en":"Dragon Ball"},"publicationDemographic":"shounen"}},
        {"id":"jjk","attributes":{"title":{"en":"Jujutsu Kaisen"},"publicationDemographic":"shounen"}},
        {"id":"misleading","attributes":{"title":{"en":"Shounen in the title"},"publicationDemographic":"seinen"}}
    ]}"""
    @Test fun `Shounen uses demographic not title search and rejects mismatched metadata`() = runBlocking {
        val source = dex { request ->
            assertEquals("shounen", request.url.queryParameter("publicationDemographic[]"))
            assertNull(request.url.queryParameter("title"))
            assertEquals("pt-br", request.url.queryParameter("availableTranslatedLanguage[]"))
            books()
        }
        assertEquals(listOf("Naruto", "Dragon Ball", "Jujutsu Kaisen"), source.searchManga("", "pt-br", "Shounen").map { it.title })
    }
    @Test fun `title and category are combined and empty response never drops constraints`() = runBlocking {
        var calls = 0
        val source = dex { request ->
            calls++
            assertEquals("dragon ball", request.url.queryParameter("title"))
            assertEquals("seinen", request.url.queryParameter("publicationDemographic[]"))
            """{"data":[]}"""
        }
        assertTrue(source.searchManga("dragon ball", "pt-br", "Seinen").isEmpty())
        assertEquals(1, calls)
    }
    @Test fun `missing detail metadata does not invent a replacement work`() = runBlocking {
        try {
            dex { "{}" }.getMangaDetails("https://api.mangadex.org/manga/dragon-ball")
            fail("Missing details must let the caller retain the original work")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message!!.contains("detalhes"))
        }
    }
    @Test fun `translated genre resolves real MangaDex tag id and preserves query`() = runBlocking {
        val requests = mutableListOf<Request>()
        val source = dex { request ->
            requests += request
            if (request.url.encodedPath == "/manga/tag") """{"data":[{"id":"action-tag","attributes":{"name":{"en":"Action"}}}]}"""
            else {
                assertEquals("action-tag", request.url.queryParameter("includedTags[]"))
                assertEquals("naruto", request.url.queryParameter("title"))
                assertNull(request.url.queryParameter("publicationDemographic[]"))
                """{"data":[{"id":"naruto","attributes":{"title":{"en":"Naruto"},"tags":[{"attributes":{"name":{"en":"Action"}}}]}}]}"""
            }
        }
        assertEquals("Naruto", source.searchManga("naruto", "en", "Ação").single().title)
        source.searchManga("naruto", "en", "Ação")
        assertEquals(1, requests.count { it.url.encodedPath == "/manga/tag" })
    }
    @Test fun `HTML sources send separate native category and title parameters`() = runBlocking {
        for (site in HtmlMangaSource.Site.entries) {
            var searched = false
            val source = HtmlMangaSource(site) { url ->
                if (!url.contains('?')) {
                    if (site == HtmlMangaSource.Site.WEEBCENTRAL) """<input id="tag-Shounen-value" value="Shounen">"""
                    else """<select name="genre"><option value="/manga/?genre=shounen">Shounen</option></select>"""
                } else {
                    val parsed = HttpUrl.Builder().scheme("https").host("fixture.test").build().resolve(url)!!
                    assertEquals("Shounen".takeIf { site == HtmlMangaSource.Site.WEEBCENTRAL } ?: "shounen",
                        parsed.queryParameter(if (site == HtmlMangaSource.Site.WEEBCENTRAL) "included_tag" else "genre"))
                    assertEquals("Dragon Ball", parsed.queryParameter(if (site == HtmlMangaSource.Site.WEEBCENTRAL) "text" else "q"))
                    searched = true
                    if (site == HtmlMangaSource.Site.WEEBCENTRAL) """<article><section><a href="/series/db/Dragon-Ball"><img src="/db.jpg" alt="Dragon Ball cover"></a></section></article>"""
                    else """<article class="lc-card"><a class="lc-card-name" href="/manga/db/dragon-ball/">Dragon Ball</a></article>"""
                }
            }
            assertEquals("Dragon Ball", source.searchManga("Dragon Ball", site.language, "Shounen").single().title)
            assertTrue(searched)
        }
    }
    @Test fun `unsupported HTML category cannot become an unfiltered search`() = runBlocking {
        var requests = 0
        val source = HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL) { requests++; "<input id='tag-Action-value' value='Action'>" }
        assertTrue(source.searchManga("", "en", "Ninja").isEmpty())
        assertEquals(1, requests)
    }
    @Test fun `MangaDash sends native category slug with text query`() = runBlocking {
        val source = MangaDashSource({ emptyList() }) { url ->
            if (url.endsWith("/mangas")) """<select id="filterCategoria"><option value="shounen">Shounen (7)</option></select>"""
            else {
                assertTrue(url.contains("categoria=shounen"))
                assertTrue(url.contains("q=Dragon%20Ball"))
                """{"items":[{"id":1,"slug":"dragon-ball","nome":"Dragon Ball"}]}"""
            }
        }
        assertEquals(listOf("Shounen"), source.searchManga("Dragon Ball", "pt-br", "Shounen").single().genres)
    }
}
