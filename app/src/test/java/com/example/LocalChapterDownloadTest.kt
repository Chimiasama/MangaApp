package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadEntity
import com.example.source.ChapterModel
import com.example.source.MangaModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalChapterDownloadTest {
    @Test fun `converted PDF images can be kept offline without an HTTP request for a file URI`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val id = "local-pdf-${java.util.UUID.randomUUID()}"
        val local = File(context.cacheDir, "$id.png")
        val bytes = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aNU0AAAAASUVORK5CYII=")
        local.writeBytes(bytes)
        val manager = ChapterDownloadManager(context, db.downloadDao())
        try {
            val manga = MangaModel("https://mangadash.net/manga/test", "manga", "Test", "", sourceId = "mangadash")
            val chapter = ChapterModel(id, "Capítulo 1", "https://mangadash.net/capitulo/test/1")
            manager.downloadChapter(manga, chapter, listOf(local.toURI().toString()))
            val download = db.downloadDao().getDownloadSync(id)!!
            assertEquals(DownloadEntity.STATE_COMPLETED, download.downloadState)
            val saved = File(download.localPath).listFiles()!!.single()
            assertArrayEquals(bytes, saved.readBytes())
        } finally {
            manager.deleteDownload(id)
            local.delete()
            db.close()
        }
    }
}
