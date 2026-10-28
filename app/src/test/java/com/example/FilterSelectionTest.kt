package com.example

import com.example.source.*
import org.junit.Assert.*
import org.junit.Test

class FilterSelectionTest {
    @Test fun `classification chips cycle include exclude clear`() {
        val start = CatalogFilters()
        val included = start.cycle("genre:action")
        val excluded = included.cycle("genre:action")
        assertEquals(FilterSelection.INCLUDE, included.selection("genre:action"))
        assertEquals(FilterSelection.EXCLUDE, excluded.selection("genre:action"))
        assertEquals(start, excluded.cycle("genre:action"))
    }
    @Test fun `active filters count changes once per classification and each dimension`() {
        val defaults = BrowseFilterRequest("", "all", "pt-br", "all", CatalogFilters())
        assertEquals(0, defaults.activeFilterCount)
        assertEquals(0, defaults.copy(language = "all").activeFilterCount)
        val both = CatalogFilters(included = setOf("genre:action"), excluded = setOf("genre:action"))
        assertEquals(1, both.activeCount)
        assertEquals(4, defaults.copy(sourceId = "mangadex", language = "en", yearRange = "1900-1950", filters = both).activeFilterCount)
        val manga = MangaModel("a", "a", "A", "", genres = listOf("Action"), sourceId = "test")
        assertFalse(both.matches(manga))
    }
}
