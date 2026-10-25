package com.example

import com.example.source.MangaDashSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MangaDashSourceTest {
    @Test fun `PDF chapter is delegated to PDF storage instead of being treated as an image`() = runBlocking {
        var pdf = ""
        val source = MangaDashSource(pdfPages = { pdf = it; listOf("mangapdf://page/0", "mangapdf://page/1") }, fetch = {
            """<script id="chapterViewerData" type="application/json">{"pdfUrl":"https://pdf.test/Capitulo 1.pdf"}</script>"""
        })
        assertEquals(2, source.getPages("https://mangadash.net/capitulo/367-dragon-ball/1").size)
        assertEquals("https://pdf.test/Capitulo%201.pdf", pdf)
    }
    @Test fun `Portuguese source never relabels chapters as English`() = runBlocking {
        val source = MangaDashSource(pdfPages = { error("unused") }, fetch = { error("must not request") })
        assertTrue(source.searchManga("Dragon Ball", "en").isEmpty())
        assertTrue(source.getChapters("https://mangadash.net/book", "es").isEmpty())
    }
    @Test fun `lexical website chapter order is corrected for chapter navigation`() = runBlocking {
        val source = MangaDashSource(pdfPages = { error("unused") }, fetch = {
            "<div class='chapters-scroll-container'>" + listOf(99, 1, 100, 10).joinToString("") {
                "<a class='chapter-row' href='/capitulo/book/$it'><div class='chapter-title-group'><h4>Capítulo $it</h4></div></a>"
            } + "</div>"
        })
        val chapters = source.getChapters("https://mangadash.net/manga/book", "pt-br")
        assertEquals(listOf(100f, 99f, 10f, 1f), chapters.map { it.chapterNumber })
        assertTrue(chapters.all { it.language == "pt-br" })
    }
    @Test fun `catalog resolves the site cover path and excludes adult entries`() = runBlocking {
        val source = MangaDashSource(pdfPages = { error("unused") }, fetch = {
            """{"items":[{"id":367,"slug":"dragon-ball","nome":"Dragon Ball","capa":"../images/covers/dragon.webp","isPlus18":false},{"id":2,"slug":"other","nome":"Other","isPlus18":true}]}"""
        })
        val book = source.searchManga("Dragon Ball").single()
        assertEquals("https://mangadash.net/static/images/covers/dragon.webp", book.coverUrl)
        assertEquals("https://mangadash.net/manga/367-dragon-ball", book.url)
    }
}
