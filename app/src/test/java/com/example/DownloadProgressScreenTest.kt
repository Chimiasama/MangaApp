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

    @Test fun `library shows back button when opened from manga details and back triggers callback`() {
        var backed = false
        compose.setContent {
            MangaReaderTheme {
                Surface {
                    LibraryScreen(
                        favorites = emptyList(),
                        history = emptyList(),
                        downloads = emptyList(),
                        requestedTab = 3,
                        onMangaClick = {},
                        onContinueReading = {},
                        onReadDownloadedChapter = { _, _ -> },
                        onDeleteDownload = {},
                        onBack = { backed = true }
                    )
                }
            }
        }
        compose.onNodeWithTag("library_back_button").assertExists().performClick()
        compose.runOnIdle { assertTrue(backed) }
    }

    @Test fun `library hides back button when opened normally as a root tab`() {
        compose.setContent {
            MangaReaderTheme {
                Surface {
                    LibraryScreen(
                        favorites = emptyList(),
                        history = emptyList(),
                        downloads = emptyList(),
                        onMangaClick = {},
                        onContinueReading = {},
                        onReadDownloadedChapter = { _, _ -> },
                        onDeleteDownload = {},
                        onBack = null
                    )
                }
            }
        }
        compose.onNodeWithTag("library_back_button").assertDoesNotExist()
    }

    @Test fun `naturalAlphabeticalCompare orders chapters naturally with numbers`() {
        val list = listOf("Capítulo 10", "Capítulo 2", "Capítulo 1", "Capítulo 20", "Capítulo 1.5", "[PT-BR] Capítulo 3")
        val sorted = list.sortedWith(Comparator { a, b ->
            com.example.ui.screens.naturalAlphabeticalCompare(a, b)
        })
        assertEquals(
            listOf("Capítulo 1", "Capítulo 1.5", "Capítulo 2", "[PT-BR] Capítulo 3", "Capítulo 10", "Capítulo 20"),
            sorted
        )
    }

    @Test fun `multiple mangas start collapsed with summary and can expand collapse individually and all together`() {
        val db1 = DownloadEntity("db_10", "Capítulo 10", "url10", "url_db", "Dragon Ball", "", "mangadex",
            downloadState = DownloadEntity.STATE_QUEUED)
        val db2 = DownloadEntity("db_2", "Capítulo 2", "url2", "url_db", "Dragon Ball", "", "mangadex",
            downloadState = DownloadEntity.STATE_COMPLETED, totalPages = 20)
        val db3 = DownloadEntity("db_1", "Capítulo 1", "url1", "url_db", "Dragon Ball", "", "mangadex",
            downloadState = DownloadEntity.STATE_DOWNLOADING)

        val naruto1 = DownloadEntity("naruto_1", "Capítulo 1", "url_n1", "url_naruto", "Naruto", "", "mangadex",
            downloadState = DownloadEntity.STATE_COMPLETED, totalPages = 18)
        val naruto2 = DownloadEntity("naruto_2", "Capítulo 2", "url_n2", "url_naruto", "Naruto", "", "mangadex",
            downloadState = DownloadEntity.STATE_COMPLETED, totalPages = 19)

        val allDownloads = listOf(db1, db2, db3, naruto1, naruto2)

        compose.setContent {
            MangaReaderTheme {
                Surface {
                    LibraryScreen(
                        favorites = emptyList(),
                        history = emptyList(),
                        downloads = allDownloads,
                        requestedTab = 3,
                        onMangaClick = {},
                        onContinueReading = {},
                        onReadDownloadedChapter = { _, _ -> },
                        onDeleteDownload = {}
                    )
                }
            }
        }

        // Summary for each manga should be displayed in closed state
        compose.onNodeWithTag("download_group_title_${"url_db".hashCode()}", useUnmergedTree = true)
            .assertExists()
            .assertTextContains("Dragon Ball")
        compose.onNodeWithTag("download_group_summary_${"url_db".hashCode()}", useUnmergedTree = true)
            .assertExists()
            .assertTextContains("Baixou 1 de 3", substring = true)

        compose.onNodeWithTag("download_group_title_${"url_naruto".hashCode()}", useUnmergedTree = true)
            .assertExists()
            .assertTextContains("Naruto")
        compose.onNodeWithTag("download_group_summary_${"url_naruto".hashCode()}", useUnmergedTree = true)
            .assertExists()
            .assertTextContains("Baixou todos os 2 capítulos", substring = true)

        // Chapters should initially be collapsed when multiple mangas are present
        compose.onNodeWithTag("download_item_db_10").assertDoesNotExist()

        // Tap Dragon Ball group header to expand it
        compose.onNodeWithTag("download_group_header_${"url_db".hashCode()}").performClick()
        compose.onNodeWithTag("download_item_db_1").assertExists()
        compose.onNodeWithTag("download_item_db_2").assertExists()
        compose.onNodeWithTag("download_item_db_10").assertExists()

        // Naruto should still be collapsed
        compose.onNodeWithTag("download_item_naruto_1").assertDoesNotExist()

        // Tap Expandir todos
        compose.onNodeWithTag("toggle_expand_all_downloads").performClick()
        compose.onNodeWithTag("download_item_naruto_1").assertExists()
        compose.onNodeWithTag("download_item_naruto_2").assertExists()

        // Tap Recolher todos
        compose.onNodeWithTag("toggle_expand_all_downloads").performClick()
        compose.onNodeWithTag("download_item_db_1").assertDoesNotExist()
        compose.onNodeWithTag("download_item_naruto_1").assertDoesNotExist()
    }
}
