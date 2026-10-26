package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.local.ProgressEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.source.MangaSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Controlled source latency; real Room. These are host timings, not device/frame timings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LatencyBenchmarkTest {
    @Test fun measureRepositoryPaths() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(),
                db.readingSessionDao(), ChapterDownloadManager(context, db.downloadDao()),
                UserPreferencesRepository(context), db.chapterDao())
            repository.clearSources()
            repeat(3) { index -> repository.registerSource(object : MangaSource {
                override val id = "source$index"
                override val name = id
                override val baseUrl = "https://$id.test"
                override val isNsfw = false
                override val lang = "pt-br"
                override suspend fun getPopularManga(): List<MangaModel> = searchManga("")
                override suspend fun searchManga(query: String): List<MangaModel> {
                    delay(80)
                    return listOf(MangaModel("$baseUrl/manga", id, "Dragon Ball", "", sourceId = id))
                }
                override suspend fun getMangaDetails(mangaUrl: String): MangaModel = searchManga("").single()
                override suspend fun getChapters(mangaUrl: String): List<ChapterModel> {
                    delay(80)
                    return listOf(ChapterModel("chapter$index", "Chapter", "$baseUrl/chapter", chapterNumber = (index + 1).toFloat(), language = "pt-br"))
                }
                override suspend fun getPages(chapterUrl: String): List<String> = emptyList()
            }) }
            repeat(1000) { index -> db.progressDao().saveProgress(ProgressEntity(
                "manga$index", "chapter$index", "Chapter $index", "url$index", 1, 20, timestamp = index.toLong())) }
            suspend fun measure(name: String, block: suspend () -> Unit) {
                block() // Warm JVM, Room and statements before collecting samples.
                val samples = (1..7).map {
                    val start = System.nanoTime()
                    block()
                    (System.nanoTime() - start) / 1_000_000.0
                }.sorted()
                println("LATENCY $name samples_ms=${samples.joinToString(",") { "%.3f".format(java.util.Locale.US, it) }} median_ms=${samples[3]} max_ms=${samples.last()}")
            }
            measure("search_all_3x80ms") { assertEquals(3, repository.searchManga("dragon", "all").size) }
            measure("popular_all_3x80ms") { assertEquals(3, repository.getPopularManga("all").size) }
            measure("open_aggregated_chapters") { assertEquals(3, repository.getAggregatedChapters("Dragon Ball", "https://source0.test/manga", "source0").size) }
            measure("history_1000_first_emission") {
                val history = repository.readingProgress.first()
                assertEquals(1000, history.size)
                assertEquals("manga999", history.first().mangaUrl)
            }
        } finally { db.close() }
    }
}
