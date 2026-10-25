package com.example

import com.example.source.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Optional network check: -PliveSources=true --tests com.example.LiveCategoryCheck */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveCategoryCheck {
    @Test fun mangaDexDemographicAndTags() = runBlocking {
        val source = MangaDexSource()
        for (category in listOf("Shounen", "Ação")) {
            val books = source.searchManga("", "pt-br", category)
            assertTrue(books.isNotEmpty())
            assertTrue(books.all { MangaCategories.matches(it.genres, category) })
            println("LIVE CATEGORY MangaDex $category: ${books.size} matches, ${books.take(5).map { it.title }}")
        }
        for (title in listOf("Naruto", "Dragon Ball", "Jujutsu Kaisen")) {
            val matches = source.searchManga(title, "pt-br", "Shounen")
            assertTrue("$title has related Shounen editions in PT-BR", matches.isNotEmpty())
            assertTrue(matches.all { MangaCategories.matches(it.genres, "Shounen") })
            println("LIVE COMBINED MangaDex: $title, ${matches.size} matches")
        }
    }
    @Test fun weebCentralCategory() = check(HtmlMangaSource(HtmlMangaSource.Site.WEEBCENTRAL), "en")
    @Test fun leerCapituloCategory() = check(HtmlMangaSource(HtmlMangaSource.Site.LEERCAPITULO), "es")
    @Test fun mangaDashCategory() = check(MangaDashSource({ error("Catalog does not need PDF") }), "pt-br")
    private fun check(source: MangaSource, language: String) = runBlocking {
        val books = source.searchManga("", language, "Shounen")
        assertTrue("${source.name} should provide Shounen works", books.isNotEmpty())
        // Independently inspect detail metadata to detect a server ignoring its filter.
        val details = source.getMangaDetails(books.first().url)
        assertTrue("${source.name}: ${details.title}, categories=${details.genres}", MangaCategories.matches(details.genres, "Shounen"))
        println("LIVE CATEGORY ${source.name}: ${books.size} matches; detail ${details.title}: ${details.genres}")
        if (source is HtmlMangaSource) {
            val combined = source.searchManga("Dragon Ball", language, "Shounen")
            assertTrue(combined.any { it.title.contains("Dragon Ball", true) })
            val dragon = source.getMangaDetails(combined.first().url)
            assertTrue(MangaCategories.matches(dragon.genres, "Shounen"))
            println("LIVE COMBINED ${source.name}: ${combined.size} matches; detail ${dragon.title}: ${dragon.genres}")
        }
    }
}
