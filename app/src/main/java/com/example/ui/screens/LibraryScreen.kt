package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.BookmarkEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.MangaEntity
import com.example.data.local.ProgressEntity
import com.example.data.model.ReadingStats
import com.example.source.ChapterModel
import com.example.source.MangaModel
import com.example.ui.components.MangaCard
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.PrimaryPurple

@Composable
fun LibraryScreen(
    favorites: List<MangaEntity>,
    history: List<ProgressEntity>,
    downloads: List<DownloadEntity>,
    bookmarks: List<BookmarkEntity> = emptyList(),
    stats: ReadingStats = ReadingStats(),
    onMangaClick: (MangaModel) -> Unit,
    onContinueReading: (ProgressEntity) -> Unit,
    onReadDownloadedChapter: (MangaModel, ChapterModel) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onOpenBookmark: (BookmarkEntity) -> Unit = {},
    onDeleteBookmark: (Long) -> Unit = {},
    onDeleteHistory: (String) -> Unit = {},
    onDeleteFavorite: (String) -> Unit = {},
    onSyncClicked: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Favoritos (${favorites.size})",
        "Marcadores (${bookmarks.size})",
        "Histórico",
        "Downloads (${downloads.size})",
        "Estatísticas"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        ScrollableTabRow(
            edgePadding = 0.dp,
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = PrimaryPurple,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PrimaryPurple
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val icon = when (index) {
                    0 -> Icons.Default.Favorite
                    1 -> Icons.Default.Bookmark
                    2 -> Icons.Default.History
                    3 -> Icons.Default.Download
                    else -> Icons.Default.AutoGraph
                }
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag("library_tab_$index"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "library_tab_transition"
        ) { targetTab ->
            when (targetTab) {
                0 -> FavoritesList(
                    favorites = favorites,
                    onMangaClick = onMangaClick,
                    onDeleteFavorite = onDeleteFavorite
                )
                1 -> BookmarksList(
                    bookmarks = bookmarks,
                    onOpenBookmark = onOpenBookmark,
                    onDeleteBookmark = onDeleteBookmark
                )
                2 -> HistoryList(
                    history = history,
                    onContinueReading = onContinueReading,
                    onDeleteHistory = onDeleteHistory
                )
                3 -> DownloadsList(
                    downloads = downloads,
                    onReadDownloadedChapter = onReadDownloadedChapter,
                    onDeleteDownload = onDeleteDownload
                )
                4 -> StatsDashboardScreen(stats = stats)
            }
        }
    }
}

