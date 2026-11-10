package com.example

import android.content.Context
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.*
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.home.*
import com.example.source.*
import com.example.ui.viewmodel.MangaViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeHistoryRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: MangaRepository
    private lateinit var preferences: UserPreferencesRepository
    private val store = ViewModelStore()
    private val book = MangaEntity("book:dragon", "dragon", "Dragon Ball", "cover.jpg", "", "fixture")
    private fun progress(url: String = book.url) = ProgressEntity(url, "one", "Capítulo 1", "chapter:one", 1, 3)

    @Before fun prepare() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        preferences = UserPreferencesRepository(context)
        preferences.setHomeSections(listOf(HomeSection("continue_reading", "Continuar lendo", HomeSectionType.CONTINUE_READING)))
        preferences.setPagePreloadEnabled(false)
        repository = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(), db.readingSessionDao(),
            ChapterDownloadManager(context, db.downloadDao()), preferences, db.chapterDao(), db.bookmarkDao())
        repository.clearSources()
    }
    @After fun close(): Unit = runBlocking {
        store.clear()
        preferences.setHomeSections(HomeSections.defaults)
        db.close()
        Dispatchers.resetMain()
    }
    private fun model(home: Boolean = false) = ViewModelProvider(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaViewModel(repository, loadBrowseOnInit = false, loadHomeOnInit = home,
                readerPages = { _, _ -> listOf("a", "b", "c") }) as T
    })[MangaViewModel::class.java]

    @Test fun `unfavorite preserves real history metadata and deletion removes home card without restart`(): Unit = runBlocking {
        db.mangaDao().insertManga(book)
        db.progressDao().saveProgress(progress())
        val vm = model(home = true)
        withTimeout(10_000) { vm.homeUiState.first { it.sections.flatMap { s -> s.items }.any { it.url == book.url } } }
        repository.removeFavorite(book.url)
        assertFalse(db.mangaDao().getMangaByUrlSync(book.url)!!.isFavorite)
        assertTrue(repository.favorites.first().isEmpty())
        assertEquals("Dragon Ball", repository.continueReadingManga.first().single().title)
        db.downloadDao().insertDownload(DownloadEntity("one", "Capítulo 1", "chapter:one", book.url, book.title, book.coverUrl, book.sourceId))
        repository.deleteDownload("one")
        vm.deleteHistory(book.url)
        withTimeout(10_000) { vm.homeUiState.first { it.sections.none { s -> s.isContinueReading } } }
        assertTrue(repository.readingProgress.first().isEmpty())
    }

    @Test fun `orphaned progress never produces placeholder and metadata changes update home with same history URLs`(): Unit = runBlocking {
        db.progressDao().saveProgress(progress())
        val vm = model(home = true)
        assertTrue(repository.continueReadingManga.first().isEmpty())
        assertTrue(vm.homeUiState.value.sections.flatMap { it.items }.isEmpty())
        db.mangaDao().insertManga(book.copy(isFavorite = false))
        withTimeout(10_000) { vm.homeUiState.first { it.sections.flatMap { s -> s.items }.any { it.title == book.title } } }
        db.mangaDao().insertManga(book.copy(title = "Título atualizado", coverUrl = "new-cover.jpg", isFavorite = false))
        withTimeout(10_000) { vm.homeUiState.first { it.sections.flatMap { s -> s.items }.any { it.title == "Título atualizado" && it.coverUrl == "new-cover.jpg" } } }
        // Reproduce an orphan produced by previous versions that deleted the whole favorite row.
        db.mangaDao().deleteFavoriteByUrl(book.url)
        withTimeout(10_000) { vm.homeUiState.first { it.sections.none { s -> s.isContinueReading } } }
        assertEquals(1, repository.readingProgress.first().size)
    }

    @Test fun `old reader flush cannot resurrect deleted history and deliberate new reading can save again`(): Unit = runBlocking {
        val vm = model()
        val manga = MangaModel(book.url, book.id, book.title, book.coverUrl, sourceId = book.sourceId)
        val chapter = ChapterModel("one", "Capítulo 1", "chapter:one")
        vm.openReader(manga, chapter, listOf(chapter))
        withTimeout(10_000) { vm.readerUiState.first { !it.isLoading && it.pages.isNotEmpty() } }
        vm.updateReaderPageIndex(1)
        vm.flushReaderScrollPosition()
        withTimeout(10_000) { repository.readingProgress.first { it.isNotEmpty() } }
        vm.deleteHistory(book.url)
        withTimeout(10_000) { repository.readingProgress.first { it.isEmpty() } }
        vm.flushReaderScrollPosition()
        delay(400)
        assertTrue(repository.readingProgress.first().isEmpty())
        vm.openReader(manga, chapter, listOf(chapter))
        withTimeout(10_000) { vm.readerUiState.first { !it.isLoading && it.historyRevision == 1L } }
        vm.updateReaderPageIndex(2)
        vm.flushReaderScrollPosition()
        withTimeout(10_000) { repository.readingProgress.first { it.singleOrNull()?.lastPageRead == 2 } }
    }
}
