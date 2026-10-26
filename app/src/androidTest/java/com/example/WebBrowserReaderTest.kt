package com.example

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.source.WebCapture
import com.example.source.WebLibrarySource
import com.example.ui.screens.SourceToolsScreen
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class WebBrowserReaderTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var db: AppDatabase
    private lateinit var repository: MangaRepository
    @Before fun prepare() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(), db.readingSessionDao(),
            ChapterDownloadManager(context, db.downloadDao()), UserPreferencesRepository(context), db.chapterDao(), appContext = context)
    }
    @After fun close() { db.close() }
    private fun webView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) webView(view.getChildAt(index))?.let { return it }
        return null
    }
    @Test fun renderedPageCanBeReviewedAndImportedWithoutAnExternalSource() {
        var captured: WebCapture? = null
        var browser: WebView? = null
        val loaded = java.util.concurrent.atomic.AtomicBoolean(false)
        compose.setContent { MaterialTheme { SourceToolsScreen(repository, onClose = {}, onRead = { capture, _ -> captured = capture }) } }
        compose.runOnIdle {
            browser = webView(compose.activity.window.decorView)!!
            val view = browser!!
            assertTrue(view.settings.javaScriptEnabled)
            assertFalse(view.settings.allowFileAccess)
            assertFalse(view.settings.allowContentAccess)
            assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, view.settings.mixedContentMode)
            val original = view.webViewClient
            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(web: WebView, url: String?, favicon: Bitmap?) { original.onPageStarted(web, url, favicon) }
                override fun onPageFinished(web: WebView, url: String?) { original.onPageFinished(web, url); loaded.set(true) }
                override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse {
                    val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aY1cAAAAASUVORK5CYII=", android.util.Base64.DEFAULT)
                    return WebResourceResponse("image/png", null, ByteArrayInputStream(png))
                }
            }
            view.loadDataWithBaseURL("https://fixture.test/read/1", "<html><head><title>Fixture chapter</title></head><body><div class='reader-images'><img src='https://fixture.test/1.png'><img src='https://fixture.test/2.png'></div></body></html>", "text/html", "UTF-8", null)
        }
        compose.waitUntil(15_000) { loaded.get() }
        compose.onNodeWithTag("web_analyze").performClick()
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("web_read").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithTag("source_tools_message").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onRoot().printToLog("WEB_CAPTURE_TEST")
            compose.onNodeWithTag("web_read").assertExists()
        } catch (error: Throwable) {
            compose.onRoot().printToLog("WEB_CAPTURE_TEST")
            throw error
        }
        compose.onNodeWithTag("web_read").performClick()
        compose.runOnIdle { assertNotNull(captured) }
        val capture = captured!!
        assertEquals(listOf("https://fixture.test/1.png", "https://fixture.test/2.png"), capture.pages)
        runBlocking {
            val (book, chapter) = repository.importWebCapture(capture)
            assertEquals("web", book.sourceId)
            assertEquals(capture.pages, repository.getPages(chapter.chapterUrl, chapter.id, book.sourceId))
            val context = ApplicationProvider.getApplicationContext<Context>()
            assertEquals(capture.pages, WebLibrarySource(context).getPages(capture.url))
            db.chapterDao().updateChapterReadStatus(chapter.id, true, 1, 2)
            repository.importWebCapture(capture)
            assertTrue(db.chapterDao().getChapterByIdSync(chapter.id)!!.isRead)
        }
    }
}
