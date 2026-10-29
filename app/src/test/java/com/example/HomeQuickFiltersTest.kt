package com.example

import com.example.home.HomeQuickFilters
import com.example.home.HomeSection
import com.example.source.MangaModel
import org.junit.Assert.*
import org.junit.Test

class HomeQuickFiltersTest {
    @Test fun `source chapter language and genre remain together in catalog and search requests`() {
        val filters = HomeQuickFilters("mangadash", "en", "genre:romance")
        val request = filters.catalogRequest(HomeSection().filters.copy(yearRange = "1980-2000"))
        assertEquals("mangadash", request.sourceId)
        assertEquals("en", request.language)
        assertEquals(setOf("genre:romance"), request.filters.included)
        assertEquals("1980-2000", request.yearRange)
        assertEquals(request.copy(yearRange = "all"), filters.searchRequest())
        assertEquals(HomeSection().filters, HomeQuickFilters().catalogRequest(HomeSection().filters))
    }

    @Test fun `matching uses metadata with AND without treating original language as chapter language`() {
        val selected = HomeQuickFilters("mangadash", "en", "genre:romance")
        val japanese = MangaModel("book", "book", "Book", "", genres = listOf("Romance"),
            sourceId = "mangadash", originalLanguage = "ja")
        assertTrue(selected.matches(japanese)) // Chapter language is validated by the source query.
        assertFalse(selected.matches(japanese.copy(sourceId = "mangadex")))
        assertFalse(selected.matches(japanese.copy(title = "Romance", description = "Romance", genres = listOf("Action"))))
    }
}
