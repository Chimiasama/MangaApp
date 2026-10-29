package com.example.data.download

import android.content.Context
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.source.ChapterModel
import com.example.source.MangaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class ChapterDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(com.example.source.SourceHttp.imageHeaders).build()
) {

    private val calls: okhttp3.Call.Factory = com.example.data.network.MangaDexCallFactory(okHttpClient)

    private val downloadsDir: File
        get() {
            val dir = File(context.filesDir, "manga_downloads")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    suspend fun downloadChapter(
        manga: MangaModel,
        chapter: ChapterModel,
        pageUrls: List<String>
    ) = withContext(Dispatchers.IO) {
        if (pageUrls.isEmpty()) return@withContext false

        val chapterDir = File(downloadsDir, "${manga.url.hashCode()}_${chapter.id}")
        if (!chapterDir.exists()) {
            chapterDir.mkdirs()
        }

        // Initial DB record
        val initialEntity = DownloadEntity(
            chapterId = chapter.id,
            chapterTitle = chapter.title,
            chapterUrl = chapter.chapterUrl,
            mangaUrl = manga.url,
            mangaTitle = manga.title,
            mangaCoverUrl = manga.coverUrl,
            sourceId = manga.sourceId,
            totalPages = pageUrls.size,
            downloadedPagesCount = 0,
            localPath = chapterDir.absolutePath,
            downloadState = DownloadEntity.STATE_DOWNLOADING,
            downloadProgress = 0f
        )
        downloadDao.insertDownload(initialEntity)

        var downloadedCount = 0
        val totalPages = pageUrls.size

        try {
            for (index in pageUrls.indices) {
                val pageUrl = pageUrls[index]
                val pageFile = File(chapterDir, "page_${index}.jpg")

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!validImage(pageFile)) {
                    pageFile.delete()
                    val pending = File(chapterDir, "page_${index}.part")
                    pending.delete()
                    try {
                        if (pageUrl.startsWith("file:")) {
                            val local = File(java.net.URI(pageUrl))
                            if (!validImage(local)) throw java.io.IOException("Página local indisponível ou inválida.")
                            local.copyTo(pending, overwrite = true)
                        } else {
                            downloadRemotePage(pageUrl, pending)
                        }
                        if (!validImage(pending)) throw java.io.IOException("Página ${index + 1} não contém uma imagem válida.")
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (!pending.renameTo(pageFile)) throw java.io.IOException("Não foi possível salvar a página ${index + 1}.")
                    } finally { pending.delete() }
                }

                downloadedCount++
                val progress = downloadedCount.toFloat() / totalPages.toFloat()

                downloadDao.updateDownloadStatus(
                    chapterId = chapter.id,
                    downloadedPagesCount = downloadedCount,
                    progress = progress,
                    state = if (downloadedCount == totalPages) DownloadEntity.STATE_COMPLETED else DownloadEntity.STATE_DOWNLOADING,
                    totalPages = totalPages,
                    localPath = chapterDir.absolutePath
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            downloadDao.updateDownloadStatus(
                chapterId = chapter.id,
                downloadedPagesCount = downloadedCount,
                progress = if (totalPages > 0) downloadedCount.toFloat() / totalPages.toFloat() else 0f,
                state = DownloadEntity.STATE_FAILED,
                totalPages = totalPages,
                localPath = chapterDir.absolutePath
            )
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    suspend fun getDownloadedPages(chapterId: String): List<String> = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadSync(chapterId) ?: return@withContext emptyList()
        if (download.downloadState != DownloadEntity.STATE_COMPLETED || download.totalPages <= 0) return@withContext emptyList()
        val chapterDir = File(download.localPath)
        val files = (0 until download.totalPages).map { File(chapterDir, "page_${it}.jpg") }
        if (files.any { !validImage(it) }) {
            val count = files.count(::validImage)
            downloadDao.updateDownloadStatus(chapterId, count, count.toFloat() / download.totalPages,
                DownloadEntity.STATE_FAILED, download.totalPages, download.localPath)
            return@withContext emptyList()
        }
        files.map { it.toURI().toString() }
    }

    private class PageHttpFailure(val code: Int) : java.io.IOException("Servidor da página respondeu HTTP $code")

    private suspend fun downloadRemotePage(original: String, target: File) {
        try { fetchImage(original, target); return }
        catch (primary: java.io.IOException) {
            val registry = com.example.data.network.MangaDexPages.registry
            if (primary is PageHttpFailure || !registry.contains(original) || certificateFailure(primary)) throw primary
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val alternatives = registry.alternatives(original)
            var last = primary
            for (alternative in alternatives.take(2)) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                try { fetchImage(alternative, target); return }
                catch (error: java.io.IOException) {
                    if (certificateFailure(error) || (error is PageHttpFailure && error.code in listOf(403, 429))) throw error
                    last = error
                }
            }
            throw last
        }
    }

    private fun certificateFailure(error: Throwable) = generateSequence(error) { it.cause }.take(16).any {
        it is java.security.cert.CertificateException || it is javax.net.ssl.SSLPeerUnverifiedException
    }

    private fun fetchImage(url: String, target: File) {
        calls.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw PageHttpFailure(response.code)
            val body = response.body ?: throw java.io.IOException("Resposta de página vazia.")
            val expected = body.contentLength()
            val copied = body.byteStream().use { input -> FileOutputStream(target).use { input.copyTo(it) } }
            if (expected >= 0 && copied != expected) throw java.io.IOException("Transferência de página incompleta.")
        }
        if (!validImage(target)) throw java.io.IOException("A resposta não contém uma imagem válida.")
    }

    private fun validImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val options = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit((maxOf(bounds.outWidth, bounds.outHeight) / 1024).coerceAtLeast(1))
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options) ?: return false
        bitmap.recycle()
        return true
    }

    suspend fun deleteDownload(chapterId: String) = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadSync(chapterId)
        if (download != null) {
            if (download.localPath.isNotBlank()) {
                val chapterDir = File(download.localPath)
                if (chapterDir.exists()) chapterDir.deleteRecursively()
            }
            downloadDao.deleteDownload(chapterId)
        }
    }
}
