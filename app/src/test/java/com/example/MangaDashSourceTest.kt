package com.example

import com.example.source.MangaDashSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaDashSourceTest {

    @Test
    fun testMangaDashSearchAndDetails() = runTest {
        val sampleSearchHtml = """
            <html>
                <body>
                    <article class="manga-card">
                        <a href="https://mangadash.org/manga/dragon-ball">
                            <img src="https://mangadash.org/covers/db.jpg" alt="Dragon Ball Super" />
                            <h3 class="title">Dragon Ball Super</h3>
                        </a>
                    </article>
                </body>
            </html>
        """.trimIndent()

        val sampleChapterHtml = """
            <html>
                <body>
                    <h1 class="manga-title">Dragon Ball Super</h1>
                    <div class="synopsis">Aventura de Goku e seus amigos.</div>
                    <a href="https://mangadash.org/capitulo/1" class="chapter-link">Capítulo 1</a>
                    <a href="https://mangadash.org/capitulo/2" class="chapter-link">Capítulo 2</a>
                </body>
            </html>
        """.trimIndent()

        val source = MangaDashSource(
            fetch = { url ->
                if (url.contains("/capitulo")) ""
                else if (url.contains("/manga/")) sampleChapterHtml
                else sampleSearchHtml
            }
        )

        val searchResult = source.searchManga("Dragon Ball")
        assertEquals(1, searchResult.size)
        assertEquals("Dragon Ball Super", searchResult[0].title)
        assertEquals("mangadash", searchResult[0].sourceId)

        val details = source.getMangaDetails("https://mangadash.org/manga/dragon-ball")
        assertEquals("Dragon Ball Super", details.title)
        assertEquals("Aventura de Goku e seus amigos.", details.description)

        val chapters = source.getChapters("https://mangadash.org/manga/dragon-ball")
        assertEquals(2, chapters.size)
        assertEquals("Capítulo 1", chapters[0].title)
    }

    @Test
    fun testPdfExtractionFallback() = runTest {
        val chapterHtmlWithPdf = """
            <html>
                <body>
                    <a href="https://mangadash.org/files/chapter_1.pdf">Baixar PDF</a>
                </body>
            </html>
        """.trimIndent()

        val source = MangaDashSource(
            fetch = { chapterHtmlWithPdf }
        )

        val pages = source.getPages("https://mangadash.org/capitulo/1")
        assertEquals(1, pages.size)
        assertEquals("https://mangadash.org/files/chapter_1.pdf", pages[0])
    }
}
