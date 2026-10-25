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

}
