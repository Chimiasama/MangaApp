package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookmarkEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.MangaEntity
import com.example.data.local.ProgressEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.model.ReadingStats
import com.example.data.repository.MangaRepository
import com.example.data.sync.CloudSyncState
import com.example.source.ChapterModel
import com.example.source.GenreCarouselSection
import com.example.source.MangaDexSource
import com.example.source.MangaModel
import com.example.source.MangaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import com.example.ui.reader.ReaderViewport
import com.example.ui.reader.adjacentChapter

import com.example.data.network.RetrofitClient
import com.example.data.network.model.MalRecommendationItem
import com.example.data.sync.AniListSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull

data class BrowseUiState(
    val selectedSourceId: String = "all",
    val selectedGenre: String = "all",
    val selectedLanguage: String = "pt-br",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val mangaList: List<MangaModel> = emptyList(),
    val carouselSections: List<GenreCarouselSection> = emptyList(),
    val availableSources: List<MangaSource> = emptyList(),
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
    val errorMessage: String? = null
)

data class MangaDetailUiState(
    val manga: MangaModel? = null,
    val chapters: List<ChapterModel> = emptyList(),
    val isLoading: Boolean = false,
    val isFavorite: Boolean = false,
    val readingProgress: ProgressEntity? = null,
    val malRecommendations: List<MalRecommendationItem> = emptyList(),
    val readChapterIds: Set<String> = emptySet()
)

data class ReaderUiState(
    val mangaUrl: String = "",
    val mangaTitle: String = "",
    val mangaCoverUrl: String = "",
    val sourceId: String = "",
    val currentChapter: ChapterModel? = null,
    val pages: List<String> = emptyList(),
    val currentPageIndex: Int = 0,
    val scrollOffset: Int = 0,
    val isLoading: Boolean = false,
    val chaptersList: List<ChapterModel> = emptyList(),
    val isOfflineDownloaded: Boolean = false,
    val bookmarkedPages: Set<Int> = emptySet(),
    val viewport: ReaderViewport? = null,
    val contentVersion: Long = 0,
    val pendingChapter: ChapterModel? = null,
    val errorMessage: String? = null,
    val nextChapterFirstPages: List<String> = emptyList()
)

private data class MangaData(
    val list: List<MangaModel>,
    val carousels: List<GenreCarouselSection>
)

private data class BrowseParams(
    val sourceId: String,
    val genre: String,
    val language: String,
    val query: String,
    val loading: Boolean
)

