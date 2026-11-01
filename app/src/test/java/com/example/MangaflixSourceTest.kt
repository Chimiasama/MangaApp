package com.example

import com.example.source.MangaflixSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class MangaflixSourceTest {

    private val sampleBrowseHtml = """
        <!DOCTYPE html>
        <html>
        <head><title>MangaFlix - Ler Mangás Online</title></head>
        <body>
        <div class="embla__container">
            <a href="/br/manga/651467d51a137ff269d69510">
                <div class="group">
                    <img alt="Poster do Manga Kill the Hero" src="https://static.mangaflix.net/up/kill_hero.jpg" />
                    <span class="text-white font-bold text-center mt-2">Kill the Hero</span>
                    <p class="line-clamp-4 text-sm text-gray-100">Um dia, o mundo se transformou em um jogo.</p>
                </div>
            </a>
            <a href="/br/manga/6514bc051a137ff2696da8b5">
                <div class="group">
                    <img alt="Poster do Manga To Not Die" data-src="https://static.mangaflix.net/up/to_not_die.jpg" />
                    <span class="text-white font-bold text-center mt-2">To Not Die</span>
                    <p class="line-clamp-4 text-sm text-gray-100">Im Dajun é um estudante que sofre bullying.</p>
                </div>
            </a>
        </div>
        </body>
        </html>
    """.trimIndent()

    private val sampleSearchJson = """
        {
          "status": 200,
          "data": {
            "works": [
              {
                "_id": "680fb247e6863af3d923b4fd",
                "name": "Imperador Solo",
                "description": "Traído por sua guilda e derrotado...",
                "poster": {
                  "default_url": "https://static.mangaflix.net/up/solo_emperor.jpg"
                },
                "genres": ["6511e95bae08773cd411c21c"]
              }
            ]
          }
        }
    """.trimIndent()

    private val sampleMangaDetailJson = """
        {
          "data": {
            "_id": "651467d51a137ff269d69510",
            "name": "Kill the Hero",
            "description": "Um dia, o mundo se transformou em um jogo...",
            "poster": {
              "default_url": "https://static.mangaflix.net/up/kill_hero.jpg"
            },
            "genres": [
              { "_id": "g1", "name": "Ação" },
              { "_id": "g2", "name": "Aventura" },
              { "_id": "g3", "name": "Sobrenatural" }
            ],
            "chapters": [
              {
                "_id": "ch_1",
                "number": "1",
                "date": "2024-01-01",
                "owners": [{ "name": "ScanA" }]
              },
              {
                "_id": "ch_2",
                "number": "2",
                "date": "2024-01-08",
                "owners": [{ "name": "ScanB" }]
              }
            ]
          }
        }
    """.trimIndent()

    private val sampleChapterPagesJson = """
        {
          "data": {
            "_id": "ch_1",
            "images": [
              {
                "_id": "img_2",
                "default_url": "https://static.mangaflix.net/pages/p02.jpg",
                "order": 2
              },
              {
                "_id": "img_1",
                "default_url": "https://static.mangaflix.net/pages/p01.jpg",
                "order": 1
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `getPopularManga parses catalog cards correctly`() = runBlocking {
        val calls = mutableListOf<String>()
        val source = MangaflixSource(fetch = { url ->
            calls.add(url)
            sampleBrowseHtml
        })

        val mangas = source.getPopularManga("pt-br")
        assertEquals(2, mangas.size)

        val first = mangas[0]
        assertEquals("Kill the Hero", first.title)
        assertEquals("https://mangaflix.net/br/manga/651467d51a137ff269d69510", first.url)
        assertEquals("mangaflix:651467d51a137ff269d69510", first.id)
        assertEquals("https://static.mangaflix.net/up/kill_hero.jpg", first.coverUrl)
        assertEquals("mangaflix", first.sourceId)
        assertFalse(first.isNsfw)

        val second = mangas[1]
        assertEquals("To Not Die", second.title)
        assertEquals("https://static.mangaflix.net/up/to_not_die.jpg", second.coverUrl)

        // Languages other than pt-br return empty list
        assertTrue(source.getPopularManga("ja").isEmpty())
    }

    @Test
    fun `searchManga queries API and returns parsed works`() = runBlocking {
        val calls = mutableListOf<String>()
        val source = MangaflixSource(fetch = { url ->
            calls.add(url)
            sampleSearchJson
        })

        val results = source.searchManga("Imperador", "pt-br")
        assertEquals(1, results.size)
        assertTrue(calls.first().contains("api.mangaflix.net/v1/search/mangas?query=Imperador"))

        val manga = results[0]
        assertEquals("Imperador Solo", manga.title)
        assertEquals("mangaflix", manga.sourceId)
        assertEquals("https://static.mangaflix.net/up/solo_emperor.jpg", manga.coverUrl)
        assertFalse(manga.isNsfw)
    }

    @Test
    fun `getMangaDetails parses detail response with genres`() = runBlocking {
        val source = MangaflixSource(fetch = { sampleMangaDetailJson })
        val details = source.getMangaDetails("https://mangaflix.net/br/manga/651467d51a137ff269d69510")

        assertEquals("Kill the Hero", details.title)
        assertEquals("mangaflix", details.sourceId)
        assertEquals(listOf("Ação", "Aventura", "Sobrenatural"), details.genres)
        assertEquals("https://static.mangaflix.net/up/kill_hero.jpg", details.coverUrl)
        assertFalse(details.isNsfw)
    }

    @Test
    fun `getChapters extracts chapters and sorts them descending by number`() = runBlocking {
        val source = MangaflixSource(fetch = { sampleMangaDetailJson })
        val chapters = source.getChapters("https://mangaflix.net/br/manga/651467d51a137ff269d69510", "pt-br")

        assertEquals(2, chapters.size)
        // Chapter 2 comes first due to descending sort
        assertEquals(2.0f, chapters[0].chapterNumber)
        assertEquals("Capítulo 2 - ScanB", chapters[0].title)
        assertEquals("pt-br", chapters[0].language)

        assertEquals(1.0f, chapters[1].chapterNumber)
        assertEquals("Capítulo 1 - ScanA", chapters[1].title)
    }

    @Test
    fun `getPages extracts and orders images by order attribute`() = runBlocking {
        val calls = mutableListOf<String>()
        val source = MangaflixSource(fetch = { url ->
            calls.add(url)
            sampleChapterPagesJson
        })

        val pages = source.getPages("https://api.mangaflix.net/v1/chapters/ch_1?selected_language=pt-br")
        assertEquals(2, pages.size)
        assertEquals("https://static.mangaflix.net/pages/p01.jpg", pages[0])
        assertEquals("https://static.mangaflix.net/pages/p02.jpg", pages[1])
        assertTrue(calls.first().contains("/v1/chapters/ch_1"))
    }

    @Test
    fun `detects Cloudflare WAF block and throws descriptive exception`() = runBlocking {
        val source = MangaflixSource(fetch = {
            "<html><head><title>Just a moment...</title></head><body>Verify you are human</body></html>"
        })

        try {
            source.getPopularManga("pt-br")
            fail("Should throw on Cloudflare block")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("WAF") == true || e.message?.contains("verificação") == true)
        }
    }
}
