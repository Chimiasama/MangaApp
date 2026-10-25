package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.*
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiscoveryPersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun repo(db: AppDatabase) = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(), db.readingSessionDao(),
        ChapterDownloadManager(context = context, downloadDao = db.downloadDao()), UserPreferencesRepository(context),
        chapterDao = db.chapterDao(), searchHistoryDao = db.searchHistoryDao())
    @Test fun `five recent searches survive restart and repeated Spy does not create duplicates`() = runBlocking {
        val name = "recent-${java.util.UUID.randomUUID()}"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val repo = repo(db)
            for (query in listOf("Naruto", "Phoenix", "Dragon Ball", "GTO", "Berserk", "Spy")) repo.saveSearchQuery(query)
            coroutineScope { listOf("spy", " SPY ", "Spy").map { async { repo.saveSearchQuery(it) } }.awaitAll() }
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
            val recent = db.searchHistoryDao().getRecentSearchHistory().first()
            assertEquals(5, recent.size)
            assertEquals(1, recent.count { it.query.equals("spy", true) })
            assertFalse(recent.any { it.query == "Naruto" })
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun `external chapters and zero page records have clear errors before page fetch`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = repo(db)
            val external = ChapterModel("ex", "External", "https://api.mangadex.org/chapter/ex", language = "en", externalUrl = "https://publisher.test/read")
            try { repo.getPages(external.chapterUrl, external.id, "mangadex", external); fail("Should explain external hosting") }
            catch (error: ChapterAccessException) { assertEquals(external.externalUrl, error.originalUrl) }
            val empty = external.copy(id = "zero", externalUrl = "", pageCount = 0)
            try { repo.getPages(empty.chapterUrl, empty.id, "mangadex", empty); fail("Should explain missing pages") }
            catch (error: ChapterAccessException) { assertTrue(error.message!!.contains("zero páginas")) }
        } finally { db.close() }
    }
    @Test fun `chapter metadata refresh preserves reading progress and prefers hosted images`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.chapterDao().insertChapter(ChapterEntity("hosted", "manga", "Old title", "chapter", isRead = true, lastPageRead = 7))
            val repo = repo(db)
            repo.clearSources()
            repo.registerSource(object : MangaSource {
                override val id = "fixture"
                override val name = "Fixture"
                override val baseUrl = "https://fixture.test"
                override val lang = "en"
                override val isNsfw = false
                override suspend fun searchManga(query: String): List<MangaModel> = emptyList()
                override suspend fun getPopularManga(): List<MangaModel> = emptyList()
                override suspend fun getMangaDetails(mangaUrl: String): MangaModel = error("unused")
                override suspend fun getPages(chapterUrl: String): List<String> = emptyList()
                override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = listOf(
                    ChapterModel("external", "External", "external", language = "en", externalUrl = "https://publisher.test/read", pageCount = 0),
                    ChapterModel("hosted", "Hosted", "hosted", language = "en", pageCount = 42))
            })
            val chapters = repo.getAggregatedChapters("Phoenix", "manga", "fixture", "en")
            assertEquals("hosted", chapters.single().id)
            val stored = db.chapterDao().getChapterByIdSync("hosted")!!
            assertTrue(stored.isRead); assertEquals(7, stored.lastPageRead)
            assertEquals(42, stored.pageCount); assertEquals("en", stored.language)
        } finally { db.close() }
    }

    @Test fun `version eight migration keeps favorites history and reading progress`() = runBlocking {
        val name = "discovery-migration-${java.util.UUID.randomUUID()}"
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        db.mangaDao().insertManga(MangaEntity("manga", "manga", "Phoenix", "", "", "mangadex", isFavorite = true))
        db.chapterDao().insertChapter(ChapterEntity("chapter", "manga", "Chapter", "link", isRead = true, lastPageRead = 7, language = "en"))
        db.searchHistoryDao().insertOrUpdateSearchQuery(SearchHistoryEntity(query = "Spy"))
        db.close()
        val sqlite = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        sqlite.execSQL("ALTER TABLE chapters DROP COLUMN externalUrl")
        sqlite.execSQL("ALTER TABLE chapters DROP COLUMN pageCount")
        sqlite.execSQL("ALTER TABLE manga_favorites DROP COLUMN publicationYear")
        sqlite.version = 8; sqlite.close()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_8_9).build()
        try {
            assertTrue(migrated.mangaDao().getMangaByUrlSync("manga")!!.isFavorite)
            assertNull(migrated.mangaDao().getMangaByUrlSync("manga")!!.publicationYear)
            val chapter = migrated.chapterDao().getChapterByIdSync("chapter")!!
            assertEquals(7, chapter.lastPageRead); assertTrue(chapter.isRead); assertEquals("en", chapter.language)
            assertEquals("", chapter.externalUrl); assertNull(chapter.pageCount)
            migrated.chapterDao().insertChapter(chapter.copy(externalUrl = "https://publisher.test/read", pageCount = 0))
            assertEquals(0, migrated.chapterDao().getChapterByIdSync("chapter")!!.pageCount)
            assertEquals("Spy", migrated.searchHistoryDao().getRecentSearchHistory().first().single().query)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
