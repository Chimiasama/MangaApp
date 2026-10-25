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
    const val USER_AGENT = "Mozilla/5.0 MangaApp/1.1"
    val imageHeaders = Interceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
    }
    val client = OkHttpClient.Builder().addInterceptor(imageHeaders)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()

    private val gates = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private val lastRequest = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun get(url: String): String {
        val host = java.net.URI(url).host ?: throw IOException("URL de fonte inválida")
        val gate = gates.getOrPut(host) { kotlinx.coroutines.sync.Mutex() }
        gate.lock()
        try {
            val elapsed = (System.nanoTime() - (lastRequest[host] ?: 0L)) / 1_000_000
            if (elapsed < 1500) kotlinx.coroutines.delay(1500 - elapsed)
            lastRequest[host] = System.nanoTime()
            return request(url)
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
                        if (!it.isSuccessful) throw IOException("Fonte respondeu HTTP ${it.code}")
                        it.body?.string() ?: throw IOException("Resposta vazia da fonte")
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
