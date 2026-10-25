package com.example

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ChapterDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.pdf.PdfChapterStore
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.util.ImageCacheConfig
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PdfChapterStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun bytes(): ByteArray = InstrumentationRegistry.getInstrumentation().context.assets.open("pdf-three-pages.pdf").use { it.readBytes() }
    private fun client(data: ByteArray, calls: AtomicInteger = AtomicInteger(), code: Int = 200) =
        OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).code(code).message("fixture").protocol(Protocol.HTTP_1_1)
                .body(data.toResponseBody()).build()
        }.build()
    @Test fun pdfDownloadsOnceAndRendersPagesOnDemand() = runBlocking {
        val calls = AtomicInteger()
        val store = PdfChapterStore(context, client(bytes(), calls))
        val url = "https://pdf.test/${UUID.randomUUID()}.pdf"
        val pages = store.open(url)
        assertEquals(3, pages.size)
        assertEquals(pages, store.open(url))
        assertEquals(1, calls.get())
        val loader = ImageCacheConfig.setupCoilImageLoader(context)
        try {
            for ((index, color) in listOf(0 to Color.RED, 2 to Color.BLUE)) {
                val result = loader.execute(ImageRequest.Builder(context).data(pages[index]).size(600, 900).build())
                assertTrue("Coil must load PDF page: $result", result is SuccessResult)
                val bitmap = (result as SuccessResult).drawable as android.graphics.drawable.BitmapDrawable
                assertEquals(color, bitmap.bitmap.getPixel(bitmap.bitmap.width / 2, bitmap.bitmap.height / 2))
            }
        } finally { loader.shutdown() }
    }
    @Test fun htmlErrorsNeverBecomePdfOrLeavePartialFiles() = runBlocking {
        val store = PdfChapterStore(context, client("<html>Access denied</html>".toByteArray()))
        try { store.open("https://pdf.test/${UUID.randomUUID()}.pdf"); fail("Must reject HTML") }
        catch (expected: IOException) { assertTrue(expected.message!!.contains("PDF")) }
        assertTrue(File(context.cacheDir, "pdf_chapters").listFiles().orEmpty().none { it.extension == "part" })
    }
    @Test fun offlinePdfSurvivesCacheClearing() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PdfChapterStore(context, client(bytes()))
        val pages = store.open("https://pdf.test/${UUID.randomUUID()}.pdf")
        val manager = ChapterDownloadManager(context, db.downloadDao())
        val chapter = ChapterModel("pdf-${UUID.randomUUID()}", "Capítulo 1", "https://pdf.test/chapter", language = "pt-br")
        val manga = MangaModel("https://pdf.test/book", "book", "PDF", "", sourceId = "mangadash")
        try {
            manager.downloadChapter(manga, chapter, pages)
            ImageCacheConfig.clearImageCache(context)
            val offline = manager.getDownloadedPages(chapter.id)
            assertEquals(3, offline.size)
            val bitmap = manager.pdfStore.render(Uri.parse(offline[1]), 1024)
            assertEquals(Color.GREEN, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            bitmap.recycle()
            manager.deleteDownload(chapter.id)
            assertTrue(manager.getDownloadedPages(chapter.id).isEmpty())
        } finally { db.close() }
    }
    @Test fun invalidPdfPagesAndExternalPathsFail() = runBlocking {
        val store = PdfChapterStore(context, client(bytes()))
        val pages = store.open("https://pdf.test/${UUID.randomUUID()}.pdf")
        val bad = Uri.parse(pages[0]).buildUpon().path("99").build()
        try { store.render(bad); fail("Must reject invalid page") } catch (_: IOException) {}
        try { store.file(Uri.parse("mangapdf://page/0?file=/etc/passwd")); fail("Must reject outside storage") } catch (_: IOException) {}
    }
    @Test fun cancellingDownloadClosesRequestAndRemovesPartialFile() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val network = OkHttpClient.Builder().addInterceptor { chain ->
            started.complete(Unit)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(5)
            throw IOException("cancelled")
        }.build()
        val store = PdfChapterStore(context, network)
        val download = async(Dispatchers.IO) { store.open("https://pdf.test/${UUID.randomUUID()}.pdf") }
        withTimeout(10_000) { started.await() }
        withTimeout(5_000) { download.cancelAndJoin() }
        assertTrue(download.isCancelled)
        assertTrue(File(context.cacheDir, "pdf_chapters").listFiles().orEmpty().none { it.extension == "part" })
    }

    @Test fun importedPdfStaysInLibraryAndReadsWithoutOriginalOrNetwork() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val manager = ChapterDownloadManager(context, db.downloadDao())
        val repo = com.example.data.repository.MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(),
            db.readingSessionDao(), manager, com.example.data.preferences.UserPreferencesRepository(context), db.chapterDao())
        val original = File(context.cacheDir, "original-${UUID.randomUUID()}.pdf").apply { writeBytes(bytes()) }
        try {
            val book = repo.importPdf(context, Uri.fromFile(original))
            val chapter = com.example.source.LocalPdfSource.chapter(book)
            db.chapterDao().updateChapterReadStatus(chapter.id, true, 1, 3)
            val second = repo.importPdf(context, Uri.fromFile(original))
            assertEquals(book.url, second.url)
            assertEquals(1, db.mangaDao().getAllFavoritesSync().size)
            assertEquals(1, db.chapterDao().getChapterByIdSync(chapter.id)!!.lastPageRead)
            assertTrue(repo.getAvailableSources().none { it.id == "localpdf" })
            original.delete()
            ImageCacheConfig.clearImageCache(context)
            val pages = repo.getPages(chapter.chapterUrl, chapter.id, "localpdf")
            assertEquals(3, pages.size)
            val bitmap = manager.pdfStore.render(Uri.parse(pages[1]))
            assertEquals(Color.GREEN, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            bitmap.recycle()
            manager.deleteDownload(chapter.id)
        } finally { original.delete(); db.close() }
    }
    @Test fun invalidImportedDocumentDoesNotCreateABook() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val manager = ChapterDownloadManager(context, db.downloadDao())
        val repo = com.example.data.repository.MangaRepository(db.mangaDao(), db.progressDao(), db.downloadDao(),
            db.readingSessionDao(), manager, com.example.data.preferences.UserPreferencesRepository(context), db.chapterDao())
        val original = File(context.cacheDir, "invalid-${UUID.randomUUID()}.pdf").apply { writeText("Not a PDF") }
        try {
            try { repo.importPdf(context, Uri.fromFile(original)); fail("Invalid document must fail") } catch (_: IOException) {}
            assertTrue(db.mangaDao().getAllFavoritesSync().isEmpty())
        } finally { original.delete(); db.close() }
    }

}
