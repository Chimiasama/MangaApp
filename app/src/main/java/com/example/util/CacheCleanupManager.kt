package com.example.util

import android.content.Context
import com.example.data.repository.MangaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

object CacheCleanupManager {

    private const val DEFAULT_RETENTION_DAYS = 3
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    data class StorageStats(
        val pageCacheBytes: Long,
        val downloadsBytes: Long,
        val totalBytes: Long = pageCacheBytes + downloadsBytes
    ) {
        val pageCacheFormatted: String get() = ImageCacheConfig.formatSizeBytes(pageCacheBytes)
        val downloadsFormatted: String get() = ImageCacheConfig.formatSizeBytes(downloadsBytes)
        val totalFormatted: String get() = ImageCacheConfig.formatSizeBytes(totalBytes)
    }

    data class CleanupResult(
        val bytesReclaimed: Long = 0L,
        val downloadsDeleted: Int = 0
    )

    fun getStorageStats(context: Context): StorageStats {
        val pageCacheBytes = ImageCacheConfig.getDiskCacheSizeBytes(context)
        val downloadsDir = File(context.filesDir, "manga_downloads")
        val downloadsBytes = if (downloadsDir.exists()) {
            downloadsDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else 0L

        return StorageStats(
            pageCacheBytes = pageCacheBytes,
            downloadsBytes = downloadsBytes
        )
    }

    suspend fun purgeOldDiskCache(
        context: Context,
        days: Int = DEFAULT_RETENTION_DAYS
    ): Long = withContext(Dispatchers.IO) {
        val dir = context.cacheDir.resolve("manga_image_disk_cache")
        if (!dir.exists()) return@withContext 0L

        val cutoff = System.currentTimeMillis() - (days.coerceAtLeast(1) * MILLIS_PER_DAY)
        var bytesReclaimed = 0L

        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    val length = file.length()
                    if (file.delete()) {
                        bytesReclaimed += length
                    }
                }
            }
        } catch (_: Exception) {}

        bytesReclaimed
    }

    suspend fun purgeOldDownloads(
        context: Context,
        repository: MangaRepository,
        days: Int = DEFAULT_RETENTION_DAYS,
        protectFavorites: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (days.coerceAtLeast(1) * MILLIS_PER_DAY)
        var deletedCount = 0

        try {
            val allDownloads = repository.downloadDao.getAllDownloadsSync()
            val favoriteUrls = if (protectFavorites) {
                try {
                    repository.favorites.first().map { it.url }.toSet()
                } catch (_: Exception) {
                    emptySet()
                }
            } else {
                emptySet()
            }

            for (download in allDownloads) {
                // If protecting favorites and this manga is a favorite, do not auto-delete
                if (protectFavorites && favoriteUrls.contains(download.mangaUrl)) {
                    continue
                }

                if (download.timestamp < cutoff) {
                    repository.deleteDownload(download.chapterId)
                    deletedCount++
                }
            }
        } catch (_: Exception) {}

        deletedCount
    }

    suspend fun performAutoCleanup(
        context: Context,
        repository: MangaRepository
    ): CleanupResult = withContext(Dispatchers.IO) {
        try {
            val prefs = repository.preferencesRepository
            val cacheCleanupEnabled = prefs.autoCleanCacheEnabled.first()
            val retentionDays = prefs.autoCleanCacheDays.first()
            val downloadsCleanupEnabled = prefs.autoCleanDownloadsEnabled.first()
            val protectFavorites = prefs.keepFavoritesDownloads.first()

            var bytesReclaimed = 0L
            var downloadsDeleted = 0

            if (cacheCleanupEnabled) {
                bytesReclaimed += purgeOldDiskCache(context, retentionDays)
            }

            if (downloadsCleanupEnabled) {
                downloadsDeleted = purgeOldDownloads(context, repository, retentionDays, protectFavorites)
            }

            CleanupResult(bytesReclaimed, downloadsDeleted)
        } catch (_: Exception) {
            CleanupResult()
        }
    }

    suspend fun clearAllPageCache(context: Context): Long = withContext(Dispatchers.IO) {
        val initialBytes = ImageCacheConfig.getDiskCacheSizeBytes(context)
        ImageCacheConfig.clearImageCache(context)
        val remainingBytes = ImageCacheConfig.getDiskCacheSizeBytes(context)
        (initialBytes - remainingBytes).coerceAtLeast(0L)
    }

    suspend fun clearAllDownloads(context: Context, repository: MangaRepository): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val allDownloads = repository.downloadDao.getAllDownloadsSync()
            for (download in allDownloads) {
                repository.deleteDownload(download.chapterId)
                count++
            }
            val downloadsDir = File(context.filesDir, "manga_downloads")
            if (downloadsDir.exists()) {
                downloadsDir.deleteRecursively()
                downloadsDir.mkdirs()
            }
        } catch (_: Exception) {}
        count
    }
}
