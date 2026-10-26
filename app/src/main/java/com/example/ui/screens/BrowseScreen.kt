package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.source.MangaModel
import com.example.ui.components.MangaCard
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.PrimaryPurple
import com.example.ui.viewmodel.BrowseUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onQueryChange: (String) -> Unit = {},
    onSearchSubmit: (String) -> Unit = {},
    onLanguageSelected: (String) -> Unit = {},
    onSourceSelected: (String) -> Unit = {},
    onGenreSelected: (String) -> Unit = {},
    onMangaClick: (MangaModel) -> Unit,
    onFavoriteToggle: (MangaModel) -> Unit,
    favoriteUrls: Set<String>,
    onDeleteSearchHistoryItem: (Long) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onSearchCommitted: (String) -> Unit = {},
    onPeriodSelected: (com.example.source.PublicationPeriod?) -> Unit = {},
    onOpenWeb: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var queryInput by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }

    var searchFocused by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.isImeVisible
    val latestCommit by androidx.compose.runtime.rememberUpdatedState({ onSearchCommitted(queryInput) })
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { latestCommit() } }
    var imeWasVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(keyboardVisible) {
        if (imeWasVisible && !keyboardVisible) latestCommit()
        imeWasVisible = keyboardVisible
    }
    fun dismissSearchKeyboard() {
        onSearchCommitted(queryInput)
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Text("Filtros", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { showFilters = false; onOpenWeb() }, modifier = Modifier.testTag("open_web_sources")) { Text("Navegador web e gerenciamento de fontes") }
                Text("Idioma dos capítulos", modifier = Modifier.padding(top = 20.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("pt-br" to "Português", "en" to "Inglês", "es" to "Espanhol", "all" to "Todos").forEach { (code, label) ->
                        FilterChip(selected = state.selectedLanguage == code, onClick = { onLanguageSelected(code) },
                            label = { Text(label) }, modifier = Modifier.testTag("lang_chip_$code"))
                    }
                }
                Text("Fonte", modifier = Modifier.padding(top = 16.dp))
                Text("Para fontes EN ou ES, selecione o idioma correspondente ou Todos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.selectedSourceId == "all", onClick = { onSourceSelected("all") }, label = { Text("Todas") })
                    state.availableSources.forEach { source ->
                        FilterChip(selected = state.selectedSourceId == source.id, onClick = { onSourceSelected(source.id) },
                            label = { Text(source.name + when (source.lang) { "en" -> " · EN"; "es" -> " · ES"; else -> "" }) }, modifier = Modifier.testTag("source_chip_${source.id}"))
                    }
                }
                Text("Categorias", modifier = Modifier.padding(top = 16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "Todos", "Shounen" to "Shounen", "Sci-Fi" to "Sci-Fi", "Shoujo" to "Shoujo", "Yuri" to "Yuri",
                        "Ninja" to "Ninja", "Ação" to "Ação", "Aventura" to "Aventura", "Fantasia" to "Fantasia", "Isekai" to "Isekai",
                        "Romance" to "Romance", "Seinen" to "Seinen", "Comédia" to "Comédia", "Sobrenatural" to "Sobrenatural", "Escolar" to "Escolar").forEach { (key, label) ->
                        FilterChip(selected = state.selectedGenre == key, onClick = { onGenreSelected(key) },
                            label = { Text(label) }, modifier = Modifier.testTag("genre_chip_$key"))
                    }
                }
                Text("Ano original de publicação", modifier = Modifier.padding(top = 16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.publicationPeriod == null, onClick = { onPeriodSelected(null) }, label = { Text("Todas as épocas") })
                    com.example.source.PublicationPeriod.presets().forEach { period ->
                        FilterChip(selected = state.publicationPeriod == period, onClick = { onPeriodSelected(period) },
                            label = { Text(period.label) }, modifier = Modifier.testTag("period_${period.from}_${period.through}"))
                    }
                }
                Text("MangaDex e MangaDash permitem consultar por ano. WeebCentral filtra o catálogo carregado; LeerCapítulo e obras sem ano informado ficam fora do filtro. A data de envio dos capítulos não é usada.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Text("Categorias usam a classificação de cada fonte. Nome e categoria são combinados; a disponibilidade varia por fonte e idioma.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = { showFilters = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("close_filters_button")) { Text("Ver resultados") }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("browse_screen")
    ) {
        TextButton(onClick = onOpenWeb, modifier = Modifier.testTag("browse_web_sources")) { Text("Pesquisar na web / gerenciar fontes") }
        // Clean Minimal Header Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = queryInput,
                    onValueChange = { queryInput = it; onQueryChange(it) },
                    modifier = Modifier.weight(1f).onFocusChanged {
                        val wasFocused = searchFocused
                        searchFocused = it.isFocused
                        if (wasFocused && !it.isFocused) onSearchCommitted(queryInput)
                    }.testTag("search_input"),
                    placeholder = { Text("Buscar mangás") },
                    leadingIcon = {
                        IconButton(onClick = { dismissSearchKeyboard(); onSearchSubmit(queryInput) }, modifier = Modifier.testTag("search_icon_button")) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    },
                    trailingIcon = {
                        Row {
                            if (queryInput.isNotEmpty()) IconButton(onClick = { queryInput = ""; onSearchSubmit("") }, modifier = Modifier.testTag("search_clear_button")) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                            }
                            if (searchFocused || keyboardVisible) IconButton(onClick = { dismissSearchKeyboard() },
                                modifier = Modifier.testTag("hide_search_keyboard")) {
                                Icon(Icons.Default.KeyboardHide, contentDescription = "Ocultar teclado")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { dismissSearchKeyboard(); onSearchSubmit(queryInput) }),
                    shape = RoundedCornerShape(16.dp), singleLine = true
                )
                IconButton(onClick = { dismissSearchKeyboard(); showFilters = true }, modifier = Modifier.testTag("open_filters_button")) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filtros")
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.publicationPeriod?.let { period ->
                    InputChip(selected = false, onClick = { onPeriodSelected(null) }, label = { Text(period.label) },
                        trailingIcon = { Icon(Icons.Default.Close, "Remover período", Modifier.size(16.dp)) })
                }
                if (state.selectedLanguage != "all") {
                    val label = when (state.selectedLanguage) { "pt-br" -> "Português"; "en" -> "Inglês"; "es" -> "Espanhol"; else -> state.selectedLanguage }
                    InputChip(selected = false, onClick = { showFilters = true }, label = { Text(label) })
                }
                if (state.selectedGenre.isNotBlank() && state.selectedGenre != "all") InputChip(selected = false,
                    onClick = { onGenreSelected("all") }, label = { Text(state.selectedGenre) }, trailingIcon = { Icon(Icons.Default.Close, "Remover gênero", Modifier.size(16.dp)) })
                if (state.selectedSourceId != "all") InputChip(selected = false, onClick = { onSourceSelected("all") },
                    label = { Text(state.availableSources.find { it.id == state.selectedSourceId }?.name ?: state.selectedSourceId) },
                    trailingIcon = { Icon(Icons.Default.Close, "Remover fonte", Modifier.size(16.dp)) })
            }

            // Recent Search History Chips (if present)
            if (searchFocused && state.searchHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.History, "Buscas recentes", modifier = Modifier.size(16.dp).align(Alignment.CenterVertically))
                    state.searchHistory.take(5).forEach { history ->
                        InputChip(
                            selected = false,
                            onClick = {
                                dismissSearchKeyboard()
                                queryInput = history.query
                                onSearchSubmit(history.query)
                            },
                            label = { Text(history.query, fontSize = 11.sp) },
                            trailingIcon = {
                                IconButton(onClick = { onDeleteSearchHistoryItem(history.id) }) {
                                    Icon(Icons.Default.Close, "Remover do histórico", Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.testTag("search_history_chip_${history.id}")
                        )
                    }
                    TextButton(onClick = onClearSearchHistory, modifier = Modifier.testTag("clear_history_button")) {
                        Text("Limpar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

        }

        val isFilterOrSearchActive = state.publicationPeriod != null || state.searchQuery.isNotBlank() || (state.selectedGenre.isNotBlank() && state.selectedGenre != "all") || state.selectedSourceId != "all"

        // Some IMEs/OEMs dispatch Back to the app before dismissing the keyboard.
        // Editing must end before Back can clear the current search or filters.
        BackHandler(enabled = !showFilters && (searchFocused || keyboardVisible || isFilterOrSearchActive)) {
            if (searchFocused || keyboardVisible) dismissSearchKeyboard() else onClearFilters()
        }

        PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
        // Main Content Area
        if (state.isLoading && (isFilterOrSearchActive || (state.mangaList.isEmpty() && state.carouselSections.isEmpty()))) {
            Box(
                modifier = Modifier.fillMaxSize().testTag("browse_loading"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CrimsonRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Buscando fontes de mangá...", fontSize = 14.sp)
                }
            }
        } else if (isFilterOrSearchActive && state.mangaList.isEmpty() && !state.isLoading) {
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
                        text = state.errorMessage ?: "Nenhum mangá corresponde aos filtros selecionados.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onClearFilters,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("clear_filter_empty_button")
                        ) {
                            Text("Limpar filtros")
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
        } else if (isFilterOrSearchActive) {
            // Filtered / Search Results in Grid
            Column(modifier = Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.mangaList.size} resultados", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { if (state.searchQuery.isNotBlank()) onSearchSubmit("") else onClearFilters() }, modifier = Modifier.testTag("clear_filter_header_button")) {
                        Text(if (state.searchQuery.isNotBlank()) "Limpar busca" else "Limpar filtros")
                    }
                }

                if (state.errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
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
                            onClick = { dismissSearchKeyboard(); onMangaClick(manga) },
                            isFavorite = isFav,
                            onFavoriteToggle = { onFavoriteToggle(manga) },
                            showSource = state.mangaList.map { it.sourceId }.distinct().size > 1
                        )
                    }
                }
            }
        } else {
            // Home Carousels View
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_carousels_list"),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (state.errorMessage != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
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
                }

                val sectionsToRender = if (state.carouselSections.isNotEmpty()) {
                    state.carouselSections
                } else if (state.mangaList.isNotEmpty()) {
                    listOf(
                        com.example.source.GenreCarouselSection(
                            id = "popular",
                            title = "🔥 Mangás em Destaque",
                            genreKey = "all",
                            items = state.mangaList
                        )
                    )
                } else {
                    emptyList()
                }

                items(sectionsToRender, key = { it.id }) { section ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .testTag("carousel_section_${section.id}")
                    ) {
                        // Section Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = section.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${section.items.size})",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextButton(
                                onClick = {
                                    if (section.genreKey != "all") {
                                        onGenreSelected(section.genreKey)
                                    } else {
                                        onGenreSelected("Shounen")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("show_more_button_${section.id}")
                            ) {
                                Text(
                                    text = "Mostrar mais",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CrimsonRed
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Mostrar mais de ${section.title}",
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal carousel row
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("carousel_row_${section.id}"),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(section.items, key = { "${section.id}_${it.id}" }) { manga ->
                                val isFav = favoriteUrls.contains(manga.url)
                                MangaCard(
                                    manga = manga,
                                    onClick = { dismissSearchKeyboard(); onMangaClick(manga) },
                                    isFavorite = isFav,
                                    onFavoriteToggle = { onFavoriteToggle(manga) },
                                    showSource = section.items.map { it.sourceId }.distinct().size > 1,
                                    modifier = Modifier.width(135.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
