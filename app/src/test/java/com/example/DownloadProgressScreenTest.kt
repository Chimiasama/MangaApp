package com.example

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.DownloadEntity
import com.example.source.MangaModel
import com.example.ui.screens.LibraryScreen
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
class DownloadProgressScreenTest {
    @get:Rule val compose = createComposeRule()
    private val queued = DownloadEntity("queued", "Capítulo 1", "chapter:1", "book", "Livro", "", "mangadash",
        downloadState = DownloadEntity.STATE_QUEUED)

    @Test fun `library opens downloads directly and shows waiting preparation and failure with retry`() {
        var retry = ""
        val rows = listOf(queued, queued.copy(chapterId = "preparing", downloadState = DownloadEntity.STATE_PREPARING),
            queued.copy(chapterId = "failed", downloadState = DownloadEntity.STATE_FAILED))
        compose.setContent { MangaReaderTheme { Surface { LibraryScreen(
            favorites = emptyList(), history = emptyList(), downloads = rows, requestedTab = 3,
            onMangaClick = {}, onContinueReading = {}, onReadDownloadedChapter = { _, _ -> }, onDeleteDownload = {},
            onRetryDownload = { _, chapter -> retry = chapter.id }) } } }
        compose.onNodeWithText("Downloads: 0/3 concluídos").assertExists()
        compose.onNodeWithTag("download_status_preparing", useUnmergedTree = true).assertTextEquals("Preparando páginas…")
        compose.onNodeWithTag("download_item_queued").assertIsNotEnabled()
        compose.onNodeWithTag("retry_download_failed").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("failed", retry) }
        compose.onNodeWithTag("download_status_failed", useUnmergedTree = true).assertTextEquals("Falha no download")
    }

    @Test fun `manga shows queued totals and a direct link to the download list`() {
        var opened = false
        val manga = MangaModel("book", "book", "Livro", "", sourceId = "mangadash")
        compose.setContent { MangaReaderTheme { Surface { MangaDetailScreen(
            state = MangaDetailUiState(manga = manga), downloads = listOf(queued), onBack = {}, onToggleFavorite = {},
            onChapterClick = {}, onDownloadChapter = {}, onDeleteDownload = {}, onOpenDownloads = { opened = true }) } } }
        compose.onNodeWithTag("open_downloads").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
        compose.onNodeWithText("Downloads: 0/1 concluídos").assertExists()
    }
}
