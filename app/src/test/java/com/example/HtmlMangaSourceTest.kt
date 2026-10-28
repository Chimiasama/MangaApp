package com.example

import com.example.source.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class HtmlMangaSourceTest {
    @Test fun `English search preserves title cover source and encoded queries`() = runBlocking {
        var requested = ""
        val source = HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL) { url ->
            requested = url
            """<article><section><a href="/series/old/Dragon-Ball"><img src="https://covers.test/db.jpg" alt="Dragon Ball cover"></a></section></article>"""
        }
        val book = source.searchManga("Dragon Ball & GTO", "en").single()
        assertEquals("Dragon Ball", book.title)
        assertEquals("weebcentral", book.sourceId)
        assertEquals("https://weebcentral.com/series/old/Dragon-Ball", book.url)
        assertEquals("https://covers.test/db.jpg", book.coverUrl)
        assertTrue(requested.contains("Dragon%20Ball%20%26%20GTO"))
    }
    @Test fun `unsupported language does not fetch or relabel foreign chapters`() = runBlocking {
        for (site in HtmlMangaSource.Site.entries) {
            val source = HtmlMangaSource(site) { error("PT-BR must not query a foreign-language source") }
            assertTrue(source.searchManga("Dragon Ball", "pt-br").isEmpty())
            assertTrue(source.getPopularManga("pt-br").isEmpty())
            assertTrue(source.getChapters("https://example.test/series/a/b", "pt-br").isEmpty())
        }
    }
    @Test fun `restarting displayed numbers across sagas keeps every chapter in reading order`() = runBlocking {
        val source = HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL) { url ->
            assertTrue(url.endsWith("/series/old/full-chapter-list"))
            listOf("Dragon Ball Z 1", "Dragon Ball 2", "Dragon Ball 1").mapIndexed { i, title ->
                """<div x-data="chapter"><a href="/chapters/$i"><span class="flex"><span>$title</span></span><time datetime="2026-01-01T00:00:00Z"></time></a></div>"""
            }.joinToString("")
        }
        val chapters = source.getChapters("https://weebcentral.com/series/old/Dragon-Ball", "en")
        assertEquals(listOf(3f, 2f, 1f), chapters.map { it.chapterNumber })
        assertEquals(3, chapters.map { it.id }.toSet().size)
        assertTrue(chapters.all { it.language == "en" })
        assertEquals("Dragon Ball 1", chapters.minBy { it.chapterNumber }.title)
    }
    @Test fun `Spanish catalog and chapters resolve relative links and keep Spanish metadata`() = runBlocking {
        val source = HtmlMangaSource(HtmlMangaSource.Site.LEERCAPITULO) { url ->
            if (url.contains("?q=")) """<article class="lc-card"><a class="lc-card-name" href="/manga/db/dragon-ball/">Dragon Ball</a><img src="/covers/db.jpg"></article>"""
            else """<a class="lc-chapter-row" href="/leer/db/1/"><span class="n">Capitulo 1</span><span class="d">2026-01-01</span></a>"""
        }
        val book = source.searchManga("Dragon Ball", "es").single()
        assertEquals("https://www.leercapitulo.co/covers/db.jpg", book.coverUrl)
        val chapter = source.getChapters(book.url, "es").single()
        assertEquals("es", chapter.language)
        assertEquals("https://www.leercapitulo.co/leer/db/1/", chapter.chapterUrl)
        assertTrue(book.id.startsWith("leercapitulo:"))
        assertTrue(chapter.id.startsWith("leercapitulo:"))
    }
    @Test fun `reader images exclude ads and keep document order`() = runBlocking {
        val source = HtmlMangaSource(HtmlMangaSource.Site.LEERCAPITULO) {
            """<img data-src="https://ads.test/banner.jpg"><div id="lcPages"><img data-src="https://pages.test/1.webp"><img data-src="https://pages.test/2.webp"></div>"""
        }
        assertEquals(listOf("https://pages.test/1.webp", "https://pages.test/2.webp"), source.getPages("https://www.leercapitulo.co/leer/db/1/"))
    }
    @Test fun `no images is an actionable failure instead of an invented page`() = runBlocking {
        val source = HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL) { "<html><body>Unavailable</body></html>" }
        try { source.getPages("https://weebcentral.com/chapters/one"); fail("Must report missing images") }
        catch (expected: IOException) { assertTrue(expected.message!!.contains("imagens")) }
    }
    @Test fun `access challenge is not a successful empty catalog`() = runBlocking {
        val source = HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL) { "<title>Just a moment...</title>" }
        try { source.searchManga("Dragon Ball"); fail("Must report verification") }
        catch (expected: IOException) { assertTrue(expected.message!!.contains("verificação")) }
    }
    @Test fun `image requests use the tested identification header`() {
        var agent = ""
        val client = okhttp3.OkHttpClient.Builder().addInterceptor(SourceHttp.imageHeaders).addInterceptor { chain ->
            agent = chain.request().header("User-Agent").orEmpty()
            okhttp3.Response.Builder().request(chain.request()).code(200).message("fixture")
                .protocol(okhttp3.Protocol.HTTP_1_1).body(byteArrayOf().toResponseBody()).build()
        }.build()
        client.newCall(okhttp3.Request.Builder().url("https://pages.test/1.webp").build()).execute().close()
        assertEquals(SourceHttp.USER_AGENT, agent)
    }
    @Test fun `declared languages can evolve without source specific UI or repository rules`() = runBlocking {
        val source = HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL, supportedLanguages = setOf("en", "pt-br")) {
            """<article><section><a href="/series/test/book"><img src="https://covers.test/book.jpg" alt="Livro cover"></a></section></article>"""
        }
        assertTrue(source.supportsLanguage("pt-br"))
        assertEquals("Livro", source.searchManga("Livro", "pt-br").single().title)
    }

}
