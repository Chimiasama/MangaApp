package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.source.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WebSourceToolsTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("source_workspace_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("web_chapters_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private val json = """[{"id":"fixture_source","name":"Fixture","baseUrl":"https://old.test","listSelector":".book","titleSelector":"h3","coverAttrSelector":"img","linkAttrSelector":"a","chapterListSelector":".chapter","chapterTitleSelector":"a","chapterLinkSelector":"a","pageImageSelector":".reader img"}]"""
    @Test fun `extracts reader images in DOM order resolves relative urls and ignores icons`() {
        val html = """<h1>Book chapter 2</h1><img src='/logo.png'><div class='reader-images'><img data-src='../pages/2.jpg'><img src='//cdn.test/10.jpg'><img src='//cdn.test/10.jpg'><img src='javascript:alert(1)'></div><a href='/chapter-2'>Chapter 2</a><a href='https://other.test/chapter-3'>Chapter 3</a>"""
        val result = WebExtraction.extract(html, "https://fixture.test/book/chapter")
        assertEquals(listOf("https://fixture.test/pages/2.jpg", "https://cdn.test/10.jpg"), result.pages)
        assertEquals(1, result.chapters.size)
        assertEquals("https://fixture.test/chapter-2", result.chapters.single().chapterUrl)
    }
    @Test fun `generic fallback needs large images and manual selector can recover small pages`() {
        val html = """<header><img src='/ad' width='500' height='500'></header><img src='/small' width='100' height='100'><img src='/page' data-natural-width='800' data-natural-height='1200'>"""
        assertEquals(listOf("https://fixture.test/page"), WebExtraction.extract(html, "https://fixture.test").pages)
        assertEquals(listOf("https://fixture.test/small"), WebExtraction.extract(html, "https://fixture.test", "img[src='/small']").pages)
    }
    @Test fun `rejects unsafe addresses and blocked pages`() {
        listOf("file:///etc/passwd", "javascript:alert(1)", "http://fixture.test", "https://user:password@fixture.test").forEach {
            assertTrue(runCatching { WebUrls.valid(it) }.isFailure)
        }
        assertTrue(runCatching { WebExtraction.extract("<title>Just a moment...</title>", "https://fixture.test") }.isFailure)
    }
    @Test fun `rules apply atomically persist and rollback without losing routes`() {
        val workspace = SourceWorkspace(context)
        workspace.applyRules(json)
        workspace.relocate("fixture_source", "https://new.test")
        val reopened = SourceWorkspace(context)
        assertEquals("fixture_source", reopened.rules().single().id)
        assertEquals("https://new.test", reopened.destination("fixture_source"))
        reopened.rollback()
        assertNull(reopened.destination("fixture_source"))
        assertEquals(1, reopened.rules().size)
        val previous = reopened.snapshot().toString()
        assertTrue(runCatching { reopened.applyRules(json.replace(".book", "[")) }.isFailure)
        assertEquals(previous, reopened.snapshot().toString())
    }
    @Test fun `reserved duplicate ids and cross origin search paths are rejected`() {
        val workspace = SourceWorkspace(context)
        assertTrue(runCatching { workspace.applyRules(json.replace("fixture_source", "mangadex")) }.isFailure)
        assertTrue(runCatching { workspace.applyRules("[" + json.removePrefix("[").removeSuffix("]") + "," + json.removePrefix("[").removeSuffix("]") + "]") }.isFailure)
        assertTrue(runCatching { workspace.applyRules(json.replace("\"listSelector\"", "\"searchPath\":\"//evil.test/{query}\",\"listSelector\"")) }.isFailure)
        assertEquals("{}", workspace.snapshot().toString())
    }
    @Test fun `dynamic scraper uses link selector encodes query and never invents chapters`() = runBlocking {
        val rules = SourceWorkspace(context).parseRules(json).single()
        val calls = mutableListOf<String>()
        val source = DynamicJsoupSource(rules) { url ->
            calls += url
            """<div class='book'><h3>Dragon Ball</h3><a href='../book/db'>Open</a><img data-src='/cover.jpg'></div>"""
        }
        val books = source.searchManga("dragon & ball")
        assertEquals("https://old.test/book/db", books.single().url)
        assertTrue(calls.single().contains("dragon+%26+ball"))
        assertTrue(source.getChapters(books.single().url).isEmpty())
        assertTrue(runCatching { source.getPages(books.single().url) }.isFailure)
    }
    @Test fun `dynamic scraper propagates cancellation`() = runBlocking {
        val rules = SourceWorkspace(context).parseRules(json).single()
        val started = CompletableDeferred<Unit>()
        val job = async { DynamicJsoupSource(rules) { started.complete(Unit); awaitCancellation() }.getPopularManga() }
        started.await(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
    @Test fun `relocation retains canonical bookmarks and does not rewrite cdn`() = runBlocking {
        val rules = SourceWorkspace(context).parseRules(json).single()
        val source = DynamicJsoupSource(rules) { """<div class='book'><h3>Book</h3><a href='https://new.test/book/1'>Open</a><img src='https://cdn.test/cover.jpg'></div><div class='chapter'><a href='https://new.test/book/1/chapter/1'>Chapter 1</a></div>""" }
        val moved = RelocatedSource(source, "https://new.test")
        val book = moved.getPopularManga().single()
        assertEquals("https://old.test/book/1", book.url)
        assertEquals("https://cdn.test/cover.jpg", book.coverUrl)
        assertEquals("https://old.test/book/1".hashCode().toString(), book.id)
        assertEquals("https://old.test/book/1/chapter/1".hashCode().toString(), moved.getChapters(book.url).single().id)
        assertEquals("https://new.test/a?q=1", WebUrls.relocate("https://old.test/a?q=1", "https://old.test", "https://new.test"))
        assertEquals("https://cdn.test/a", WebUrls.relocate("https://cdn.test/a", "https://old.test", "https://new.test"))
    }
    @Test fun `confirmed page selection and order survive new web source instance`() = runBlocking {
        val capture = WebCapture("https://fixture.test/chapter/1", "Book", listOf("https://cdn.test/2.jpg", "https://cdn.test/1.jpg"), emptyList(), language = "pt-br")
        WebLibrarySource(context).save(capture)
        val reopened = WebLibrarySource(context)
        assertEquals(capture.pages, reopened.getPages(capture.url))
        assertEquals("pt-br", reopened.getChapters(capture.url).single().language)
    }
    @Test fun `experimental API exposes real fixture results and encodes remote ids`() = runBlocking {
        val calls = mutableListOf<String>()
        val source = ConsumetInstanceSource("https://api.test", "fixture", "en") { url ->
            calls += url
            when {
                url.contains("/info?") -> """{"id":"book/1","title":"Book","chapters":[{"id":"chapter/1?x=2","title":"Chapter 1"}]}"""
                url.contains("/read?") -> """[{"img":"https://cdn.test/p1.jpg"}]"""
                else -> """{"results":[{"id":"book/1","title":"Book","image":"https://cdn.test/cover.jpg"}]}"""
            }
        }
        assertTrue(source.getPopularManga().isEmpty())
        val book = source.searchManga("book & more").single()
        val ch = source.getChapters(book.url).single()
        assertEquals(listOf("https://cdn.test/p1.jpg"), source.getPages(ch.chapterUrl))
        assertTrue(calls.last().contains("chapterId=chapter%2F1%3Fx%3D2"))
        assertFalse(source.supportsLanguage("pt-br"))
        assertTrue(runCatching { ConsumetInstanceSource("https://api.test", "fixture", "en") { throw java.io.IOException("offline") }.searchManga("book") }.isFailure)
    }
    @Test fun `relocation rewrites first party covers and pages but leaves external CDN intact`() = runBlocking {
        val rules = SourceWorkspace(context).parseRules(json).single()
        val html = "<div class='book'><h3>Book</h3><a href='/book/1'>Open</a><img src='/cover.jpg'></div><div class='reader'><img src='/page.jpg'><img src='https://cdn.test/page.jpg'></div>"
        val moved = RelocatedSource(DynamicJsoupSource(rules) { html }, "https://new.test")
        assertEquals("https://new.test/cover.jpg", moved.getPopularManga().single().coverUrl)
        assertEquals(listOf("https://new.test/page.jpg", "https://cdn.test/page.jpg"), moved.getPages("https://old.test/chapter/1"))
    }
    @Test fun `manifest host update preserves canonical base and rejects downgrades`() {
        val workspace = SourceWorkspace(context)
        workspace.applyRules(json)
        workspace.applyRules(json.replace("https://old.test", "https://new.test").replace("\"name\":", "\"version\":2,\"name\":"))
        assertEquals("https://old.test", workspace.rules().single().baseUrl)
        assertEquals("https://new.test", workspace.destination("fixture_source"))
        assertTrue(runCatching { workspace.applyRules(json) }.isFailure)
        workspace.rollback()
        assertEquals(1, workspace.rules().single().version)
    }
    @Test fun `paused source is not queried even when it was selected before pausing`() = runBlocking {
        val downloads = MangaRepositoryTest.FakeDownloadDao()
        val repository = com.example.data.repository.MangaRepository(MangaRepositoryTest.FakeMangaDao(), MangaRepositoryTest.FakeProgressDao(),
            downloads, MangaRepositoryTest.FakeReadingSessionDao(), com.example.data.download.ChapterDownloadManager(context, downloads),
            com.example.data.preferences.UserPreferencesRepository(context), appContext = context)
        val rule = SourceWorkspace(context).parseRules(json).single()
        repository.registerSource(DynamicJsoupSource(rule) { throw AssertionError("Paused source performed a request") })
        repository.sourceWorkspace!!.setDisabled(rule.id, true)
        assertTrue(repository.searchManga("book", rule.id).isEmpty())
    }
    @Test fun `paused source state persists and is reversible`() {
        val workspace = SourceWorkspace(context)
        workspace.setDisabled("mangadash", true)
        assertTrue(SourceWorkspace(context).isDisabled("mangadash"))
        workspace.rollback()
        assertFalse(SourceWorkspace(context).isDisabled("mangadash"))
    }
    @Test fun `directed web search encodes queries without interpreting javascript`() {
        val url = WebUrls.search("Dragon & Ball", listOf("one.test", "two.test"))
        assertTrue(url.startsWith("https://www.google.com/search?"))
        assertTrue(url.contains("site%3Aone.test"))
        assertFalse(url.contains("Dragon & Ball"))
    }
}
