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
    val causes = catalogFailureCauses(error)
    val http = causes.filterIsInstance<HttpException>().firstOrNull()
    val description = if (http != null) {
        when (http.code()) {
            401, 403 -> "a fonte recusou o acesso (HTTP ${http.code()})"
            429 -> "a fonte limitou as consultas temporariamente (HTTP 429)"
            in 500..599 -> "o servidor da fonte está indisponível (HTTP ${http.code()})"
            else -> "a fonte retornou um erro (HTTP ${http.code()})"
        }
    } else when {
        causes.any { it is SocketTimeoutException } -> "o tempo de resposta da fonte esgotou"
        causes.any { it is UnknownHostException } -> "não foi possível localizar o endereço da fonte (DNS)"
        causes.any { it is SSLException } -> "não foi possível estabelecer uma conexão segura com a fonte (TLS)"
        causes.any { it is ConnectException } -> "não foi possível conectar ao servidor da fonte"
        causes.any { it is JsonDataException || it is JsonEncodingException } -> "a fonte enviou uma resposta em formato inesperado"
        else -> "falha ao consultar a fonte (${causes.last().javaClass.simpleName})"
    }
    return "$description\n\nDetalhes:\n${catalogFailureDetails(error)}"
}

/** Include every cause and its complete message, without dumping the stack trace. */
fun catalogFailureDetails(error: Throwable): String = catalogFailureCauses(error).joinToString("\nCausado por: ") {
    "${it.javaClass.simpleName.ifBlank { it.javaClass.name }}${it.message?.let { message -> ": $message" }.orEmpty()}"
}

private fun catalogFailureCauses(error: Throwable): List<Throwable> {
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    return generateSequence(error) { it.cause }.takeWhile { seen.add(it) }.toList()
}
