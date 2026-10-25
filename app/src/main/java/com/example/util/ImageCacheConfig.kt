package com.example.util

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import okhttp3.OkHttpClient

object ImageCacheConfig {

    fun setupCoilImageLoader(context: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder().addInterceptor(com.example.source.SourceHttp.imageHeaders).build()

        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .components { add(com.example.data.pdf.PdfPageFetcher.Factory()) }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.35) // 35% of memory for manga image caching
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("manga_image_disk_cache"))
                    .maxSizeBytes(500L * 1024 * 1024) // 500 MB disk cache for manga covers & pages
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .allowRgb565(true) // Reduces bitmap RAM and GPU memory bandwidth by 50% for high battery efficiency
            .crossfade(true)
            .build()
    }

    fun getDiskCacheSizeBytes(context: Context): Long {
        val dir = context.cacheDir.resolve("manga_image_disk_cache")
        val pdfBytes = context.cacheDir.resolve("pdf_chapters").walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return pdfBytes + if (dir.exists()) {
            dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else 0L
    }

    fun formatSizeBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun clearImageCache(context: Context) {
        try {
            coil.Coil.imageLoader(context).diskCache?.clear()
            coil.Coil.imageLoader(context).memoryCache?.clear()
        } catch (_: Exception) {}
        context.cacheDir.resolve("pdf_chapters").deleteRecursively()
        val dir = context.cacheDir.resolve("manga_image_disk_cache")
        if (dir.exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
        }
    }
}
