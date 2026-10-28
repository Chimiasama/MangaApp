package com.example

import com.example.source.*
import org.junit.Assert.*
import org.junit.Test

class CatalogFiltersTest {
    private fun book(vararg tags: String, status: String = "Unknown", origin: String = "") =
        MangaModel("m", "m", "Ninja in the title", "", genres = tags.toList(), sourceId = "mangadex",
            status = status, originalLanguage = origin)

    @Test fun `ninja exclusion does not exclude unrelated martial arts and never infers tags from titles`() {
        val filters = CatalogFilters(excluded = setOf("theme:ninja"))
        assertFalse(filters.matches(book("Ninja", "Action")))
        assertTrue(filters.matches(book("Martial Arts", "Action")))
        assertTrue(filters.matches(book("Drama")))
        assertFalse(filters.matches(book()))
        assertTrue(filters.copy(hideUnknownForExclusions = false).matches(book()))
    }
    @Test fun `any all and exclusions retain distinct logical semantics across groups`() {
        val filters = CatalogFilters(included = setOf("genre:action", "status:completed"))
        assertTrue(filters.matches(book("Action", status = "Concluído")))
        assertFalse(filters.matches(book("Ação", status = "ongoing")))
        assertTrue(filters.copy(includeMode = IncludeMode.ANY).matches(book("Drama", status = "completed")))
        assertFalse(filters.copy(includeMode = IncludeMode.ANY, excluded = setOf("theme:ninja"))
            .matches(book("Ninja", status = "completed")))
    }
    @Test fun `toggle is mutually exclusive reversible and unknown ids cannot become filters`() {
        val included = CatalogFilters().select("theme:ninja", FilterSelection.INCLUDE)
        assertEquals(setOf("theme:ninja"), included.included)
        val excluded = included.select("theme:ninja", FilterSelection.EXCLUDE)
        assertTrue(excluded.included.isEmpty())
        assertEquals(setOf("theme:ninja"), excluded.excluded)
        assertFalse(excluded.select("theme:ninja", FilterSelection.EXCLUDE).isActive)
        assertEquals(excluded, excluded.select("invented", FilterSelection.INCLUDE))
    }
    @Test fun `source aliases demographic origin and status are normalized without using translation language`() {
        assertTrue(CatalogFilters(included = setOf("public:shounen", "genre:action", "origin:ko", "status:completed"))
            .matches(book("Shonen", "Ação", status = "Concluído", origin = "ko")))
        assertFalse(CatalogFilters(included = setOf("origin:ja")).matches(book("Action", origin = "ko")))
        assertTrue(CatalogFilters(included = setOf("origin:zh")).matches(book("Action", origin = "zh-hk")))
    }
    @Test fun `normalization ignores accents and punctuation while preserving exact tag boundaries`() {
        assertEquals(normalizedClassification("Ficção científica"), normalizedClassification("FICCAO CIENTIFICA"))
        assertTrue(CatalogFilters(included = setOf("format:oneshot")).matches(book("One Shot")))
        assertFalse(CatalogFilters(included = setOf("genre:action")).matches(book("Not Action")))
        assertTrue(CatalogTaxonomy.options.all { it.id in CatalogTaxonomy.byId })
        assertEquals(CatalogTaxonomy.options.size, CatalogTaxonomy.byId.size)
    }
}
