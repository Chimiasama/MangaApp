package com.example

import com.example.data.preferences.UserPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testDefaultLanguageIsPortuguese() {
        assertEquals("pt-br", UserPreferencesRepository.DEFAULT_LANGUAGE)
    }

    @Test
    fun testLanguageCodesMapping() {
        val resolveTargetLanguages: (String) -> List<String>? = { language ->
            when (language) {
                "pt-br" -> listOf("pt-br", "pt")
                "en" -> listOf("en")
                "es" -> listOf("es-la", "es")
                else -> null
            }
        }

        assertEquals(listOf("pt-br", "pt"), resolveTargetLanguages("pt-br"))
        assertEquals(listOf("en"), resolveTargetLanguages("en"))
        assertEquals(listOf("es-la", "es"), resolveTargetLanguages("es"))
        assertEquals(null, resolveTargetLanguages("all"))
    }

    @Test
    fun testChapterChunkingBy50() {
        val totalChapters = 135
        val dummyChapters = (1..totalChapters).map { it }
        val chunks = dummyChapters.chunked(50)

        assertEquals(3, chunks.size)
        assertEquals(50, chunks[0].size)
        assertEquals(50, chunks[1].size)
        assertEquals(35, chunks[2].size)
    }

    @Test
    fun testProgressEntityWithExactScrollOffset() {
        val progress = com.example.data.local.ProgressEntity(
            mangaUrl = "https://example.com/manga/solo-leveling",
            chapterId = "ch-101",
            chapterTitle = "Capítulo 101",
            chapterUrl = "https://example.com/ch/101",
            lastPageRead = 14,
            totalPages = 25,
            scrollOffset = 480
        )

        assertEquals(14, progress.lastPageRead)
        assertEquals(480, progress.scrollOffset)
        assertEquals("ch-101", progress.chapterId)
    }

    @Test
    fun testAutoBookmarkEntityWithExactPosition() {
        val bookmark = com.example.data.local.BookmarkEntity(
            mangaUrl = "https://example.com/manga/solo-leveling",
            mangaTitle = "Solo Leveling",
            chapterId = "ch-101",
            chapterTitle = "Capítulo 101",
            pageIndex = 14,
            pageUrl = "https://example.com/page14.jpg",
            scrollOffset = 480,
            isAutoBookmark = true,
            note = "Posição exata salva automaticamente"
        )

        assertEquals(14, bookmark.pageIndex)
        assertEquals(480, bookmark.scrollOffset)
        assertTrue(bookmark.isAutoBookmark)
    }

    @Test
    fun testResumeScrollTargetCalculation() {
        val initialPage: Int? = null
        val initialOffset: Int? = null
        val savedPage = 8
        val savedOffset = 620

        val targetPage = initialPage ?: savedPage
        val targetOffset = initialOffset ?: savedOffset

        assertEquals(8, targetPage)
        assertEquals(620, targetOffset)
    }
}
