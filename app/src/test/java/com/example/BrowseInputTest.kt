package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.screens.BrowseScreen
import com.example.ui.viewmodel.BrowseUiState
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
class BrowseInputTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `typing Dragon Ball dispatches a query without needing to find the search icon`() {
        var typed = ""
        var submitted = ""
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(), onQueryChange = { typed = it }, onSearchSubmit = { submitted = it },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("search_input").performTextInput("dragon ball")
        compose.runOnIdle { assertEquals("dragon ball", typed); assertEquals("", submitted) }
        compose.onNodeWithTag("search_input").performImeAction()
        compose.runOnIdle { assertEquals("dragon ball", submitted) }
    }
    @Test fun `an active search shows loading instead of a misleading empty result`() {
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(searchQuery = "dragon ball", isLoading = true),
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("browse_loading").assertExists()
        compose.onNodeWithText("Nenhum mangá encontrado").assertDoesNotExist()
    }

    @Test fun `filters are available in a sheet instead of squeezing the search field`() {
        var language = ""
        var advanced = com.example.source.CatalogFilters()
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(), onLanguageSelected = { language = it }, onCatalogFiltersChanged = { advanced = it },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("lang_chip_en").assertDoesNotExist()
        compose.onNodeWithTag("open_filters_button").performClick()
        compose.onNodeWithTag("lang_chip_en").performClick()
        compose.onNodeWithTag("filter_classification_search").performScrollTo().performTextInput("shounen")
        compose.onNodeWithTag("include_public:shounen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("", language); assertTrue(advanced.included.isEmpty()) }
        compose.onNodeWithTag("close_filters_button").performClick()
        compose.runOnIdle { assertEquals("en", language); assertTrue("public:shounen" in advanced.included) }
        compose.onNodeWithTag("lang_chip_en").assertDoesNotExist()
    }

    @Test fun `recent searches only appear when the empty search field has focus`() {
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(searchHistory = listOf(com.example.data.local.SearchHistoryEntity(id = 1, query = "dragon ball"))),
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("search_history_chip_1").assertDoesNotExist()
        compose.onNodeWithTag("search_input").performClick()
        compose.onNodeWithTag("search_history_chip_1").assertExists()
        compose.onNodeWithTag("search_input").performTextInput("dragon")
        compose.onNodeWithTag("search_history_chip_1").assertDoesNotExist()
    }

}
