package com.example

import com.example.data.mapping.toChapterModel
import com.example.data.mapping.toMangaModel
import com.example.data.network.MangaDexApiService
import com.example.data.network.RetrofitClient
import com.example.data.network.model.*
import com.example.source.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class DiscoveryMetadataTest {
    private fun dex(reply: (Request) -> Pair<Int, String>): MangaDexSource {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = reply(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return MangaDexSource(Retrofit.Builder().baseUrl("https://api.mangadex.org/").client(client)
            .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi)).build().create(MangaDexApiService::class.java))
    }
    @Test fun `period uses original year and excludes unknowns at inclusive boundaries`() {
        val period = PublicationPeriod(1951, 1960)
        assertTrue(period.matches(1951)); assertTrue(period.matches(1960))
        assertFalse(period.matches(1950)); assertFalse(period.matches(1961)); assertFalse(period.matches(null))
        val dto = MangaDexMangaDto("phoenix", attributes = MangaDexMangaAttributesDto(year = 1954))
        assertEquals(1954, dto.toMangaModel().publicationYear)
    }
    @Test fun `decade search sends exact years retaining title language and demographic`() = runBlocking {
        val years = mutableListOf<Int>()
        val source = dex { req ->
            val year = req.url.queryParameter("year")!!.toInt(); years += year
            assertEquals("Phoenix", req.url.queryParameter("title"))
            assertEquals("en", req.url.queryParameter("availableTranslatedLanguage[]"))
            assertEquals("shounen", req.url.queryParameter("publicationDemographic[]"))
            200 to """{"data":[{"id":"$year","attributes":{"title":{"en":"Phoenix"},"year":$year,"publicationDemographic":"shounen"}}]}"""
        }
        assertEquals(listOf(1954, 1955), source.searchManga("Phoenix", "en", "Shounen", PublicationPeriod(1954, 1955)).map { it.publicationYear })
        assertEquals(listOf(1954, 1955), years)
    }
    @Test fun `early period scans oldest years instead of taking modern popular manga`() = runBlocking {
        val source = dex { req ->
            assertEquals("asc", req.url.queryParameter("order[year]"))
            assertNull(req.url.queryParameter("order[followedCount]"))
            assertNull(req.url.queryParameter("year"))
            200 to """{"data":[{"id":"old","attributes":{"year":1928}},{"id":"new","attributes":{"year":1966}},{"id":"unknown","attributes":{}}],"total":3}"""
        }
        assertEquals(listOf(1928), source.searchManga("", "all", "all", PublicationPeriod(1900, 1950)).map { it.publicationYear })
    }
    @Test fun `WeebCentral extracts year from metadata never the chapter date`() = runBlocking {
        val source = HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL) {
            """<article><section><a href="/series/id/Phoenix"><img alt="Phoenix cover"></a></section><section><div><strong>Year:</strong><span>1966</span></div></section></article>"""
        }
        assertEquals(1966, source.searchManga("Phoenix", "en", "all", PublicationPeriod(1961, 1970)).single().publicationYear)
        assertTrue(source.searchManga("Phoenix", "en", "all", PublicationPeriod(1951, 1960)).isEmpty())
    }
    @Test fun `WeebCentral detail uses Released label and identifies the author`() = runBlocking {
        val source = HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL) {
            """<h1>Phoenix</h1><ul><li><strong>Released:</strong><span>1954</span></li><li><strong>Author(s):</strong><span><a>TEZUKA Osamu</a></span></li></ul>"""
        }
        val book = source.getMangaDetails("https://weebcentral.com/series/id/Phoenix")
        assertEquals(1954, book.publicationYear); assertEquals("TEZUKA Osamu", book.author)
    }
    @Test fun `MangaDash combines year and text with source supplied year choices`() = runBlocking {
        var queried = false
        val source = MangaDashSource({ emptyList() }) { url ->
            if (url.endsWith("/mangas")) """<select id="filterAno"><option value="1966">1966</option><option value="2025">2025</option></select>"""
            else {
                queried = true
                assertTrue(url.contains("ano=1966")); assertTrue(url.contains("q=Phoenix"))
                """{"items":[{"id":1,"nome":"Phoenix","slug":"phoenix","ano_lancamento":"1966"}]}"""
            }
        }
        assertEquals(1966, source.searchManga("Phoenix", "pt-br", "all", PublicationPeriod(1961, 1970)).single().publicationYear)
        assertTrue(queried)
    }
    @Test fun `MangaDex propagates HTTP errors instead of returning empty pages`() = runBlocking {
        try { dex { 403 to "{}" }.getPages("https://api.mangadex.org/chapter/test"); fail("Should preserve access denial") }
        catch (error: retrofit2.HttpException) { assertTrue(SourcePresentation.readError(error).contains("HTTP 403")) }
    }
    @Test fun `empty image data is a specific unavailable chapter error`() = runBlocking {
        try { dex { 200 to """{"baseUrl":"https://cdn.test","chapter":{"hash":"hash","data":[]}}""" }.getPages("chapter/id"); fail("Should not silently return empty pages") }
        catch (error: ChapterAccessException) { assertTrue(error.message!!.contains("não entregou imagens")) }
    }
    @Test fun `chapter mapping preserves external link page count and language`() {
        val chapter = MangaDexChapterDto("external", attributes = MangaDexChapterAttributesDto(
            translatedLanguage = "en", pages = 0, externalUrl = "https://publisher.test/read/phoenix" )).toChapterModel()
        assertEquals("en", chapter.language); assertEquals(0, chapter.pageCount)
        assertEquals("https://publisher.test/read/phoenix", chapter.externalUrl)
    }
    @Test fun `access messages distinguish denial removal rate limits and source failure without claiming DRM`() {
        assertTrue(SourcePresentation.readError(SourceHttpException(403)).contains("não confirma DRM"))
        assertTrue(SourcePresentation.readError(SourceHttpException(404)).contains("removido"))
        assertTrue(SourcePresentation.readError(SourceHttpException(429)).contains("limitou"))
        assertTrue(SourcePresentation.readError(SourceHttpException(503)).contains("servidor"))
        assertNull(SourcePresentation.safeWebUrl("javascript:alert(1)"))
        assertNull(SourcePresentation.safeWebUrl("file:///private"))
        assertEquals("Idioma não informado", SourcePresentation.language(""))
    }
}
