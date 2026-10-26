package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.source.MangaSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepositoryConcurrencyTest {
    private fun repository(): MangaRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloads = MangaRepositoryTest.FakeDownloadDao()
        return MangaRepository(MangaRepositoryTest.FakeMangaDao(), MangaRepositoryTest.FakeProgressDao(),
            downloads, MangaRepositoryTest.FakeReadingSessionDao(), ChapterDownloadManager(context, downloads),
            UserPreferencesRepository(context)).also { it.clearSources() }
    }
    private open class Source(override val id: String) : MangaSource {
        override val name = id
        override val baseUrl = "https://$id.test"
        override val isNsfw = false
        override val lang = "pt-br"
        override suspend fun getPopularManga() = searchManga("")
        override suspend fun searchManga(query: String) = listOf(MangaModel("$baseUrl/manga", id, "Dragon Ball", "", sourceId = id))
        override suspend fun getMangaDetails(mangaUrl: String) = searchManga("").single()
        override suspend fun getChapters(mangaUrl: String) = listOf(ChapterModel(id, "Chapter", "$baseUrl/chapter", chapterNumber = 1f, language = "pt-br"))
        override suspend fun getPages(chapterUrl: String) = emptyList<String>()
    }
    @Test fun `search starts every source before waiting and preserves source order`() = runBlocking {
        val repository = repository()
        val started = List(3) { CompletableDeferred<Unit>() }
        val release = CompletableDeferred<Unit>()
        repeat(3) { index -> repository.registerSource(object : Source("s$index") {
            override suspend fun searchManga(query: String): List<MangaModel> {
                started[index].complete(Unit)
                release.await()
                return super.searchManga(query)
            }
        }) }
        val result = async { repository.searchManga("dragon", "all") }
        try {
            withTimeout(2000) { started.forEach { it.await() } }
            release.complete(Unit)
            assertEquals(listOf("s0", "s1", "s2"), result.await().map { it.sourceId })
        } finally { release.complete(Unit); result.cancelAndJoin() }
    }
    @Test fun `failure of one source keeps successful search results`() = runBlocking {
        val repository = repository()
        repository.registerSource(object : Source("failed") {
            override suspend fun searchManga(query: String): List<MangaModel> = throw java.io.IOException("offline fixture")
        })
        repository.registerSource(Source("working"))
        assertEquals(listOf("working"), repository.searchManga("dragon", "all").map { it.sourceId })
    }
    @Test fun `cancelled search cancels every source instead of returning cache`() = runBlocking {
        val repository = repository()
        val started = List(2) { CompletableDeferred<Unit>() }
        val cancelled = mutableSetOf<Int>()
        repeat(2) { index -> repository.registerSource(object : Source("s$index") {
            override suspend fun searchManga(query: String): List<MangaModel> {
                started[index].complete(Unit)
                try { awaitCancellation() } finally { cancelled += index }
            }
        }) }
        val job = launch { repository.searchManga("dragon", "all"); fail("Cancelled request returned results") }
        try { withTimeout(2000) { started.forEach { it.await() } } }
        finally { job.cancelAndJoin() }
        assertEquals(setOf(0, 1), cancelled)
    }
    @Test fun `secondary chapter search starts while primary chapters are pending`() = runBlocking {
        val repository = repository()
        val primaryStarted = CompletableDeferred<Unit>()
        val secondaryStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        repository.registerSource(object : Source("primary") {
            override suspend fun getChapters(mangaUrl: String): List<ChapterModel> {
                primaryStarted.complete(Unit); release.await()
                return super.getChapters(mangaUrl)
            }
        })
        repository.registerSource(object : Source("secondary") {
            override suspend fun searchManga(query: String): List<MangaModel> {
                secondaryStarted.complete(Unit); release.await()
                return super.searchManga(query)
            }
        })
        val result = async { repository.getAggregatedChapters("Dragon Ball", "https://primary.test/manga", "primary") }
        try {
            withTimeout(2000) { primaryStarted.await(); secondaryStarted.await() }
            release.complete(Unit)
            // The primary source retains priority for duplicate chapter numbers.
            assertEquals("primary", result.await().single().id)
        } finally { release.complete(Unit); result.cancelAndJoin() }
    }
}
