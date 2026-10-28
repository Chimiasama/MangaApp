package com.example

import com.example.data.network.MangaDexCallFactory
import com.example.data.network.MangaDexCatalogFilterApi
import com.example.data.network.RetrofitClient
import com.example.source.MangaDexSource
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.TlsVersion
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.io.IOException
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class MangaDexCallFactoryTest {
    private val certificate = HeldCertificate.Builder().commonName("MangaDex transport test")
        .addSubjectAlternativeName("api.mangadex.org")
        .addSubjectAlternativeName("uploads.mangadex.org")
        .addSubjectAlternativeName("api.mangadex.org.example.test")
        .build()
    private val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
    private val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
    private val localDns = object : Dns {
        override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder().dns(localDns)
        .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()

    @Test fun `real HTTPS API returns the two Portuguese works over TLS13 and HTTP2 with original filters`() = runBlocking {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        server.start()
        val observed = mutableListOf<Protocol>()
        val tlsVersions = mutableListOf<TlsVersion>()
        val base = client().newBuilder().addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).also {
                observed += it.protocol
                it.handshake?.tlsVersion?.let(tlsVersions::add)
            }
        }.build()
        try {
            val raw = javaClass.getResourceAsStream("/mangadex-historic-ptbr.json")!!.bufferedReader().use { it.readText() }
            server.enqueue(MockResponse().setBody(raw).setHeader("Content-Type", "application/json"))
            val api = Retrofit.Builder().baseUrl("https://api.mangadex.org:${server.port}/")
                .callFactory(MangaDexCallFactory(base))
                .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi))
                .build().create(MangaDexCatalogFilterApi::class.java)
            val result = MangaDexSource(catalogApi = api).getCatalogManga("", "pt-br", "1900-1950")
            assertEquals(listOf("Tank Tankuro", "Sazae-san"), result.map { it.title })
            assertEquals(listOf(Protocol.HTTP_2), observed)
            assertEquals(listOf(TlsVersion.TLS_1_3), tlsVersions)
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("MangaReaderApp/1.1.0", request.getHeader("User-Agent"))
            assertEquals(listOf("pt-br", "pt"), request.requestUrl!!.queryParameterValues("availableTranslatedLanguage[]"))
            assertEquals("asc", request.requestUrl!!.queryParameter("order[year]"))
            assertNull(request.requestUrl!!.queryParameter("title"))
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `covers and other hosts retain TLS13 and HTTP2`() {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        server.start()
        val base = client()
        try {
            val factory = MangaDexCallFactory(base)
            for ((host, protocol, tls) in listOf(
                Triple("uploads.mangadex.org", Protocol.HTTP_2, TlsVersion.TLS_1_3),
                Triple("api.mangadex.org.example.test", Protocol.HTTP_2, TlsVersion.TLS_1_3))) {
                server.enqueue(MockResponse().setBody("original image bytes"))
                val request = Request.Builder().url("https://$host:${server.port}/covers/test.jpg").build()
                factory.newCall(request).execute().use { response ->
                    assertEquals(protocol, response.protocol)
                    assertEquals(tls, response.handshake!!.tlsVersion)
                    assertEquals("original image bytes", response.body!!.string())
                }
            }
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `TLS handshake failure includes DNS and selected address without recording private query`() {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE))
        server.start()
        val base = client().newBuilder().retryOnConnectionFailure(false).build()
        try {
            val request = Request.Builder().url("https://api.mangadex.org:${server.port}/manga?title=private-query").build()
            val error = assertThrows(IOException::class.java) { MangaDexCallFactory(base).newCall(request).execute().close() }
            val report = error.message!!
            assertTrue(report, report.contains("Etapa da falha: TLS"))
            assertTrue(report, report.contains("DNS: 127.0.0.1"))
            assertTrue(report, report.contains("TCP: 127.0.0.1:${server.port}"))
            assertFalse(report.contains("private-query"))
            assertNotNull(error.cause)
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `interrupted response body identifies receive stage and retains original failure`() {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.enqueue(MockResponse().setBody("response data".repeat(1000))
            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        server.start()
        val base = client()
        try {
            val request = Request.Builder().url("https://uploads.mangadex.org:${server.port}/covers/test.jpg").build()
            val error = assertThrows(IOException::class.java) {
                MangaDexCallFactory(base).newCall(request).execute().use { it.body!!.string() }
            }
            assertTrue(error.message, error.message!!.contains("Etapa da falha: HTTP (dados)"))
            assertTrue(error.message!!.contains("Resposta HTTP 200"))
            assertNotNull(error.cause)
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `normal negotiation still rejects an untrusted HTTPS certificate`() {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        val base = OkHttpClient.Builder().dns(localDns).connectTimeout(5, TimeUnit.SECONDS).build()
        try {
            val request = Request.Builder().url("https://api.mangadex.org:${server.port}/manga").build()
            val error = assertThrows(IOException::class.java) { MangaDexCallFactory(base).newCall(request).execute().close() }
            assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it is SSLException })
            assertTrue(error.message!!.contains("Etapa da falha: TLS"))
            assertEquals(0, server.requestCount)
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }
}
