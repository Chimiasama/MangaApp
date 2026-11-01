package com.example.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.Locale

class MangaDexWafException(
    val statusCode: Int,
    val url: String,
    val cfRay: String? = null,
    message: String = "Acesso bloqueado por verificação anti-bot Cloudflare WAF (HTTP $statusCode) na URL $url."
) : IOException(message)

/**
 * Interceptor that intercepts Cloudflare WAF / bot-protection challenges (HTTP 403, 503, 429
 * or 200 with HTML verification pages) and transforms them into structured JSON error responses,
 * preventing JSON deserialization crashes (e.g. JsonDataException, MalformedJsonException).
 */
class MangaDexWafInterceptor(
    private val logTag: String = "MangaDexWaf",
    private val throwException: Boolean = false
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val code = response.code
        val isTargetCode = code == 403 || code == 503 || code == 429 || code == 200
        if (!isTargetCode) {
            return response
        }

        val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT).orEmpty()
        val isImageContentType = contentType.startsWith("image/")

        // Never interfere with successful image downloads
        if (isImageContentType && code == 200) {
            return response
        }

        // Fast path: if 200 OK and clearly JSON, no challenge
        val isJsonContentType = contentType.contains("application/json")
        if (code == 200 && isJsonContentType) {
            return response
        }

        // Peek up to 4KB of response body without consuming the response stream
        val peekString = try {
            response.peekBody(4096).string()
        } catch (_: Exception) {
            null
        }

        val isWafChallenge = detectWafChallenge(code, contentType, response, peekString)

        if (!isWafChallenge) {
            return response
        }

        val cfRay = response.header("cf-ray") ?: "não informado"
        val logMsg = "Cloudflare WAF / Bot challenge detectado (HTTP $code) na URL ${request.url} (CF-Ray: $cfRay). " +
                "Evitando falha de deserialização JSON ao sanitizar a resposta HTML."
        try {
            Log.w(logTag, logMsg)
        } catch (_: Throwable) {
            System.err.println("[$logTag] $logMsg")
        }

        if (throwException) {
            throw MangaDexWafException(code, request.url.toString(), cfRay)
        }

        val finalCode = if (code == 200) 403 else code

        val sanitizedJson = """
            {
              "result": "error",
              "errors": [
                {
                  "id": "cloudflare_waf_challenge",
                  "status": $finalCode,
                  "title": "Cloudflare WAF Bot Verification",
                  "detail": "Acesso bloqueado por desafio Cloudflare WAF/Captcha (HTTP $code). O servidor retornou página HTML de verificação de bot em vez de dados JSON."
                }
              ]
            }
        """.trimIndent()

        val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val newBody = sanitizedJson.toResponseBody(jsonMediaType)

        return response.newBuilder()
            .code(finalCode)
            .message("Blocked by Cloudflare WAF Bot Verification (HTTP $code)")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-MangaDex-WAF-Challenge", "true")
            .body(newBody)
            .build()
    }

    private fun detectWafChallenge(
        code: Int,
        contentType: String,
        response: Response,
        peekBody: String?
    ): Boolean {
        val hasCfHeader = response.header("cf-ray") != null ||
                response.header("cf-chl-bypass") != null ||
                response.header("server")?.contains("cloudflare", ignoreCase = true) == true

        val isHtml = contentType.contains("text/html") ||
                contentType.contains("text/plain") ||
                peekBody?.trimStart()?.startsWith("<!doctype html", ignoreCase = true) == true ||
                peekBody?.trimStart()?.startsWith("<html", ignoreCase = true) == true

        if (peekBody.isNullOrBlank()) {
            return (code in setOf(403, 503)) && (isHtml || hasCfHeader)
        }

        val lowerBody = peekBody.lowercase(Locale.ROOT)
        val hasChallengeKeywords = lowerBody.contains("just a moment") ||
                lowerBody.contains("cf-chl") ||
                lowerBody.contains("cloudflare") ||
                lowerBody.contains("turnstile") ||
                lowerBody.contains("captcha") ||
                lowerBody.contains("attention required") ||
                lowerBody.contains("bot verification") ||
                lowerBody.contains("checking your browser") ||
                lowerBody.contains("challenge-running") ||
                lowerBody.contains("ray id")

        if ((code == 403 || code == 503 || code == 429) && (isHtml || hasChallengeKeywords || hasCfHeader)) {
            return true
        }

        if (code == 200 && isHtml && hasChallengeKeywords) {
            return true
        }

        return false
    }
}
