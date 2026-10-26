package com.example.source

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.io.IOException

/** Data only: no fetched page can execute code in the native reader. */
data class WebCapture(val url: String, val title: String, val pages: List<String>, val chapters: List<ChapterModel>, val selector: String = "", val language: String = "")

object WebUrls {
    fun valid(value: String): String {
        val url = value.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Informe um endereço HTTPS válido.")
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "Use HTTPS sem usuário ou senha no endereço." }
        return url.toString()
    }
    fun origin(value: String): String = valid(value).toHttpUrlOrNull()!!.let { "https://${it.host}" + if (it.port != 443) ":${it.port}" else "" }
    fun relocate(value: String, original: String, replacement: String): String {
        val url = value.toHttpUrlOrNull() ?: return value
        if (origin(value) != origin(original)) return value
        val target = valid(replacement).toHttpUrlOrNull()!!
        return url.newBuilder().scheme(target.scheme).host(target.host).port(target.port).build().toString()
    }
    fun search(query: String, hosts: List<String> = emptyList()): String = "https://www.google.com/search".toHttpUrlOrNull()!!.newBuilder()
        .addQueryParameter("q", query.trim() + if (hosts.isEmpty()) " manga capítulos leitura" else " (" + hosts.distinct().take(10).joinToString(" OR ") { "site:$it" } + ")")
        .build().toString()
}

object WebExtraction {
    private val containers = listOf("#readerarea", ".reading-content", ".reader-images", "#chapter-content", ".chapter-content", ".container-chapter-reader", "[data-reader]")
    fun extract(html: String, address: String, selector: String = "", language: String = ""): WebCapture {
        val url = WebUrls.valid(address)
        require(html.length <= 4_000_000) { "Página muito grande para analisar. Abra um capítulo individual." }
        val doc = Jsoup.parse(html, url)
        val title = doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() } ?: doc.title().ifBlank { url }
        if (doc.title().contains("just a moment", true) || doc.title().contains("access denied", true))
            throw IOException("O site solicita verificação. Confira no navegador antes de tentar a extração.")
        val imageElements = if (selector.isNotBlank()) doc.select(selector) else {
            val container = containers.firstOrNull { doc.select("$it img").isNotEmpty() }
            if (container != null) doc.select("$container img") else doc.select("img").filter { image ->
                val width = image.attr("data-natural-width").toIntOrNull() ?: image.attr("width").toIntOrNull() ?: 0
                val height = image.attr("data-natural-height").toIntOrNull() ?: image.attr("height").toIntOrNull() ?: 0
                width >= 300 && height >= 300 && image.parents().none { it.tagName() in setOf("header", "footer", "nav", "aside") }
            }
        }
        val pages = imageElements.asSequence().filter { it.tagName() == "img" }.mapNotNull { image ->
            val raw = listOf("data-src", "data-lazy-src", "data-original", "src").firstNotNullOfOrNull { attr ->
                image.attr(attr).trim().takeIf { it.isNotEmpty() && !it.startsWith("data:") && !it.startsWith("blob:") }
            } ?: return@mapNotNull null
            val resolved = doc.location().toHttpUrlOrNull()?.resolve(raw)?.toString() ?: return@mapNotNull null
            runCatching { WebUrls.valid(resolved) }.getOrNull()
        }.distinct().take(1000).toList()
        val host = url.toHttpUrlOrNull()!!.host
        val chapterPattern = Regex("(?i)(?:cap[íi]tulo|cap\\.?|chapter|ch\\.?)\\s*(\\d+(?:\\.\\d+)?)")
        val chapters = doc.select("a[href]").mapNotNull { a ->
            val match = chapterPattern.find(a.text()) ?: return@mapNotNull null
            val link = a.absUrl("href").toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == host }?.toString() ?: return@mapNotNull null
            ChapterModel(id = link, title = a.text(), chapterUrl = link, chapterNumber = match.groupValues[1].toFloat(), language = language)
        }.distinctBy { it.chapterUrl }.take(2000)
        return WebCapture(url, title.take(300), pages, chapters, selector, language)
    }
}
