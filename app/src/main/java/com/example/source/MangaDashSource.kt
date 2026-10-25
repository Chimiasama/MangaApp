package com.example.source

import com.example.data.network.RetrofitClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.io.IOException

class MangaDashSource(private val pdfPages: suspend (String) -> List<String>,
    private val fetch: suspend (String) -> String = SourceHttp::get) : MangaSource {
    override val id = "mangadash"
    override val name = "MangaDash"
    override val baseUrl = "https://mangadash.net"
    override val lang = "pt-br"
    override val isNsfw = false
    override val canAggregateChapters = false
    override fun supportsLanguage(language: String) = ChapterLanguage.matches(lang, language)
    private fun json(text: String): Map<*, *> = RetrofitClient.moshi.adapter(Map::class.java).fromJson(text)
        ?: throw IOException("Resposta inválida do MangaDash.")
    private fun absolute(url: String) = baseUrl.toHttpUrl().resolve(url)?.toString().orEmpty()
    override suspend fun getPopularManga() = getPopularManga(lang)
    override suspend fun getPopularManga(language: String) = searchManga("", language)
    override suspend fun searchManga(query: String) = searchManga(query, lang)
    override suspend fun searchManga(query: String, language: String): List<MangaModel> {
        if (!supportsLanguage(language)) return emptyList()
        val url = "$baseUrl/api/mangas/list".toHttpUrl().newBuilder().addQueryParameter("page", "1")
            .addQueryParameter("q", query).addQueryParameter("sort", "populares").build().toString()
        val response = json(fetch(url))
        val items = response["items"] as? List<*> ?: throw IOException("MangaDash não entregou o catálogo.")
        return items.mapNotNull { item ->
            val data = item as? Map<*, *> ?: return@mapNotNull null
            if (data["isPlus18"] == true) return@mapNotNull null
            val key = (data["id"] as? Number)?.toInt() ?: return@mapNotNull null
            val slug = data["slug"] as? String ?: return@mapNotNull null
            val title = data["nome"] as? String ?: return@mapNotNull null
            val cover = data["capa"] as? String ?: ""
            MangaModel("$baseUrl/manga/$key-$slug", "$id:$key", title,
                if (cover.startsWith("http")) cover else absolute(if (cover.startsWith("/")) cover else "/static/covers/$cover"), sourceId = id)
        }
    }
    override suspend fun getMangaDetails(mangaUrl: String): MangaModel {
        val doc = Jsoup.parse(fetch(mangaUrl), mangaUrl)
        val title = doc.selectFirst(".neon-title")?.text()?.takeIf { it.isNotBlank() }
            ?: throw IOException("Não foi possível carregar esta obra no MangaDash.")
        return MangaModel(mangaUrl, "$id:${mangaUrl.toHttpUrl().pathSegments.last().substringBefore('-')}", title,
            doc.selectFirst(".cover-3d img")?.absUrl("src").orEmpty(),
            description = doc.selectFirst(".manga-description")?.text().orEmpty(),
            genres = doc.select(".manga-tags a.tag:not(.tag-author)").map { it.text() },
            sourceId = id, author = doc.selectFirst(".tag-author")?.text().orEmpty())
    }
    override suspend fun getChapters(mangaUrl: String) = getChapters(mangaUrl, lang)
    override suspend fun getChapters(mangaUrl: String, language: String): List<ChapterModel> {
        if (!supportsLanguage(language)) return emptyList()
        val doc = Jsoup.parse(fetch(mangaUrl), mangaUrl)
        return doc.select(".chapters-scroll-container .chapter-row").mapNotNull { element ->
            val url = element.absUrl("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = element.selectFirst(".chapter-title-group h4")?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val number = Regex("(?i)cap[íi]tulo\\s*(\\d+(?:[.,]\\d+)?)").find(title)?.groupValues?.get(1)
                ?.replace(',', '.')?.toFloatOrNull() ?: return@mapNotNull null
            ChapterModel("$id:${url.toHttpUrl().encodedPath}", title, url, number,
                element.selectFirst(".chapter-meta-info:has(.fa-calendar)")?.text().orEmpty(), lang)
        }.distinctBy { it.id }.sortedByDescending { it.chapterNumber }
    }
    override suspend fun getPages(chapterUrl: String): List<String> {
        val doc = Jsoup.parse(fetch(chapterUrl), chapterUrl)
        val script = doc.selectFirst("script#chapterViewerData")?.data()
            ?: throw IOException("MangaDash não entregou os dados do capítulo. Pode ser necessário acessar a fonte.")
        val data = json(script)
        val images = (data["images"] as? List<*>)?.filterIsInstance<String>()?.map { absolute(it) }?.filter { it.isNotBlank() }.orEmpty()
        if (images.isNotEmpty()) return images
        val pdf = (data["pdfUrl"] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IOException("Este capítulo não contém imagens nem PDF acessível.")
        return pdfPages(absolute(pdf))
    }
}
