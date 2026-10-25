package com.example

import android.graphics.BitmapFactory
import com.example.source.*
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Optional network diagnostics; no chapter/image is retained in the repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveDiscoveryCheck {
    @Test fun phoenixMangaDexMetadataAndEnglishPages() = runBlocking {
        val source = MangaDexSource()
        val book = source.getMangaDetails("https://api.mangadex.org/manga/cc647d62-3a2c-474b-ac4e-844256ab185d")
        assertEquals(1966, book.publicationYear)
        assertTrue(book.author.contains("Tezuka", true))
        val chapters = source.getChapters(book.url, "all")
        val languages = chapters.map { it.language }.distinct()
        println("LIVE PHOENIX MangaDex: ${book.title}; year=${book.publicationYear}; languages=$languages; chapters=${chapters.size}")
        val chapter = chapters.first { it.language == "en" && it.externalUrl.isBlank() && (it.pageCount ?: 0) > 0 }
        val pages = source.getPages(chapter.chapterUrl)
        assertTrue(pages.isNotEmpty())
        SourceHttp.client.newCall(Request.Builder().url(pages.first()).build()).execute().use { response ->
            assertTrue("First Phoenix image HTTP ${response.code}", response.isSuccessful)
            val bytes = response.body!!.bytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull("First Phoenix image must decode", bitmap)
            bitmap!!.recycle()
        }
        println("LIVE PHOENIX READER: ${chapter.title}; language=${chapter.language}; pages=${pages.size}; first image decoded")
    }
    @Test fun historicalMangaDexRangeFindsTheEarlierPhoenixEdition() = runBlocking {
        val source = MangaDexSource()
        val books = source.searchManga("Phoenix", "all", "all", PublicationPeriod(1951, 1960))
        assertTrue(books.all { it.publicationYear in 1951..1960 })
        assertTrue("Original Hi no Tori 1954 edition must be found", books.any { it.id == "fb4ad02c-f4d6-49d9-b8f0-7b705d490026" })
        val oldest = source.searchManga("", "all", "all", PublicationPeriod(1900, 1950))
        assertTrue(oldest.isNotEmpty())
        assertTrue(oldest.all { it.publicationYear in 1900..1950 })
        println("LIVE YEAR MangaDex: Phoenix 1951–1960 matches=${books.size}; 1900–1950 works=${oldest.size}")
    }
    @Test fun weebCentralPhoenixYearComesFromTheSource() = runBlocking {
        val source = HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL)
        val books = source.searchManga("Phoenix", "en", "all", PublicationPeriod(1951, 1960))
        assertTrue(books.isNotEmpty())
        val details = source.getMangaDetails(books.first().url)
        assertEquals(1954, details.publicationYear)
        println("LIVE YEAR WeebCentral: ${details.title}, year=${details.publicationYear}")
    }
    @Test fun mangaDashRespectsTheSelectedPublicationYear() = runBlocking {
        val source = MangaDashSource({ error("No PDF needed for catalog filtering") })
        val books = source.searchManga("", "pt-br", "all", PublicationPeriod(2025, 2025))
        assertTrue(books.isNotEmpty()); assertTrue(books.all { it.publicationYear == 2025 })
        println("LIVE YEAR MangaDash: ${books.size} works in 2025")
    }
}