@Composable
private fun SwipeGestureHintHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Swipe,
            contentDescription = null,
            tint = PrimaryPurple,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Deslize para a esquerda para excluir • Deslize para a direita para atualizar/abrir",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SwipeableLibraryItem(
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    deleteLabel: String = "Excluir",
    refreshLabel: String = "Atualizar",
    testTag: String = "",
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }
    val maxSwipePx = with(density) { 140.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "swipe_offset_anim"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    animatedOffsetX > 15f -> PrimaryPurple.copy(alpha = 0.85f)
                    animatedOffsetX < -15f -> CrimsonRed.copy(alpha = 0.9f)
                    else -> Color.Transparent
                }
            )
            .testTag(testTag)
    ) {
        // Left Background Action (Swipe Right -> Refresh / Action)
        if (animatedOffsetX > 10f && onRefresh != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = refreshLabel,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = refreshLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Right Background Action (Swipe Left -> Delete)
        if (animatedOffsetX < -10f && onDelete != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = deleteLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = deleteLabel,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Foreground Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedOffsetX
                }
                .pointerInput(onDelete, onRefresh) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > thresholdPx && onRefresh != null) {
                                onRefresh()
                            } else if (offsetX < -thresholdPx && onDelete != null) {
                                onDelete()
                            }
                            offsetX = 0f
                        },
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            val minBound = if (onDelete != null) -maxSwipePx else 0f
                            val maxBound = if (onRefresh != null) maxSwipePx else 0f
                            offsetX = newOffset.coerceIn(minBound, maxBound)
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun FavoritesList(
    favorites: List<MangaEntity>,
    onMangaClick: (MangaModel) -> Unit,
    onDeleteFavorite: (String) -> Unit = {}
) {
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Sem Favoritos",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sua biblioteca está vazia",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Adicione mangás aos favoritos na aba Explorar.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SwipeGestureHintHeader()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favorites) { entity ->
                    val model = MangaModel(
                        url = entity.url,
                        id = entity.id,
                        title = entity.title,
                        coverUrl = entity.coverUrl,
                        description = entity.description,
                        genres = if (entity.genres.isBlank()) emptyList() else entity.genres.split(","),
                        isNsfw = entity.isNsfw,
                        sourceId = entity.sourceId
                    )
                    SwipeableLibraryItem(
                        onDelete = { onDeleteFavorite(entity.url) },
                        onRefresh = { onMangaClick(model) },
                        deleteLabel = "Remover",
                        refreshLabel = "Abrir",
                        testTag = "favorite_swipe_item_${entity.id}"
                    ) {
                        MangaCard(
                            manga = model,
                            onClick = { onMangaClick(model) },
                            isFavorite = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryList(
    history: List<ProgressEntity>,
    onContinueReading: (ProgressEntity) -> Unit,
    onDeleteHistory: (String) -> Unit = {}
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Sem Histórico",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Nenhum histórico recente",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "O progresso de leitura será salvo automaticamente aqui.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SwipeGestureHintHeader()
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(history) { progress ->
                    SwipeableLibraryItem(
                        onDelete = { onDeleteHistory(progress.mangaUrl) },
                        onRefresh = { onContinueReading(progress) },
                        deleteLabel = "Excluir Histórico",
                        refreshLabel = "Continuar",
                        testTag = "history_swipe_item_${progress.chapterId}"
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onContinueReading(progress) }
                                .testTag("history_item_${progress.chapterId}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(PrimaryPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Book,
                                        contentDescription = null,
                                        tint = PrimaryPurple
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = progress.chapterTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val currentPage = progress.lastPageRead + 1
                                    val total = progress.totalPages
                                    val offsetText = if (progress.scrollOffset > 0) " • Posição exata" else ""
                                    Text(
                                        text = "Progresso: Página $currentPage de $total$offsetText",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val progressFraction = if (total > 0) currentPage.toFloat() / total.toFloat() else 0f
                                    LinearProgressIndicator(
                                        progress = { progressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = AccentGold,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Continuar",
                                    tint = PrimaryPurple
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsList(
    downloads: List<DownloadEntity>,
    onReadDownloadedChapter: (MangaModel, ChapterModel) -> Unit,
    onDeleteDownload: (String) -> Unit
) {
    if (downloads.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Sem Downloads",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Nenhum capítulo baixado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Baixe capítulos para ler sem conexão com a internet.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SwipeGestureHintHeader()
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(downloads) { download ->
                    val mangaModel = MangaModel(
                        url = download.mangaUrl,
                        id = download.mangaUrl.hashCode().toString(),
                        title = download.mangaTitle,
                        coverUrl = download.mangaCoverUrl,
                        sourceId = download.sourceId
                    )
                    val chapterModel = ChapterModel(
                        id = download.chapterId,
                        title = download.chapterTitle,
                        chapterUrl = download.chapterUrl
                    )

                    SwipeableLibraryItem(
                        onDelete = { onDeleteDownload(download.chapterId) },
                        onRefresh = { onReadDownloadedChapter(mangaModel, chapterModel) },
                        deleteLabel = "Excluir Download",
                        refreshLabel = "Ler Offline",
                        testTag = "download_swipe_item_${download.chapterId}"
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onReadDownloadedChapter(mangaModel, chapterModel) }
                                .testTag("download_item_${download.chapterId}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    AsyncImage(
                                        model = download.mangaCoverUrl,
                                        contentDescription = download.mangaTitle,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = download.mangaTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = download.chapterTitle,
                                        fontSize = 13.sp,
                                        color = PrimaryPurple,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (download.downloadState == DownloadEntity.STATE_COMPLETED) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${download.totalPages} páginas baixadas (Offline)",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        val percent = (download.downloadProgress * 100).toInt()
                                        Text(
                                            text = "Baixando... $percent% (${download.downloadedPagesCount}/${download.totalPages})",
                                            fontSize = 11.sp,
                                            color = PrimaryPurple
                                        )
                                    }
                                }

                                IconButton(onClick = { onDeleteDownload(download.chapterId) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir Download",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@Composable
private fun BookmarksList(
    bookmarks: List<BookmarkEntity>,
    onOpenBookmark: (BookmarkEntity) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Sem Marcadores",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Nenhum marcador salvo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Toque no ícone de marcador durante a leitura para salvar páginas favoritas.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SwipeGestureHintHeader()
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(bookmarks) { bookmark ->
                    SwipeableLibraryItem(
                        onDelete = { onDeleteBookmark(bookmark.id) },
                        onRefresh = { onOpenBookmark(bookmark) },
                        deleteLabel = "Excluir Marcador",
                        refreshLabel = "Abrir Página",
                        testTag = "bookmark_swipe_item_${bookmark.id}"
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenBookmark(bookmark) }
                                .testTag("bookmark_item_${bookmark.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    if (bookmark.mangaCoverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = bookmark.mangaCoverUrl,
                                            contentDescription = bookmark.mangaTitle,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(PrimaryPurple.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bookmark,
                                                contentDescription = null,
                                                tint = AccentGold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookmark.mangaTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${bookmark.chapterTitle} • Página ${bookmark.pageIndex + 1}",
                                            fontSize = 13.sp,
                                            color = PrimaryPurple,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (bookmark.isAutoBookmark) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "AUTO",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentGold,
                                                modifier = Modifier
                                                    .background(AccentGold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (bookmark.scrollOffset > 0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (bookmark.isAutoBookmark) "Posição exata salva automaticamente (+${bookmark.scrollOffset}px)" else "Posição exata: +${bookmark.scrollOffset}px",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else if (bookmark.note.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = bookmark.note,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir Marcador",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
}
