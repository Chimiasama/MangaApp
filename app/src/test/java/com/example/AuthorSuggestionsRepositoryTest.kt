package com.example

import android.content.Context
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.*
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.data.network.MangaDexApiService
import com.example.source.*
import com.example.ui.viewmodel.MangaViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthorSuggestionsRepositoryTest {
    @Test fun `registered MangaDex enriches cached author and suggestions reach detail ViewModel`(): Unit = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = ViewModelStore()
        val requests = java.util.concurrent.CopyOnWriteArrayList<HttpUrl>()
        val relationship = """{"id":"toriyama","type":"author","attributes":{"name":"Toriyama Akira"}}"""
        val dragon = """{"id":"dragon","attributes":{"title":{"en":"Dragon Ball"}},"relationships":[$relationship]}"""
        val slump = """{"id":"slump","attributes":{"title":{"en":"Dr. Slump"}},"relationships":[$relationship]}"""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            requests += url
            val body = when (url.encodedPath) {
                "/manga/dragon" -> """{"data":$dragon}"""
                "/manga" -> """{"data":[$dragon,$slump]}"""
                else -> """{"data":[]}"""
            }
            Response.Builder().request(chain.request()).code(200).message("OK").protocol(Protocol.HTTP_1_1)
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val api = Retrofit.Builder().baseUrl("https://api.mangadex.org/").client(client)
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .build().create(MangaDexApiService::class.java)
            val source = MangaDexSource(apiService = api)
            assertTrue("Author lookup must be enabled on the actual provider", source.supportsAuthorRecommendations)
            val prefs = UserPreferencesRepository(context)
            prefs.setSelectedLanguage("pt-br")
            prefs.setPagePreloadEnabled(false)
            val repository = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(), db.readingSessionDao(),
                ChapterDownloadManager(context, db.downloadDao()), prefs, db.chapterDao(), db.bookmarkDao())
            repository.clearSources()
            repository.registerSource(source)
            val url = "https://api.mangadex.org/manga/dragon"
            db.mangaDao().insertManga(MangaEntity(url, "dragon", "Dragon Ball", "cover.jpg", "", "mangadex"))
            val progress = ProgressEntity(url, "one", "Capítulo 1", "chapter:one", 0, 2)
            db.chapterDao().insertChapter(ChapterEntity("one", url, "Capítulo 1", "chapter:one", language = "pt-br"))
            val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MangaViewModel(repository, loadBrowseOnInit = false, loadHomeOnInit = false,
                        readerPages = { _, _ -> listOf("a", "b") }) as T
            })[MangaViewModel::class.java]
            val observer = launch { vm.detailUiState.collect {} }
            try {
                // The continue-reading route starts with Room metadata, which has no author IDs.
                vm.resumeReadingFromProgress(progress)
                val state = withTimeout(10_000) { vm.detailUiState.first { it.authorSuggestions.items.isNotEmpty() } }
                assertEquals("Toriyama Akira", state.authorSuggestions.author)
                assertEquals(listOf("slump"), state.authorSuggestions.items.map { it.id })
                assertEquals(url, state.authorSuggestions.mangaUrl)
                assertTrue(requests.any { it.encodedPath == "/manga/dragon" })
                val query = requests.single { it.encodedPath == "/manga" }
                assertEquals(listOf("toriyama"), query.queryParameterValues("authors[]"))
                assertEquals(listOf("pt-br"), query.queryParameterValues("availableTranslatedLanguage[]"))
            } finally { observer.cancel() }
        } finally {
            store.clear(); db.close(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); Dispatchers.resetMain()
        }
    }
}
