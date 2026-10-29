package com.example.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import com.example.source.ChapterModel
import com.example.source.MangaModel

object DownloadWorkHelper {
    internal val activeDownloads = Semaphore(2)
    const val DOWNLOADS_TAG = "chapter_downloads"
    fun workName(chapterId: String) = "download_chapter_$chapterId"

    internal fun record(manga: MangaModel, chapter: ChapterModel) = DownloadEntity(
        chapter.id, chapter.title, chapter.chapterUrl, manga.url, manga.title, manga.coverUrl, manga.sourceId,
        downloadState = DownloadEntity.STATE_QUEUED)

    suspend fun enqueueMultipleChapterDownloads(
        context: Context,
        manga: MangaModel,
        chapters: List<ChapterModel>,
        dao: DownloadDao = AppDatabase.getDatabase(context).downloadDao()
    ): Int = withContext(Dispatchers.IO) {
        if (chapters.isEmpty()) return@withContext 0

        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        enqueueChapters(manga, chapters, dao) { chapter ->
            val workRequest = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(ChapterDownloadWorker.createInputData(manga, chapter))
                .addTag("download_${chapter.id}")
                .addTag("manga_${manga.url.hashCode()}")
                .addTag(DOWNLOADS_TAG)
                .build()

            workManager.enqueueUniqueWork(workName(chapter.id), ExistingWorkPolicy.KEEP, workRequest).await()
        }
    }

    internal suspend fun enqueueChapters(manga: MangaModel, chapters: List<ChapterModel>, dao: DownloadDao,
        enqueue: suspend (ChapterModel) -> Unit): Int {
        var queued = 0
        for (chapter in chapters.distinctBy { it.id }.sortedBy { it.chapterNumber }) {
            if (dao.getDownloadSync(chapter.id)?.downloadState == DownloadEntity.STATE_COMPLETED) continue
            dao.markEnqueued(record(manga, chapter))
            try { enqueue(chapter); queued++ }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                dao.markStateIfPresent(chapter.id, DownloadEntity.STATE_FAILED)
                throw error
            }
        }
        return queued
    }

    suspend fun cancel(context: Context, chapterId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(chapterId)).await()
    }
}
