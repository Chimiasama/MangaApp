package com.example

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.ui.reader.ReaderOptions
import com.example.ui.reader.ReaderViewport
import com.example.ui.viewmodel.MangaViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReaderSessionTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MangaRepository
    private lateinit var preferences: UserPreferencesRepository
    private val store = ViewModelStore()
    private val chapters = (1..3).map { ChapterModel("$it", "Capítulo $it", "chapter:$it", it.toFloat()) }
    private val manga = MangaModel(url = "test:${UUID.randomUUID()}", id = "manga", title = "Teste", coverUrl = "", sourceId = "fake")

    @Before fun prepare() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        preferences = UserPreferencesRepository(context)
        preferences.setPagePreloadEnabled(false)
        repository = MangaRepository(database.mangaDao(), database.progressDao(), database.downloadDao(), database.readingSessionDao(),
            ChapterDownloadManager(context, database.downloadDao()), preferences, database.chapterDao(), database.bookmarkDao())
    }
    @After fun close() { store.clear(); database.close(); Dispatchers.resetMain() }
    private fun model(loader: suspend (ChapterModel, String) -> List<String>): MangaViewModel =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MangaViewModel(repository, loadBrowseOnInit = false, readerPages = loader) as T
        })[MangaViewModel::class.java]

    @Test fun `only reaching the final page records completion and rereading keeps it`() = runBlocking {
        val vm = model { _, _ -> listOf("a", "b", "c") }
        vm.openReader(manga, chapters[0], chapters)
        withTimeout(10_000) { vm.readerUiState.first { it.currentChapter?.id == "1" && !it.isLoading } }
        vm.updateReaderPageIndex(1)
        vm.flushReaderScrollPosition()
        withTimeout(10_000) { database.progressDao().getProgressForManga(manga.url).first { it?.lastPageRead == 1 } }
        assertTrue(repository.getReadChapterIds(manga.url, manga.sourceId).first().isEmpty())
        vm.updateReaderPageIndex(2)
        vm.flushReaderScrollPosition()
        withTimeout(10_000) { repository.getReadChapterIds(manga.url, manga.sourceId).first { "1" in it } }
        vm.updateReaderPageIndex(0)
        vm.navigateChapter(true)
        withTimeout(10_000) { vm.readerUiState.first { it.currentChapter?.id == "2" && !it.isLoading } }
        assertEquals(listOf("1"), repository.getReadChapterIds(manga.url, manga.sourceId).first())
        assertTrue(repository.getReadChapterIds(manga.url, "another-source").first().isEmpty())
    }

    @Test fun `completion signal persists even when viewport starts on an earlier page and ignores stale chapters`() = runBlocking {
        val vm = model { _, _ -> listOf("a", "b", "c") }
        vm.openReader(manga, chapters[0], chapters)
        withTimeout(10_000) { vm.readerUiState.first { it.currentChapter?.id == "1" && !it.isLoading } }
        vm.completeReaderChapter("stale-chapter")
        assertTrue(repository.getReadChapterIds(manga.url, manga.sourceId).first().isEmpty())
        vm.completeReaderChapter("1")
        withTimeout(10_000) { repository.getReadChapterIds(manga.url, manga.sourceId).first { "1" in it } }
        assertEquals(0, vm.readerUiState.value.currentPageIndex)
    }

    @Test fun `rapid requests cannot let an old chapter replace the latest result`() = runBlocking {
        val firstRequest = CompletableDeferred<Unit>()
        val vm = model { chapter, _ ->
            if (chapter.id == "1") withContext(NonCancellable) { firstRequest.await() }
            listOf("page:${chapter.id}")
        }
        vm.openReader(manga, chapters[0], chapters)
        vm.openReader(manga, chapters[1], chapters)
        withTimeout(10_000) { vm.readerUiState.first { it.currentChapter?.id == "2" && !it.isLoading } }
        firstRequest.complete(Unit)
        delay(100)
        assertEquals("2", vm.readerUiState.value.currentChapter?.id)
        assertEquals(listOf("page:2"), vm.readerUiState.value.pages)
    }
    @Test fun `failed chapter retains current pages and retry starts at the top`() = runBlocking {
        var failing = true
        val vm = model { chapter, _ -> if (chapter.id == "2" && failing) error("offline") else listOf("a", "b", "c") }
        vm.openReader(manga, chapters[0], chapters)
        withTimeout(10_000) { vm.readerUiState.first { it.currentChapter?.id == "1" && !it.isLoading } }
        vm.updateReaderScrollPosition(2, 123)
        vm.updateReaderViewport(ReaderViewport("1", 2, .4f, 2.5f, -.2f))
        vm.navigateChapter(true)
        withTimeout(10_000) { vm.readerUiState.first { it.errorMessage != null && !it.isLoading } }
        assertEquals("1", vm.readerUiState.value.currentChapter?.id)
        assertEquals(3, vm.readerUiState.value.pages.size)
        failing = false
        vm.retryReaderChapter()
        withTimeout(10_000) { vm.readerUiState.first { it.currentChapter?.id == "2" && !it.isLoading } }
        assertEquals(0, vm.readerUiState.value.currentPageIndex)
        assertEquals(0, vm.readerUiState.value.scrollOffset)
        assertEquals(2.5f, vm.readerUiState.value.viewport!!.scale)
        assertEquals(0f, vm.readerUiState.value.viewport!!.panXFraction)
        val previous = database.progressDao().getProgressForMangaSync(manga.url)!!
        assertEquals("1", previous.chapterId)
        assertEquals(2, previous.lastPageRead)
    }
    @Test fun `viewport options and normalized position survive repository recreation`() = runBlocking {
        val viewport = ReaderViewport("1", 2, .375f, 2.75f, -.5f, .25f, "PAGED_RTL")
        preferences.saveReaderViewport(manga.url, viewport)
        preferences.setReaderOptions(ReaderOptions(true, 0, 2.75f))
        val recreated = UserPreferencesRepository(ApplicationProvider.getApplicationContext())
        assertEquals(viewport, recreated.getReaderViewport(manga.url))
        assertEquals(ReaderOptions(true, 0, 2.75f), recreated.readerOptions.first())
        val vm = model { _, _ -> listOf("a", "b", "c") }
        vm.openReader(manga, chapters[0], chapters)
        withTimeout(10_000) { vm.readerUiState.first { it.currentChapter != null && !it.isLoading } }
        assertEquals(viewport, vm.readerUiState.value.viewport)
    }
    @Test fun `bookmark updates cannot discard a concurrently completed chapter prefetch`() = runBlocking {
        preferences.setPagePreloadEnabled(true)
        preferences.setBatterySaverEnabled(false)
        val mappingStarted = java.util.concurrent.CountDownLatch(1)
        val releaseMapping = java.util.concurrent.CountDownLatch(1)
        val bookmark = com.example.data.local.BookmarkEntity(mangaUrl = manga.url, mangaTitle = manga.title,
            chapterId = "1", chapterTitle = "Capítulo 1", pageIndex = 0, pageUrl = "a:1")
        val delayedBookmarks = object : AbstractList<com.example.data.local.BookmarkEntity>() {
            override val size = 1
            override fun get(index: Int): com.example.data.local.BookmarkEntity {
                mappingStarted.countDown()
                check(releaseMapping.await(10, java.util.concurrent.TimeUnit.SECONDS))
                return bookmark
            }
        }
        val bookmarks = object : com.example.data.local.BookmarkDao by database.bookmarkDao() {
            override fun getBookmarksForChapter(chapterId: String) =
                flowOf<List<com.example.data.local.BookmarkEntity>>(delayedBookmarks).flowOn(Dispatchers.IO)
        }
        repository = MangaRepository(database.mangaDao(), database.progressDao(), database.downloadDao(), database.readingSessionDao(),
            ChapterDownloadManager(ApplicationProvider.getApplicationContext(), database.downloadDao()), preferences,
            database.chapterDao(), bookmarks)
        try {
            val vm = model { chapter, _ ->
                if (chapter.id == "2") withContext(Dispatchers.IO) {
                    check(mappingStarted.await(10, java.util.concurrent.TimeUnit.SECONDS))
                }
                listOf("a:${chapter.id}", "b:${chapter.id}")
            }
            vm.openReader(manga, chapters[0], chapters)
            withTimeout(10_000) { vm.readerUiState.first { it.nextChapterFirstPages.size == 2 } }
            releaseMapping.countDown()
            val state = withTimeout(10_000) { vm.readerUiState.first { 0 in it.bookmarkedPages } }
            assertEquals(listOf("a:2", "b:2"), state.nextChapterFirstPages)
        } finally { releaseMapping.countDown() }
    }

    @Test fun `upcoming chapter metadata is prefetched once and reused on navigation`() = runBlocking {
        preferences.setPagePreloadEnabled(true)
        preferences.setBatterySaverEnabled(false)
        val calls = mutableListOf<String>()
        val vm = model { chapter, _ -> calls += chapter.id; listOf("a:${chapter.id}", "b:${chapter.id}") }
        vm.openReader(manga, chapters[0], chapters)
        withTimeout(10_000) { vm.readerUiState.first { it.nextChapterFirstPages.size == 2 } }
        assertEquals(listOf("1", "2"), calls)
        vm.navigateChapter(true)
        withTimeout(10_000) { vm.readerUiState.first { it.currentChapter?.id == "2" && !it.isLoading } }
        assertEquals(1, calls.count { it == "2" })
        assertEquals("a:2", vm.readerUiState.value.pages.first())
    }
}
