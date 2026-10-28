package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.source.*

@Composable
fun CatalogFilterEditor(filters: CatalogFilters, onChanged: (CatalogFilters) -> Unit) {
    var search by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf(FilterGroup.GENRE)) }
    Text("Classificação da obra", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
    Text("Inclua o que quer encontrar e exclua o que não quer ver. Exclusões sempre prevalecem.",
        style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(search, { search = it }, label = { Text("Buscar classificação") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("filter_classification_search"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(filters.includeMode == IncludeMode.ALL, { onChanged(filters.copy(includeMode = IncludeMode.ALL)) },
            label = { Text("Todos os incluídos") }, modifier = Modifier.testTag("filter_mode_all"))
        FilterChip(filters.includeMode == IncludeMode.ANY, { onChanged(filters.copy(includeMode = IncludeMode.ANY)) },
            label = { Text("Qualquer incluído") }, modifier = Modifier.testTag("filter_mode_any"))
    }
    for (group in FilterGroup.entries) {
        val choices = CatalogTaxonomy.options.filter { it.group == group }.filter {
            search.isBlank() || (it.aliases + it.label).any { label -> normalizedClassification(label).contains(normalizedClassification(search)) }
        }
        if (choices.isEmpty()) continue
        val count = choices.count { filters.selection(it.id) != FilterSelection.NEUTRAL }
        TextButton(onClick = { expanded = if (group in expanded) expanded - group else expanded + group },
            modifier = Modifier.fillMaxWidth().testTag("filter_group_${group.name}")) {
            Text(group.label + if (count > 0) " ($count)" else "", modifier = Modifier.weight(1f))
            Text(if (group in expanded || search.isNotBlank()) "−" else "+")
        }
        if (group in expanded || search.isNotBlank()) {
            choices.forEach { option ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(option.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    FilterChip(filters.selection(option.id) == FilterSelection.INCLUDE,
                        { onChanged(filters.select(option.id, FilterSelection.INCLUDE)) }, label = { Text("Incluir") },
                        modifier = Modifier.testTag("include_${option.id}").semantics { contentDescription = "Incluir ${option.label}" })
                    FilterChip(filters.selection(option.id) == FilterSelection.EXCLUDE,
                        { onChanged(filters.select(option.id, FilterSelection.EXCLUDE)) }, label = { Text("Excluir") },
                        modifier = Modifier.testTag("exclude_${option.id}").semantics { contentDescription = "Excluir ${option.label}" })
                }
            }
        }
    }
    if (filters.excluded.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(filters.hideUnknownForExclusions, { onChanged(filters.copy(hideUnknownForExclusions = it)) },
                modifier = Modifier.testTag("filter_hide_unknown"))
            Text("Ocultar obras sem dados para verificar as exclusões", style = MaterialTheme.typography.bodySmall)
        }
    }
    TextButton(onClick = { onChanged(CatalogFilters()) }, modifier = Modifier.testTag("clear_classification_filters")) {
        Text("Limpar classificação")
    }
}
