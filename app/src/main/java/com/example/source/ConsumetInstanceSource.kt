package com.example.source

import org.json.JSONObject
import org.json.JSONArray
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Optional user-hosted Consumet-compatible REST contract. No public endpoint or invented fallback. */
class ConsumetInstanceSource(override val baseUrl: String, private val provider: String, override val lang: String,
    private val fetch: suspend (String) -> String = SourceHttp::get) : MangaSource {
    override val id = "experimental_api"
    override val name = "API experimental ($provider)"
    override val isNsfw = false
    override val canAggregateChapters = false
    override fun supportsLanguage(language: String) = ChapterLanguage.matches(lang, language)
    private fun endpoint(path: String, key: String? = null, value: String = "") = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("manga").addPathSegment(provider).addPathSegment(path).apply { if (key != null) addQueryParameter(key, value) }.build().toString()
    private fun array(data: JSONArray) = (0 until data.length()).map { data.getJSONObject(it) }
    override suspend fun getPopularManga() = emptyList<MangaModel>() // This contract has no real popular endpoint.
    override suspend fun searchManga(query: String): List<MangaModel> {
        if (query.isBlank()) return emptyList()
        return array(JSONObject(fetch(endpoint(query))).optJSONArray("results") ?: JSONArray()).map { item ->
            val rawId = item.getString("id")
            MangaModel(endpoint("info", "id", rawId), rawId, item.optString("title").ifBlank { rawId }, item.optString("image"), sourceId = id)
        }
    }
    override suspend fun getMangaDetails(mangaUrl: String): MangaModel {
        require(WebUrls.origin(mangaUrl) == WebUrls.origin(baseUrl))
        val data = JSONObject(fetch(mangaUrl))
        return MangaModel(mangaUrl, data.getString("id"), data.optString("title"), data.optString("image"), description = data.optString("description"),
            genres = data.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(), sourceId = id)
    }
    override suspend fun getChapters(mangaUrl: String): List<ChapterModel> {
        require(WebUrls.origin(mangaUrl) == WebUrls.origin(baseUrl))
        return array(JSONObject(fetch(mangaUrl)).optJSONArray("chapters") ?: JSONArray()).mapIndexed { index, ch ->
            val rawId = ch.getString("id")
            ChapterModel("$provider:$rawId", ch.optString("title").ifBlank { "Capítulo ${index + 1}" }, endpoint("read", "chapterId", rawId),
                chapterNumber = ch.optString("chapterNumber").toFloatOrNull() ?: (index + 1).toFloat(), language = lang)
        }
    }
    override suspend fun getPages(chapterUrl: String): List<String> {
        require(WebUrls.origin(chapterUrl) == WebUrls.origin(baseUrl))
        return array(JSONArray(fetch(chapterUrl))).mapNotNull { runCatching { WebUrls.valid(it.getString("img")) }.getOrNull() }
            .also { require(it.isNotEmpty()) { "A API não retornou páginas compatíveis." } }
    }
}
