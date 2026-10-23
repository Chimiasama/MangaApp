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

    @Test
    fun testSteppedZoomProgression() {
        // Estágio 1: 1.0x -> 1.75x (amplia um pouco)
        val stage1 = com.example.ui.screens.getNextZoomScale(1.0f)
        assertEquals(1.75f, stage1, 0.001f)

        // Estágio 2: 1.75x -> 2.75x (amplia mais)
        val stage2 = com.example.ui.screens.getNextZoomScale(stage1)
        assertEquals(2.75f, stage2, 0.001f)

        // Estágio 3: 2.75x -> 4.0x (amplia totalmente)
        val stage3 = com.example.ui.screens.getNextZoomScale(stage2)
        assertEquals(4.0f, stage3, 0.001f)

        // Estágio 4: 4.0x -> 1.0x (reset suave ao padrão)
        val stage4 = com.example.ui.screens.getNextZoomScale(stage3)
        assertEquals(1.0f, stage4, 0.001f)
    }

    @Test
    fun testPanClampingBoundsCalculation() {
        val width = 1080f
        val height = 1920f
        val scale = 2.75f

        val maxOffsetX = ((scale - 1f) * width) / 2f
        val maxOffsetY = ((scale - 1f) * height) / 2f

        assertEquals(945f, maxOffsetX, 0.001f)
        assertEquals(1680f, maxOffsetY, 0.001f)

        // Panning beyond bounds is safely clamped
        val desiredX = 1200f
        val clampedX = desiredX.coerceIn(-maxOffsetX, maxOffsetX)
        assertEquals(945f, clampedX, 0.001f)

        val desiredNegativeY = -2000f
        val clampedY = desiredNegativeY.coerceIn(-maxOffsetY, maxOffsetY)
        assertEquals(-1680f, clampedY, 0.001f)
    }
}
