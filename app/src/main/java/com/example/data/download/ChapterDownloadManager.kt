package com.example.data.download

import android.content.Context
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.source.ChapterModel
import com.example.source.MangaModel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class ChapterDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(com.example.source.SourceHttp.browserCookies)
        .addInterceptor(com.example.source.SourceHttp.imageHeaders).build()
) {

    val pdfStore = com.example.data.pdf.PdfChapterStore(context)

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
        if (pageUrls.isEmpty()) return@withContext

        val chapterDir = File(downloadsDir, downloadStorageKey(manga.url, chapter.id))
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

        if (pageUrls.first().startsWith(com.example.data.pdf.PdfChapterStore.SCHEME + ":")) {
            try {
                val source = pdfStore.file(android.net.Uri.parse(pageUrls.first()))
                val target = File(chapterDir, "chapter.pdf")
                val pending = File(chapterDir, "chapter.pdf.part")
                source.inputStream().use { input -> pending.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                    }
                } }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!pending.renameTo(target)) throw java.io.IOException("Não foi possível salvar o PDF offline.")
                val total = pdfStore.pages(target).size
                downloadDao.updateDownloadStatus(chapter.id, total, 1f, DownloadEntity.STATE_COMPLETED, total, chapterDir.absolutePath)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable) {
                    downloadDao.updateDownloadStatus(chapter.id, 0, 0f, DownloadEntity.STATE_FAILED, pageUrls.size, chapterDir.absolutePath)
                }
                throw cancelled
            } catch (error: Exception) {
                downloadDao.updateDownloadStatus(chapter.id, 0, 0f, DownloadEntity.STATE_FAILED, pageUrls.size, chapterDir.absolutePath)
                throw error
            } finally { File(chapterDir, "chapter.pdf.part").delete() }
            return@withContext
        }

        var downloadedCount = 0
        val totalPages = pageUrls.size

        try {
            for (index in pageUrls.indices) {
                val pageUrl = pageUrls[index]
                val pageFile = File(chapterDir, "page_${index}.jpg")

                if (!pageFile.exists() || pageFile.length() == 0L) {
                    val request = Request.Builder()
                        .url(pageUrl)
                        .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful && response.body != null) {
                            response.body!!.byteStream().use { input ->
                                FileOutputStream(pageFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
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
        }
    }

    suspend fun getDownloadedPages(chapterId: String): List<String> = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadSync(chapterId) ?: return@withContext emptyList()
        if (download.downloadState != DownloadEntity.STATE_COMPLETED && download.downloadedPagesCount == 0) {
            return@withContext emptyList()
        }

        val chapterDir = File(download.localPath)
        if (!chapterDir.exists() || !chapterDir.isDirectory) return@withContext emptyList()

        val pdf = File(chapterDir, "chapter.pdf")
        if (download.downloadState == DownloadEntity.STATE_COMPLETED && pdf.isFile) return@withContext pdfStore.pages(pdf)

        val files = chapterDir.listFiles()?.filter { it.extension == "jpg" }?.sortedBy { file ->
            val numStr = file.nameWithoutExtension.removePrefix("page_")
            numStr.toIntOrNull() ?: 0
        } ?: emptyList()

        return@withContext files.map { "file://${it.absolutePath}" }
    }

    suspend fun deleteDownload(chapterId: String) = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadSync(chapterId)
        if (download != null) {
            val chapterDir = File(download.localPath)
            if (chapterDir.exists()) {
                chapterDir.deleteRecursively()
            }
            downloadDao.deleteDownload(chapterId)
        }
    }
}

/** Opaque external IDs/URLs are data, never filesystem paths. Existing downloads use their saved localPath. */
internal fun downloadStorageKey(mangaUrl: String, chapterId: String): String = "chapter_" +
    java.security.MessageDigest.getInstance("SHA-256")
        .digest("${mangaUrl.length}:$mangaUrl$chapterId".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
