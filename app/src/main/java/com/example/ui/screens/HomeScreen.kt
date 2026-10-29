package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
fun HomeScreen(state: HomeUiState, favorites: Set<String>, onSearch: () -> Unit = {},
    onMangaClick: (MangaModel) -> Unit, onFavoriteToggle: (MangaModel) -> Unit,
    onMore: (String) -> Unit, onRefresh: () -> Unit, onContinue: (MangaModel) -> Unit = onMangaClick) {
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val quickTags = listOf(
        null to "Todos",
        "Romance" to "Romance",
        "Ninja" to "Ninja",
        "Ação" to "Ação",
        "Aventura" to "Aventura",
        "Comédia" to "Comédia",
        "Shounen" to "Shounen",
        "Drama" to "Drama"
    )

    Column(Modifier.fillMaxSize().testTag("home_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Explorar", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickTags.forEach { (tag, label) ->
                FilterChip(
                    selected = selectedTag == tag,
                    onClick = { selectedTag = tag },
                    label = { Text(label) },
                    modifier = Modifier.testTag("home_filter_${tag ?: "all"}")
                )
            }
        }
        PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            val visibleSections = state.sections.mapNotNull { section ->
                val filteredItems = if (selectedTag == null) section.items else {
                    section.items.filter { manga ->
                        manga.genres.any { it.contains(selectedTag!!, ignoreCase = true) } ||
                        manga.title.contains(selectedTag!!, ignoreCase = true) ||
                        manga.description.contains(selectedTag!!, ignoreCase = true)
                    }
                }
                if (filteredItems.isNotEmpty() || selectedTag == null) section.copy(items = filteredItems) else null
            }
            LazyColumn(Modifier.fillMaxSize().testTag("home_carousels_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.error != null) item {
                    TextButton(onClick = onRefresh) { Text("Não foi possível atualizar as seções. Tentar novamente") }
                }
                if (!state.isLoading && visibleSections.isEmpty() && state.error == null) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (selectedTag != null) "Nenhuma obra com o gênero '$selectedTag' nas seções atuais."
                            else "Nenhuma seção com resultados. Configure a tela inicial nos Ajustes.")
                        if (selectedTag != null) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { selectedTag = null }) { Text("Limpar filtro") }
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
