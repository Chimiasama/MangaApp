package com.example

import com.example.source.*
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.download.ChapterDownloadManager
import com.example.data.repository.MangaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveHistoricCatalogCheck {
    @Test fun catalogAllSourcesAllLanguagesHistoricRange() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = ViewModelStore()
        try {
            val repository = MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(), db.readingSessionDao(),
                ChapterDownloadManager(context, db.downloadDao()), UserPreferencesRepository(context),
                db.chapterDao(), db.bookmarkDao(), appContext = context)
            val catalog = withTimeout(90_000) { repository.loadCatalog("", "all", "all", "1900-1950", CatalogFilters()) }
            val records = catalog.items.filter { it.year?.let { year -> year in 1900..1950 } == true }
            println("LIVE HISTORIC APP records=${records.size}; sources=${records.map { it.sourceId }.distinct()}; warning=${catalog.warning}")
            assertTrue("The aggregated catalog must find historic works", records.isNotEmpty())
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun allLanguagesWithoutTitleFindsWorksPublishedFrom1900To1950() = runBlocking {
        val results = MangaDexSource().getCatalogManga("", "all", "1900-1950")
        val historical = results.filter { it.year != null && it.year in 1900..1950 && !it.isNsfw }
        println("LIVE HISTORIC catalog=${results.size}; nonAdultInRange=${historical.size}; years=${historical.mapNotNull { it.year }.distinct().sorted()}")
        assertTrue("1900–1950 should not be an empty search", historical.isNotEmpty())
    }
}
