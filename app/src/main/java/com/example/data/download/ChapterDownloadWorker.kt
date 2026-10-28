package com.example.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MangaRepository
import com.example.source.ChapterModel
import com.example.source.MangaModel

class ChapterDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_MANGA_URL = "key_manga_url"
        const val KEY_MANGA_TITLE = "key_manga_title"
        const val KEY_MANGA_COVER_URL = "key_manga_cover_url"
        const val KEY_SOURCE_ID = "key_source_id"
        const val KEY_CHAPTER_ID = "key_chapter_id"
        const val KEY_CHAPTER_TITLE = "key_chapter_title"
        const val KEY_CHAPTER_URL = "key_chapter_url"

        fun createInputData(
            manga: MangaModel,
            chapter: ChapterModel
        ): Data {
            return Data.Builder()
                .putString(KEY_MANGA_URL, manga.url)
                .putString(KEY_MANGA_TITLE, manga.title)
                .putString(KEY_MANGA_COVER_URL, manga.coverUrl)
                .putString(KEY_SOURCE_ID, manga.sourceId)
                .putString(KEY_CHAPTER_ID, chapter.id)
                .putString(KEY_CHAPTER_TITLE, chapter.title)
                .putString(KEY_CHAPTER_URL, chapter.chapterUrl)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val mangaUrl = inputData.getString(KEY_MANGA_URL) ?: return Result.failure()
        val mangaTitle = inputData.getString(KEY_MANGA_TITLE) ?: ""
        val mangaCoverUrl = inputData.getString(KEY_MANGA_COVER_URL) ?: ""
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: ""
        val chapterId = inputData.getString(KEY_CHAPTER_ID) ?: return Result.failure()
        val chapterTitle = inputData.getString(KEY_CHAPTER_TITLE) ?: ""
        val chapterUrl = inputData.getString(KEY_CHAPTER_URL) ?: ""

        val db = AppDatabase.getDatabase(applicationContext)
        val downloadManager = ChapterDownloadManager(
            context = applicationContext,
            downloadDao = db.downloadDao()
        )
        val preferencesRepository = UserPreferencesRepository(applicationContext)
        val repository = MangaRepository(
            mangaDao = db.mangaDao(),
            progressDao = db.progressDao(),
            downloadDao = db.downloadDao(),
            readingSessionDao = db.readingSessionDao(),
            downloadManager = downloadManager,
            preferencesRepository = preferencesRepository,
            chapterDao = db.chapterDao(),
            bookmarkDao = db.bookmarkDao(),
            searchHistoryDao = db.searchHistoryDao()
        )

        val manga = MangaModel(
            url = mangaUrl,
            id = mangaUrl.hashCode().toString(),
            title = mangaTitle,
            coverUrl = mangaCoverUrl,
            sourceId = sourceId
        )

        val chapter = ChapterModel(
            id = chapterId,
            title = chapterTitle,
            chapterUrl = chapterUrl
        )

        return try {
            val pages = repository.getPages(chapterUrl, chapterId, sourceId)
            if (pages.isNotEmpty()) {
                if (downloadManager.downloadChapter(manga, chapter, pages)) Result.success() else Result.failure()
            } else {
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            val denied = generateSequence<Throwable>(e) { it.cause }.take(16).any {
                it is retrofit2.HttpException && it.code() in listOf(403, 429)
            }
            if (!denied && runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
