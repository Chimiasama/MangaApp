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
import com.example.source.CatalogFilters
import com.example.source.IncludeMode
import com.example.source.BrowseFilterRequest
import com.example.source.MangaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import com.example.home.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

enum class BrowseFailure { OFFLINE, TIMEOUT, SOURCE }

data class ConnectionDiagnosticState(val isRunning: Boolean = false, val report: String? = null)

data class FilterPreviewState(val request: BrowseFilterRequest? = null, val count: Int? = null)

data class BrowseUiState(
    val selectedSourceId: String = "all",
    val selectedGenre: String = "all",
    val selectedYearRange: String = "all",
    val selectedLanguage: String = "pt-br",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val mangaList: List<MangaModel> = emptyList(),
    val carouselSections: List<GenreCarouselSection> = emptyList(),
    val availableSources: List<MangaSource> = emptyList(),
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
    val errorMessage: String? = null,
    val catalogFilters: CatalogFilters = CatalogFilters(),
    val failure: BrowseFailure? = null,
    val connectionDiagnostic: ConnectionDiagnosticState = ConnectionDiagnosticState(),
    val filterPreview: FilterPreviewState = FilterPreviewState(),
    val unclassifiedExcluded: Int = 0,
    val isNsfwEnabled: Boolean = false
)

data class HomeUiState(val sections: List<GenreCarouselSection> = emptyList(), val isLoading: Boolean = true, val error: String? = null, val configurations: List<HomeSection> = emptyList())

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

private data class BrowseSelection(
    val source: String = "all", val genre: String = "all", val year: String = "all",
    val language: String = "pt-br", val query: String = "", val classifications: CatalogFilters = CatalogFilters()
)

private data class MangaData(
    val list: List<MangaModel>,
    val carousels: List<GenreCarouselSection>,
    val selection: BrowseSelection = BrowseSelection(),
    val error: String? = null,
    val failure: BrowseFailure? = null,
    val unclassified: Int = 0
)

private fun normalizedBrowseQuery(query: String) = query.trim().replace(Regex("""\s+"""), " ")

fun matchesYearRange(year: Int?, rangeKey: String, description: String = ""): Boolean {
    if (rangeKey.isBlank() || rangeKey == "all") return true
    val actualYear = year ?: Regex("""(?i)(?:lançado|publicado|lançamento|publicação|published|released)\s*(?:no ano de|em|in|:)?\s*((?:19|20)\d{2})\b""").find(description)?.groupValues?.get(1)?.toIntOrNull()
    if (rangeKey == "unknown" || rangeKey == "none") {
        return actualYear == null
    }
    if (actualYear == null) return false
    val parts = rangeKey.split("-")
    if (parts.size != 2) return true
    val startYear = parts[0].toIntOrNull() ?: return true
    val endYear = parts[1].toIntOrNull() ?: return true
    return actualYear in startYear..endYear
}

