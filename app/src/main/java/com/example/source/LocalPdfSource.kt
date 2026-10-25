package com.example.source

import android.net.Uri
import com.example.data.pdf.PdfChapterStore

/** User-imported documents are kept out of remote catalog searches and language guesses. */
class LocalPdfSource(private val store: PdfChapterStore) : MangaSource {
    override val id = ID
    override val name = "PDF local"
    override val baseUrl = "mangapdf://page"
    override val isNsfw = false
    override val lang = "local"
    override val canAggregateChapters = false
    override fun supportsLanguage(language: String) = false
    override suspend fun getPopularManga() = emptyList<MangaModel>()
    override suspend fun searchManga(query: String) = emptyList<MangaModel>()
    override suspend fun getMangaDetails(mangaUrl: String) = MangaModel(mangaUrl, mangaUrl, "PDF local", mangaUrl, sourceId = ID)
    override suspend fun getChapters(mangaUrl: String) = emptyList<ChapterModel>()
    override suspend fun getPages(chapterUrl: String): List<String> = try {
        store.pages(store.file(Uri.parse(chapterUrl)))
    } catch (error: java.io.IOException) {
        throw java.io.IOException("PDF local indisponível. Importe o arquivo novamente pela Biblioteca.", error)
    }
    companion object {
        const val ID = "localpdf"
        fun chapter(manga: MangaModel) = ChapterModel(manga.id + ":document", "PDF completo", manga.url)
    }
}
