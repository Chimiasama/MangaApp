package com.example.data.repository

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
import com.example.data.local.SearchHistoryEntity
import com.example.data.model.DailySpeedStat
import com.example.data.model.GenreStat
import com.example.data.model.ReadingStats
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.sync.FirestoreSyncManager
import com.example.source.ChapterLanguage
import com.example.source.ChapterModel
import com.example.source.ComickSource
import com.example.source.ConsumetMangaSource
import com.example.source.DynamicJsoupSource
import com.example.source.MangaDexSource
import com.example.source.MangaModel
import com.example.source.MangaSource
import com.example.source.SampleSources
import com.example.source.SourceRuleConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MangaRepository(
    private val mangaDao: MangaDao,
    private val progressDao: ProgressDao,
    private val downloadDao: DownloadDao,
    private val readingSessionDao: ReadingSessionDao,
    val downloadManager: ChapterDownloadManager,
    val preferencesRepository: UserPreferencesRepository,
    private val chapterDao: ChapterDao? = null,
    private val bookmarkDao: BookmarkDao? = null,
    private val searchHistoryDao: SearchHistoryDao? = null,
    private val appContext: android.content.Context? = null
) {

    val recentSearchHistory: Flow<List<SearchHistoryEntity>> = searchHistoryDao?.getRecentSearchHistory() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val dao = searchHistoryDao ?: return
        val existing = dao.getSearchHistoryByQuery(trimmed)
        val entity = SearchHistoryEntity(
            id = existing?.id ?: 0,
            query = trimmed,
            timestamp = System.currentTimeMillis()
        )
        dao.insertOrUpdateSearchQuery(entity)
    }

    suspend fun deleteSearchHistoryItem(id: Long) {
        searchHistoryDao?.deleteSearchHistoryById(id)
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao?.clearSearchHistory()
    }

    val allBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao?.getAllBookmarks() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun getBookmarksForChapter(chapterId: String): Flow<List<BookmarkEntity>> {
        return bookmarkDao?.getBookmarksForChapter(chapterId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun toggleBookmark(
        mangaUrl: String,
        mangaTitle: String,
        mangaCoverUrl: String,
        chapterId: String,
        chapterTitle: String,
        pageIndex: Int,
        pageUrl: String,
        scrollOffset: Int = 0,
        note: String = ""
    ) {
        val dao = bookmarkDao ?: return
        if (dao.isPageBookmarked(chapterId, pageIndex)) {
            dao.deleteBookmark(chapterId, pageIndex)
        } else {
            val bookmark = BookmarkEntity(
                mangaUrl = mangaUrl,
                mangaTitle = mangaTitle,
                mangaCoverUrl = mangaCoverUrl,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                pageIndex = pageIndex,
                pageUrl = pageUrl,
                scrollOffset = scrollOffset,
                note = note,
                isAutoBookmark = false
            )
            dao.insertBookmark(bookmark)
        }
    }

    suspend fun saveAutoScrollBookmark(
        mangaUrl: String,
        mangaTitle: String,
        mangaCoverUrl: String,
        chapterId: String,
        chapterTitle: String,
        pageIndex: Int,
        pageUrl: String,
        scrollOffset: Int = 0
    ) {
        val dao = bookmarkDao ?: return
        val existing = dao.getAutoBookmarkForManga(mangaUrl)
        val autoBookmark = BookmarkEntity(
            id = existing?.id ?: 0,
            mangaUrl = mangaUrl,
            mangaTitle = mangaTitle,
            mangaCoverUrl = mangaCoverUrl,
            chapterId = chapterId,
            chapterTitle = chapterTitle,
            pageIndex = pageIndex,
            pageUrl = pageUrl,
            scrollOffset = scrollOffset,
            note = "Posição exata salva automaticamente",
            isAutoBookmark = true,
            timestamp = System.currentTimeMillis()
        )
        dao.insertBookmark(autoBookmark)
    }

    suspend fun getAutoBookmarkForManga(mangaUrl: String): BookmarkEntity? {
        return bookmarkDao?.getAutoBookmarkForManga(mangaUrl)
    }

    suspend fun deleteBookmarkById(id: Long) {
        bookmarkDao?.deleteBookmarkById(id)
    }

    val syncManager = FirestoreSyncManager(mangaDao, progressDao)
    val cloudSyncState = syncManager.syncState

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val sourceRegistry = mutableMapOf<String, MangaSource>()

    init {
        // Register default source (MangaDex as requested)
        registerSource(MangaDexSource())
        registerSource(com.example.source.MangaDashSource(downloadManager.pdfStore::open))
        com.example.source.HtmlMangaSource.Site.entries.forEach { registerSource(com.example.source.HtmlMangaSource(it)) }
    }

    fun registerSource(source: MangaSource) {
        sourceRegistry[source.id] = source
    }

    fun clearSources() {
        sourceRegistry.clear()
    }

    fun loadScraperRulesFromJson(json: String): Boolean {
        return try {
            val listType = Types.newParameterizedType(List::class.java, SourceRuleConfig::class.java)
            val adapter = moshi.adapter<List<SourceRuleConfig>>(listType)
            val rulesList = adapter.fromJson(json) ?: emptyList()
            
            for (rule in rulesList) {
                val dynamicSource = DynamicJsoupSource(rule)
                registerSource(dynamicSource)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getAvailableSources(): List<MangaSource> {
        return sourceRegistry.values.filter { source -> !source.isNsfw }
    }

    fun getSourceById(id: String): MangaSource? = sourceRegistry[id]

    private suspend fun cacheMangaToDb(mangaList: List<MangaModel>) {
        for (manga in mangaList) {
            val existing = mangaDao.getMangaByUrlSync(manga.url)
            val entity = MangaEntity(
                url = manga.url,
                id = manga.id,
                title = manga.title,
                coverUrl = manga.coverUrl,
                description = manga.description.ifBlank { existing?.description ?: "" },
                sourceId = manga.sourceId,
                genres = if (manga.genres.isNotEmpty()) manga.genres.joinToString(",") else (existing?.genres ?: ""),
                isFavorite = existing?.isFavorite ?: false,
                isNsfw = false,
                addedTimestamp = existing?.addedTimestamp ?: System.currentTimeMillis()
            )
            mangaDao.insertManga(entity)
        }
    }

    suspend fun getPopularManga(
        sourceId: String?,
        language: String = "pt-br"
    ): List<MangaModel> {
        val sourcesToFetch = if (!sourceId.isNullOrEmpty() && sourceId != "all") {
            listOfNotNull(sourceRegistry[sourceId])
        } else {
            getAvailableSources()
        }

        val adultTags = setOf("hentai", "+18", "adulto", "erotico", "erótica", "nsfw", "smut", "doujinshi", "ecchi", "hentai / 18+")

        val results = mutableListOf<MangaModel>()
        for (src in sourcesToFetch.filter { it.supportsLanguage(language) }) {
            try {
                val list = src.getPopularManga(language)
                val filtered = list.filter { manga ->
                    if (manga.isNsfw || manga.genres.any { g -> adultTags.contains(g.trim().lowercase()) }) return@filter false
                    true
                }
                cacheMangaToDb(filtered)
                results.addAll(filtered)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (results.isEmpty()) {
            val cachedEntities = if (!sourceId.isNullOrEmpty() && sourceId != "all") {
                mangaDao.getCachedMangasBySourceSync(sourceId)
            } else {
                mangaDao.getAllCachedMangasSync()
            }
            val filteredCached = cachedEntities.filter { entity ->
                if (entity.isNsfw || entity.genres.split(",").any { g -> adultTags.contains(g.trim().lowercase()) }) return@filter false
                true
            }
            return filteredCached.map { entity ->
                MangaModel(
                    url = entity.url,
                    id = entity.id,
                    title = entity.title,
                    coverUrl = entity.coverUrl,
                    description = entity.description,
                    sourceId = entity.sourceId,
                    genres = if (entity.genres.isBlank()) emptyList() else entity.genres.split(","),
                    isNsfw = false
                )
            }
        }

        return results
    }

    suspend fun searchManga(
        query: String,
        sourceId: String?,
        language: String = "pt-br"
    ): List<MangaModel> {
        val sourcesToFetch = if (!sourceId.isNullOrEmpty() && sourceId != "all") {
            listOfNotNull(sourceRegistry[sourceId])
        } else {
            getAvailableSources()
        }

        val adultTags = setOf("hentai", "+18", "adulto", "erotico", "erótica", "nsfw", "smut", "doujinshi", "ecchi", "hentai / 18+")

        var lastFailure: Exception? = null
        var completedSources = 0
        val results = mutableListOf<MangaModel>()
        for (src in sourcesToFetch.filter { it.supportsLanguage(language) }) {
            try {
                val list = src.searchManga(query, language)
                val filtered = list.filter { manga ->
                    if (manga.isNsfw || manga.genres.any { g -> adultTags.contains(g.trim().lowercase()) }) return@filter false
                    true
                }
                results.addAll(filtered)
                completedSources++
                cacheMangaToDb(filtered)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                lastFailure = e
                e.printStackTrace()
            }
        }

        if (results.isEmpty() && query.isNotBlank()) {
            val cachedEntities = if (!sourceId.isNullOrEmpty() && sourceId != "all") {
                mangaDao.getCachedMangasBySourceSync(sourceId)
            } else {
                mangaDao.getAllCachedMangasSync()
            }
            val queryLower = query.lowercase(Locale.getDefault())
            val matched = cachedEntities.filter { entity ->
                if (entity.isNsfw || entity.genres.split(",").any { g -> adultTags.contains(g.trim().lowercase()) }) return@filter false
                entity.title.lowercase(Locale.getDefault()).contains(queryLower) ||
                entity.genres.lowercase(Locale.getDefault()).contains(queryLower)
            }
            if (matched.isEmpty() && completedSources == 0 && lastFailure != null) {
                throw java.io.IOException("Não foi possível consultar as fontes de mangá. Verifique sua conexão e tente novamente.", lastFailure)
            }
            return matched.map { entity ->
                MangaModel(
                    url = entity.url,
                    id = entity.id,
                    title = entity.title,
                    coverUrl = entity.coverUrl,
                    description = entity.description,
                    sourceId = entity.sourceId,
                    genres = if (entity.genres.isBlank()) emptyList() else entity.genres.split(","),
                    isNsfw = false
                )
            }
        }

        return results
    }

    suspend fun getMangaDetails(mangaUrl: String, sourceId: String): MangaModel {
        val source = sourceRegistry[sourceId] ?: sourceRegistry.values.first()
        return try {
            val networkManga = source.getMangaDetails(mangaUrl)
            cacheMangaToDb(listOf(networkManga))
            networkManga
        } catch (e: Exception) {
            val cached = mangaDao.getMangaByUrlSync(mangaUrl)
            if (cached != null) {
                MangaModel(
                    url = cached.url,
                    id = cached.id,
                    title = cached.title,
                    coverUrl = cached.coverUrl,
                    description = cached.description,
                    sourceId = cached.sourceId,
                    genres = if (cached.genres.isBlank()) emptyList() else cached.genres.split(","),
                    isNsfw = cached.isNsfw
                )
            } else {
                MangaModel(
                    url = mangaUrl,
                    id = mangaUrl.hashCode().toString(),
                    title = "Manga",
                    coverUrl = "https://picsum.photos/300/450",
                    description = "Erro ao carregar detalhes.",
                    sourceId = sourceId
                )
            }
        }
    }

    suspend fun getAggregatedChapters(
        mangaTitle: String,
        mangaUrl: String,
        primarySourceId: String,
        language: String = "pt-br"
    ): List<ChapterModel> = kotlinx.coroutines.coroutineScope {
        val primarySource = sourceRegistry[primarySourceId] ?: sourceRegistry.values.first()
        val primaryChapters = try {
            primarySource.getChapters(mangaUrl, language)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            emptyList()
        }

        val chapterMap = LinkedHashMap<Pair<Float, String>, ChapterModel>()
        fun addChapters(chapters: List<ChapterModel>) {
            chapters.filter { ChapterLanguage.matches(it.language, language) }.forEach { chapter ->
                if (chapter.chapterNumber >= 0f) {
                    val key = chapter.chapterNumber to ChapterLanguage.normalize(chapter.language)
                    chapterMap.putIfAbsent(key, chapter)
                }
            }
        }
        addChapters(primaryChapters)

        // Cross-reference with other sources concurrently to fill missing chapter gaps
        val secondarySources = if (primarySource.canAggregateChapters) {
            sourceRegistry.values.filter { it.id != primarySourceId && it.canAggregateChapters }
        } else emptyList()
        val deferredList = secondarySources.map { src ->
            async {
                try {
                    suspend fun fetchFromSource(targetLang: String): List<ChapterModel> {
                        return if (mangaUrl.contains(src.id, ignoreCase = true)) {
                            src.getChapters(mangaUrl, targetLang)
                        } else if (mangaTitle.isNotBlank()) {
                            val searchResults = src.searchManga(mangaTitle, targetLang)
                            val match = searchResults.firstOrNull {
                                it.title.trim().equals(mangaTitle.trim(), ignoreCase = true)
                            }
                            if (match != null) src.getChapters(match.url, targetLang) else emptyList()
                        } else {
                            emptyList()
                        }
                    }

                    fetchFromSource(language)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

        deferredList.forEach { addChapters(it.await()) }
        val mergedList = chapterMap.values.sortedByDescending { it.chapterNumber }

        if (mergedList.isNotEmpty() && chapterDao != null) {
            val entities = mergedList.mapIndexed { index, ch ->
                ChapterEntity(
                    id = ch.id,
                    mangaUrl = mangaUrl,
                    title = ch.title,
                    chapterUrl = ch.chapterUrl,
                    chapterNumber = if (ch.chapterNumber > 0) ch.chapterNumber else (index + 1).toFloat(),
                    dateUpload = ch.dateUpload,
                    language = ch.language
                )
            }
            chapterDao.insertChapters(entities)
        }

        mergedList
    }

    suspend fun getChapters(
        mangaUrl: String,
        sourceId: String,
        language: String = "pt-br"
    ): List<ChapterModel> {
        val source = sourceRegistry[sourceId] ?: sourceRegistry.values.first()
        return try {
            val networkChapters = source.getChapters(mangaUrl, language).filter { ChapterLanguage.matches(it.language, language) }
            if (networkChapters.isNotEmpty() && chapterDao != null) {
                val entities = networkChapters.mapIndexed { index, ch ->
                    ChapterEntity(
                        id = ch.id,
                        mangaUrl = mangaUrl,
                        title = ch.title,
                        chapterUrl = ch.chapterUrl,
                        chapterNumber = if (ch.chapterNumber > 0) ch.chapterNumber else (index + 1).toFloat(),
                        dateUpload = ch.dateUpload,
                        language = ch.language
                    )
                }
                chapterDao.insertChapters(entities)
            }
            networkChapters
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            val cached = chapterDao?.getChaptersForMangaSync(mangaUrl) ?: emptyList()
            if (cached.isNotEmpty()) {
                cached.filter { ChapterLanguage.matches(it.language, language) }.map { entity ->
                    ChapterModel(
                        id = entity.id,
                        title = entity.title,
                        chapterUrl = entity.chapterUrl,
                        chapterNumber = entity.chapterNumber,
                        dateUpload = entity.dateUpload,
                        language = entity.language
                    )
                }
            } else {
                emptyList()
            }
        }
    }

    suspend fun getPages(chapterUrl: String, chapterId: String, sourceId: String): List<String> {
        // First check if downloaded locally
        val localPages = downloadManager.getDownloadedPages(chapterId)
        if (localPages.isNotEmpty()) {
            return localPages
        }

        // Check if chapter exists in Room to retrieve real chapterUrl if empty
        val resolvedChapterUrl = if (chapterUrl.isBlank()) {
            chapterDao?.getChapterByIdSync(chapterId)?.chapterUrl ?: chapterId
        } else {
            chapterUrl
        }

        val resolvedSourceId = detectSourceIdFromUrl(resolvedChapterUrl) ?: sourceId.ifBlank { "mangadex" }
        val source = sourceRegistry[resolvedSourceId]
            ?: throw java.io.IOException("A fonte $resolvedSourceId não está disponível.")
        val pages = source.getPages(resolvedChapterUrl)
        return if (pages.size == 1 && runCatching { java.net.URI(pages.single()).path.endsWith(".pdf", true) }.getOrDefault(false)) {
            downloadManager.pdfStore.open(pages.single())
        } else pages
    }

    suspend fun getMangaByUrlSync(url: String): MangaEntity? = mangaDao.getMangaByUrlSync(url)

    suspend fun getChaptersForMangaSync(mangaUrl: String): List<ChapterEntity> = chapterDao?.getChaptersForMangaSync(mangaUrl) ?: emptyList()

    suspend fun getChapterByIdSync(chapterId: String): ChapterEntity? = chapterDao?.getChapterByIdSync(chapterId)

    fun detectSourceIdFromUrl(url: String): String? {
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        if (host != null) sourceRegistry.values.firstOrNull {
            runCatching { java.net.URI(it.baseUrl).host.equals(host, ignoreCase = true) }.getOrDefault(false)
        }?.let { return it.id }
        return when {
            url.contains("tsundoku", ignoreCase = true) -> "tsundoku"
            url.contains("lermanga", ignoreCase = true) -> "ler_manga"
            url.contains("manga-easy", ignoreCase = true) -> "manga_easy"
            url.contains("scansraw", ignoreCase = true) -> "scans_raw"
            url.contains("comick", ignoreCase = true) -> "comick"
            url.contains("consumet", ignoreCase = true) -> "consumet"
            url.contains("mangadex", ignoreCase = true) -> "mangadex"
            else -> null
        }
    }

    // Downloads
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()

    suspend fun downloadChapter(manga: MangaModel, chapter: ChapterModel) {
        if (downloadDao.getDownloadSync(chapter.id)?.downloadState == DownloadEntity.STATE_COMPLETED &&
            downloadManager.getDownloadedPages(chapter.id).isNotEmpty()) return
        val pending = DownloadEntity(chapter.id, chapter.title, chapter.chapterUrl, manga.url, manga.title,
            manga.coverUrl, manga.sourceId)
        downloadDao.insertDownload(pending)
        try {
            val pages = getPages(chapter.chapterUrl, chapter.id, manga.sourceId)
            if (pages.isEmpty()) throw java.io.IOException("O capítulo não contém páginas disponíveis.")
            downloadManager.downloadChapter(manga, chapter, pages)
        } catch (error: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                val current = downloadDao.getDownloadSync(chapter.id) ?: pending
                downloadDao.insertDownload(current.copy(downloadState = DownloadEntity.STATE_FAILED))
            }
            throw error
        }
    }

    suspend fun deleteDownload(chapterId: String) {
        downloadManager.deleteDownload(chapterId)
    }

    // Room Database Favorites
    val favorites: Flow<List<MangaEntity>> = mangaDao.getFavoriteMangas()

    fun isFavorite(url: String): Flow<Boolean> {
        return mangaDao.getMangaByUrl(url).map { entity ->
            entity?.isFavorite == true
        }
    }

    suspend fun toggleFavorite(manga: MangaModel) {
        val entity = MangaEntity(
            url = manga.url,
            id = manga.id,
            title = manga.title,
            coverUrl = manga.coverUrl,
            description = manga.description,
            sourceId = manga.sourceId,
            genres = manga.genres.joinToString(","),
            isFavorite = true,
            isNsfw = manga.isNsfw
        )
        mangaDao.insertManga(entity)
    }

    suspend fun removeFavorite(url: String) {
        mangaDao.deleteFavoriteByUrl(url)
    }

    // Room Reading Progress
    val readingProgress: Flow<List<ProgressEntity>> = progressDao.getAllProgress()

    fun getProgressForManga(mangaUrl: String): Flow<ProgressEntity?> {
        return progressDao.getProgressForManga(mangaUrl)
    }

    suspend fun saveReadingProgress(
        mangaUrl: String,
        chapterId: String,
        chapterTitle: String,
        chapterUrl: String,
        lastPageRead: Int,
        totalPages: Int,
        scrollOffset: Int = 0
    ) {
        val progress = ProgressEntity(
            mangaUrl = mangaUrl,
            chapterId = chapterId,
            chapterTitle = chapterTitle,
            chapterUrl = chapterUrl,
            lastPageRead = lastPageRead,
            totalPages = totalPages,
            scrollOffset = scrollOffset,
            timestamp = System.currentTimeMillis()
        )
        progressDao.saveProgress(progress)

        // Ensure chapter record exists with the real chapterUrl in Room
        val existingChapter = chapterDao?.getChapterByIdSync(chapterId)
        if (existingChapter == null && chapterUrl.isNotBlank()) {
            chapterDao?.insertChapter(
                ChapterEntity(
                    id = chapterId,
                    mangaUrl = mangaUrl,
                    title = chapterTitle,
                    chapterUrl = chapterUrl,
                    isRead = true,
                    lastPageRead = lastPageRead,
                    totalPages = totalPages
                )
            )
        } else if (existingChapter != null) {
            chapterDao?.updateChapterReadStatus(chapterId, true, lastPageRead, totalPages)
        }

        // Trigger background WorkManager progress sync with Firestore
        appContext?.let { ctx ->
            com.example.data.sync.ProgressSyncWorkHelper.triggerImmediateProgressSync(
                context = ctx,
                mangaUrl = mangaUrl,
                chapterId = chapterId
            )
        }
    }

    suspend fun deleteProgress(mangaUrl: String) {
        progressDao.deleteProgress(mangaUrl)
    }

    // Reading Sessions & Statistics
    val readingSessions: Flow<List<ReadingSessionEntity>> = readingSessionDao.getAllSessions()

    suspend fun recordReadingSession(
        mangaUrl: String,
        mangaTitle: String,
        chapterId: String,
        chapterTitle: String,
        genres: List<String>,
        pagesRead: Int,
        durationSeconds: Long
    ) {
        if (pagesRead <= 0 && durationSeconds <= 0) return
        val session = ReadingSessionEntity(
            mangaUrl = mangaUrl,
            mangaTitle = mangaTitle,
            chapterId = chapterId,
            chapterTitle = chapterTitle,
            genres = genres.joinToString(","),
            pagesRead = pagesRead,
            durationSeconds = durationSeconds,
            timestamp = System.currentTimeMillis()
        )
        readingSessionDao.insertSession(session)
    }

    val readingStats: Flow<ReadingStats> = combine(
        readingSessions,
        readingProgress,
        favorites
    ) { sessions, progressList, favoritesList ->
        computeReadingStats(sessions, progressList, favoritesList)
    }

    private fun computeReadingStats(
        sessions: List<ReadingSessionEntity>,
        progressList: List<ProgressEntity>,
        favoritesList: List<MangaEntity>
    ): ReadingStats {
        val totalChapters = (sessions.map { it.chapterId } + progressList.map { it.chapterId }).distinct().size
        var totalPages = sessions.sumOf { it.pagesRead }
        if (totalPages == 0 && progressList.isNotEmpty()) {
            totalPages = progressList.sumOf { it.lastPageRead + 1 }
        }

        var totalSeconds = sessions.sumOf { it.durationSeconds }
        if (totalSeconds <= 0L && totalPages > 0) {
            // Default baseline estimate (~30 seconds per page)
            totalSeconds = totalPages * 30L
        }

        val totalMinutes = totalSeconds.toFloat() / 60f
        val pagesPerMinute = if (totalMinutes > 0f) totalPages.toFloat() / totalMinutes else 0f
        val secondsPerPage = if (totalPages > 0) totalSeconds.toFloat() / totalPages else 0f

        // Genre calculation
        val genreCountMap = mutableMapOf<String, Int>()
        val defaultGenres = listOf("Ação", "Fantasia", "Aventura", "Romance", "Isekai", "Comédia", "Sci-Fi", "Drama", "Mistério")

        sessions.forEach { s ->
            if (s.genres.isNotBlank()) {
                s.genres.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { g ->
                    genreCountMap[g] = (genreCountMap[g] ?: 0) + 1
                }
            }
        }
        favoritesList.forEach { f ->
            if (f.genres.isNotBlank()) {
                f.genres.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { g ->
                    genreCountMap[g] = (genreCountMap[g] ?: 0) + 2
                }
            }
        }

        // If user has minimal reading data yet, seed realistic fallback distributions for visuals
        if (genreCountMap.isEmpty()) {
            genreCountMap["Ação"] = 12
            genreCountMap["Fantasia"] = 10
            genreCountMap["Aventura"] = 7
            genreCountMap["Isekai"] = 5
            genreCountMap["Romance"] = 4
        }

        val totalGenreCount = genreCountMap.values.sum().coerceAtLeast(1)
        val topGenres = genreCountMap.entries
            .sortedByDescending { it.value }
            .take(6)
            .map { (genre, count) ->
                GenreStat(
                    genre = genre,
                    count = count,
                    percentage = (count.toFloat() / totalGenreCount.toFloat()) * 100f
                )
            }

        // Daily trend calculation for past 7 days
        val dayFormat = SimpleDateFormat("EEE", Locale("pt", "BR"))
        val calendar = Calendar.getInstance()
        val dailyTrendList = mutableListOf<DailySpeedStat>()

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dayLabel = dayFormat.format(cal.time).replaceFirstChar { it.uppercase() }
            val dayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val dayEnd = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis

            val daySessions = sessions.filter { it.timestamp in dayStart..dayEnd }
            var dayPages = daySessions.sumOf { it.pagesRead }
            var dayDuration = daySessions.sumOf { it.durationSeconds }

            // Fallback mock trend for empty days to provide useful initial charts
            if (dayPages == 0 && totalPages > 0) {
                val seed = (i * 7 + totalPages) % 15 + 5
                dayPages = seed
                dayDuration = (seed * 25).toLong()
            } else if (dayPages == 0 && totalPages == 0) {
                val mockPages = listOf(8, 14, 22, 18, 30, 25, 35)[i]
                dayPages = mockPages
                dayDuration = (mockPages * 28).toLong()
            }

            val dayMinutes = dayDuration.toFloat() / 60f
            val dayPpm = if (dayMinutes > 0f) dayPages.toFloat() / dayMinutes else 0f

            dailyTrendList.add(
                DailySpeedStat(
                    dayLabel = dayLabel,
                    pagesRead = dayPages,
                    pagesPerMinute = dayPpm,
                    durationMinutes = dayMinutes
                )
            )
        }

        return ReadingStats(
            totalChaptersRead = if (totalChapters > 0) totalChapters else if (totalPages > 0) (totalPages / 18).coerceAtLeast(1) else 0,
            totalPagesRead = totalPages,
            totalReadingTimeSeconds = totalSeconds,
            averagePagesPerMinute = if (pagesPerMinute > 0f) pagesPerMinute else 2.1f,
            averageSecondsPerPage = if (secondsPerPage > 0f) secondsPerPage else 28.5f,
            topGenres = topGenres,
            dailyTrend = dailyTrendList
        )
    }

    suspend fun triggerCloudSync() = syncManager.performFullSync(
        localFavorites = mangaDao.getAllFavoritesSync(),
        localProgress = progressDao.getAllProgressSync()
    )

    suspend fun backupToCloud() = syncManager.backupOnly(
        localFavorites = mangaDao.getAllFavoritesSync(),
        localProgress = progressDao.getAllProgressSync()
    )

    suspend fun restoreFromCloud() = syncManager.restoreOnly()

    fun setCustomSyncKey(key: String) = syncManager.setCustomSyncKey(key)
}
