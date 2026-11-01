package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.UserPreferencesRepository
import com.example.util.CacheCleanupManager
import com.example.util.ImageCacheConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CacheCleanupAndFavoritesTest {

    private lateinit var context: Context
    private lateinit var preferencesRepository: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferencesRepository = UserPreferencesRepository(context)
    }

    @Test
    fun `test cache auto-cleanup preferences defaults and updates`() {
        runBlocking {
            // Defaults
            assertTrue(preferencesRepository.autoCleanCacheEnabled.first())
            assertEquals(3, preferencesRepository.autoCleanCacheDays.first())
            assertTrue(preferencesRepository.autoCleanDownloadsEnabled.first())
            assertTrue(preferencesRepository.keepFavoritesDownloads.first())

            // Updates
            preferencesRepository.setAutoCleanCacheEnabled(false)
            assertFalse(preferencesRepository.autoCleanCacheEnabled.first())

            preferencesRepository.setAutoCleanCacheDays(7)
            assertEquals(7, preferencesRepository.autoCleanCacheDays.first())

            preferencesRepository.setKeepFavoritesDownloads(false)
            assertFalse(preferencesRepository.keepFavoritesDownloads.first())
        }
    }

    @Test
    fun `test disk cache purge removes files older than retention days`() {
        runBlocking {
            val cacheDir = context.cacheDir.resolve("manga_image_disk_cache")
            cacheDir.mkdirs()

            // Create a recent file
            val recentFile = File(cacheDir, "recent_page.jpg")
            recentFile.writeBytes(ByteArray(1024) { 1 })
            recentFile.setLastModified(System.currentTimeMillis())

            // Create an old file (> 3 days old)
            val oldFile = File(cacheDir, "old_page.jpg")
            oldFile.writeBytes(ByteArray(2048) { 2 })
            val fourDaysAgo = System.currentTimeMillis() - (4L * 24L * 60L * 60L * 1000L)
            oldFile.setLastModified(fourDaysAgo)

            assertTrue(recentFile.exists())
            assertTrue(oldFile.exists())

            // Run purge with 3 days retention
            val bytesReclaimed = CacheCleanupManager.purgeOldDiskCache(context, days = 3)

            assertTrue("Expected bytes reclaimed >= 2048, got $bytesReclaimed", bytesReclaimed >= 2048L)
            assertTrue("Recent file should still exist", recentFile.exists())
            assertFalse("Old file should be deleted", oldFile.exists())

            // Cleanup
            recentFile.delete()
        }
    }

    @Test
    fun `test storage stats calculation accurately reports bytes and formatting`() {
        val cacheDir = context.cacheDir.resolve("manga_image_disk_cache")
        cacheDir.mkdirs()
        val testPage = File(cacheDir, "sample_page.jpg")
        testPage.writeBytes(ByteArray(1024 * 50) { 0 }) // 50 KB

        val downloadsDir = File(context.filesDir, "manga_downloads")
        downloadsDir.mkdirs()
        val testDownload = File(downloadsDir, "download_sample.jpg")
        testDownload.writeBytes(ByteArray(1024 * 100) { 0 }) // 100 KB

        val stats = CacheCleanupManager.getStorageStats(context)
        assertTrue(stats.pageCacheBytes >= 50 * 1024L)
        assertTrue(stats.downloadsBytes >= 100 * 1024L)
        assertTrue(stats.totalBytes >= 150 * 1024L)

        // Verify format helper
        assertEquals("0 B", ImageCacheConfig.formatSizeBytes(0L))
        assertTrue(stats.pageCacheFormatted.contains("KB") || stats.pageCacheFormatted.contains("MB"))

        // Cleanup
        testPage.delete()
        testDownload.delete()
    }
}
