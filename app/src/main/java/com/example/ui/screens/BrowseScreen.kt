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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.example.source.BrowseFilterRequest
import com.example.source.CatalogFilters
import com.example.source.CatalogTaxonomy
import com.example.ui.components.CatalogFilterEditor
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
    onYearSelected: (String) -> Unit = {},
    onMangaClick: (MangaModel) -> Unit,
    onFavoriteToggle: (MangaModel) -> Unit,
    favoriteUrls: Set<String>,
    onDeleteSearchHistoryItem: (Long) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onCatalogFiltersChanged: (CatalogFilters) -> Unit = {},
    onApplyFilters: ((BrowseFilterRequest) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var queryInput by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }

    var searchFocused by remember { mutableStateOf(false) }
    var filtersApplied by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    if (showFilters) {
        var draftSource by remember { mutableStateOf(state.selectedSourceId) }
        var draftLanguage by remember { mutableStateOf(state.selectedLanguage) }
        var draftYear by remember { mutableStateOf(state.selectedYearRange) }
        var draftFilters by remember { mutableStateOf(state.catalogFilters.withLegacy(state.selectedGenre)) }
        ModalBottomSheet(onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Text("Filtros", style = MaterialTheme.typography.titleLarge)
                Text("Idioma dos capítulos", modifier = Modifier.padding(top = 20.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("pt-br" to "Português", "en" to "Inglês", "es" to "Espanhol", "all" to "Todos").forEach { (code, label) ->
                        FilterChip(selected = draftLanguage == code, onClick = { draftLanguage = code },
                            label = { Text(label) }, modifier = Modifier.testTag("lang_chip_$code"))
                    }
                }
                Text("Fonte", modifier = Modifier.padding(top = 16.dp))
                Text("Para fontes EN ou ES, selecione o idioma correspondente ou Todos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draftSource == "all", onClick = { draftSource = "all" }, label = { Text("Todas") })
                    state.availableSources.forEach { source ->
                        FilterChip(selected = draftSource == source.id, onClick = { draftSource = source.id },
                            label = { Text(source.name + when (source.lang) { "en" -> " · EN"; "es" -> " · ES"; else -> "" }) }, modifier = Modifier.testTag("source_chip_${source.id}"))
                    }
                }
                Text(state.availableSources.find { it.id == draftSource }?.filterSupportNote
                    ?: "Cada fonte oferece classificações diferentes. Obras sem os dados necessários não confirmam uma inclusão.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp).testTag("filter_source_support"))
                CatalogFilterEditor(draftFilters, { draftFilters = it })
                Text("Ano de lançamento", modifier = Modifier.padding(top = 16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
                    val effectiveCurrentYear = if (currentYear >= 2020) currentYear else 2026
                    val yearRanges = remember(effectiveCurrentYear) {
                        listOf(
                            "all" to "Todas",
                            "1900-1950" to "1900 - 1950",
                            "1950-1960" to "1950 - 1960",
                            "1960-1970" to "1960 - 1970",
                            "1970-1980" to "1970 - 1980",
                            "1980-1990" to "1980 - 1990",
                            "1990-2000" to "1990 - 2000",
                            "2000-2010" to "2000 - 2010",
                            "2010-2020" to "2010 - 2020",
                            "2020-$effectiveCurrentYear" to "2020 - $effectiveCurrentYear",
                            "unknown" to "Sem data definida"
                        )
                    }
                    yearRanges.forEach { (key, label) ->
                        FilterChip(
                            selected = draftYear == key,
                            onClick = { draftYear = key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("year_chip_$key")
                        )
                    }
                }
            }
                Button(onClick = {
                    filtersApplied = true
                    showFilters = false
                    val request = BrowseFilterRequest(queryInput, draftSource, draftLanguage, draftYear, draftFilters)
                    if (onApplyFilters != null) onApplyFilters(request) else {
                        onSourceSelected(draftSource); onLanguageSelected(draftLanguage); onYearSelected(draftYear)
                        onCatalogFiltersChanged(draftFilters); onSearchSubmit(queryInput)
                    }
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).testTag("close_filters_button")) { Text("Ver resultados") }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("browse_screen")
    ) {
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
                    modifier = Modifier.weight(1f).onFocusChanged { searchFocused = it.isFocused }.testTag("search_input"),
                    placeholder = { Text("Buscar mangás") },
                    leadingIcon = {
                        IconButton(onClick = { keyboardController?.hide(); focusManager.clearFocus(); onSearchSubmit(queryInput) }, modifier = Modifier.testTag("search_icon_button")) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    },
                    trailingIcon = {
                        if (queryInput.isNotEmpty()) IconButton(onClick = { queryInput = ""; onSearchSubmit("") }, modifier = Modifier.testTag("search_clear_button")) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide(); focusManager.clearFocus(); onSearchSubmit(queryInput) }),
                    shape = RoundedCornerShape(16.dp), singleLine = true
                )
                IconButton(onClick = { keyboardController?.hide(); focusManager.clearFocus(); showFilters = true }, modifier = Modifier.testTag("open_filters_button")) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filtros")
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.selectedLanguage != "all") {
                    val label = when (state.selectedLanguage) { "pt-br" -> "Português"; "en" -> "Inglês"; "es" -> "Espanhol"; else -> state.selectedLanguage }
                    InputChip(selected = false, onClick = { showFilters = true }, label = { Text(label) })
                }
                CatalogTaxonomy.options.filter { it.id in state.catalogFilters.included || it.id in state.catalogFilters.excluded }.forEach { option ->
                    val excluded = option.id in state.catalogFilters.excluded
                    InputChip(selected = false, onClick = { onCatalogFiltersChanged(state.catalogFilters.without(option.id)) },
                        label = { Text((if (excluded) "− " else "+ ") + option.label) },
                        trailingIcon = { Icon(Icons.Default.Close, "Remover filtro ${option.label}", Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("active_filter_${option.id}"))
                }
                if (state.selectedYearRange.isNotBlank() && state.selectedYearRange != "all") {
                    val activeYearLabel = when (state.selectedYearRange) {
                        "unknown" -> "Sem data definida"
                        else -> state.selectedYearRange.replace("-", " - ")
                    }
                    InputChip(
                        selected = false,
                        onClick = { onYearSelected("all") },
                        label = { Text(activeYearLabel) },
                        trailingIcon = { Icon(Icons.Default.Close, "Remover ano", Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("active_year_chip")
                    )
                }
                if (state.selectedSourceId != "all") InputChip(selected = false, onClick = { onSourceSelected("all") },
                    label = { Text(state.availableSources.find { it.id == state.selectedSourceId }?.name ?: state.selectedSourceId) },
                    trailingIcon = { Icon(Icons.Default.Close, "Remover fonte", Modifier.size(16.dp)) })
            }

            // Recent Search History Chips (if present)
            if (searchFocused && queryInput.isBlank() && state.searchHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
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
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Recentes",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = onClearSearchHistory,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("clear_history_button")
                    ) {
                        Text(
                            text = "Limpar",
                            fontSize = 10.sp,
                            color = CrimsonRed
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.searchHistory.forEach { history ->
                        InputChip(
                            selected = false,
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
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
                }
            }

        }

        val isFilterOrSearchActive = state.catalogFilters.isActive || filtersApplied || state.searchQuery.isNotBlank() || (state.selectedGenre.isNotBlank() && state.selectedGenre != "all") || state.selectedSourceId != "all" || (state.selectedYearRange.isNotBlank() && state.selectedYearRange != "all")

        if (state.unclassifiedExcluded > 0) Text(
            "${state.unclassifiedExcluded} obras ocultadas: faltam dados para verificar as exclusões.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("filter_unknown_notice"))

        // Intercept back button to return to full carousel view when filtering
        BackHandler(enabled = isFilterOrSearchActive && !showFilters) {
            filtersApplied = false
            onClearFilters()
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
                    Text(if (state.catalogFilters.isActive) "Aplicando filtros de classificação..." else "Buscando fontes de mangá...", fontSize = 14.sp)
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
                        text = if (state.errorMessage != null) "Não foi possível concluir a busca" else "Nenhum mangá encontrado",
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
                            onClick = { filtersApplied = false; onClearFilters() },
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
                    TextButton(onClick = { if (state.searchQuery.isNotBlank()) onSearchSubmit("") else { filtersApplied = false; onClearFilters() } }, modifier = Modifier.testTag("clear_filter_header_button")) {
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
                            text = state.errorMessage.orEmpty(),
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
                                text = state.errorMessage.orEmpty(),
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
                                    onClick = { onMangaClick(manga) },
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
