package com.example

import com.example.home.*
import com.example.source.*
import org.junit.Assert.*
import org.junit.Test

class HomeSectionTest {
    @Test fun `home sections roundtrip all filters and layout preferences`() {
        val section = HomeSection("test", "Favoritos da home", filters = BrowseFilterRequest("", "mangadex", "es", "1980-2000",
            CatalogFilters(included = setOf("genre:action"), excluded = setOf("theme:ninja"), includeMode = IncludeMode.ANY)),
            sort = HomeSort.RECENT, itemCount = 30, active = false)
        assertEquals(listOf(section), HomeSections.decode(HomeSections.encode(listOf(section))))
        assertEquals(emptyList<HomeSection>(), HomeSections.decode("[]"))
        assertEquals(HomeSections.defaults, HomeSections.decode("invalid"))
    }
    @Test fun `deduplication respects order and fills visible section limits`() {
        fun book(id: String) = MangaModel(id, id, id, "", sourceId = "test")
        val a = book("a"); val b = book("b"); val c = book("c")
        val sections = listOf(GenreCarouselSection("first", "First", "all", listOf(a,b)), GenreCarouselSection("second", "Second", "all", listOf(a,b,c)))
        val result = deduplicateHomeSections(sections, mapOf("first" to 1, "second" to 2))
        assertEquals(listOf(a), result[0].items)
        assertEquals(listOf(b,c), result[1].items)
    }
    @Test fun `reordering persists positions and only actual scores affect rating order`() {
        val sections = HomeSections.defaults
        val moved = HomeSections.move(sections, "recent", 0)
        assertEquals("recent", moved.first().id)
        assertEquals(moved.indices.toList(), moved.map { it.position })
        val a = MangaModel("a","a","A","",sourceId="test")
        val b = a.copy(id="b", rating=9.0)
        assertEquals(listOf(b,a), sortHomeItems(listOf(a,b), HomeSort.RATING))
        assertEquals(listOf(a,b.copy(rating=null)), sortHomeItems(listOf(a,b.copy(rating=null)), HomeSort.RATING))
    }
}
