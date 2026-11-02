package com.example

import com.example.data.network.MangaDexApiConfig
import com.example.data.network.MangaDexEndpointFallbackInterceptor
import com.example.data.network.MangaDexEndpointManager
import com.example.data.network.MangaDexHeaderInterceptor
import com.example.data.network.MangaDexWafInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class MangaDexEndpointFallbackTest {

    @Before
    fun setUp() {
        MangaDexEndpointManager.reset()
    }

    @After
    fun tearDown() {
        MangaDexEndpointManager.reset()
    }

    @Test
    fun `header interceptor adds Referer and Accept-Language for mangadex requests`() {
        var observedReferer: String? = null
        var observedAcceptLang: String? = null
        var observedUa: String? = null

        val mockTransport = Interceptor { chain ->
            val req = chain.request()
            observedReferer = req.header("Referer")
            observedAcceptLang = req.header("Accept-Language")
            observedUa = req.header("User-Agent")
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexHeaderInterceptor())
            .addInterceptor(mockTransport)
            .build()

        val request = Request.Builder()
            .url("https://api.mangadex.org/manga?limit=5")
            .build()

        client.newCall(request).execute().use { res ->
            assertEquals(200, res.code)
            assertEquals("https://mangadex.org/", observedReferer)
            assertEquals("pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7", observedAcceptLang)
            assertEquals(MangaDexApiConfig.USER_AGENT, observedUa)
        }
    }

    @Test
    fun `fallback interceptor transparently fails over to api mangadex dev when primary returns 403 WAF challenge`() {
        val requestedHosts = mutableListOf<String>()

        val mockBackend = Interceptor { chain ->
            val req = chain.request()
            requestedHosts.add(req.url.host)

            if (req.url.host == MangaDexEndpointManager.PRIMARY_HOST) {
                // Primary blocked by WAF challenge
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("Forbidden")
                    .header("X-MangaDex-WAF-Challenge", "true")
                    .body("""{"result":"error"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            } else if (req.url.host == MangaDexEndpointManager.FALLBACK_HOST) {
                // Secondary succeeds
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"result":"ok","data":[]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            } else {
                throw IOException("Unexpected host: ${req.url.host}")
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexEndpointFallbackInterceptor(MangaDexEndpointManager))
            .addInterceptor(mockBackend)
            .build()

        val request = Request.Builder()
            .url("https://api.mangadex.org/manga?limit=5")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("""{"result":"ok","data":[]}""", response.body!!.string())
            assertEquals(listOf("api.mangadex.org", "api.mangadex.dev"), requestedHosts)
            assertTrue(MangaDexEndpointManager.isFallbackActive())
        }
    }

    @Test
    fun `fallback interceptor recovers on network SocketTimeoutException by retrying with api mangadex dev`() {
        val requestedHosts = mutableListOf<String>()

        val mockBackend = Interceptor { chain ->
            val req = chain.request()
            requestedHosts.add(req.url.host)

            if (req.url.host == MangaDexEndpointManager.PRIMARY_HOST) {
                throw SocketTimeoutException("Connection timed out to api.mangadex.org")
            } else if (req.url.host == MangaDexEndpointManager.FALLBACK_HOST) {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"result":"ok","data":[{"id":"m1"}]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            } else {
                throw IOException("Unexpected host: ${req.url.host}")
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexEndpointFallbackInterceptor(MangaDexEndpointManager))
            .addInterceptor(mockBackend)
            .build()

        val request = Request.Builder()
            .url("https://api.mangadex.org/manga/m1")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("""{"result":"ok","data":[{"id":"m1"}]}""", response.body!!.string())
            assertEquals(listOf("api.mangadex.org", "api.mangadex.dev"), requestedHosts)
            assertTrue(MangaDexEndpointManager.isFallbackActive())
        }
    }

    @Test
    fun `subsequent requests use fallback endpoint directly while active without latency penalty`() {
        MangaDexEndpointManager.markPrimaryFailure("Cloudflare WAF")
        assertTrue(MangaDexEndpointManager.isFallbackActive())

        val requestedHosts = mutableListOf<String>()
        val mockBackend = Interceptor { chain ->
            val req = chain.request()
            requestedHosts.add(req.url.host)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"result":"ok"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexEndpointFallbackInterceptor(MangaDexEndpointManager))
            .addInterceptor(mockBackend)
            .build()

        val request = Request.Builder()
            .url("https://api.mangadex.org/manga/123/feed")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            // Should route directly to fallback host without hitting primary
            assertEquals(listOf("api.mangadex.dev"), requestedHosts)
        }
    }

    @Test
    fun `at-home chapter allocation endpoints are never redirected to api mangadex dev`() {
        MangaDexEndpointManager.markPrimaryFailure("Temporary outage")
        assertTrue(MangaDexEndpointManager.isFallbackActive())

        val requestedHosts = mutableListOf<String>()
        val mockBackend = Interceptor { chain ->
            val req = chain.request()
            requestedHosts.add(req.url.host)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"result":"ok","baseUrl":"https://uploads.mangadex.org"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexEndpointFallbackInterceptor(MangaDexEndpointManager))
            .addInterceptor(mockBackend)
            .build()

        val request = Request.Builder()
            .url("https://api.mangadex.org/at-home/server/chap-12345")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            // /at-home/ MUST remain on api.mangadex.org even when fallback is active
            assertEquals(listOf("api.mangadex.org"), requestedHosts)
        }
    }
}
