package com.example.util

import android.content.Context
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Gerenciador de Pré-carregamento Inteligente de Páginas (Pre-fetching)
 * - Carrega assincronamente as próximas páginas e a página anterior no cache do Coil
 * - Elimina esperas e telas de carregamento durante a leitura fluida
 * - Pré-carrega o início do próximo capítulo quando o leitor se aproxima do final
 */
object PagePreloaderManager {
    private var prefetchJob: Job? = null

    fun preloadPages(
        context: Context,
        scope: CoroutineScope,
        pages: List<String>,
        currentIndex: Int,
        forwardCount: Int = 3,
        backwardCount: Int = 1,
        nextChapterFirstPages: List<String> = emptyList()
    ) {
        if (pages.isEmpty()) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            val imageLoader = Coil.imageLoader(context)

            // 1. Pré-carregar páginas seguintes (alta prioridade para leitura contínua)
            val endForward = minOf(pages.lastIndex, currentIndex + forwardCount)
            if (currentIndex + 1 <= endForward) {
                for (i in (currentIndex + 1)..endForward) {
                    val url = pages.getOrNull(i) ?: continue
                    if (url.isNotBlank()) {
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            }

            // 2. Pré-carregar página anterior (para retorno instantâneo)
            val startBackward = maxOf(0, currentIndex - backwardCount)
            if (startBackward < currentIndex) {
                for (i in startBackward until currentIndex) {
                    val url = pages.getOrNull(i) ?: continue
                    if (url.isNotBlank()) {
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            }

            // 3. Pré-carregar capa e primeiras páginas do próximo capítulo quando próximo ao final
            if (currentIndex >= pages.size - 2 && nextChapterFirstPages.isNotEmpty()) {
                for (nextUrl in nextChapterFirstPages.take(2)) {
                    if (nextUrl.isNotBlank()) {
                        val request = ImageRequest.Builder(context)
                            .data(nextUrl)
                            .memoryCacheKey(nextUrl)
                            .diskCacheKey(nextUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            }
        }
    }
}
