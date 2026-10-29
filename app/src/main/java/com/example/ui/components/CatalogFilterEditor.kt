package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.source.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogFilterEditor(
    filters: CatalogFilters,
    onChanged: (CatalogFilters) -> Unit,
    isNsfwEnabled: Boolean = false,
    groups: List<FilterGroup> = if (isNsfwEnabled) listOf(FilterGroup.CONTENT_RATING, FilterGroup.GENRE, FilterGroup.THEME, FilterGroup.FORMAT, FilterGroup.ORIGIN, FilterGroup.PUBLIC, FilterGroup.STATUS)
        else listOf(FilterGroup.GENRE, FilterGroup.THEME, FilterGroup.FORMAT, FilterGroup.ORIGIN, FilterGroup.PUBLIC, FilterGroup.STATUS),
    showCombine: Boolean = true
) {
    var search by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(if (isNsfwEnabled) setOf(FilterGroup.CONTENT_RATING, FilterGroup.GENRE, FilterGroup.THEME) else setOf(FilterGroup.GENRE, FilterGroup.THEME)) }
    if (showCombine) {
        OutlinedTextField(search, { search = it }, label = { Text("Buscar gênero ou tema") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("filter_classification_search"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Combinar:")
            FilterChip(filters.includeMode == IncludeMode.ALL, { onChanged(filters.copy(includeMode = IncludeMode.ALL)) },
                label = { Text("E") }, modifier = Modifier.testTag("filter_mode_all"))
            FilterChip(filters.includeMode == IncludeMode.ANY, { onChanged(filters.copy(includeMode = IncludeMode.ANY)) },
                label = { Text("OU") }, modifier = Modifier.testTag("filter_mode_any"))
        }
        Text("E exige todos; OU aceita qualquer incluído. Exclusões sempre prevalecem.", style = MaterialTheme.typography.bodySmall)
    }

    if (isNsfwEnabled) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("🔞 Filtros rápidos +18:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "rating:pornographic" to "Pornográfico (18+)",
                "rating:erotica" to "Erótico",
                "genre:doujinshi" to "Doujinshi",
                "genre:hentai" to "Hentai"
            ).forEach { (id, label) ->
                val isIncluded = filters.selection(id) == FilterSelection.INCLUDE
                FilterChip(
                    selected = isIncluded,
                    onClick = {
                        val next = if (isIncluded) FilterSelection.NEUTRAL else FilterSelection.INCLUDE
                        onChanged(filters.select(id, next))
                    },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.testTag("quick_nsfw_$id")
                )
            }
        }
    }

    for (group in groups) {
        if (!isNsfwEnabled && group == FilterGroup.CONTENT_RATING) continue
        val choices = CatalogTaxonomy.options.filter { it.group == group }.filter {
            if (!isNsfwEnabled && (it.id in setOf("rating:pornographic", "rating:erotica", "genre:hentai", "genre:smut", "genre:sexual_violence"))) false
            else search.isBlank() || (it.aliases + it.label).any { label -> normalizedClassification(label).contains(normalizedClassification(search)) }
        }
        if (choices.isEmpty()) continue
        val count = choices.count { filters.selection(it.id) != FilterSelection.NEUTRAL }
        TextButton(onClick = { expanded = if (group in expanded) expanded - group else expanded + group },
            modifier = Modifier.fillMaxWidth().testTag("filter_group_${group.name}")) {
            Text(group.label + if (count > 0) " ($count)" else "", Modifier.weight(1f))
            Text(if (group in expanded || search.isNotBlank()) "−" else "+")
        }
        if (group in expanded || search.isNotBlank()) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                choices.forEach { option ->
                    val selection = filters.selection(option.id)
                    val excluded = selection == FilterSelection.EXCLUDE
                    FilterChip(selection != FilterSelection.NEUTRAL, { onChanged(filters.cycle(option.id)) },
                        label = { Text(option.label, textDecoration = if (excluded) TextDecoration.LineThrough else TextDecoration.None) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (excluded) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = if (excluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer),
                        modifier = Modifier.testTag("filter_option_${option.id}").semantics {
                            contentDescription = "${option.label}: ${when (selection) { FilterSelection.INCLUDE -> "incluído"; FilterSelection.EXCLUDE -> "excluído"; else -> "sem filtro" }}"
                        })
                }
            }
        }
    }
    if (showCombine && filters.excluded.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(filters.hideUnknownForExclusions, { onChanged(filters.copy(hideUnknownForExclusions = it)) }, Modifier.testTag("filter_hide_unknown"))
        Text("Ocultar obras sem dados para verificar exclusões", style = MaterialTheme.typography.bodySmall)
    }
}
