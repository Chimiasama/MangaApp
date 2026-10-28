package com.example.source

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import retrofit2.HttpException

/** Describe the actual source failure without confusing an unreachable source with no matches. */
fun catalogFailureDescription(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(10).toList()
    causes.filterIsInstance<HttpException>().firstOrNull()?.let {
        return when (it.code()) {
            401, 403 -> "a fonte recusou o acesso (HTTP ${it.code()})"
            429 -> "a fonte limitou as consultas temporariamente (HTTP 429)"
            in 500..599 -> "o servidor da fonte está indisponível (HTTP ${it.code()})"
            else -> "a fonte retornou um erro (HTTP ${it.code()})"
        }
    }
    return when {
        causes.any { it is SocketTimeoutException } -> "o tempo de resposta da fonte esgotou"
        causes.any { it is UnknownHostException } -> "não foi possível localizar o endereço da fonte (DNS)"
        causes.any { it is SSLException } -> "não foi possível estabelecer uma conexão segura com a fonte (TLS)"
        causes.any { it is ConnectException } -> "não foi possível conectar ao servidor da fonte"
        causes.any { it is JsonDataException || it is JsonEncodingException } -> "a fonte enviou uma resposta em formato inesperado"
        else -> "falha ao consultar a fonte (${causes.last().javaClass.simpleName})"
    }
}
