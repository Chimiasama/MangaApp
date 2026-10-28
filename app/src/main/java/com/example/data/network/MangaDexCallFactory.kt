package com.example.data.network

import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

/** Scoped connection tracing; normal requests preserve the original TLS and HTTP negotiation. */
class MangaDexCallFactory(
    private val defaultClient: OkHttpClient,
    compatibilityProfile: Boolean = false,
    automaticRecovery: Boolean = !compatibilityProfile,
    nativeConnection: (java.net.URL) -> javax.net.ssl.HttpsURLConnection = {
        it.openConnection() as javax.net.ssl.HttpsURLConnection
    }
) : Call.Factory {
    private val tracedClient = defaultClient.newBuilder()
        .apply {
            if (compatibilityProfile) {
                protocols(listOf(Protocol.HTTP_1_1))
                connectionSpecs(listOf(ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2).build()))
            }
        }
        // Keep MangaDex diagnostics scoped to its own connections.
        .connectionPool(ConnectionPool())
        .eventListenerFactory(object : okhttp3.EventListener.Factory {
            override fun create(call: Call): okhttp3.EventListener =
                call.request().tag(MangaDexConnectionTrace::class.java) ?: okhttp3.EventListener.NONE
        })
        .apply { if (automaticRecovery) addInterceptor(MangaDexRecoveryInterceptor(nativeConnection)) }
        .addInterceptor(MangaDexTraceInterceptor)
        .build()

    override fun newCall(request: Request): Call {
        return when (request.url.host) {
            "api.mangadex.org", "uploads.mangadex.org" -> {
                val traced = request.newBuilder()
                    .tag(MangaDexConnectionTrace::class.java, MangaDexConnectionTrace(request.url.host)).build()
                tracedClient.newCall(traced)
            }
            else -> defaultClient.newCall(request)
        }
    }
}
