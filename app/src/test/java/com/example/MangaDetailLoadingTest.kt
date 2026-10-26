package com.example

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.*
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.source.*
import com.example.ui.viewmodel.MangaViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MangaDetailLoadingTest {
    private lateinit var db: AppDatabase
    private val store = ViewModelStore()
    private val manga = MangaModel("https://test.test/manga", "manga", "Dragon Ball", "", sourceId = "test")
    @Before fun prepare() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }
    @After fun close() { store.clear(); db.close(); Dispatchers.resetMain() }
    private fun repository(mangaDao: MangaDao = db.mangaDao()): MangaRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return MangaRepository(mangaDao, db.progressDao(), db.downloadDao(), db.readingSessionDao(),
            ChapterDownloadManager(context, db.downloadDao()), UserPreferencesRepository(context), db.chapterDao())
            .also { it.clearSources() }
    }
    private fun model(repository: MangaRepository): MangaViewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaViewModel(repository, loadBrowseOnInit = false, recommendationLoader = { emptyList() }) as T
    })[MangaViewModel::class.java]
    private fun source(chapters: suspend () -> List<ChapterModel>) = object : MangaSource {
        override val id = "test"
        override val name = id
        override val baseUrl = "https://test.test"
        override val isNsfw = false
        override val lang = "pt-br"
        override val canAggregateChapters = false
        override suspend fun searchManga(query: String) = emptyList<MangaModel>()
        override suspend fun getPopularManga() = emptyList<MangaModel>()
        override suspend fun getMangaDetails(mangaUrl: String) = manga.copy(url = mangaUrl)
        override suspend fun getChapters(mangaUrl: String) = chapters()
        override suspend fun getPages(chapterUrl: String) = emptyList<String>()
    }
    @Test fun `cached chapters are usable before network and survive refresh failure`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        db.chapterDao().insertChapter(ChapterEntity(id = "cached", mangaUrl = manga.url, title = "Chapter 1",
            chapterUrl = "chapter", chapterNumber = 1f, dateUpload = "", language = "pt-br"))
        val repository = repository()
        repository.registerSource(source { started.complete(Unit); release.await(); throw java.io.IOException("offline fixture") })
        val vm = model(repository)
        vm.openMangaDetails(manga)
        try {
            withTimeout(3000) { started.await() }
            val cached = withTimeout(3000) { vm.detailUiState.first { it.chapters.isNotEmpty() } }
            assertEquals("cached", cached.chapters.single().id)
            assertFalse(cached.isLoading)
            release.complete(Unit)
            val failed = withTimeout(3000) { vm.detailUiState.first { it.chapterError != null } }
            assertEquals("cached", failed.chapters.single().id)
        } finally { release.complete(Unit) }
    }
    @Test fun `switching manga removes old favorite observer`() = runBlocking {
        val oldFavorite = MutableStateFlow<MangaEntity?>(null)
        val newFavorite = MutableStateFlow<MangaEntity?>(null)
        val dao = object : MangaDao by MangaRepositoryTest.FakeMangaDao() {
            override fun getMangaByUrl(url: String): Flow<MangaEntity?> = if (url == manga.url) oldFavorite else newFavorite
        }
        val repository = repository(dao)
        repository.registerSource(source { emptyList() })
        val vm = model(repository)
        vm.openMangaDetails(manga)
        withTimeout(3000) { oldFavorite.subscriptionCount.first { it == 1 } }
        val next = manga.copy(url = "https://test.test/next", id = "next")
        vm.openMangaDetails(next)
        withTimeout(3000) { newFavorite.subscriptionCount.first { it == 1 }; oldFavorite.subscriptionCount.first { it == 0 } }
        val state = withTimeout(3000) { vm.detailUiState.first { it.manga?.url == next.url } }
        assertFalse(state.isFavorite)
        assertNull(state.readingProgress)
    }
}
