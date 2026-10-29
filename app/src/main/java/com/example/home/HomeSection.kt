package com.example.home

import com.example.source.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class HomeSectionType { CATALOG, CONTINUE_READING }
enum class HomeSort(val label: String) { POPULAR("Popular"), RECENT("Mais recente"), RATING("Melhor nota") }

data class HomeSection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "Nova seção",
    val type: HomeSectionType = HomeSectionType.CATALOG,
    val filters: BrowseFilterRequest = BrowseFilterRequest("", "all", "pt-br", "all", CatalogFilters()),
    val sort: HomeSort = HomeSort.POPULAR,
    val itemCount: Int = 20,
    val active: Boolean = true,
    val position: Int = 0
)

object HomeSections {
    val defaults: List<HomeSection> get() = listOf(
        HomeSection("continue_reading", "Continuar lendo", HomeSectionType.CONTINUE_READING, position = 0),
        HomeSection("highlights", "Destaques & Populares", position = 1),
        HomeSection("shounen", "Shounen Épico", filters = HomeSection().filters.copy(filters = CatalogFilters(included = setOf("public:shounen"))), position = 2),
        HomeSection("recent", "Atualizados recentemente", sort = HomeSort.RECENT, position = 3)
    )
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<HomeSection>>(Types.newParameterizedType(List::class.java, HomeSection::class.java))
    fun normalize(sections: List<HomeSection>) = sections.distinctBy { it.id }.sortedBy { it.position }.mapIndexed { index, section ->
        section.copy(title = section.title.trim().ifBlank { "Seção" }, position = index,
            itemCount = section.itemCount.takeIf { it in setOf(10, 20, 30) } ?: 20,
            filters = section.filters.copy(filters = section.filters.filters.copy(
                included = section.filters.filters.included.filter { it in CatalogTaxonomy.byId }.toSet(),
                excluded = section.filters.filters.excluded.filter { it in CatalogTaxonomy.byId }.toSet())))
    }
    fun encode(sections: List<HomeSection>): String = adapter.toJson(normalize(sections))
    fun decode(json: String?): List<HomeSection> = if (json == null) defaults else runCatching {
        normalize(adapter.fromJson(json) ?: defaults)
    }.getOrElse { defaults }
    fun move(sections: List<HomeSection>, id: String, target: Int): List<HomeSection> {
        val ordered = sections.sortedBy { it.position }.toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0 || target !in ordered.indices) return sections
        val item = ordered.removeAt(index)
        ordered.add(target, item)
        return ordered.mapIndexed { i, section -> section.copy(position = i) }
    }
}

fun deduplicateHomeSections(sections: List<GenreCarouselSection>, limits: Map<String, Int> = emptyMap()): List<GenreCarouselSection> {
    val seen = mutableSetOf<String>()
    return sections.map { section -> section.copy(items = section.items.asSequence().filter { it.id.ifBlank { it.url } !in seen }
        .take(limits[section.id] ?: Int.MAX_VALUE).onEach { seen.add(it.id.ifBlank { it.url }) }.toList()) }
        .filter { it.items.isNotEmpty() }
}

fun sortHomeItems(items: List<MangaModel>, sort: HomeSort): List<MangaModel> = when (sort) {
    HomeSort.POPULAR -> items
    HomeSort.RECENT -> items.sortedByDescending { it.updatedAt }
    HomeSort.RATING -> items.sortedWith(compareByDescending<MangaModel> { it.rating ?: Double.NEGATIVE_INFINITY })
}
