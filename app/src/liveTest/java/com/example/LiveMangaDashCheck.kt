package com.example

import java.io.File
import javax.imageio.ImageIO
import java.util.concurrent.TimeUnit
import com.example.source.MangaDashSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Explicit opt-in only: -PliveSources=true --tests com.example.LiveMangaDashCheck. */
class LiveMangaDashCheck {
    @Test fun publicCatalogSearchChapterAndPdfImagesWorkTogether() = runBlocking {
        val cache = java.nio.file.Files.createTempDirectory("mangadash-live").toFile()
        try {
        // The desktop converter validates the public file independently of Android's native PDF runtime.
        val source = MangaDashSource(cacheDir = cache, renderPdf = { pdf, dir ->
            dir.mkdirs()
            if (dir.listFiles().orEmpty().none { it.extension == "png" }) {
                val process = ProcessBuilder("pdftoppm", "-png", "-scale-to", "1200", pdf.path, File(dir, "page").path)
                    .redirectOutput(File(dir, "conversion-stdout.log")).redirectError(File(dir, "conversion.log")).start()
                if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); error("PDF conversion timed out") }
                check(process.exitValue() == 0) { File(dir, "conversion.log").readText() }
            }
            dir.listFiles().orEmpty().filter { it.extension == "png" }.sortedBy { it.name }.map { it.toURI().toString() }
        })
        val catalog = source.getPopularManga("all")
        assertTrue("Catalog with blank title must contain real manga", catalog.isNotEmpty())
        val search = source.searchManga("Dragon Ball", "all")
        val manga = search.first { it.title.equals("Dragon Ball", true) }
        val details = source.getMangaDetails(manga.url)
        assertEquals(1984, details.year)
        val dated = source.getCatalogManga("", "all", "1984-1984")
        assertTrue(dated.any { it.title.equals("Dragon Ball", true) })
        val chapters = source.getChapters(manga.url, "all")
        assertTrue("Archive, not only latest chapter", chapters.size > 100)
        val chapter = chapters.filter { it.chapterNumber >= 1f }.minBy { it.chapterNumber }
        val pages = source.getPages(chapter.chapterUrl)
        assertTrue(pages.isNotEmpty())
        assertTrue(pages.all { it.startsWith("file:") })
        for (page in listOf(pages.first(), pages.last())) {
            val bitmap = ImageIO.read(File(java.net.URI(page)))
            assertNotNull("Rendered first/last page must be readable", bitmap)
            assertTrue(bitmap!!.width > 0 && bitmap.height > 0)
            bitmap.flush()
        }
        assertEquals(pages, source.getPages(chapter.chapterUrl))
        println("LIVE MANGADASH catalog=${catalog.size}, search=${search.size}, dated=${dated.size}, chapters=${chapters.size}, pdfPages=${pages.size}; public PDF first/last decoded with Poppler; cached reopen passed")
        } finally { cache.deleteRecursively() }
    }
}
