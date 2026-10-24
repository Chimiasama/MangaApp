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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val readingProgress: ProgressEntity? = null
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
    val bookmarkedPages: Set<Int> = emptySet()
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
    val repository: MangaRepository
) : ViewModel() {

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

    val detailUiState: StateFlow<MangaDetailUiState> = combine(
        _detailManga,
        _detailChapters,
        _isLoadingDetail,
        _isFavorite,
        _currentMangaProgress
    ) { manga, chapters, loading, isFav, progress ->
        MangaDetailUiState(
            manga = manga,
            chapters = chapters,
            isLoading = loading,
            isFavorite = isFav,
            readingProgress = progress
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MangaDetailUiState())

    // Reader State
    private val _readerState = MutableStateFlow(ReaderUiState())
    val readerUiState: StateFlow<ReaderUiState> = _readerState

    init {
        // Initial load of browse items
        loadBrowseManga()
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

    fun loadBrowseManga() {
        viewModelScope.launch {
            _isLoadingBrowse.value = true
            _errorMessage.value = null
            val currentSource = _selectedSourceId.value
            val currentGenre = _selectedGenre.value
            val currentLanguage = selectedLanguage.value
            val query = _searchQuery.value

            try {
                if (query.isBlank() && (currentGenre.isBlank() || currentGenre == "all")) {
                    val popularList = repository.getPopularManga(currentSource, currentLanguage)
                    _mangaList.value = popularList
                    if (popularList.isNotEmpty()) {
                        _carouselSections.value = buildCarouselSections(popularList)
                    }
                } else if (query.isNotBlank()) {
                    val searchResults = repository.searchManga(query, currentSource, currentLanguage)
                    _mangaList.value = searchResults
                } else {
                    // Genre filter is active (e.g., clicked "Mostrar mais" or selected genre chip)
                    val genreResults = repository.searchManga(currentGenre, currentSource, currentLanguage)
                    val baseList = if (genreResults.isNotEmpty()) {
                        genreResults
                    } else {
                        repository.getPopularManga(currentSource, currentLanguage)
                    }

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

                    _mangaList.value = if (filtered.isNotEmpty()) filtered else baseList
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar mangás: ${e.localizedMessage ?: "Verifique sua conexão."}"
            } finally {
                _isLoadingBrowse.value = false
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

    fun selectLanguage(language: String) {
        viewModelScope.launch {
            repository.preferencesRepository.setSelectedLanguage(language)
            loadBrowseManga()
            val currentManga = _detailManga.value
            if (currentManga != null) {
                try {
                    _isLoadingDetail.value = true
                    _detailChapters.value = repository.getAggregatedChapters(
                        mangaTitle = currentManga.title,
                        mangaUrl = currentManga.url,
                        primarySourceId = currentManga.sourceId,
                        language = language
                    )
                } catch (e: Exception) {
                    _detailChapters.value = emptyList()
                } finally {
                    _isLoadingDetail.value = false
                }
            }
        }
    }

    fun submitSearch(query: String) {
        val trimmed = query.trim()
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
        _searchQuery.value = query
        if (saveToHistory && trimmed.length >= 2) {
            viewModelScope.launch {
                repository.saveSearchQuery(trimmed)
            }
        }
        loadBrowseManga()
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
        viewModelScope.launch {
            try {
                val currentLang = selectedLanguage.value
                val chapters = repository.getAggregatedChapters(
                    mangaTitle = manga.title,
                    mangaUrl = manga.url,
                    primarySourceId = manga.sourceId,
                    language = currentLang
                )
                _detailChapters.value = chapters
            } catch (e: Exception) {
                _detailChapters.value = emptyList()
            } finally {
                _isLoadingDetail.value = false
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

    fun openReader(
        manga: MangaModel,
        chapter: ChapterModel,
        chaptersList: List<ChapterModel>,
        initialPage: Int? = null,
        initialScrollOffset: Int? = null
    ) {
        viewModelScope.launch {
            _readerState.value = ReaderUiState(
                mangaUrl = manga.url,
                mangaTitle = manga.title,
                mangaCoverUrl = manga.coverUrl,
                sourceId = manga.sourceId,
                currentChapter = chapter,
                isLoading = true,
                chaptersList = chaptersList
            )

            // Collect bookmarked pages for this chapter
            viewModelScope.launch {
                repository.getBookmarksForChapter(chapter.id).collect { chapterBookmarks ->
                    val set = chapterBookmarks.map { it.pageIndex }.toSet()
                    _readerState.value = _readerState.value.copy(bookmarkedPages = set)
                }
            }

            val pages = repository.getPages(chapter.chapterUrl, chapter.id, manga.sourceId)
            val isOffline = pages.any { it.startsWith("file://") }
            
            // Get last saved page and exact scroll offset if available from Room
            val progress = repository.getProgressForManga(manga.url).first()
            val savedPage = if (progress?.chapterId == chapter.id) progress.lastPageRead else 0
            val savedOffset = if (progress?.chapterId == chapter.id) progress.scrollOffset else 0
            val targetPage = initialPage ?: savedPage
            val targetOffset = initialScrollOffset ?: savedOffset

            _readerState.value = _readerState.value.copy(
                pages = pages,
                currentPageIndex = if (targetPage in pages.indices) targetPage else 0,
                scrollOffset = targetOffset,
                isLoading = false,
                isOfflineDownloaded = isOffline
            )
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

    fun openBookmark(bookmark: BookmarkEntity) {
        val dummyManga = MangaModel(
            url = bookmark.mangaUrl,
            id = bookmark.mangaUrl.hashCode().toString(),
            title = bookmark.mangaTitle,
            coverUrl = bookmark.mangaCoverUrl,
            sourceId = "mangadex"
        )
        val dummyChapter = ChapterModel(
            id = bookmark.chapterId,
            title = bookmark.chapterTitle,
            chapterUrl = ""
        )
        openReader(
            manga = dummyManga,
            chapter = dummyChapter,
            chaptersList = listOf(dummyChapter),
            initialPage = bookmark.pageIndex,
            initialScrollOffset = bookmark.scrollOffset
        )
    }

    private var scrollSyncJob: kotlinx.coroutines.Job? = null

    fun updateReaderScrollPosition(pageIndex: Int, scrollOffset: Int) {
        val currentState = _readerState.value
        val chapter = currentState.currentChapter ?: return
        val totalPages = currentState.pages.size
        if (totalPages == 0) return

        val newIndex = pageIndex.coerceIn(0, totalPages - 1)
        _readerState.value = currentState.copy(
            currentPageIndex = newIndex,
            scrollOffset = scrollOffset
        )

        scrollSyncJob?.cancel()
        scrollSyncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            persistCurrentScrollPosition(newIndex, scrollOffset)
        }
    }

    fun flushReaderScrollPosition() {
        scrollSyncJob?.cancel()
        val currentState = _readerState.value
        if (currentState.pages.isNotEmpty()) {
            viewModelScope.launch {
                persistCurrentScrollPosition(currentState.currentPageIndex, currentState.scrollOffset)
            }
        }
    }

    private suspend fun persistCurrentScrollPosition(pageIndex: Int, scrollOffset: Int) {
        val currentState = _readerState.value
        val chapter = currentState.currentChapter ?: return
        val totalPages = currentState.pages.size
        if (totalPages == 0) return

        // 1. Sync reading progress with exact scroll offset to local Room
        repository.saveReadingProgress(
            mangaUrl = currentState.mangaUrl,
            chapterId = chapter.id,
            chapterTitle = chapter.title,
            chapterUrl = chapter.chapterUrl,
            lastPageRead = pageIndex,
            totalPages = totalPages,
            scrollOffset = scrollOffset
        )

        // 2. Automatically sync bookmark for exact scroll position to local Room
        val pageUrl = currentState.pages.getOrElse(pageIndex) { "" }
        val mangaTitle = if (currentState.mangaTitle.isBlank()) "Mangá" else currentState.mangaTitle
        repository.saveAutoScrollBookmark(
            mangaUrl = currentState.mangaUrl,
            mangaTitle = mangaTitle,
            mangaCoverUrl = currentState.mangaCoverUrl,
            chapterId = chapter.id,
            chapterTitle = chapter.title,
            pageIndex = pageIndex,
            pageUrl = pageUrl,
            scrollOffset = scrollOffset
        )
    }

    fun updateReaderPageIndex(pageIndex: Int) {
        updateReaderScrollPosition(pageIndex, 0)
    }

    fun navigateChapter(next: Boolean) {
        val currentState = _readerState.value
        val chapters = currentState.chaptersList
        val currentCh = currentState.currentChapter ?: return
        val currentIndex = chapters.indexOfFirst { it.id == currentCh.id }
        if (currentIndex == -1) return

        val targetIndex = if (next) currentIndex - 1 else currentIndex + 1
        if (targetIndex in chapters.indices) {
            val targetChapter = chapters[targetIndex]
            val mockManga = MangaModel(
                url = currentState.mangaUrl,
                id = currentState.mangaUrl.hashCode().toString(),
                title = "",
                coverUrl = "",
                sourceId = currentState.sourceId
            )
            openReader(mockManga, targetChapter, chapters)
        }
    }
}
