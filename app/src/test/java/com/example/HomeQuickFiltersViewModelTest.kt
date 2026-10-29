package com.example

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.home.HomeQuickFilters
import com.example.source.*
import com.example.ui.viewmodel.MangaViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeQuickFiltersViewModelTest {
    @Test fun `home queries chapter language and filters before carousel limits with all choices retained`() = runBlocking {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = ViewModelStore()
        try {
            val repository = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(), db.readingSessionDao(),
                ChapterDownloadManager(context, db.downloadDao()), UserPreferencesRepository(context), db.chapterDao(), db.bookmarkDao())
            val requests = mutableListOf<Pair<String, CatalogFilters>>()
            val target = MangaModel("target", "target", "Romance alvo", "", genres = listOf("Romance", "Shounen"),
                sourceId = "home-fixture", originalLanguage = "ja")
            repository.registerSource(object : MangaSource {
                override val id = "home-fixture"
                override val name = "Teste"
                override val baseUrl = "https://example.test"
                override val isNsfw = false
                override val lang = "en"
                override suspend fun getFilteredCatalog(query: String, language: String, yearRange: String, filters: CatalogFilters): List<MangaModel> {
                    requests += language to filters
                    return (1..30).map { target.copy(id = "$it", url = "$it", genres = listOf("Action")) } +
                        target + target.copy(id = "wrong-source", url = "other", sourceId = "other")
                }
                override suspend fun getPopularManga() = emptyList<MangaModel>()
                override suspend fun searchManga(query: String) = emptyList<MangaModel>()
                override suspend fun getMangaDetails(mangaUrl: String) = target
                override suspend fun getChapters(mangaUrl: String) = emptyList<ChapterModel>()
                override suspend fun getPages(chapterUrl: String) = emptyList<String>()
            })
            val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MangaViewModel(repository, loadBrowseOnInit = false, loadHomeOnInit = false) as T
            })[MangaViewModel::class.java]
            val filters = HomeQuickFilters("home-fixture", "en", "genre:romance")
            vm.updateHomeQuickFilters(filters)
            main.scheduler.advanceTimeBy(300)
            main.scheduler.runCurrent()
            val state = withTimeout(10_000) { vm.homeUiState.first { !it.isLoading } }
            assertEquals(filters, state.quickFilters)
            assertEquals(listOf("target"), state.sections.flatMap { it.items }.map { it.id })
            assertEquals(listOf("en" to CatalogFilters(included = setOf("genre:romance"))), requests)
            // The Room cache now contains books; a successful empty English query must not reuse them.
            vm.updateHomeQuickFilters(filters.copy(genreId = null))
            main.scheduler.advanceTimeBy(300)
            main.scheduler.runCurrent()
            val empty = withTimeout(10_000) { vm.homeUiState.first { !it.isLoading } }
            assertEquals(filters.copy(genreId = null), empty.quickFilters)
            assertTrue(empty.sections.isEmpty())
            assertNull(empty.error)
        } finally {
            store.clear()
            db.close()
            Dispatchers.resetMain()
        }
    }
}
