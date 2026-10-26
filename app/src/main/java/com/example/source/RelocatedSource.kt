package com.example.source

/** Keep canonical book/chapter URLs so bookmarks and progress survive host changes. */
class RelocatedSource(private val delegate: MangaSource, val destination: String) : MangaSource by delegate {
    override val baseUrl = destination
    val canonicalOrigin = WebUrls.origin(delegate.baseUrl)
    private fun canonical(url: String) = WebUrls.relocate(url, destination, canonicalOrigin)
    private fun network(url: String) = WebUrls.relocate(url, canonicalOrigin, destination)
    private fun manga(m: MangaModel) = m.copy(url = canonical(m.url), coverUrl = network(m.coverUrl), id = if (delegate is DynamicJsoupSource) canonical(m.url).hashCode().toString() else if (m.id.startsWith("https://")) canonical(m.id) else m.id)
    private fun chapter(ch: ChapterModel) = ch.copy(chapterUrl = canonical(ch.chapterUrl), id = if (delegate is DynamicJsoupSource) canonical(ch.chapterUrl).hashCode().toString() else if (ch.id.startsWith("https://")) canonical(ch.id) else ch.id)
    override suspend fun getPopularManga() = delegate.getPopularManga().map(::manga)
    override suspend fun getPopularManga(language: String) = delegate.getPopularManga(language).map(::manga)
    override suspend fun searchManga(query: String) = delegate.searchManga(query).map(::manga)
    override suspend fun searchManga(query: String, language: String) = delegate.searchManga(query, language).map(::manga)
    override suspend fun searchManga(query: String, language: String, category: String) = delegate.searchManga(query, language, category).map(::manga)
    override suspend fun searchManga(query: String, language: String, category: String, period: PublicationPeriod?) = delegate.searchManga(query, language, category, period).map(::manga)
    override suspend fun getMangaDetails(mangaUrl: String) = manga(delegate.getMangaDetails(canonical(mangaUrl)))
    override suspend fun getChapters(mangaUrl: String) = delegate.getChapters(canonical(mangaUrl)).map(::chapter)
    override suspend fun getChapters(mangaUrl: String, language: String) = delegate.getChapters(canonical(mangaUrl), language).map(::chapter)
    override suspend fun getPages(chapterUrl: String) = delegate.getPages(canonical(chapterUrl)).map(::network).also { SourceHttp.rememberImages(it, WebUrls.relocate(chapterUrl, canonicalOrigin, destination)) }
}
