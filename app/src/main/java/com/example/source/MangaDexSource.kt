package com.example.source

import com.example.data.local.Chapter
import com.example.data.local.ChapterEntity
import com.example.data.local.Manga
import com.example.data.local.MangaEntity
import com.example.data.mapping.MangaDexMapper
import com.example.data.mapping.toChapter
import com.example.data.mapping.toChapterModel
import com.example.data.mapping.toManga
import com.example.data.mapping.toMangaModel
import com.example.data.network.MangaDexApiService
import com.example.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [MangaSource] communicating with the MangaDex REST API v5
 * using Retrofit and converting responses via [MangaDexMapper] into standard Room database
 * entities ([MangaEntity], [ChapterEntity]) and domain models ([MangaModel], [ChapterModel]).
 */
class MangaDexSource(
    private val apiService: MangaDexApiService = RetrofitClient.mangaDexApiService,
    private val mapper: MangaDexMapper = MangaDexMapper
) : MangaSource {

    override val id: String = "mangadex"
    override val name: String = "MangaDex (API Oficial)"
    override val baseUrl: String = "https://api.mangadex.org"
    override val isNsfw: Boolean = false
    override val lang: String = "pt-BR"

    // =========================================================================
    // MangaSource Contract Implementations
    // =========================================================================

    override suspend fun getPopularManga(): List<MangaModel> = withContext(Dispatchers.IO) {
        // Tier 1: Try fetching manga with translated chapters available in pt-br
        var dtos: List<com.example.data.network.model.MangaDexMangaDto> = try {
            val responsePt = apiService.getMangaList(
                limit = 25,
                translatedLanguages = listOf("pt-br"),
                orderFollowedCount = "desc",
                includes = listOf("cover_art", "author")
            )
            responsePt.data.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }

        // Tier 2: If pt-br filter returned empty or failed, fetch globally top-followed manga
        if (dtos.isEmpty()) {
            dtos = try {
                val responseGlobal = apiService.getMangaList(
                    limit = 25,
                    orderFollowedCount = "desc",
                    includes = listOf("cover_art", "author")
                )
                responseGlobal.data.orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
        }

        // Tier 3: If still empty, fetch latest updated manga
        if (dtos.isEmpty()) {
            dtos = try {
                val responseFallback = apiService.getMangaList(
                    limit = 25,
                    includes = listOf("cover_art", "author")
                )
                responseFallback.data.orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
        }

        if (dtos.isNotEmpty()) {
            return@withContext dtos.map { it.toMangaModel(baseUrl, id) }
        }

        // Tier 4: Fallback to curated starter offline catalog if network is down or blocked
        STARTER_FALLBACK_MANGAS
    }

    override suspend fun searchManga(query: String): List<MangaModel> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 1. Try search with Portuguese translation preference
        var dtos: List<com.example.data.network.model.MangaDexMangaDto> = try {
            val responsePt = apiService.getMangaList(
                title = trimmed,
                limit = 25,
                translatedLanguages = listOf("pt-br"),
                includes = listOf("cover_art", "author")
            )
            responsePt.data.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }

        // 2. If empty, fall back to global title search (allowing English, Japanese, and Romanized titles)
        if (dtos.isEmpty()) {
            dtos = try {
                val responseGlobal = apiService.getMangaList(
                    title = trimmed,
                    limit = 25,
                    includes = listOf("cover_art", "author")
                )
                responseGlobal.data.orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
        }

        if (dtos.isNotEmpty()) {
            return@withContext dtos.map { it.toMangaModel(baseUrl, id) }
        }

        // 3. Fallback search on offline curated catalog
        STARTER_FALLBACK_MANGAS.filter { m ->
            m.title.contains(trimmed, ignoreCase = true) ||
            m.author.contains(trimmed, ignoreCase = true) ||
            m.genres.any { it.contains(trimmed, ignoreCase = true) } ||
            m.description.contains(trimmed, ignoreCase = true)
        }
    }

    override suspend fun getMangaDetails(mangaUrl: String): MangaModel = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()
        try {
            val response = apiService.getMangaDetails(mangaId, includes = listOf("cover_art", "author"))
            val dto = response.data ?: return@withContext createFallbackDetails(mangaId)
            dto.toMangaModel(baseUrl, id)
        } catch (e: Exception) {
            e.printStackTrace()
            createFallbackDetails(mangaId)
        }
    }

    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()

        try {
            // 1. Try Portuguese translated chapters first
            var chapters = fetchAndFilterChapters(mangaId, listOf("pt-br"))

            // 2. If no Portuguese chapters, try English translations
            if (chapters.isEmpty()) {
                chapters = fetchAndFilterChapters(mangaId, listOf("en"))
            }

            // 3. If still empty, fetch latest chapters in any language
            if (chapters.isEmpty()) {
                chapters = fetchAndFilterChapters(mangaId, null)
            }

            chapters.mapIndexed { index, dto ->
                dto.toChapterModel(baseUrl, (chapters.size - index).toFloat())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getPages(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
        val chapterId = chapterUrl.substringAfterLast("/").trim()
        val pages = mutableListOf<String>()

        try {
            val response = apiService.getAtHomeServer(chapterId)
            val host = response.baseUrl.orEmpty()
            val chapterObj = response.chapter
            val hash = chapterObj?.hash.orEmpty()
            val dataArray = chapterObj?.data.orEmpty()

            if (host.isNotEmpty() && hash.isNotEmpty() && dataArray.isNotEmpty()) {
                for (fileName in dataArray) {
                    pages.add("$host/data/$hash/$fileName")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        pages
    }

    // =========================================================================
    // Direct Room Database Models Mapping API (Manga & Chapter Entities)
    // =========================================================================

    /**
     * Fetches popular mangas mapped directly into the Room database [Manga] ([MangaEntity]) model.
     */
    suspend fun getPopularMangaAsEntities(): List<Manga> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMangaList(
                limit = 25,
                translatedLanguages = listOf("pt-br"),
                orderFollowedCount = "desc",
                includes = listOf("cover_art", "author")
            )
            val dtos = response.data.orEmpty()
            val targetDtos = if (dtos.isNotEmpty()) {
                dtos
            } else {
                apiService.getMangaList(
                    limit = 25,
                    orderFollowedCount = "desc",
                    includes = listOf("cover_art", "author")
                ).data.orEmpty()
            }
            targetDtos.map { it.toManga(baseUrl, id) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Searches mangas mapped directly into the Room database [Manga] ([MangaEntity]) model.
     */
    suspend fun searchMangaAsEntities(query: String): List<Manga> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            val responsePt = apiService.getMangaList(
                title = trimmed,
                limit = 25,
                translatedLanguages = listOf("pt-br"),
                includes = listOf("cover_art", "author")
            )
            val ptResults = responsePt.data.orEmpty()
            val targetDtos = if (ptResults.isNotEmpty()) {
                ptResults
            } else {
                apiService.getMangaList(
                    title = trimmed,
                    limit = 25,
                    includes = listOf("cover_art", "author")
                ).data.orEmpty()
            }
            targetDtos.map { it.toManga(baseUrl, id) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetches details mapped directly into the Room database [Manga] ([MangaEntity]) model.
     */
    suspend fun getMangaDetailsAsEntity(mangaUrl: String): Manga = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()
        try {
            val response = apiService.getMangaDetails(mangaId, includes = listOf("cover_art", "author"))
            val dto = response.data
            dto?.toManga(baseUrl, id) ?: mapper.toMangaEntity(
                dto = com.example.data.network.model.MangaDexMangaDto(id = mangaId),
                baseUrl = baseUrl,
                sourceId = id
            )
        } catch (e: Exception) {
            mapper.toMangaEntity(
                dto = com.example.data.network.model.MangaDexMangaDto(id = mangaId),
                baseUrl = baseUrl,
                sourceId = id
            )
        }
    }

    /**
     * Fetches chapters mapped directly into the Room database [Chapter] ([ChapterEntity]) model.
     */
    suspend fun getChaptersAsEntities(mangaUrl: String): List<Chapter> = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()
        try {
            var rawChapters = fetchAndFilterChapters(mangaId, listOf("pt-br"))
            if (rawChapters.isEmpty()) {
                rawChapters = fetchAndFilterChapters(mangaId, listOf("en"))
            }
            if (rawChapters.isEmpty()) {
                rawChapters = fetchAndFilterChapters(mangaId, null)
            }

            rawChapters.mapIndexed { index, dto ->
                dto.toChapter(
                    mangaUrl = mangaUrl,
                    baseUrl = baseUrl,
                    fallbackNumber = (rawChapters.size - index).toFloat()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private suspend fun fetchAndFilterChapters(
        mangaId: String,
        languages: List<String>?
    ): List<com.example.data.network.model.MangaDexChapterDto> {
        val response = apiService.getChapterFeed(
            mangaId = mangaId,
            limit = 100,
            translatedLanguages = languages,
            orderChapter = "desc"
        )

        return response.data.orEmpty().filter { ch ->
            val attr = ch.attributes
            val externalUrl = attr?.externalUrl
            val isExternal = !externalUrl.isNullOrBlank() && externalUrl != "null"
            val pageCount = attr?.pages ?: 0
            !isExternal && pageCount > 0
        }
    }

    private fun createFallbackDetails(mangaId: String): MangaModel {
        return MangaModel(
            url = "$baseUrl/manga/$mangaId",
            id = mangaId,
            title = "Manga #$mangaId",
            coverUrl = "https://placehold.co/300x450/1e293b/ffffff.png?text=Manga",
            description = "Detalhes carregados via API MangaDex.",
            sourceId = id
        )
    }

    companion object {
        val STARTER_FALLBACK_MANGAS = listOf(
            MangaModel(
                url = "https://api.mangadex.org/manga/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0",
                id = "32d76d19-8a05-4db0-9fc2-e0b0648fe9d0",
                title = "Solo Leveling",
                coverUrl = "https://uploads.mangadex.org/covers/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0/4a86b1c4-118a-4467-bc18-289569faeb9d.jpg.256.jpg",
                description = "Em um mundo onde caçadores despertam com poderes mágicos para enfrentar monstros, Sung Jin-Woo é conhecido como o mais fraco de toda a humanidade. Porém, após uma missão quase mortal, ele recebe uma oportunidade única.",
                genres = listOf("Shounen", "Ação", "Fantasia", "Sobrenatural"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Chugong"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/a1c7c817-4e59-43b7-9365-09675a149a6f",
                id = "a1c7c817-4e59-43b7-9365-09675a149a6f",
                title = "One Piece",
                coverUrl = "https://uploads.mangadex.org/covers/a1c7c817-4e59-43b7-9365-09675a149a6f/a0f121e7-d055-46eb-8e54-e6962351abf1.jpg.256.jpg",
                description = "Monkey D. Luffy se recusa a deixar que qualquer obstáculo fique em seu caminho para se tornar o Rei dos Piratas. Navegando pelas águas perigosas da Grand Line com sua leal tripulação.",
                genres = listOf("Shounen", "Ação", "Aventura", "Comédia", "Fantasia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Eiichiro Oda"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/c52b2ce3-7f95-469c-96b0-479524fb7a1a",
                id = "c52b2ce3-7f95-469c-96b0-479524fb7a1a",
                title = "Jujutsu Kaisen",
                coverUrl = "https://uploads.mangadex.org/covers/c52b2ce3-7f95-469c-96b0-479524fb7a1a/c2bc04fb-16d2-4e4b-9759-7b3d1b6df085.jpg.256.jpg",
                description = "Yuji Itadori é um estudante do ensino médio que passa seus dias visitando seu avô no hospital. No entanto, sua vida pacífica toma um rumo sombrio quando ele engole um dedo amaldiçoado.",
                genres = listOf("Shounen", "Ação", "Sobrenatural", "Escolar"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Gege Akutami"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/a77742c1-fda6-40e3-9826-bc830577384e",
                id = "a77742c1-fda6-40e3-9826-bc830577384e",
                title = "Chainsaw Man",
                coverUrl = "https://uploads.mangadex.org/covers/a77742c1-fda6-40e3-9826-bc830577384e/58c2b7ae-2a07-4e94-8149-a1b7e28b2db6.jpg.256.jpg",
                description = "Denji tem um sonho simples: viver uma vida feliz e pacífica com sua cadela-demônio, Pochita. Mas a dura realidade das dívidas da máfia muda seu destino.",
                genres = listOf("Shounen", "Ação", "Sobrenatural", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Tatsuki Fujimoto"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/6b1eb93e-473a-4ab3-999f-7a2569e2ae24",
                id = "6b1eb93e-473a-4ab3-999f-7a2569e2ae24",
                title = "Naruto",
                coverUrl = "https://uploads.mangadex.org/covers/6b1eb93e-473a-4ab3-999f-7a2569e2ae24/1dc553cb-7ffc-469b-8e10-cf9183c5a611.jpg.256.jpg",
                description = "A história épica do jovem ninja Naruto Uzumaki que busca o reconhecimento de sua aldeia e sonha em se tornar o Hokage, o líder dos ninjas.",
                genres = listOf("Shounen", "Ninja", "Ação", "Aventura"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Masashi Kishimoto"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/0c634c26-5b96-417c-a496-e3c3b0eb6884",
                id = "0c634c26-5b96-417c-a496-e3c3b0eb6884",
                title = "Demon Slayer: Kimetsu no Yaiba",
                coverUrl = "https://uploads.mangadex.org/covers/0c634c26-5b96-417c-a496-e3c3b0eb6884/2cfc80cb-f4f7-41a4-946d-c50d4eb952fe.jpg.256.jpg",
                description = "Tanjiro Kamado é um garoto de bom coração que vende carvão para sustentar sua família. Sua vida muda para sempre quando sua família é massacrada por um demônio.",
                genres = listOf("Shounen", "Ação", "Sobrenatural", "Fantasia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Koyoharu Gotouge"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/6e3553b9-ddb5-4d37-b7a2-799f236057ec",
                id = "6e3553b9-ddb5-4d37-b7a2-799f236057ec",
                title = "Spy x Family",
                coverUrl = "https://uploads.mangadex.org/covers/6e3553b9-ddb5-4d37-b7a2-799f236057ec/8a25c3f6-f641-4770-9856-42d45a27863e.jpg.256.jpg",
                description = "Um espião mestre disfarçado cria uma família falsa para cumprir sua missão secreta, sem saber que sua esposa é uma assassina e sua filha adotiva tem poderes telepáticos.",
                genres = listOf("Shounen", "Comédia", "Ação", "Escolar"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Tatsuya Endo"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/0a0f8b34-8c88-4444-a621-e7cf77ec8a6c",
                id = "0a0f8b34-8c88-4444-a621-e7cf77ec8a6c",
                title = "Bleach",
                coverUrl = "https://uploads.mangadex.org/covers/0a0f8b34-8c88-4444-a621-e7cf77ec8a6c/323fa34b-85e7-4bb5-8db6-e5758a032890.jpg.256.jpg",
                description = "Ichigo Kurosaki é um estudante com a habilidade de ver fantasmas. Sua vida muda completamente quando conhece Rukia Kuchiki, uma Ceifeira de Almas.",
                genres = listOf("Shounen", "Ação", "Sobrenatural"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Tite Kubo"
            )
        )
    }
}