class MangaViewModel(
    val repository: MangaRepository,
    private val loadBrowseOnInit: Boolean = true,
    private val loadHomeOnInit: Boolean = false,
    private val readerPages: suspend (ChapterModel, String) -> List<String> = { chapter, source ->
        repository.getPages(chapter.chapterUrl, chapter.id, source)
    },
    private val connectionDiagnosticRunner: suspend () -> String = {
        com.example.data.network.MangaDexConnectionDiagnostic().run()
    }
) : ViewModel() {

    private var filterPreviewJob: Job? = null
    private val _filterPreview = MutableStateFlow(FilterPreviewState())
    private var diagnosticJob: Job? = null
    private var diagnosticRequestId = 0L
    private val _connectionDiagnostic = MutableStateFlow(ConnectionDiagnosticState())

    private var applyFiltersJob: Job? = null
    private var browseJob: Job? = null
    private var browseRequestId = 0L

    private val _catalogFilters = MutableStateFlow(CatalogFilters())
    private val _selectedSourceId = MutableStateFlow("all")
    private val _selectedGenre = MutableStateFlow("all")
    private val _selectedYearRange = MutableStateFlow("all")
    private val _searchQuery = MutableStateFlow("")
    private val _isLoadingBrowse = MutableStateFlow(false)
    private val _browseData = MutableStateFlow(MangaData(
        emptyList(), emptyList()
    ))

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState
    private var homeJob: Job? = null

    private var homeConfigurations: List<HomeSection> = HomeSections.defaults
    private var homeCatalogSections = emptyList<GenreCarouselSection>()
    private var homeHistoryItems = emptyList<MangaModel>()
    private var browseRequested = false

    fun ensureSearchLoaded() { if (!browseRequested) loadBrowseManga() }

    fun refreshHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch { loadHomeSections(homeConfigurations) }
    }

    private fun publishHome(loading: Boolean = false, error: String? = null) {
        val sections = homeConfigurations.filter { it.active }.sortedBy { it.position }.mapNotNull { section ->
            if (section.type == HomeSectionType.CONTINUE_READING)
                GenreCarouselSection(section.id, section.title, "all", homeHistoryItems, true)
            else homeCatalogSections.find { it.id == section.id }?.copy(title = section.title)
        }
        _homeUiState.value = HomeUiState(deduplicateHomeSections(sections, homeConfigurations.associate { it.id to it.itemCount }), loading, error, homeConfigurations)
    }

    private suspend fun loadHomeSections(configurations: List<HomeSection>) {
        homeConfigurations = configurations
        homeCatalogSections = emptyList()
        publishHome(loading = true)
        val pools = mutableMapOf<Triple<String, String, String>, List<MangaModel>>()
        var failure: String? = null
        for (section in configurations.filter { it.active && it.type == HomeSectionType.CATALOG }.sortedBy { it.position }) {
            try {
                val filter = section.filters
                val key = Triple(filter.sourceId, filter.language, filter.yearRange)
                val candidates = pools[key] ?: repository.loadCatalog("", filter.sourceId, filter.language, filter.yearRange).items.also { pools[key] = it }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val items = sortHomeItems(candidates.filter {
                    filter.filters.matches(it) && matchesYearRange(it.year, filter.yearRange, it.description)
                }, section.sort)
                homeCatalogSections = homeCatalogSections + GenreCarouselSection(section.id, section.title, "all", items)
                publishHome(loading = true, error = failure)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = "Não foi possível atualizar todas as seções." }
        }
        if (repository.preferencesRepository.cachedNsfw) {
            try {
                val adultItems = pools[Triple("all", "pt-br", "all")]?.filter { manga ->
                    manga.isNsfw || manga.contentRating in listOf("erotica", "pornographic") ||
                    manga.genres.any { g ->
                        val gl = g.lowercase()
                        gl.contains("hentai") || gl.contains("doujinshi") || gl.contains("ecchi") || gl.contains("smut")
                    }
                } ?: repository.loadCatalog("", "all", "pt-br", "all",
                    CatalogFilters(included = setOf("genre:doujinshi", "rating:pornographic"), includeMode = IncludeMode.ANY)).items
                if (adultItems.isNotEmpty()) {
                    homeCatalogSections = homeCatalogSections + GenreCarouselSection(
                        id = "adult_doujinshi",
                        title = "🔞 Adulto & Doujinshi (+18)",
                        genreKey = "all",
                        items = adultItems.take(20)
                    )
                }
            } catch (_: Exception) {}
        }
        publishHome(error = failure)
    }

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

    val isNsfwEnabled: StateFlow<Boolean> = repository.preferencesRepository.nsfwEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.preferencesRepository.cachedNsfw)

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
        combine(_selectedSourceId, _selectedGenre, _selectedYearRange, selectedLanguage, _searchQuery) { sourceId, genre, yearRange, language, query ->
            Triple(sourceId, genre, yearRange) to (language to query)
        },
        _isLoadingBrowse,
        _browseData,
        _catalogFilters,
        combine(searchHistory, isNsfwEnabled) { history, nsfw -> history to nsfw }
    ) { (filters, langAndQuery), loading, mangaData, advanced, (history, isNsfw) ->
        val sources = repository.getAvailableSources()
        val effectiveFilters = advanced.withLegacy(filters.second)
        val selection = BrowseSelection(filters.first, filters.second, filters.third,
            langAndQuery.first, normalizedBrowseQuery(langAndQuery.second), effectiveFilters)
        val matchesSelection = mangaData.selection == selection
        BrowseUiState(
            selectedSourceId = filters.first,
            selectedGenre = filters.second,
            selectedYearRange = filters.third,
            selectedLanguage = langAndQuery.first,
            searchQuery = langAndQuery.second,
            isLoading = loading || !matchesSelection,
            mangaList = if (matchesSelection) mangaData.list else emptyList(),
            carouselSections = if (matchesSelection) mangaData.carousels else emptyList(),
            availableSources = sources,
            searchHistory = history,
            errorMessage = if (matchesSelection) mangaData.error else null,
            failure = if (matchesSelection) mangaData.failure else null,
            catalogFilters = effectiveFilters,
            unclassifiedExcluded = if (matchesSelection) mangaData.unclassified else 0,
            isNsfwEnabled = isNsfw
        )
    }.combine(_filterPreview) { state, preview -> state.copy(filterPreview = preview)
    }.combine(_connectionDiagnostic) { state, diagnostic ->
        state.copy(connectionDiagnostic = diagnostic)
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
        if (loadHomeOnInit) {
            viewModelScope.launch {
                repository.preferencesRepository.homeSections.collectLatest { sections ->
                    homeJob?.cancel()
                    homeJob = launch { loadHomeSections(sections) }
                }
            }
            viewModelScope.launch {
                repository.readingProgress.distinctUntilChangedBy { history -> history.map { it.mangaUrl } }.collectLatest { history ->
                    homeHistoryItems = history.map { progress ->
                        val cached = repository.getMangaByUrlSync(progress.mangaUrl)
                        MangaModel(progress.mangaUrl, cached?.id ?: progress.mangaUrl, cached?.title ?: "Continuar lendo", cached?.coverUrl.orEmpty(),
                            sourceId = cached?.sourceId.orEmpty())
                    }
                    publishHome(_homeUiState.value.isLoading, _homeUiState.value.error)
                }
            }
        }
        viewModelScope.launch {
            repository.connectivity.distinctUntilChanged().collect { online ->
                if (applyFiltersJob?.isActive != true &&
                    ((online && _browseData.value.failure == BrowseFailure.OFFLINE) ||
                        (!online && _isLoadingBrowse.value))) loadBrowseManga()
            }
        }
        viewModelScope.launch {
            isNsfwEnabled.drop(1).collect {
                refreshHome()
                loadBrowseManga()
            }
        }
    }

    fun previewBrowseFilters(request: BrowseFilterRequest) {
        filterPreviewJob?.cancel()
        val current = browseUiState.value
        val currentRequest = BrowseFilterRequest(current.searchQuery, current.selectedSourceId, current.selectedLanguage,
            current.selectedYearRange, current.catalogFilters)
        if (request == currentRequest && !current.isLoading) {
            _filterPreview.value = FilterPreviewState(request, current.mangaList.size)
            return
        }
        _filterPreview.value = FilterPreviewState(request)
        filterPreviewJob = viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            try {
                if (!repository.isNetworkAvailable()) return@launch
                val result = kotlinx.coroutines.withTimeout(120_000) {
                    repository.loadCatalog(request.query, request.sourceId, request.language, request.yearRange, request.filters)
                }
                ensureActive()
                _filterPreview.value = FilterPreviewState(request, result.items.count {
                    request.filters.matches(it) && matchesYearRange(it.year, request.yearRange, it.description)
                })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _filterPreview.value = FilterPreviewState(request) }
        }
    }

    fun dismissFilterPreview() { filterPreviewJob?.cancel(); _filterPreview.value = FilterPreviewState() }

    fun applyBrowseFilters(request: BrowseFilterRequest) {
        dismissFilterPreview()
        val applyRequest = ++browseRequestId
        browseJob?.cancel()
        _isLoadingBrowse.value = true
        _selectedSourceId.value = request.sourceId
        _selectedGenre.value = "all"
        _selectedYearRange.value = request.yearRange
        _searchQuery.value = request.query.trim()
        _catalogFilters.value = request.filters
        applyFiltersJob?.cancel()
        applyFiltersJob = viewModelScope.launch {
            repository.preferencesRepository.setSelectedLanguage(request.language)
            if (request.query.trim().length >= 2) repository.saveSearchQuery(request.query.trim())
            if (applyRequest == browseRequestId) loadBrowseManga()
        }
    }

    fun setCatalogFilters(filters: CatalogFilters) {
        _selectedGenre.value = "all"
        _catalogFilters.value = filters
        loadBrowseManga()
    }

    fun loadBrowseManga(debounceMillis: Long = 0) {
        browseRequested = true
        val request = ++browseRequestId
        browseJob?.cancel()
        val currentSource = _selectedSourceId.value
        val currentGenre = _selectedGenre.value
        val currentYearRange = _selectedYearRange.value
        val catalogFilters = _catalogFilters.value.withLegacy(currentGenre)
        val query = normalizedBrowseQuery(_searchQuery.value)
        var requestLanguage = selectedLanguage.value
        fun selection() = BrowseSelection(currentSource, currentGenre, currentYearRange, requestLanguage, query, catalogFilters)
        _isLoadingBrowse.value = true
        _browseData.update { it.copy(error = null, failure = null) }
        browseJob = viewModelScope.launch {
            try {
                if (debounceMillis > 0) kotlinx.coroutines.delay(debounceMillis)
                val currentLanguage = repository.preferencesRepository.selectedLanguage.first()
                requestLanguage = currentLanguage
                if (!repository.isNetworkAvailable()) throw java.net.UnknownHostException("offline")
                val popular = query.isBlank() && !catalogFilters.isActive
                var sourceWarning: String? = null
                val baseResults = kotlinx.coroutines.withTimeout(120_000) { when {
                    catalogFilters.isActive || (currentYearRange != "all" && currentYearRange.isNotBlank()) -> {
                        val catalog = repository.loadCatalog(query, currentSource, currentLanguage, currentYearRange, catalogFilters)
                        sourceWarning = catalog.warning
                        catalog.items
                    }
                    popular -> repository.getPopularManga(currentSource, currentLanguage)
                    query.isNotBlank() -> repository.searchManga(query, currentSource, currentLanguage)
                    else -> repository.getPopularManga(currentSource, currentLanguage)
                }

                }

                val results = if (currentYearRange != "all" && currentYearRange.isNotBlank()) {
                    baseResults.filter { manga ->
                        matchesYearRange(manga.year, currentYearRange, manga.description)
                    }
                } else {
                    baseResults
                }

                ensureActive()
                if (request != browseRequestId) return@launch
                _browseData.value = MangaData(
                    list = results.filter(catalogFilters::matches),
                    carousels = emptyList(),
                    selection = selection(),
                    error = sourceWarning,
                    unclassified = results.count { catalogFilters.missingExclusionData(it) }
                )
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                if (request == browseRequestId) _browseData.value = browseError(selection(), error, BrowseFailure.TIMEOUT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == browseRequestId) _browseData.value = browseError(selection(), error)
            } finally {
                if (request == browseRequestId) _isLoadingBrowse.value = false
            }
        }
    }

    private fun browseError(selection: BrowseSelection, error: Throwable, kind: BrowseFailure? = null): MangaData {
        val causes = generateSequence(error) { it.cause }.take(10).toList()
        val failure = kind ?: when {
            !repository.isNetworkAvailable() -> BrowseFailure.OFFLINE
            causes.any { it is java.net.SocketTimeoutException } -> BrowseFailure.TIMEOUT
            else -> BrowseFailure.SOURCE
        }
        val message = when (failure) {
            BrowseFailure.OFFLINE -> "Aguardando conexão para pesquisar novamente. A busca será retomada quando a internet voltar."
            BrowseFailure.TIMEOUT -> "A consulta demorou demais e foi interrompida. Tente novamente."
            BrowseFailure.SOURCE -> "Não foi possível concluir a consulta às fontes. Tente novamente."
        }
        android.util.Log.w("BrowseSearch", "failure=$failure source=${selection.source} year=${selection.year} language=${selection.language}", error)
        return MangaData(emptyList(), emptyList(), selection,
            error = "$message\n\nDetalhes:\n${com.example.source.catalogFailureDetails(error)}", failure = failure)
    }

    fun diagnoseMangaDexConnection() {
        if (_connectionDiagnostic.value.isRunning) return
        val request = ++diagnosticRequestId
        _connectionDiagnostic.value = ConnectionDiagnosticState(isRunning = true)
        diagnosticJob = viewModelScope.launch {
            try {
                val report = connectionDiagnosticRunner()
                if (request == diagnosticRequestId) _connectionDiagnostic.value = ConnectionDiagnosticState(report = report)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (request == diagnosticRequestId) _connectionDiagnostic.value = ConnectionDiagnosticState(
                    report = "Não foi possível concluir o diagnóstico.\n${com.example.source.catalogFailureDetails(error)}")
            }
        }
    }

    fun dismissConnectionDiagnostic() {
        diagnosticRequestId++
        diagnosticJob?.cancel()
        _connectionDiagnostic.value = ConnectionDiagnosticState()
    }

    fun refresh() {
        loadBrowseManga()
    }

    fun clearFiltersAndReload() {
        _searchQuery.value = ""
        _catalogFilters.value = CatalogFilters()
        _selectedGenre.value = "all"
        _selectedSourceId.value = "all"
        _selectedYearRange.value = "all"
        loadBrowseManga()
    }

    fun selectGenre(genre: String) {
        _selectedGenre.value = genre
        loadBrowseManga()
    }

    fun selectYearRange(yearRange: String) {
        _selectedYearRange.value = yearRange
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
                        val markedPages = bookmarks.map { it.pageIndex }.toSet()
                        _readerState.update { current ->
                            if (current.contentVersion == request && current.currentChapter?.id == chapter.id)
                                current.copy(bookmarkedPages = markedPages)
                            else current
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
                    _readerState.update { current ->
                        if (current.contentVersion == state.contentVersion && !current.isLoading)
                            current.copy(nextChapterFirstPages = pages.take(2))
                        else current
                    }
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

    fun setChapterRead(chapter: ChapterModel, read: Boolean) {
        val manga = _detailManga.value ?: return
        viewModelScope.launch {
            if (read) repository.markChapterRead(manga.url, manga.sourceId, chapter.id)
            else repository.markChapterUnread(manga.url, manga.sourceId, chapter.id)
        }
    }

    fun completeReaderChapter(chapterId: String) {
        val state = _readerState.value
        if (state.isLoading || state.pages.isEmpty() || state.currentChapter?.id != chapterId) return
        viewModelScope.launch { repository.markChapterRead(state.mangaUrl, state.sourceId, chapterId) }
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
