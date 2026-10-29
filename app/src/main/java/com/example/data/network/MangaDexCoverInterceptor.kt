package com.example.data.network

import okhttp3.Interceptor
import okhttp3.Response

/** A missing generated thumbnail can still have a valid original cover. */
internal class MangaDexCoverInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.method != "GET" || request.url.host != "uploads.mangadex.org" ||
            !request.url.encodedPath.startsWith("/covers/") || chain.call().isCanceled() ||
            (response.code != 404 && response.code != 410 && response.code !in 500..599)) return response
        val original = request.url.encodedPath.replace(Regex("\\.(256|512)\\.jpg$"), "")
        if (original == request.url.encodedPath) return response
        response.close()
        return chain.proceed(request.newBuilder().url(request.url.newBuilder().encodedPath(original).build()).build())
    }
}
