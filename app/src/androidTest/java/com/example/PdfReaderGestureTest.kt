package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import com.example.data.pdf.PdfChapterStore
import com.example.source.ChapterModel
import com.example.ui.reader.ReaderOptions
import com.example.ui.reader.ReaderViewport
import com.example.ui.screens.ReaderScreen
import com.example.ui.viewmodel.ReaderUiState
import com.example.util.ImageCacheConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfReaderGestureTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pdfUsesZoomPageGesturesProgressAndChapterControls() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = File(context.cacheDir, "gesture-test.pdf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("pdf-three-pages.pdf").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        val pages = PdfChapterStore(context).pages(fixture)
        Coil.setImageLoader(ImageCacheConfig.setupCoilImageLoader(context))
        var position: ReaderViewport? = null
        var next = false
        val chapter = ChapterModel("pdf", "Capítulo PDF", "pdf", 1f)
        compose.setContent { MaterialTheme { Box(Modifier.size(360.dp, 640.dp)) {
            ReaderScreen(state = ReaderUiState(mangaTitle = "PDF", currentChapter = chapter, pages = pages,
                chaptersList = listOf(chapter, chapter.copy(id = "next", chapterNumber = 2f)), contentVersion = 1,
                viewport = ReaderViewport(chapter.id, mode = "PAGED_LTR")), readerMode = "PAGED_LTR",
                pagePreloadEnabled = false, defaultFullscreen = false, onBack = {},
                onNavigateChapter = { next = it }, onViewportChanged = { position = it },
                options = ReaderOptions(controlsTimeoutSeconds = 0))
        } } }
        compose.waitUntil(15_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "loaded"),
                useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("reader_screen").performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertEquals(2.5f, position!!.scale, .05f)
        compose.onNodeWithTag("reader_screen").performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertEquals(1f, position!!.scale, .05f)
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { position?.pageIndex == 1 }
        compose.onNodeWithTag("reader_screen").performTouchInput { click(center) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("nav_next_chapter").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_next_chapter").performClick()
        compose.runOnIdle { assertTrue(next) }
    }
}
