package com.example.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.source.MangaModel
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MangaDetailScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.MangaViewModel
import com.example.ui.viewmodel.SettingsViewModel

object MangaDestinations {
    const val BROWSE = "browse"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DETAIL = "detail"
    const val READER = "reader"
}

@Composable
fun MangaApp(
    mangaViewModel: MangaViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()

    val browseState by mangaViewModel.browseUiState.collectAsStateWithLifecycle()
    val detailState by mangaViewModel.detailUiState.collectAsStateWithLifecycle()
    val readerState by mangaViewModel.readerUiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val favorites by mangaViewModel.favorites.collectAsStateWithLifecycle()
    val history by mangaViewModel.readingHistory.collectAsStateWithLifecycle()
    val downloads by mangaViewModel.downloads.collectAsStateWithLifecycle()
    val bookmarks by mangaViewModel.bookmarks.collectAsStateWithLifecycle()
    val stats by mangaViewModel.readingStats.collectAsStateWithLifecycle()
    val cloudSyncState by mangaViewModel.cloudSyncState.collectAsStateWithLifecycle()
    val selectedLanguage by mangaViewModel.selectedLanguage.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavRoutes = listOf(
        MangaDestinations.BROWSE,
        MangaDestinations.LIBRARY,
        MangaDestinations.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF211F26),
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = androidx.compose.ui.graphics.Color(0xFF1D192B),
                        selectedTextColor = PrimaryPurple,
                        indicatorColor = androidx.compose.ui.graphics.Color(0xFFE8DEF8),
                        unselectedIconColor = TextSecondaryDark,
                        unselectedTextColor = TextSecondaryDark
                    )

                    NavigationBarItem(
                        selected = currentRoute == MangaDestinations.BROWSE,
                        onClick = {
                            if (currentRoute != MangaDestinations.BROWSE) {
                                navController.navigate(MangaDestinations.BROWSE) {
                                    popUpTo(MangaDestinations.BROWSE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Explorar") },
                        label = { Text("Explorar", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_item_browse"),
                        colors = navItemColors
                    )

                    NavigationBarItem(
                        selected = currentRoute == MangaDestinations.LIBRARY,
                        onClick = {
                            if (currentRoute != MangaDestinations.LIBRARY) {
                                navController.navigate(MangaDestinations.LIBRARY) {
                                    popUpTo(MangaDestinations.BROWSE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Bookmark, contentDescription = "Biblioteca") },
                        label = { Text("Biblioteca", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_item_library"),
                        colors = navItemColors
                    )

                    NavigationBarItem(
                        selected = currentRoute == MangaDestinations.SETTINGS,
                        onClick = {
                            if (currentRoute != MangaDestinations.SETTINGS) {
                                navController.navigate(MangaDestinations.SETTINGS) {
                                    popUpTo(MangaDestinations.BROWSE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                        label = { Text("Ajustes", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_item_settings"),
                        colors = navItemColors
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MangaDestinations.BROWSE,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(250)) },
            popEnterTransition = { fadeIn(animationSpec = tween(250)) },
            popExitTransition = { fadeOut(animationSpec = tween(250)) }
        ) {
            composable(MangaDestinations.BROWSE) {
                BrowseScreen(
                    state = browseState,
                    onQueryChange = { query -> mangaViewModel.searchManga(query, saveToHistory = false) },
                    onSearchSubmit = { query -> mangaViewModel.submitSearch(query) },
                    onLanguageSelected = { lang -> mangaViewModel.selectLanguage(lang) },
                    onSourceSelected = { sourceId -> mangaViewModel.selectSource(sourceId) },
                    onGenreSelected = { genre -> mangaViewModel.selectGenre(genre) },
                    onMangaClick = { manga ->
                        mangaViewModel.openMangaDetails(manga)
                        navController.navigate(MangaDestinations.DETAIL)
                    },
                    onFavoriteToggle = { manga -> mangaViewModel.toggleFavorite(manga) },
                    favoriteUrls = favorites.map { it.url }.toSet(),
                    onDeleteSearchHistoryItem = { id -> mangaViewModel.deleteSearchHistoryItem(id) },
                    onClearSearchHistory = { mangaViewModel.clearSearchHistory() },
                    onRefresh = { mangaViewModel.refresh() },
                    onClearFilters = { mangaViewModel.clearFiltersAndReload() }
                )
            }

            composable(MangaDestinations.LIBRARY) {
                LibraryScreen(
                    favorites = favorites,
                    history = history,
                    downloads = downloads,
                    bookmarks = bookmarks,
                    stats = stats,
                    onMangaClick = { manga ->
                        mangaViewModel.openMangaDetails(manga)
                        navController.navigate(MangaDestinations.DETAIL)
                    },
                    onContinueReading = { progress ->
                        mangaViewModel.resumeReadingFromProgress(progress)
                        navController.navigate(MangaDestinations.READER)
                    },
                    onReadDownloadedChapter = { manga, chapter ->
                        mangaViewModel.openReader(manga, chapter, listOf(chapter))
                        navController.navigate(MangaDestinations.READER)
                    },
                    onDeleteDownload = { chapterId ->
                        mangaViewModel.deleteDownload(chapterId)
                    },
                    onOpenBookmark = { bookmark ->
                        mangaViewModel.openBookmark(bookmark)
                        navController.navigate(MangaDestinations.READER)
                    },
                    onDeleteBookmark = { bookmarkId ->
                        mangaViewModel.deleteBookmark(bookmarkId)
                    },
                    onDeleteHistory = { mangaUrl ->
                        mangaViewModel.deleteHistory(mangaUrl)
                    },
                    onDeleteFavorite = { mangaUrl ->
                        mangaViewModel.removeFavoriteByUrl(mangaUrl)
                    },
                    onSyncClicked = { mangaViewModel.triggerCloudSync() }
                )
            }

            composable(MangaDestinations.SETTINGS) {
                val context = LocalContext.current
                SettingsScreen(
                    state = settingsState,
                    syncState = cloudSyncState,
                    onSaveRulesJson = { json -> settingsViewModel.importScraperRulesJson(json) },
                    onSyncClicked = { mangaViewModel.triggerCloudSync() },
                    onBackupClicked = { mangaViewModel.backupToCloud() },
                    onRestoreClicked = { mangaViewModel.restoreFromCloud() },
                    onSetCustomSyncKey = { key -> mangaViewModel.setCustomSyncKey(key) },
                    onThemeModeSelected = { mode -> settingsViewModel.setThemeMode(mode) },
                    onReaderModeSelected = { mode -> settingsViewModel.setReaderMode(mode) },
                    onReaderOrientationSelected = { orientation -> settingsViewModel.setReaderOrientation(orientation) },
                    onDefaultFullscreenToggled = { enabled -> settingsViewModel.setDefaultFullscreenReader(enabled) },
                    onAutoCloudSyncToggled = { enabled -> settingsViewModel.setAutoCloudSync(enabled) },
                    onReaderBackgroundColorSelected = { color -> settingsViewModel.setReaderBackgroundColor(color) },
                    onReaderInvertColorsToggled = { inverted -> settingsViewModel.setReaderInvertColors(inverted) },
                    onPagePreloadToggled = { enabled -> settingsViewModel.setPagePreloadEnabled(enabled) },
                    onClearCacheClicked = { settingsViewModel.clearImageCache(context) },
                    onRefreshCacheSize = { settingsViewModel.refreshCacheSize(context) },
                    onConnectAniList = { token -> settingsViewModel.connectAniList(token) },
                    onDisconnectAniList = { settingsViewModel.disconnectAniList() },
                    onAniListAutoSyncToggled = { enabled -> settingsViewModel.setAniListAutoSync(enabled) },
                    onClearAniListError = { settingsViewModel.clearAniListError() },
                    onBatterySaverToggled = { enabled -> settingsViewModel.setBatterySaverEnabled(enabled) },
                    onBatterySaverDimPercentChanged = { percent -> settingsViewModel.setBatterySaverDimPercent(percent) },
                    onSyncOnWifiOnlyToggled = { wifiOnly ->
                        settingsViewModel.setSyncOnWifiOnly(wifiOnly)
                        com.example.data.sync.ProgressSyncWorkHelper.schedulePeriodicProgressSync(
                            context = context,
                            requiresWifiOnly = wifiOnly,
                            requiresBatteryNotLow = true
                        )
                    }
                )
            }

            composable(
                route = MangaDestinations.DETAIL,
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                }
            ) {
                val context = LocalContext.current
                MangaDetailScreen(
                    state = detailState,
                    downloads = downloads,
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { lang -> mangaViewModel.selectLanguage(lang) },
                    onBack = { navController.popBackStack() },
                    onToggleFavorite = { manga -> mangaViewModel.toggleFavorite(manga) },
                    onChapterClick = { chapter ->
                        val manga = detailState.manga ?: return@MangaDetailScreen
                        mangaViewModel.openReader(manga, chapter, detailState.chapters)
                        navController.navigate(MangaDestinations.READER)
                    },
                    onDownloadChapter = { chapter ->
                        val manga = detailState.manga ?: return@MangaDetailScreen
                        mangaViewModel.downloadChapter(manga, chapter)
                    },
                    onDownloadMultipleChapters = { chapters ->
                        val manga = detailState.manga ?: return@MangaDetailScreen
                        mangaViewModel.downloadMultipleChaptersWorkManager(context, manga, chapters)
                    },
                    onDeleteDownload = { chapterId ->
                        mangaViewModel.deleteDownload(chapterId)
                    },
                    onSearchRecommendation = { title ->
                        mangaViewModel.searchManga(title, saveToHistory = true)
                        navController.navigate(MangaDestinations.BROWSE) {
                            popUpTo(MangaDestinations.BROWSE) { inclusive = false }
                        }
                    }
                )
            }

            composable(
                route = MangaDestinations.READER,
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(350)) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(350)) + fadeOut(animationSpec = tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(350)) + fadeOut(animationSpec = tween(300))
                }
            ) {
                ReaderScreen(
                    state = readerState,
                    readerMode = settingsState.readerMode,
                    readerOrientation = settingsState.readerOrientation,
                    defaultFullscreen = settingsState.defaultFullscreenReader,
                    readerBackgroundColor = settingsState.readerBackgroundColor,
                    readerInvertColors = settingsState.readerInvertColors,
                    pagePreloadEnabled = settingsState.pagePreloadEnabled,
                    onBack = { navController.popBackStack() },
                    onPageSelected = { index -> mangaViewModel.updateReaderPageIndex(index) },
                    onScrollPositionChanged = { index, offset -> mangaViewModel.updateReaderScrollPosition(index, offset) },
                    onFlushScrollPosition = { mangaViewModel.flushReaderScrollPosition() },
                    onNavigateChapter = { next -> mangaViewModel.navigateChapter(next) },
                    onToggleBookmark = { pageIndex -> mangaViewModel.togglePageBookmark(pageIndex) },
                    onReaderModeChanged = { mode -> settingsViewModel.setReaderMode(mode) },
                    onReaderBackgroundColorChanged = { color -> settingsViewModel.setReaderBackgroundColor(color) },
                    onReaderInvertColorsChanged = { inverted -> settingsViewModel.setReaderInvertColors(inverted) },
                    batterySaverEnabled = settingsState.batterySaverEnabled,
                    batterySaverDimPercent = settingsState.batterySaverDimPercent,
                    onBatterySaverToggled = { enabled -> settingsViewModel.setBatterySaverEnabled(enabled) },
                    onBatterySaverDimPercentChanged = { percent -> settingsViewModel.setBatterySaverDimPercent(percent) }
                )
            }
        }
    }
}
