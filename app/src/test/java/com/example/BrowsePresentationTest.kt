package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.example.source.MangaModel
import com.example.ui.screens.BrowseScreen
import com.example.ui.theme.MangaReaderTheme
import com.example.ui.viewmodel.BrowseUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowsePresentationTest {
    @get:Rule val compose = createComposeRule()
    private val titles = listOf(
        MangaModel("one", "one", "Dragon Ball", "cover:one", genres = listOf("Shounen"), sourceId = "mangadex"),
        MangaModel("two", "two", "Dragon Ball (Official Colored)", "cover:two", genres = listOf("Shounen"), sourceId = "mangadex")
    )

    @Before fun localImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Coil.setImageLoader(ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                // Local stand-ins keep presentation tests independent of cover servers.
                val bitmap = Bitmap.createBitmap(280, 400, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(if (chain.request.data.toString().endsWith("one")) Color.rgb(50, 110, 90) else Color.rgb(165, 100, 35))
                SuccessResult(BitmapDrawable(context.resources, bitmap), chain.request, DataSource.MEMORY)
            })
        }.build())
    }

    @Test fun `applying all filters without a title displays the result grid and refreshes catalog`() {
        var submitted: String? = null
        compose.setContent {
            MangaReaderTheme { Surface { BrowseScreen(
                state = BrowseUiState(mangaList = titles, selectedLanguage = "all"), onSearchSubmit = { submitted = it },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) } }
        }
        compose.onNodeWithTag("open_filters_button").performClick()
        compose.onNodeWithTag("close_filters_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("", submitted) }
        compose.onNodeWithText("2 resultados").assertExists()
        compose.onNodeWithTag("manga_card_one").assertExists()
        compose.onNodeWithTag("manga_card_two").assertExists()
    }

    @Test fun `cards align and favoriting does not open manga details`() {
        var opened = false
        var favorite = ""
        compose.setContent {
            MangaReaderTheme { Surface { BrowseScreen(
                state = BrowseUiState(searchQuery = "dragon ball", mangaList = titles),
                onMangaClick = { opened = true }, onFavoriteToggle = { favorite = it.id }, favoriteUrls = emptySet()) } }
        }
        val first = compose.onNodeWithTag("manga_card_one").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("manga_card_two").fetchSemanticsNode().boundsInRoot
        assertEquals(first.height, second.height, 1f)
        compose.onNodeWithText("MANGADEX").assertDoesNotExist()
        compose.onNodeWithTag("favorite_button_one").performClick()
        compose.runOnIdle { assertEquals("one", favorite); assertFalse(opened) }
        val output = java.io.File("build/outputs/roborazzi/browse-clean.png")
        output.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(filePath = output.path)
        compose.onNodeWithTag("manga_card_one").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun `pulling down the results refreshes the current search`() {
        var refreshed = 0
        compose.setContent {
            MangaReaderTheme { Surface { BrowseScreen(
                state = BrowseUiState(searchQuery = "dragon ball", mangaList = titles), onRefresh = { refreshed++ },
                onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet()) } }
        }
        compose.onNodeWithTag("browse_screen").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width / 2f, height * .3f),
                androidx.compose.ui.geometry.Offset(width / 2f, height * .9f), 700)
        }
        compose.runOnIdle { assertEquals(1, refreshed) }
    }
}
