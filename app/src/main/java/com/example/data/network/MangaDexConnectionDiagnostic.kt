package com.example.data.network

import com.example.data.network.model.MangaDexMangaListResponse
import com.example.source.catalogFailureDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume

/** A manual, bounded comparison. All clients use the same public GET and honest app identity. */
class MangaDexConnectionDiagnostic(
    client: OkHttpClient = RetrofitClient.okHttpClient,
    private val endpoint: HttpUrl = "https://api.mangadex.org/manga?year=1935&limit=1".toHttpUrl(),
    private val pauseMillis: Long = 1200,
    private val openNative: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }
) {
    private val probeClient = client.newBuilder().retryOnConnectionFailure(false)
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).build()
    private val request = Request.Builder().url(endpoint)
        .header("User-Agent", USER_AGENT).header("Accept", "application/json").build()

    private data class Result(val name: String, val ok: Boolean, val status: Int?, val details: String)

    suspend fun run(): String {
        val results = mutableListOf<Result>()
        val probes: List<suspend () -> Result> = listOf(
            { okhttp("Cliente atual: OkHttp padrão / negociação TLS e HTTP automática", false) },
            { okhttp("Perfil antigo: TLS 1.2 / HTTP/1.1 (somente diagnóstico)", true) },
            { native() }
        )
        for ((index, probe) in probes.withIndex()) {
            if (index > 0) delay(pauseMillis)
            val result = probe()
            results += result
            // Do not continue probing after an explicit denial or rate limit.
            if (result.status == 403 || result.status == 429) break
        }
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        return buildString {
            append("Diagnóstico comparativo da MangaDex\nUTC: $date\nConsulta: $endpoint\nUser-Agent: $USER_AGENT\n\n")
            for (result in results) {
                append("${result.name}\n${if (result.ok) "SUCESSO" else "FALHA"}\n${result.details}\n\n")
            }
            when {
                results.any { it.status == 403 || it.status == 429 } ->
                    append("A fonte recusou ou limitou uma consulta. Os testes seguintes foram suspensos. Aguarde antes de tentar novamente; o X-Request-ID pode ajudar o suporte da MangaDex.")
                results.any { it.ok } ->
                    append("Pelo menos um cliente dentro do app recebeu dados válidos. O resultado não sustenta um bloqueio total do aplicativo; compare os clientes acima. Ele não confirma nem exclui regras específicas de proteção do servidor.")
                else -> append("Nenhum cliente do app concluiu a consulta. Se o navegador funciona na mesma rede, ainda é necessário distinguir incompatibilidade do cliente de filtragem na rede ou no serviço. Este resultado não confirma bloqueio intencional da MangaDex.")
            }
            append("\nNenhum certificado foi ignorado e não houve mudança de DNS, proxy ou identidade para simular um navegador.")
        }
    }

    private suspend fun okhttp(name: String, compatibility: Boolean): Result = suspendCancellableCoroutine { continuation ->
        val call = MangaDexCallFactory(probeClient, compatibilityProfile = compatibility, automaticRecovery = false).newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(failure(name, e))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    try {
                        received(name, it.code, it.header("X-Request-ID"),
                            if (it.code == 200) it.body?.string().orEmpty() else "",
                            "TLS: ${it.handshake?.tlsVersion?.javaName}; HTTP: ${it.protocol}")
                    } catch (error: Exception) { failure(name, error, it.code, it.header("X-Request-ID")) }
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }

    private suspend fun native(): Result = runInterruptible(Dispatchers.IO) {
        val name = "HTTPS nativo do Android: HttpsURLConnection"
        var connection: HttpsURLConnection? = null
        var observedStatus: Int? = null
        var requestId: String? = null
        try {
            val active = openNative(endpoint.toUrl()).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            connection = active
            val code = active.responseCode
            observedStatus = code
            requestId = active.getHeaderField("X-Request-ID")
            received(name, code, requestId,
                if (code == 200) active.inputStream.bufferedReader().use { it.readText() } else "",
                "Cifra TLS: ${runCatching { active.cipherSuite }.getOrDefault("não informada")}")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure(name, error, observedStatus, requestId) }
        finally { connection?.disconnect() }
    }

    private fun received(name: String, code: Int, requestId: String?, body: String, transport: String): Result {
        val prefix = "HTTP $code\nX-Request-ID: ${requestId ?: "não recebido"}\n$transport"
        if (code != 200) return Result(name, false, code, prefix)
        return try {
            val data = RetrofitClient.moshi.adapter(MangaDexMangaListResponse::class.java).fromJson(body)
            val ok = data != null && data.result == "ok" && data.data != null
            Result(name, ok, code, "$prefix\nAPI: result=${data?.result}; registros=${data?.data.orEmpty().size}")
        } catch (error: Exception) { Result(name, false, code, "$prefix\nResposta inválida: ${catalogFailureDetails(error)}") }
    }

    private fun failure(name: String, error: Throwable, status: Int? = null, requestId: String? = null): Result {
        val http = if (status == null) "HTTP: não recebido" else "HTTP $status\nX-Request-ID: ${requestId ?: "não recebido"}"
        return Result(name, false, status, "$http\n${catalogFailureDetails(error)}")
    }

    companion object { const val USER_AGENT = "MangaReaderApp/1.1.0" }
}
