package com.example.util

import android.content.Context
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Gerenciador de Pré-carregamento Inteligente e Sequencial de Páginas (Pre-fetching)
 * - Baixa as páginas uma a uma em ordem estritamente sequencial (página atual -> próximas -> anteriores)
 * - Garante que cada imagem conclua o download e armazenamento em cache antes de iniciar a próxima
 * - Impede a disputa de banda da rede entre páginas distantes, garantindo que o leitor veja o conteúdo em sequência sem interrupção
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
    ): Job? {
        if (pages.isEmpty()) return null

        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            val imageLoader = Coil.imageLoader(context)

            // Lista ordenada de URLs para pré-carregamento em sequência estrita
            val sequence = mutableListOf<String>()

            // 1. Página atual (prioridade máxima para exibição imediata)
            val currentUrl = pages.getOrNull(currentIndex)
            if (!currentUrl.isNullOrBlank()) {
                sequence.add(currentUrl)
            }

            // 2. Páginas seguintes em ordem estritamente crescente (currentIndex + 1 .. endForward)
            val endForward = minOf(pages.lastIndex, currentIndex + forwardCount)
            if (currentIndex + 1 <= endForward) {
                for (i in (currentIndex + 1)..endForward) {
                    val url = pages.getOrNull(i)
                    if (!url.isNullOrBlank() && !sequence.contains(url)) {
                        sequence.add(url)
                    }
                }
            }

            // 3. Página anterior (para retorno instantâneo)
            val startBackward = maxOf(0, currentIndex - backwardCount)
            if (startBackward < currentIndex) {
                for (i in startBackward until currentIndex) {
                    val url = pages.getOrNull(i)
                    if (!url.isNullOrBlank() && !sequence.contains(url)) {
                        sequence.add(url)
                    }
                }
            }

            // 4. Primeiras páginas do próximo capítulo (se próximo ao final)
            if (currentIndex >= pages.size - 2 && nextChapterFirstPages.isNotEmpty()) {
                for (nextUrl in nextChapterFirstPages.take(2)) {
                    if (!nextUrl.isNullOrBlank() && !sequence.contains(nextUrl)) {
                        sequence.add(nextUrl)
                    }
                }
            }

            // Execução estritamente sequencial com suspend execute(request)
            for (url in sequence) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .memoryCacheKey(url)
                        .diskCacheKey(url)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build()
                    // execute() suspende até a imagem terminar o download no cache
                    imageLoader.execute(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Ignora erros pontuais de rede em uma página para prosseguir na sequência
                }
            }
        }
        return prefetchJob
    }
}
