package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.DownloadEntity
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.NsfwRedBadge
import com.example.ui.theme.PrimaryPurple
import com.example.ui.viewmodel.MangaDetailUiState

@Composable
fun MangaDetailScreen(
    state: MangaDetailUiState,
    downloads: List<DownloadEntity>,
    selectedLanguage: String = "pt-br",
    onLanguageSelected: (String) -> Unit = {},
    onBack: () -> Unit,
    onToggleFavorite: (MangaModel) -> Unit,
    onChapterClick: (ChapterModel) -> Unit,
    onDownloadChapter: (ChapterModel) -> Unit,
    onDownloadMultipleChapters: (List<ChapterModel>) -> Unit = {},
    onDeleteDownload: (String) -> Unit
) {
    val manga = state.manga ?: return
    val context = LocalContext.current

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedChapterIds by remember { mutableStateOf(setOf<String>()) }
    var sortAscending by remember { mutableStateOf(false) }
    var chapterSearchQuery by remember { mutableStateOf("") }
    var isGroupingEnabled by remember { mutableStateOf(true) }

    val sortedChapters = remember(state.chapters, sortAscending, chapterSearchQuery) {
        val list = if (sortAscending) {
            state.chapters.sortedBy { it.chapterNumber }
        } else {
            state.chapters.sortedByDescending { it.chapterNumber }
        }
        if (chapterSearchQuery.isBlank()) {
            list
        } else {
            list.filter { it.title.contains(chapterSearchQuery.trim(), ignoreCase = true) }
        }
    }

    val chapterChunks = remember(sortedChapters) {
        sortedChapters.chunked(50)
    }

    var expandedGroups by remember(state.chapters.size, sortAscending, isGroupingEnabled) {
        val initialSet = mutableSetOf<Int>()
        if (sortedChapters.size <= 50) {
            initialSet.add(0)
        } else {
            val progressChapterId = state.readingProgress?.chapterId
            val activeChunkIndex = if (progressChapterId != null) {
                chapterChunks.indexOfFirst { chunk -> chunk.any { it.id == progressChapterId } }
            } else -1
            if (activeChunkIndex != -1) {
                initialSet.add(activeChunkIndex)
            } else {
                initialSet.add(0)
            }
        }
        mutableStateOf(initialSet.toSet())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("manga_detail_screen")
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Header Hero Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    AsyncImage(
                        model = manga.coverUrl,
                        contentDescription = manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Black.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )

                    // Top Bar Back Button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }

                    // Marcador Fixo de Favorito no topo da tela ao abrir o mangá
                    IconButton(
                        onClick = { onToggleFavorite(manga) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(
                                if (state.isFavorite) CrimsonRed else Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .testTag("favorite_fixed_marker")
                    ) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (state.isFavorite) "Remover dos Favoritos" else "Adicionar aos Favoritos",
                            tint = Color.White
                        )
                    }

                    // Cover + Header info
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(8.dp),
                            modifier = Modifier
                                .width(110.dp)
                                .aspectRatio(0.7f)
                        ) {
                            AsyncImage(
                                model = manga.coverUrl,
                                contentDescription = manga.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            if (manga.isNsfw) {
                                Text(
                                    text = "CONTEÚDO RESTRITO (+18)",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(NsfwRedBadge, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = manga.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                maxLines = 2
                            )
                            val authorText = manga.author.takeIf {
                                it.isNotBlank() && !it.equals("Autor Desconhecido", ignoreCase = true)
                            }
                            if (authorText != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Autor: $authorText",
                                    color = AccentGold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Description & Action
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sinopse",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cleanDescription(manga.description),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reading progress resume banner if available
                    val progress = state.readingProgress
                    if (progress != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PrimaryPurple.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("continue_reading_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = PrimaryPurple
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Continuar Leitura",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PrimaryPurple
                                    )
                                    Text(
                                        text = "${formatChapterTitle(progress.chapterTitle)} • Pág ${progress.lastPageRead + 1}/${progress.totalPages}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                val continueChapter = state.chapters.find { it.id == progress.chapterId }
                                if (continueChapter != null) {
                                    Button(
                                        onClick = { onChapterClick(continueChapter) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                        modifier = Modifier.testTag("continue_button")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Continuar")
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (state.chapters.isNotEmpty()) {
                        // Banner de início direto para novos leitores
                        val firstChapter = state.chapters.lastOrNull() ?: state.chapters.first()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CrimsonRed.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_reading_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = CrimsonRed
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Começar Leitura",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CrimsonRed
                                    )
                                    Text(
                                        text = formatChapterTitle(firstChapter.title),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                                Button(
                                    onClick = { onChapterClick(firstChapter) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                    modifier = Modifier.testTag("start_reading_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ler Agora")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (isSelectionMode) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PrimaryPurple.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("multi_download_banner")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Modo de Seleção (${selectedChapterIds.size} selecionados)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PrimaryPurple
                                    )

                                    IconButton(
                                        onClick = {
                                            isSelectionMode = false
                                            selectedChapterIds = emptySet()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancelar Seleção",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedChapterIds = state.chapters.map { it.id }.toSet()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Todos", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { selectedChapterIds = emptySet() },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Limpar", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val selectedList = state.chapters.filter { it.id in selectedChapterIds }
                                            if (selectedList.isNotEmpty()) {
                                                onDownloadMultipleChapters(selectedList)
                                                Toast.makeText(
                                                    context,
                                                    "${selectedList.size} capítulos enfileirados em segundo plano!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                isSelectionMode = false
                                                selectedChapterIds = emptySet()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Nenhum capítulo selecionado",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .testTag("download_selected_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DownloadForOffline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Baixar (${selectedChapterIds.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Capítulos (${state.chapters.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Botão claro de ordenação dos capítulos
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrimaryPurple.copy(alpha = 0.12f))
                                        .clickable { sortAscending = !sortAscending }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("toggle_sort_order_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = if (sortAscending) "Ordenar: Mais Recentes Primeiro" else "Ordenar: Do Começo",
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (sortAscending) "Crescente ⬆" else "Decrescente ⬇",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryPurple
                                    )
                                }
                            }

                            // Chips de seleção rápida de idioma
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val languages = listOf(
                                    "pt-br" to "🇧🇷 PT-BR",
                                    "en" to "🇺🇸 EN",
                                    "es" to "🇪🇸 ES",
                                    "all" to "🌐 Todas"
                                )
                                languages.forEach { (code, label) ->
                                    val isSelected = if (code == "all") {
                                        selectedLanguage.lowercase() !in listOf("pt-br", "en", "es") || selectedLanguage.isEmpty() || selectedLanguage == "all"
                                    } else {
                                        selectedLanguage.equals(code, ignoreCase = true)
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onLanguageSelected(code) },
                                        label = { Text(label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CrimsonRed.copy(alpha = 0.2f),
                                            selectedLabelColor = CrimsonRed
                                        ),
                                        modifier = Modifier.testTag("filter_chip_$code")
                                    )
                                }
                            }

                            if (state.chapters.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { isSelectionMode = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("toggle_selection_mode_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Checklist,
                                            contentDescription = null,
                                            tint = PrimaryPurple,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Selecionar",
                                            fontSize = 13.sp,
                                            color = PrimaryPurple,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val unDownloaded = state.chapters.filter { ch ->
                                                downloads.none { it.chapterId == ch.id && it.downloadState == DownloadEntity.STATE_COMPLETED }
                                            }
                                            if (unDownloaded.isNotEmpty()) {
                                                onDownloadMultipleChapters(unDownloaded)
                                                Toast.makeText(
                                                    context,
                                                    "${unDownloaded.size} capítulos enfileirados para download em segundo plano!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Todos os capítulos já foram baixados!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple.copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("download_all_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            tint = PrimaryPurple,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Baixar Todos",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryPurple,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Filtro rápido de capítulos quando há muitos capítulos
                            if (state.chapters.size > 3) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = chapterSearchQuery,
                                    onValueChange = { chapterSearchQuery = it },
                                    placeholder = { Text("Filtrar capítulos (ex: 12)", fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (chapterSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { chapterSearchQuery = "" }) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Limpar busca",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chapter_search_input"),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    )
                                )
                            }

                            // Grouping controls (50-in-50) & Expand/Collapse All
                            if (state.chapters.size > 20) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = isGroupingEnabled,
                                            onClick = { isGroupingEnabled = true },
                                            label = { Text("Agrupado (50)", fontSize = 12.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryPurple.copy(alpha = 0.2f),
                                                selectedLabelColor = PrimaryPurple
                                            ),
                                            modifier = Modifier.testTag("group_by_50_chip")
                                        )

                                        FilterChip(
                                            selected = !isGroupingEnabled,
                                            onClick = { isGroupingEnabled = false },
                                            label = { Text("Lista Completa", fontSize = 12.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Checklist,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryPurple.copy(alpha = 0.2f),
                                                selectedLabelColor = PrimaryPurple
                                            ),
                                            modifier = Modifier.testTag("full_list_chip")
                                        )
                                    }

                                    if (isGroupingEnabled && chapterSearchQuery.isBlank()) {
                                        TextButton(
                                            onClick = {
                                                expandedGroups = if (expandedGroups.size == chapterChunks.size) {
                                                    emptySet()
                                                } else {
                                                    chapterChunks.indices.toSet()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("toggle_all_groups_button")
                                        ) {
                                            Text(
                                                text = if (expandedGroups.size == chapterChunks.size) "Recolher Todos" else "Expandir Todos",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PrimaryPurple
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Chapters List
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryPurple)
                    }
                }
            } else if (state.chapters.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val langLabel = when (selectedLanguage.lowercase()) {
                            "pt-br" -> "Português (PT-BR)"
                            "en" -> "Inglês (EN)"
                            "es" -> "Espanhol (ES)"
                            else -> "Todas as línguas"
                        }
                        Text(
                            text = "Nenhum capítulo disponível em $langLabel.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tentar outro idioma:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "pt-br" to "🇧🇷 PT",
                                "en" to "🇺🇸 EN",
                                "es" to "🇪🇸 ES",
                                "all" to "🌐 Todas"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = selectedLanguage.equals(code, ignoreCase = true),
                                    onClick = { onLanguageSelected(code) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("detail_lang_chip_$code"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonRed,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            } else if (sortedChapters.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum capítulo encontrado para \"$chapterSearchQuery\".",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isGroupingEnabled && chapterSearchQuery.isBlank() && sortedChapters.size > 20) {
                // Modo Agrupado de 50 em 50
                chapterChunks.forEachIndexed { chunkIndex, chunk ->
                    val isExpanded = expandedGroups.contains(chunkIndex)
                    val hasReadingProgress = chunk.any { it.id == state.readingProgress?.chapterId }
                    val allDownloaded = chunk.isNotEmpty() && chunk.all { ch ->
                        downloads.any { it.chapterId == ch.id && it.downloadState == DownloadEntity.STATE_COMPLETED }
                    }
                    val allSelectedInGroup = chunk.isNotEmpty() && chunk.all { it.id in selectedChapterIds }

                    item(key = "group_header_$chunkIndex") {
                        ChapterGroupHeader(
                            groupTitle = getChunkTitle(chunk, sortAscending, chunkIndex, chapterChunks.size),
                            chapterCount = chunk.size,
                            isExpanded = isExpanded,
                            hasReadingProgress = hasReadingProgress,
                            allDownloaded = allDownloaded,
                            isSelectionMode = isSelectionMode,
                            allSelectedInGroup = allSelectedInGroup,
                            onToggleExpand = {
                                expandedGroups = if (isExpanded) {
                                    expandedGroups - chunkIndex
                                } else {
                                    expandedGroups + chunkIndex
                                }
                            },
                            onToggleSelectGroup = {
                                val chunkIds = chunk.map { it.id }.toSet()
                                selectedChapterIds = if (allSelectedInGroup) {
                                    selectedChapterIds - chunkIds
                                } else {
                                    selectedChapterIds + chunkIds
                                }
                            }
                        )
                    }

                    if (isExpanded) {
                        items(chunk, key = { it.id }) { chapter ->
                            ChapterRowItem(
                                chapter = chapter,
                                isCurrentReading = state.readingProgress?.chapterId == chapter.id,
                                downloadRecord = downloads.find { it.chapterId == chapter.id },
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedChapterIds.contains(chapter.id),
                                onToggleSelect = {
                                    selectedChapterIds = if (selectedChapterIds.contains(chapter.id)) {
                                        selectedChapterIds - chapter.id
                                    } else {
                                        selectedChapterIds + chapter.id
                                    }
                                },
                                onChapterClick = { onChapterClick(chapter) },
                                onLongPress = {
                                    isSelectionMode = true
                                    selectedChapterIds = setOf(chapter.id)
                                },
                                onDownloadChapter = { onDownloadChapter(chapter) },
                                onDeleteDownload = { onDeleteDownload(chapter.id) }
                            )
                        }
                    }
                }
            } else {
                // Modo Lista Completa (ou quando há busca ativa)
                if (chapterSearchQuery.isNotBlank()) {
                    item {
                        Text(
                            text = "Resultados da busca: ${sortedChapters.size} capítulos",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                items(sortedChapters, key = { it.id }) { chapter ->
                    ChapterRowItem(
                        chapter = chapter,
                        isCurrentReading = state.readingProgress?.chapterId == chapter.id,
                        downloadRecord = downloads.find { it.chapterId == chapter.id },
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedChapterIds.contains(chapter.id),
                        onToggleSelect = {
                            selectedChapterIds = if (selectedChapterIds.contains(chapter.id)) {
                                selectedChapterIds - chapter.id
                            } else {
                                selectedChapterIds + chapter.id
                            }
                        },
                        onChapterClick = { onChapterClick(chapter) },
                        onLongPress = {
                            isSelectionMode = true
                            selectedChapterIds = setOf(chapter.id)
                        },
                        onDownloadChapter = { onDownloadChapter(chapter) },
                        onDeleteDownload = { onDeleteDownload(chapter.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterGroupHeader(
    groupTitle: String,
    chapterCount: Int,
    isExpanded: Boolean,
    hasReadingProgress: Boolean,
    allDownloaded: Boolean,
    isSelectionMode: Boolean,
    allSelectedInGroup: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelectGroup: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onToggleExpand)
            .testTag("chapter_group_header_$groupTitle"),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) {
                PrimaryPurple.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = PrimaryPurple,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = groupTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (hasReadingProgress) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lendo",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(PrimaryPurple, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (allDownloaded) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Baixado",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$chapterCount capítulos",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectionMode) {
                    TextButton(
                        onClick = onToggleSelectGroup,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (allSelectedInGroup) "Desmarcar 50" else "Marcar 50",
                            fontSize = 12.sp,
                            color = PrimaryPurple,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Recolher" else "Expandir",
                        tint = PrimaryPurple
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRowItem(
    chapter: ChapterModel,
    isCurrentReading: Boolean,
    downloadRecord: DownloadEntity?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onChapterClick: () -> Unit,
    onLongPress: () -> Unit,
    onDownloadChapter: () -> Unit,
    onDeleteDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelectionMode) {
                    Modifier.clickable(onClick = onToggleSelect)
                } else {
                    Modifier.pointerInput(chapter.id) {
                        detectTapGestures(
                            onTap = { onChapterClick() },
                            onLongPress = { onLongPress() }
                        )
                    }
                }
            )
            .testTag("chapter_item_${chapter.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                PrimaryPurple.copy(alpha = 0.25f)
            } else if (isCurrentReading) {
                PrimaryPurple.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = PrimaryPurple),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("chapter_checkbox_${chapter.id}")
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatChapterTitle(chapter.title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isCurrentReading || isSelected) PrimaryPurple else MaterialTheme.colorScheme.onSurface
                )
                if (chapter.dateUpload.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = chapter.dateUpload,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Download Action / Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (downloadRecord?.downloadState) {
                    DownloadEntity.STATE_COMPLETED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Capítulo Baixado",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onDeleteDownload,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Excluir Download",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    DownloadEntity.STATE_DOWNLOADING -> {
                        val percent = (downloadRecord.downloadProgress * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { downloadRecord.downloadProgress },
                                modifier = Modifier.size(18.dp),
                                color = PrimaryPurple,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$percent%",
                                fontSize = 11.sp,
                                color = PrimaryPurple,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    else -> {
                        IconButton(
                            onClick = onDownloadChapter,
                            modifier = Modifier.size(32.dp).testTag("download_button_${chapter.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Baixar Capítulo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Ler Capítulo",
                    tint = PrimaryPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatChapterNumDisplay(num: Float): String {
    return if (num % 1f == 0f) num.toInt().toString() else num.toString()
}

private fun getChunkTitle(chunk: List<ChapterModel>, sortAscending: Boolean, chunkIndex: Int, totalChunks: Int): String {
    if (chunk.isEmpty()) return "Grupo ${chunkIndex + 1}"
    val nums = chunk.map { it.chapterNumber }.filter { it > 0 }
    return if (nums.isNotEmpty()) {
        val firstNum = chunk.first().chapterNumber
        val lastNum = chunk.last().chapterNumber
        val firstStr = if (firstNum > 0) formatChapterNumDisplay(firstNum) else "1"
        val lastStr = if (lastNum > 0) formatChapterNumDisplay(lastNum) else "${chunk.size}"
        if (firstStr == lastStr) "Capítulo $firstStr" else "Capítulos $firstStr a $lastStr"
    } else {
        "Grupo ${chunkIndex + 1} (${chunk.size} capítulos)"
    }
}

/**
 * Removes leading language/origin tags such as [PT-BR], [EN], [PT], [Scan], etc.
 */
fun formatChapterTitle(title: String): String {
    if (title.isBlank()) return title
    return title.replace(Regex("^\\[[^\\]]+\\]\\s*"), "").trim()
}

/**
 * Cleans synopsis by removing markdown dividers, external source links, origin notes, and junk tags.
 */
fun cleanDescription(description: String): String {
    if (description.isBlank()) return "Sem descrição disponível."

    val lines = description.lines()
    val cleanedLines = mutableListOf<String>()

    for (line in lines) {
        val trimmed = line.trim()
        val lower = trimmed.lowercase()

        // Discard markdown dividers
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            continue
        }

        // Discard origin/source metadata headers and scan links
        if (lower.startsWith("origem:") || lower.startsWith("fonte:") ||
            lower.startsWith("source:") || lower.startsWith("links:") ||
            lower.startsWith("**links:**") || lower.startsWith("scan:") ||
            lower.startsWith("scanlator:") || lower.startsWith("raw:") ||
            lower.startsWith("official raw:") || lower.startsWith("original webcomic:") ||
            lower.startsWith("track on") || lower.startsWith("alternative titles:")
        ) {
            continue
        }

        // Discard markdown link list items like "* [Official English](...)" or "[Pixiv](...)"
        if (trimmed.startsWith("* [") || trimmed.startsWith("- [") || (trimmed.startsWith("[") && trimmed.contains("]("))) {
            continue
        }

        // Convert [Title](url) -> Title
        var cleanedLine = trimmed.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
        // Strip bold/italics markers
        cleanedLine = cleanedLine.replace("**", "").replace("*", "")
        // Strip basic HTML tags
        cleanedLine = cleanedLine.replace(Regex("<[^>]*>"), "")

        cleanedLines.add(cleanedLine)
    }

    val result = cleanedLines.joinToString("\n").trim()
    return if (result.isBlank()) "Sem descrição disponível." else result
}
