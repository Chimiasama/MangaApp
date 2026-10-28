package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.source.*
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
@Config(sdk = [36], qualifiers = "w360dp-h800dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatalogFilterScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `MangaDex ninja exclusion is staged and applied once with no title`() {
        var request: BrowseFilterRequest? = null
        var calls = 0
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(availableSources = listOf(MangaDexSource())),
                onApplyFilters = { request = it; calls++ }, onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("open_filters_button").performClick()
        compose.onNodeWithTag("source_chip_mangadex").performClick()
        compose.onNodeWithTag("lang_chip_all").performClick()
        compose.onNodeWithTag("filter_classification_search").performScrollTo().performTextInput("ninja")
        compose.onNodeWithTag("include_theme:ninja").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("exclude_theme:ninja").performClick().assertIsSelected()
        compose.onNodeWithTag("include_theme:ninja").assertIsNotSelected()
        compose.runOnIdle { assertNull(request); assertEquals(0, calls) }
        compose.onNodeWithTag("close_filters_button").performClick()
        compose.runOnIdle {
            assertEquals(1, calls)
            assertEquals("mangadex", request!!.sourceId)
            assertEquals("all", request!!.language)
            assertEquals("", request!!.query)
            assertEquals(setOf("theme:ninja"), request!!.filters.excluded)
            assertTrue(request!!.filters.included.isEmpty())
        }
    }
    @Test fun `removing an exclusion chip preserves unrelated inclusions`() {
        val filters = mutableStateOf(CatalogFilters(included = setOf("genre:action"), excluded = setOf("theme:ninja")))
        compose.setContent {
            MaterialTheme { BrowseScreen(state = BrowseUiState(catalogFilters = filters.value),
                onCatalogFiltersChanged = { filters.value = it }, onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) }
        }
        compose.onNodeWithTag("active_filter_theme:ninja").performClick()
        compose.onNodeWithTag("active_filter_theme:ninja").assertDoesNotExist()
        compose.onNodeWithTag("active_filter_genre:action").assertExists()
        compose.runOnIdle { assertTrue(filters.value.excluded.isEmpty()); assertEquals(setOf("genre:action"), filters.value.included) }
    }
}
