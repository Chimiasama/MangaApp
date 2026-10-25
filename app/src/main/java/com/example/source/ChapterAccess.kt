package com.example.source

import java.io.IOException

class ChapterAccessException(message: String, val originalUrl: String? = null) : IOException(message)
class SourceHttpException(val status: Int) : IOException("A fonte respondeu HTTP $status.")

object SourcePresentation {
    fun name(id: String) = when (id) {
        "mangadex" -> "MangaDex"; "weebcentral" -> "WeebCentral"; "leercapitulo" -> "LeerCapítulo"
        "mangadash" -> "MangaDash"; "localpdf" -> "PDF local"; else -> id.ifBlank { "Fonte não informada" }
    }
    fun language(code: String) = when (val key = ChapterLanguage.normalize(code)) {
        "pt-br" -> "Português (PT-BR)"; "pt" -> "Português (Portugal)"; "en" -> "Inglês"
        "es", "es-la", "es-es" -> "Espanhol"; "ru" -> "Russo"; "ja" -> "Japonês"; "fr" -> "Francês"
        "local" -> "Idioma não informado"; "vi" -> "Vietnamita"; "zh", "zh-hk", "zh-ro" -> "Chinês"; "" -> "Idioma não informado"
        else -> key.uppercase(java.util.Locale.ROOT)
    }
    fun safeWebUrl(url: String?): String? = url?.takeIf {
        runCatching { val uri = java.net.URI(it); uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() }.getOrDefault(false)
    }
    fun mangaUrl(manga: MangaModel): String? = if (manga.sourceId == "mangadex")
        safeWebUrl("https://mangadex.org/title/${manga.url.substringAfterLast('/')}") else safeWebUrl(manga.url)
    fun chapterUrl(chapter: ChapterModel?, sourceId: String): String? {
        chapter ?: return null
        safeWebUrl(chapter.externalUrl)?.let { return it }
        return safeWebUrl(if (sourceId == "mangadex") "https://mangadex.org/chapter/${chapter.chapterUrl.substringAfterLast('/')}" else chapter.chapterUrl)
    }
    fun readError(error: Throwable): String {
        val status = when (error) {
            is SourceHttpException -> error.status
            is retrofit2.HttpException -> error.code()
            is coil.network.HttpException -> error.response.code
            else -> null
        }
        return when (status) {
            401, 403 -> "A fonte recusou o acesso (HTTP $status). Pode exigir login, verificação ou restringir sua região. Isso não confirma DRM. Abra a página original para conferir."
            404, 410 -> "O capítulo ou a imagem não está disponível na fonte (HTTP $status). Pode ter sido removido ou o link pode ter mudado."
            429 -> "A fonte limitou as consultas (HTTP 429). Aguarde um pouco e tente novamente."
            in 500..599 -> "O servidor da fonte está com falha (HTTP $status). Tente novamente mais tarde."
            else -> when (error) {
                is java.net.SocketTimeoutException -> "A fonte demorou para responder. Confira a conexão e tente novamente."
                is java.net.UnknownHostException, is java.net.ConnectException -> "Não foi possível conectar à fonte. Confira sua conexão e a disponibilidade do site."
                else -> error.message?.takeIf { it.isNotBlank() } ?: "Não foi possível carregar este conteúdo. Abra a página original da fonte para conferir."
            }
        }
    }
}
