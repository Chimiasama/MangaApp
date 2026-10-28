package com.example

import com.example.data.network.MangaDexConnectionDiagnostic
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.Proxy
import javax.net.ssl.HttpsURLConnection

class MangaDexConnectionDiagnosticTest {
    private val certificate = HeldCertificate.Builder().commonName("Diagnostic test")
        .addSubjectAlternativeName("api.mangadex.org").addSubjectAlternativeName("localhost").build()
    private val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
    private val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
    private val json = """{"result":"ok","data":[{"id":"historic","attributes":{"title":{"en":"Tank Tankuro"},"year":1935}}]}"""

    private fun client() = OkHttpClient.Builder().dns(object : Dns {
        override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
    }).sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()

    private fun diagnostic(server: MockWebServer, client: OkHttpClient) = MangaDexConnectionDiagnostic(
        client, server.url("/manga?year=1935&limit=1").newBuilder().host("api.mangadex.org").build(), 0,
        openNative = { url ->
            (server.url(url.file).toUrl().openConnection(Proxy.NO_PROXY) as HttpsURLConnection)
                .apply { sslSocketFactory = clientTls.sslSocketFactory() }
        })

    @Test fun `all three real HTTPS clients use the same query and honest user agent`() = runBlocking {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        val base = client()
        try {
            repeat(3) { server.enqueue(MockResponse().setBody(json).setHeader("x-request-id", "request-$it")) }
            val report = diagnostic(server, base).run()
            assertEquals(3, server.requestCount)
            repeat(3) {
                val request = server.takeRequest()
                assertEquals("/manga?year=1935&limit=1", request.path)
                assertEquals(MangaDexConnectionDiagnostic.USER_AGENT, request.getHeader("User-Agent"))
                assertEquals("application/json", request.getHeader("Accept"))
                assertTrue(report.contains("X-Request-ID: request-$it"))
            }
            assertEquals(3, Regex("SUCESSO").findAll(report).count())
            assertTrue(report.contains("registros=1"))
            assertTrue(report.contains("não sustenta um bloqueio total"))
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `interrupted bodies retain request ids and empty valid API responses still succeed`() = runBlocking {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.enqueue(MockResponse().setBody(json).setHeader("X-Request-ID", "interrupted")
            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        repeat(2) { server.enqueue(MockResponse().setBody("""{"result":"ok","data":[]}""")) }
        server.start()
        val base = client()
        try {
            val report = diagnostic(server, base).run()
            assertTrue(report.contains("HTTP 200\nX-Request-ID: interrupted"))
            assertTrue(report.contains("Etapa da falha: HTTP (dados)"))
            assertEquals(2, Regex("SUCESSO").findAll(report).count())
            assertTrue(report.contains("registros=0"))
            assertTrue(report.contains("Pelo menos um cliente dentro do app recebeu dados válidos"))
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }

    @Test fun `explicit access denial or rate limit stops subsequent client probes`() = runBlocking {
        for (status in listOf(403, 429)) {
            val server = MockWebServer()
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val base = client()
            try {
                server.enqueue(MockResponse().setResponseCode(status).setHeader("X-Request-ID", "denied"))
                val report = diagnostic(server, base).run()
                assertEquals(1, server.requestCount)
                assertTrue(report.contains("HTTP $status"))
                assertTrue(report.contains("testes seguintes foram suspensos"))
                assertFalse(report.contains("HTTPS nativo do Android"))
            } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
        }
    }

    @Test fun `a failed TLS profile does not prevent testing the other engines`() = runBlocking {
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE))
        repeat(2) { server.enqueue(MockResponse().setBody(json)) }
        server.start()
        val base = client()
        try {
            val report = diagnostic(server, base).run()
            assertTrue(report.contains("Etapa da falha: TLS"))
            assertEquals(2, Regex("SUCESSO").findAll(report).count())
            assertTrue(report.contains("Pelo menos um cliente dentro do app recebeu dados válidos"))
            assertTrue(report.contains("não confirma nem exclui"))
        } finally { server.shutdown(); base.dispatcher.executorService.shutdown(); base.connectionPool.evictAll() }
    }
}
