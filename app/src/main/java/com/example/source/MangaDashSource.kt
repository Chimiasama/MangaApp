package com.example.source

import com.example.util.PdfRendererHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MangaDashSource(
    private val fetch: suspend (String) -> String = SourceHttp::get,
    private val cacheDir: File? = null
) : MangaSource {

    override val id: String = "mangadash"
    override val name: String = "MangaDash"
    override val baseUrl: String = "https://mangadash.org"
    override val lang: String = "pt-BR"
    override val isNsfw: Boolean = false
    override val canAggregateChapters: Boolean = false

    override fun supportsLanguage(language: String): Boolean = ChapterLanguage.matches(lang, language)

    override suspend fun getPopularManga(): List<MangaModel> = getPopularManga(lang)

    override suspend fun getPopularManga(language: String): List<MangaModel> {
        if (!supportsLanguage(language)) return emptyList()
        return searchManga("", language)
    }

    override suspend fun searchManga(query: String): List<MangaModel> = searchManga(query, lang)

    override suspend fun searchManga(query: String, language: String): List<MangaModel> = withContext(Dispatchers.IO) {
        if (!supportsLanguage(language)) return@withContext emptyList()
        val url = if (query.isBlank()) {
            "$baseUrl/mangas"
        } else {
            "$baseUrl/mangas".toHttpUrl().newBuilder().addQueryParameter("q", query).build().toString()
        }

        try {
            val html = fetch(url)
            val doc = Jsoup.parse(html, url)
            doc.select("article.manga-card, div.manga-item, a[href*=/manga/]").mapNotNull { card ->
                val href = card.absUrl("href").ifBlank { card.selectFirst("a")?.absUrl("href").orEmpty() }
                val title = card.selectFirst(".title, h3, h4, img")?.let { el ->
                    if (el.tagName() == "img") el.attr("alt") else el.text()
                }?.trim().orEmpty()
                val cover = card.selectFirst("img")?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }.orEmpty()

                if (href.isBlank() || title.isBlank()) null
                else MangaModel(
                    url = href,
                    id = "$id:${href.toHttpUrl().encodedPath}",
                    title = title,
                    coverUrl = cover,
                    sourceId = id,
                    isNsfw = false
                )
            }.distinctBy { it.url }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMangaDetails(mangaUrl: String): MangaModel = withContext(Dispatchers.IO) {
        val html = fetch(mangaUrl)
        val doc = Jsoup.parse(html, mangaUrl)
        val title = doc.selectFirst("h1, .manga-title")?.text()?.trim() ?: "MangaDash Comic"
        val cover = doc.selectFirst(".cover img, img.manga-cover")?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }.orEmpty()
        val description = doc.selectFirst(".description, .synopsis, #sinopsis")?.text()?.trim().orEmpty()
        val genres = doc.select(".genre, .tag, a[href*=genre]").map { it.text().trim() }

        MangaModel(
            url = mangaUrl,
            id = "$id:${mangaUrl.toHttpUrl().encodedPath}",
            title = title,
            coverUrl = cover,
            description = description,
            genres = genres,
            sourceId = id,
            isNsfw = false
        )
    }

    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, lang)

    override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        if (!supportsLanguage(language)) return@withContext emptyList()
        val html = fetch(mangaUrl)
        val doc = Jsoup.parse(html, mangaUrl)
        val links = doc.select("a[href*=/capitulo/], a[href*=/chapter/], a.chapter-link").distinctBy { it.absUrl("href") }

        links.mapIndexed { index, a ->
            val href = a.absUrl("href")
            val title = a.text().trim().ifBlank { "Capítulo ${links.size - index}" }
            val chapterNum = title.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: (links.size - index).toFloat()

            ChapterModel(
                id = "$id:${href.toHttpUrl().encodedPath}",
                title = title,
                chapterUrl = href,
                chapterNumber = chapterNum,
                dateUpload = "",
                language = lang
            )
        }
    }

    override suspend fun getPages(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
        val html = fetch(chapterUrl)
        val doc = Jsoup.parse(html, chapterUrl)

        // Check if direct images exist
        val directImages = doc.select("img.page-img, .reader-images img").map { it.absUrl("src") }.filter { it.isNotBlank() }
        if (directImages.isNotEmpty()) {
            return@withContext directImages
        }

        // Search for PDF link (e.g. <a href="...file.pdf"> or iframe/embed src)
        val pdfUrl = doc.selectFirst("a[href$=.pdf], iframe[src$=.pdf], embed[src$=.pdf]")?.let {
            it.absUrl("href").ifBlank { it.absUrl("src") }
        } ?: throw IOException("MangaDash não disponibilizou imagens ou arquivo PDF para este capítulo.")

        if (cacheDir == null) {
            // If running in environment without File cache dir available, return PDF URL directly
            return@withContext listOf(pdfUrl)
        }

        // Download PDF to temporary cache and render pages via PdfRendererHelper
        val chapterHash = chapterUrl.hashCode().toString()
        val pdfFile = File(cacheDir, "mangadash_$chapterHash.pdf")
        val outputDir = File(cacheDir, "mangadash_pages_$chapterHash")

        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            val client = OkHttpClient()
            val request = Request.Builder().url(pdfUrl).header("User-Agent", SourceHttp.USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Falha ao baixar PDF do capítulo em MangaDash: ${response.code}")
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(pdfFile).use { output -> input.copyTo(output) }
                }
            }
        }

        val renderedPages = PdfRendererHelper.renderPdfPagesToImages(pdfFile, outputDir)
        if (renderedPages.isEmpty()) {
            listOf(pdfUrl)
        } else {
            renderedPages
        }
    }
}
