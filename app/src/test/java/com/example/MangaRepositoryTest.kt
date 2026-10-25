package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.BookmarkDao
import com.example.data.local.BookmarkEntity
import com.example.data.local.ChapterDao
import com.example.data.local.ChapterEntity
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.MangaDao
import com.example.data.local.MangaEntity
import com.example.data.local.ProgressDao
import com.example.data.local.ProgressEntity
import com.example.data.local.ReadingSessionDao
import com.example.data.local.ReadingSessionEntity
import com.example.data.local.SearchHistoryDao
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.source.MangaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MangaRepositoryTest {

    private lateinit var mangaDao: MangaDao
    private lateinit var progressDao: ProgressDao
    private lateinit var downloadDao: DownloadDao
    private lateinit var readingSessionDao: ReadingSessionDao
    private lateinit var downloadManager: ChapterDownloadManager
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var repository: MangaRepository

    class FakeMangaDao : MangaDao {
        override fun getFavoriteMangas(): Flow<List<MangaEntity>> = flowOf(emptyList())
        override suspend fun getAllFavoritesSync(): List<MangaEntity> = emptyList()
        override fun getMangaByUrl(url: String): Flow<MangaEntity?> = flowOf(null)
        override suspend fun getMangaByUrlSync(url: String): MangaEntity? = null
        override suspend fun getAllCachedMangasSync(): List<MangaEntity> = emptyList()
        override suspend fun getCachedMangasBySourceSync(sourceId: String): List<MangaEntity> = emptyList()
        override suspend fun insertManga(manga: MangaEntity) {}
        override suspend fun deleteFavoriteByUrl(url: String) {}
        override suspend fun updateFavoriteStatus(url: String, isFavorite: Boolean) {}
    }

    class FakeProgressDao : ProgressDao {
        override fun getAllProgress(): Flow<List<ProgressEntity>> = flowOf(emptyList())
        override suspend fun getAllProgressSync(): List<ProgressEntity> = emptyList()
        override fun getProgressForManga(mangaUrl: String): Flow<ProgressEntity?> = flowOf(null)
        override suspend fun getProgressForMangaSync(mangaUrl: String): ProgressEntity? = null
        override suspend fun saveProgress(progress: ProgressEntity) {}
        override suspend fun deleteProgress(mangaUrl: String) {}
    }

    class FakeDownloadDao : DownloadDao {
        override fun getAllDownloads(): Flow<List<DownloadEntity>> = flowOf(emptyList())
        override fun getDownload(chapterId: String): Flow<DownloadEntity?> = flowOf(null)
        override suspend fun getDownloadSync(chapterId: String): DownloadEntity? = null
        override fun getCompletedDownloads(): Flow<List<DownloadEntity>> = flowOf(emptyList())
        override suspend fun insertDownload(download: DownloadEntity) {}
        override suspend fun updateDownloadStatus(
            chapterId: String,
            downloadedPagesCount: Int,
            progress: Float,
            state: String,
            totalPages: Int,
            localPath: String
        ) {}
        override suspend fun deleteDownload(chapterId: String) {}
    }

    class FakeReadingSessionDao : ReadingSessionDao {
        override fun getAllSessions(): Flow<List<ReadingSessionEntity>> = flowOf(emptyList())
        override suspend fun insertSession(session: ReadingSessionEntity) {}
        override fun getTotalChaptersCount(): Flow<Int> = flowOf(0)
        override fun getTotalPagesRead(): Flow<Int?> = flowOf(0)
        override fun getTotalDurationSeconds(): Flow<Long?> = flowOf(0L)
        override suspend fun clearAllStats() {}
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mangaDao = FakeMangaDao()
        progressDao = FakeProgressDao()
        downloadDao = FakeDownloadDao()
        readingSessionDao = FakeReadingSessionDao()
        downloadManager = ChapterDownloadManager(downloadDao = downloadDao, context = context)
        preferencesRepository = UserPreferencesRepository(context = context)

        repository = MangaRepository(
            mangaDao = mangaDao,
            progressDao = progressDao,
            downloadDao = downloadDao,
            readingSessionDao = readingSessionDao,
            downloadManager = downloadManager,
            preferencesRepository = preferencesRepository
        )
        repository.clearSources()
    }

    @Test
    fun `test getAggregatedChapters combines primary and secondary sources with fallback language`() = runBlocking {
        // Primary source returns sparse PT-BR list (only recent chapters 114, 115)
        val primarySource = object : MangaSource {
            override val id: String = "primary_src"
            override val name: String = "Primary Source"
            override val baseUrl: String = "https://primary.com"
            override val isNsfw: Boolean = false
            override val lang: String = "pt-br"

            override suspend fun getPopularManga(): List<MangaModel> = emptyList()
            override suspend fun searchManga(query: String): List<MangaModel> = emptyList()
            override suspend fun getMangaDetails(mangaUrl: String): MangaModel = MangaModel(url = mangaUrl, id = "1", title = "My Manga", coverUrl = "", description = "", sourceId = id)
            override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, "pt-br")
            override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> {
                return if (language == "pt-br") {
                    listOf(
                        ChapterModel(id = "ch-115", title = "Cap. 115", chapterUrl = "p115", chapterNumber = 115f, dateUpload = ""),
                        ChapterModel(id = "ch-114", title = "Cap. 114", chapterUrl = "p114", chapterNumber = 114f, dateUpload = "")
                    )
                } else {
                    emptyList()
                }
            }
            override suspend fun getPages(chapterUrl: String): List<String> = emptyList()
        }

        // Secondary source provides older chapters 1..113 in English fallback
        val secondarySource = object : MangaSource {
            override val id: String = "comick"
            override val name: String = "Comick Source"
            override val baseUrl: String = "https://comick.app"
            override val isNsfw: Boolean = false
            override val lang: String = "pt-br / en"

            override suspend fun getPopularManga(): List<MangaModel> = emptyList()
            override suspend fun searchManga(query: String): List<MangaModel> = emptyList()
            override suspend fun searchManga(query: String, language: String): List<MangaModel> {
                return listOf(MangaModel(url = "https://comick.app/comic/my-manga", id = "cm-1", title = query, coverUrl = "", description = "", sourceId = id))
            }
            override suspend fun getMangaDetails(mangaUrl: String): MangaModel = MangaModel(url = mangaUrl, id = "cm-1", title = "My Manga", coverUrl = "", description = "", sourceId = id)
            override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, "pt-br")
            override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> {
                return if (language == "en") {
                    (1..115).map { num ->
                        ChapterModel(
                            id = "sec-ch-$num",
                            title = "Cap. $num [EN]",
                            chapterUrl = "sec-$num",
                            chapterNumber = num.toFloat(),
                            dateUpload = ""
                        )
                    }
                } else {
                    emptyList()
                }
            }
            override suspend fun getPages(chapterUrl: String): List<String> = emptyList()
        }

        repository.registerSource(primarySource)
        repository.registerSource(secondarySource)

        val aggregated = repository.getAggregatedChapters(
            mangaTitle = "My Manga",
            mangaUrl = "https://primary.com/manga/1",
            primarySourceId = "primary_src",
            language = "pt-br"
        )

        // Total should be 115 chapters (1 to 115)
        assertEquals(115, aggregated.size)

        // Chapter 115 should come from primary source (PT-BR) without [EN] tag
        val ch115 = aggregated.find { it.chapterNumber == 115f }
        assertNotNull(ch115)
        assertEquals("Cap. 115", ch115?.title)

        // Chapter 1 should come from secondary source fallback (EN)
        val ch1 = aggregated.find { it.chapterNumber == 1f }
        assertNotNull(ch1)
        assertEquals("Cap. 1 [EN]", ch1?.title)
    }

    @Test
    fun `test getAggregatedChapters prioritizes primary language when both sources offer the same chapter`() = runBlocking {
        val primarySource = object : MangaSource {
            override val id: String = "primary_src"
            override val name: String = "Primary"
            override val baseUrl: String = "https://primary.com"
            override val isNsfw: Boolean = false
            override val lang: String = "pt-br"

            override suspend fun getPopularManga(): List<MangaModel> = emptyList()
            override suspend fun searchManga(query: String): List<MangaModel> = emptyList()
            override suspend fun getMangaDetails(mangaUrl: String): MangaModel = MangaModel(url = mangaUrl, id = "1", title = "Manga", coverUrl = "", description = "", sourceId = id)
            override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, "pt-br")
            override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> {
                return listOf(
                    ChapterModel(id = "ch-1-pt", title = "Cap. 1 - Inicio", chapterUrl = "p1", chapterNumber = 1f, dateUpload = "")
                )
            }
            override suspend fun getPages(chapterUrl: String): List<String> = emptyList()
        }

        val secondarySource = object : MangaSource {
            override val id: String = "secondary_src"
            override val name: String = "Secondary"
            override val baseUrl: String = "https://secondary.com"
            override val isNsfw: Boolean = false
            override val lang: String = "en"

            override suspend fun getPopularManga(): List<MangaModel> = emptyList()
            override suspend fun searchManga(query: String): List<MangaModel> = emptyList()
            override suspend fun searchManga(query: String, language: String): List<MangaModel> = listOf(
                MangaModel(url = "https://secondary.com/manga/1", id = "2", title = query, coverUrl = "", description = "", sourceId = id)
            )
            override suspend fun getMangaDetails(mangaUrl: String): MangaModel = MangaModel(url = mangaUrl, id = "2", title = "Manga", coverUrl = "", description = "", sourceId = id)
            override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, "en")
            override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> {
                return listOf(
                    ChapterModel(id = "ch-1-en", title = "Cap. 1 [EN]", chapterUrl = "s1", chapterNumber = 1f, dateUpload = ""),
                    ChapterModel(id = "ch-2-en", title = "Cap. 2 [EN]", chapterUrl = "s2", chapterNumber = 2f, dateUpload = "")
                )
            }
            override suspend fun getPages(chapterUrl: String): List<String> = emptyList()
        }

        repository.registerSource(primarySource)
        repository.registerSource(secondarySource)

        val aggregated = repository.getAggregatedChapters(
            mangaTitle = "Manga",
            mangaUrl = "https://primary.com/manga/1",
            primarySourceId = "primary_src",
            language = "pt-br"
        )

        assertEquals(2, aggregated.size)
        // Chapter 1 must be PT-BR version
        val ch1 = aggregated.find { it.chapterNumber == 1f }
        assertEquals("Cap. 1 - Inicio", ch1?.title)
        // Chapter 2 is added from secondary
        val ch2 = aggregated.find { it.chapterNumber == 2f }
        assertEquals("Cap. 2 [EN]", ch2?.title)
    }
}
