package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

    val sortedChapters = remember(state.chapters, sortAscending, chapterSearchQuery) {
        val list = if (sortAscending) state.chapters.reversed() else state.chapters
        if (chapterSearchQuery.isBlank()) {
            list
        } else {
            list.filter { it.title.contains(chapterSearchQuery.trim(), ignoreCase = true) }
        }
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Fonte: ${manga.sourceId.uppercase()} • ${manga.author.ifEmpty { "Autor Desconhecido" }}",
                                color = AccentGold,
                                fontSize = 12.sp
                            )
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
                        text = manga.description.ifEmpty { "Sem sinopse informada." },
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
                                        text = "${progress.chapterTitle} • Pág ${progress.lastPageRead + 1}/${progress.totalPages}",
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
                                        text = firstChapter.title,
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Capítulos (${state.chapters.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Botão de ordenação dos capítulos
                                    IconButton(
                                        onClick = { sortAscending = !sortAscending },
                                        modifier = Modifier.size(32.dp).testTag("toggle_sort_order_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = if (sortAscending) "Ordenar: Mais Recentes Primeiro" else "Ordenar: Do Começo",
                                            tint = PrimaryPurple,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                if (state.chapters.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { isSelectionMode = true },
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.testTag("toggle_selection_mode_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Checklist,
                                                contentDescription = null,
                                                tint = PrimaryPurple,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Selecionar", fontSize = 12.sp, color = PrimaryPurple)
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
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.testTag("download_all_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = PrimaryPurple,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Baixar Todos", fontSize = 12.sp, color = PrimaryPurple)
                                        }
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum capítulo disponível para esta fonte.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            } else {
                items(sortedChapters) { chapter ->
                    val isCurrentReading = state.readingProgress?.chapterId == chapter.id
                    val downloadRecord = downloads.find { it.chapterId == chapter.id }
                    val isSelected = selectedChapterIds.contains(chapter.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isSelectionMode) {
                                    Modifier.clickable {
                                        selectedChapterIds = if (isSelected) {
                                            selectedChapterIds - chapter.id
                                        } else {
                                            selectedChapterIds + chapter.id
                                        }
                                    }
                                } else {
                                    Modifier.pointerInput(chapter.id) {
                                        detectTapGestures(
                                            onTap = { onChapterClick(chapter) },
                                            onLongPress = {
                                                isSelectionMode = true
                                                selectedChapterIds = setOf(chapter.id)
                                            }
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
                                    onCheckedChange = { checked ->
                                        selectedChapterIds = if (checked) {
                                            selectedChapterIds + chapter.id
                                        } else {
                                            selectedChapterIds - chapter.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryPurple),
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .testTag("chapter_checkbox_${chapter.id}")
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chapter.title,
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
                                                onClick = { onDeleteDownload(chapter.id) },
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
                                            onClick = { onDownloadChapter(chapter) },
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
            }
        }
    }
}
