package com.example.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder

class DynamicJsoupSource(val rules: SourceRuleConfig, private val fetch: suspend (String) -> String = SourceHttp::get) : MangaSource {
    override val id = rules.id
    override val name = rules.name
    override val baseUrl = rules.baseUrl
    override val isNsfw = rules.isNsfw
    override val lang = rules.lang
    override val canAggregateChapters = false
    override fun supportsLanguage(language: String) = ChapterLanguage.matches(lang, language)
    private suspend fun document(url: String): Document = Jsoup.parse(fetch(WebUrls.valid(url)), url).also {
        if (it.title().contains("just a moment", true) || it.title().contains("access denied", true))
            throw IOException("A fonte solicita verificação de acesso. Abra no navegador.")
    }
    private fun resolved(doc: Document, value: String): String = if (value.isBlank()) "" else doc.location().toHttpUrl().resolve(value.trim())?.toString()?.let { runCatching { WebUrls.valid(it) }.getOrNull() }.orEmpty()
    private fun image(doc: Document, el: org.jsoup.nodes.Element?): String = el?.let {
        resolved(doc, it.attr("data-src").ifBlank { it.attr("src") })
    }.orEmpty()
    override suspend fun getPopularManga(): List<MangaModel> = withContext(Dispatchers.IO) {
        parse(document(baseUrl.toHttpUrl().resolve(rules.popularPath)!!.toString()))
    }
    override suspend fun searchManga(query: String): List<MangaModel> = withContext(Dispatchers.IO) {
        val path = rules.searchPath.replace("{query}", URLEncoder.encode(query, "UTF-8"))
        parse(document(baseUrl.toHttpUrl().resolve(path)!!.toString()))
    }
    private fun parse(doc: Document) = doc.select(rules.listSelector).mapNotNull { el ->
        val title = el.selectFirst(rules.titleSelector)?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val linkEl = el.selectFirst(rules.linkAttrSelector) ?: if (el.hasAttr("href")) el else return@mapNotNull null
        val link = resolved(doc, linkEl.attr("href")).takeIf { it.isNotBlank() } ?: return@mapNotNull null
        MangaModel(link, link.hashCode().toString(), title, image(doc, el.selectFirst(rules.coverAttrSelector)), sourceId = id, isNsfw = isNsfw)
    }.distinctBy { it.url }
    override suspend fun getMangaDetails(mangaUrl: String): MangaModel = withContext(Dispatchers.IO) {
        val doc = document(mangaUrl)
        val title = doc.selectFirst(rules.titleSelector)?.text()?.takeIf { it.isNotBlank() } ?: throw IOException("O seletor de título não encontrou conteúdo. Revise as regras da fonte.")
        MangaModel(mangaUrl, mangaUrl.hashCode().toString(), title, image(doc, doc.selectFirst(rules.coverAttrSelector)),
            description = if (rules.detailDescSelector.isBlank()) "" else doc.select(rules.detailDescSelector).text(),
            author = if (rules.detailAuthorSelector.isBlank()) "" else doc.select(rules.detailAuthorSelector).text(), sourceId = id, isNsfw = isNsfw)
    }
    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        val doc = document(mangaUrl)
        doc.select(rules.chapterListSelector).mapIndexedNotNull { index, el ->
            val title = (el.selectFirst(rules.chapterTitleSelector) ?: el).text()
            val anchor = el.selectFirst(rules.chapterLinkSelector) ?: el
            val link = resolved(doc, anchor.attr("href")).takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val number = Regex("(?i)(?:cap[íi]tulo|cap\\.?|chapter|ch\\.?)\\s*(\\d+(?:\\.\\d+)?)").find(title)?.groupValues?.get(1)?.toFloatOrNull() ?: (index + 1).toFloat()
            ChapterModel(link.hashCode().toString(), title.ifBlank { "Capítulo $number" }, link, number, language = lang)
        }.distinctBy { it.id }
    }
    override suspend fun getPages(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
        val doc = document(chapterUrl)
        doc.select(rules.pageImageSelector).mapNotNull { el ->
            resolved(doc, el.attr(rules.pageImageAttr).ifBlank { el.attr("data-src").ifBlank { el.attr("src") } }).takeIf { it.isNotBlank() }
        }.distinct().also { if (it.isEmpty()) throw IOException("Nenhuma página encontrada. Revise o seletor de imagens ou abra no navegador.") }
    }
}
