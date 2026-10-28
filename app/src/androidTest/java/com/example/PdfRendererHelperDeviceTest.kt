package com.example

import android.graphics.BitmapFactory
import android.net.Uri
import com.example.util.PdfRendererHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfRendererHelperDeviceTest {
    @Test fun twoPagePdfRendersAndReusesCachedImages() = runBlocking {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-reader-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            val pdf = File(root, "chapter.pdf").apply {
                writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("mangadash/two-pages.pdf").readBytes())
            }
            val output = File(root, "pages")
            val pages = PdfRendererHelper.renderPdfPagesToImages(pdf, output)
            assertEquals(2, pages.size)
            val images = pages.map { File(Uri.parse(it).path!!) }
            assertTrue(images.all { it.isFile && BitmapFactory.decodeFile(it.path) != null })
            val modified = images.map { it.lastModified() }
            assertEquals(pages, PdfRendererHelper.renderPdfPagesToImages(pdf, output))
            assertEquals(modified, images.map { it.lastModified() })
        } finally { root.deleteRecursively() }
    }
}
