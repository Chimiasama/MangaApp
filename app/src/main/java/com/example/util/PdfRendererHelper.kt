package com.example.util

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfRendererHelper {

    /**
     * Renders all pages of a PDF file located at [pdfFile] into PNG image files stored in [outputDir].
     * Returns a list of file paths (e.g. "file:///path/to/page_1.png") ready to be loaded by Coil / Image reader.
     */
    suspend fun renderPdfPagesToImages(
        pdfFile: File,
        outputDir: File
    ): List<String> = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return@withContext emptyList()
        }

        outputDir.mkdirs()
        val pagePaths = mutableListOf<String>()

        try {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            val pageCount = pdfRenderer.pageCount
            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)

                // High resolution scaling (2x scale for crisp reading text)
                val scale = 2
                val width = page.width * scale
                val height = page.height * scale

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Fill white background for transparent PDF pages
                bitmap.eraseColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val imageFile = File(outputDir, "page_${i + 1}.png")
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()

                pagePaths.add(imageFile.absolutePath)
            }

            pdfRenderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext pagePaths
    }
}
