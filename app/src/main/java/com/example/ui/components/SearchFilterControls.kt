package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.source.*
import kotlin.math.roundToInt

val searchLanguages = listOf("pt-br" to "Português", "en" to "Inglês", "es" to "Espanhol", "all" to "Todos")
fun languageLabel(language: String) = searchLanguages.find { it.first == language }?.second ?: language

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterControls(request: BrowseFilterRequest, sources: List<MangaSource>, onChanged: (BrowseFilterRequest) -> Unit) {
    CatalogFilterEditor(request.filters, { onChanged(request.copy(filters = it)) }, listOf(FilterGroup.GENRE, FilterGroup.THEME))
    Text("Idioma dos capítulos", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        searchLanguages.forEach { (code, label) ->
            FilterChip(request.language == code, { onChanged(request.copy(language = code)) }, label = { Text(label) },
                modifier = Modifier.testTag("lang_chip_$code"))
        }
    }
    var sourceExpanded by remember { mutableStateOf(false) }
    TextButton(onClick = { sourceExpanded = !sourceExpanded }, modifier = Modifier.fillMaxWidth().testTag("filter_source_expand")) {
        Text("Fonte: " + (sources.find { it.id == request.sourceId }?.name ?: "Todas"), Modifier.weight(1f))
        Text(if (sourceExpanded) "−" else "+")
    }
    if (sourceExpanded) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(request.sourceId == "all", { onChanged(request.copy(sourceId = "all")) }, label = { Text("Todas") })
        sources.forEach { source ->
            FilterChip(request.sourceId == source.id, { onChanged(request.copy(sourceId = source.id)) },
                label = { Text(source.name) }, modifier = Modifier.testTag("source_chip_${source.id}"))
        }
    }
    CatalogFilterEditor(request.filters, { onChanged(request.copy(filters = it)) },
        listOf(FilterGroup.FORMAT, FilterGroup.ORIGIN, FilterGroup.PUBLIC, FilterGroup.STATUS), showCombine = false)
    Text("Ano de lançamento", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
    val maxYear = remember { maxOf(2026, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    val parts = request.yearRange.split('-')
    val start = parts.firstOrNull()?.toIntOrNull()?.coerceIn(1900, maxYear) ?: 1900
    val end = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(start, maxYear) ?: maxYear
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(request.yearRange == "all", { onChanged(request.copy(yearRange = "all")) }, label = { Text("Todos os anos") })
        FilterChip(request.yearRange == "unknown", { onChanged(request.copy(yearRange = "unknown")) },
            label = { Text("Sem data definida") }, modifier = Modifier.testTag("year_chip_unknown"))
    }
    Text("$start – $end", style = MaterialTheme.typography.bodySmall)
    RangeSlider(value = start.toFloat()..end.toFloat(), onValueChange = {
        onChanged(request.copy(yearRange = "${it.start.roundToInt()}-${it.endInclusive.roundToInt()}"))
    }, valueRange = 1900f..maxYear.toFloat(), steps = maxYear - 1901,
        enabled = request.yearRange != "unknown", modifier = Modifier.testTag("year_range_slider"))
    var startInput by remember(request.yearRange) { mutableStateOf(start.toString()) }
    var endInput by remember(request.yearRange) { mutableStateOf(end.toString()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(startInput, { text -> startInput = text; text.toIntOrNull()?.takeIf { it in 1900..end }?.let {
            onChanged(request.copy(yearRange = "$it-$end"))
        } }, label = { Text("De") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            modifier = Modifier.weight(1f).testTag("year_start_input"))
        OutlinedTextField(endInput, { text -> endInput = text; text.toIntOrNull()?.takeIf { it in start..maxYear }?.let {
            onChanged(request.copy(yearRange = "$start-$it"))
        } }, label = { Text("Até") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            modifier = Modifier.weight(1f).testTag("year_end_input"))
    }
    Spacer(Modifier.height(16.dp))
}
