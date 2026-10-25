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
    @Test fun mangaDashFirstChapterDownloadsAndRendersAsReaderPages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PdfChapterStore(context)
        val source = MangaDashSource(store::open)
        val book = source.searchManga("Dragon Ball", "pt-br").first { it.title == "Dragon Ball" }
        val chapters = source.getChapters(book.url, "pt-br")
        assertTrue(chapters.size > 100)
        val chapter = chapters.first { it.chapterNumber == 1f }
        val pages = source.getPages(chapter.chapterUrl)
        assertTrue(pages.size > 1)
        for (url in listOf(pages.first(), pages.last())) {
            val bitmap = store.render(Uri.parse(url), 1536)
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            bitmap.recycle()
        }
        assertEquals(pages, source.getPages(chapter.chapterUrl))
        println("LIVE PDF MangaDash: ${chapters.size} capítulos PT-BR; capítulo 1 com ${pages.size} páginas; primeira e última renderizadas no Android.")
    }
}
