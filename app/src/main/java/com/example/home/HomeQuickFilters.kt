package com.example.home

import com.example.source.BrowseFilterRequest
import com.example.source.CatalogFilters
import com.example.source.MangaModel

/** Temporary home selections; language is resolved by the source's chapter-language query. */
data class HomeQuickFilters(
    val sourceId: String = "all",
    val language: String = "all",
    val genreId: String? = null
) {
    val isActive get() = sourceId != "all" || language != "all" || genreId != null
    val classifications get() = CatalogFilters(included = setOfNotNull(genreId))

    fun catalogRequest(section: BrowseFilterRequest): BrowseFilterRequest = section.copy(
        sourceId = if (isActive) sourceId else section.sourceId,
        language = if (isActive) language else section.language,
        filters = classifications
    )

    fun matches(manga: MangaModel): Boolean =
        (sourceId == "all" || manga.sourceId == sourceId) && classifications.matches(manga)

    fun searchRequest() = BrowseFilterRequest("", sourceId, language, "all", classifications)
}
