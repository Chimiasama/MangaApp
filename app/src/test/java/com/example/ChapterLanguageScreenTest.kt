package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.ui.screens.MangaDetailScreen
import com.example.ui.viewmodel.MangaDetailUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChapterLanguageScreenTest {
    @get:Rule val compose = createComposeRule()
    private val manga = MangaModel("manga", "manga", "Teste", "", sourceId = "mangadex")
    private val russian = ChapterModel("ru", "Конец. [RU]", "ru", 115f, language = "ru")
    private val portuguese = ChapterModel("pt", "Epílogo", "pt", 115.5f, language = "pt-br")
    @Test fun `detail identifies completed chapters separately from unread chapters`() {
        val unread = portuguese.copy(id = "unread", chapterNumber = 116f)
        compose.setContent {
            MaterialTheme { MangaDetailScreen(state = MangaDetailUiState(manga = manga,
                chapters = listOf(portuguese, unread), readChapterIds = setOf(portuguese.id)),
                downloads = emptyList(), onBack = {}, onToggleFavorite = {},
                onChapterClick = {}, onDownloadChapter = {}, onDeleteDownload = {}) }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("chapter_item_pt"))
        compose.onNodeWithTag("chapter_read_pt", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("chapter_read_unread", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `manual chapter toggle can correct an older reading record`() {
        val read = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            MaterialTheme { MangaDetailScreen(state = MangaDetailUiState(manga = manga,
                chapters = listOf(portuguese), readChapterIds = if (read.value) setOf(portuguese.id) else emptySet()),
                downloads = emptyList(), onBack = {}, onToggleFavorite = {},
                onChapterClick = {}, onDownloadChapter = {}, onDeleteDownload = {},
                onChapterReadChanged = { chapter, value -> assertEquals(portuguese.id, chapter.id); read.value = value }) }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("chapter_item_pt"))
        compose.onNodeWithTag("chapter_toggle_read_pt").performClick()
        compose.onNodeWithTag("chapter_read_pt", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("chapter_toggle_read_pt").performClick()
        compose.onNodeWithTag("chapter_read_pt", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `read now uses Portuguese even when the supplied list contains a lower Russian chapter`() {
        var opened = ""
        compose.setContent {
            MaterialTheme { MangaDetailScreen(state = MangaDetailUiState(manga = manga, chapters = listOf(portuguese, russian)),
                downloads = emptyList(), selectedLanguage = "pt-br", onBack = {}, onToggleFavorite = {},
                onChapterClick = { opened = it.id }, onDownloadChapter = {}, onDeleteDownload = {}) }
        }
        compose.onNodeWithTag("start_reading_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("pt", opened) }
        compose.onNodeWithText("Capítulos (1)").performScrollTo().assertExists()
        compose.onNodeWithTag("chapter_item_ru").assertDoesNotExist()
    }
    @Test fun `no Portuguese chapters shows an empty state instead of a foreign read now action`() {
        compose.setContent {
            MaterialTheme { MangaDetailScreen(state = MangaDetailUiState(manga = manga, chapters = listOf(russian)),
                downloads = emptyList(), selectedLanguage = "pt-br", onBack = {}, onToggleFavorite = {},
                onChapterClick = {}, onDownloadChapter = {}, onDeleteDownload = {}) }
        }
        compose.onNodeWithTag("start_reading_button").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Nenhum capítulo disponível em Português (PT-BR)."))
        compose.onNodeWithText("Nenhum capítulo disponível em Português (PT-BR).").assertExists()
    }
}
