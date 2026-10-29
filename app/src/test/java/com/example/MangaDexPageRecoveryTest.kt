package com.example

import com.example.data.network.MangaDexPageInterceptor
import com.example.data.network.MangaDexPageRegistry
import com.example.data.network.model.MangaDexAtHomeChapterDto
import com.example.data.network.model.MangaDexAtHomeResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class MangaDexPageRecoveryTest {
    private fun manifest(server: MockWebServer, count: Int = 3) = MangaDexAtHomeResponse("ok", server.url("/").toString(),
        MangaDexAtHomeChapterDto("hash", (1..count).map { "$it.png" }, (1..count).map { "$it.jpg" }))

    @Test fun `missing second page uses the second compact page without shifting chapter order`() {
        val server = MockWebServer().apply { start() }
        var refreshes = 0
        val registry = MangaDexPageRegistry(load = { refreshes++; manifest(server) })
        val pages = registry.register("chapter-471", manifest(server))
        val client = OkHttpClient.Builder().addInterceptor(MangaDexPageInterceptor(registry)).build()
        try {
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("second page"))
            val request = Request.Builder().url(pages[1]).header("User-Agent", "MangaReaderApp/1.1.0").build()
            client.newCall(request).execute().use { assertEquals(200, it.code); assertEquals("second page", it.body!!.string()) }
            assertEquals("/data/hash/2.png", server.takeRequest().path)
            val compact = server.takeRequest()
            assertEquals("/data-saver/hash/2.jpg", compact.path)
            assertEquals("MangaReaderApp/1.1.0", compact.getHeader("User-Agent"))
            assertEquals(1, refreshes)
        } finally { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun `expired node is refreshed before retrying the same page on the current server`() {
        val old = MockWebServer().apply { start() }
        val current = MockWebServer().apply { start() }
        val registry = MangaDexPageRegistry(load = { manifest(current) })
        val pages = registry.register("chapter", manifest(old))
        val client = OkHttpClient.Builder().addInterceptor(MangaDexPageInterceptor(registry)).build()
        try {
            old.enqueue(MockResponse().setResponseCode(503))
            current.enqueue(MockResponse().setBody("third page"))
            client.newCall(Request.Builder().url(pages[2]).build()).execute().use {
                assertEquals("third page", it.body!!.string())
            }
            assertEquals("/data/hash/3.png", current.takeRequest().path)
            assertEquals(1, old.requestCount)
            assertEquals(1, current.requestCount)
        } finally { old.shutdown(); current.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun `certificate failures never refresh the manifest or switch image routes`() {
        val server = MockWebServer().apply { start() }
        val registry = MangaDexPageRegistry(load = { throw AssertionError("Unexpected refresh") })
        val pages = registry.register("certificate-test", manifest(server))
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor(MangaDexPageInterceptor(registry))
            .addInterceptor { attempts.incrementAndGet(); throw javax.net.ssl.SSLPeerUnverifiedException("Untrusted") }.build()
        try {
            assertThrows(javax.net.ssl.SSLPeerUnverifiedException::class.java) {
                client.newCall(Request.Builder().url(pages[1]).build()).execute().close()
            }
            assertEquals(1, attempts.get())
        } finally { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun `denial and rate limit never refresh or retry page routes`() {
        val server = MockWebServer().apply { start() }
        val registry = MangaDexPageRegistry(load = { throw AssertionError("Unexpected refresh") })
        val pages = registry.register("chapter", manifest(server))
        val client = OkHttpClient.Builder().addInterceptor(MangaDexPageInterceptor(registry)).build()
        try {
            for (code in listOf(403, 429)) {
                server.enqueue(MockResponse().setResponseCode(code))
                client.newCall(Request.Builder().url(pages[1]).build()).execute().use { assertEquals(code, it.code) }
            }
            assertEquals(2, server.requestCount)
        } finally { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun `changed page count cannot map an old index to a different chapter layout`() {
        val server = MockWebServer().apply { start() }
        val registry = MangaDexPageRegistry(load = { manifest(server, 4) })
        val pages = registry.register("chapter", manifest(server, 3))
        val client = OkHttpClient.Builder().addInterceptor(MangaDexPageInterceptor(registry)).build()
        try {
            server.enqueue(MockResponse().setResponseCode(404))
            client.newCall(Request.Builder().url(pages[1]).build()).execute().use { assertEquals(404, it.code) }
            server.enqueue(MockResponse().setResponseCode(404))
            client.newCall(Request.Builder().url(pages[1]).build()).execute().use { assertEquals(404, it.code) }
            assertEquals(2, server.requestCount)
        } finally { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun `metadata denial stops refreshes across other failing pages`() = kotlinx.coroutines.runBlocking {
        val server = MockWebServer().apply { start() }
        var refreshes = 0
        val registry = MangaDexPageRegistry(load = {
            refreshes++
            throw retrofit2.HttpException(retrofit2.Response.error<Any>(429,
                okhttp3.ResponseBody.create(null, "limited")))
        })
        try {
            val pages = registry.register("chapter", manifest(server))
            try { registry.alternatives(pages[0]); fail("Expected metadata denial") }
            catch (_: retrofit2.HttpException) { }
            assertTrue(registry.alternatives(pages[1]).isEmpty())
            assertTrue(registry.alternatives(pages[2]).isEmpty())
            assertEquals(1, refreshes)
        } finally { server.shutdown() }
    }

}
