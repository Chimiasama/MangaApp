package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.source.SourceHttp
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.sqrt

/** PDF bytes are cached once; only requested pages are rasterized, with bounded memory. */
class PdfChapterStore(private val context: Context,
    private val client: OkHttpClient = SourceHttp.client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()) {
    private val directory get() = File(context.cacheDir, "pdf_chapters").apply { mkdirs() }

    suspend fun open(url: String): List<String> = withContext(Dispatchers.IO) {
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        locks.getOrPut(key) { Mutex() }.withLock {
            val target = File(directory, "$key.pdf")
            if (target.exists()) {
                try { return@withLock pages(target) } catch (_: IOException) { target.delete() }
            }
            val temporary = File.createTempFile(key, ".part", directory)
            val call = client.newCall(Request.Builder().url(url).build())
            val job = currentCoroutineContext()[kotlinx.coroutines.Job]!!
            // Cancellation closes a blocking network read as well as stopping the copy loop.
            val watcher = kotlinx.coroutines.CoroutineScope(currentCoroutineContext()).launchCancellationWatch(call)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Não foi possível baixar o PDF (HTTP ${response.code}).")
                    val body = response.body ?: throw IOException("PDF vazio.")
                    if (body.contentLength() > MAX_BYTES) throw IOException("PDF excede o limite de 256 MB.")
                    body.byteStream().use { input -> temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var size = 0L
                        while (true) {
                            job.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            if (size > MAX_BYTES) throw IOException("PDF excede o limite de 256 MB.")
                            output.write(buffer, 0, count)
                        }
                    } }
                }
                count(temporary) // Validate before publishing; HTML/error bodies never enter the cache.
                job.ensureActive()
                if (!temporary.renameTo(target)) throw IOException("Não foi possível salvar o PDF.")
                trim(target)
                pages(target)
            } catch (error: Exception) {
                job.ensureActive()
                throw error
            } finally { watcher.cancel(); temporary.delete() }
        }
    }

    suspend fun pages(file: File): List<String> = withContext(Dispatchers.IO) {
        val safe = requireFile(file)
        val size = count(safe)
        safe.setLastModified(System.currentTimeMillis())
        (0 until size).map { index -> Uri.Builder().scheme(SCHEME).authority("page")
            .appendPath(index.toString()).appendQueryParameter("file", safe.absolutePath).build().toString() }
    }

    fun file(uri: Uri): File {
        if (uri.scheme != SCHEME) throw IOException("Página PDF inválida.")
        return requireFile(File(uri.getQueryParameter("file") ?: throw IOException("PDF ausente.")))
    }

    private fun requireFile(file: File): File {
        val canonical = file.canonicalFile
        val roots = listOf(context.cacheDir.canonicalPath, context.filesDir.canonicalPath)
        if (roots.none { canonical.path.startsWith(it + File.separator) } || !canonical.isFile)
            throw IOException("PDF não está disponível no armazenamento do app. Reabra o capítulo.")
        return canonical
    }

    private fun count(file: File): Int {
        try {
            file.inputStream().use { if (String(it.readNBytesCompat(5), Charsets.US_ASCII) != "%PDF-") throw IOException("A fonte não entregou um PDF válido.") }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.pageCount.takeIf { it in 1..10000 } ?: throw IOException("PDF sem páginas válidas.")
                }
            }
        } catch (e: IOException) { throw e }
        catch (e: Exception) { throw IOException("Não foi possível abrir o PDF. O arquivo pode estar danificado ou protegido por senha.", e) }
    }

    suspend fun render(uri: Uri, requestedWidth: Int = 1536): Bitmap = withContext(Dispatchers.IO) {
        rendering.withPermit {
            currentCoroutineContext().ensureActive()
            val pdf = file(uri)
            val index = uri.lastPathSegment?.toIntOrNull() ?: throw IOException("Página PDF inválida.")
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (index !in 0 until renderer.pageCount) throw IOException("Página fora do PDF.")
                    renderer.openPage(index).use { page ->
                        val width = requestedWidth.coerceIn(1024, 3072)
                        val scale = min(width.toDouble() / page.width,
                            sqrt(8_000_000.0 / (page.width.toDouble() * page.height)))
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            currentCoroutineContext().ensureActive()
                            pdf.setLastModified(System.currentTimeMillis())
                            bitmap
                        } catch (e: Throwable) { bitmap.recycle(); throw e }
                    }
                }
            }
        }
    }

    private fun trim(keep: File) {
        val files = directory.listFiles()?.filter { it.extension == "pdf" }?.sortedBy { it.lastModified() }.orEmpty()
        var bytes = files.sumOf { it.length() }
        for (file in files) if (bytes > 512L * 1024 * 1024 && file != keep) { bytes -= file.length(); file.delete() }
    }

    companion object {
        const val SCHEME = "mangapdf"
        private const val MAX_BYTES = 256L * 1024 * 1024
        private val locks = ConcurrentHashMap<String, Mutex>()
        private val rendering = Semaphore(2)
    }
}

private fun java.io.InputStream.readNBytesCompat(size: Int): ByteArray {
    val bytes = ByteArray(size)
    var count = 0
    while (count < size) { val read = read(bytes, count, size - count); if (read < 0) break; count += read }
    return bytes.copyOf(count)
}

private fun kotlinx.coroutines.CoroutineScope.launchCancellationWatch(call: okhttp3.Call) =
    launch(context = Dispatchers.Default, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
        try { kotlinx.coroutines.awaitCancellation() } finally { call.cancel() }
    }
