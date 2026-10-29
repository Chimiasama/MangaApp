package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.download.DownloadWorkHelper
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadEntity
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.ui.components.downloadCounts
import com.example.ui.components.statusText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadQueueTest {
    private val manga = MangaModel("manga:test", "book", "Teste", "", sourceId = "mangadash")

    @Test fun `all selected chapters become visible before scheduling and repeated requests preserve progress`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.downloadDao()
        val one = ChapterModel("one", "Capítulo 1", "chapter:one", 1f)
        val two = ChapterModel("two", "Capítulo 2", "chapter:two", 2f)
        val completed = ChapterModel("done", "Capítulo 3", "chapter:done", 3f)
        try {
            dao.insertDownload(DownloadWorkHelper.record(manga, completed).copy(downloadState = DownloadEntity.STATE_COMPLETED))
            dao.insertDownload(DownloadWorkHelper.record(manga, two).copy(downloadState = DownloadEntity.STATE_DOWNLOADING,
                totalPages = 20, downloadedPagesCount = 7, downloadProgress = .35f))
            val scheduled = mutableListOf<String>()
            val count = DownloadWorkHelper.enqueueChapters(manga, listOf(two, one, completed, one), dao) { chapter ->
                val visible = dao.getDownloadSync(chapter.id)!!
                if (chapter.id == "one") assertEquals(DownloadEntity.STATE_QUEUED, visible.downloadState)
                else { assertEquals(7, visible.downloadedPagesCount); assertEquals(DownloadEntity.STATE_DOWNLOADING, visible.downloadState) }
                scheduled += chapter.id
            }
            assertEquals(2, count)
            assertEquals(listOf("one", "two"), scheduled)
            assertEquals(DownloadEntity.STATE_COMPLETED, dao.getDownloadSync("done")!!.downloadState)
        } finally { db.close() }
    }

    @Test fun `scheduling failure is visible and retry keeps already downloaded pages`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val chapter = ChapterModel("failed", "Capítulo", "chapter:failed")
        try {
            try {
                DownloadWorkHelper.enqueueChapters(manga, listOf(chapter), db.downloadDao()) { throw java.io.IOException("scheduler failed") }
                fail("Must expose scheduling failure")
            } catch (_: java.io.IOException) {}
            assertEquals(DownloadEntity.STATE_FAILED, db.downloadDao().getDownloadSync(chapter.id)!!.downloadState)
            db.downloadDao().updateDownloadStatus(chapter.id, 3, .3f, DownloadEntity.STATE_FAILED, 10, "/existing")
            DownloadWorkHelper.enqueueChapters(manga, listOf(chapter), db.downloadDao()) {
                val row = db.downloadDao().getDownloadSync(it.id)!!
                assertEquals(DownloadEntity.STATE_QUEUED, row.downloadState)
                assertEquals(3, row.downloadedPagesCount)
                assertEquals("/existing", row.localPath)
            }
            val metadataOnly = chapter.copy(id = "metadata-only")
            db.downloadDao().insertDownload(DownloadWorkHelper.record(manga, metadataOnly))
            ChapterDownloadManager(context, db.downloadDao()).deleteDownload(metadataOnly.id)
            assertNull(db.downloadDao().getDownloadSync(metadataOnly.id))
            db.downloadDao().markStateIfPresent(metadataOnly.id, DownloadEntity.STATE_QUEUED)
            assertNull(db.downloadDao().getDownloadSync(metadataOnly.id))
        } finally { db.close() }
    }

    @Test fun `queue and failed states are distinct from transferring pages`() {
        val base = DownloadWorkHelper.record(manga, ChapterModel("one", "Capítulo", "chapter:one"))
        val rows = listOf(DownloadEntity.STATE_QUEUED, DownloadEntity.STATE_PREPARING, DownloadEntity.STATE_DOWNLOADING,
            DownloadEntity.STATE_RETRYING, DownloadEntity.STATE_FAILED, DownloadEntity.STATE_COMPLETED)
            .mapIndexed { i, state -> base.copy(chapterId = "$i", downloadState = state) }
        val counts = downloadCounts(rows)
        assertEquals(6, counts.total)
        assertEquals(4, counts.pending)
        assertEquals(1, counts.completed)
        assertEquals(1, counts.failed)
        assertFalse(rows[0].statusText().contains("Baixando"))
        assertEquals("Falha no download", rows[4].statusText())
    }
}
