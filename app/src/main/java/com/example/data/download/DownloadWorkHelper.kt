package com.example.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.source.ChapterModel
import com.example.source.MangaModel

object DownloadWorkHelper {

    fun enqueueMultipleChapterDownloads(
        context: Context,
        manga: MangaModel,
        chapters: List<ChapterModel>
    ) {
        if (chapters.isEmpty()) return

        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        chapters.forEach { chapter ->
            val workRequest = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(ChapterDownloadWorker.createInputData(manga, chapter))
                .addTag("download_${chapter.id}")
                .addTag("manga_${manga.url.hashCode()}")
                .build()

            workManager.enqueueUniqueWork(
                "download_chapter_${chapter.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
