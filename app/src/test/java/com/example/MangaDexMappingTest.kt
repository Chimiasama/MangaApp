package com.example

import com.example.data.local.Chapter
import com.example.data.local.Manga
import com.example.data.mapping.MangaDexMapper
import com.example.data.mapping.toChapter
import com.example.data.mapping.toChapterModel
import com.example.data.mapping.toDomainModel
import com.example.data.mapping.toManga
import com.example.data.mapping.toMangaEntity
import com.example.data.mapping.toMangaModel
import com.example.data.network.MangaDexApiService
import com.example.data.network.model.MangaDexAtHomeChapterDto
import com.example.data.network.model.MangaDexAtHomeResponse
import com.example.data.network.model.MangaDexChapterAttributesDto
import com.example.data.network.model.MangaDexChapterDto
import com.example.data.network.model.MangaDexChapterFeedResponse
import com.example.data.network.model.MangaDexMangaAttributesDto
import com.example.data.network.model.MangaDexMangaDetailsResponse
import com.example.data.network.model.MangaDexMangaDto
import com.example.data.network.model.MangaDexMangaListResponse
import com.example.data.network.model.MangaDexRelationshipAttributesDto
import com.example.data.network.model.MangaDexRelationshipDto
import com.example.data.network.model.MangaDexTagAttributesDto
import com.example.data.network.model.MangaDexTagDto
import com.example.data.local.MangaEntity
import com.example.source.ChapterModel
import com.example.source.MangaDexSource
import com.example.source.MangaModel
import com.example.source.MangaSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MangaDexMapper] and [MangaDexSource], validating clean transformation
 * between MangaDex API responses and standard Room database models ([Manga], [Chapter]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MangaDexMappingTest {

    @Test
    fun `test mapping MangaDexMangaDto to Room Manga entity with Portuguese priority`() {
        val mangaDto = MangaDexMangaDto(
            id = "manga-uuid-1",
            type = "manga",
            attributes = MangaDexMangaAttributesDto(
                title = mapOf("en" to "Solo Leveling", "pt-br" to "Solo Leveling (PT-BR)"),
                altTitles = listOf(
                    mapOf("en" to "Only I Level Up"),
                    mapOf("pt-br" to "Subindo de Nível Sozinho")
                ),
                description = mapOf("pt-br" to "Em um mundo onde caçadores despertam...", "en" to "In a world..."),
                status = "completed",
                contentRating = "safe",
                tags = listOf(
                    MangaDexTagDto(
                        id = "tag-1",
                        attributes = MangaDexTagAttributesDto(name = mapOf("en" to "Ação"))
                    ),
                    MangaDexTagDto(
                        id = "tag-2",
                        attributes = MangaDexTagAttributesDto(name = mapOf("en" to "Fantasia"))
                    )
                )
            ),
            relationships = listOf(
                MangaDexRelationshipDto(
                    id = "rel-cover-1",
                    type = "cover_art",
                    attributes = MangaDexRelationshipAttributesDto(fileName = "cover123.jpg")
                ),
                MangaDexRelationshipDto(
                    id = "rel-author-1",
                    type = "author",
                    attributes = MangaDexRelationshipAttributesDto(name = "Chugong")
                )
            )
        )

        // Map to Room database entity (typealias Manga = MangaEntity)
        val mangaEntity: Manga = mangaDto.toManga()

        assertEquals("manga-uuid-1", mangaEntity.id)
        assertEquals("Solo Leveling (PT-BR)", mangaEntity.title)
        assertEquals("https://uploads.mangadex.org/covers/manga-uuid-1/cover123.jpg.256.jpg", mangaEntity.coverUrl)
        assertEquals("Em um mundo onde caçadores despertam...", mangaEntity.description)
        assertEquals("Ação, Fantasia", mangaEntity.genres)
        assertFalse(mangaEntity.isNsfw)
        assertEquals("https://api.mangadex.org/manga/manga-uuid-1", mangaEntity.url)
        assertEquals("mangadex", mangaEntity.sourceId)

        // Also test domain MangaModel
        val domainModel = mangaDto.toMangaModel()
        assertEquals("Solo Leveling (PT-BR)", domainModel.title)
        assertEquals("Chugong", domainModel.author)
        assertEquals("completed", domainModel.status)
        assertEquals(listOf("Ação", "Fantasia"), domainModel.genres)
    }

    @Test
    fun `test mapping MangaDexChapterDto to Room Chapter entity`() {
        val chapterDto = MangaDexChapterDto(
            id = "chapter-uuid-42",
            type = "chapter",
            attributes = MangaDexChapterAttributesDto(
                volume = "1",
                chapter = "15.5",
                title = "O Retorno das Sombras",
                translatedLanguage = "pt-br",
                publishAt = "2024-05-10T12:00:00Z",
                pages = 28
            )
        )

        val mangaUrl = "https://api.mangadex.org/manga/manga-uuid-1"
        val chapterEntity: Chapter = chapterDto.toChapter(mangaUrl)

        assertEquals("chapter-uuid-42", chapterEntity.id)
        assertEquals(mangaUrl, chapterEntity.mangaUrl)
        assertEquals("Cap. 15.5 - O Retorno das Sombras", chapterEntity.title)
        assertEquals(15.5f, chapterEntity.chapterNumber, 0.001f)
        assertEquals("2024-05-10", chapterEntity.dateUpload)
        assertEquals(28, chapterEntity.totalPages)
        assertEquals("https://api.mangadex.org/chapter/chapter-uuid-42", chapterEntity.chapterUrl)
        assertFalse(chapterEntity.isRead)

        // Also test domain ChapterModel
        val chapterModel = chapterDto.toChapterModel()
        assertEquals("chapter-uuid-42", chapterModel.id)
        assertEquals("Cap. 15.5 - O Retorno das Sombras", chapterModel.title)
        assertEquals(15.5f, chapterModel.chapterNumber, 0.001f)
    }

    @Test
    fun `test bidirectional entity and domain model conversion`() {
        val originalEntity: Manga = MangaDexMapper.toMangaEntity(
            MangaDexMangaDto(
                id = "m-test",
                attributes = MangaDexMangaAttributesDto(
                    title = mapOf("en" to "Test Manga"),
                    description = mapOf("en" to "Description Test"),
                    tags = listOf(MangaDexTagDto(attributes = MangaDexTagAttributesDto(name = mapOf("en" to "Shounen"))))
                )
            )
        )

        val domain = originalEntity.toDomainModel()
        assertEquals("Test Manga", domain.title)
        assertEquals(listOf("Shounen"), domain.genres)

        val backToEntity = domain.toMangaEntity(isFavorite = true)
        assertEquals(originalEntity.id, backToEntity.id)
        assertEquals(originalEntity.title, backToEntity.title)
        assertTrue(backToEntity.isFavorite)
    }

    @Test
    fun `test MangaDexSource using mock Retrofit ApiService returns mapped data`() = runBlocking {
        val fakeApiService = object : MangaDexApiService {
            override suspend fun getMangaList(
                limit: Int,
                offset: Int,
                title: String?,
                translatedLanguages: List<String>?,
                orderFollowedCount: String?,
                orderRelevance: String?,
                includes: List<String>,
                contentRatings: List<String>,
                userAgent: String
            ): MangaDexMangaListResponse {
                return MangaDexMangaListResponse(
                    result = "ok",
                    data = listOf(
                        MangaDexMangaDto(
                            id = "mock-1",
                            attributes = MangaDexMangaAttributesDto(
                                title = mapOf("en" to "Mocked Adventure", "pt-br" to "Aventura Simulada"),
                                description = mapOf("pt-br" to "Uma grande jornada"),
                                status = "ongoing"
                            )
                        )
                    )
                )
            }

            override suspend fun getMangaDetails(
                mangaId: String,
                includes: List<String>,
                userAgent: String
            ): MangaDexMangaDetailsResponse {
                return MangaDexMangaDetailsResponse(
                    result = "ok",
                    data = MangaDexMangaDto(
                        id = mangaId,
                        attributes = MangaDexMangaAttributesDto(
                            title = mapOf("pt-br" to "Detalhe do Mangá $mangaId"),
                            description = mapOf("pt-br" to "Descrição detalhada")
                        )
                    )
                )
            }

            override suspend fun getChapterFeed(
                mangaId: String,
                limit: Int,
                offset: Int,
                translatedLanguages: List<String>?,
                orderChapter: String,
                contentRatings: List<String>,
                userAgent: String
            ): MangaDexChapterFeedResponse {
                return MangaDexChapterFeedResponse(
                    result = "ok",
                    data = listOf(
                        MangaDexChapterDto(
                            id = "ch-101",
                            attributes = MangaDexChapterAttributesDto(
                                chapter = "1",
                                title = "Início",
                                translatedLanguage = "pt-br",
                                pages = 20
                            )
                        )
                    )
                )
            }

            override suspend fun getAtHomeServer(
                chapterId: String,
                userAgent: String
            ): MangaDexAtHomeResponse {
                return MangaDexAtHomeResponse(
                    result = "ok",
                    baseUrl = "https://uploads.mangadex.org",
                    chapter = MangaDexAtHomeChapterDto(
                        hash = "secret-hash",
                        data = listOf("page1.jpg", "page2.jpg")
                    )
                )
            }
        }

        val source = MangaDexSource(apiService = fakeApiService)

        // 1. Popular manga
        val popular = source.getPopularManga()
        assertEquals(1, popular.size)
        assertEquals("Aventura Simulada", popular[0].title)

        // 2. Room entities direct method
        val popularEntities: List<Manga> = source.getPopularMangaAsEntities()
        assertEquals(1, popularEntities.size)
        assertEquals("Aventura Simulada", popularEntities[0].title)

        // 3. Search manga
        val searchResults = source.searchManga("Aventura")
        assertEquals(1, searchResults.size)
        assertEquals("Aventura Simulada", searchResults[0].title)

        // 4. Details
        val details = source.getMangaDetails("https://api.mangadex.org/manga/mock-1")
        assertEquals("Detalhe do Mangá mock-1", details.title)

        // 5. Chapters
        val chapters = source.getChapters("https://api.mangadex.org/manga/mock-1")
        assertEquals(1, chapters.size)
        assertEquals("Cap. 1 - Início", chapters[0].title)

        // 6. Chapter Room entities direct method
        val chapterEntities: List<Chapter> = source.getChaptersAsEntities("https://api.mangadex.org/manga/mock-1")
        assertEquals(1, chapterEntities.size)
        assertEquals("ch-101", chapterEntities[0].id)
        assertEquals(20, chapterEntities[0].totalPages)

        // 7. Pages
        val pages = source.getPages("https://api.mangadex.org/chapter/ch-101")
        assertEquals(2, pages.size)
        assertEquals("https://uploads.mangadex.org/data/secret-hash/page1.jpg", pages[0])
        assertEquals("https://uploads.mangadex.org/data/secret-hash/page2.jpg", pages[1])
    }

    @Test
    fun `test fetchChaptersForLanguage merges fallback chapters when primary language chapters are sparse`() = runBlocking {
        val fakeApiService = object : MangaDexApiService {
            override suspend fun getMangaList(
                limit: Int, offset: Int, title: String?, translatedLanguages: List<String>?,
                orderFollowedCount: String?, orderRelevance: String?, includes: List<String>,
                contentRatings: List<String>, userAgent: String
            ): MangaDexMangaListResponse = MangaDexMangaListResponse("ok", "collection", emptyList())

            override suspend fun getMangaDetails(
                mangaId: String, includes: List<String>, userAgent: String
            ): MangaDexMangaDetailsResponse = MangaDexMangaDetailsResponse("ok", "entity", MangaDexMangaDto(mangaId))

            override suspend fun getChapterFeed(
                mangaId: String, limit: Int, offset: Int, translatedLanguages: List<String>?,
                orderChapter: String, contentRatings: List<String>, userAgent: String
            ): MangaDexChapterFeedResponse {
                val isPt = translatedLanguages?.contains("pt-br") == true
                val isEn = translatedLanguages?.contains("en") == true

                return if (isPt) {
                    // Only 1 chapter available in PT-BR (Ch 115.5)
                    MangaDexChapterFeedResponse(
                        result = "ok",
                        data = listOf(
                            MangaDexChapterDto(
                                id = "ch-pt-115.5",
                                attributes = MangaDexChapterAttributesDto(
                                    chapter = "115.5",
                                    title = "Epílogo",
                                    translatedLanguage = "pt-br",
                                    pages = 30
                                )
                            )
                        )
                    )
                } else if (isEn) {
                    // 10 chapters available in EN (Ch 1 through 10)
                    val enList = (1..10).map { num ->
                        MangaDexChapterDto(
                            id = "ch-en-$num",
                            attributes = MangaDexChapterAttributesDto(
                                chapter = num.toString(),
                                title = "Chapter $num",
                                translatedLanguage = "en",
                                pages = 20
                            )
                        )
                    }
                    MangaDexChapterFeedResponse(result = "ok", data = enList)
                } else {
                    MangaDexChapterFeedResponse(result = "ok", data = emptyList())
                }
            }

            override suspend fun getAtHomeServer(
                chapterId: String, userAgent: String
            ): MangaDexAtHomeResponse = MangaDexAtHomeResponse("ok", "", MangaDexAtHomeChapterDto("", emptyList()))
        }

        val source = MangaDexSource(apiService = fakeApiService)

        // Requesting chapters for pt-br
        val chapters = source.getChapters("https://api.mangadex.org/manga/test-bisque-doll", "pt-br")

        // Should return 11 total chapters: 1 PT-BR (115.5) + 10 EN (1..10)
        assertEquals(11, chapters.size)

        // Check PT-BR chapter is present
        val ptChapter = chapters.find { it.id == "ch-pt-115.5" }
        assertNotNull(ptChapter)
        assertEquals("Cap. 115.5 - Epílogo", ptChapter?.title)

        // Check English fallback chapters (Ch 1..10) are filled in
        val enChapter1 = chapters.find { it.id == "ch-en-1" }
        assertNotNull(enChapter1)
        assertEquals("Cap. 1 - Chapter 1 🇺🇸", enChapter1?.title)
    }

    @Test
    fun `test getAggregatedChapters fills missing chapters from secondary source`() = runBlocking {
        val primarySource = object : MangaSource {
            override val id: String = "mangadex"
            override val name: String = "MangaDex"
            override val baseUrl: String = "https://mangadex.org"
            override val isNsfw: Boolean = false
            override val lang: String = "pt-br"

            override suspend fun getPopularManga(): List<MangaModel> = emptyList()
            override suspend fun searchManga(query: String): List<MangaModel> = emptyList()
            override suspend fun getMangaDetails(mangaUrl: String): MangaModel = throw NotImplementedError()
            override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, "pt-br")
            override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> {
                return listOf(
                    ChapterModel("ch-115.5", "Cap. 115.5 - Epílogo", "https://mangadex.org/ch-115.5", 115.5f)
                )
            }
            override suspend fun getPages(chapterUrl: String): List<String> = emptyList()
        }

        val secondarySource = object : MangaSource {
            override val id: String = "secondary"
            override val name: String = "Secondary"
            override val baseUrl: String = "https://secondary.com"
            override val isNsfw: Boolean = false
            override val lang: String = "en"

            override suspend fun getPopularManga(): List<MangaModel> = emptyList()
            override suspend fun searchManga(query: String): List<MangaModel> = listOf(
                MangaModel("https://secondary.com/manga/test", "test", "Sono Bisque Doll", "", "", listOf("Romance"), false, id)
            )
            override suspend fun getMangaDetails(mangaUrl: String): MangaModel = throw NotImplementedError()
            override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, "en")
            override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> {
                return (1..5).map { num ->
                    ChapterModel("sec-ch-$num", "Cap. $num", "https://secondary.com/ch-$num", num.toFloat())
                }
            }
            override suspend fun getPages(chapterUrl: String): List<String> = emptyList()
        }

        val fakeMangaDao = object : com.example.data.local.MangaDao {
            override fun getFavoriteMangas(): kotlinx.coroutines.flow.Flow<List<MangaEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun getAllFavoritesSync(): List<MangaEntity> = emptyList()
            override fun getMangaByUrl(url: String): kotlinx.coroutines.flow.Flow<MangaEntity?> = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun getMangaByUrlSync(url: String): MangaEntity? = null
            override suspend fun getAllCachedMangasSync(): List<MangaEntity> = emptyList()
            override suspend fun getCachedMangasBySourceSync(sourceId: String): List<MangaEntity> = emptyList()
            override suspend fun insertManga(manga: MangaEntity) {}
            override suspend fun updateFavoriteStatus(url: String, isFavorite: Boolean) {}
            override suspend fun deleteFavoriteByUrl(url: String) {}
        }

        val fakeProgressDao = object : com.example.data.local.ProgressDao {
            override fun getAllProgress(): kotlinx.coroutines.flow.Flow<List<com.example.data.local.ProgressEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun getAllProgressSync(): List<com.example.data.local.ProgressEntity> = emptyList()
            override fun getProgressForManga(mangaUrl: String): kotlinx.coroutines.flow.Flow<com.example.data.local.ProgressEntity?> = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun saveProgress(progress: com.example.data.local.ProgressEntity) {}
            override suspend fun deleteProgress(mangaUrl: String) {}
        }

        val fakeDownloadDao = object : com.example.data.local.DownloadDao {
            override fun getAllDownloads(): kotlinx.coroutines.flow.Flow<List<com.example.data.local.DownloadEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override fun getCompletedDownloads(): kotlinx.coroutines.flow.Flow<List<com.example.data.local.DownloadEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override fun getDownload(chapterId: String): kotlinx.coroutines.flow.Flow<com.example.data.local.DownloadEntity?> = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun getDownloadSync(chapterId: String): com.example.data.local.DownloadEntity? = null
            override suspend fun insertDownload(download: com.example.data.local.DownloadEntity) {}
            override suspend fun updateDownloadStatus(chapterId: String, downloadedPagesCount: Int, progress: Float, state: String, totalPages: Int, localPath: String) {}
            override suspend fun deleteDownload(chapterId: String) {}
        }

        val fakeReadingSessionDao = object : com.example.data.local.ReadingSessionDao {
            override fun getAllSessions(): kotlinx.coroutines.flow.Flow<List<com.example.data.local.ReadingSessionEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun insertSession(session: com.example.data.local.ReadingSessionEntity) {}
            override fun getTotalChaptersCount(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(0)
            override fun getTotalPagesRead(): kotlinx.coroutines.flow.Flow<Int?> = kotlinx.coroutines.flow.flowOf(0)
            override fun getTotalDurationSeconds(): kotlinx.coroutines.flow.Flow<Long?> = kotlinx.coroutines.flow.flowOf(0)
            override suspend fun clearAllStats() {}
        }

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        val repository = com.example.data.repository.MangaRepository(
            mangaDao = fakeMangaDao,
            progressDao = fakeProgressDao,
            downloadDao = fakeDownloadDao,
            readingSessionDao = fakeReadingSessionDao,
            downloadManager = com.example.data.download.ChapterDownloadManager(
                context = context,
                downloadDao = fakeDownloadDao
            ),
            preferencesRepository = com.example.data.preferences.UserPreferencesRepository(context)
        )

        repository.registerSource(primarySource)
        repository.registerSource(secondarySource)

        val aggregated = repository.getAggregatedChapters("Sono Bisque Doll", "https://mangadex.org/manga/test", "mangadex", "pt-br")

        // Should return 6 total chapters: 115.5 + chapters 1..5 from secondary source
        assertEquals(6, aggregated.size)
        assertNotNull(aggregated.find { it.chapterNumber == 115.5f })
        assertNotNull(aggregated.find { it.chapterNumber == 1.0f })
    }

    @Test
    fun `test Moshi parses real sample_mangadex json`() {
        val file = java.io.File("/tmp/sample_mangadex.json")
        if (file.exists()) {
            val json = file.readText()
            val adapter = com.example.data.network.RetrofitClient.moshi.adapter(com.example.data.network.model.MangaDexMangaListResponse::class.java)
            val response = adapter.fromJson(json)
            assertNotNull(response)
            assertNotNull(response?.data)
            println("PARSED DATA SIZE: ${response?.data?.size}")
            assertTrue((response?.data?.size ?: 0) > 0)
        }
    }
}
