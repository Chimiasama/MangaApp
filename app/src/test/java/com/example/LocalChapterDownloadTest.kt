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
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class LocalChapterDownloadTest {
    @Test fun `converted PDF images can be kept offline without an HTTP request for a file URI`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val id = "local-pdf-${java.util.UUID.randomUUID()}"
        val local = File(context.cacheDir, "$id.png")
        val bytes = image()
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
    private fun image(): ByteArray = java.io.ByteArrayOutputStream().use { out ->
        val bitmap = android.graphics.Bitmap.createBitmap(3, 4, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        out.toByteArray()
    }

    @Test fun `failed middle page never completes or exposes a shortened chapter and retry repairs it`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val server = okhttp3.mockwebserver.MockWebServer().apply { start() }
        val id = "missing-${java.util.UUID.randomUUID()}"
        val manager = ChapterDownloadManager(context, db.downloadDao())
        val manga = MangaModel("https://test/manga/$id", id, "Test", "", sourceId = "test")
        val chapter = ChapterModel(id, "Chapter", "https://test/chapter/$id")
        val bytes = image()
        val urls = (0..2).map { server.url("/page-$it").toString() }
        try {
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(okio.Buffer().write(bytes)))
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(503))
            assertFalse(manager.downloadChapter(manga, chapter, urls))
            val failed = db.downloadDao().getDownloadSync(id)!!
            assertEquals(DownloadEntity.STATE_FAILED, failed.downloadState)
            assertEquals(1, failed.downloadedPagesCount)
            assertTrue(manager.getDownloadedPages(id).isEmpty())
            assertFalse(File(failed.localPath, "page_1.jpg").exists())
            assertFalse(File(failed.localPath).listFiles()!!.any { it.extension == "part" })
            repeat(2) { server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(okio.Buffer().write(bytes))) }
            assertTrue(manager.downloadChapter(manga, chapter, urls))
            assertEquals(DownloadEntity.STATE_COMPLETED, db.downloadDao().getDownloadSync(id)!!.downloadState)
            val saved = manager.getDownloadedPages(id)
            assertEquals(3, saved.size)
            saved.forEachIndexed { index, url ->
                assertEquals("page_$index.jpg", File(java.net.URI(url)).name)
                assertArrayEquals(bytes, File(java.net.URI(url)).readBytes())
            }
            assertEquals(listOf("/page-0", "/page-1", "/page-1", "/page-2"),
                (1..4).map { server.takeRequest().path })
        } finally { manager.deleteDownload(id); server.shutdown(); db.close() }
    }

    @Test fun `legacy completed download with corrupt middle page is rejected and repaired`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val id = "corrupt-${java.util.UUID.randomUUID()}"
        val manager = ChapterDownloadManager(context, db.downloadDao())
        val local = File(context.cacheDir, "$id.png").apply { writeBytes(image()) }
        val manga = MangaModel("https://test/manga/$id", id, "Test", "", sourceId = "test")
        val chapter = ChapterModel(id, "Chapter", "https://test/chapter/$id")
        try {
            val urls = List(3) { local.toURI().toString() }
            assertTrue(manager.downloadChapter(manga, chapter, urls))
            val record = db.downloadDao().getDownloadSync(id)!!
            File(record.localPath, "page_1.jpg").writeText("<html>server error</html>")
            assertTrue(manager.getDownloadedPages(id).isEmpty())
            assertEquals(DownloadEntity.STATE_FAILED, db.downloadDao().getDownloadSync(id)!!.downloadState)
            assertTrue(manager.downloadChapter(manga, chapter, urls))
            assertEquals(3, manager.getDownloadedPages(id).size)
            assertArrayEquals(local.readBytes(), File(record.localPath, "page_1.jpg").readBytes())
        } finally { manager.deleteDownload(id); local.delete(); db.close() }
    }

    @Test fun `HTTP 200 containing an error document is not counted as a downloaded page`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val server = okhttp3.mockwebserver.MockWebServer().apply { start() }
        val id = "html-${java.util.UUID.randomUUID()}"
        val manager = ChapterDownloadManager(context, db.downloadDao())
        try {
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("<html>temporarily unavailable</html>"))
            val manga = MangaModel("https://test/manga/$id", id, "Test", "", sourceId = "test")
            val chapter = ChapterModel(id, "Chapter", "https://test/chapter/$id")
            assertFalse(manager.downloadChapter(manga, chapter, listOf(server.url("/image").toString())))
            val record = db.downloadDao().getDownloadSync(id)!!
            assertEquals(DownloadEntity.STATE_FAILED, record.downloadState)
            assertEquals(0, record.downloadedPagesCount)
            assertTrue(File(record.localPath).listFiles()!!.isEmpty())
            assertTrue(manager.getDownloadedPages(id).isEmpty())
        } finally { manager.deleteDownload(id); server.shutdown(); db.close() }
    }

}
