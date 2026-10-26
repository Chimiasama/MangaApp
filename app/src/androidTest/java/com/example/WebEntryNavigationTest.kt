package com.example

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.ui.MangaApp
import com.example.ui.theme.MangaReaderTheme
import com.example.ui.viewmodel.MangaViewModel
import com.example.ui.viewmodel.SettingsViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Covers the actual Scaffold + NavHost + dark theme, rather than just the destination in isolation. */
@RunWith(AndroidJUnit4::class)
class WebEntryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase
    private val store = ViewModelStore()
    @After fun close() { store.clear(); if (::db.isInitialized) db.close() }
    private fun containsBrowser(view: View): Boolean = view is WebView ||
        (view is ViewGroup && (0 until view.childCount).any { containsBrowser(view.getChildAt(it)) })
    @Test fun browseButtonImmediatelyShowsSearchAndSourceControlsInTheApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val prefs = UserPreferencesRepository(context)
        val repository = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(), db.readingSessionDao(),
            ChapterDownloadManager(context, db.downloadDao()), prefs, db.chapterDao(), appContext = context)
        repository.clearSources() // Navigation never depends on an external catalog or internet connection.
        val provider = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                (if (modelClass == MangaViewModel::class.java) MangaViewModel(repository, loadBrowseOnInit = false)
                else SettingsViewModel(repository, prefs)) as T
        })
        val manga = provider[MangaViewModel::class.java]
        val settings = provider[SettingsViewModel::class.java]
        compose.setContent { MangaReaderTheme("DARK") { MangaApp(manga, settings) } }
        compose.runOnIdle { manga.submitSearch("dragon ball") }
        compose.waitUntil(5000) { compose.onAllNodesWithText("dragon ball").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search_input").performClick()
        compose.onNodeWithTag("browse_web_sources").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("source_tools").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("web_address").assertIsDisplayed().assertTextContains("dragon ball")
        compose.onNodeWithTag("web_go").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Reparar").assertIsDisplayed()
        compose.onNodeWithText("Regras / API").assertIsDisplayed()
        compose.runOnIdle { assertFalse("The blank entry screen must not initialize WebView", containsBrowser(compose.activity.window.decorView)) }
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.cacheDir
        directory.mkdirs()
        File(directory, "web-entry-dark.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Reparar").performClick()
        compose.onNodeWithText("Fonte selecionada: mangadash").assertExists()
        compose.onNodeWithText("Voltar").performClick()
        compose.onNodeWithTag("browse_web_sources").assertIsDisplayed().performClick()
        compose.onNodeWithTag("web_address").assertIsDisplayed()
        compose.runOnIdle { assertFalse(containsBrowser(compose.activity.window.decorView)) }
    }
}
