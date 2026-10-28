package com.example.source

import com.example.data.network.model.MangaDexMangaListResponse
import com.example.data.mapping.toMangaModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Retry transient GET failures only; cancellation and long rate-limit waits are never swallowed. */
internal suspend fun mangaDexCatalogRequest(fetch: suspend () -> MangaDexMangaListResponse): MangaDexMangaListResponse {
    for (attempt in 0..2) {
        coroutineContext.ensureActive()
        try { return fetch() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: HttpException) {
            val retryable = error.code() == 408 || error.code() == 429 || error.code() >= 500
            val wait = error.response()?.headers()?.get("Retry-After")?.toLongOrNull()
            if (!retryable || attempt == 2 || (wait != null && wait > 5)) throw error
            delay(wait?.coerceAtLeast(1)?.times(1000) ?: (600L * (attempt + 1)))
        } catch (error: IOException) {
            if (attempt == 2) throw error
            delay(600L * (attempt + 1))
        }
    }
    error("Catalog retry exhausted")
}

/** The historic range is a small chronological prefix, not 51 separate year searches. */
internal suspend fun historicMangaDexCatalog(start: Int, end: Int,
    fetch: suspend (Int) -> MangaDexMangaListResponse): List<MangaModel> {
    val result = mutableListOf<MangaModel>()
    var offset = 0
    try {
        while (true) {
            coroutineContext.ensureActive()
            val response = mangaDexCatalogRequest { fetch(offset) }
            val page = response.data.orEmpty()
            result += page.filter { it.attributes?.year?.let { year -> year in start..end } == true }
                .map { it.toMangaModel("https://api.mangadex.org", "mangadex") }
            offset += page.size
            val pastRange = page.any { (it.attributes?.year ?: Int.MIN_VALUE) > end }
            val hasMore = page.size == 100 && offset < (response.total ?: 0).coerceAtMost(10_000)
            if (pastRange || !hasMore) break
            delay(350)
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) {
        if (result.isEmpty()) throw error
        throw SourceCatalogPartialException(result.distinctBy { it.url }, error)
    }
    return result.distinctBy { it.url }
}
