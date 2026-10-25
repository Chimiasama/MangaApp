package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.pdf.PdfChapterStore
import com.example.source.MangaDashSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit remote verification on a real Android runtime, enabled with -PliveSources=true. */
@RunWith(AndroidJUnit4::class)
class LivePdfChapterTest {
    @Test fun mangaDashPdfDownloadsAndRendersAsReaderPages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PdfChapterStore(context)
        // The public catalog can reject datacenter IPs. Test document rendering independently
        // and report catalog reachability without misrepresenting it as reader support.
        try {
            com.example.source.SourceHttp.get("https://mangadash.net/api/mangas/list?page=1&q=dragon%20ball")
            println("LIVE PDF catalog: accessible from emulator")
        } catch (error: java.io.IOException) {
            println("LIVE PDF catalog unavailable from emulator: ${error.message}")
        }
        val pdfUrl = "https://leitor.mangadash.net/storage/mangas/dragon_ball/Capitulo%201.pdf"
        val source = MangaDashSource(pdfPages = store::open, fetch = {
            """<script id="chapterViewerData">{"pdfUrl":"$pdfUrl"}</script>"""
        })
        val pages = source.getPages("https://mangadash.net/capitulo/367-dragon-ball/1")
        assertEquals(34, pages.size)
        for (url in listOf(pages.first(), pages.last())) {
            val bitmap = store.render(Uri.parse(url), 1536)
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            val colors = mutableSetOf<Int>()
            for (x in 1..8) for (y in 1..8) colors += bitmap.getPixel(bitmap.width * x / 9, bitmap.height * y / 9)
            assertTrue("The real manga page must contain visible content", colors.size > 1)
            bitmap.recycle()
        }
        assertEquals(pages, source.getPages("https://mangadash.net/capitulo/367-dragon-ball/1"))
        println("LIVE PDF MangaDash: capítulo 1 com ${pages.size} páginas; primeira e última renderizadas no Android com conteúdo visível.")
    }
}
