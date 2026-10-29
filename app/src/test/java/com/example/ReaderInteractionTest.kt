package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.SuccessResult
import com.example.source.ChapterModel
import com.example.ui.reader.ReaderOptions
import com.example.ui.reader.ReaderViewport
import com.example.ui.screens.ReaderScreen
import com.example.ui.viewmodel.ReaderUiState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val completed = mutableListOf<String>()
    private val orientations = mutableListOf<String>()
    private val navigations = mutableListOf<Boolean>()
    private var snapshot: ReaderViewport? = null
    private val attempts = AtomicInteger()
    private val requests = java.util.Collections.synchronizedList(mutableListOf<coil.request.ImageRequest>())
    private var failFirst = false
    private val chapterList = listOf(chapter("one", 1f), chapter("two", 2f), chapter("three", 3f))

    @Before fun images() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Coil.setImageLoader(ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                requests += chain.request
                val attempt = attempts.incrementAndGet()
                if (failFirst && attempt == 1) ErrorResult(null, chain.request, IOException("offline"))
                else {
                    val height = when { chain.request.data.toString().contains("tall") -> 1200
                        chain.request.data.toString().contains("short") -> 100; else -> 400 }
                    SuccessResult(BitmapDrawable(context.resources, Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888)),
                        chain.request, DataSource.MEMORY)
                }
            })
        }.build())
    }

    private fun show(mode: String = "WEBTOON", scale: Float = 1f, pages: List<String> = (0..4).map { "tall:$it" },
                     current: ChapterModel = chapterList[1], onChapter: (ChapterModel) -> Unit = {},
                     initialViewport: ReaderViewport? = null, readerOptions: ReaderOptions = ReaderOptions(),
                     width: MutableState<Int>? = null, externalState: MutableState<ReaderUiState>? = null, height: Int = 640) {
        compose.setContent {
            MaterialTheme {
                var orientation by remember { mutableStateOf("SENSOR") }
                Box(Modifier.size((width?.value ?: 360).dp, height.dp)) {
                    ReaderScreen(state = externalState?.value ?: ReaderUiState(mangaTitle = "Teste", currentChapter = current, pages = pages,
                        chaptersList = chapterList, contentVersion = 1,
                        viewport = initialViewport ?: ReaderViewport(current.id, scale = scale, mode = mode)),
                        readerOrientation = orientation,
                        onReaderOrientationChanged = { orientation = it; orientations += it },
                        onChapterCompleted = { completed += it },
                        readerMode = mode, pagePreloadEnabled = false, defaultFullscreen = false,
                        onBack = {}, onNavigateChapter = { navigations += it }, onChapterSelected = onChapter,
                        onViewportChanged = { snapshot = it }, options = readerOptions)
                }
            }
        }
        compose.waitUntil(10_000) { snapshot != null && attempts.get() > 0 }
        compose.waitForIdle()
    }
    private fun screen() = compose.onNodeWithTag("reader_screen")

    @Test fun `webtoon completes when final page is visible even with first page still on screen`() {
        show(pages = listOf("short:1", "short:2"))
        compose.waitUntil(10_000) { completed.isNotEmpty() }
        assertEquals(0, snapshot!!.pageIndex)
        assertEquals(listOf("two"), completed)
    }

    @Test fun `position information appears only together with controls`() {
        show()
        compose.onNodeWithTag("reader_position").assertDoesNotExist()
        screen().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("reader_position").assertExists()
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        compose.onNodeWithTag("reader_position").assertDoesNotExist()
    }

    @Test fun `orientation menu selects portrait landscape and free rotation`() {
        show(readerOptions = ReaderOptions(controlsTimeoutSeconds = 0))
        screen().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        for (value in listOf("PORTRAIT", "LANDSCAPE", "SENSOR")) {
            compose.onNodeWithTag("reader_orientation").performClick()
            compose.onNodeWithTag("reader_orientation_$value").performClick()
            compose.waitForIdle()
        }
        assertEquals(listOf("PORTRAIT", "LANDSCAPE", "SENSOR"), orientations)
    }

    @Test fun `dimming slider is available without enabling battery saver`() {
        show(readerOptions = ReaderOptions(controlsTimeoutSeconds = 0))
        screen().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Opções do leitor").performClick()
        compose.onNodeWithTag("reader_dimming_slider").assertExists()
        compose.onNodeWithText("Economia de bateria").assertDoesNotExist()
    }

    @Test fun `pinch enlarges webtoon and vertical swipe still scrolls while zoomed`() {
        show()
        screen().performTouchInput {
            pinch(Offset(width * .4f, height * .5f), Offset(width * .6f, height * .5f),
                Offset(width * .2f, height * .5f), Offset(width * .8f, height * .5f), 400)
        }
        compose.waitForIdle()
        assertTrue("Pinch must change scale", snapshot!!.scale > 1.5f)
        val before = snapshot!!.pageIndex + snapshot!!.offsetFraction
        screen().performTouchInput { swipe(Offset(width * .5f, height * .8f), Offset(width * .5f, height * .2f), 500) }
        compose.waitForIdle()
        assertTrue("Zoom must not trap vertical webtoon scrolling", snapshot!!.pageIndex + snapshot!!.offsetFraction > before)
        assertTrue(navigations.isEmpty())
    }
    @Test fun `a new drag at the zoomed image edge turns page but the first drag only pans`() {
        show(mode = "PAGED_LTR", scale = 2.5f, pages = listOf("square:1", "square:2"))
        screen().performTouchInput { swipe(Offset(width * .9f, height * .5f), Offset(width * .1f, height * .5f), 500) }
        compose.waitForIdle()
        assertEquals(0, snapshot!!.pageIndex)
        assertTrue(snapshot!!.panXFraction < -.9f)
        screen().performTouchInput { swipe(Offset(width * .9f, height * .5f), Offset(width * .1f, height * .5f), 500) }
        compose.waitForIdle()
        assertEquals(1, snapshot!!.pageIndex)
        assertEquals(2.5f, snapshot!!.scale)
        assertTrue(navigations.isEmpty())
    }
    @Test fun `chapter pull waits for release and dispatches only once`() {
        show(pages = listOf("short:1"))
        screen().performTouchInput { down(Offset(width * .5f, height * .8f)); moveTo(Offset(width * .5f, height * .3f), 400) }
        compose.waitForIdle()
        assertTrue(navigations.isEmpty())
        compose.onNodeWithTag("chapter_pull_indicator").assertExists()
        screen().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(true), navigations)
    }
    @Test fun `reversing a chapter pull cancels navigation`() {
        show(pages = listOf("short:1"))
        screen().performTouchInput {
            down(Offset(width * .5f, height * .8f))
            moveTo(Offset(width * .5f, height * .5f), 400)
            moveTo(Offset(width * .5f, height * .8f), 400)
            up()
        }
        compose.waitForIdle()
        assertTrue(navigations.isEmpty())
    }
    @Test fun `last chapter cannot advance by pulling`() {
        show(pages = listOf("short:1"), current = chapterList.last())
        screen().performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(navigations.isEmpty())
    }
    @Test fun `double tap toggles zoom and reset restores framing`() {
        show()
        screen().performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertEquals(2.5f, snapshot!!.scale, .01f)
        screen().performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertEquals(1f, snapshot!!.scale, .01f)
    }
    @Test fun `chapter picker selects in place without leaving reader`() {
        var selected: ChapterModel? = null
        show(onChapter = { selected = it })
        screen().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithTag("nav_chapters").performClick()
        compose.onNodeWithTag("reader_chapter_search").performTextInput("3.0")
        compose.onNodeWithTag("reader_chapter_three").performClick()
        compose.waitForIdle()
        assertEquals("three", selected?.id)
        screen().assertExists()
        compose.onNodeWithTag("reader_chapter_list").assertDoesNotExist()
    }
    @Test fun `failed page can be retried independently`() {
        failFirst = true
        show(pages = listOf("square:retry"))
        compose.onNodeWithTag("retry_page_0").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("reader_image_0").fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.StateDescription) { "" } == "loaded"
            }
        }
        assertEquals(2, attempts.get())
        assertFalse(requests[1].memoryCachePolicy.readEnabled)
        assertFalse(requests[1].diskCachePolicy.readEnabled)
        assertEquals("no-cache", requests[1].headers["Cache-Control"])
    }
    @Test fun `controls remain visible during a held interaction and hide after release`() {
        show()
        screen().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("reader_page_slider").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithTag("reader_controls").assertExists()
        compose.onNodeWithTag("reader_page_slider").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(5000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
    }
    @Test fun `rtl reader advances with a new rightward drag at the image edge`() {
        show(mode = "PAGED_RTL", pages = listOf("square:1", "square:2"),
            initialViewport = ReaderViewport("two", scale = 2.5f, panXFraction = 1f, mode = "PAGED_RTL"))
        screen().performTouchInput { swipe(Offset(width * .1f, height * .5f), Offset(width * .9f, height * .5f), 500) }
        compose.waitForIdle()
        assertEquals(1, snapshot!!.pageIndex)
        assertEquals(2.5f, snapshot!!.scale)
    }
    @Test fun `cancelling an armed chapter pull does not navigate`() {
        show(pages = listOf("short:cancel"))
        screen().performTouchInput {
            down(Offset(width * .5f, height * .8f))
            moveTo(Offset(width * .5f, height * .3f), 400)
            cancel()
        }
        compose.waitForIdle()
        assertTrue(navigations.isEmpty())
        compose.onNodeWithTag("chapter_pull_indicator").assertDoesNotExist()
    }
    @Test fun `normalized webtoon position and zoom survive width changes`() {
        val width = mutableStateOf(300)
        show(width = width, initialViewport = ReaderViewport("two", pageIndex = 1, offsetFraction = .35f, scale = 1.8f))
        assertEquals(1, snapshot!!.pageIndex)
        assertEquals(.35f, snapshot!!.offsetFraction, .025f)
        compose.runOnIdle { width.value = 400 }
        compose.waitForIdle()
        assertEquals(1, snapshot!!.pageIndex)
        assertEquals(.35f, snapshot!!.offsetFraction, .025f)
        assertEquals(1.8f, snapshot!!.scale)
    }
    @Test fun `pinch in paginated mode never turns a page or opens controls`() {
        show(mode = "PAGED_LTR", pages = listOf("square:1", "square:2"))
        screen().performTouchInput {
            pinch(Offset(width * .4f, height * .4f), Offset(width * .6f, height * .6f),
                Offset(width * .1f, height * .2f), Offset(width * .9f, height * .8f), 400)
        }
        compose.waitForIdle()
        assertTrue("Pinch result: $snapshot", snapshot!!.scale > 1.5f)
        assertEquals(0, snapshot!!.pageIndex)
        assertTrue(navigations.isEmpty())
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
    }
    @Test fun `controls timeout can be disabled`() {
        show(readerOptions = ReaderOptions(controlsTimeoutSeconds = 0))
        screen().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(11_000)
        compose.waitForIdle()
        compose.onNodeWithTag("reader_controls").assertExists()
    }
    @Test fun `loading a new chapter cancels an old zoom animation and opens at the top`() {
        val state = mutableStateOf(ReaderUiState(mangaTitle = "Teste", currentChapter = chapterList[1],
            pages = (0..4).map { "tall:$it" }, chaptersList = chapterList, contentVersion = 1,
            viewport = ReaderViewport("two", pageIndex = 1, offsetFraction = .4f)))
        show(externalState = state)
        compose.mainClock.autoAdvance = false
        screen().performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            state.value = state.value.copy(currentChapter = chapterList[2], contentVersion = 2,
                currentPageIndex = 0, scrollOffset = 0, viewport = ReaderViewport("three", scale = 2f))
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("three", snapshot!!.chapterId)
        assertEquals(0, snapshot!!.pageIndex)
        assertEquals(0f, snapshot!!.offsetFraction, .01f)
        assertEquals(2f, snapshot!!.scale, .01f)
    }
    private fun chapter(id: String, number: Float) = ChapterModel(id, "Capítulo $number", "chapter:$id", number)
    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h450dp-land")
    fun `compact landscape controls overlay the page without changing its size`() {
        show(pages = listOf("square:1"), width = mutableStateOf(800), height = 450,
            readerOptions = ReaderOptions(controlsTimeoutSeconds = 0))
        val before = compose.onNodeWithTag("reader_image_0").fetchSemanticsNode().boundsInRoot
        screen().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("reader_controls").assertExists()
        compose.onNodeWithTag("reader_bottom_controls").assertExists()
        compose.onNodeWithTag("reader_extra_info").assertDoesNotExist()
        val after = compose.onNodeWithTag("reader_image_0").fetchSemanticsNode().boundsInRoot
        assertEquals(before.width, after.width, 1f)
        assertEquals(before.height, after.height, 1f)
        assertTrue(compose.onNodeWithTag("reader_controls").fetchSemanticsNode().boundsInRoot.height < before.width * .2f)
    }

    @Test fun `top slider and optional reader information are independent preferences`() {
        show(readerOptions = ReaderOptions(controlsTimeoutSeconds = 0, sliderPosition = "top", showExtraInfo = true))
        compose.onNodeWithTag("reader_extra_info").assertExists()
        screen().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("reader_page_slider").assertExists()
        compose.onNodeWithTag("reader_bottom_controls").assertDoesNotExist()
    }

}
