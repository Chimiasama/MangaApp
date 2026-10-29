package com.example.data.network

import com.example.data.network.model.MangaDexAtHomeResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/** Keeps page indices tied to their chapter, including URLs from an older At-Home lease. */
class MangaDexPageRegistry(
    private val load: suspend (String) -> MangaDexAtHomeResponse,
    private val now: () -> Long = System::nanoTime
) {
    private data class Page(val chapter: String, val index: Int, val count: Int)
    private data class Manifest(val response: MangaDexAtHomeResponse, val refreshed: Long)
    private val pages = object : LinkedHashMap<String, Page>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Page>) = size > 4096
    }
    private val manifests = object : LinkedHashMap<String, Manifest>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Manifest>) = size > 64
    }
    private val refreshGate = Mutex()
    private var unavailableUntil = Long.MIN_VALUE

    @Synchronized fun contains(url: String): Boolean = pages.containsKey(url)

    @Synchronized fun register(chapter: String, response: MangaDexAtHomeResponse, preserveExisting: Boolean = false): List<String> {
        val files = response.chapter?.data.orEmpty()
        if (response.result != "ok" || response.baseUrl.isNullOrBlank() || response.chapter?.hash.isNullOrBlank() || files.isEmpty()) {
            throw IOException("A fonte não retornou uma lista completa de páginas.")
        }
        val urls = files.map { url(response, "data", it) }
        urls.forEachIndexed { index, value ->
            if (!preserveExisting || value !in pages) pages[value] = Page(chapter, index, files.size)
        }
        val saver = response.chapter?.dataSaver.orEmpty()
        if (saver.size == files.size) saver.forEachIndexed { index, file ->
            val value = url(response, "data-saver", file)
            if (!preserveExisting || value !in pages) pages[value] = Page(chapter, index, files.size)
        }
        val hash = response.chapter?.hash.orEmpty()
        if (hash.isNotBlank()) {
            files.forEachIndexed { index, file ->
                val central = "https://uploads.mangadex.org/data/$hash/$file"
                if (!preserveExisting || central !in pages) pages[central] = Page(chapter, index, files.size)
            }
            if (saver.size == files.size) {
                saver.forEachIndexed { index, file ->
                    val centralSaver = "https://uploads.mangadex.org/data-saver/$hash/$file"
                    if (!preserveExisting || centralSaver !in pages) pages[centralSaver] = Page(chapter, index, files.size)
                }
            }
        }
        manifests[chapter] = Manifest(response, Long.MIN_VALUE)
        return urls
    }

    @Synchronized fun invalidate(url: String) {
        val chapter = pages[url]?.chapter ?: return
        manifests[chapter]?.let { manifests[chapter] = it.copy(refreshed = Long.MIN_VALUE) }
    }

    suspend fun alternatives(original: String): List<String> = refreshGate.withLock {
        val page = synchronized(this) { pages[original] } ?: return@withLock emptyList()
        var manifest = synchronized(this) { manifests[page.chapter] }
        val time = now()
        if (time < unavailableUntil) return@withLock emptyList()
        if (manifest == null || manifest.refreshed == Long.MIN_VALUE || time - manifest.refreshed > 10_000_000_000L) {
            val response = try { load(page.chapter) } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val code = (error as? retrofit2.HttpException)?.code()
                unavailableUntil = time + if (code in listOf(403, 429)) 60_000_000_000L else 10_000_000_000L
                throw error
            }
            synchronized(this) {
                register(page.chapter, response, preserveExisting = true)
                manifests[page.chapter] = Manifest(response, time)
            }
            manifest = Manifest(response, time)
        }
        val response = manifest.response
        val files = response.chapter?.data.orEmpty()
        // Never shift pages if the source changed the chapter's page count.
        if (files.size != page.count || page.index !in files.indices) return@withLock emptyList()
        val hash = response.chapter?.hash.orEmpty()
        val fileName = files.getOrNull(page.index).orEmpty()
        buildList {
            add(url(response, "data", files[page.index]))
            val saver = response.chapter?.dataSaver.orEmpty()
            if (saver.size == files.size) add(url(response, "data-saver", saver[page.index]))
            if (hash.isNotBlank() && fileName.isNotBlank()) {
                add("https://uploads.mangadex.org/data/$hash/$fileName")
                if (saver.size == files.size) {
                    add("https://uploads.mangadex.org/data-saver/$hash/${saver[page.index]}")
                }
            }
        }.distinct().filter { it != original }
    }

    private fun url(response: MangaDexAtHomeResponse, mode: String, file: String) =
        "${response.baseUrl!!.trimEnd('/')}/$mode/${response.chapter!!.hash}/$file"
}

object MangaDexPages {
    val registry = MangaDexPageRegistry({ RetrofitClient.mangaDexApiService.getAtHomeServer(it) })
}

/** Only retries known chapter pages, never an HTTP denial or an untrusted certificate. */
class MangaDexPageInterceptor(private val registry: MangaDexPageRegistry = MangaDexPages.registry) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" || !registry.contains(request.url.toString())) return chain.proceed(request)
        var response: Response? = null
        var failure: IOException? = null
        try {
            response = chain.proceed(request)
            if (!recoverable(response.code)) return response
        } catch (error: IOException) {
            if (!recoverable(error) || chain.call().isCanceled()) throw error
            failure = error
        }
        val alternatives = try { runBlocking {
            val job = coroutineContext[Job]!!
            val cancellation = launch {
                while (isActive) {
                    if (chain.call().isCanceled()) { job.cancel(); break }
                    delay(100)
                }
            }
            try { registry.alternatives(request.url.toString()) } finally { cancellation.cancel() }
        } }
        catch (error: Exception) {
            // Keep an existing HTTP response intact when refreshing the lease itself fails.
            if (response != null) return response
            throw IOException("Não foi possível atualizar o servidor das páginas.", error).apply { failure?.let(::addSuppressed) }
        }
        if (alternatives.isEmpty()) return response ?: throw failure!!
        for (url in alternatives.take(4)) {
            if (chain.call().isCanceled()) { response?.close(); throw IOException("Canceled") }
            response?.close()
            response = null
            try {
                response = chain.proceed(request.newBuilder().url(url).build())
                if (!recoverable(response.code)) return response
            } catch (error: IOException) {
                if (!recoverable(error) || chain.call().isCanceled()) throw error
                failure = error
            }
        }
        return response ?: throw failure!!
    }

    private fun recoverable(code: Int) = code == 404 || code == 410 || code in 500..599
    private fun recoverable(error: IOException): Boolean {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(16).toList()
        return causes.any {
            it is SocketException || it is SocketTimeoutException || it is javax.net.ssl.SSLException ||
            it is CertificateException || it is SSLPeerUnverifiedException || it is java.net.UnknownHostException ||
            it is java.net.ConnectException
        }
    }
}
