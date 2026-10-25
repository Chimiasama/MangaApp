package com.example.source

import com.example.data.network.RemoteMangaApiService
import com.example.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class DynamicJsoupSource(
    val rules: SourceRuleConfig,
    private val apiService: RemoteMangaApiService = RetrofitClient.createServiceForBaseUrl(rules.baseUrl)
) : MangaSource {

    override val id: String = rules.id
    override val name: String = rules.name
    override val baseUrl: String = rules.baseUrl
    override val isNsfw: Boolean = rules.isNsfw
    override val lang: String = rules.lang

    private suspend fun fetchDocument(url: String): Document = withContext(Dispatchers.IO) {
        try {
            apiService.getPageDocument(url)
        } catch (e: Exception) {
            Jsoup.parse("<html><body></body></html>", url)
        }
    }

    override suspend fun getPopularManga(): List<MangaModel> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(baseUrl)
        parseMangaList(doc)
    }

    override suspend fun searchManga(query: String): List<MangaModel> = withContext(Dispatchers.IO) {
        val searchUrl = if (baseUrl.endsWith("/")) "${baseUrl}search?q=$query" else "$baseUrl/search?q=$query"
        val doc = fetchDocument(searchUrl)
        val results = parseMangaList(doc)
        if (results.isEmpty()) {
            // Filter popular by query if search endpoint structure differs
            getPopularManga().filter { it.title.contains(query, ignoreCase = true) }
        } else {
            results
        }
    }

    private fun parseMangaList(doc: Document): List<MangaModel> {
        val elements = doc.select(rules.listSelector)
        val list = mutableListOf<MangaModel>()
        
        for (el in elements) {
            val titleEl = el.select(rules.titleSelector).first()
            val title = titleEl?.text() ?: continue
            
            val linkEl = el.select(rules.titleSelector).first() ?: el
            var link = linkEl.attr("href")
            if (link.startsWith("/")) link = baseUrl.removeSuffix("/") + link
            
            var cover = el.select(rules.coverAttrSelector).attr("src")
            if (cover.isEmpty()) cover = el.select(rules.coverAttrSelector).attr("data-src")
            if (cover.startsWith("/")) cover = baseUrl.removeSuffix("/") + cover
            
            val mangaId = link.hashCode().toString()
            
            list.add(
                MangaModel(
                    url = if (link.isNotEmpty()) link else "$baseUrl/manga/$mangaId",
                    id = mangaId,
                    title = title,
                    coverUrl = cover,
                    description = "Manga do catálogo $name",
                    isNsfw = rules.isNsfw,
                    sourceId = id
                )
            )
        }
        return list
    }

    override suspend fun getMangaDetails(mangaUrl: String): MangaModel = withContext(Dispatchers.IO) {
        val doc = fetchDocument(mangaUrl)
        
        val desc = if (rules.detailDescSelector.isNotEmpty()) {
            doc.select(rules.detailDescSelector).text()
        } else {
            "Sem descrição disponível."
        }
        
        val author = if (rules.detailAuthorSelector.isNotEmpty()) {
            doc.select(rules.detailAuthorSelector).text()
        } else {
            "Autor Desconhecido"
        }
        
        val title = doc.select(rules.titleSelector).first()?.text() ?: "Manga Detalhes"
        var cover = doc.select(rules.coverAttrSelector).first()?.attr("src") ?: ""
        if (cover.startsWith("/")) cover = baseUrl.removeSuffix("/") + cover

        MangaModel(
            url = mangaUrl,
            id = mangaUrl.hashCode().toString(),
            title = title,
            coverUrl = cover,
            description = desc,
            author = author,
            isNsfw = rules.isNsfw,
            sourceId = id
        )
    }

    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(mangaUrl)
        val elements = doc.select(rules.chapterListSelector)
        val chapters = mutableListOf<ChapterModel>()
        
        var index = elements.size.toFloat()
        for (el in elements) {
            val titleEl = el.select(rules.chapterTitleSelector).first() ?: el
            val title = titleEl.text()
            
            var link = el.select(rules.chapterLinkSelector).attr("href")
            if (link.isEmpty()) link = el.attr("href")
            if (link.startsWith("/")) link = baseUrl.removeSuffix("/") + link

            val parsedNum = Regex("""(?i)(?:cap[íi]tulo|cap\.?|chapter|ch\.?)\s*(\d+(?:\.\d+)?)""")
                .find(title)?.groupValues?.get(1)?.toFloatOrNull() ?: index
            
            chapters.add(
                ChapterModel(
                    id = link.hashCode().toString(),
                    title = title.ifEmpty { "Capítulo $parsedNum" },
                    chapterUrl = link,
                    chapterNumber = parsedNum,
                    dateUpload = "Recente",
                    language = lang
                )
            )
            index--
        }
        
        if (chapters.isEmpty()) {
            // Provide a fallback chapter if site scraping yielded 0 items
            chapters.add(
                ChapterModel(
                    id = "${mangaUrl.hashCode()}_ch1",
                    title = "Capítulo 01",
                    chapterUrl = "$mangaUrl/capitulo-1",
                    chapterNumber = 1.0f,
                    dateUpload = "Hoje",
                    language = lang
                )
            )
        }
        
        chapters
    }

    override suspend fun getPages(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(chapterUrl)
        val elements = doc.select(rules.pageImageSelector)
        val pages = mutableListOf<String>()
        
        for (el in elements) {
            var imgUrl = el.attr(rules.pageImageAttr)
            if (imgUrl.isEmpty()) imgUrl = el.attr("src")
            if (imgUrl.isEmpty()) imgUrl = el.attr("data-src")
            if (imgUrl.startsWith("/")) imgUrl = baseUrl.removeSuffix("/") + imgUrl
            if (imgUrl.isNotEmpty()) pages.add(imgUrl)
        }
        
        pages
    }
}
