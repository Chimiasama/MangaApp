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

    override suspend fun getPopularManga(): List<MangaModel> = getPopularManga("pt-br")

    override suspend fun getPopularManga(language: String): List<MangaModel> = withContext(Dispatchers.IO) {
        val targetLanguages = when (language) {
            "pt-br" -> listOf("pt-br", "pt")
            "en" -> listOf("en")
            "es" -> listOf("es-la", "es")
            else -> null
        }

        // Tier 1: Try fetching manga with chapters available in selected language
        var dtos: List<com.example.data.network.model.MangaDexMangaDto> = try {
            val responseLang = apiService.getMangaList(
                limit = 50,
                translatedLanguages = targetLanguages,
                orderFollowedCount = "desc",
                includes = listOf("cover_art", "author")
            )
            responseLang.data.orEmpty()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            emptyList()
        }

        // Tier 2: If language filter returned empty or failed, fetch globally top-followed manga
        if (dtos.isEmpty() && targetLanguages != null) {
            dtos = try {
                val responseGlobal = apiService.getMangaList(
                    limit = 50,
                    orderFollowedCount = "desc",
                    includes = listOf("cover_art", "author")
                )
                responseGlobal.data.orEmpty()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
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

    override suspend fun searchManga(query: String): List<MangaModel> = searchManga(query, "pt-br")

    override suspend fun searchManga(query: String, language: String): List<MangaModel> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val targetLanguages = when (language) {
            "pt-br" -> listOf("pt-br", "pt")
            "en" -> listOf("en")
            "es" -> listOf("es-la", "es")
            else -> null
        }

        var lastFailure: Exception? = null

        // 1. Try search with selected language preference
        var dtos: List<com.example.data.network.model.MangaDexMangaDto> = try {
            val responseLang = apiService.getMangaList(
                title = trimmed,
                limit = 25,
                translatedLanguages = targetLanguages,
                includes = listOf("cover_art", "author")
            )
            lastFailure = null
            responseLang.data.orEmpty()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            lastFailure = e
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
                lastFailure = null
                responseGlobal.data.orEmpty()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                lastFailure = e
                emptyList()
            }
        }

        if (dtos.isNotEmpty()) {
            return@withContext dtos.map { it.toMangaModel(baseUrl, id) }
        }

        // 3. Fallback search on offline curated catalog
        val offlineMatches = STARTER_FALLBACK_MANGAS.filter { m ->
            m.title.contains(trimmed, ignoreCase = true) ||
            m.author.contains(trimmed, ignoreCase = true) ||
            m.genres.any { it.contains(trimmed, ignoreCase = true) } ||
            m.description.contains(trimmed, ignoreCase = true)
        }
        if (offlineMatches.isNotEmpty()) return@withContext offlineMatches
        lastFailure?.let { failure ->
            throw java.io.IOException("Não foi possível consultar a MangaDex. Verifique sua conexão e tente novamente.", failure)
        }
        emptyList()
    }

    private var categoryTags: List<com.example.data.network.model.MangaDexTagDto>? = null

    override suspend fun searchManga(query: String, language: String, category: String): List<MangaModel> =
        searchManga(query, language, category, null)

    override suspend fun searchManga(query: String, language: String, category: String, period: PublicationPeriod?): List<MangaModel> {
        if (MangaCategories.isAll(category) && period == null) return if (query.isBlank()) getPopularManga(language) else searchManga(query, language)
        val key = MangaCategories.key(category)
        val demographic = when (key) {
            "Shounen" -> "shounen"; "Shoujo" -> "shoujo"; "Seinen" -> "seinen"; "Josei" -> "josei"
            else -> null
        }
        val tags = if (demographic != null || MangaCategories.isAll(category)) null else {
            val available = categoryTags ?: apiService.getMangaTags().data.also { categoryTags = it }
            val tag = available.firstOrNull { tag ->
                tag.attributes?.name?.values.orEmpty().any { MangaCategories.key(it) == key }
            }?.id ?: return emptyList()
            listOf(tag)
        }
        val languages = when (language) {
            "pt-br" -> listOf("pt-br", "pt"); "en" -> listOf("en"); "es" -> listOf("es", "es-la"); else -> null
        }
        suspend fun request(year: Int? = null, offset: Int = 0, oldestFirst: Boolean = false) = apiService.getFilteredMangaList(
            title = query.trim().takeIf { it.isNotEmpty() }, demographics = demographic?.let(::listOf),
            includedTags = tags, translatedLanguages = languages, year = year, offset = offset,
            order = if (oldestFirst) null else "desc", orderYear = if (oldestFirst) "asc" else null)
        val dtos = mutableListOf<com.example.data.network.model.MangaDexMangaDto>()
        when {
            period == null -> dtos += request().data.orEmpty()
            period.through - period.from <= 15 -> {
                // The official API accepts an exact year, not a range. Keep every other filter.
                for (year in period.from..period.through) {
                    if (year != period.from) kotlinx.coroutines.delay(300)
                    dtos += request(year = year).data.orEmpty()
                }
            }
            else -> {
                // Early historical catalogs are small: scan by publication year, not popularity.
                var offset = 0
                while (offset < 1000) {
                    val response = request(offset = offset, oldestFirst = true)
                    val batch = response.data.orEmpty()
                    dtos += batch
                    if (batch.isEmpty() || batch.any { (it.attributes?.year ?: Int.MIN_VALUE) > period.through } ||
                        batch.size < 100 || offset + batch.size >= (response.total ?: Int.MAX_VALUE)) break
                    offset += batch.size
                    kotlinx.coroutines.delay(300)
                }
            }
        }
        return dtos.distinctBy { it.id }.map { it.toMangaModel(baseUrl, id) }
            .filter { MangaCategories.matches(it.genres, category) && (period == null || period.matches(it.publicationYear)) }
    }

    override suspend fun getMangaDetails(mangaUrl: String): MangaModel = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()
        val response = apiService.getMangaDetails(mangaId, includes = listOf("cover_art", "author"))
        val dto = response.data ?: throw java.io.IOException("MangaDex não entregou os detalhes desta obra.")
        dto.toMangaModel(baseUrl, id)
    }

    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, "pt-br")

    override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").trim()
        fetchChaptersForLanguage(mangaId, language).mapIndexed { index, dto ->
            dto.toChapterModel(baseUrl, (index + 1).toFloat())
        }
    }

    override suspend fun getPages(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
        val chapterId = chapterUrl.substringAfterLast("/").trim()
        val response = apiService.getAtHomeServer(chapterId)
        val host = response.baseUrl.orEmpty()
        val hash = response.chapter?.hash.orEmpty()
        val files = response.chapter?.data.orEmpty()
        if (host.isBlank() || hash.isBlank() || files.isEmpty()) throw ChapterAccessException(
            "MangaDex não entregou imagens deste capítulo. O registro pode não ter páginas ou pode ter sido removido. Não há confirmação de DRM.")
        files.map { "$host/data/$hash/$it" }
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
            val rawChapters = fetchMergedChapters(mangaId)
            rawChapters.mapIndexed { index, dto ->
                val fallbackNum = (index + 1).toFloat()
                dto.toChapter(
                    mangaUrl = mangaUrl,
                    baseUrl = baseUrl,
                    fallbackNumber = fallbackNum
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

    private suspend fun fetchAllChapters(
        mangaId: String,
        languages: List<String>?
    ): List<com.example.data.network.model.MangaDexChapterDto> {
        val allChapters = mutableListOf<com.example.data.network.model.MangaDexChapterDto>()
        var offset = 0
        val pageSize = 500
        var total = Int.MAX_VALUE

        // Paginate up to 3000 chapters to cover complete long-running series
        while (offset < total && allChapters.size < 3000) {
            val response = try {
                apiService.getChapterFeed(
                    mangaId = mangaId,
                    limit = pageSize,
                    offset = offset,
                    translatedLanguages = languages,
                    orderChapter = "asc"
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            }

            val data = response.data.orEmpty()
            if (data.isEmpty()) break

            total = response.total ?: data.size
            allChapters.addAll(data)
            offset += data.size
            if (data.size < pageSize) break
        }

        return allChapters
    }

    private suspend fun fetchChaptersForLanguage(
        mangaId: String,
        language: String
    ): List<com.example.data.network.model.MangaDexChapterDto> {
        val targets = ChapterLanguage.requested(language)?.toList()
        return fetchAllChapters(mangaId, targets)
            .filter { ChapterLanguage.matches(it.attributes?.translatedLanguage.orEmpty(), language) }
            .sortedByDescending { if (!it.attributes?.externalUrl?.takeUnless { url -> url == "null" }.isNullOrBlank()) 0 else if ((it.attributes?.pages ?: 1) > 0) 2 else 1 }
            .distinctBy { Triple(it.attributes?.volume, it.attributes?.chapter ?: it.id,
                ChapterLanguage.normalize(it.attributes?.translatedLanguage.orEmpty())) }
            .sortedBy { it.attributes?.chapter?.toFloatOrNull() ?: Float.MAX_VALUE }
    }

    private suspend fun fetchMergedChapters(mangaId: String) = fetchChaptersForLanguage(mangaId, "all")

    companion object {
        val STARTER_FALLBACK_MANGAS = listOf(
            MangaModel(
                url = "https://api.mangadex.org/manga/ade0306c-f4b6-4890-9edb-1ddf04df2039",
                id = "ade0306c-f4b6-4890-9edb-1ddf04df2039",
                title = "Solo Leveling",
                coverUrl = "https://uploads.mangadex.org/covers/ade0306c-f4b6-4890-9edb-1ddf04df2039/fe76445d-387f-4ff6-8340-f06403c20dbe.jpg.256.jpg",
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
                coverUrl = "https://uploads.mangadex.org/covers/a1c7c817-4e59-43b7-9365-09675a149a6f/2f4aca53-64c7-46ac-ae85-3bc9b3169890.png.256.jpg",
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
                coverUrl = "https://uploads.mangadex.org/covers/c52b2ce3-7f95-469c-96b0-479524fb7a1a/6d9134b2-21ea-4d02-ac2b-7c0d1c6a2aaa.jpg.256.jpg",
                description = "Yuji Itadori é um estudante do ensino médio que engole um dedo amaldiçoado e ingressa no Colégio Técnico de Jujutsu.",
                genres = listOf("Shounen", "Ação", "Sobrenatural", "Escolar"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Gege Akutami"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/e896c48c-3150-437d-ba57-d8567eb399ae",
                id = "e896c48c-3150-437d-ba57-d8567eb399ae",
                title = "Chainsaw Man",
                coverUrl = "https://uploads.mangadex.org/covers/e896c48c-3150-437d-ba57-d8567eb399ae/fa06e4e4-ef2a-477b-bfb6-a2a88793620b.jpg.256.jpg",
                description = "Denji tem um sonho simples, mas após ser traído, renasce com o poder de demônio da motosserra.",
                genres = listOf("Shounen", "Ação", "Sobrenatural", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Tatsuki Fujimoto"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/275c3ee8-bdeb-4070-a333-6add23a8415a",
                id = "275c3ee8-bdeb-4070-a333-6add23a8415a",
                title = "Naruto",
                coverUrl = "https://uploads.mangadex.org/covers/275c3ee8-bdeb-4070-a333-6add23a8415a/b95e8e7c-c510-4a52-a759-9fe6d195afe9.jpg.256.jpg",
                description = "A história épica do jovem ninja Naruto Uzumaki que busca o reconhecimento de sua aldeia e sonha em se tornar o Hokage.",
                genres = listOf("Shounen", "Ninja", "Ação", "Aventura"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Masashi Kishimoto"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/789642f8-ca89-4e4e-8f7b-eee4d17ea08b",
                id = "789642f8-ca89-4e4e-8f7b-eee4d17ea08b",
                title = "Demon Slayer: Kimetsu no Yaiba",
                coverUrl = "https://uploads.mangadex.org/covers/789642f8-ca89-4e4e-8f7b-eee4d17ea08b/60530e72-f76f-45d5-b6f9-f95e05058fc3.png.256.jpg",
                description = "Tanjiro Kamado parte em uma jornada perigosa para curar sua irmã Nezuko e vingar sua família.",
                genres = listOf("Shounen", "Ação", "Sobrenatural", "Fantasia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Koyoharu Gotouge"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/6b958848-c885-4735-9201-12ee77abcb3c",
                id = "6b958848-c885-4735-9201-12ee77abcb3c",
                title = "Spy x Family",
                coverUrl = "https://uploads.mangadex.org/covers/6b958848-c885-4735-9201-12ee77abcb3c/91a35e78-62b2-41fe-9869-ce051f2d1070.jpg.256.jpg",
                description = "Um espião mestre cria uma família falsa onde a esposa é uma assassina e a filha uma telepata.",
                genres = listOf("Shounen", "Comédia", "Ação", "Escolar"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Tatsuya Endo"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/a460ab18-22c1-47eb-a08a-9ee85fe37ec8",
                id = "a460ab18-22c1-47eb-a08a-9ee85fe37ec8",
                title = "Bleach",
                coverUrl = "https://uploads.mangadex.org/covers/a460ab18-22c1-47eb-a08a-9ee85fe37ec8/7c8f9203-2b82-41f2-beb3-e7fb00e151e2.jpg.256.jpg",
                description = "Ichigo Kurosaki ganha poderes de Ceifeiro de Almas e defende o mundo dos Hollows.",
                genres = listOf("Shounen", "Ação", "Sobrenatural", "Fantasia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Tite Kubo"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/529d7d4e-54ad-4370-8d12-163bf437c9d4",
                id = "529d7d4e-54ad-4370-8d12-163bf437c9d4",
                title = "Attack on Titan",
                coverUrl = "https://uploads.mangadex.org/covers/529d7d4e-54ad-4370-8d12-163bf437c9d4/512c61d2-bed8-449c-a7d1-fb03a045f05a.jpg.256.jpg",
                description = "A humanidade vive protegida por gigantescas muralhas contra Titãs devoradores.",
                genres = listOf("Shounen", "Ação", "Fantasia", "Mistério"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Hajime Isayama"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/1a051bb3-094e-4494-aa2e-fdac29b9ab5b",
                id = "1a051bb3-094e-4494-aa2e-fdac29b9ab5b",
                title = "My Hero Academia",
                coverUrl = "https://uploads.mangadex.org/covers/1a051bb3-094e-4494-aa2e-fdac29b9ab5b/a9d17668-36d4-414b-b146-e15dd8cdafb9.jpg.256.jpg",
                description = "Em um mundo onde quase todos possuem individualidades, Izuku Midoriya nasce sem poderes mas sonha em ser herói.",
                genres = listOf("Shounen", "Ação", "Escolar", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Kohei Horikoshi"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/b0b721ff-c388-4486-aa0f-c2b0bb321512",
                id = "b0b721ff-c388-4486-aa0f-c2b0bb321512",
                title = "Sousou no Frieren",
                coverUrl = "https://uploads.mangadex.org/covers/b0b721ff-c388-4486-aa0f-c2b0bb321512/b9a049b0-186b-4714-a83e-bb965458914a.jpg.256.jpg",
                description = "A elfa Frieren derrota o Rei Demônio junto aos seus companheiros e embarca em uma nova jornada após anos de paz.",
                genres = listOf("Shounen", "Aventura", "Fantasia", "Magia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Kanehito Yamada"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/61d81be6-2759-4cc4-9815-7952a3449149",
                id = "61d81be6-2759-4cc4-9815-7952a3449149",
                title = "Tensei Shitara Slime Datta Ken",
                coverUrl = "https://uploads.mangadex.org/covers/61d81be6-2759-4cc4-9815-7952a3449149/1eebb672-3aa3-4f1d-99c6-ba298420bf54.jpg.256.jpg",
                description = "Um homem comum de 37 anos é esfaqueado e reencarna em outro mundo como um lodo (slime) com habilidades predadoras.",
                genres = listOf("Shounen", "Isekai", "Fantasia", "Ação", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Fuse"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/6114f59f-4047-496b-8605-c6264c76d49f",
                id = "6114f59f-4047-496b-8605-c6264c76d49f",
                title = "Mushoku Tensei: Jobless Reincarnation",
                coverUrl = "https://uploads.mangadex.org/covers/6114f59f-4047-496b-8605-c6264c76d49f/f9a50ba5-b50c-42b6-b524-9037199da109.jpg.256.jpg",
                description = "Um desempregado de 34 anos é atropelado e renasce em um mundo mágico determinado a viver sem arrependimentos.",
                genres = listOf("Isekai", "Fantasia", "Aventura", "Romance"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Rifujin na Magonote"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/a84264f6-a979-4573-89b3-09dd5c050c2b",
                id = "a84264f6-a979-4573-89b3-09dd5c050c2b",
                title = "Kaguya-sama: Love Is War",
                coverUrl = "https://uploads.mangadex.org/covers/a84264f6-a979-4573-89b3-09dd5c050c2b/d92bee6c-a04a-404e-afb2-185066508d30.jpg.256.jpg",
                description = "Dois prodígios apaixonados tentam a todo custo forçar o outro a se confessar primeiro em uma guerra mental cômica.",
                genres = listOf("Romance", "Comédia", "Escolar", "Seinen"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Aka Akasaka"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/a25e46ec-30f7-4db6-89df-cacbc1d9a900",
                id = "a25e46ec-30f7-4db6-89df-cacbc1d9a900",
                title = "Horimiya",
                coverUrl = "https://uploads.mangadex.org/covers/a25e46ec-30f7-4db6-89df-cacbc1d9a900/04feed00-d919-4c72-b355-15b37555eb1e.jpg.256.jpg",
                description = "A garota popular e o garoto quieto compartilham segredos sobre suas verdadeiras personalidades fora da escola.",
                genres = listOf("Romance", "Comédia", "Escolar", "Shounen"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "HERO"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/a96676e5-8ae2-425e-b549-7f15dd34a6d8",
                id = "a96676e5-8ae2-425e-b549-7f15dd34a6d8",
                title = "Komi Can't Communicate",
                coverUrl = "https://uploads.mangadex.org/covers/a96676e5-8ae2-425e-b549-7f15dd34a6d8/f8f44329-1dd7-4301-9ec7-a4a76182e8eb.jpg.256.jpg",
                description = "Shouko Komi tem extrema ansiedade social e dificuldade em falar, mas com a ajuda de Tadano busca fazer 100 amigos.",
                genres = listOf("Comédia", "Escolar", "Shounen", "Romance"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Tomohito Oda"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/29c42e49-d6f5-4084-9cec-771f5660c90f",
                id = "29c42e49-d6f5-4084-9cec-771f5660c90f",
                title = "One-Punch Man",
                coverUrl = "https://uploads.mangadex.org/covers/29c42e49-d6f5-4084-9cec-771f5660c90f/e477f308-bd50-4197-96e1-a14ffbc8a563.png.256.jpg",
                description = "Saitama treinou tanto que ficou calvo e agora derrota qualquer oponente com um único soco, entediado pela falta de desafios.",
                genres = listOf("Shounen", "Ação", "Comédia", "Sci-Fi"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "ONE"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/6e2d91b9-81b4-448c-8ace-fb17d4a99d44",
                id = "6e2d91b9-81b4-448c-8ace-fb17d4a99d44",
                title = "Mob Psycho 100",
                coverUrl = "https://uploads.mangadex.org/covers/6e2d91b9-81b4-448c-8ace-fb17d4a99d44/5f1ebcf0-bfb0-4f5c-8a0d-11a93c04f039.png.256.jpg",
                description = "Shigeo Kageyama (Mob) tenta levar uma vida normal apesar de seus poderes psíquicos avassaladores.",
                genres = listOf("Shounen", "Ação", "Comédia", "Sobrenatural", "Escolar"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "ONE"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/869ad120-7341-4ea9-a980-f3545bc0503c",
                id = "869ad120-7341-4ea9-a980-f3545bc0503c",
                title = "Dr. STONE",
                coverUrl = "https://uploads.mangadex.org/covers/869ad120-7341-4ea9-a980-f3545bc0503c/93fe851b-4ec5-41d3-8014-ed860b32eef9.jpg.256.jpg",
                description = "Após a humanidade virar pedra por milênios, o gênio Senku desperta decidido a reconstruir a civilização com a ciência.",
                genres = listOf("Sci-Fi", "Aventura", "Shounen", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Riichiro Inagaki"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/801513ba-a712-498c-8f57-cae55b38cc92",
                id = "801513ba-a712-498c-8f57-cae55b38cc92",
                title = "Berserk",
                coverUrl = "https://uploads.mangadex.org/covers/801513ba-a712-498c-8f57-cae55b38cc92/81e1c82d-6672-400c-8c58-4ff9bfb89031.jpg.256.jpg",
                description = "Guts, o Espadachim Negro, busca vingança contra o homem que traiu sua lealdade em um mundo medieval sombrio.",
                genres = listOf("Seinen", "Ação", "Aventura", "Fantasia", "Sobrenatural"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Kentaro Miura"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/91d928a1-7237-4d03-abc3-4b1440cc7d55",
                id = "91d928a1-7237-4d03-abc3-4b1440cc7d55",
                title = "The Eminence in Shadow",
                coverUrl = "https://uploads.mangadex.org/covers/91d928a1-7237-4d03-abc3-4b1440cc7d55/33439369-8fe5-4306-abab-cece3befe57b.jpg.256.jpg",
                description = "Cid renasce e cria uma organização secreta imaginária que coincidentemente existe na realidade.",
                genres = listOf("Isekai", "Ação", "Fantasia", "Comédia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Daisuke Aizawa"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/f9c9614d-0657-44c6-9c33-47fd58cd51b3",
                id = "f9c9614d-0657-44c6-9c33-47fd58cd51b3",
                title = "Fullmetal Alchemist",
                coverUrl = "https://uploads.mangadex.org/covers/f9c9614d-0657-44c6-9c33-47fd58cd51b3/2ccc4501-f3e8-4608-9789-d8308185f4c1.jpg.256.jpg",
                description = "Dois irmãos alquimistas buscam a Pedra Filosofal para restaurar seus corpos após um ritual proibido.",
                genres = listOf("Shounen", "Ação", "Aventura", "Fantasia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Hiromu Arakawa"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/158e8c61-8146-45fc-b945-d8d08a405477",
                id = "158e8c61-8146-45fc-b945-d8d08a405477",
                title = "Steins;Gate",
                coverUrl = "https://uploads.mangadex.org/covers/158e8c61-8146-45fc-b945-d8d08a405477/dc90c861-7805-4deb-a259-4ad71c895a3c.jpg.256.jpg",
                description = "Um cientista excêntrico descobre acidentalmente como enviar mensagens ao passado através de um micro-ondas.",
                genres = listOf("Sci-Fi", "Sobrenatural", "Mistério"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "5pb. / Nitroplus"
            ),
            MangaModel(
                url = "https://api.mangadex.org/manga/db692d58-4b13-4174-ae8c-30c515c0689c",
                id = "db692d58-4b13-4174-ae8c-30c515c0689c",
                title = "Hunter x Hunter",
                coverUrl = "https://uploads.mangadex.org/covers/db692d58-4b13-4174-ae8c-30c515c0689c/aa112927-f1e5-4fe4-a4db-7fd4a1536e3c.jpg.256.jpg",
                description = "Gon Freecss quer se tornar um Hunter de elite para encontrar seu pai que o deixou quando era pequeno.",
                genres = listOf("Shounen", "Ação", "Aventura", "Fantasia"),
                isNsfw = false,
                sourceId = "mangadex",
                author = "Yoshihiro Togashi"
            )
        )
    }
}
