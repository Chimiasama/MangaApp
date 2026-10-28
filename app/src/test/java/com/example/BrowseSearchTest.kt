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
import com.example.source.*
import com.example.ui.viewmodel.MangaViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
class BrowseSearchTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: MangaRepository
    private val store = ViewModelStore()
    private val dragon = MangaModel("https://example.test/dragon-ball", "dragon", "Dragon Ball", "", sourceId = "test")

    @Before fun prepare() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val prefs = UserPreferencesRepository(context)
        prefs.setSelectedLanguage("pt-br")
        repository = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(), db.readingSessionDao(),
            ChapterDownloadManager(context, db.downloadDao()), prefs, db.chapterDao())
        repository.clearSources()
    }
    @After fun close() { store.clear(); db.close(); Dispatchers.resetMain() }
    private fun model() = ViewModelProvider(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaViewModel(repository) as T
    })[MangaViewModel::class.java]
    private abstract class Source : MangaSource {
        override val id = "test"
        override val name = "Test source"
        override val baseUrl = "https://example.test/"
        override val isNsfw = false
        override val lang = "pt-br"
        override suspend fun getPopularManga(): List<MangaModel> = emptyList()
        override suspend fun getMangaDetails(mangaUrl: String): MangaModel = error("not used")
        override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = emptyList()
        override suspend fun getPages(chapterUrl: String): List<String> = emptyList()
    }
    @Test fun `late startup catalog cannot erase Dragon Ball search results`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        repository.registerSource(object : Source() {
            override suspend fun getPopularManga(): List<MangaModel> = withContext(NonCancellable) {
                started.complete(Unit); release.await(); finished.complete(Unit); listOf(dragon.copy(id = "naruto", url = "https://example.test/naruto", title = "Naruto"))
            }
            override suspend fun searchManga(query: String): List<MangaModel> = listOf(dragon)
        })
        val vm = model()
        withTimeout(10_000) { started.await() }
        vm.submitSearch("dragon ball")
        withTimeout(10_000) { vm.browseUiState.first { !it.isLoading && it.searchQuery == "dragon ball" && it.mangaList.isNotEmpty() } }
        release.complete(Unit)
        withTimeout(10_000) { finished.await() }
        delay(100)
        val result = vm.browseUiState.first { it.searchQuery == "dragon ball" && !it.isLoading }
        assertEquals(listOf("Dragon Ball"), result.mangaList.map { it.title })
    }
    @Test fun `older empty query cannot overwrite a newer matching search`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        repository.registerSource(object : Source() {
            override suspend fun searchManga(query: String): List<MangaModel> {
                if (query == "dragon ball super") return withContext(NonCancellable) {
                    started.complete(Unit); release.await(); finished.complete(Unit); emptyList()
                }
                return listOf(dragon)
            }
        })
        val vm = model()
        vm.submitSearch("dragon ball super")
        withTimeout(10_000) { started.await() }
        vm.submitSearch("dragon ball")
        withTimeout(10_000) { vm.browseUiState.first { !it.isLoading && it.mangaList.isNotEmpty() } }
        release.complete(Unit)
        withTimeout(10_000) { finished.await() }
        delay(100)
        assertEquals("Dragon Ball", vm.browseUiState.first { !it.isLoading }.mangaList.single().title)
    }
    @Test fun `network failure with empty cache is an error instead of zero search matches`() = runBlocking {
        repository.registerSource(object : Source() {
            override suspend fun searchManga(query: String): List<MangaModel> = throw java.io.IOException("offline")
        })
        val vm = model()
        vm.submitSearch("dragon ball")
        val result = withTimeout(10_000) { vm.browseUiState.first { it.searchQuery == "dragon ball" && !it.isLoading } }
        assertNotNull("A failed search must offer a connection error and retry", result.errorMessage)
        assertTrue(result.mangaList.isEmpty())
    }
    @Test fun `typing debounces requests and uses only the final query`() = runBlocking {
        val queries = mutableListOf<String>()
        repository.registerSource(object : Source() {
            override suspend fun searchManga(query: String): List<MangaModel> { queries += query; return listOf(dragon) }
        })
        val vm = model()
        vm.searchManga("d")
        vm.searchManga("dragon")
        vm.searchManga("dragon ball")
        withTimeout(10_000) { vm.browseUiState.first { it.searchQuery == "dragon ball" && !it.isLoading } }
        assertEquals(listOf("dragon ball"), queries)
    }
    @Test fun `search normalizes extra whitespace before contacting a source`() = runBlocking {
        val queries = mutableListOf<String>()
        repository.registerSource(object : Source() {
            override suspend fun searchManga(query: String): List<MangaModel> { queries += query; return listOf(dragon) }
        })
        val vm = model()
        vm.submitSearch("  dragon   ball  ")
        withTimeout(10_000) { vm.browseUiState.first { !it.isLoading && it.mangaList.isNotEmpty() } }
        assertEquals(listOf("dragon ball"), queries)
    }

    @Test fun `matchesYearRange accurately validates manga publication years`() {
        // Range 1900 - 1950
        assertTrue(com.example.ui.viewmodel.matchesYearRange(1920, "1900-1950"))
        assertTrue(com.example.ui.viewmodel.matchesYearRange(1950, "1900-1950"))
        assertFalse(com.example.ui.viewmodel.matchesYearRange(1951, "1900-1950"))

        // Range 1980 - 1990
        assertTrue(com.example.ui.viewmodel.matchesYearRange(1989, "1980-1990"))
        assertFalse(com.example.ui.viewmodel.matchesYearRange(1997, "1980-1990"))

        // Range 1990 - 2000
        assertTrue(com.example.ui.viewmodel.matchesYearRange(1997, "1990-2000"))
        assertTrue(com.example.ui.viewmodel.matchesYearRange(1999, "1990-2000"))
        assertFalse(com.example.ui.viewmodel.matchesYearRange(2001, "1990-2000"))

        // Range 2010 - 2020
        assertTrue(com.example.ui.viewmodel.matchesYearRange(2018, "2010-2020"))

        // Range 2020 - 2030
        assertTrue(com.example.ui.viewmodel.matchesYearRange(2020, "2020-2030"))

        // Description fallback when year field is null
        assertTrue(com.example.ui.viewmodel.matchesYearRange(null, "1900-1950", "Lançado no ano de 1935."))
        assertFalse(com.example.ui.viewmodel.matchesYearRange(null, "1900-1950", "Lançado no ano de 1988."))

        // 'all' range
        assertTrue(com.example.ui.viewmodel.matchesYearRange(1940, "all"))
        assertTrue(com.example.ui.viewmodel.matchesYearRange(2022, "all"))
    }

    @Test fun `year range filter filters popular manga in ViewModel`() = runBlocking {
        val manga1940 = MangaModel("https://test.com/m1", "1", "Old Manga", "", sourceId = "test", year = 1940)
        val manga1985 = MangaModel("https://test.com/m2", "2", "80s Classic", "", sourceId = "test", year = 1985)
        val manga1997 = MangaModel("https://test.com/m3", "3", "90s Hit", "", sourceId = "test", year = 1997)
        val manga2018 = MangaModel("https://test.com/m4", "4", "Modern Shounen", "", sourceId = "test", year = 2018)

        repository.registerSource(object : Source() {
            override suspend fun getPopularManga(): List<MangaModel> = listOf(manga1940, manga1985, manga1997, manga2018)
            override suspend fun searchManga(query: String): List<MangaModel> = emptyList()
        })

        val vm = model()
        // Wait for initial load
        withTimeout(10_000) { vm.browseUiState.first { !it.isLoading && it.mangaList.size == 4 } }

        // Select 1900-1950
        vm.selectYearRange("1900-1950")
        val result1900 = withTimeout(10_000) { vm.browseUiState.first { !it.isLoading && it.selectedYearRange == "1900-1950" } }
        assertEquals(listOf("Old Manga"), result1900.mangaList.map { it.title })

        // Select 1990-2000
        vm.selectYearRange("1990-2000")
        val result1990 = withTimeout(10_000) { vm.browseUiState.first { !it.isLoading && it.selectedYearRange == "1990-2000" } }
        assertEquals(listOf("90s Hit"), result1990.mangaList.map { it.title })

        // Clear filters
        vm.clearFiltersAndReload()
        val resultClear = withTimeout(10_000) { vm.browseUiState.first { !it.isLoading && it.selectedYearRange == "all" } }
        assertEquals(4, resultClear.mangaList.size)
    }
}
