package com.example

import com.example.source.MangaDashSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class MangaDashSourceTest {
    private fun fixture(name: String) = javaClass.getResource("/mangadash/$name.html")!!.readText()

    @Test fun `all languages with blank title reads actual catalog cards from the net domain`() = runBlocking {
        val calls = mutableListOf<String>()
        val source = MangaDashSource(fetch = { calls += it; fixture("catalog") })
        val mangas = source.getPopularManga("all")
        assertEquals(listOf("https://mangadash.net/mangas"), calls)
        assertEquals("Dragon Ball", mangas.single().title)
        assertEquals(1984, mangas.single().year)
        assertTrue(mangas.single().coverUrl.endsWith("dragon_ball_capa.webp"))
        assertEquals("mangadash", mangas.single().sourceId)
        assertTrue(source.searchManga("dragon ball", "en").isEmpty())
        source.searchManga("dragon ball", "all")
        assertTrue(calls.last().contains("q=dragon%20ball"))
    }

    @Test fun `details chapters and publication metadata use live site selectors`() = runBlocking {
        val source = MangaDashSource(fetch = { fixture("detail") })
        val manga = source.getMangaDetails("https://mangadash.net/manga/367-dragon-ball")
        assertEquals("Dragon Ball", manga.title)
        assertEquals(1984, manga.year)
        assertEquals(listOf("Ação", "Aventura"), manga.genres)
        assertEquals("Akira Toriyama", manga.author)
        assertTrue(manga.coverUrl.startsWith("https://mangadash.net/"))
        val chapters = source.getChapters(manga.url, "all")
        assertEquals(2, chapters.size)
        assertTrue(chapters.all { it.language == "pt-br" && it.chapterNumber in 1f..1000f })
        assertEquals(chapters.sortedByDescending { it.chapterNumber }, chapters)
        assertTrue(chapters.all { it.title.startsWith("Capítulo") && !it.title.contains("/2026") })
    }

    @Test fun `year only filter uses the catalog year parameter without requiring a title`() = runBlocking {
        val calls = mutableListOf<String>()
        val source = MangaDashSource(fetch = { calls += it; fixture("catalog") })
        assertEquals(listOf(1984), source.getCatalogManga("", "all", "1980-1990").map { it.year })
        assertEquals(listOf("https://mangadash.net/mangas", "https://mangadash.net/mangas?ano=1984"), calls)
    }

    @Test fun `network failure is not silently converted into an empty search result`() = runBlocking {
        val source = MangaDashSource(fetch = { throw IOException("offline") })
        try { source.getPopularManga("all"); fail("Must report source failure") }
        catch (expected: IOException) { assertEquals("offline", expected.message) }
    }

    @Test fun `viewer JSON downloads and renders a PDF into reader image paths and reuses cached download`() = runBlocking {
        val cache = java.nio.file.Files.createTempDirectory("mangadash-test").toFile()
        var downloads = 0
        try {
            val source = MangaDashSource(fetch = { fixture("chapter") }, cacheDir = cache,
                downloadPdf = { url, referer, target ->
                    assertTrue(url.startsWith("https://leitor.mangadash.net/"))
                    assertTrue(url.endsWith("Capitulo 1.pdf"))
                    assertEquals("https://mangadash.net/capitulo/421-beware-the-villainess/1", referer)
                    target.writeText("%PDF-test"); downloads++
                }, renderPdf = { file, dir ->
                    assertTrue(file.length() > 0); dir.mkdirs()
                    listOf(File(dir, "page_1.png").apply { writeText("image") }.toURI().toString())
                })
            val chapter = "https://mangadash.net/capitulo/421-beware-the-villainess/1"
            assertTrue(source.getPages(chapter).single().startsWith("file:"))
            source.getPages(chapter)
            assertEquals(1, downloads)
        } finally { cache.deleteRecursively() }
    }

    @Test fun `source does not pass an unrendered PDF URL to the image reader`() = runBlocking {
        val source = MangaDashSource(fetch = { fixture("chapter") })
        try { source.getPages("https://mangadash.net/capitulo/1"); fail("PDF must require local rendering") }
        catch (expected: IOException) { assertTrue(expected.message!!.contains("cache")) }
    }
}
