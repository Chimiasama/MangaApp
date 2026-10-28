package com.example.source

import java.text.Normalizer
import java.util.Locale

enum class FilterGroup(val label: String) {
    PUBLIC("Público editorial"), GENRE("Gêneros"), THEME("Temas"), FORMAT("Formatos"), ORIGIN("Origem da obra"), STATUS("Publicação")
}
enum class IncludeMode { ALL, ANY }
enum class FilterSelection { NEUTRAL, INCLUDE, EXCLUDE }

data class CatalogFilterOption(val id: String, val label: String, val group: FilterGroup,
    val aliases: Set<String>, val mangaDexTagId: String? = null)

fun normalizedClassification(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("""\p{M}+"""), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

object CatalogTaxonomy {
    // MangaDex IDs verified against its public /manga/tag endpoint; labels stay independent of source language.
    val options = listOf(
        CatalogFilterOption("public:shounen", "Shounen", FilterGroup.PUBLIC, setOf("shounen", "shonen")),
        CatalogFilterOption("public:shoujo", "Shoujo", FilterGroup.PUBLIC, setOf("shoujo", "shojo")),
        CatalogFilterOption("public:seinen", "Seinen", FilterGroup.PUBLIC, setOf("seinen")),
        CatalogFilterOption("public:josei", "Josei", FilterGroup.PUBLIC, setOf("josei")),
        CatalogFilterOption("genre:action", "Ação", FilterGroup.GENRE, setOf("Action", "Ação"), "391b0423-d847-456f-aff0-8b0cfc03066b"),
        CatalogFilterOption("genre:adventure", "Aventura", FilterGroup.GENRE, setOf("Adventure", "Aventura"), "87cc87cd-a395-47af-b27a-93258283bbc6"),
        CatalogFilterOption("genre:comedy", "Comédia", FilterGroup.GENRE, setOf("Comedy", "Comédia"), "4d32cc48-9f00-4cca-9b5a-a839f0764984"),
        CatalogFilterOption("genre:drama", "Drama", FilterGroup.GENRE, setOf("Drama"), "b9af3a63-f058-46de-a9a0-e0c13906197a"),
        CatalogFilterOption("genre:romance", "Romance", FilterGroup.GENRE, setOf("Romance"), "423e2eae-a7a2-4a8b-ac03-a8351462d71d"),
        CatalogFilterOption("genre:fantasy", "Fantasia", FilterGroup.GENRE, setOf("Fantasy", "Fantasia"), "cdc58593-87dd-415e-bbc0-2ec27bf404cc"),
        CatalogFilterOption("genre:scifi", "Ficção científica", FilterGroup.GENRE, setOf("Sci-Fi", "Ficção científica", "Science Fiction"), "256c8bd9-4904-4360-bf4f-508a76d67183"),
        CatalogFilterOption("genre:horror", "Terror", FilterGroup.GENRE, setOf("Horror", "Terror"), "cdad7e68-1419-41dd-bdce-27753074a640"),
        CatalogFilterOption("genre:mystery", "Mistério", FilterGroup.GENRE, setOf("Mystery", "Mistério"), "ee968100-4191-4968-93d3-f82d72be7e46"),
        CatalogFilterOption("genre:thriller", "Suspense", FilterGroup.GENRE, setOf("Thriller", "Suspense"), "07251805-a27e-4d59-b488-f0bfbec15168"),
        CatalogFilterOption("genre:sports", "Esportes", FilterGroup.GENRE, setOf("Sports", "Esportes"), "69964a64-2f90-4d33-beeb-f3ed2875eb4c"),
        CatalogFilterOption("genre:historical", "Histórico", FilterGroup.GENRE, setOf("Historical", "Histórico"), "33771934-028e-4cb3-8744-691e866a923e"),
        CatalogFilterOption("genre:psychological", "Psicológico", FilterGroup.GENRE, setOf("Psychological", "Psicológico"), "3b60b75c-a2d7-4860-ab56-05f391bb889c"),
        CatalogFilterOption("genre:sliceoflife", "Cotidiano", FilterGroup.GENRE, setOf("Slice of Life", "Cotidiano", "Cotidianos"), "e5301a23-ebd9-49dd-a0cb-2add944c7fe9"),
        CatalogFilterOption("genre:mecha", "Mecha", FilterGroup.GENRE, setOf("Mecha"), "50880a9d-5440-4732-9afb-8f457127e836"),
        CatalogFilterOption("genre:isekai", "Isekai", FilterGroup.GENRE, setOf("Isekai"), "ace04997-f6bd-436e-b261-779182193d3d"),
        CatalogFilterOption("genre:tragedy", "Tragédia", FilterGroup.GENRE, setOf("Tragedy", "Tragédia"), "f8f62932-27da-4fe4-8ee1-6779a8c5edba"),
        CatalogFilterOption("theme:ninja", "Ninja", FilterGroup.THEME, setOf("Ninja", "Ninjas", "Shinobi"), "489dd859-9b61-4c37-af75-5b18e88daafc"),
        CatalogFilterOption("theme:samurai", "Samurai", FilterGroup.THEME, setOf("Samurai", "Samurais"), "81183756-1453-4c81-aa9e-f6e1b63be016"),
        CatalogFilterOption("theme:school", "Escolar", FilterGroup.THEME, setOf("School Life", "School", "Escolar"), "caaa44eb-cd40-4177-b930-79d3ef2afe87"),
        CatalogFilterOption("theme:magic", "Magia", FilterGroup.THEME, setOf("Magic", "Magia"), "a1f53773-c69a-4ce5-8cab-fffcd90b1565"),
        CatalogFilterOption("theme:martialarts", "Artes marciais", FilterGroup.THEME, setOf("Martial Arts", "Artes marciais"), "799c202e-7daa-44eb-9cf7-8a3c0441531e"),
        CatalogFilterOption("theme:timetravel", "Viagem no tempo", FilterGroup.THEME, setOf("Time Travel", "Viagem no tempo"), "292e862b-2d17-4062-90a2-0356caa4ae27"),
        CatalogFilterOption("theme:reincarnation", "Reencarnação", FilterGroup.THEME, setOf("Reincarnation", "Reencarnação"), "0bc90acb-ccc1-44ca-a34a-b9f3a73259d0"),
        CatalogFilterOption("theme:military", "Militar", FilterGroup.THEME, setOf("Military", "Militar"), "ac72833b-c4e9-4878-b9db-6c8a4a99444a"),
        CatalogFilterOption("theme:survival", "Sobrevivência", FilterGroup.THEME, setOf("Survival", "Sobrevivência"), "5fff9cde-849c-4d78-aab0-0d52b2ee1d25"),
        CatalogFilterOption("theme:cooking", "Culinária", FilterGroup.THEME, setOf("Cooking", "Culinária"), "ea2bc92d-1c26-4930-9b7c-d5c0dc1b6869"),
        CatalogFilterOption("theme:music", "Música", FilterGroup.THEME, setOf("Music", "Música"), "f42fbf9e-188a-447b-9fdc-f19dc1e4d685"),
        CatalogFilterOption("theme:videogames", "Videogames", FilterGroup.THEME, setOf("Video Games", "Videogames"), "9438db5a-7e2a-4ac0-b39e-e0d95a34b8a8"),
        CatalogFilterOption("theme:supernatural", "Sobrenatural", FilterGroup.THEME, setOf("Supernatural", "Sobrenatural"), "eabc5b4c-6aff-42f3-b657-3e90cbd00b75"),
        CatalogFilterOption("theme:vampires", "Vampiros", FilterGroup.THEME, setOf("Vampires", "Vampiros"), "d7d1730f-6eb0-4ba6-9437-602cac38664c"),
        CatalogFilterOption("theme:zombies", "Zumbis", FilterGroup.THEME, setOf("Zombies", "Zumbis"), "631ef465-9aba-4afb-b0fc-ea10efe274a8"),
        CatalogFilterOption("theme:ghosts", "Fantasmas", FilterGroup.THEME, setOf("Ghosts", "Fantasmas"), "3bb26d85-09d5-4d2e-880c-c34b974339e9"),
        CatalogFilterOption("theme:monsters", "Monstros", FilterGroup.THEME, setOf("Monsters", "Monstros"), "36fd93ea-e8b8-445e-b836-358f02b3d33d"),
        CatalogFilterOption("format:oneshot", "One-shot", FilterGroup.FORMAT, setOf("Oneshot", "One-shot", "One Shot"), "0234a31e-a729-4e28-9d6a-3f87c4966b9e"),
        CatalogFilterOption("format:longstrip", "Leitura vertical", FilterGroup.FORMAT, setOf("Long Strip", "Webtoon"), "3e2b8dae-350e-4ab8-a8ce-016e844b9f0d"),
        CatalogFilterOption("format:webcomic", "Webcomic", FilterGroup.FORMAT, setOf("Web Comic", "Webcomic"), "e197df38-d0e7-43b5-9b09-2842d0c326dd"),
        CatalogFilterOption("format:fullcolor", "Colorido", FilterGroup.FORMAT, setOf("Full Color", "Colorido"), "f5ba408b-0e7a-484d-8d49-4e9125ac96de"),
        CatalogFilterOption("format:officialcolored", "Colorido oficial", FilterGroup.FORMAT, setOf("Official Colored", "Colorido oficial"), "320831a8-4026-470b-94f6-8353740e6f04"),
        CatalogFilterOption("format:anthology", "Antologia", FilterGroup.FORMAT, setOf("Anthology", "Antologia"), "51d83883-4103-437c-b4b1-731cb73d786c"),
        CatalogFilterOption("format:4koma", "Quatro quadros", FilterGroup.FORMAT, setOf("4-Koma", "4 Koma"), "b11fda93-8f1d-4bef-b2ed-8803d3733170"),
        CatalogFilterOption("format:adaptation", "Adaptação", FilterGroup.FORMAT, setOf("Adaptation", "Adaptação"), "f4122d1c-3b44-44d0-9936-ff7502c39ad3"),
        CatalogFilterOption("origin:ja", "Mangá (origem japonesa)", FilterGroup.ORIGIN, setOf("ja", "Manga", "Japonesa")),
        CatalogFilterOption("origin:ko", "Manhwa (origem coreana)", FilterGroup.ORIGIN, setOf("ko", "Manhwa", "Coreana")),
        CatalogFilterOption("origin:zh", "Manhua (origem chinesa)", FilterGroup.ORIGIN, setOf("zh", "zh-hk", "Manhua", "Chinesa")),
        CatalogFilterOption("status:ongoing", "Em andamento", FilterGroup.STATUS, setOf("ongoing", "Em andamento", "Em publicação", "En curso")),
        CatalogFilterOption("status:completed", "Concluído", FilterGroup.STATUS, setOf("completed", "Concluído", "Completo", "Finalizado", "Finalizado")),
        CatalogFilterOption("status:hiatus", "Pausado", FilterGroup.STATUS, setOf("hiatus", "Pausado", "Em pausa")),
        CatalogFilterOption("status:cancelled", "Cancelado", FilterGroup.STATUS, setOf("cancelled", "canceled", "Cancelado"))
    )
    val byId = options.associateBy { it.id }
    fun fromLegacy(label: String) = options.firstOrNull { option ->
        normalizedClassification(label) in (option.aliases + option.label).map(::normalizedClassification)
    }
    fun matches(manga: MangaModel, option: CatalogFilterOption): Boolean {
        val values = when (option.group) {
            FilterGroup.STATUS -> listOf(manga.status)
            FilterGroup.ORIGIN -> if (manga.originalLanguage.isNotBlank()) listOf(manga.originalLanguage) else manga.genres
            else -> manga.genres
        }.map(::normalizedClassification).toSet()
        return (option.aliases + option.label).any { normalizedClassification(it) in values }
    }
    fun known(manga: MangaModel, group: FilterGroup): Boolean = when (group) {
        FilterGroup.STATUS -> options.any { it.group == group && matches(manga, it) }
        FilterGroup.ORIGIN -> manga.originalLanguage.isNotBlank() || options.any { it.group == group && matches(manga, it) }
        FilterGroup.PUBLIC -> options.any { it.group == group && matches(manga, it) }
        else -> if (manga.sourceId == "mangadex") manga.genres.isNotEmpty()
            else options.any { it.group == group && matches(manga, it) }
    }
}

data class CatalogFilters(
    val included: Set<String> = emptySet(),
    val excluded: Set<String> = emptySet(),
    val includeMode: IncludeMode = IncludeMode.ALL,
    val hideUnknownForExclusions: Boolean = true
) {
    val activeCount get() = (included + excluded).size
    fun cycle(id: String) = when (selection(id)) {
        FilterSelection.NEUTRAL -> select(id, FilterSelection.INCLUDE)
        FilterSelection.INCLUDE -> select(id, FilterSelection.EXCLUDE)
        FilterSelection.EXCLUDE -> without(id)
    }
    val isActive get() = included.isNotEmpty() || excluded.isNotEmpty()
    fun selection(id: String) = when (id) { in excluded -> FilterSelection.EXCLUDE; in included -> FilterSelection.INCLUDE; else -> FilterSelection.NEUTRAL }
    fun select(id: String, selection: FilterSelection): CatalogFilters {
        if (id !in CatalogTaxonomy.byId) return this
        val next = if (this.selection(id) == selection) FilterSelection.NEUTRAL else selection
        return copy(included = (included - id).let { if (next == FilterSelection.INCLUDE) it + id else it },
            excluded = (excluded - id).let { if (next == FilterSelection.EXCLUDE) it + id else it })
    }
    fun without(id: String) = copy(included = included - id, excluded = excluded - id)
    fun withLegacy(genre: String): CatalogFilters {
        val id = CatalogTaxonomy.fromLegacy(genre)?.id ?: return this
        return if (id in excluded) this else copy(included = included + id)
    }
    fun missingExclusionData(manga: MangaModel) = hideUnknownForExclusions && excluded.mapNotNull(CatalogTaxonomy.byId::get)
        .any { !CatalogTaxonomy.known(manga, it.group) }
    fun matches(manga: MangaModel): Boolean {
        if (excluded.mapNotNull(CatalogTaxonomy.byId::get).any { CatalogTaxonomy.matches(manga, it) }) return false
        if (missingExclusionData(manga)) return false
        val wanted = (included - excluded).mapNotNull(CatalogTaxonomy.byId::get)
        return wanted.isEmpty() || when (includeMode) {
            IncludeMode.ALL -> wanted.all { CatalogTaxonomy.matches(manga, it) }
            IncludeMode.ANY -> wanted.any { CatalogTaxonomy.matches(manga, it) }
        }
    }
}

data class BrowseFilterRequest(val query: String, val sourceId: String, val language: String,
    val yearRange: String, val filters: CatalogFilters)

/** Counts changes from the default Portuguese / all sources / all years selection. */
val BrowseFilterRequest.activeFilterCount: Int get() = filters.activeCount +
    (if (sourceId != "all") 1 else 0) + (if (language !in setOf("pt-br", "all")) 1 else 0) +
    (if (yearRange.isNotBlank() && yearRange != "all") 1 else 0)
