package com.example

import com.example.data.network.MangaDexApiConfig
import com.example.data.network.MangaDexHeaderInterceptor
import com.example.data.network.MangaDexWafInterceptor
import com.example.data.network.RetrofitClient
import com.example.data.network.model.MangaDexMangaListResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class MangaDexWafAndHeadersTest {

    @Test
    fun `header interceptor adds formatted User-Agent and Accept application json when missing`() {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexHeaderInterceptor())
            .build()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val request = Request.Builder().url(server.url("/manga")).build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }

            val recorded = server.takeRequest()
            assertEquals("MangaReader/1.1.0 (chimiasama@gmail.com)", recorded.getHeader("User-Agent"))
            assertEquals("application/json", recorded.getHeader("Accept"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `header interceptor replaces generic okhttp user agent with official format`() {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexHeaderInterceptor())
            .build()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val request = Request.Builder()
                .url(server.url("/manga"))
                .header("User-Agent", "okhttp/4.12.0")
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }

            val recorded = server.takeRequest()
            assertEquals(MangaDexApiConfig.USER_AGENT, recorded.getHeader("User-Agent"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `header interceptor preserves custom user agent if already set`() {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexHeaderInterceptor())
            .build()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val request = Request.Builder()
                .url(server.url("/manga"))
                .header("User-Agent", "CustomClient/2.0.0")
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }

            val recorded = server.takeRequest()
            assertEquals("CustomClient/2.0.0", recorded.getHeader("User-Agent"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `header interceptor sets image accept header for image requests`() {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexHeaderInterceptor())
            .build()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("image-bytes"))
            val request = Request.Builder()
                .url(server.url("/covers/123/cover.jpg"))
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }

            val recorded = server.takeRequest()
            val accept = recorded.getHeader("Accept").orEmpty()
            assertTrue(accept, accept.contains("image/"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `waf interceptor converts 403 html challenge to structured json avoiding moshi parse crash`() {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexWafInterceptor())
            .build()
        try {
            val htmlChallenge = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <title>Just a moment...</title>
                </head>
                <body>
                    <h1>Attention Required! | Cloudflare</h1>
                    <p>Please complete the security check to access api.mangadex.org</p>
                    <div id="cf-chl-widget"></div>
                    <p>Ray ID: 89ab12cd34ef56</p>
                </body>
                </html>
            """.trimIndent()

            server.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "text/html; charset=UTF-8")
                    .setHeader("cf-ray", "89ab12cd34ef56")
                    .setBody(htmlChallenge)
            )

            val request = Request.Builder().url(server.url("/manga")).build()
            client.newCall(request).execute().use { response ->
                assertEquals(403, response.code)
                assertEquals("application/json; charset=utf-8", response.header("Content-Type"))
                assertEquals("true", response.header("X-MangaDex-WAF-Challenge"))

                val bodyString = response.body!!.string()
                // Must be valid JSON parseable by Moshi without throwing MalformedJsonException
                val moshiAdapter = RetrofitClient.moshi.adapter(MangaDexMangaListResponse::class.java)
                val parsed = moshiAdapter.fromJson(bodyString)
                assertNotNull(parsed)
                assertEquals("error", parsed?.result)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `waf interceptor converts 200 html challenge into 403 error preventing moshi deserialization crash`() {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexWafInterceptor())
            .build()
        try {
            val htmlChallenge = """
                <!doctype html>
                <html>
                <head><title>Just a moment...</title></head>
                <body>Cloudflare Turnstile Challenge Running</body>
                </html>
            """.trimIndent()

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=UTF-8")
                    .setHeader("server", "cloudflare")
                    .setBody(htmlChallenge)
            )

            val request = Request.Builder().url(server.url("/manga")).build()
            client.newCall(request).execute().use { response ->
                assertEquals(403, response.code)
                assertEquals("true", response.header("X-MangaDex-WAF-Challenge"))

                val parsed = RetrofitClient.moshi.adapter(MangaDexMangaListResponse::class.java).fromJson(response.body!!.string())
                assertNotNull(parsed)
                assertEquals("error", parsed?.result)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `waf interceptor leaves normal json responses untouched`() {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .addInterceptor(MangaDexWafInterceptor())
            .build()
        try {
            val validJson = """{"result":"ok","response":"collection","data":[]}"""
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(validJson)
            )

            val request = Request.Builder().url(server.url("/manga")).build()
            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertNull(response.header("X-MangaDex-WAF-Challenge"))
                assertEquals(validJson, response.body!!.string())
            }
        } finally {
            server.shutdown()
        }
    }
}
