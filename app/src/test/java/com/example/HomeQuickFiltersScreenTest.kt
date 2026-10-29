package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.example.home.HomeQuickFilters
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.source.MangaSource
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MangaReaderTheme
import com.example.ui.viewmodel.HomeUiState
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
class HomeQuickFiltersScreenTest {
    @get:Rule val compose = createComposeRule()
    private val source = object : MangaSource {
        override val id = "mangadash"
        override val name = "MangaDash"
        override val baseUrl = "https://example.test"
        override val isNsfw = false
        override val lang = "pt-br"
        override suspend fun getPopularManga() = emptyList<MangaModel>()
        override suspend fun searchManga(query: String) = emptyList<MangaModel>()
        override suspend fun getMangaDetails(mangaUrl: String): MangaModel = error("Unused")
        override suspend fun getChapters(mangaUrl: String) = emptyList<ChapterModel>()
        override suspend fun getPages(chapterUrl: String) = emptyList<String>()
    }

    @Test fun `one options carousel preserves all three choices when switching categories`() {
        var state by mutableStateOf(HomeUiState(isLoading = false))
        var changes = 0
        compose.setContent {
            MangaReaderTheme { androidx.compose.material3.Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground) { HomeScreen(state, emptySet(), availableSources = listOf(source),
                onMangaClick = {}, onFavoriteToggle = {}, onMore = {}, onRefresh = {},
                onQuickFiltersChanged = { state = state.copy(quickFilters = it); changes++ }) } }
        }
        compose.onAllNodesWithTag("home_filter_options").assertCountEquals(1)
        compose.onNodeWithTag("home_filter_source_mangadash").performClick()
        category("lang")
        compose.runOnIdle { assertEquals(1, changes) }
        compose.onNodeWithTag("home_filter_options").performScrollToNode(hasTestTag("home_filter_lang_en"))
        compose.onNodeWithTag("home_filter_lang_en").performClick()
        category("genre")
        compose.onNodeWithTag("home_filter_genre_genre:romance").performClick()
        category("source")
        compose.onNodeWithTag("home_filter_source_mangadash").assertIsSelected()
        compose.onNodeWithTag("home_filters_summary")
            .assertTextContains("Fonte: MangaDash · Idioma: Inglês · Gênero: Romance")
        compose.runOnIdle {
            assertEquals(HomeQuickFilters("mangadash", "en", "genre:romance"), state.quickFilters)
            assertEquals(3, changes)
        }
        compose.onNodeWithTag("home_screen").captureRoboImage(filePath = "/tmp/home-unified-picker.png")
        compose.onNodeWithTag("home_clear_filters").performClick()
        compose.runOnIdle { assertEquals(HomeQuickFilters(), state.quickFilters) }
        compose.onNodeWithTag("home_filters_summary").assertDoesNotExist()
    }

    @Test fun `empty combination can relax language while preserving source and genre`() {
        var state by mutableStateOf(HomeUiState(isLoading = false,
            quickFilters = HomeQuickFilters("mangadash", "en", "genre:romance")))
        compose.setContent {
            MangaReaderTheme { androidx.compose.material3.Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground) { HomeScreen(state, emptySet(), availableSources = listOf(source),
                onMangaClick = {}, onFavoriteToggle = {}, onMore = {}, onRefresh = {},
                onQuickFiltersChanged = { state = state.copy(quickFilters = it) }) } }
        }
        compose.onNodeWithTag("home_try_all_languages").performClick()
        compose.runOnIdle { assertEquals(HomeQuickFilters("mangadash", "all", "genre:romance"), state.quickFilters) }
    }

    private fun category(id: String) {
        compose.onNodeWithTag("home_filter_dimension").performClick()
        compose.onNodeWithTag("home_filter_category_$id").performClick()
    }
}
