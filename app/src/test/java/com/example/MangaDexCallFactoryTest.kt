package com.example

import com.example.data.network.MangaDexCallFactory
import com.example.data.network.MangaDexCatalogFilterApi
import com.example.data.network.RetrofitClient
import com.example.source.MangaDexSource
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

    @Test fun `real HTTPS API returns the two Portuguese works over HTTP1 with original filters`() = runBlocking {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        server.start()
        val observed = mutableListOf<Protocol>()
        val base = client().newBuilder().addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).also { observed += it.protocol }
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
            assertEquals(listOf(Protocol.HTTP_1_1), observed)
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("MangaReaderApp/1.1.0", request.getHeader("User-Agent"))
            assertEquals(listOf("pt-br", "pt"), request.requestUrl!!.queryParameterValues("availableTranslatedLanguage[]"))
            assertEquals("asc", request.requestUrl!!.queryParameter("order[year]"))
            assertNull(request.requestUrl!!.queryParameter("title"))
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `cover downloads use HTTP1 while other hosts retain HTTP2`() {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        server.start()
        val base = client()
        try {
            val factory = MangaDexCallFactory(base)
            for ((host, protocol) in listOf("uploads.mangadex.org" to Protocol.HTTP_1_1,
                "api.mangadex.org.example.test" to Protocol.HTTP_2)) {
                server.enqueue(MockResponse().setBody("original image bytes"))
                val request = Request.Builder().url("https://$host:${server.port}/covers/test.jpg").build()
                factory.newCall(request).execute().use { response ->
                    assertEquals(protocol, response.protocol)
                    assertEquals("original image bytes", response.body!!.string())
                }
            }
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `compatibility mode still rejects an untrusted HTTPS certificate`() {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        val base = OkHttpClient.Builder().dns(localDns).connectTimeout(5, TimeUnit.SECONDS).build()
        try {
            val request = Request.Builder().url("https://api.mangadex.org:${server.port}/manga").build()
            assertThrows(SSLException::class.java) { MangaDexCallFactory(base).newCall(request).execute().close() }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }
}
