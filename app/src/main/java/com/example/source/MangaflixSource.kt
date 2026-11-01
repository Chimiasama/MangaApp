package com.example.source

import com.squareup.moshi.Moshi
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder

/**
 * Mangaflix source integration (https://mangaflix.net).
 *
 * Features:
 * - Popular/browse manga from https://mangaflix.net/br/browse (and latest-releases fallback)
 * - Fast, structured JSON search via https://api.mangaflix.net/v1/search/mangas
 * - Manga details and complete chapter feed via https://api.mangaflix.net/v1/mangas/{id}
 * - Page extraction via https://api.mangaflix.net/v1/chapters/{id}
 * - Direct image delivery via https://static.mangaflix.net
 * - Strictly non-adult (isNsfw = false)
 */
class MangaflixSource(
    private val fetch: suspend (String) -> String = SourceHttp::get
) : MangaSource {

    override val id: String = "mangaflix"
    override val name: String = "Mangaflix"
    override val baseUrl: String = "https://mangaflix.net"
    val apiUrl: String = "https://api.mangaflix.net"

    override val supportedLanguages: Set<String> = setOf("pt-br")
    override val lang: String get() = "pt-br"
    override val isNsfw: Boolean = false
    override val canAggregateChapters: Boolean = false
    override val filterSupportNote: String = "Mangaflix fornece mangás e manhwas em português (pt-BR)."

    private val jsonAdapter = Moshi.Builder().build().adapter(Map::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun parseJson(text: String): Map<String, Any?>? = try {
        jsonAdapter.fromJson(text) as? Map<String, Any?>
    } catch (_: Exception) {
        null
    }

    private suspend fun fetchDocument(url: String): Document {
        val html = fetch(url)
        val doc = Jsoup.parse(html, url)
        val title = doc.title().lowercase()
        if (title.contains("just a moment") || title.contains("access denied")) {
            throw IOException("$name está solicitando verificação de acesso (WAF). Tente novamente mais tarde.")
        }
        return doc
    }

    override suspend fun getPopularManga(): List<MangaModel> = getPopularManga(lang)

    override suspend fun getPopularManga(language: String): List<MangaModel> {
        if (!supportsLanguage(language)) return emptyList()

        val browseUrl = "$baseUrl/br/browse"
        return try {
            val doc = fetchDocument(browseUrl)
            val parsed = parseCatalogCards(doc)
            if (parsed.isNotEmpty()) parsed else parseFromLatestReleases()
        } catch (e: Exception) {
            if (e is IOException && e.message?.contains("WAF") == true) throw e
            parseFromLatestReleases()
        }
    }

    private suspend fun parseFromLatestReleases(): List<MangaModel> {
        val releasesUrl = "$baseUrl/br/latest-releases"
        val doc = fetchDocument(releasesUrl)
        return parseCatalogCards(doc)
    }

    private fun parseCatalogCards(doc: Document): List<MangaModel> {
        val cards = doc.select("a[href*=/br/manga/], a[href*=/manga/]")
        val result = mutableListOf<MangaModel>()

        for (card in cards) {
            val href = card.absUrl("href")
            val rawId = extractMangaId(href)
            if (rawId.isBlank()) continue

            val img = card.selectFirst("img")
            val coverUrl = img?.let {
                it.absUrl("data-src").ifBlank { it.absUrl("src") }
            }.orEmpty()

            val title = card.selectFirst("span.text-white, span.font-bold, .card-title, h3, h4")?.text()?.trim()
                ?: img?.attr("alt")?.removePrefix("Poster do Manga ")?.trim()
                ?: ""

            if (title.isBlank()) continue

            val synopsis = card.selectFirst("p.line-clamp-4, p.text-gray-100, p")?.text()?.trim().orEmpty()

            result.add(
                MangaModel(
                    url = "$baseUrl/br/manga/$rawId",
                    id = "$id:$rawId",
                    title = title,
                    coverUrl = coverUrl,
                    description = synopsis,
                    sourceId = id,
                    isNsfw = false
                )
            )
        }

        return result.distinctBy { it.url }
    }

    override suspend fun searchManga(query: String): List<MangaModel> = searchManga(query, lang)

    override suspend fun searchManga(query: String, language: String): List<MangaModel> {
        if (!supportsLanguage(language)) return emptyList()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return getPopularManga(language)

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val searchUrl = "$apiUrl/v1/search/mangas?query=$encoded&selected_language=pt-br"

        val responseText = try {
            fetch(searchUrl)
        } catch (e: Exception) {
            // If API fails, fall back to searching in popular titles
            return getPopularManga(language).filter { it.title.contains(trimmed, ignoreCase = true) }
        }

        val parsed = parseJson(responseText) ?: return emptyList()
        val dataObj = parsed["data"] as? Map<*, *> ?: return emptyList()
        val works = dataObj["works"] as? List<*> ?: return emptyList()

        val results = mutableListOf<MangaModel>()
        for (item in works) {
            val work = item as? Map<*, *> ?: continue
            val rawId = work["_id"]?.toString()?.trim().orEmpty()
            val name = work["name"]?.toString()?.trim().orEmpty()
            if (rawId.isBlank() || name.isBlank()) continue

            val desc = work["description"]?.toString()?.trim().orEmpty()
            val posterMap = work["poster"] as? Map<*, *>
            val poster = posterMap?.get("default_url")?.toString()?.trim().orEmpty()

            results.add(
                MangaModel(
                    url = "$baseUrl/br/manga/$rawId",
                    id = "$id:$rawId",
                    title = name,
                    coverUrl = poster,
                    description = desc,
                    sourceId = id,
                    isNsfw = false
                )
            )
        }
        return results.distinctBy { it.url }
    }

    override suspend fun getMangaDetails(mangaUrl: String): MangaModel {
        val rawId = extractMangaId(mangaUrl)
        if (rawId.isBlank()) {
            throw IOException("URL de mangá inválida para Mangaflix: $mangaUrl")
        }

        val detailApiUrl = "$apiUrl/v1/mangas/$rawId?selected_language=pt-br"
        try {
            val responseText = fetch(detailApiUrl)
            val parsed = parseJson(responseText)
                ?: throw IOException("Dados do mangá indisponíveis no Mangaflix")
            val data = parsed["data"] as? Map<*, *>
                ?: throw IOException("Dados do mangá indisponíveis no Mangaflix")

            val title = data["name"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IOException("Título do mangá indisponível no Mangaflix")

            val desc = data["description"]?.toString()?.trim().orEmpty()
            val posterMap = data["poster"] as? Map<*, *>
            val cover = posterMap?.get("default_url")?.toString()?.trim().orEmpty()

            val genres = mutableListOf<String>()
            val genresList = data["genres"] as? List<*>
            if (genresList != null) {
                for (g in genresList) {
                    val gName = when (g) {
                        is Map<*, *> -> g["name"]?.toString()?.trim()
                        is String -> g.trim()
                        else -> null
                    }
                    if (!gName.isNullOrBlank()) {
                        genres.add(gName)
                    }
                }
            }

            return MangaModel(
                url = "$baseUrl/br/manga/$rawId",
                id = "$id:$rawId",
                title = title,
                coverUrl = cover,
                description = desc,
                genres = genres,
                sourceId = id,
                isNsfw = false
            )
        } catch (e: Exception) {
            if (e is IOException && (e.message?.contains("WAF") == true || e.message?.contains("indisponível") == true)) throw e
            // Fallback to HTML parsing if API detail fails
            return getMangaDetailsFromHtml(mangaUrl, rawId)
        }
    }

    private suspend fun getMangaDetailsFromHtml(mangaUrl: String, rawId: String): MangaModel {
        val doc = fetchDocument(mangaUrl)
        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?.removePrefix("Ler ")?.removeSuffix(" online gratis")?.trim()
            ?: throw IOException("Não foi possível carregar os detalhes do mangá no Mangaflix")

        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val desc = doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
        val genres = doc.select("a[href*=/categories/]").map { it.text().trim() }.filter { it.isNotBlank() }

        return MangaModel(
            url = "$baseUrl/br/manga/$rawId",
            id = "$id:$rawId",
            title = title,
            coverUrl = cover,
            description = desc,
            genres = genres,
            sourceId = id,
            isNsfw = false
        )
    }

    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = getChapters(mangaUrl, lang)

    override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> {
        if (!supportsLanguage(language)) return emptyList()

        val rawId = extractMangaId(mangaUrl)
        if (rawId.isBlank()) return emptyList()

        val detailApiUrl = "$apiUrl/v1/mangas/$rawId?selected_language=pt-br"
        val responseText = fetch(detailApiUrl)
        val parsed = parseJson(responseText) ?: return emptyList()
        val data = parsed["data"] as? Map<*, *> ?: return emptyList()
        val chaptersList = data["chapters"] as? List<*> ?: return emptyList()

        val list = mutableListOf<ChapterModel>()
        for (item in chaptersList) {
            val ch = item as? Map<*, *> ?: continue
            val chId = ch["_id"]?.toString()?.trim().orEmpty()
            if (chId.isBlank()) continue

            val numberStr = ch["number"]?.toString()?.trim() ?: "1"
            val chapterNumber = numberStr.toFloatOrNull() ?: 1f

            val ownersList = ch["owners"] as? List<*>
            val firstOwner = ownersList?.firstOrNull() as? Map<*, *>
            val scan = firstOwner?.get("name")?.toString()?.trim()

            val title = if (!scan.isNullOrBlank()) "Capítulo $numberStr - $scan" else "Capítulo $numberStr"
            val date = (ch["date"] ?: ch["iso_date"])?.toString()?.trim().orEmpty()
            val chapterUrl = "$apiUrl/v1/chapters/$chId?selected_language=pt-br"

            list.add(
                ChapterModel(
                    id = "mangaflix_$chId",
                    title = title,
                    chapterUrl = chapterUrl,
                    chapterNumber = chapterNumber,
                    dateUpload = date,
                    language = "pt-br"
                )
            )
        }

        return list.sortedByDescending { it.chapterNumber }
    }

    override suspend fun getPages(chapterUrl: String): List<String> {
        val chapterId = extractChapterId(chapterUrl)
        if (chapterId.isBlank()) {
            throw IOException("Identificador de capítulo inválido no Mangaflix")
        }

        val chapterApiUrl = "$apiUrl/v1/chapters/$chapterId?selected_language=pt-br"
        val responseText = fetch(chapterApiUrl)
        val parsed = parseJson(responseText)
            ?: throw IOException("Dados do capítulo não encontrados no Mangaflix")
        val data = parsed["data"] as? Map<*, *>
            ?: throw IOException("Dados do capítulo não encontrados no Mangaflix")

        val imagesList = data["images"] as? List<*>
            ?: throw IOException("Nenhuma página encontrada para o capítulo no Mangaflix")

        data class PageItem(val url: String, val order: Int)
        val pages = mutableListOf<PageItem>()

        for ((index, item) in imagesList.withIndex()) {
            val imgMap = item as? Map<*, *> ?: continue
            val url = imgMap["default_url"]?.toString()?.trim().orEmpty()
            val order = (imgMap["order"] as? Number)?.toInt() ?: index
            if (url.isNotBlank()) {
                pages.add(PageItem(url, order))
            }
        }

        if (pages.isEmpty()) {
            throw IOException("Nenhuma página disponível neste capítulo do Mangaflix")
        }

        return pages.sortedBy { it.order }.map { it.url }
    }

    private fun extractMangaId(url: String): String {
        return when {
            url.contains("/manga/") -> url.substringAfterLast("/manga/").substringBefore("?").substringBefore("/").trim()
            url.contains(":") -> url.substringAfterLast(":").trim()
            else -> url.substringAfterLast("/").substringBefore("?").trim()
        }
    }

    private fun extractChapterId(url: String): String {
        return when {
            url.contains("/v1/chapters/") -> url.substringAfter("/v1/chapters/").substringBefore("?").substringBefore("/").trim()
            url.contains("/chapters/") -> url.substringAfter("/chapters/").substringBefore("?").substringBefore("/").trim()
            url.contains("/chapter/") -> url.substringAfterLast("/chapter/").substringBefore("?").substringBefore("/").trim()
            url.startsWith("mangaflix_") -> url.removePrefix("mangaflix_").trim()
            else -> url.substringAfterLast("/").substringBefore("?").trim()
        }
    }
}
