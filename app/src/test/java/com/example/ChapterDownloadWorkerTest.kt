package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import androidx.concurrent.futures.ResolvableFuture
import com.example.data.download.ChapterDownloadManager
import com.example.data.download.ChapterDownloadWorker
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadEntity
import com.example.source.ChapterModel
import com.example.source.MangaModel
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadow.api.Shadow
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [WorkerPdfRendererShadow::class, WorkerPdfPageShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChapterDownloadWorkerTest {
    private fun parameters(data: Data, attempt: Int = 0): WorkerParameters {
        val executor = java.util.concurrent.Executor { it.run() }
        return WorkerParameters(UUID.randomUUID(), data, listOf("test-download"), WorkerParameters.RuntimeExtras(),
            attempt, 0, executor, WorkManagerTaskExecutor(executor), WorkerFactory.getDefaultWorkerFactory(),
            ProgressUpdater { _, _, _ -> ResolvableFuture.create<Void>().apply { set(null) } },
            ForegroundUpdater { _, _, _ -> ResolvableFuture.create<Void>().apply { set(null) } })
    }

    @Test fun `bulk worker exposes preparation then renders a real MangaDash PDF with application cache`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val server = MockWebServer().apply { start() }
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val id = "worker-${UUID.randomUUID()}"
        val manga = MangaModel(server.url("/manga").toString(), "test", "PDF de teste", "", sourceId = "mangadash")
        val chapter = ChapterModel(id, "Capítulo 1", server.url("/chapter").toString(), 1f)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = if (request.path == "/chapter") {
                started.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                MockResponse().setBody("<script id='chapterViewerData' type='application/json'>{\"pdfUrl\":\"${server.url("/chapter.pdf")}\"}</script>")
            } else MockResponse().setHeader("Content-Type", "application/pdf").setBody(okio.Buffer().write(pdf()))
        }
        try {
            WorkerPdfRendererShadow.constructorCalls = 0
            val worker = ChapterDownloadWorker(context, parameters(ChapterDownloadWorker.createInputData(manga, chapter)))
            val running = async(Dispatchers.Default) { worker.doWork() }
            assertTrue(withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) })
            val preparing = db.downloadDao().getDownloadSync(id)!!
            assertEquals(DownloadEntity.STATE_PREPARING, preparing.downloadState)
            assertEquals("PDF de teste", preparing.mangaTitle)
            assertEquals(0, preparing.totalPages)
            release.countDown()
            assertEquals(ListenableWorker.Result.success(), withTimeout(20_000) { running.await() })
            val saved = db.downloadDao().getDownloadSync(id)!!
            assertTrue(WorkerPdfRendererShadow.constructorCalls > 0)
            assertEquals(DownloadEntity.STATE_COMPLETED, saved.downloadState)
            assertEquals(1, saved.totalPages)
            assertEquals(1, ChapterDownloadManager(context, db.downloadDao()).getDownloadedPages(id).size)
        } finally {
            release.countDown()
            ChapterDownloadManager(context, db.downloadDao()).deleteDownload(id)
            server.shutdown()
        }
    }

    @Test fun `page resolution failure remains visible while retrying and after terminal failure`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val server = MockWebServer().apply { start() }
        val id = "failed-worker-${UUID.randomUUID()}"
        val manga = MangaModel(server.url("/manga").toString(), "test", "Teste", "", sourceId = "mangadash")
        val chapter = ChapterModel(id, "Capítulo", server.url("/chapter").toString())
        val data = ChapterDownloadWorker.createInputData(manga, chapter)
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(ListenableWorker.Result.retry(), ChapterDownloadWorker(context, parameters(data)).doWork())
            assertEquals(DownloadEntity.STATE_RETRYING, db.downloadDao().getDownloadSync(id)!!.downloadState)
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(ListenableWorker.Result.failure(), ChapterDownloadWorker(context, parameters(data, 2)).doWork())
            assertEquals(DownloadEntity.STATE_FAILED, db.downloadDao().getDownloadSync(id)!!.downloadState)
        } finally { db.downloadDao().deleteDownload(id); server.shutdown() }
    }

    private fun pdf(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun write(s: String) { out.write(s.toByteArray(Charsets.US_ASCII)) }
        val paint = "0 0 1 rg\n0 0 200 300 re\nf\n"
        val objects = listOf("<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] /Resources << >> /Contents 4 0 R >>",
            "<< /Length ${paint.length} >>\nstream\n${paint}endstream")
        write("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, body -> out.size().also { write("${index + 1} 0 obj\n$body\nendobj\n") } }
        val xref = out.size()
        write("xref\n0 5\n0000000000 65535 f \n")
        offsets.forEach { write("%010d 00000 n \n".format(java.util.Locale.ROOT, it)) }
        write("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}

// Robolectric API 36 cannot detach a JDK FileDescriptor for PdfProcessor
// (getOwnerId$ is Android-only). Isolate that native bridge; retain the real
// worker, HTTP/PDF download, PdfRendererHelper, bitmaps, file validation and Room.
// Public chapter PDF contents are separately verified by LiveSpyFamilyCheck/Poppler.
@Implements(PdfRenderer::class)
class WorkerPdfRendererShadow {
    companion object { var constructorCalls = 0 }
    @Implementation fun __constructor__(descriptor: ParcelFileDescriptor) { constructorCalls++ }
    @Implementation fun getPageCount(): Int = 1
    @Implementation fun openPage(index: Int): PdfRenderer.Page = Shadow.newInstanceOf(PdfRenderer.Page::class.java)
    @Implementation fun close() {}
}

@Implements(PdfRenderer.Page::class)
class WorkerPdfPageShadow {
    @Implementation fun getWidth(): Int = 200
    @Implementation fun getHeight(): Int = 300
    @Implementation fun render(bitmap: Bitmap, clip: Rect?, matrix: Matrix?, mode: Int) { bitmap.eraseColor(android.graphics.Color.BLUE) }
    @Implementation fun close() {}
}
