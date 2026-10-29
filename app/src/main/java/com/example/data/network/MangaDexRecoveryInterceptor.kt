package com.example.data.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.source
import okio.buffer
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.CertificateException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException

/** One native HTTPS recovery for public reads, before any response has been delivered. */
internal class MangaDexRecoveryInterceptor(
    private val openNative: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
    private val now: () -> Long = System::nanoTime
) : Interceptor {
    private val nativeUnavailableUntil = AtomicLong(Long.MIN_VALUE)
    private val requestAttempts = object : LinkedHashMap<String, Long>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > 256
    }

    private fun claimRequest(url: String, time: Long): Boolean = synchronized(requestAttempts) {
        if (time < nativeUnavailableUntil.get() || time < (requestAttempts[url] ?: Long.MIN_VALUE)) false
        else { requestAttempts[url] = time + TimeUnit.SECONDS.toNanos(30); true }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        try { return chain.proceed(chain.request()) }
        catch (primary: IOException) {
            val request = chain.request()
            if (request.method != "GET" || request.url.scheme != "https" || chain.call().isCanceled() || !eligible(primary)) throw primary
            val time = now()
            // Successful recovery of one endpoint must not suppress covers, feeds or another query.
            if (!claimRequest(request.url.toString(), time)) throw primary
            val connection = try { openNative(request.url.toUrl()) }
            catch (secondary: IOException) {
                nativeUnavailableUntil.set(now() + TimeUnit.SECONDS.toNanos(30))
                throw combined(primary, secondary)
            }
            val started = System.nanoTime()
            val guard = watchdog.scheduleAtFixedRate({
                if (chain.call().isCanceled() || System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(20)) connection.disconnect()
            }, 100, 100, TimeUnit.MILLISECONDS)
            val deadline = watchdog.schedule({ connection.disconnect(); guard.cancel(false) }, 20, TimeUnit.SECONDS)
            try {
                if (chain.call().isCanceled()) throw IOException("Canceled")
                connection.apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = false
                    useCaches = false
                    requestMethod = "GET"
                    for (name in request.headers.names()) {
                        if (name.lowercase() !in setOf("host", "connection", "accept-encoding", "content-length")) {
                            setRequestProperty(name, request.headers.values(name).joinToString(", "))
                        }
                    }
                    setRequestProperty("Accept-Encoding", "identity")
                    if (request.header("User-Agent") == null) setRequestProperty("User-Agent", MangaDexConnectionDiagnostic.USER_AGENT)
                }
                val code = connection.responseCode
                if (code in 200..299) synchronized(requestAttempts) {
                    requestAttempts.remove(request.url.toString())
                }
                if (code in listOf(403, 429)) nativeUnavailableUntil.set(now() + TimeUnit.SECONDS.toNanos(30))
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val source = object : okio.ForwardingSource((stream ?: java.io.ByteArrayInputStream(byteArrayOf())).source()) {
                    override fun close() {
                        try { super.close() } finally { guard.cancel(false); deadline.cancel(false); connection.disconnect() }
                    }
                }.buffer()
                val body = object : ResponseBody() {
                    override fun contentType() = connection.contentType?.toMediaTypeOrNull()
                    override fun contentLength() = connection.getHeaderFieldLong("Content-Length", -1)
                    override fun source() = source
                    override fun close() {
                        try { source.close() } finally { guard.cancel(false); deadline.cancel(false); connection.disconnect() }
                    }
                }
                val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(code).message(connection.responseMessage.orEmpty()).body(body)
                connection.headerFields.forEach { (name, values) ->
                    if (name != null) values.forEach { builder.addHeader(name, it) }
                }
                return builder.build()
            } catch (secondary: Exception) {
                guard.cancel(false)
                deadline.cancel(false)
                connection.disconnect()
                if (secondary is IOException) {
                    if (!chain.call().isCanceled()) nativeUnavailableUntil.set(now() + TimeUnit.SECONDS.toNanos(30))
                    throw combined(primary, secondary)
                }
                throw secondary
            }
        }
    }

    private fun eligible(error: IOException): Boolean {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(16).toList()
        if (causes.any { it is CertificateException || it is SSLPeerUnverifiedException }) return false
        // Do not reinterpret certificate validation failures as protocol incompatibility.
        if (causes.any { it is SSLHandshakeException } && causes.none {
                it is SSLProtocolException || it is SocketException || it is SocketTimeoutException
            }) return false
        return causes.any { it is SocketException || it is SocketTimeoutException || it is SSLProtocolException }
    }

    private fun combined(primary: IOException, secondary: IOException) = IOException(
        "Falha no OkHttp e na recuperação por HTTPS nativo do Android.\n" +
            "Primeira tentativa: ${com.example.source.catalogFailureDetails(primary)}\n" +
            "Segunda tentativa: ${com.example.source.catalogFailureDetails(secondary)}", secondary
    ).apply { addSuppressed(primary) }

    companion object {
        private val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "MangaDex-recovery-cancellation").apply { isDaemon = true }
        }
    }
}
