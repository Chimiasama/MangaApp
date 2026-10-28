package com.example.util

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.sqrt

object PdfRendererHelper {
    /** Render public chapter PDFs into local image URIs usable by the existing reader. */
    suspend fun renderPdfPagesToImages(pdfFile: File, outputDir: File): List<String> = withContext(Dispatchers.IO) {
        if (!pdfFile.isFile || pdfFile.length() == 0L) throw IOException("Arquivo PDF ausente ou vazio.")
        if (!outputDir.isDirectory && !outputDir.mkdirs()) throw IOException("Não foi possível criar o cache de páginas.")
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) throw IOException("O PDF não contém páginas.")
                val files = (1..renderer.pageCount).map { File(outputDir, "page_$it.png") }
                val complete = File(outputDir, "complete.txt")
                if (complete.takeIf { it.exists() }?.readText() == renderer.pageCount.toString() && files.all { it.length() > 0 })
                    return@withContext files.map { Uri.fromFile(it).toString() }
                complete.delete()
                for (index in files.indices) {
                    coroutineContext.ensureActive()
                    renderer.openPage(index).use { page ->
                        val scale = min(2.0, min(2048.0 / page.width.coerceAtLeast(1),
                            sqrt(8_000_000.0 / (page.width.toDouble() * page.height).coerceAtLeast(1.0))))
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            coroutineContext.ensureActive()
                            files[index].outputStream().use { output ->
                                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Falha ao converter página do PDF.")
                            }
                        } finally { bitmap.recycle() }
                    }
                }
                complete.writeText(renderer.pageCount.toString())
                files.map { Uri.fromFile(it).toString() }
            }
        }
    }
}
