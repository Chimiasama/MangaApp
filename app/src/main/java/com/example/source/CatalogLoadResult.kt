package com.example.source

import java.io.IOException

/** Partial source failures must not masquerade as an authoritative empty catalog. */
data class CatalogLoadResult(val items: List<MangaModel>, val warning: String? = null)

class SourceCatalogPartialException(val items: List<MangaModel>, cause: Exception) :
    IOException("A fonte não respondeu a todas as páginas do catálogo.", cause)
