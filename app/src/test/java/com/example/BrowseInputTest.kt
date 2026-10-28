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

    @Test fun `opening filters and selecting only 1900 to 1950 preserves the saved language`() {
        var applied: com.example.source.BrowseFilterRequest? = null
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(), onApplyFilters = { applied = it },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("open_filters_button").performClick()
        compose.onNodeWithTag("lang_chip_pt-br").assertIsSelected()
        compose.onNodeWithTag("year_chip_1900-1950").performScrollTo().performClick()
        compose.onNodeWithTag("sheet_filter_summary")
            .assertTextEquals("Data: 1900–1950 · Fonte: Todas · Idioma: Português · Origem: Todas · Classificação: Todas")
        compose.onNodeWithTag("close_filters_button").performClick()
        compose.runOnIdle {
            assertEquals(com.example.source.BrowseFilterRequest("", "all", "pt-br", "1900-1950", com.example.source.CatalogFilters()), applied)
        }
    }

    @Test fun `date only search displays every effective filter in a compact summary`() {
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(selectedYearRange = "1900-1950"),
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("active_filter_summary")
            .assertTextEquals("Data: 1900–1950 · Fonte: Todas · Idioma: Português · Origem: Todas · Classificação: Todas")
        compose.onNodeWithTag("open_filters_button").performClick()
        compose.onNodeWithTag("sheet_filter_summary")
            .assertTextEquals("Data: 1900–1950 · Fonte: Todas · Idioma: Português · Origem: Todas · Classificação: Todas")
    }

    @Test fun `offline and timeout messages never claim there are no matching manga`() {
        val state = androidx.compose.runtime.mutableStateOf(BrowseUiState(
            selectedYearRange = "1900-1950", failure = com.example.ui.viewmodel.BrowseFailure.OFFLINE,
            errorMessage = "Aguardando conexão para pesquisar novamente."))
        var retries = 0
        compose.setContent {
            MaterialTheme { BrowseScreen(state = state.value, onRefresh = { retries++ },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithText("Sem conexão com a internet").assertExists()
        compose.onNodeWithText("Nenhum mangá encontrado").assertDoesNotExist()
        compose.onNodeWithTag("retry_browse_button").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state.value = state.value.copy(failure = com.example.ui.viewmodel.BrowseFailure.TIMEOUT,
                errorMessage = "Tente novamente.")
        }
        compose.onNodeWithText("A pesquisa demorou demais").assertExists()
        compose.onNodeWithText("Nenhum mangá encontrado").assertDoesNotExist()
    }

    @Test fun `long socket error is fully displayed scrollable and copied without truncation`() {
        val message = "Consulta incompleta: MangaDex\nSocketException: Connection reset\n" + "Detalhe completo da conexão.\n".repeat(60)
        lateinit var clipboard: androidx.compose.ui.platform.ClipboardManager
        compose.setContent {
            clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            MaterialTheme { BrowseScreen(state = BrowseUiState(selectedYearRange = "1900-1950", errorMessage = message),
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("browse_error_details").assertTextEquals(message)
        compose.onNodeWithTag("copy_browse_error_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(message, clipboard.getText()?.text) }
        compose.onNodeWithTag("retry_browse_button").performScrollTo().assertIsDisplayed()
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
