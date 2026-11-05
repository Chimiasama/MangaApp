package com.example

import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.ui.components.DisclosureSection
import com.example.ui.screens.MangaDetailScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MangaReaderTheme
import com.example.ui.viewmodel.MangaDetailUiState
import com.example.ui.viewmodel.SettingsUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MinimalLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `settings start collapsed and keep appearance controls reachable`() {
        var theme by mutableStateOf("DARK")
        compose.setContent { MangaReaderTheme(themeMode = theme) { Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(SettingsUiState(themeMode = theme), onSaveRulesJson = {}, onThemeModeSelected = { theme = it })
        } } }
        compose.onNodeWithTag("theme_preferences_card").assertDoesNotExist()
        compose.onNodeWithTag("connection_diagnostics_switch").assertDoesNotExist()
        compose.onNodeWithTag("settings_screen").captureRoboImage(filePath = "/tmp/minimal-settings.png")
        compose.onNodeWithTag("disclosure_Aparência").performScrollTo().performClick()
        compose.onNodeWithTag("theme_preferences_card").assertExists()
        compose.onNodeWithTag("theme_option_LIGHT").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("LIGHT", theme) }
        compose.onNodeWithTag("settings_screen").captureRoboImage(filePath = "/tmp/minimal-settings-light.png")
    }

    @Test fun `disclosure restores expanded state after recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MangaReaderTheme { DisclosureSection("Ferramentas") { androidx.compose.material3.Text("Conteúdo") } } }
        compose.onNodeWithText("Conteúdo").assertDoesNotExist()
        compose.onNodeWithTag("disclosure_Ferramentas").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Conteúdo").assertExists()
        compose.onNodeWithTag("disclosure_Ferramentas").performClick()
        compose.onNodeWithText("Conteúdo").assertDoesNotExist()
    }

    @Test fun `bulk download remains available behind chapter options and preserves chapter list`() {
        val chapters = (1..4).map { ChapterModel("$it", "Capítulo $it", "chapter:$it", it.toFloat(), language = "pt-br") }
        var requested = emptyList<ChapterModel>()
        compose.setContent { MangaReaderTheme { Surface(color = MaterialTheme.colorScheme.background) {
            MangaDetailScreen(downloads = emptyList(), state = MangaDetailUiState(manga = MangaModel("book", "book", "Minha biblioteca", "",
                description = "Uma aventura para acompanhar no seu ritmo. ".repeat(12), sourceId = "mangadash"), chapters = chapters),
                onBack = {}, onToggleFavorite = {}, onChapterClick = {}, onDownloadChapter = {}, onDeleteDownload = {},
                onDownloadMultipleChapters = { requested = it })
        } } }
        compose.onNodeWithTag("download_all_button").assertDoesNotExist()
        compose.onNodeWithTag("disclosure_Opções dos capítulos").performScrollTo().performClick()
        compose.onNodeWithTag("download_all_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(chapters, requested) }
        compose.onNodeWithTag("disclosure_Opções dos capítulos").performScrollTo().performClick()
        compose.onNodeWithTag("chapter_search_input").assertDoesNotExist()
        compose.onNodeWithTag("manga_detail_screen").captureRoboImage(filePath = "/tmp/minimal-detail.png")
    }
}
