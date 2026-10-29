package com.example.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.home.*
import com.example.source.MangaSource
import kotlin.math.abs

@Composable
fun HomeSectionsEditor(sections: List<HomeSection>, sources: List<MangaSource>, onChanged: (List<HomeSection>) -> Unit) {
    var local by remember { mutableStateOf(sections) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<HomeSection?>(null) }
    val current by rememberUpdatedState(local)
    val rowHeight = with(LocalDensity.current) { 64.dp.toPx() }
    LaunchedEffect(sections) { if (dragged == null) local = sections }
    fun save(items: List<HomeSection>) { local = HomeSections.normalize(items); onChanged(local) }
    Card(Modifier.fillMaxWidth().testTag("home_sections_settings")) {
        Column(Modifier.padding(12.dp)) {
            Text("Tela inicial", style = MaterialTheme.typography.titleMedium)
            Text("Ative, edite ou arraste as seções pela alça.", style = MaterialTheme.typography.bodySmall)
            local.sortedBy { it.position }.forEach { section -> key(section.id) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Mover ${section.title} para cima") {
                            save(HomeSections.move(current, section.id, current.indexOfFirst { it.id == section.id } - 1)); true
                        },
                        CustomAccessibilityAction("Mover ${section.title} para baixo") {
                            save(HomeSections.move(current, section.id, current.indexOfFirst { it.id == section.id } + 1)); true
                        })
                }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DragHandle, "Arrastar ${section.title}", Modifier.size(40.dp)
                        .testTag("home_drag_${section.id}").pointerInput(section.id) {
                            var distance = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragged = section.id; distance = 0f },
                                onDragEnd = { dragged = null; save(current) },
                                onDragCancel = { dragged = null; local = sections },
                                onDrag = { change, amount ->
                                    change.consume(); distance += amount.y
                                    if (abs(distance) >= rowHeight) {
                                        val index = current.indexOfFirst { it.id == section.id }
                                        val target = index + if (distance > 0) 1 else -1
                                        local = HomeSections.move(current, section.id, target)
                                        distance = 0f
                                    }
                                })
                        })
                    Text(section.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Switch(section.active, { active -> save(current.map { if (it.id == section.id) it.copy(active = active) else it }) },
                        modifier = Modifier.testTag("home_active_${section.id}"))
                    IconButton(onClick = { editing = section }) { Icon(Icons.Default.Edit, "Editar ${section.title}", Modifier.size(18.dp)) }
                    IconButton(onClick = { save(current.filterNot { it.id == section.id }) }) { Icon(Icons.Default.Delete, "Excluir ${section.title}", Modifier.size(18.dp)) }
                }
            } }
            Row {
                TextButton(onClick = { editing = HomeSection(position = current.size) }, modifier = Modifier.testTag("new_home_section")) { Text("Nova seção") }
                TextButton(onClick = { save(HomeSections.defaults) }, modifier = Modifier.testTag("reset_home_sections")) { Text("Restaurar padrão") }
            }
        }
    }
    editing?.let { original ->
        var draft by remember(original.id) { mutableStateOf(original) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(if (original.id in current.map { it.id }) "Editar seção" else "Nova seção") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(draft.title, { draft = draft.copy(title = it) }, label = { Text("Nome da seção") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("home_section_title"))
                    Text("Quantidade de itens", Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 20, 30).forEach { count -> FilterChip(draft.itemCount == count, { draft = draft.copy(itemCount = count) }, label = { Text("$count") }) }
                    }
                    if (draft.type == HomeSectionType.CATALOG) {
                        Text("Ordenação")
                        HomeSort.entries.forEach { sort ->
                            FilterChip(draft.sort == sort, { draft = draft.copy(sort = sort) }, label = { Text(sort.label) })
                        }
                        if (draft.sort == HomeSort.RATING) Text("Sem notas fornecidas pela fonte, mantém a ordem do catálogo.", style = MaterialTheme.typography.bodySmall)
                        SearchFilterControls(draft.filters, sources) { draft = draft.copy(filters = it) }
                    } else Text("Esta seção usa seu histórico de leitura.", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = {
                TextButton(enabled = draft.title.isNotBlank(), onClick = {
                    save(if (current.any { it.id == draft.id }) current.map { if (it.id == draft.id) draft else it } else current + draft)
                    editing = null
                }, modifier = Modifier.testTag("save_home_section")) { Text("Salvar") }
            }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } })
    }
}
