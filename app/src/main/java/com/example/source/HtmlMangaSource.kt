package com.example.source

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

/** Site-specific selectors; never invent chapters or fall back to a different work. */
class HtmlMangaSource(
    val site: Site,
    private val fetch: suspend (String) -> String = SourceHttp::get
) : MangaSource {
    enum class Site(val key: String, val label: String, val origin: String, val language: String) {
        WEEBCENTRAL("weebcentral", "WeebCentral", "https://weebcentral.com", "en"),
        LEERCAPITULO("leercapitulo", "LeerCapítulo", "https://www.leercapitulo.co", "es")
    }
    override val id = site.key
    override val name = site.label
    override val baseUrl = site.origin
    override val lang = site.language
    override val isNsfw = false
    override val canAggregateChapters = false
    override fun supportsLanguage(language: String) = ChapterLanguage.matches(lang, language)

    private suspend fun document(url: String): Document {
        val doc = Jsoup.parse(fetch(url), url)
        val title = doc.title().lowercase()
        if (title.contains("just a moment") || title.contains("access denied"))
            throw IOException("$name está solicitando verificação de acesso. Tente novamente mais tarde.")
        return doc
    }
    private fun image(element: Element?): String = element?.selectFirst("img")?.let {
        it.absUrl("data-src").ifBlank { it.absUrl("src") }
    }.orEmpty()
    private fun model(url: String, title: String, cover: String, genres: List<String> = emptyList()) = MangaModel(
        url, "$id:${url.toHttpUrl().encodedPath}", title, cover, genres = genres, sourceId = id,
        isNsfw = genres.any { it.lowercase() in setOf("hentai", "erotica", "pornographic", "adult") })

    override suspend fun getPopularManga() = getPopularManga(lang)
    override suspend fun getPopularManga(language: String): List<MangaModel> =
        if (!ChapterLanguage.matches(lang, language)) emptyList() else searchManga("", language)
    override suspend fun searchManga(query: String) = searchManga(query, lang)
    override suspend fun searchManga(query: String, language: String): List<MangaModel> = searchManga(query, language, "all")
    override suspend fun searchManga(query: String, language: String, category: String): List<MangaModel> {
        if (!ChapterLanguage.matches(lang, language)) return emptyList()
        val categoryValue = if (MangaCategories.isAll(category)) null else {
            val filters = document(if (site == Site.WEEBCENTRAL) "$baseUrl/search" else "$baseUrl/manga/")
            when (site) {
                Site.WEEBCENTRAL -> filters.select("input[id$=-value]").firstOrNull {
                    MangaCategories.key(it.attr("value")) == MangaCategories.key(category)
                }?.attr("value")
                Site.LEERCAPITULO -> filters.select("select[name=genre] option").firstOrNull {
                    MangaCategories.key(it.text()) == MangaCategories.key(category)
                }?.attr("value")?.let { baseUrl.toHttpUrl().resolve(it)?.queryParameter("genre") }
            } ?: return emptyList()
        }
        val url = when (site) {
            Site.WEEBCENTRAL -> "$baseUrl/search/data".toHttpUrl().newBuilder()
                .addQueryParameter("text", query).addQueryParameter("limit", "50")
                .addQueryParameter("offset", "0").addQueryParameter("display_mode", "Full Display")
                .apply { if (categoryValue != null) addQueryParameter("included_tag", categoryValue) }
                .addQueryParameter("sort", "Popularity").addQueryParameter("order", "Descending").build().toString()
            Site.LEERCAPITULO -> "$baseUrl/manga/".toHttpUrl().newBuilder().addQueryParameter("q", query)
                .apply { if (categoryValue != null) addQueryParameter("genre", categoryValue) }.build().toString()
        }
        val doc = document(url)
        return when (site) {
            Site.WEEBCENTRAL -> doc.select("article > section > a[href*=/series/]").mapNotNull { a ->
                val title = a.selectFirst("img")?.attr("alt")?.removeSuffix(" cover")?.trim().orEmpty()
                val genres = a.closest("article")?.select("div:has(> strong:contains(Tag)) > span")
                    ?.map { it.text().trim().trimEnd(',') }.orEmpty()
                if (title.isEmpty()) null else model(a.absUrl("href"), title, image(a), genres)
            }
            Site.LEERCAPITULO -> doc.select("article.lc-card").mapNotNull { card ->
                val a = card.selectFirst("a.lc-card-name") ?: return@mapNotNull null
                model(a.absUrl("href"), a.text(), image(card))
            }
        }.distinctBy { it.url }.map { manga ->
            if (categoryValue == null || manga.genres.isNotEmpty()) manga
            else manga.copy(genres = listOf(MangaCategories.key(category)))
        }
    }
    override suspend fun getMangaDetails(mangaUrl: String): MangaModel {
        val doc = document(mangaUrl)
        val title = doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: throw IOException("Não foi possível ler os detalhes em $name")
        val genres = when (site) {
            Site.WEEBCENTRAL -> doc.select("li:has(strong:contains(Tag)) a").map { it.text() }
            Site.LEERCAPITULO -> doc.select("a.badge[href*=genre], a.badge[href*=theme]").map { it.text() }
        }
        val cover = when (site) {
            Site.WEEBCENTRAL -> image(doc.selectFirst("section[x-data] > section"))
            Site.LEERCAPITULO -> image(doc.selectFirst(".lc-cover-lg"))
        }
        val description = when (site) {
            Site.WEEBCENTRAL -> doc.selectFirst("li:has(strong:contains(Description)) > p")?.text()
            Site.LEERCAPITULO -> doc.selectFirst("#sinopsis p:not(.lc-muted)")?.text()
        }.orEmpty()
        return model(mangaUrl, title, cover, genres).copy(description = description)
    }
    override suspend fun getChapters(mangaUrl: String) = getChapters(mangaUrl, lang)
    override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> {
        if (!ChapterLanguage.matches(lang, language)) return emptyList()
        val url = when (site) {
            Site.WEEBCENTRAL -> "$baseUrl/series/${mangaUrl.toHttpUrl().pathSegments[1]}/full-chapter-list"
            Site.LEERCAPITULO -> mangaUrl
        }
        val doc = document(url)
        val links = when (site) {
            Site.WEEBCENTRAL -> doc.select("div[x-data] > a[href*=/chapters/]")
            Site.LEERCAPITULO -> doc.select("a.lc-chapter-row")
        }.distinctBy { it.absUrl("href") }
        return links.mapIndexed { index, a ->
            val title = when (site) {
                Site.WEEBCENTRAL -> a.selectFirst("span.flex > span")?.text()
                Site.LEERCAPITULO -> a.selectFirst(".n")?.text()
            }?.takeIf { it.isNotBlank() } ?: throw IOException("Capítulo sem título em $name")
            val link = a.absUrl("href")
            // Source order preserves sagas/seasons whose displayed chapter numbers restart.
            ChapterModel("$id:${link.toHttpUrl().encodedPath}", title, link,
                (links.size - index).toFloat(),
                a.selectFirst("time")?.attr("datetime")?.take(10) ?: a.selectFirst(".d")?.text().orEmpty(), lang)
        }
    }
    override suspend fun getPages(chapterUrl: String): List<String> {
        val url = when (site) {
            Site.WEEBCENTRAL -> chapterUrl.trimEnd('/') + "/images?is_prev=False&reading_style=long_strip"
            Site.LEERCAPITULO -> chapterUrl
        }
        val doc = document(url)
        val images = when (site) {
            Site.WEEBCENTRAL -> doc.select("section[x-data*=scroll] > img").map { it.absUrl("src") }
            Site.LEERCAPITULO -> doc.select("#lcPages img[data-src]").map { it.absUrl("data-src") }
        }.filter { it.startsWith("https://") || it.startsWith("http://") }.distinct()
        if (images.isEmpty()) throw IOException("$name não entregou imagens deste capítulo. Tente novamente mais tarde.")
        return images
    }
}
