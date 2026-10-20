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
                limit = 50,
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
                    limit = 50,
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
                    limit = 50,
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
                description = "Em um mundo onde caçadores despertam com poderes mágicos para enfrentar monstros, Sung Jin-Woo é conhecido como o mais fraco de toda a humanidade.",
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
                description = "Monkey D. Luffy se recusa a deixar que qualquer obstáculo fique em seu caminho para se tornar o Rei dos Piratas.",
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
                description = "Yuji Itadori é um estudante do ensino médio que engole um dedo amaldiçoado e ingressa no Colégio Técnico de Jujutsu.",
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
                description = "Denji tem um sonho simples, mas após ser traído, renasce com o poder de demônio da motosserra.",
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
                description = "A história épica do jovem ninja Naruto Uzumaki que busca o reconhecimento de sua aldeia e sonha em se tornar o Hokage.",
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
                description = "Tanjiro Kamado parte em uma jornada perigosa para curar sua irmã Nezuko e vingar sua família.",
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
                description = "Um espião mestre cria uma família falsa onde a esposa é uma assassina e a filha uma telepata.",
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
                description = "Ichigo Kurosaki ganha poderes de Ceifeiro de Almas e defende o mundo dos Hollows.",
                genres = listOf("Shounen", "Ação", "Sobrenatural", "Fantasia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Tite Kubo"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/3046a5dd-56e1-4ae9-98c9-40749e3fa7cf",
                id = "3046a5dd-56e1-4ae9-98c9-40749e3fa7cf",
                title = "Attack on Titan",
                coverUrl = "https://uploads.mangadex.org/covers/3046a5dd-56e1-4ae9-98c9-40749e3fa7cf/d9016142-f8eb-4b2a-89f5-ff7c4faee469.jpg.256.jpg",
                description = "A humanidade vive protegida por gigantescas muralhas contra Titãs devoradores.",
                genres = listOf("Shounen", "Ação", "Fantasia", "Mistério"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Hajime Isayama"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/d86cf65b-5f6c-437d-a0af-19a31f4f694f",
                id = "d86cf65b-5f6c-437d-a0af-19a31f4f694f",
                title = "My Hero Academia",
                coverUrl = "https://uploads.mangadex.org/covers/d86cf65b-5f6c-437d-a0af-19a31f4f694f/45f94a82-f709-4011-8ecb-91d60721245d.jpg.256.jpg",
                description = "Em um mundo onde quase todos possuem individualidades, Izuku Midoriya nasce sem poderes mas sonha em ser herói.",
                genres = listOf("Shounen", "Ação", "Escolar", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Kohei Horikoshi"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/b0b721ff-c388-4486-aa0f-c8b0e9215226",
                id = "b0b721ff-c388-4486-aa0f-c8b0e9215226",
                title = "Sousou no Frieren",
                coverUrl = "https://uploads.mangadex.org/covers/b0b721ff-c388-4486-aa0f-c8b0e9215226/89498db2-df6e-4f1d-ba72-3532c58e7a03.jpg.256.jpg",
                description = "A elfa Frieren derrota o Rei Demônio junto aos seus companheiros e embarca em uma nova jornada após anos de paz.",
                genres = listOf("Shounen", "Aventura", "Fantasia", "Magia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Kanehito Yamada"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/e78a489b-6632-4d61-b0b6-3e51373b0153",
                id = "e78a489b-6632-4d61-b0b6-3e51373b0153",
                title = "Tensei Shitara Slime Datta Ken",
                coverUrl = "https://uploads.mangadex.org/covers/e78a489b-6632-4d61-b0b6-3e51373b0153/f286e7a2-f674-4b47-b895-1cbbe19f3f4c.jpg.256.jpg",
                description = "Um homem comum de 37 anos é esfaqueado e reencarna em outro mundo como um lodo (slime) com habilidades predadoras.",
                genres = listOf("Shounen", "Isekai", "Fantasia", "Ação", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Fuse"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/360e3029-2ced-4623-b678-0cf241bfb48a",
                id = "360e3029-2ced-4623-b678-0cf241bfb48a",
                title = "Mushoku Tensei: Jobless Reincarnation",
                coverUrl = "https://uploads.mangadex.org/covers/360e3029-2ced-4623-b678-0cf241bfb48a/429cfbe3-3be4-4ec5-b6d8-21dbece4ef4f.jpg.256.jpg",
                description = "Um desempregado de 34 anos é atropelado e renasce em um mundo mágico determinado a viver sem arrependimentos.",
                genres = listOf("Isekai", "Fantasia", "Aventura", "Romance"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Rifujin na Magonote"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/37f5c308-7f95-460b-8d7d-5a822fb183b5",
                id = "37f5c308-7f95-460b-8d7d-5a822fb183b5",
                title = "Kaguya-sama: Love Is War",
                coverUrl = "https://uploads.mangadex.org/covers/37f5c308-7f95-460b-8d7d-5a822fb183b5/9ba691d1-67ca-413c-8a14-8785ebf91754.jpg.256.jpg",
                description = "Dois prodígios apaixonados tentam a todo custo forçar o outro a se confessar primeiro em uma guerra mental cômica.",
                genres = listOf("Romance", "Comédia", "Escolar", "Seinen"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Aka Akasaka"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/a25e46ec-3092-4d32-98f4-022137021319",
                id = "a25e46ec-3092-4d32-98f4-022137021319",
                title = "Horimiya",
                coverUrl = "https://uploads.mangadex.org/covers/a25e46ec-3092-4d32-98f4-022137021319/5e18ef9b-cbb7-4a0b-bc11-a548e64c393d.jpg.256.jpg",
                description = "A garota popular e o garoto quieto compartilham segredos sobre suas verdadeiras personalidades fora da escola.",
                genres = listOf("Romance", "Comédia", "Escolar", "Shounen"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "HERO"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/a9667631-b79e-4733-af39-e489562725fa",
                id = "a9667631-b79e-4733-af39-e489562725fa",
                title = "Komi Can't Communicate",
                coverUrl = "https://uploads.mangadex.org/covers/a9667631-b79e-4733-af39-e489562725fa/c8e54e4c-1d70-4f59-9943-41bbd9319ee8.jpg.256.jpg",
                description = "Shouko Komi tem extrema ansiedade social e dificuldade em falar, mas com a ajuda de Tadano busca fazer 100 amigos.",
                genres = listOf("Comédia", "Escolar", "Shounen", "Romance"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Tomohito Oda"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/d8a959fb-e100-4391-a067-c2901c0b396e",
                id = "d8a959fb-e100-4391-a067-c2901c0b396e",
                title = "One-Punch Man",
                coverUrl = "https://uploads.mangadex.org/covers/d8a959fb-e100-4391-a067-c2901c0b396e/679db172-8877-4404-b9ae-05f32ebda7d0.jpg.256.jpg",
                description = "Saitama treinou tanto que ficou calvo e agora derrota qualquer oponente com um único soco, entediado pela falta de desafios.",
                genres = listOf("Shounen", "Ação", "Comédia", "Sci-Fi"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "ONE"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/59b66236-c4f4-4364-8898-33306db7c63c",
                id = "59b66236-c4f4-4364-8898-33306db7c63c",
                title = "Mob Psycho 100",
                coverUrl = "https://uploads.mangadex.org/covers/59b66236-c4f4-4364-8898-33306db7c63c/4ae0a459-ec58-403d-b452-f4728aa0757f.jpg.256.jpg",
                description = "Shigeo Kageyama (Mob) tenta levar uma vida normal apesar de seus poderes psíquicos avassaladores.",
                genres = listOf("Shounen", "Ação", "Comédia", "Sobrenatural", "Escolar"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "ONE"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/6b194d36-8c4d-44a6-8e50-f8d227b61358",
                id = "6b194d36-8c4d-44a6-8e50-f8d227b61358",
                title = "Dr. STONE",
                coverUrl = "https://uploads.mangadex.org/covers/6b194d36-8c4d-44a6-8e50-f8d227b61358/761cb789-d4ee-48c2-bb58-6a568c81881e.jpg.256.jpg",
                description = "Após a humanidade virar pedra por milênios, o gênio Senku desperta decidido a reconstruir a civilização com a ciência.",
                genres = listOf("Sci-Fi", "Aventura", "Shounen", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Riichiro Inagaki"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/801513ba-a712-4985-8c30-cbb87b04bc55",
                id = "801513ba-a712-4985-8c30-cbb87b04bc55",
                title = "Berserk",
                coverUrl = "https://uploads.mangadex.org/covers/801513ba-a712-4985-8c30-cbb87b04bc55/267d3056-bb6b-4e08-ba90-50b73c4f74d0.jpg.256.jpg",
                description = "Guts, o Espadachim Negro, busca vingança contra o homem que traiu sua lealdade em um mundo medieval sombrio.",
                genres = listOf("Seinen", "Ação", "Aventura", "Fantasia", "Sobrenatural"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Kentaro Miura"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/77bee52c-d2d6-44ad-a33a-1734c1fe696a",
                id = "77bee52c-d2d6-44ad-a33a-1734c1fe696a",
                title = "The Eminence in Shadow",
                coverUrl = "https://uploads.mangadex.org/covers/77bee52c-d2d6-44ad-a33a-1734c1fe696a/6079dd31-838b-4d61-87c4-121f3ad19158.jpg.256.jpg",
                description = "Cid renasce e cria uma organização secreta imaginária que coincidentemente existe na realidade.",
                genres = listOf("Isekai", "Ação", "Fantasia", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Daisuke Aizawa"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/276ae9d8-9df8-43d9-9523-fc317e0b5711",
                id = "276ae9d8-9df8-43d9-9523-fc317e0b5711",
                title = "Fullmetal Alchemist",
                coverUrl = "https://uploads.mangadex.org/covers/276ae9d8-9df8-43d9-9523-fc317e0b5711/c9a416a2-632d-4560-a292-6f2945d70659.jpg.256.jpg",
                description = "Dois irmãos alquimistas buscam a Pedra Filosofal para restaurar seus corpos após um ritual proibido.",
                genres = listOf("Shounen", "Ação", "Aventura", "Fantasia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Hiromu Arakawa"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/e0da6f93-0e86-45ef-893c-cf57cb0f199d",
                id = "e0da6f93-0e86-45ef-893c-cf57cb0f199d",
                title = "Steins;Gate",
                coverUrl = "https://uploads.mangadex.org/covers/e0da6f93-0e86-45ef-893c-cf57cb0f199d/c9f7a7b4-180a-48d8-90ce-e5550a26d701.jpg.256.jpg",
                description = "Um cientista excêntrico descobre acidentalmente como enviar mensagens ao passado através de um micro-ondas.",
                genres = listOf("Sci-Fi", "Sobrenatural", "Mistério"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "5pb. / Nitroplus"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/4f3bcae4-2d96-41f9-ae20-6101d29d48b8",
                id = "4f3bcae4-2d96-41f9-ae20-6101d29d48b8",
                title = "Hunter x Hunter",
                coverUrl = "https://uploads.mangadex.org/covers/4f3bcae4-2d96-41f9-ae20-6101d29d48b8/11956557-0870-4bc3-95ca-ba2358c2cb57.jpg.256.jpg",
                description = "Gon Freecss quer se tornar um Hunter de elite para encontrar seu pai que o deixou quando era pequeno.",
                genres = listOf("Shounen", "Ação", "Aventura", "Fantasia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Yoshihiro Togashi"
            )
        )
    }
}
