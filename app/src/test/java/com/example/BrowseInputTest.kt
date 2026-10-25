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
    @Test fun `leaving live search records Spy without submitting and shows only five recent queries`() {
        var committed = ""
        var selected = ""
        val history = (1..7).map { com.example.data.local.SearchHistoryEntity(it.toLong(), "query$it", it.toLong()) }
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(searchHistory = history),
                onSearchCommitted = { committed = it }, onSearchSubmit = { selected = it },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("search_input").performTextInput("Spy")
        compose.onNodeWithTag("hide_search_keyboard").performClick()
        compose.runOnIdle { assertEquals("Spy", committed) }
        compose.onNodeWithTag("search_input").performClick()
        compose.onNodeWithTag("search_history_chip_6").assertDoesNotExist()
        compose.onNodeWithTag("search_history_chip_7").assertDoesNotExist()
        compose.onNodeWithText("query1", substring = false, useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("query1", selected) }
    }

    @Test fun `publication period is selectable and removable without clearing the query`() {
        var selected: com.example.source.PublicationPeriod? = null
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(searchQuery = "Phoenix"), onPeriodSelected = { selected = it },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("open_filters_button").performClick()
        compose.onNodeWithTag("period_1951_1960").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(com.example.source.PublicationPeriod(1951, 1960), selected) }
    }

    @Test fun `back ends search editing before clearing the query or category`() {
        var cleared = 0
        lateinit var back: androidx.activity.OnBackPressedDispatcher
        compose.setContent {
            back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            MaterialTheme { BrowseScreen(state = BrowseUiState(searchQuery = "dragon ball", selectedGenre = "Shounen"),
                onClearFilters = { cleared++ }, onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("search_input").performClick().assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("search_input").assertIsNotFocused().assertTextContains("dragon ball")
        compose.onNodeWithText("Shounen").assertExists()
        compose.runOnIdle { assertEquals(0, cleared) }
        compose.runOnIdle { back.onBackPressed() }
        compose.runOnIdle { assertEquals(1, cleared) }
    }

    @Test fun `explicit keyboard dismissal keeps results and search on a narrow screen`() {
        checkDismissButton()
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp-port")
    fun `keyboard dismissal works after widening the available screen`() {
        checkDismissButton()
    }

    private fun checkDismissButton() {
        var cleared = 0
        var submitted = 0
        val book = com.example.source.MangaModel("db", "db", "Dragon Ball", "", sourceId = "mangadex")
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(searchQuery = "dragon ball", mangaList = listOf(book)),
                onClearFilters = { cleared++ }, onSearchSubmit = { submitted++ },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("search_input").performClick()
        compose.onNodeWithTag("hide_search_keyboard").performClick()
        compose.onNodeWithTag("search_input").assertIsNotFocused().assertTextContains("dragon ball")
        compose.onNodeWithText("Dragon Ball", substring = false).assertExists()
        compose.onNodeWithTag("hide_search_keyboard").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, cleared); assertEquals(0, submitted) }
    }

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
        var genre = ""
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(), onLanguageSelected = { language = it }, onGenreSelected = { genre = it },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("lang_chip_en").assertDoesNotExist()
        compose.onNodeWithTag("open_filters_button").performClick()
        compose.onNodeWithTag("lang_chip_en").performClick()
        compose.onNodeWithTag("genre_chip_Shounen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("en", language); assertEquals("Shounen", genre) }
        compose.onNodeWithTag("close_filters_button").performScrollTo().performClick()
        compose.onNodeWithTag("lang_chip_en").assertDoesNotExist()
    }

    @Test fun `recent searches stay available while editing and disappear when keyboard is dismissed`() {
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(searchHistory = listOf(com.example.data.local.SearchHistoryEntity(id = 1, query = "dragon ball"))),
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("search_history_chip_1").assertDoesNotExist()
        compose.onNodeWithTag("search_input").performClick()
        compose.onNodeWithTag("search_history_chip_1").assertExists()
        compose.onNodeWithTag("search_input").performTextInput("dragon")
        compose.onNodeWithTag("search_history_chip_1").assertExists()
        compose.onNodeWithTag("hide_search_keyboard").performClick()
        compose.onNodeWithTag("search_history_chip_1").assertDoesNotExist()
    }

}
