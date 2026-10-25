package com.example.source

import java.text.Normalizer
import java.util.Locale

/** Category identity is based on source metadata, never title/synopsis keywords. */
object MangaCategories {
    private fun fold(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    private val aliases = mapOf(
        "Shounen" to listOf("shounen", "shonen", "shōnen"),
        "Shoujo" to listOf("shoujo", "shojo", "shōjo"),
        "Seinen" to listOf("seinen"), "Josei" to listOf("josei"),
        "Ação" to listOf("ação", "action", "acción", "acao"),
        "Aventura" to listOf("aventura", "adventure"),
        "Fantasia" to listOf("fantasia", "fantasy", "fantasía"),
        "Comédia" to listOf("comédia", "comedy", "comedia"),
        "Sci-Fi" to listOf("sci-fi", "science fiction", "ficção científica", "ciencia ficción"),
        "Escolar" to listOf("escolar", "school life", "vida escolar"),
        "Sobrenatural" to listOf("sobrenatural", "supernatural"),
        "Yuri" to listOf("yuri", "girls' love", "girls love"),
        "Romance" to listOf("romance"), "Ninja" to listOf("ninja"), "Isekai" to listOf("isekai")
    )
    fun key(value: String): String {
        val folded = fold(value)
        return aliases.entries.firstOrNull { (_, names) -> names.any { fold(it) == folded } }?.key ?: value.trim()
    }
    fun isAll(value: String) = value.isBlank() || value == "all"
    fun matches(genres: List<String>, category: String) = isAll(category) || genres.any { key(it) == key(category) }
    fun display(genres: List<String>) = genres.map(::key).filter(String::isNotBlank).distinct()
    fun english(category: String) = when (key(category)) {
        "Ação" -> "Action"; "Aventura" -> "Adventure"; "Fantasia" -> "Fantasy"
        "Comédia" -> "Comedy"; "Escolar" -> "School Life"; "Sobrenatural" -> "Supernatural"
        "Yuri" -> "Girls' Love"; else -> key(category)
    }
}
