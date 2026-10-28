package com.example.data.network

import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

/** A scoped compatibility profile; HTTPS verification and the original request are preserved. */
class MangaDexCallFactory(private val defaultClient: OkHttpClient) : Call.Factory {
    private val compatibleClient = defaultClient.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        // Do not reuse a connection negotiated by the default HTTP/2 client.
        .connectionPool(ConnectionPool())
        .eventListenerFactory(object : okhttp3.EventListener.Factory {
            override fun create(call: Call): okhttp3.EventListener =
                call.request().tag(MangaDexConnectionTrace::class.java) ?: okhttp3.EventListener.NONE
        })
        .addInterceptor(MangaDexTraceInterceptor)
        .build()

    override fun newCall(request: Request): Call {
        return when (request.url.host) {
            "api.mangadex.org", "uploads.mangadex.org" -> {
                val traced = request.newBuilder()
                    .tag(MangaDexConnectionTrace::class.java, MangaDexConnectionTrace(request.url.host)).build()
                compatibleClient.newCall(traced)
            }
            else -> defaultClient.newCall(request)
        }
    }
}
