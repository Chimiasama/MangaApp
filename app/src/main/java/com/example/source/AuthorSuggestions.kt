package com.example.source

fun hasKnownAuthor(author: String): Boolean = author.trim().lowercase(java.util.Locale.ROOT) !in
    setOf("", "unknown", "unknown author", "desconhecido", "autor desconhecido", "não informado", "nao informado", "n/a", "-", "—")

data class AuthorSuggestions(
    val mangaUrl: String = "",
    val sourceId: String = "",
    val author: String = "",
    val items: List<MangaModel> = emptyList()
)

/** Keep discovery bounded, remove the open book, and never bypass adult-content preferences. */
fun authorSuggestions(manga: MangaModel, items: List<MangaModel>, allowAdult: Boolean): AuthorSuggestions {
    if (!hasKnownAuthor(manga.author)) return AuthorSuggestions()
    val adultTags = setOf("hentai", "+18", "adulto", "erotico", "erótica", "nsfw", "smut", "doujinshi", "ecchi", "hentai / 18+", "pornographic", "erotica")
    return AuthorSuggestions(manga.url, manga.sourceId, manga.author.trim(), items.asSequence()
        .filter { it.sourceId == manga.sourceId && it.url != manga.url && it.id != manga.id }
        .filter { allowAdult || (!it.isNsfw && it.contentRating !in setOf("erotica", "pornographic") && it.genres.none { genre -> genre.trim().lowercase() in adultTags }) }
        .distinctBy { it.url }.distinctBy { it.id }.take(12).toList())
}
