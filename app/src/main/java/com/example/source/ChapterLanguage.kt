package com.example.source

/** Chapter language comes from source metadata, never from the display title. */
object ChapterLanguage {
    fun normalize(code: String): String = code.trim().lowercase(java.util.Locale.ROOT).replace('_', '-')
    fun requested(code: String): Set<String>? = when (val value = normalize(code)) {
        "all" -> null
        "" -> setOf("pt-br")
        "pt-pt" -> setOf("pt")
        "es", "es-la", "es-es" -> setOf("es", "es-la", "es-es")
        else -> setOf(value)
    }
    fun matches(actual: String, selected: String): Boolean =
        requested(selected)?.contains(normalize(actual)) ?: true
}
