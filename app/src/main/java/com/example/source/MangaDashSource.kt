package com.example.source

import com.example.util.PdfRendererHelper
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Verified against the public .net catalog, chapter rows and chapterViewerData payload. */
class MangaDashSource(
    private val fetch: suspend (String) -> String = SourceHttp::get,
    private val cacheDir: File? = null,
    private val downloadPdf: suspend (String, String, File) -> Unit = ::downloadMangaDashPdf,
    private val renderPdf: suspend (File, File) -> List<String> = PdfRendererHelper::renderPdfPagesToImages
) : MangaSource {
    override val id = "mangadash"
    override val name = "MangaDash"
    override val baseUrl = "https://mangadash.net"
    override val supportedLanguages = setOf("pt-br")
    override val lang get() = supportedLanguages.first()
    override val isNsfw = false
    override val canAggregateChapters = false
    override val filterSupportNote = "MangaDash fornece classificação nos detalhes. Público, origem e formato podem não estar disponíveis."
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val viewerAdapter = Moshi.Builder().build().adapter(Map::class.java)

    override suspend fun getPopularManga() = getPopularManga(lang)
    override suspend fun getPopularManga(language: String) = searchManga("", language)
    override suspend fun searchManga(query: String) = searchManga(query, lang)

    private fun catalogUrl(query: String, year: Int? = null, genre: String? = null): String = "$baseUrl/mangas".toHttpUrl().newBuilder().apply {
        if (query.isNotBlank()) addQueryParameter("q", query.trim())
        if (year != null) addQueryParameter("ano", year.toString())
        if (!genre.isNullOrBlank()) {
            addQueryParameter("genero", genre.trim().lowercase())
        }
    }.build().toString()

    private fun parseCatalog(doc: Document): List<MangaModel> =
        doc.select(".card-new, article.manga-card, div.manga-item").mapNotNull { card ->
            val link = card.selectFirst("a[href*=/manga/]") ?: return@mapNotNull null
            val url = link.absUrl("href")
            val image = card.selectFirst("img")
            val title = card.selectFirst(".card-title, .title, h3, h4")?.text()?.trim()
                ?: image?.attr("title")?.takeIf { it.isNotBlank() }
                ?: image?.attr("alt")?.removePrefix("Capa do mangá ")?.substringBefore(" - Acompanhe online")
            if (url.isBlank() || title.isNullOrBlank()) return@mapNotNull null
            val cardGenres = card.select(".badge, .tag, .genre, .categoria, .genres a").map { it.text().trim() }.filter { it.isNotBlank() }
            MangaModel(url = url, id = "$id:${url.toHttpUrl().encodedPath}", title = title,
                coverUrl = image?.absUrl("data-src")?.ifBlank { image.absUrl("src") }.orEmpty(), sourceId = id,
                genres = cardGenres,
                isNsfw = card.hasClass("is-plus18") || card.selectFirst(".badge-18, .adult-badge") != null,
                year = card.selectFirst(".netflix-meta-custom .fa-calendar")?.parent()?.text()?.trim()?.toIntOrNull())
        }.distinctBy { it.url }

    override suspend fun searchManga(query: String, language: String): List<MangaModel> = withContext(Dispatchers.IO) {
        if (!supportsLanguage(language)) return@withContext emptyList()
        val url = catalogUrl(query)
        val doc = Jsoup.parse(fetch(url), url)
        val results = parseCatalog(doc).toMutableList()
        val nextLinks = doc.select(".pagination a[href], a.page-link[href], .page-item a[href]")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() && it != url && (it.contains("page=") || it.contains("pagina=")) }
            .distinct()
        for (nextUrl in nextLinks.take(2)) {
            coroutineContext.ensureActive()
            try {
                val nextDoc = Jsoup.parse(fetch(nextUrl), nextUrl)
                results.addAll(parseCatalog(nextDoc))
            } catch (_: Exception) {}
        }
        val distinctResults = results.distinctBy { it.url }
        if (distinctResults.isEmpty() && (query.isBlank() || doc.selectFirst("#mangasGrid") == null)) throw IOException("MangaDash não retornou seu catálogo. Tente novamente mais tarde.")
        distinctResults
    }

    override suspend fun getCatalogManga(query: String, language: String, yearRange: String): List<MangaModel> = withContext(Dispatchers.IO) {
        if (!supportsLanguage(language)) return@withContext emptyList()
        val bounds = yearRange.split("-").mapNotNull { it.toIntOrNull() }
        if (bounds.size != 2) return@withContext searchManga(query, language)
        val url = catalogUrl(query)
        val doc = Jsoup.parse(fetch(url), url)
        val years = doc.select("#filterAno option").mapNotNull { it.attr("value").toIntOrNull() }
            .filter { it in bounds[0]..bounds[1] }.distinct()
        years.flatMap { year ->
            coroutineContext.ensureActive()
            val filteredUrl = catalogUrl(query, year)
            parseCatalog(Jsoup.parse(fetch(filteredUrl), filteredUrl))
        }.distinctBy { it.url }
    }

    override suspend fun getFilteredCatalog(query: String, language: String, yearRange: String, filters: CatalogFilters): List<MangaModel> = withContext(Dispatchers.IO) {
        if (!supportsLanguage(language)) return@withContext emptyList()
        val genreFilter = filters.included.firstOrNull { it.startsWith("genre:") }
        val genreSlug = genreFilter?.substringAfter("genre:")?.takeIf { it.isNotBlank() }

        val bounds = yearRange.split("-").mapNotNull { it.toIntOrNull() }
        val candidates = if (bounds.size == 2) {
            val url = catalogUrl(query, genre = genreSlug)
            val doc = Jsoup.parse(fetch(url), url)
            val years = doc.select("#filterAno option").mapNotNull { it.attr("value").toIntOrNull() }
                .filter { it in bounds[0]..bounds[1] }.distinct()
            years.flatMap { year ->
                coroutineContext.ensureActive()
                val filteredUrl = catalogUrl(query, year, genreSlug)
                parseCatalog(Jsoup.parse(fetch(filteredUrl), filteredUrl))
            }.distinctBy { it.url }
        } else {
            val url = catalogUrl(query, genre = genreSlug)
            val doc = Jsoup.parse(fetch(url), url)
            val all = parseCatalog(doc).toMutableList()
            val nextLinks = doc.select(".pagination a[href], a.page-link[href], .page-item a[href]")
                .map { it.absUrl("href") }
                .filter { it.isNotBlank() && it != url && (it.contains("page=") || it.contains("pagina=")) }
                .distinct()
            for (nextUrl in nextLinks.take(2)) {
                coroutineContext.ensureActive()
                try {
                    val nextDoc = Jsoup.parse(fetch(nextUrl), nextUrl)
                    all.addAll(parseCatalog(nextDoc))
                } catch (_: Exception) {}
            }
            all.distinctBy { it.url }
        }
        if (!filters.isActive) return@withContext candidates
        candidates.map { manga ->
            if (manga.genres.isNotEmpty()) manga else {
                try {
                    val details = getMangaDetails(manga.url)
                    details.copy(id = manga.id, url = manga.url, sourceId = manga.sourceId,
                        year = details.year ?: manga.year, coverUrl = details.coverUrl.ifBlank { manga.coverUrl },
                        genres = details.genres.ifEmpty { manga.genres })
                } catch (_: Exception) { manga }
            }
        }
    }

    private fun currentUrl(url: String): String {
        val parsed = url.toHttpUrl()
        return if (parsed.host == "mangadash.org" || parsed.host == "www.mangadash.org")
            parsed.newBuilder().scheme("https").host("mangadash.net").build().toString() else url
    }

    override suspend fun getMangaDetails(mangaUrl: String): MangaModel = withContext(Dispatchers.IO) {
        val url = currentUrl(mangaUrl)
        val doc = Jsoup.parse(fetch(url), url)
        val title = doc.selectFirst("h1, .manga-title")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IOException("MangaDash não disponibilizou os detalhes desta obra.")
        MangaModel(url = url, id = "$id:${url.toHttpUrl().encodedPath}", title = title, sourceId = id,
            coverUrl = doc.selectFirst(".cover-3d img, .cover img, img.manga-cover")?.absUrl("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty(),
            description = doc.selectFirst(".manga-description, .description, .synopsis, #sinopsis")?.text().orEmpty(),
            genres = doc.select(".manga-tags a[href*='categoria='], a[href*='genre=']").map { it.text().trim() },
            author = doc.selectFirst(".tag-author")?.text().orEmpty(),
            status = doc.selectFirst(".stat-item[title=Status] span")?.text() ?: "Unknown",
            year = doc.selectFirst(".stat-item[title='Ano de Lançamento'] span")?.text()?.toIntOrNull(),
            isNsfw = doc.selectFirst(".cover-3d.is-plus18") != null)
    }

    override suspend fun getChapters(mangaUrl: String) = getChapters(mangaUrl, lang)
    override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        if (!supportsLanguage(language)) return@withContext emptyList()
        val url = currentUrl(mangaUrl)
        val doc = Jsoup.parse(fetch(url), url)
        val rows = doc.select("a.chapter-row, a.chapter-link").distinctBy { it.absUrl("href") }
        rows.mapNotNull { row ->
            val href = row.absUrl("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = row.selectFirst(".chapter-title-group h4")?.text() ?: row.text()
            val number = row.attr("data-chapter-number").toFloatOrNull()
                ?: Regex("""(?i)cap[ií]tulo\s*(\d+(?:[.,]\d+)?)""").find(title)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()
                ?: return@mapNotNull null
            ChapterModel(id = "$id:${href.toHttpUrl().encodedPath}", title = title, chapterUrl = href,
                chapterNumber = number, language = lang,
                dateUpload = row.selectFirst(".chapter-meta-info .fa-calendar")?.parent()?.text().orEmpty())
        }.sortedByDescending { it.chapterNumber }
    }

    override suspend fun getPages(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
        val url = currentUrl(chapterUrl)
        val doc = Jsoup.parse(fetch(url), url)
        val direct = doc.select("img.page-img, .reader-images img").map {
            it.absUrl("data-src").ifBlank { it.absUrl("src") }
        }.filter { it.isNotBlank() }
        if (direct.isNotEmpty()) return@withContext direct
        val viewerJson = doc.selectFirst("script#chapterViewerData")?.data()
        val payload = viewerJson?.let { viewerAdapter.fromJson(it) }
        val pdfUrl = (payload?.get("pdfUrl") as? String)?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("a[href$=.pdf], iframe[src$=.pdf], embed[src$=.pdf]")?.let {
                it.absUrl("href").ifBlank { it.absUrl("src") }
            }?.takeIf { it.isNotBlank() }
            ?: throw IOException("MangaDash não disponibilizou páginas públicas ou PDF para este capítulo.")
        val cache = cacheDir ?: throw IOException("O cache local é necessário para abrir o PDF deste capítulo.")
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        locks.getOrPut(key) { Mutex() }.withLock {
            val pdfFile = File(cache, "mangadash_$key.pdf")
            val pageDir = File(cache, "mangadash_pages_$key")
            if (!pdfFile.exists() || pdfFile.length() == 0L) downloadPdf(pdfUrl, url, pdfFile)
            try {
                renderPdf(pdfFile, pageDir).also {
                    if (it.isEmpty()) throw IOException("Não foi possível converter o PDF do MangaDash em páginas.")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { pdfFile.delete(); throw error }
        }
    }
}

private suspend fun downloadMangaDashPdf(url: String, referer: String, target: File): Unit = suspendCancellableCoroutine { continuation ->
    val call = SourceHttp.client.newCall(Request.Builder().url(url).header("Referer", referer)
        .header("Accept", "application/pdf").build())
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            val partial = File(target.parentFile, target.name + ".part")
            try {
                target.parentFile?.mkdirs()
                response.use {
                    if (!it.isSuccessful) throw IOException("MangaDash: falha ao baixar PDF (HTTP ${it.code}).")
                    val body = it.body ?: throw IOException("MangaDash retornou um PDF vazio.")
                    body.byteStream().buffered().use { input ->
                        input.mark(5)
                        val signature = ByteArray(5)
                        java.io.DataInputStream(input).readFully(signature)
                        if (String(signature, Charsets.US_ASCII) != "%PDF-")
                            throw IOException("MangaDash retornou um conteúdo que não é PDF.")
                        input.reset()
                        partial.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                if (!continuation.isActive) return
                if (!partial.renameTo(target)) throw IOException("Não foi possível salvar o PDF no cache.")
                continuation.resume(Unit)
            } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            finally { partial.delete() }
        }
    })
}