class MangaViewModel(
    val repository: MangaRepository,
    private val loadBrowseOnInit: Boolean = true,
    private val readerPages: suspend (ChapterModel, String) -> List<String> = { chapter, source ->
        repository.getPages(chapter.chapterUrl, chapter.id, source)
    }
) : ViewModel() {

    private var browseJob: Job? = null
    private var browseRequestId = 0L

    private val _selectedSourceId = MutableStateFlow("all")
    private val _selectedGenre = MutableStateFlow("all")
    private val _searchQuery = MutableStateFlow("")
    private val _isLoadingBrowse = MutableStateFlow(false)
    private val _mangaList = MutableStateFlow<List<MangaModel>>(emptyList())
    private val _carouselSections = MutableStateFlow<List<GenreCarouselSection>>(
        buildCarouselSections(MangaDexSource.STARTER_FALLBACK_MANGAS)
    )
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Room Flows
    val favorites: StateFlow<List<MangaEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readingHistory: StateFlow<List<ProgressEntity>> = repository.readingProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = repository.recentSearchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readingStats: StateFlow<ReadingStats> = repository.readingStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingStats())

    val cloudSyncState: StateFlow<CloudSyncState> = repository.cloudSyncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CloudSyncState.Idle())

    val selectedLanguage: StateFlow<String> = repository.preferencesRepository.selectedLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "pt-br")

    fun triggerCloudSync() {
        viewModelScope.launch {
            repository.triggerCloudSync()
        }
    }

    fun backupToCloud() {
        viewModelScope.launch {
            repository.backupToCloud()
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            repository.restoreFromCloud()
        }
    }

    fun setCustomSyncKey(key: String) {
        repository.setCustomSyncKey(key)
    }

    val browseUiState: StateFlow<BrowseUiState> = combine(
        combine(_selectedSourceId, _selectedGenre, selectedLanguage, _searchQuery, _isLoadingBrowse) { sourceId, genre, language, query, loading ->
            BrowseParams(sourceId, genre, language, query, loading)
        },
        combine(_mangaList, _carouselSections) { list, carousels ->
            MangaData(list, carousels)
        },
        _errorMessage,
        searchHistory
    ) { params, mangaData, error, history ->
        val sources = repository.getAvailableSources()
        BrowseUiState(
            selectedSourceId = params.sourceId,
            selectedGenre = params.genre,
            selectedLanguage = params.language,
            searchQuery = params.query,
            isLoading = params.loading,
            mangaList = mangaData.list,
            carouselSections = mangaData.carousels,
            availableSources = sources,
            searchHistory = history,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowseUiState())

    // Manga Detail State
    private val _detailManga = MutableStateFlow<MangaModel?>(null)
    private val _detailChapters = MutableStateFlow<List<ChapterModel>>(emptyList())
    private val _isLoadingDetail = MutableStateFlow(false)
    private val _isFavorite = MutableStateFlow(false)
    private val _currentMangaProgress = MutableStateFlow<ProgressEntity?>(null)

    private val _detailMalRecommendations = MutableStateFlow<List<MalRecommendationItem>>(emptyList())
    private val aniListSyncManager = AniListSyncManager()

    private val baseDetailUiState = combine(
        _detailManga,
        _detailChapters,
        _isLoadingDetail,
        _isFavorite,
        combine(_currentMangaProgress, _detailMalRecommendations) { progress, recs ->
            Pair(progress, recs)
        }
    ) { manga, chapters, loading, isFav, (progress, recs) ->
        MangaDetailUiState(
            manga = manga,
            chapters = chapters,
            isLoading = loading,
            isFavorite = isFav,
            readingProgress = progress,
            malRecommendations = recs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MangaDetailUiState())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val detailUiState: StateFlow<MangaDetailUiState> = combine(
        baseDetailUiState,
        _detailManga.flatMapLatest { manga ->
            if (manga == null) kotlinx.coroutines.flow.flowOf(Triple("", "", emptyList<String>()))
            else repository.getReadChapterIds(manga.url, manga.sourceId)
                .map { ids -> Triple(manga.url, manga.sourceId, ids) }
        }
    ) { detail, read ->
        val matchesManga = detail.manga?.url == read.first && detail.manga?.sourceId == read.second
        detail.copy(readChapterIds = if (matchesManga) read.third.toSet() else emptySet())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MangaDetailUiState())

    // Reader State
    private val _readerState = MutableStateFlow(ReaderUiState())
    val readerUiState: StateFlow<ReaderUiState> = _readerState

    init {
        // Initial load of browse items
        if (loadBrowseOnInit) loadBrowseManga()
    }

    private fun buildCarouselSections(allMangas: List<MangaModel>): List<GenreCarouselSection> {
        if (allMangas.isEmpty()) return emptyList()

        val highlights = allMangas.take(20)

        fun getForGenre(genreKey: String, keywords: List<String>): List<MangaModel> {
            val matched = allMangas.filter { manga ->
                val gLower = manga.genres.map { it.lowercase() }
                val tLower = manga.title.lowercase()
                val dLower = manga.description.lowercase()
                keywords.any { kw ->
                    gLower.any { it.contains(kw) } || tLower.contains(kw) || dLower.contains(kw)
                }
            }
            return if (matched.size >= 5) {
                matched.take(20)
            } else {
                (matched + allMangas.shuffled(java.util.Random(genreKey.hashCode().toLong())))
                    .distinctBy { it.id }
                    .take(20)
            }
        }

        return listOf(
            GenreCarouselSection(
                id = "highlights",
                title = "🔥 Destaques & Populares",
                genreKey = "all",
                items = highlights
            ),
            GenreCarouselSection(
                id = "shounen",
                title = "⚔️ Shounen Épico",
                genreKey = "Shounen",
                items = getForGenre("Shounen", listOf("shounen", "shonen", "ninja", "herói", "luta", "one piece", "naruto", "jujutsu", "chainsaw", "bleach", "hero"))
            ),
            GenreCarouselSection(
                id = "action",
                title = "💥 Ação & Aventura",
                genreKey = "Ação",
                items = getForGenre("Ação", listOf("ação", "action", "aventura", "adventure", "batalha", "combate", "caçador", "hunter"))
            ),
            GenreCarouselSection(
                id = "fantasy_isekai",
                title = "🪄 Fantasia & Isekai",
                genreKey = "Fantasia",
                items = getForGenre("Fantasia", listOf("fantasia", "fantasy", "isekai", "magia", "magic", "elfa", "slime", "reencarna"))
            ),
            GenreCarouselSection(
                id = "romance_comedy",
                title = "❤️ Romance & Comédia Escolar",
                genreKey = "Romance",
                items = getForGenre("Romance", listOf("romance", "comédia", "comedy", "escolar", "school", "escola", "amor"))
            ),
            GenreCarouselSection(
                id = "scifi_supernatural",
                title = "🚀 Sci-Fi & Sobrenatural",
                genreKey = "Sci-Fi",
                items = getForGenre("Sci-Fi", listOf("sci-fi", "science fiction", "ficção", "sobrenatural", "supernatural", "demônios", "psíquico", "tempo", "pedra"))
            ),
            GenreCarouselSection(
                id = "seinen",
                title = "🗡️ Seinen & Histórias Maduras",
                genreKey = "Seinen",
                items = getForGenre("Seinen", listOf("seinen", "sombrio", "dark", "medieval", "espada", "guerra", "vingança"))
            )
        )
    }

    fun loadBrowseManga(debounceMillis: Long = 0) {
        val request = ++browseRequestId
        browseJob?.cancel()
        val currentSource = _selectedSourceId.value
        val currentGenre = _selectedGenre.value
        val query = _searchQuery.value.trim().replace(Regex("\\s+"), " ")
        _isLoadingBrowse.value = true
        _errorMessage.value = null
        browseJob = viewModelScope.launch {
            try {
                if (debounceMillis > 0) kotlinx.coroutines.delay(debounceMillis)
                val currentLanguage = repository.preferencesRepository.selectedLanguage.first()
                val popular = query.isBlank() && (currentGenre.isBlank() || currentGenre == "all")
                val results = when {
                    popular -> repository.getPopularManga(currentSource, currentLanguage)
                    query.isNotBlank() -> repository.searchManga(query, currentSource, currentLanguage)
                    else -> {
                        val genreResults = repository.searchManga(currentGenre, currentSource, currentLanguage)
                        val baseList = if (genreResults.isNotEmpty()) genreResults
                            else repository.getPopularManga(currentSource, currentLanguage)
                        val genreLower = currentGenre.lowercase(java.util.Locale.getDefault())
                        val tagEquivalents = when (genreLower) {
                            "ação" -> listOf("action", "ação", "combate", "luta", "batalha")
                            "aventura" -> listOf("adventure", "aventura", "jornada")
                            "fantasia" -> listOf("fantasy", "magic", "fantasia", "magia")
                            "comédia" -> listOf("comedy", "comédia", "engraçado")
                            "romance" -> listOf("romance", "amor")
                            "sci-fi" -> listOf("sci-fi", "science fiction", "ficção", "futuro")
                            "sobrenatural" -> listOf("supernatural", "ghosts", "demons", "sobrenatural", "demônio", "espírito")
                            "escolar" -> listOf("school life", "school", "escolar", "escola")
                            "ninja" -> listOf("ninja", "martial arts", "shinobi")
                            "shounen" -> listOf("shounen", "shonen")
                            "seinen" -> listOf("seinen")
                            "shoujo" -> listOf("shoujo", "shojo")
                            "isekai" -> listOf("isekai", "reencarnação", "outro mundo")
                            else -> listOf(genreLower)
                        }

                        val filtered = baseList.filter { manga ->
                            manga.genres.any { g ->
                                val gLower = g.lowercase(java.util.Locale.getDefault())
                                tagEquivalents.any { eq -> gLower.contains(eq) } || gLower.contains(genreLower)
                            } ||
                            manga.title.lowercase(java.util.Locale.getDefault()).contains(genreLower) ||
                            manga.description.lowercase(java.util.Locale.getDefault()).contains(genreLower)
                        }

                        if (filtered.isNotEmpty()) filtered else baseList
                    }
                }
                ensureActive()
                if (request != browseRequestId) return@launch
                _mangaList.value = results
                if (popular && results.isNotEmpty()) _carouselSections.value = buildCarouselSections(results)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == browseRequestId) {
                    _mangaList.value = emptyList()
                    _errorMessage.value = "Erro ao carregar mangás: ${error.localizedMessage ?: "Verifique sua conexão."}"
                }
            } finally {
                if (request == browseRequestId) _isLoadingBrowse.value = false
            }
        }
    }

    fun refresh() {
        loadBrowseManga()
    }

    fun clearFiltersAndReload() {
        _searchQuery.value = ""
        _selectedGenre.value = "all"
        _selectedSourceId.value = "all"
        loadBrowseManga()
    }

    fun selectGenre(genre: String) {
        _selectedGenre.value = genre
        loadBrowseManga()
    }

    fun deleteSearchHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteSearchHistoryItem(id)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun selectSource(sourceId: String) {
        _selectedSourceId.value = sourceId
        loadBrowseManga()
    }

    private var detailChapterJob: Job? = null
    private var detailChapterRequest = 0L

    private fun loadDetailChapters(manga: MangaModel, language: String? = null) {
        val request = ++detailChapterRequest
        detailChapterJob?.cancel()
        _detailChapters.value = emptyList()
        _isLoadingDetail.value = true
        detailChapterJob = viewModelScope.launch {
            try {
                val target = language ?: repository.preferencesRepository.selectedLanguage.first()
                val chapters = repository.getAggregatedChapters(manga.title, manga.url, manga.sourceId, target)
                ensureActive()
                if (request == detailChapterRequest) _detailChapters.value = chapters
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == detailChapterRequest) _detailChapters.value = emptyList()
            } finally {
                if (request == detailChapterRequest) _isLoadingDetail.value = false
            }
        }
    }

    fun selectLanguage(language: String) {
        _detailManga.value?.let { loadDetailChapters(it, language) }
        viewModelScope.launch {
            repository.preferencesRepository.setSelectedLanguage(language)
            loadBrowseManga()
        }
    }

    fun submitSearch(query: String) {
        val trimmed = query.trim()
        _isLoadingBrowse.value = true
        _searchQuery.value = trimmed
        if (trimmed.length >= 2) {
            viewModelScope.launch {
                repository.saveSearchQuery(trimmed)
            }
        }
        loadBrowseManga()
    }

    fun searchManga(query: String, saveToHistory: Boolean = false) {
        val trimmed = query.trim()
        _isLoadingBrowse.value = true
        _searchQuery.value = query
        if (saveToHistory && trimmed.length >= 2) {
            viewModelScope.launch {
                repository.saveSearchQuery(trimmed)
            }
        }
        loadBrowseManga(debounceMillis = if (saveToHistory || trimmed.isEmpty()) 0 else 350)
    }

    fun openMangaDetails(manga: MangaModel) {
        viewModelScope.launch {
            _isLoadingDetail.value = true
            _detailManga.value = manga
            
            // Check favorite status from Room
            repository.isFavorite(manga.url).collect { isFav ->
                _isFavorite.value = isFav
            }
        }
        viewModelScope.launch {
            // Check progress from Room
            repository.getProgressForManga(manga.url).collect { prog ->
                _currentMangaProgress.value = prog
            }
        }
        loadDetailChapters(manga)
        if (repository.getSourceById(manga.sourceId) is com.example.source.HtmlMangaSource) {
            viewModelScope.launch {
                val detailed = repository.getMangaDetails(manga.url, manga.sourceId)
                if (_detailManga.value?.url == manga.url) _detailManga.value = detailed
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _detailMalRecommendations.value = emptyList()
                val cleanTitle = manga.title.replace(Regex("""(?i)\(pt-br\)|\[pt-br\]|\(color\)|\[color\]|manhwa|webtoon"""), "").trim()
                val searchRes = RetrofitClient.jikanApiService.searchManga(query = cleanTitle, limit = 1)
                val malId = searchRes.data?.firstOrNull()?.malId
                if (malId != null) {
                    val recRes = RetrofitClient.jikanApiService.getRecommendations(malId)
                    val recs = recRes.data.orEmpty().take(15).mapNotNull { rec ->
                        val entry = rec.entry ?: return@mapNotNull null
                        val imageUrl = entry.images?.jpg?.largeImageUrl
                            ?: entry.images?.jpg?.imageUrl
                            ?: entry.images?.webp?.imageUrl
                            ?: "https://picsum.photos/300/450"
                        MalRecommendationItem(
                            malId = entry.malId,
                            title = entry.title ?: "Sem título",
                            imageUrl = imageUrl,
                            votes = rec.votes ?: 0
                        )
                    }
                    _detailMalRecommendations.value = recs
                }
            } catch (e: Exception) {
                _detailMalRecommendations.value = emptyList()
            }
        }
    }

    fun toggleFavorite(manga: MangaModel) {
        viewModelScope.launch {
            if (_isFavorite.value) {
                repository.removeFavorite(manga.url)
            } else {
                repository.toggleFavorite(manga)
            }
        }
    }

    fun removeFavoriteByUrl(mangaUrl: String) {
        viewModelScope.launch {
            repository.removeFavorite(mangaUrl)
        }
    }

    fun deleteHistory(mangaUrl: String) {
        viewModelScope.launch {
            repository.deleteProgress(mangaUrl)
        }
    }

    fun downloadChapter(manga: MangaModel, chapter: ChapterModel) {
        viewModelScope.launch {
            repository.downloadChapter(manga, chapter)
        }
    }

    fun downloadMultipleChaptersWorkManager(context: android.content.Context, manga: MangaModel, chapters: List<ChapterModel>) {
        com.example.data.download.DownloadWorkHelper.enqueueMultipleChapterDownloads(context, manga, chapters)
    }

    fun deleteDownload(chapterId: String) {
        viewModelScope.launch {
            repository.deleteDownload(chapterId)
        }
    }

    fun recordReadingSession(
        mangaTitle: String,
        chapterTitle: String,
        genres: List<String>,
        pagesRead: Int,
        durationSeconds: Long
    ) {
        val currentState = _readerState.value
        val chapter = currentState.currentChapter ?: return
        viewModelScope.launch {
            repository.recordReadingSession(
                mangaUrl = currentState.mangaUrl,
                mangaTitle = mangaTitle,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                genres = genres,
                pagesRead = pagesRead,
                durationSeconds = durationSeconds
            )
        }
    }

    private var readerJob: Job? = null
    private var readerBookmarksJob: Job? = null
    private var chapterPrefetchJob: Job? = null
    private var readerRequestId = 0L
    private var retryReaderRequest: (() -> Unit)? = null
    private var prefetchedChapter: Pair<String, List<String>>? = null

    fun openReader(
        manga: MangaModel,
        chapter: ChapterModel,
        chaptersList: List<ChapterModel>,
        initialPage: Int? = null,
        initialScrollOffset: Int? = null,
        restoreViewport: Boolean = initialPage == null
    ) {
        val previous = _readerState.value
        val request = ++readerRequestId
        readerJob?.cancel()
        if (previous.mangaUrl != manga.url) readerBookmarksJob?.cancel()
        chapterPrefetchJob?.cancel()
        scrollSyncJob?.cancel()
        retryReaderRequest = { openReader(manga, chapter, chaptersList, initialPage, initialScrollOffset, restoreViewport) }
        val retained = if (previous.mangaUrl == manga.url) previous else ReaderUiState(
            mangaUrl = manga.url, mangaTitle = manga.title, mangaCoverUrl = manga.coverUrl,
            sourceId = manga.sourceId, chaptersList = chaptersList
        )
        _readerState.value = retained.copy(isLoading = true, pendingChapter = chapter, errorMessage = null)
        readerJob = viewModelScope.launch {
            try {
                if (previous.pages.isNotEmpty()) persistReaderSnapshot(previous)
                val cacheKey = "${manga.url}|${manga.sourceId}|${chapter.id}"
                val pages = prefetchedChapter?.takeIf { it.first == cacheKey }?.second
                    ?: readerPages(chapter, manga.sourceId)
                ensureActive()
                if (request != readerRequestId) return@launch
                check(pages.isNotEmpty()) { "Nenhuma página disponível. Verifique a conexão ou tente outra fonte." }
                val progress = repository.getProgressForManga(manga.url).first()
                val saved = repository.preferencesRepository.getReaderViewport(manga.url)
                    ?.takeIf { it.chapterId == chapter.id && restoreViewport }
                val savedPage = if (progress?.chapterId == chapter.id) progress.lastPageRead else 0
                val savedOffset = if (progress?.chapterId == chapter.id) progress.scrollOffset else 0
                val scale = if (previous.mangaUrl == manga.url) previous.viewport?.scale
                    ?: repository.preferencesRepository.readerOptions.first().preferredScale
                    else repository.preferencesRepository.readerOptions.first().preferredScale
                val target = (saved?.pageIndex ?: initialPage ?: savedPage).coerceIn(0, pages.lastIndex)
                _readerState.value = ReaderUiState(
                    mangaUrl = manga.url, mangaTitle = manga.title, mangaCoverUrl = manga.coverUrl,
                    sourceId = manga.sourceId, currentChapter = chapter, pages = pages,
                    currentPageIndex = target, scrollOffset = initialScrollOffset ?: savedOffset,
                    chaptersList = chaptersList, isOfflineDownloaded = pages.any { it.startsWith("file:") },
                    viewport = saved?.copy(pageIndex = target) ?: ReaderViewport(chapter.id, target, scale = scale),
                    contentVersion = request
                )
                readerBookmarksJob?.cancel()
                readerBookmarksJob = viewModelScope.launch {
                    repository.getBookmarksForChapter(chapter.id).collect { bookmarks ->
                        if (_readerState.value.currentChapter?.id == chapter.id) {
                            _readerState.value = _readerState.value.copy(bookmarkedPages = bookmarks.map { it.pageIndex }.toSet())
                        }
                    }
                }
                prefetchNextChapter()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == readerRequestId) _readerState.value = _readerState.value.copy(
                    isLoading = false, errorMessage = error.message ?: "Não foi possível carregar o capítulo.",
                    pendingChapter = chapter
                )
            }
        }
    }

    fun retryReaderChapter() { if (!_readerState.value.isLoading) retryReaderRequest?.invoke() }

    fun selectReaderChapter(chapter: ChapterModel) {
        val state = _readerState.value
        if (state.isLoading || chapter.id == state.currentChapter?.id) return
        openReader(MangaModel(url = state.mangaUrl, id = state.mangaUrl, title = state.mangaTitle,
            coverUrl = state.mangaCoverUrl, sourceId = state.sourceId), chapter, state.chaptersList, 0, 0)
    }

    fun updateReaderOptions(options: com.example.ui.reader.ReaderOptions) {
        viewModelScope.launch { repository.preferencesRepository.setReaderOptions(options) }
    }

    fun updateReaderViewport(viewport: ReaderViewport) {
        val state = _readerState.value
        if (state.isLoading || state.currentChapter?.id != viewport.chapterId) return
        _readerState.value = state.copy(viewport = viewport)
        scheduleReaderSave()
    }

    private fun prefetchNextChapter() {
        val state = _readerState.value
        if (state.isLoading || state.currentPageIndex < state.pages.size - 2) return
        val next = adjacentChapter(state.chaptersList, state.currentChapter?.id, true) ?: return
        val key = "${state.mangaUrl}|${state.sourceId}|${next.id}"
        if (prefetchedChapter?.first == key || chapterPrefetchJob?.isActive == true) return
        chapterPrefetchJob = viewModelScope.launch {
            if (!repository.preferencesRepository.pagePreloadEnabled.first() ||
                repository.preferencesRepository.batterySaverEnabled.first()) return@launch
            try {
                val pages = readerPages(next, state.sourceId)
                ensureActive()
                if (_readerState.value.currentChapter?.id == state.currentChapter?.id && pages.isNotEmpty()) {
                    prefetchedChapter = key to pages
                    _readerState.value = _readerState.value.copy(nextChapterFirstPages = pages.take(2))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Speculative loading must never interrupt reading. */ }
        }
    }

    fun togglePageBookmark(pageIndex: Int, note: String = "") {
        val currentState = _readerState.value
        val chapter = currentState.currentChapter ?: return
        val pages = currentState.pages
        if (pageIndex !in pages.indices) return

        val pageUrl = pages[pageIndex]
        val mangaTitle = if (currentState.mangaTitle.isBlank()) "Mangá" else currentState.mangaTitle

        viewModelScope.launch {
            repository.toggleBookmark(
                mangaUrl = currentState.mangaUrl,
                mangaTitle = mangaTitle,
                mangaCoverUrl = currentState.mangaCoverUrl,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                pageIndex = pageIndex,
                pageUrl = pageUrl,
                scrollOffset = currentState.scrollOffset,
                note = note
            )
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.deleteBookmarkById(bookmarkId)
        }
    }

    fun resumeReadingFromProgress(progress: ProgressEntity) {
        viewModelScope.launch {
            val cachedManga = repository.getMangaByUrlSync(progress.mangaUrl)
            val cachedChapter = repository.getChapterByIdSync(progress.chapterId)
            val cachedChapters = repository.getChaptersForMangaSync(progress.mangaUrl)
            val detectedSource = repository.detectSourceIdFromUrl(progress.mangaUrl)
                ?: repository.detectSourceIdFromUrl(progress.chapterUrl)
                ?: repository.detectSourceIdFromUrl(cachedChapter?.chapterUrl ?: "")
                ?: "mangadex"
            val realSourceId = cachedManga?.sourceId?.takeIf { it.isNotBlank() && it != "mangadex" } ?: detectedSource

            val realChapterUrl = progress.chapterUrl.ifBlank {
                cachedChapter?.chapterUrl ?: progress.chapterId
            }

            val mangaTitle = cachedManga?.title?.takeIf { it.isNotBlank() }
                ?: progress.chapterTitle.substringBefore(" - Cap").takeIf { it.isNotBlank() }
                ?: "Mangá"

            val manga = MangaModel(
                url = progress.mangaUrl,
                id = cachedManga?.id ?: progress.mangaUrl.hashCode().toString(),
                title = mangaTitle,
                coverUrl = cachedManga?.coverUrl ?: "",
                description = cachedManga?.description ?: "",
                sourceId = realSourceId,
                genres = if (!cachedManga?.genres.isNullOrBlank()) cachedManga!!.genres.split(",") else emptyList()
            )

            val chapter = ChapterModel(
                id = progress.chapterId,
                title = progress.chapterTitle.ifBlank { cachedChapter?.title ?: "Capítulo" },
                chapterUrl = realChapterUrl
            )

            val targetLanguage = repository.preferencesRepository.selectedLanguage.first()
            val chaptersList = cachedChapters.filter { com.example.source.ChapterLanguage.matches(it.language, targetLanguage) }.map { entity ->
                ChapterModel(
                    id = entity.id,
                    title = entity.title,
                    chapterUrl = entity.chapterUrl,
                    chapterNumber = entity.chapterNumber,
                    dateUpload = entity.dateUpload,
                    language = entity.language
                )
            }.ifEmpty { listOf(chapter) }

            _detailManga.value = manga
            _detailChapters.value = chaptersList

            openReader(
                manga = manga,
                chapter = chapter,
                chaptersList = chaptersList,
                initialPage = progress.lastPageRead,
                initialScrollOffset = progress.scrollOffset,
                restoreViewport = true
            )

            if (cachedChapters.isEmpty()) {
                launch(Dispatchers.IO) {
                    try {
                        val currentLang = repository.preferencesRepository.selectedLanguage.first()
                        val fullChapters = repository.getAggregatedChapters(
                            mangaTitle = manga.title,
                            mangaUrl = manga.url,
                            primarySourceId = manga.sourceId,
                            language = currentLang
                        )
                        if (fullChapters.isNotEmpty()) {
                            _detailChapters.value = fullChapters
                            val readerCurrent = _readerState.value
                            if (readerCurrent.mangaUrl == manga.url) {
                                _readerState.value = readerCurrent.copy(chaptersList = fullChapters)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun openBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            val cachedManga = repository.getMangaByUrlSync(bookmark.mangaUrl)
            val cachedChapter = repository.getChapterByIdSync(bookmark.chapterId)
            val cachedChapters = repository.getChaptersForMangaSync(bookmark.mangaUrl)
            val detectedSource = repository.detectSourceIdFromUrl(bookmark.mangaUrl)
                ?: repository.detectSourceIdFromUrl(cachedChapter?.chapterUrl ?: "")
                ?: "mangadex"
            val realSourceId = cachedManga?.sourceId?.takeIf { it.isNotBlank() && it != "mangadex" } ?: detectedSource

            val realChapterUrl = cachedChapter?.chapterUrl?.ifBlank { bookmark.chapterId } ?: bookmark.chapterId

            val mangaTitle = bookmark.mangaTitle.ifBlank { cachedManga?.title ?: "Mangá" }
            val mangaCover = bookmark.mangaCoverUrl.ifBlank { cachedManga?.coverUrl ?: "" }

            val manga = MangaModel(
                url = bookmark.mangaUrl,
                id = cachedManga?.id ?: bookmark.mangaUrl.hashCode().toString(),
                title = mangaTitle,
                coverUrl = mangaCover,
                description = cachedManga?.description ?: "",
                sourceId = realSourceId,
                genres = if (!cachedManga?.genres.isNullOrBlank()) cachedManga!!.genres.split(",") else emptyList()
            )

            val chapter = ChapterModel(
                id = bookmark.chapterId,
                title = bookmark.chapterTitle.ifBlank { cachedChapter?.title ?: "Capítulo" },
                chapterUrl = realChapterUrl
            )

            val targetLanguage = repository.preferencesRepository.selectedLanguage.first()
            val chaptersList = cachedChapters.filter { com.example.source.ChapterLanguage.matches(it.language, targetLanguage) }.map { entity ->
                ChapterModel(
                    id = entity.id,
                    title = entity.title,
                    chapterUrl = entity.chapterUrl,
                    chapterNumber = entity.chapterNumber,
                    dateUpload = entity.dateUpload,
                    language = entity.language
                )
            }.ifEmpty { listOf(chapter) }

            _detailManga.value = manga
            _detailChapters.value = chaptersList

            openReader(
                manga = manga,
                chapter = chapter,
                chaptersList = chaptersList,
                initialPage = bookmark.pageIndex,
                initialScrollOffset = bookmark.scrollOffset
            )

            if (cachedChapters.isEmpty()) {
                launch(Dispatchers.IO) {
                    try {
                        val currentLang = repository.preferencesRepository.selectedLanguage.first()
                        val fullChapters = repository.getAggregatedChapters(
                            mangaTitle = manga.title,
                            mangaUrl = manga.url,
                            primarySourceId = manga.sourceId,
                            language = currentLang
                        )
                        if (fullChapters.isNotEmpty()) {
                            _detailChapters.value = fullChapters
                            val readerCurrent = _readerState.value
                            if (readerCurrent.mangaUrl == manga.url) {
                                _readerState.value = readerCurrent.copy(chaptersList = fullChapters)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private var scrollSyncJob: kotlinx.coroutines.Job? = null

    fun updateReaderScrollPosition(pageIndex: Int, scrollOffset: Int) {
        val state = _readerState.value
        if (state.isLoading || state.pages.isEmpty()) return
        _readerState.value = state.copy(currentPageIndex = pageIndex.coerceIn(state.pages.indices), scrollOffset = scrollOffset)
        scheduleReaderSave()
        prefetchNextChapter()
    }

    private fun scheduleReaderSave() {
        scrollSyncJob?.cancel()
        val snapshot = _readerState.value
        scrollSyncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            persistReaderSnapshot(snapshot)
        }
    }

    fun flushReaderScrollPosition() {
        scrollSyncJob?.cancel()
        val snapshot = _readerState.value
        if (snapshot.pages.isNotEmpty()) viewModelScope.launch { persistReaderSnapshot(snapshot) }
    }

    private suspend fun persistReaderSnapshot(state: ReaderUiState) {
        val chapter = state.currentChapter ?: return
        if (state.pages.isEmpty()) return
        state.viewport?.let { repository.preferencesRepository.saveReaderViewport(state.mangaUrl, it) }
        if (state.currentPageIndex >= state.pages.lastIndex) {
            repository.markChapterRead(state.mangaUrl, state.sourceId, chapter.id)
        }
        repository.saveReadingProgress(state.mangaUrl, chapter.id, chapter.title, chapter.chapterUrl,
            state.currentPageIndex, state.pages.size, state.scrollOffset)
        repository.saveAutoScrollBookmark(mangaUrl = state.mangaUrl, mangaTitle = state.mangaTitle.ifBlank { "Mangá" },
            mangaCoverUrl = state.mangaCoverUrl, chapterId = chapter.id, chapterTitle = chapter.title,
            pageIndex = state.currentPageIndex, pageUrl = state.pages.getOrElse(state.currentPageIndex) { "" },
            scrollOffset = state.scrollOffset)
        syncProgressToAniList(state.mangaTitle, chapter.chapterNumber)
    }

    private fun syncProgressToAniList(mangaTitle: String, chapterNumber: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = repository.preferencesRepository.aniListToken.firstOrNull()
                val autoSync = repository.preferencesRepository.aniListAutoSync.firstOrNull() ?: true
                if (!token.isNullOrBlank() && autoSync) {
                    aniListSyncManager.syncProgress(token, mangaTitle, chapterNumber)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun updateReaderPageIndex(pageIndex: Int) {
        updateReaderScrollPosition(pageIndex, 0)
    }

    fun navigateChapter(next: Boolean) {
        val state = _readerState.value
        if (state.isLoading) return
        adjacentChapter(state.chaptersList, state.currentChapter?.id, next)?.let(::selectReaderChapter)
    }
}
