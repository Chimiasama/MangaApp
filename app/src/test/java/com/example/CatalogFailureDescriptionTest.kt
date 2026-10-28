package com.example

import com.example.source.SourceCatalogPartialException
import com.example.source.catalogFailureDescription
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class CatalogFailureDescriptionTest {
    @Test fun `HTTP rejection and rate limiting retain the actual status inside partial failures`() {
        for (status in listOf(403, 429, 503)) {
            val failure = SourceCatalogPartialException(emptyList(), HttpException(Response.error<Any>(status, "".toResponseBody())))
            assertTrue(catalogFailureDescription(failure).contains("HTTP $status"))
        }
    }
    @Test fun `transport and parsing failures have distinct explanations`() {
        val failures = listOf(
            SocketTimeoutException() to "tempo de resposta",
            UnknownHostException() to "DNS",
            SSLHandshakeException("handshake") to "TLS",
            JsonDataException("unexpected token") to "formato inesperado"
        )
        for ((error, description) in failures) {
            val message = catalogFailureDescription(IOException("wrapper", error))
            assertTrue(message, message.contains(description))
            assertFalse(message.contains("Nenhum mangá"))
        }
    }
}
