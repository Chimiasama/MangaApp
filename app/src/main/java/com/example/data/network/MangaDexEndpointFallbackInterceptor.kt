package com.example.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Resilient OkHttp interceptor that automatically falls back to MangaDex's official
 * secondary endpoint (api.mangadex.dev) whenever the primary endpoint (api.mangadex.org)
 * fails due to Cloudflare WAF bot challenges (HTTP 403), connection timeouts, SSL handshake
 * issues, or server unavailability (HTTP 502/503/504).
 */
class MangaDexEndpointFallbackInterceptor(
    private val endpointManager: MangaDexEndpointManager = MangaDexEndpointManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url
        val host = originalUrl.host

        // Only manage MangaDex API hosts
        val isMangaDexApi = host == MangaDexEndpointManager.PRIMARY_HOST || host == MangaDexEndpointManager.FALLBACK_HOST
        if (!isMangaDexApi) {
            return chain.proceed(originalRequest)
        }

        // Avoid redirecting MockWebServer tests on non-standard ports (e.g. random port numbers)
        val isStandardPort = originalUrl.port == 443 || originalUrl.port == 80
        if (!isStandardPort) {
            return chain.proceed(originalRequest)
        }

        // 1. Proactive routing: If primary is currently on cooldown due to recent failures, route directly to fallback
        val effectiveRequest = if (host == MangaDexEndpointManager.PRIMARY_HOST && endpointManager.isFallbackActive()) {
            val fallbackUrl = originalUrl.newBuilder()
                .host(MangaDexEndpointManager.FALLBACK_HOST)
                .build()
            originalRequest.newBuilder().url(fallbackUrl).build()
        } else {
            originalRequest
        }

        val effectiveHost = effectiveRequest.url.host

        // 2. Execute call with reactive failover
        try {
            val response = chain.proceed(effectiveRequest)
            val code = response.code
            val isWafChallenge = response.header("X-MangaDex-WAF-Challenge") == "true"
            val isServerFailure = code in listOf(403, 502, 503, 504)

            // If primary failed with WAF or server error, switch to fallback immediately
            if (effectiveHost == MangaDexEndpointManager.PRIMARY_HOST && (isWafChallenge || isServerFailure)) {
                endpointManager.markPrimaryFailure("HTTP $code (WAF challenge: $isWafChallenge)")
                response.close()

                val fallbackUrl = originalUrl.newBuilder()
                    .host(MangaDexEndpointManager.FALLBACK_HOST)
                    .build()
                val fallbackRequest = originalRequest.newBuilder().url(fallbackUrl).build()

                safeLog("Redirecionando automaticamente de $host para ${MangaDexEndpointManager.FALLBACK_HOST} após erro HTTP $code")
                val fallbackResponse = chain.proceed(fallbackRequest)
                if (fallbackResponse.isSuccessful) {
                    endpointManager.markFallbackSuccess()
                }
                return fallbackResponse
            }

            if (response.isSuccessful) {
                if (effectiveHost == MangaDexEndpointManager.PRIMARY_HOST) {
                    endpointManager.markPrimarySuccess()
                } else {
                    endpointManager.markFallbackSuccess()
                }
            }

            return response
        } catch (transportError: IOException) {
            // Reactive failover on network transport failure (SocketTimeout, ConnectException, SSLHandshake, etc.)
            if (effectiveHost == MangaDexEndpointManager.PRIMARY_HOST) {
                endpointManager.markPrimaryFailure(transportError.message)

                val fallbackUrl = originalUrl.newBuilder()
                    .host(MangaDexEndpointManager.FALLBACK_HOST)
                    .build()
                val fallbackRequest = originalRequest.newBuilder().url(fallbackUrl).build()

                safeLog("Falha de transporte no primário ($transportError). Tentando fallback ${MangaDexEndpointManager.FALLBACK_HOST}...")
                return try {
                    val fallbackResponse = chain.proceed(fallbackRequest)
                    if (fallbackResponse.isSuccessful) {
                        endpointManager.markFallbackSuccess()
                    }
                    fallbackResponse
                } catch (fallbackError: IOException) {
                    // Both failed; combine exceptions
                    transportError.addSuppressed(fallbackError)
                    throw transportError
                }
            }
            throw transportError
        }
    }

    private fun safeLog(msg: String) {
        try {
            Log.i("MangaDexFallback", msg)
        } catch (_: Throwable) {
            System.err.println("[MangaDexFallback] $msg")
        }
    }
}
