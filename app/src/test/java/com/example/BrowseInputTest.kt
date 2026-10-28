package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.screens.BrowseScreen
import com.example.ui.viewmodel.BrowseUiState
import org.junit.Assert.*
import kotlinx.coroutines.flow.first
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
            MaterialTheme { BrowseScreen(diagnosticsEnabled = true, state = BrowseUiState(selectedYearRange = "1900-1950", errorMessage = message),
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("browse_error_details").assertTextEquals(message)
        compose.onNodeWithTag("copy_browse_error_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(message, clipboard.getText()?.text) }
        compose.onNodeWithTag("retry_browse_button").performScrollTo().assertIsDisplayed()
    }

    @Test fun `manual connection diagnostic opens a copyable report and can be closed`() {
        val state = androidx.compose.runtime.mutableStateOf(BrowseUiState(selectedYearRange = "1900-1950", errorMessage = "Connection reset"))
        val report = "Cliente atual: FALHA\nCliente padrão: FALHA\nHTTPS nativo: SUCESSO"
        lateinit var clipboard: androidx.compose.ui.platform.ClipboardManager
        compose.setContent {
            clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            MaterialTheme { BrowseScreen(diagnosticsEnabled = true, state = state.value,
                onDiagnoseMangaDex = { state.value = state.value.copy(connectionDiagnostic = com.example.ui.viewmodel.ConnectionDiagnosticState(report = report)) },
                onDismissConnectionDiagnostic = { state.value = state.value.copy(connectionDiagnostic = com.example.ui.viewmodel.ConnectionDiagnosticState()) },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("diagnose_mangadex_button").performScrollTo().performClick()
        compose.onNodeWithText("Diagnóstico da MangaDex").assertExists()
        compose.onNodeWithTag("copy_mangadex_diagnostic").performClick()
        compose.runOnIdle { assertEquals(report, clipboard.getText()?.text) }
        compose.onNodeWithText("Fechar").performClick()
        compose.onNodeWithText("Diagnóstico da MangaDex").assertDoesNotExist()
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

    @Test fun `normal mode hides technical errors and controls and enabling debug restores them`() {
        val enabled = androidx.compose.runtime.mutableStateOf(false)
        val message = "SocketException: Connection reset TLS technical details"
        compose.setContent {
            MaterialTheme { BrowseScreen(diagnosticsEnabled = enabled.value,
                state = BrowseUiState(selectedYearRange = "1900-1950", errorMessage = message),
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithText("Não foi possível acessar a fonte. Tente novamente em instantes.").assertExists()
        compose.onNodeWithTag("copy_browse_error_button").assertDoesNotExist()
        compose.onNodeWithTag("diagnose_mangadex_button").assertDoesNotExist()
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithText(message).assertExists()
        compose.onNodeWithTag("copy_browse_error_button").assertExists()
        compose.onNodeWithTag("diagnose_mangadex_button").assertExists()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithTag("diagnose_mangadex_button").assertDoesNotExist()
    }

    @Test fun `connection debug preference is off by default and persists across repository instances`() = kotlinx.coroutines.runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = com.example.data.preferences.UserPreferencesRepository(context)
        try {
            assertFalse(prefs.connectionDiagnosticsEnabled.first())
            prefs.setConnectionDiagnosticsEnabled(true)
            val reopened = com.example.data.preferences.UserPreferencesRepository(context)
            assertTrue(reopened.connectionDiagnosticsEnabled.first())
        } finally { prefs.setConnectionDiagnosticsEnabled(false) }
    }

    @Test fun `settings exposes the connection debug switch`() {
        val state = androidx.compose.runtime.mutableStateOf(com.example.ui.viewmodel.SettingsUiState())
        compose.setContent {
            MaterialTheme { com.example.ui.screens.SettingsScreen(state = state.value, onSaveRulesJson = {},
                onConnectionDiagnosticsToggled = { state.value = state.value.copy(connectionDiagnosticsEnabled = it) }) }
        }
        compose.onNodeWithTag("connection_diagnostics_switch").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("connection_diagnostics_switch").performClick().assertIsOff()
    }

}
