package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.source.MangaModel
import com.example.source.MangaSource
import com.example.ui.components.MangaCard
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.NsfwRedBadge
import com.example.ui.theme.PrimaryPurple
import com.example.ui.viewmodel.BrowseUiState

@Composable
fun BrowseScreen(
    state: BrowseUiState,
    adultContentEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSourceSelected: (String) -> Unit,
    onGenreSelected: (String) -> Unit = {},
    onMangaClick: (MangaModel) -> Unit,
    onFavoriteToggle: (MangaModel) -> Unit,
    favoriteUrls: Set<String>,
    onDeleteSearchHistoryItem: (Long) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClearFilters: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("browse_screen")
    ) {
        // Search Bar
        PaddingValues(16.dp).let {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                    placeholder = { Text("Pesquisar mangá, gênero ou autor...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = CrimsonRed
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpar busca",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("refresh_catalog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Recarregar catálogo",
                                    tint = PrimaryPurple
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    singleLine = true
                )

                // Recent Search History Chips
                if (state.searchHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Buscas Recentes",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = onClearSearchHistory,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(24.dp)
                                .testTag("clear_history_button")
                        ) {
                            Text(
                                text = "Limpar Tudo",
                                fontSize = 11.sp,
                                color = CrimsonRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.searchHistory.forEach { history ->
                            InputChip(
                                selected = false,
                                onClick = { onQueryChange(history.query) },
                                label = { Text(history.query, fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remover do histórico",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onDeleteSearchHistoryItem(history.id) }
                                    )
                                },
                                modifier = Modifier.testTag("search_history_chip_${history.id}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Source Filter Chips (apenas exibidos se houver mais de uma fonte configurada para manter a navegação limpa)
                if (state.availableSources.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = state.selectedSourceId == "all",
                            onClick = { onSourceSelected("all") },
                            label = { Text("Todas as Fontes (${state.availableSources.size})") },
                            modifier = Modifier.testTag("source_chip_all"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonRed,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )

                        state.availableSources.forEach { source ->
                            FilterChip(
                                selected = state.selectedSourceId == source.id,
                                onClick = { onSourceSelected(source.id) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (source.isNsfw) {
                                            Text(
                                                text = "+18 ",
                                                color = NsfwRedBadge,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Text(source.name)
                                    }
                                },
                                modifier = Modifier.testTag("source_chip_${source.id}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (source.isNsfw) NsfwRedBadge else CrimsonRed,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Genre / Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val genreCategories = listOf(
                        "all" to "Todos Gêneros",
                        "Shounen" to "Shounen",
                        "Sci-Fi" to "Sci-Fi 🚀",
                        "Shoujo" to "Shoujo",
                        "Yuri" to "Yuri",
                        "Ninja" to "Ninja 🥷",
                        "Ação" to "Ação",
                        "Aventura" to "Aventura",
                        "Fantasia" to "Fantasia 🪄",
                        "Isekai" to "Isekai",
                        "Romance" to "Romance ❤️",
                        "Seinen" to "Seinen",
                        "Comédia" to "Comédia 🎭",
                        "Sobrenatural" to "Sobrenatural 👻",
                        "Escolar" to "Escolar 🏫"
                    )

                    genreCategories.forEach { (genreKey, label) ->
                        val isSelected = state.selectedGenre == genreKey || (state.selectedGenre.isBlank() && genreKey == "all")
                        FilterChip(
                            selected = isSelected,
                            onClick = { onGenreSelected(genreKey) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag("genre_chip_$genreKey"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryPurple,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                if (adultContentEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                NsfwRedBadge.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = NsfwRedBadge,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Modo +18 Ativado: Exibindo fontes adultas no catálogo.",
                            color = NsfwRedBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Main Grid Content
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CrimsonRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Buscando fontes de mangá (MangaDex)...", fontSize = 14.sp)
                }
            }
        } else if (state.mangaList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (state.errorMessage != null) Icons.Default.Warning else Icons.Default.FilterList,
                        contentDescription = "Status",
                        tint = if (state.errorMessage != null) CrimsonRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.errorMessage != null) "Erro ao conectar à API" else "Nenhum mangá encontrado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.errorMessage ?: if (state.searchQuery.isNotBlank() || state.selectedGenre != "all") {
                            "Nenhum mangá corresponde aos filtros selecionados."
                        } else {
                            "A API do MangaDex pode estar oscilando ou a conexão falhou. Toque abaixo para tentar novamente."
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.searchQuery.isNotBlank() || state.selectedGenre != "all" || state.selectedSourceId != "all") {
                            OutlinedButton(
                                onClick = onClearFilters,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Limpar Filtros")
                            }
                        }

                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("retry_browse_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tentar Novamente")
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .background(AccentGold.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .clickable { onRefresh() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Exibindo dados em cache / offline. Toque para recarregar.",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = PrimaryPurple, modifier = Modifier.size(16.dp))
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.mangaList) { manga ->
                        val isFav = favoriteUrls.contains(manga.url)
                        MangaCard(
                            manga = manga,
                            onClick = { onMangaClick(manga) },
                            isFavorite = isFav,
                            onFavoriteToggle = { onFavoriteToggle(manga) }
                        )
                    }
                }
            }
        }
    }
}
