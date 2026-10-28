package com.example.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.KeyboardHide
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.LaunchedEffect
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
    onApplyFilters: ((BrowseFilterRequest) -> Unit)? = null,
    onDiagnoseMangaDex: () -> Unit = {},
    onDismissConnectionDiagnostic: () -> Unit = {},
    diagnosticsEnabled: Boolean = false,
    onBackFromSearch: (() -> Unit)? = null
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var queryInput by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }

    var searchFocused by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.isImeVisible
    fun dismissSearchKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    var filtersApplied by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    fun filterSummary(source: String, language: String, year: String, filters: CatalogFilters): String {
        val languageLabel = when (language) { "pt-br" -> "Português"; "en" -> "Inglês"; "es" -> "Espanhol"; "all" -> "Todos"; else -> language }
        val sourceLabel = if (source == "all") "Todas" else state.availableSources.find { it.id == source }?.name ?: source
        val yearLabel = when (year) { "all", "" -> "Todas"; "unknown", "none" -> "Sem data definida"; else -> year.replace("-", "–") }
        val originOptions = com.example.source.CatalogTaxonomy.options.filter { it.group == com.example.source.FilterGroup.ORIGIN }
        val origin = originOptions.filter { it.id in filters.included || it.id in filters.excluded }
            .joinToString(limit = 2, truncated = "…") { (if (it.id in filters.excluded) "Excluir " else "") + it.label }.ifBlank { "Todas" }
        val classifications = (filters.included + filters.excluded).filter { id -> originOptions.none { it.id == id } }
            .mapNotNull { com.example.source.CatalogTaxonomy.byId[it] }
            .joinToString(limit = 2, truncated = "…") { (if (it.id in filters.excluded) "Excluir " else "") + it.label }.ifBlank { "Todas" }
        val rules = if (filters.isActive) " · Inclusão: ${if (filters.includeMode == com.example.source.IncludeMode.ALL) "todas" else "qualquer uma"}" +
            (if (filters.excluded.isNotEmpty()) " · Sem classificação: ${if (filters.hideUnknownForExclusions) "ocultar" else "mostrar"}" else "") else ""
        return "Data: $yearLabel · Fonte: $sourceLabel · Idioma: $languageLabel · Origem: $origin · Classificação: $classifications$rules"
    }
    val activeSummary = filterSummary(state.selectedSourceId, state.selectedLanguage, state.selectedYearRange,
        state.catalogFilters.withLegacy(state.selectedGenre))

    val visibleError = if (diagnosticsEnabled) state.errorMessage else when {
        state.errorMessage == null -> null
        state.failure == com.example.ui.viewmodel.BrowseFailure.OFFLINE ->
            "Aguardando conexão para pesquisar novamente."
        state.failure == com.example.ui.viewmodel.BrowseFailure.TIMEOUT ->
            "A fonte demorou para responder. Tente novamente em instantes."
        state.mangaList.isNotEmpty() || state.carouselSections.isNotEmpty() ->
            "Algumas fontes não responderam. Exibindo os mangás disponíveis."
        else -> "Não foi possível acessar a fonte. Tente novamente em instantes."
    }
    LaunchedEffect(diagnosticsEnabled) {
        if (!diagnosticsEnabled) onDismissConnectionDiagnostic()
    }
    val diagnostic = state.connectionDiagnostic
    if (diagnosticsEnabled && (diagnostic.isRunning || diagnostic.report != null)) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissConnectionDiagnostic,
            title = { Text("Diagnóstico da MangaDex") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (diagnostic.isRunning) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Comparando três clientes com a mesma consulta. Isso pode levar alguns segundos.")
                    } else SelectionContainer {
                        Text(diagnostic.report.orEmpty(), modifier = Modifier.testTag("mangadex_diagnostic_report"))
                    }
                }
            },
            confirmButton = {
                if (diagnostic.report != null) TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(diagnostic.report))
                }, modifier = Modifier.testTag("copy_mangadex_diagnostic")) { Text("Copiar diagnóstico") }
            },
            dismissButton = {
                TextButton(onClick = onDismissConnectionDiagnostic) { Text(if (diagnostic.isRunning) "Cancelar" else "Fechar") }
            }
        )
    }

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
                Text(filterSummary(draftSource, draftLanguage, draftYear, draftFilters),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("sheet_filter_summary"))
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
            .imePadding()
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

            Text(activeSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).testTag("active_filter_summary"))

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
                }
            }

        }

        val isFilterOrSearchActive = state.catalogFilters.isActive || filtersApplied || state.searchQuery.isNotBlank() || (state.selectedGenre.isNotBlank() && state.selectedGenre != "all") || state.selectedSourceId != "all" || (state.selectedYearRange.isNotBlank() && state.selectedYearRange != "all")

        if (state.unclassifiedExcluded > 0) Text(
            "${state.unclassifiedExcluded} obras ocultadas: faltam dados para verificar as exclusões.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("filter_unknown_notice"))

        // Intercept back button to return to full carousel view when filtering
        BackHandler(enabled = !showFilters && (searchFocused || keyboardVisible || isFilterOrSearchActive || onBackFromSearch != null)) {
            if (searchFocused || keyboardVisible) dismissSearchKeyboard() else if (onBackFromSearch != null) onBackFromSearch() else {
                filtersApplied = false
                onClearFilters()
            }
        }

        PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
        // Main Content Area
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().testTag("browse_loading"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CrimsonRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (state.catalogFilters.isActive) "Aplicando filtros de classificação..." else "Buscando fontes de mangá...", fontSize = 14.sp)
                    if (state.selectedYearRange != "all") Text("A consulta por data pode levar mais tempo.", fontSize = 12.sp)
                }
            }
        } else if (state.mangaList.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Icon(
                        imageVector = if (state.errorMessage != null) Icons.Default.Warning else Icons.Default.FilterList,
                        contentDescription = "Status",
                        tint = if (state.errorMessage != null) CrimsonRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (state.failure) {
                            com.example.ui.viewmodel.BrowseFailure.OFFLINE -> "Sem conexão com a internet"
                            com.example.ui.viewmodel.BrowseFailure.TIMEOUT -> "A pesquisa demorou demais"
                            else -> if (state.errorMessage != null) "Não foi possível concluir a busca" else "Nenhum mangá encontrado"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = visibleError ?: "Nenhum mangá corresponde aos filtros selecionados.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.testTag("browse_error_details")
                        )
                    }
                    if (diagnosticsEnabled && state.errorMessage != null) {
                        TextButton(onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(state.errorMessage))
                        }, modifier = Modifier.testTag("copy_browse_error_button")) {
                            Text("Copiar erro completo")
                        }
                    }

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
                    if (diagnosticsEnabled && state.errorMessage != null && (state.selectedSourceId == "all" || state.selectedSourceId == "mangadex")) {
                        TextButton(onClick = onDiagnoseMangaDex,
                            modifier = Modifier.testTag("diagnose_mangadex_button")) { Text("Testar conexão com a MangaDex") }
                    }
                }
            }
        } else {
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
                            text = visibleError.orEmpty(),
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
        }
        }
    }
}
