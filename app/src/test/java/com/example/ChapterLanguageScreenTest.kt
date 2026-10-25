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
    @Test fun `detail identifies source year and chapter language including unknown metadata`() {
        compose.setContent {
            MaterialTheme { MangaDetailScreen(state = MangaDetailUiState(
                manga = manga.copy(sourceId = "weebcentral", publicationYear = 1966),
                chapters = listOf(ChapterModel("unknown", "Capítulo 1", "link", language = ""))),
                downloads = emptyList(), selectedLanguage = "all", onBack = {}, onToggleFavorite = {},
                onChapterClick = {}, onDownloadChapter = {}, onDeleteDownload = {}) }
        }
        compose.onNodeWithTag("manga_source").performScrollTo().assertTextEquals("Fonte: WeebCentral")
        compose.onNodeWithText("Publicação original: 1966").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("chapter_item_unknown"))
        compose.onNodeWithTag("chapter_language_unknown", useUnmergedTree = true).assertTextEquals("Idioma não informado")
    }

    @Test fun `chapter loading failure is not presented as missing translations`() {
        compose.setContent {
            MaterialTheme { MangaDetailScreen(state = MangaDetailUiState(manga = manga, chapterError = "Fonte respondeu HTTP 403"),
                downloads = emptyList(), selectedLanguage = "pt-br", onBack = {}, onToggleFavorite = {},
                onChapterClick = {}, onDownloadChapter = {}, onDeleteDownload = {}) }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Fonte respondeu HTTP 403"))
        compose.onNodeWithText("Fonte respondeu HTTP 403").assertExists()
        compose.onNodeWithText("Nenhum capítulo disponível em Português (PT-BR).").assertDoesNotExist()
    }

    @Test fun `detail shows normalized categories supplied by the source`() {
        compose.setContent {
            MaterialTheme { MangaDetailScreen(state = MangaDetailUiState(
                manga = manga.copy(genres = listOf("Shonen", "Action", "Adventure"))),
                downloads = emptyList(), selectedLanguage = "pt-br", onBack = {}, onToggleFavorite = {},
                onChapterClick = {}, onDownloadChapter = {}, onDeleteDownload = {}) }
        }
        compose.onNodeWithTag("manga_categories").performScrollTo().assertExists()
        compose.onNodeWithText("Shounen").assertExists()
        compose.onNodeWithText("Ação").assertExists()
        compose.onNodeWithText("Aventura").assertExists()
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
