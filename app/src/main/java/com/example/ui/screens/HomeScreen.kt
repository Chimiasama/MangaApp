package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.text.style.TextOverflow
import com.example.home.HomeQuickFilters
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.source.MangaModel
import com.example.ui.components.MangaCard
import com.example.ui.viewmodel.HomeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    favorites: Set<String>,
    availableSources: List<com.example.source.MangaSource> = emptyList(),
    onSearch: () -> Unit = {},
    onMangaClick: (MangaModel) -> Unit,
    onFavoriteToggle: (MangaModel) -> Unit,
    onMore: (String) -> Unit,
    onRefresh: () -> Unit,
    onContinue: (MangaModel) -> Unit = onMangaClick,
    onQuickFiltersChanged: (HomeQuickFilters) -> Unit = {}
) {
    val filters = state.quickFilters
    var dimension by rememberSaveable { mutableStateOf("source") }
    var showDimensions by rememberSaveable { mutableStateOf(false) }
    val sourceScroll = rememberLazyListState()
    val languageScroll = rememberLazyListState()
    val genreScroll = rememberLazyListState()
    val quickSources = listOf("all" to "Todas") + availableSources.filter { !it.isNsfw }.map { it.id to it.name }
    val quickLanguages = listOf("all" to "Todos", "pt-br" to "Português", "en" to "Inglês", "es" to "Espanhol")
    val quickTags = listOf("all" to "Todos", "genre:romance" to "Romance", "theme:ninja" to "Ninja",
        "genre:action" to "Ação", "genre:adventure" to "Aventura", "genre:comedy" to "Comédia",
        "public:shounen" to "Shounen", "genre:drama" to "Drama")
    val dimensions = listOf("source" to "Fonte", "lang" to "Idioma", "genre" to "Gênero")
    val options = when (dimension) { "lang" -> quickLanguages; "genre" -> quickTags; else -> quickSources }
    val selectedValue = when (dimension) { "lang" -> filters.language; "genre" -> filters.genreId ?: "all"; else -> filters.sourceId }
    val hasActiveFilter = filters.isActive
    fun clearFilters() = onQuickFiltersChanged(HomeQuickFilters())

    Column(Modifier.fillMaxSize().testTag("home_screen")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Explorar", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (hasActiveFilter) {
                TextButton(
                    onClick = {
                        clearFilters()
                    },
                    modifier = Modifier.testTag("home_clear_filters")
                ) {
                    Text("Limpar filtros", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { showDimensions = true }, modifier = Modifier.testTag("home_filter_dimension"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(dimensions.first { it.first == dimension }.second, style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showDimensions, onDismissRequest = { showDimensions = false }) {
                    dimensions.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, modifier = Modifier.testTag("home_filter_category_$id"),
                            onClick = { dimension = id; showDimensions = false })
                    }
                }
            }
            LazyRow(modifier = Modifier.weight(1f).testTag("home_filter_options"),
                state = when (dimension) { "lang" -> languageScroll; "genre" -> genreScroll; else -> sourceScroll },
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                items(options, key = { it.first }) { (id, label) ->
                    FilterChip(selected = selectedValue == id, onClick = {
                        onQuickFiltersChanged(when (dimension) {
                            "lang" -> filters.copy(language = id)
                            "genre" -> filters.copy(genreId = id.takeUnless { it == "all" })
                            else -> filters.copy(sourceId = id)
                        })
                    }, label = { Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
                        modifier = Modifier.testTag("home_filter_${dimension}_$id"))
                }
            }
        }
        if (hasActiveFilter) {
            val source = (quickSources.firstOrNull { it.first == filters.sourceId }?.second ?: filters.sourceId).removeSuffix(" (API Oficial)")
            val language = quickLanguages.firstOrNull { it.first == filters.language }?.second ?: filters.language
            val genre = quickTags.firstOrNull { it.first == (filters.genreId ?: "all") }?.second ?: "Todos"
            Text("Fonte: $source · Idioma: $language · Gênero: $genre",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).testTag("home_filters_summary"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            val visibleSections = state.sections
            LazyColumn(Modifier.fillMaxSize().testTag("home_carousels_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.error != null) item {
                    TextButton(onClick = onRefresh) { Text("Não foi possível atualizar as seções. Tentar novamente") }
                }
                if (!state.isLoading && visibleSections.isEmpty() && state.error == null) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (hasActiveFilter) "Nenhuma obra encontrada com os filtros selecionados."
                            else "Nenhuma seção com resultados. Configure a tela inicial nos Ajustes.")
                        if (hasActiveFilter) {
                            Spacer(Modifier.height(8.dp))
                            if (filters.language != "all") TextButton(
                                onClick = { onQuickFiltersChanged(filters.copy(language = "all")) },
                                modifier = Modifier.testTag("home_try_all_languages")
                            ) { Text("Tentar com todos os idiomas") }
                            TextButton(onClick = {
                                clearFilters()
                            }) { Text("Limpar filtros") }
                        }
                    }
                }
                items(visibleSections, key = { it.id }) { section ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("carousel_section_${section.id}")) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(section.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { onMore(section.id) }) { Text("Ver mais") }
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(section.items, key = { it.url }) { manga ->
                                MangaCard(manga, { if (section.isContinueReading) onContinue(manga) else onMangaClick(manga) }, manga.url in favorites,
                                    { onFavoriteToggle(manga) }, modifier = Modifier.width(135.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
