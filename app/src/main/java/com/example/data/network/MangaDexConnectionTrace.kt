package com.example.data.network

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/** Per-call transport diagnostics. Never records query text, headers, cookies or response content. */
internal class MangaDexConnectionTrace(private val host: String) : EventListener() {
    private val started = System.nanoTime()
    private val events = mutableListOf<String>()
    private var phase = "início"
    private var omitted = 0

    @Synchronized private fun record(message: String, nextPhase: String? = null) {
        if (nextPhase != null) phase = nextPhase
        if (events.size < 64) events += "${(System.nanoTime() - started) / 1_000_000} ms: $message" else omitted++
    }

    @Synchronized private fun snapshot(): String = buildString {
        append("Etapa da falha: $phase\nDestino: $host\nSequência da conexão:\n")
        append(events.joinToString("\n"))
        if (omitted > 0) append("\n$omitted eventos adicionais")
    }

    fun failure(error: IOException): IOException =
        if (error is MangaDexConnectionException) error else MangaDexConnectionException(snapshot(), error)

    override fun dnsStart(call: Call, domainName: String) = record("Resolvendo $domainName", "DNS")
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) =
        record("DNS: ${inetAddressList.joinToString { it.hostAddress.orEmpty() }}")
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
        record("TCP: ${address(inetSocketAddress)}; proxy=${proxy.type()}", "TCP")
    override fun secureConnectStart(call: Call) = record("Início da negociação TLS", "TLS")
    override fun secureConnectEnd(call: Call, handshake: Handshake?) =
        record("TLS concluído: ${handshake?.tlsVersion?.javaName}; ${handshake?.cipherSuite?.javaName}", "TLS concluído")
    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) =
        record("Conexão pronta: ${address(inetSocketAddress)}; protocolo=$protocol")
    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) =
        record("Conexão falhou em ${address(inetSocketAddress)}: ${ioe.javaClass.simpleName}")
    override fun connectionAcquired(call: Call, connection: Connection) =
        record("Conexão usada: ${address(connection.route().socketAddress)}; protocolo=${connection.protocol()}")
    override fun requestHeadersStart(call: Call) = record("Enviando requisição HTTP", "HTTP (envio)")
    override fun requestHeadersEnd(call: Call, request: Request) = record("Requisição HTTP enviada")
    override fun responseHeadersStart(call: Call) = record("Aguardando cabeçalhos HTTP", "HTTP (cabeçalhos)")
    override fun responseHeadersEnd(call: Call, response: Response) = record("Resposta HTTP ${response.code}; protocolo=${response.protocol}")
    override fun responseBodyStart(call: Call) = record("Recebendo corpo da resposta", "HTTP (dados)")
    override fun responseBodyEnd(call: Call, byteCount: Long) = record("Corpo recebido: $byteCount bytes")
    override fun responseFailed(call: Call, ioe: IOException) = record("Resposta interrompida: ${ioe.javaClass.simpleName}")
    override fun callFailed(call: Call, ioe: IOException) = record("Chamada falhou: ${ioe.javaClass.simpleName}")

    private fun address(value: InetSocketAddress) = "${value.address?.hostAddress ?: value.hostString}:${value.port}"
}

internal class MangaDexConnectionException(details: String, cause: IOException) : IOException(details, cause)

/** Also catches resets while streaming the body, after the headers have already arrived. */
internal object MangaDexTraceInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val trace = chain.request().tag(MangaDexConnectionTrace::class.java) ?: return chain.proceed(chain.request())
        try {
            val response = chain.proceed(chain.request())
            val body = response.body ?: return response
            return response.newBuilder().body(object : ResponseBody() {
                private val tracedSource: BufferedSource by lazy {
                    object : ForwardingSource(body.source()) {
                        override fun read(sink: Buffer, byteCount: Long): Long = try {
                            super.read(sink, byteCount)
                        } catch (error: IOException) {
                            throw trace.failure(error)
                        }
                    }.buffer()
                }
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = tracedSource
            }).build()
        } catch (error: IOException) {
            throw trace.failure(error)
        }
    }
}
