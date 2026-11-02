package com.example.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that ensures all requests to MangaDex and remote APIs include:
 * 1. A unique, properly formatted User-Agent header following MangaDex official API guidelines:
 *    "AppName/Version (URL_or_contact_email)"
 * 2. Essential complementary headers when absent (e.g. Accept: application/json for API calls).
 */
class MangaDexHeaderInterceptor(
    val userAgent: String = MangaDexApiConfig.USER_AGENT
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val isMangaDexDomain = original.url.host.contains("mangadex") ||
            original.url.host in setOf("api.mangadex.org", "api.mangadex.dev", "uploads.mangadex.org") ||
            MangaDexPages.registry.contains(original.url.toString())

        // 1. Mandatory User-Agent header
        val currentUa = original.header("User-Agent")
        if (shouldSetUserAgent(currentUa, isMangaDexDomain)) {
            builder.header("User-Agent", userAgent)
        }

        // 2. Essential complementary headers: Accept
        if (original.header("Accept") == null) {
            val isImage = isImageRequest(original.url.encodedPath, original.url.host)
            if (isImage) {
                builder.header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            } else {
                builder.header("Accept", "application/json")
            }
        }

        // 3. Legitimate client headers for Cloudflare / MangaDex WAF compliance
        if (isMangaDexDomain) {
            if (original.header("Referer") == null) {
                builder.header("Referer", "https://mangadex.org/")
            }
            if (original.header("Accept-Language") == null) {
                builder.header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
            }
        }

        return chain.proceed(builder.build())
    }

    private fun shouldSetUserAgent(currentUa: String?, isMangaDexDomain: Boolean = false): Boolean {
        if (currentUa.isNullOrBlank()) return true
        if (currentUa.startsWith("okhttp", ignoreCase = true)) return true
        if (isMangaDexDomain && (currentUa.contains("MangaApp") || currentUa == "Mozilla/5.0 MangaApp/1.1")) return true
        return false
    }

    private fun isImageRequest(path: String, host: String): Boolean {
        if (host == "uploads.mangadex.org" || host.contains("mangadex.network") ||
            path.contains("/covers/") || path.contains("/data/") || path.contains("/data-saver/")) {
            return true
        }
        val lower = path.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.endsWith(".avif")
    }
}
