package com.example

import com.example.data.network.MangaDexCallFactory
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

class MangaDexRecoveryTest {
    private val cert = HeldCertificate.Builder().commonName("Recovery")
        .addSubjectAlternativeName("api.mangadex.org").addSubjectAlternativeName("uploads.mangadex.org")
        .addSubjectAlternativeName("localhost").addSubjectAlternativeName("node.mangadex.network").build()
    private val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
    private val trusted = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
    private fun client() = OkHttpClient.Builder().dns(object : Dns {
        override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
    }).sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager).retryOnConnectionFailure(false).build()

    @Test fun `reset recovers API and covers with native HTTPS preserving filters and identity`() {
        for (host in listOf("api.mangadex.org", "uploads.mangadex.org", "node.mangadex.network")) {
            val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
            val base = client().newBuilder().addNetworkInterceptor { throw SocketException("Connection reset") }.build()
            val attempts = AtomicInteger()
            try {
                server.enqueue(MockResponse().setBody("native content").setHeader("X-Request-ID", "native-id"))
                val factory = MangaDexCallFactory(base, nativeConnection = { url ->
                    attempts.incrementAndGet()
                    (server.url(url.file).toUrl().openConnection(Proxy.NO_PROXY) as HttpsURLConnection)
                        .apply { sslSocketFactory = trusted.sslSocketFactory() }
                })
                val pageUrl = if (host == "node.mangadex.network") {
                    com.example.data.network.MangaDexPages.registry.register("test-${server.port}",
                        com.example.data.network.model.MangaDexAtHomeResponse("ok", "https://$host:${server.port}",
                            com.example.data.network.model.MangaDexAtHomeChapterDto("hash", listOf("1.png", "2.png"), listOf("1.jpg", "2.jpg")))).first()
                } else "https://$host:${server.port}/manga?year=1935&availableTranslatedLanguage%5B%5D=pt-br"
                val request = Request.Builder().url(pageUrl)
                    .header("User-Agent", "MangaReaderApp/1.1.0").build()
                factory.newCall(request).execute().use {
                    assertEquals(200, it.code); assertEquals("native content", it.body!!.string())
                    assertEquals("native-id", it.header("X-Request-ID"))
                }
                assertEquals(1, attempts.get())
                val received = server.takeRequest()
                assertEquals(request.url.encodedPath + request.url.encodedQuery?.let { "?$it" }.orEmpty(), received.path)
                assertEquals("MangaReaderApp/1.1.0", received.getHeader("User-Agent"))
                if (host == "node.mangadex.network") {
                    // A recovered page must not prevent the next distinct page from recovering.
                    server.enqueue(MockResponse().setBody("second native page"))
                    val next = request.newBuilder().url(pageUrl.replace("1.png", "2.png")).build()
                    factory.newCall(next).execute().use { assertEquals("second native page", it.body!!.string()) }
                    assertEquals(2, attempts.get())
                } else {
                    server.enqueue(MockResponse().setBody("repeated native content"))
                    factory.newCall(request).execute().use { assertEquals("repeated native content", it.body!!.string()) }
                    assertEquals(2, attempts.get())
                }
            } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
        }
    }

    @Test fun `each cover can recover without sharing the API cooldown`() {
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val base = client().newBuilder().addNetworkInterceptor { throw SocketException("reset") }.build()
        val attempts = AtomicInteger()
        try {
            val factory = MangaDexCallFactory(base, nativeConnection = { url ->
                attempts.incrementAndGet()
                (server.url(url.file).toUrl().openConnection(Proxy.NO_PROXY) as HttpsURLConnection)
                    .apply { sslSocketFactory = trusted.sslSocketFactory() }
            })
            for (path in listOf("/covers/one/a.jpg.256.jpg", "/covers/two/b.png.256.jpg")) {
                server.enqueue(MockResponse().setBody("cover"))
                val request = Request.Builder().url("https://uploads.mangadex.org:${server.port}$path").build()
                factory.newCall(request).execute().use { assertEquals("cover", it.body!!.string()) }
                server.enqueue(MockResponse().setBody("same cover retried"))
                factory.newCall(request).execute().use { assertEquals("same cover retried", it.body!!.string()) }
            }
            assertEquals(4, attempts.get())
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `catalog recovery does not block details or chapter feeds on the same client`() {
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val base = client().newBuilder().addNetworkInterceptor { throw SocketException("reset") }.build()
        val attempts = AtomicInteger()
        try {
            val factory = MangaDexCallFactory(base, nativeConnection = { url ->
                attempts.incrementAndGet()
                (server.url(url.file).toUrl().openConnection(Proxy.NO_PROXY) as HttpsURLConnection)
                    .apply { sslSocketFactory = trusted.sslSocketFactory() }
            })
            for (path in listOf("/manga?limit=50", "/manga/id", "/manga/id/feed?limit=100")) {
                server.enqueue(MockResponse().setBody("valid data"))
                val request = Request.Builder().url("https://api.mangadex.org:${server.port}$path").build()
                factory.newCall(request).execute().use { assertEquals("valid data", it.body!!.string()) }
            }
            assertEquals(3, attempts.get())
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `HTTP denial rate limit and server errors never trigger another client`() {
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val base = client()
        try {
            val factory = MangaDexCallFactory(base, nativeConnection = { throw AssertionError("Unexpected retry") })
            for (code in listOf(403, 429, 503)) {
                server.enqueue(MockResponse().setResponseCode(code))
                val request = Request.Builder().url("https://api.mangadex.org:${server.port}/manga").build()
                factory.newCall(request).execute().use { assertEquals(code, it.code) }
            }
            assertEquals(3, server.requestCount)
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `untrusted certificates never trigger recovery`() {
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val base = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
        }).retryOnConnectionFailure(false).build()
        try {
            val factory = MangaDexCallFactory(base, nativeConnection = { throw AssertionError("Unexpected retry") })
            assertThrows(IOException::class.java) {
                factory.newCall(Request.Builder().url("https://api.mangadex.org:${server.port}/manga").build()).execute().close()
            }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }
    @Test fun `two transport failures retain both causes and attempt native only once`() {
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val base = client().newBuilder().addNetworkInterceptor { throw SocketException("primary reset") }.build()
        val attempts = AtomicInteger()
        try {
            val factory = MangaDexCallFactory(base, nativeConnection = {
                attempts.incrementAndGet(); throw SocketException("native reset")
            })
            val error = assertThrows(IOException::class.java) {
                factory.newCall(Request.Builder().url("https://api.mangadex.org:${server.port}/manga").build()).execute().close()
            }
            assertTrue(error.message!!.contains("primary reset"))
            assertTrue(error.message!!.contains("native reset"))
            assertEquals(1, attempts.get())
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `POST and cancellations never open native connections`() {
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val base = client().newBuilder().addNetworkInterceptor { throw SocketException("reset") }.build()
        try {
            val factory = MangaDexCallFactory(base, nativeConnection = { throw AssertionError("Unexpected retry") })
            val get = Request.Builder().url("https://api.mangadex.org:${server.port}/manga").build()
            val canceled = factory.newCall(get).apply { cancel() }
            assertThrows(IOException::class.java) { canceled.execute().close() }
            val post = get.newBuilder().method("POST", okhttp3.RequestBody.create(null, "payload")).build()
            assertThrows(IOException::class.java) { factory.newCall(post).execute().close() }
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `cancel disconnects an in flight native recovery`() {
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val base = client().newBuilder().addNetworkInterceptor { throw SocketException("reset") }.build()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
            val factory = MangaDexCallFactory(base, nativeConnection = { url ->
                (server.url(url.file).toUrl().openConnection(Proxy.NO_PROXY) as HttpsURLConnection)
                    .apply { sslSocketFactory = trusted.sslSocketFactory() }
            })
            val call = factory.newCall(Request.Builder().url("https://api.mangadex.org:${server.port}/manga").build())
            val future = executor.submit<Boolean> {
                try { call.execute().close(); false } catch (_: IOException) { true }
            }
            assertNotNull(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS))
            call.cancel()
            assertTrue(future.get(8, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(call.isCanceled())
            assertEquals(1, server.requestCount)
        } finally {
            executor.shutdownNow(); server.shutdown()
            base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll()
        }
    }

}
