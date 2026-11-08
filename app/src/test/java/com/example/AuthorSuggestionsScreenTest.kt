package com.example

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.source.*
import com.example.ui.components.AuthorSuggestionsCarousel
import com.example.ui.screens.MangaDetailScreen
import com.example.ui.theme.MangaReaderTheme
import com.example.ui.viewmodel.MangaDetailUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthorSuggestionsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val slump = MangaModel("slump", "slump", "Dr. Slump", "", sourceId = "mangadex")
    @Test fun `unknown author or no other books shows no section`() {
        compose.setContent { MangaReaderTheme { Surface {
            AuthorSuggestionsCarousel(AuthorSuggestions(author = "", items = listOf(slump))) {}
            AuthorSuggestionsCarousel(AuthorSuggestions(author = "Toriyama Akira")) {}
        } } }
        compose.onNodeWithTag("author_suggestions").assertDoesNotExist()
    }
    @Test fun `manga shows author carousel before chapters and opens the selected book`() {
        var opened: MangaModel? = null
        compose.setContent { MangaReaderTheme { Surface { MangaDetailScreen(
            state = MangaDetailUiState(manga = slump.copy(url = "dragon", id = "dragon", title = "Dragon Ball"),
                chapters = listOf(ChapterModel("one", "Capítulo 1", "one", language = "pt-br")),
                authorSuggestions = AuthorSuggestions("dragon", "mangadex", "Toriyama Akira", listOf(slump))),
            downloads = emptyList(), onBack = {}, onToggleFavorite = {}, onChapterClick = {}, onDownloadChapter = {},
            onDeleteDownload = {}, onAuthorMangaClick = { opened = it }) } } }
        compose.onNodeWithText("Mais de Toriyama Akira").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("manga_card_slump").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(slump, opened) }
        compose.onNodeWithText("Capítulos (1)").performScrollTo().assertExists()
    }
}
