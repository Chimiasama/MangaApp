package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil.Coil
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.ui.MangaApp
import com.example.ui.theme.MangaReaderTheme
import com.example.ui.viewmodel.MangaViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.util.ImageCacheConfig

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure Coil Disk & Memory Image Caching for Manga Pages and Covers
        val customImageLoader = ImageCacheConfig.setupCoilImageLoader(applicationContext)
        Coil.setImageLoader(customImageLoader)

        // Clean Architecture Initialization
        val db = AppDatabase.getDatabase(applicationContext)
        val preferencesRepository = UserPreferencesRepository(applicationContext)
        val downloadManager = ChapterDownloadManager(
            context = applicationContext,
            downloadDao = db.downloadDao()
        )

        val mangaRepository = MangaRepository(
            mangaDao = db.mangaDao(),
            progressDao = db.progressDao(),
            downloadDao = db.downloadDao(),
            readingSessionDao = db.readingSessionDao(),
            downloadManager = downloadManager,
            preferencesRepository = preferencesRepository,
            chapterDao = db.chapterDao(),
            bookmarkDao = db.bookmarkDao(),
            searchHistoryDao = db.searchHistoryDao()
        )

        val mangaViewModel = MangaViewModel(mangaRepository)
        val settingsViewModel = SettingsViewModel(mangaRepository, preferencesRepository)

        setContent {
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            MangaReaderTheme(themeMode = settingsUiState.themeMode) {
                MangaApp(
                    mangaViewModel = mangaViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}
