package com.example

import com.example.source.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Opt-in online contract check; regular CI never requires remote availability. */
class LiveCatalogFiltersCheck {
    @Test fun mangaDexCatalogExcludesNinjaWithNoTitle() = runBlocking {
        val source = MangaDexSource()
        val filters = CatalogFilters(excluded = setOf("theme:ninja"))
        val result = source.getFilteredCatalog("", "all", "all", filters)
        assertTrue("Expected a nonempty catalog page with no title", result.isNotEmpty())
        val ninja = CatalogTaxonomy.byId.getValue("theme:ninja")
        assertTrue("Native exclusion must be enforced by MangaDex", result.none { CatalogTaxonomy.matches(it, ninja) })
        assertTrue(result.any(filters::matches))
        println("LIVE CLASSIFICATION MangaDex: records=${result.size}, Ninja excluded on server, no title required")
    }
}
