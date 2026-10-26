package com.example.source

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The same identification is used for documents, covers, reader images and downloads. */
object SourceHttp {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36 MangaApp/1.1"
    private val imageReferers = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun rememberImages(pages: List<String>, chapter: String) {
        val origin = WebUrls.origin(chapter) + "/"
        pages.mapNotNull { runCatching { java.net.URI(it).host }.getOrNull() }.distinct().forEach {
            if (imageReferers.size < 200 || imageReferers.containsKey(it)) imageReferers[it] = origin
        }
    }
    // CookieJar is consulted for each destination, including redirects: no cross-host cookie forwarding.
    val browserCookies = object : okhttp3.CookieJar {
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) = Unit
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            if (!url.isHttps || !imageReferers.containsKey(url.host)) return emptyList()
            val raw = runCatching { android.webkit.CookieManager.getInstance().getCookie(url.toString()) }.getOrNull() ?: return emptyList()
            return raw.split(';').mapNotNull { pair ->
                val parts = pair.trim().split('=', limit = 2)
                if (parts.size != 2) null else runCatching {
                    okhttp3.Cookie.Builder().name(parts[0]).value(parts[1]).hostOnlyDomain(url.host).path("/").secure().build()
                }.getOrNull()
            }
        }
    }
    val imageHeaders = Interceptor { chain ->
        val request = chain.request().newBuilder().header("User-Agent", USER_AGENT)
        val host = chain.request().url.host
        if (host == "mangadash.net" || host.endsWith(".mangadash.net")) request.header("Referer", "https://mangadash.net/")
        imageReferers[host]?.let { request.header("Referer", it) }
        chain.proceed(request.build())
    }
    val client = OkHttpClient.Builder().cookieJar(browserCookies).addInterceptor(imageHeaders)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()

    private val gates = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private val lastRequest = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun get(url: String): String {
        val checked = WebUrls.valid(url)
        val host = java.net.URI(checked).host ?: throw IOException("URL de fonte inválida")
        val gate = gates.getOrPut(host) { kotlinx.coroutines.sync.Mutex() }
        gate.lock()
        try {
            val elapsed = (System.nanoTime() - (lastRequest[host] ?: 0L)) / 1_000_000
            if (elapsed < 1500) kotlinx.coroutines.delay(1500 - elapsed)
            lastRequest[host] = System.nanoTime()
            return request(checked)
        } finally { gate.unlock() }
    }

    private suspend fun request(url: String): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw SourceHttpException(it.code)
                        val body = it.body ?: throw IOException("Resposta vazia da fonte")
                        if (body.contentLength() > 8_000_000) throw IOException("A resposta da fonte excede 8 MB.")
                        body.byteStream().use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > 8_000_000) throw IOException("A resposta da fonte excede 8 MB.")
                                output.write(buffer, 0, count)
                            }
                            output.toString("UTF-8")
                        }
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
