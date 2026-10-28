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

/** Opt in with -PliveSources=true; regular CI uses deterministic fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveAdditionalSourceCheck {
    @Test fun englishOldAndRecentPages() = check(HtmlMangaSource.Site.WEEBCENTRAL, "Dragon Ball")
    @Test fun spanishOldAndRecentPages() = check(HtmlMangaSource.Site.LEERCAPITULO, "Dragon Ball Full Color")
    @Test fun moreClassicCatalogs() = runBlocking {
        for (site in HtmlMangaSource.Site.entries) {
            val source = HtmlMangaSource(site)
            for (title in listOf("GTO", "Slam Dunk")) {
                val results = source.searchManga(title, site.languages.first())
                assertTrue("$title should be discoverable on ${site.key}", results.any { it.title.contains(title, true) })
                println("LIVE CLASSICS ${site.key}: $title; matches=${results.size}")
            }
        }
    }
    private fun check(site: HtmlMangaSource.Site, title: String) = runBlocking {
        val source = HtmlMangaSource(site)
        val results = source.searchManga(title, site.languages.first())
        val book = results.first { it.title.equals(title, true) }
        val details = source.getMangaDetails(book.url)
        assertTrue(details.title.isNotBlank())
        val chapters = source.getChapters(book.url, site.languages.first())
        assertTrue("Need an archive, not only the latest release", chapters.size > 100)
        assertTrue(chapters.all { it.language == site.languages.first() })
        image(book.coverUrl)
        for (chapter in listOf(chapters.minBy { it.chapterNumber }, chapters.maxBy { it.chapterNumber })) {
            val pages = source.getPages(chapter.chapterUrl)
            assertTrue(pages.isNotEmpty())
            image(pages.first())
            image(pages.last())
            println("LIVE ${site.key}: ${book.title}; chapters=${chapters.size}; title=${chapter.title}; pages=${pages.size}; first+last images decoded")
        }
    }
    private fun image(url: String) {
        SourceHttp.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertTrue("Image HTTP ${response.code}", response.isSuccessful)
            val bytes = response.body!!.bytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull("Must deliver a decodable image, not HTML", bitmap)
            assertTrue(bitmap!!.width > 0 && bitmap.height > 0)
            bitmap.recycle()
        }
    }
}
