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
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val mangaList: List<MangaModel> = emptyList(),
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
    val isLoading: Boolean = false,
    val chaptersList: List<ChapterModel> = emptyList(),
    val isOfflineDownloaded: Boolean = false,
    val bookmarkedPages: Set<Int> = emptySet()
)

private data class BrowseParams(
    val sourceId: String,
    val genre: String,
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
    private val _errorMessage = MutableStateFlow<String?>(null)

    val adultContentEnabled: StateFlow<Boolean> = repository.preferencesRepository.adultContentEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filterAdultSearchEnabled: StateFlow<Boolean> = repository.preferencesRepository.filterAdultContentInSearch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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
        combine(_selectedSourceId, _selectedGenre, _searchQuery, _isLoadingBrowse) { sourceId, genre, query, loading ->
            BrowseParams(sourceId, genre, query, loading)
        },
        _mangaList,
        _errorMessage,
        adultContentEnabled,
        searchHistory
    ) { params, list, error, adultEnabled, history ->
        val sources = repository.getAvailableSources(includeAdult = adultEnabled)
        BrowseUiState(
            selectedSourceId = params.sourceId,
            selectedGenre = params.genre,
            searchQuery = params.query,
            isLoading = params.loading,
            mangaList = list,
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

    fun loadBrowseManga() {
        viewModelScope.launch {
            _isLoadingBrowse.value = true
            _errorMessage.value = null
            val adultEnabled = adultContentEnabled.value
            val filterAdultSearch = filterAdultSearchEnabled.value
            val currentSource = _selectedSourceId.value
            val currentGenre = _selectedGenre.value
            val query = _searchQuery.value

            try {
                var list = if (query.isBlank()) {
                    repository.getPopularManga(currentSource, adultEnabled, filterAdultSearch)
                } else {
                    repository.saveSearchQuery(query)
                    repository.searchManga(query, currentSource, adultEnabled, filterAdultSearch)
                }

                if (currentGenre.isNotBlank() && currentGenre != "all") {
                    val genreLower = currentGenre.lowercase(java.util.Locale.getDefault())
                    val tagEquivalents = when (genreLower) {
                        "ação" -> listOf("action")
                        "aventura" -> listOf("adventure")
                        "fantasia" -> listOf("fantasy", "magic")
                        "comédia" -> listOf("comedy")
                        "romance" -> listOf("romance")
                        "sci-fi" -> listOf("sci-fi", "science fiction")
                        "sobrenatural" -> listOf("supernatural", "ghosts", "demons")
                        "escolar" -> listOf("school life")
                        "ninja" -> listOf("ninja", "martial arts")
                        else -> listOf(genreLower)
                    }

                    list = list.filter { manga ->
                        manga.genres.any { g ->
                            val gLower = g.lowercase(java.util.Locale.getDefault())
                            tagEquivalents.any { eq -> gLower.contains(eq) } || gLower.contains(genreLower)
                        } ||
                        manga.title.lowercase(java.util.Locale.getDefault()).contains(genreLower) ||
                        manga.description.lowercase(java.util.Locale.getDefault()).contains(genreLower)
                    }
                }

                _mangaList.value = list
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar mangás: ${e.localizedMessage}"
            } finally {
                _isLoadingBrowse.value = false
            }
        }
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

    fun searchManga(query: String) {
        _searchQuery.value = query
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
                val chapters = repository.getChapters(manga.url, manga.sourceId)
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
        initialPage: Int? = null
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
            
            // Get last saved page if available
            val progress = repository.getProgressForManga(manga.url).first()
            val savedPage = if (progress?.chapterId == chapter.id) progress.lastPageRead else 0
            val targetPage = initialPage ?: savedPage

            _readerState.value = _readerState.value.copy(
                pages = pages,
                currentPageIndex = if (targetPage in pages.indices) targetPage else 0,
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
            initialPage = bookmark.pageIndex
        )
    }

    fun updateReaderPageIndex(pageIndex: Int) {
        val currentState = _readerState.value
        val chapter = currentState.currentChapter ?: return
        val totalPages = currentState.pages.size
        if (totalPages == 0) return

        val newIndex = pageIndex.coerceIn(0, totalPages - 1)
        _readerState.value = currentState.copy(currentPageIndex = newIndex)

        // Save reading progress to Room DB
        viewModelScope.launch {
            repository.saveReadingProgress(
                mangaUrl = currentState.mangaUrl,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                chapterUrl = chapter.chapterUrl,
                lastPageRead = newIndex,
                totalPages = totalPages
            )
        }
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
