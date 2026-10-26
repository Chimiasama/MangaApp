package com.example.source

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

class WebLibrarySource(context: Context) : MangaSource {
    private val prefs = context.getSharedPreferences("web_chapters_v1", Context.MODE_PRIVATE)
    override val id = "web"
    override val name = "Leitor web experimental"
    override val baseUrl = "https://example.invalid"
    override val isNsfw = false
    override val lang = "all"
    override val canAggregateChapters = false
    fun save(capture: WebCapture) {
        WebUrls.valid(capture.url)
        require(capture.pages.isNotEmpty() && capture.pages.size <= 1000) { "Selecione ao menos uma página (máximo 1.000)." }
        capture.pages.forEach { WebUrls.valid(it) }
        require(prefs.contains(capture.url) || prefs.all.size < 200) { "Limite de 200 capítulos web salvos. Remova capturas antigas antes de importar mais." }
        val data = JSONObject().put("url", capture.url).put("title", capture.title).put("pages", JSONArray(capture.pages))
            .put("selector", capture.selector).put("language", capture.language)
        require(prefs.edit().putString(capture.url, data.toString()).commit()) { "Falha ao salvar capítulo web." }
    }
    fun savedUrls(): List<String> = prefs.all.keys.sorted()
    fun removeCapture(url: String) { require(prefs.edit().remove(url).commit()) }
    fun capture(url: String): WebCapture? = prefs.getString(url, null)?.let { raw ->
        val data = JSONObject(raw); val pages = data.getJSONArray("pages")
        WebCapture(url, data.getString("title"), (0 until pages.length()).map { pages.getString(it) }, emptyList(), data.optString("selector"), data.optString("language"))
    }
    override suspend fun getPopularManga() = emptyList<MangaModel>()
    override suspend fun searchManga(query: String) = emptyList<MangaModel>()
    override suspend fun getMangaDetails(mangaUrl: String): MangaModel {
        val data = capture(mangaUrl) ?: WebExtraction.extract(SourceHttp.get(mangaUrl), mangaUrl)
        return MangaModel(mangaUrl, mangaUrl, data.title, data.pages.firstOrNull().orEmpty(), sourceId = id)
    }
    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> = listOf(chapter(capture(mangaUrl) ?: WebExtraction.extract(SourceHttp.get(mangaUrl), mangaUrl)))
    override suspend fun getPages(chapterUrl: String): List<String> {
        val data = capture(chapterUrl) ?: WebExtraction.extract(SourceHttp.get(chapterUrl), chapterUrl)
        SourceHttp.rememberImages(data.pages, data.url)
        return data.pages
    }
    fun chapter(capture: WebCapture) = ChapterModel(capture.url, capture.title, capture.url, 1f, language = capture.language)
}
