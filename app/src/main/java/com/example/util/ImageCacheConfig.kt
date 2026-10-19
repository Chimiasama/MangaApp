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
        val okHttpClient = OkHttpClient.Builder().build()

        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
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
            .crossfade(true)
            .build()
    }
}
