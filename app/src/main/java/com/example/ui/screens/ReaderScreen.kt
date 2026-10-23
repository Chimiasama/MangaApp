package com.example.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.PrimaryPurple
import com.example.ui.viewmodel.ReaderUiState
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    readerMode: String = "WEBTOON",
    readerOrientation: String = "SENSOR",
    defaultFullscreen: Boolean = true,
    onBack: () -> Unit,
    onPageSelected: (Int) -> Unit = {},
    onScrollPositionChanged: (pageIndex: Int, scrollOffset: Int) -> Unit = { page, _ -> onPageSelected(page) },
    onFlushScrollPosition: () -> Unit = {},
    onNavigateChapter: (Boolean) -> Unit,
    onToggleBookmark: (Int) -> Unit = {},
    onReaderModeChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(defaultFullscreen) }
    var currentMode by remember { mutableStateOf(readerMode) }
    var showControls by remember { mutableStateOf(false) } // Iniciar limpo para foco na leitura
    var showModeDropdown by remember { mutableStateOf(false) }

    // Quick Zoom state aplicável globalmente ou por página
    var currentScale by remember { mutableFloatStateOf(1f) }
    var fitMode by remember { mutableStateOf(ContentScale.FillWidth) }

    // Orientation lock effect
    DisposableEffect(readerOrientation) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        val activity = ctx as? Activity
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        activity?.requestedOrientation = when (readerOrientation) {
            "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }

    // Fullscreen Immersive Mode Effect
    DisposableEffect(isFullscreen) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        val activity = ctx as? Activity
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, it.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.currentPageIndex,
        initialFirstVisibleItemScrollOffset = state.scrollOffset
    )
    val pagerState = rememberPagerState(
        initialPage = state.currentPageIndex.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0)),
        pageCount = { state.pages.size }
    )

    // Posicionamento inicial exato suave ao carregar o capítulo
    var initialPositionApplied by rememberSaveable(state.currentChapter?.id) { mutableStateOf(false) }
    var showResumedBadge by remember { mutableStateOf(false) }

    LaunchedEffect(state.pages, state.currentChapter?.id) {
        if (!initialPositionApplied && state.pages.isNotEmpty()) {
            val targetPage = state.currentPageIndex.coerceIn(0, state.pages.lastIndex)
            val targetOffset = state.scrollOffset
            if (targetPage > 0 || targetOffset > 0) {
                if (currentMode == "WEBTOON") {
                    listState.scrollToItem(targetPage, targetOffset)
                } else {
                    pagerState.scrollToPage(targetPage)
                }
                showResumedBadge = true
            }
            initialPositionApplied = true
        }
    }

    LaunchedEffect(showResumedBadge) {
        if (showResumedBadge) {
            kotlinx.coroutines.delay(2600)
            showResumedBadge = false
        }
    }

    // Sincroniza continuamente a posição exata de rolagem com o Room
    LaunchedEffect(listState, currentMode) {
        if (currentMode == "WEBTOON") {
            snapshotFlow {
                Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }.collect { (index, offset) ->
                if (index in state.pages.indices) {
                    onScrollPositionChanged(index, offset)
                }
            }
        }
    }

    // Salva o progresso no modo paginado horizontal
    LaunchedEffect(pagerState, currentMode) {
        if (currentMode != "WEBTOON") {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                if (page in state.pages.indices) {
                    onScrollPositionChanged(page, 0)
                }
            }
        }
    }

    // Flush de posição ao sair do leitor para garantir salvamento imediato
    DisposableEffect(Unit) {
        onDispose {
            onFlushScrollPosition()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("reader_screen")
    ) {
        // Indicador de Posição Exata Retomada
        AnimatedVisibility(
            visible = showResumedBadge,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .testTag("resumed_position_badge")
        ) {
            Surface(
                color = Color(0xEE1E1B2E),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.8f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Marcador: Leitura retomada na posição exata",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CrimsonRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Carregando páginas...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (state.pages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma página disponível para este capítulo.",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        } else {
            // Container principal de leitura
            Box(modifier = Modifier.fillMaxSize()) {
                if (currentMode == "WEBTOON") {
                    // Modo Cascata Contínua (Webtoon Vertical Fluido e Ininterrupto)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(
                            items = state.pages,
                            key = { index, _ -> "webtoon_page_$index" }
                        ) { index, pageUrl ->
                            val isBookmarked = index in state.bookmarkedPages
                            WebtoonContinuousPage(
                                pageUrl = pageUrl,
                                pageIndex = index,
                                isBookmarked = isBookmarked,
                                onCenterTap = { showControls = !showControls }
                            )
                        }

                        // Final de capítulo
                        item(key = "end_of_chapter") {
                            EndOfChapterCard(
                                onNextChapter = { onNavigateChapter(true) },
                                onBack = onBack
                            )
                        }
                    }
                } else {
                    // Modos Paginados Horizontais (PAGED_RTL - Mangá autêntico / PAGED_LTR)
                    val isRtl = currentMode == "PAGED_RTL"
                    HorizontalPager(
                        state = pagerState,
                        reverseLayout = isRtl,
                        userScrollEnabled = currentScale <= 1.05f,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val isBookmarked = pageIndex in state.bookmarkedPages
                        ZoomableMangaPage(
                            pageUrl = state.pages[pageIndex],
                            pageIndex = pageIndex,
                            isBookmarked = isBookmarked,
                            contentScale = fitMode,
                            externalScale = currentScale,
                            onScaleChanged = { currentScale = it },
                            onCenterTap = { showControls = !showControls },
                            onLeftTap = {
                                coroutineScope.launch {
                                    if (isRtl) {
                                        // No RTL, esquerda significa avançar para a próxima página
                                        if (pageIndex < state.pages.lastIndex) {
                                            pagerState.animateScrollToPage(pageIndex + 1)
                                        } else {
                                            onNavigateChapter(true)
                                        }
                                    } else {
                                        // No LTR, esquerda volta
                                        if (pageIndex > 0) {
                                            pagerState.animateScrollToPage(pageIndex - 1)
                                        } else {
                                            onNavigateChapter(false)
                                        }
                                    }
                                }
                            },
                            onRightTap = {
                                coroutineScope.launch {
                                    if (isRtl) {
                                        // No RTL, direita significa voltar
                                        if (pageIndex > 0) {
                                            pagerState.animateScrollToPage(pageIndex - 1)
                                        } else {
                                            onNavigateChapter(false)
                                        }
                                    } else {
                                        // No LTR, direita avança
                                        if (pageIndex < state.pages.lastIndex) {
                                            pagerState.animateScrollToPage(pageIndex + 1)
                                        } else {
                                            onNavigateChapter(true)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Barra de progresso ultrafina e badge flutuante quando controles estão ocultos (Modo Zen)
        if (!state.isLoading && state.pages.isNotEmpty()) {
            val totalPages = state.pages.size
            val currentProgress = if (totalPages > 0) {
                ((state.currentPageIndex + 1).toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val percent = (currentProgress * 100).toInt()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                if (!showControls) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Modo de leitura ativo discreto
                        Text(
                            text = when (currentMode) {
                                "PAGED_RTL" -> "Mangá (RTL)"
                                "PAGED_LTR" -> "Paginado (LTR)"
                                else -> "Webtoon"
                            },
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Indicador de página elegante e sutil
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${state.currentPageIndex + 1} / $totalPages ($percent%)",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Linha de progresso sutil no rodapé
                    LinearProgressIndicator(
                        progress = { currentProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .testTag("reader_progress_bar"),
                        color = CrimsonRed,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // Quick Zoom Floating Dock (Facilitador de zoom para cima e para baixo acessível)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Zoom In (+)
                    IconButton(
                        onClick = {
                            currentScale = (currentScale + 0.5f).coerceAtMost(4.5f)
                        },
                        modifier = Modifier.size(36.dp).testTag("zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Aumentar Zoom",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Porcentagem atual / Reset para 100% com 1 toque
                    val zoomPercent = (currentScale * 100).toInt()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                currentScale = getNextZoomScale(currentScale)
                            }
                            .background(
                                if (currentScale > 1.05f) CrimsonRed.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${zoomPercent}%",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Zoom Out (-)
                    IconButton(
                        onClick = {
                            currentScale = (currentScale - 0.5f).coerceAtLeast(1.0f)
                        },
                        modifier = Modifier.size(36.dp).testTag("zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Diminuir Zoom",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Botão para alternar Enquadramento: Ajustar Largura vs Altura
                    IconButton(
                        onClick = {
                            fitMode = if (fitMode == ContentScale.FillWidth) ContentScale.Fit else ContentScale.FillWidth
                        },
                        modifier = Modifier.size(36.dp).testTag("toggle_fit_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Height,
                            contentDescription = "Ajustar Enquadramento",
                            tint = if (fitMode == ContentScale.Fit) AccentGold else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Overlay Superior de Controles (Top Bar)
        val isCurrentPageBookmarked = state.currentPageIndex in state.bookmarkedPages
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.90f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("reader_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Sair do Leitor",
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        text = state.currentChapter?.title?.replace(Regex("^\\[[^\\]]+\\]\\s*"), "")?.trim() ?: "Leitor",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (currentMode) {
                                "PAGED_RTL" -> "Modo: Mangá (RTL 📖)"
                                "PAGED_LTR" -> "Modo: Paginado (LTR 📕)"
                                else -> "Modo: Webtoon (Vertical 📜)"
                            },
                            color = AccentGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.isOfflineDownloaded) {
                            Text(
                                text = " • OFFLINE",
                                color = Color(0xFF4CAF50),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Seletor de Modo de Leitura Rápido (Dropdown / Menu)
                Box {
                    IconButton(
                        onClick = { showModeDropdown = !showModeDropdown },
                        modifier = Modifier.testTag("reader_mode_menu_button")
                    ) {
                        Icon(
                            imageVector = when (currentMode) {
                                "WEBTOON" -> Icons.Default.ViewStream
                                "PAGED_RTL" -> Icons.AutoMirrored.Filled.MenuBook
                                else -> Icons.Default.ViewCarousel
                            },
                            contentDescription = "Mudar Modo de Leitura",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = showModeDropdown,
                        onDismissRequest = { showModeDropdown = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Webtoon (Cascata Vertical)") },
                            leadingIcon = { Icon(Icons.Default.ViewStream, contentDescription = null) },
                            onClick = {
                                currentMode = "WEBTOON"
                                onReaderModeChanged("WEBTOON")
                                showModeDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mangá (Página Direita → Esquerda)") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                            onClick = {
                                currentMode = "PAGED_RTL"
                                onReaderModeChanged("PAGED_RTL")
                                showModeDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Quadrinhos (Esquerda → Direita)") },
                            leadingIcon = { Icon(Icons.Default.ViewCarousel, contentDescription = null) },
                            onClick = {
                                currentMode = "PAGED_LTR"
                                onReaderModeChanged("PAGED_LTR")
                                showModeDropdown = false
                            }
                        )
                    }
                }

                // Toggle Fullscreen Mode
                IconButton(
                    onClick = { isFullscreen = !isFullscreen },
                    modifier = Modifier.testTag("toggle_fullscreen_button")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Sair da Tela Cheia" else "Tela Cheia",
                        tint = Color.White
                    )
                }

                // Marcador de Página
                IconButton(
                    onClick = { onToggleBookmark(state.currentPageIndex) },
                    modifier = Modifier.testTag("toggle_bookmark_button")
                ) {
                    Icon(
                        imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isCurrentPageBookmarked) "Remover Marcador" else "Adicionar Marcador",
                        tint = if (isCurrentPageBookmarked) AccentGold else Color.White
                    )
                }
            }
        }

        // Overlay Inferior de Controles (Bottom Bar: navegação de capítulos e scrub bar)
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.90f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val totalPages = state.pages.size
                val currentPage = (state.currentPageIndex + 1).coerceAtMost(totalPages)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onNavigateChapter(false) },
                        modifier = Modifier.testTag("prev_chapter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NavigateBefore,
                            contentDescription = "Capítulo Anterior",
                            tint = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Página $currentPage / ${if (totalPages == 0) 1 else totalPages}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .background(CrimsonRed, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )

                        // Botão de reset de zoom rápido se ampliado
                        if (currentScale > 1.05f) {
                            IconButton(
                                onClick = { currentScale = 1.0f },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Resetar Zoom",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { onNavigateChapter(true) },
                        modifier = Modifier.testTag("next_chapter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NavigateNext,
                            contentDescription = "Próximo Capítulo",
                            tint = Color.White
                        )
                    }
                }

                // Slider suave para troca rápida de página
                if (totalPages > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = state.currentPageIndex.toFloat(),
                        onValueChange = { targetFloat ->
                            val targetPage = targetFloat.toInt().coerceIn(0, totalPages - 1)
                            onPageSelected(targetPage)
                            coroutineScope.launch {
                                if (currentMode == "WEBTOON") {
                                    listState.scrollToItem(targetPage)
                                } else {
                                    pagerState.scrollToPage(targetPage)
                                }
                            }
                        },
                        valueRange = 0f..(totalPages - 1).toFloat(),
                        steps = if (totalPages > 2) totalPages - 2 else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonRed,
                            activeTrackColor = CrimsonRed,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("page_slider")
                    )
                }
            }
        }
    }
}

/**
 * Calcula a próxima etapa de escala no duplo toque:
 * - 1º duplo toque: amplia um pouco (1.75x)
 * - 2º duplo toque: amplia mais (2.75x)
 * - 3º duplo toque: amplia totalmente (4.0x)
 * - 4º duplo toque (ou quando já ampliado no máximo): retorna suavemente ao padrão (1.0x)
 */
fun getNextZoomScale(currentScale: Float): Float = when {
    currentScale < 1.4f -> 1.75f
    currentScale < 2.3f -> 2.75f
    currentScale < 3.6f -> 4.0f
    else -> 1.0f
}

/**
 * Detector de gestos para zoom de pinça (pinch) exclusivo com 2 dedos.
 * Permite pinçar para ampliar sem interceptar ou consumir gestos com 1 dedo
 * (garantindo que o scroll do LazyColumn ou swipe do Pager funcionem 100% livres).
 */
private suspend fun PointerInputScope.detectPinchZoomOnly(
    onZoom: (zoomChange: Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount > 1) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.fastForEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            }
        } while (event.changes.fastAny { it.pressed })
    }
}

/**
 * Visualizador Contínuo para Webtoon / Manhwa Vertical
 * - Encaixe perfeitamente justaposto sem divisória entre páginas (-1dp offset e Alignment.TopCenter)
 * - Zoom inteligente em etapas via duplo toque (1.75x -> 2.75x -> 4.0x -> 1.0x)
 * - Movimentação livre e suave 2D: segure a página com zoom e mova livremente em qualquer direção
 */
@Composable
fun WebtoonContinuousPage(
    pageUrl: String,
    pageIndex: Int,
    isBookmarked: Boolean,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }

    val isZoomed = scaleAnim.value > 1.05f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (pageIndex > 0) Modifier.offset(y = (-1).dp) else Modifier)
            .zIndex(if (isZoomed) 2f else 0f)
            .onSizeChanged { pageSize = it }
            .testTag("webtoon_page_$pageIndex")
            .pointerInput(isZoomed, pageSize) {
                if (isZoomed) {
                    // Quando ampliado, permite segurar a página com 1 dedo e mover livremente em 2D
                    detectTransformGestures { _, pan, zoom, _ ->
                        val width = pageSize.width.toFloat().coerceAtLeast(1f)
                        val height = pageSize.height.toFloat().coerceAtLeast(1f)

                        val currentScale = scaleAnim.value
                        val newScale = (currentScale * zoom).coerceIn(1.0f, 4.5f)

                        val maxOffsetX = ((newScale - 1f) * width) / 2f
                        val maxOffsetY = ((newScale - 1f) * height) / 2f

                        val newX = (offsetXAnim.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        val newY = (offsetYAnim.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY)

                        coroutineScope.launch {
                            scaleAnim.snapTo(newScale)
                            offsetXAnim.snapTo(newX)
                            offsetYAnim.snapTo(newY)
                        }
                    }
                } else {
                    // Quando em 1.0x, permite zoom de pinça com 2 dedos sem bloquear o scroll vertical da LazyColumn
                    detectPinchZoomOnly { zoomChange ->
                        val newScale = (scaleAnim.value * zoomChange).coerceIn(1.0f, 4.5f)
                        coroutineScope.launch {
                            scaleAnim.snapTo(newScale)
                        }
                    }
                }
            }
            .pointerInput(pageSize) {
                // Duplo toque em etapas e toque simples para alternar controles
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val width = pageSize.width.toFloat().coerceAtLeast(1f)
                        val height = pageSize.height.toFloat().coerceAtLeast(1f)

                        val nextScale = getNextZoomScale(scaleAnim.value)

                        val targetX: Float
                        val targetY: Float
                        if (nextScale <= 1.05f) {
                            targetX = 0f
                            targetY = 0f
                        } else {
                            val maxOffsetX = ((nextScale - 1f) * width) / 2f
                            val maxOffsetY = ((nextScale - 1f) * height) / 2f
                            val centerX = width / 2f
                            val centerY = height / 2f
                            val desiredX = (centerX - tapOffset.x) * (nextScale - 1f)
                            val desiredY = (centerY - tapOffset.y) * (nextScale - 1f)
                            targetX = desiredX.coerceIn(-maxOffsetX, maxOffsetX)
                            targetY = desiredY.coerceIn(-maxOffsetY, maxOffsetY)
                        }

                        coroutineScope.launch {
                            launch {
                                scaleAnim.animateTo(
                                    targetValue = nextScale,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            launch {
                                offsetXAnim.animateTo(
                                    targetValue = targetX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            launch {
                                offsetYAnim.animateTo(
                                    targetValue = targetY,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    },
                    onTap = {
                        onCenterTap()
                    }
                )
            }
    ) {
        SubcomposeAsyncImage(
            model = pageUrl,
            contentDescription = "Página ${pageIndex + 1}",
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .background(Color(0xFF101012)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = CrimsonRed,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color(0xFF18181C)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Erro ao carregar página ${pageIndex + 1}",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetXAnim.value
                    translationY = offsetYAnim.value
                    clip = scaleAnim.value > 1.05f
                }
        )

        // Indicador de Marcador Salvo na página
        if (isBookmarked) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Marcado",
                    tint = AccentGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Marcado",
                    color = AccentGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Visualizador de Página com Zoom Avançado (Pinch + Pan livre 2D)
 * - Duplo toque inteligente em etapas (1.75x -> 2.75x -> 4.0x -> 1.0x) com transições suaves
 * - Segurar e mover livremente em qualquer direção enquanto em zoom com clamping elegante
 * - Tap Zones invisíveis (Esquerda = voltar, Direita = avançar, Centro = controles)
 */
@Composable
fun ZoomableMangaPage(
    pageUrl: String,
    pageIndex: Int,
    isBookmarked: Boolean,
    contentScale: ContentScale = ContentScale.FillWidth,
    externalScale: Float = 1.0f,
    onScaleChanged: (Float) -> Unit = {},
    onCenterTap: () -> Unit,
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    // Sincroniza se o dock de zoom externo alterar o scale
    LaunchedEffect(externalScale) {
        if (kotlin.math.abs(scaleAnim.value - externalScale) > 0.05f) {
            if (externalScale <= 1.05f) {
                coroutineScope.launch {
                    launch { scaleAnim.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) }
                    launch { offsetXAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) }
                    launch { offsetYAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) }
                }
            } else {
                scaleAnim.animateTo(externalScale, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    val isZoomed = scaleAnim.value > 1.05f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .testTag("reader_page_$pageIndex")
    ) {
        val containerWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isZoomed, containerWidth, containerHeight) {
                    if (isZoomed) {
                        // Detecção de gestos quando ampliado: segurar com 1 dedo e mover livremente em 2D
                        detectTransformGestures { _, pan, zoom, _ ->
                            val currentScale = scaleAnim.value
                            val newScale = (currentScale * zoom).coerceIn(1.0f, 4.5f)
                            onScaleChanged(newScale)

                            val maxOffsetX = ((newScale - 1f) * containerWidth) / 2f
                            val maxOffsetY = ((newScale - 1f) * containerHeight) / 2f

                            val newX = (offsetXAnim.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            val newY = (offsetYAnim.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY)

                            coroutineScope.launch {
                                scaleAnim.snapTo(newScale)
                                offsetXAnim.snapTo(newX)
                                offsetYAnim.snapTo(newY)
                            }
                        }
                    } else {
                        // Quando em 1.0x, permite pinça com 2 dedos sem interceptar o swipe horizontal do pager
                        detectPinchZoomOnly { zoomChange ->
                            val newScale = (scaleAnim.value * zoomChange).coerceIn(1.0f, 4.5f)
                            onScaleChanged(newScale)
                            coroutineScope.launch {
                                scaleAnim.snapTo(newScale)
                            }
                        }
                    }
                }
                .pointerInput(containerWidth, containerHeight) {
                    // Toques rápidos, duplo toque em etapas e zonas de navegação invisíveis
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val nextScale = getNextZoomScale(scaleAnim.value)
                            onScaleChanged(nextScale)

                            val targetX: Float
                            val targetY: Float
                            if (nextScale <= 1.05f) {
                                targetX = 0f
                                targetY = 0f
                            } else {
                                val maxOffsetX = ((nextScale - 1f) * containerWidth) / 2f
                                val maxOffsetY = ((nextScale - 1f) * containerHeight) / 2f
                                val centerX = containerWidth / 2f
                                val centerY = containerHeight / 2f
                                val desiredX = (centerX - tapOffset.x) * (nextScale - 1f)
                                val desiredY = (centerY - tapOffset.y) * (nextScale - 1f)
                                targetX = desiredX.coerceIn(-maxOffsetX, maxOffsetX)
                                targetY = desiredY.coerceIn(-maxOffsetY, maxOffsetY)
                            }

                            coroutineScope.launch {
                                launch {
                                    scaleAnim.animateTo(
                                        targetValue = nextScale,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                                launch {
                                    offsetXAnim.animateTo(
                                        targetValue = targetX,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                                launch {
                                    offsetYAnim.animateTo(
                                        targetValue = targetY,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        },
                        onTap = { tapOffset ->
                            if (scaleAnim.value <= 1.1f) {
                                val xPercent = tapOffset.x / containerWidth
                                when {
                                    xPercent < 0.28f -> onLeftTap()
                                    xPercent > 0.72f -> onRightTap()
                                    else -> onCenterTap()
                                }
                            } else {
                                onCenterTap()
                            }
                        }
                    )
                }
        ) {
            SubcomposeAsyncImage(
                model = pageUrl,
                contentDescription = "Página ${pageIndex + 1}",
                contentScale = contentScale,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CrimsonRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color(0xFF1A1A1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Erro ao carregar página ${pageIndex + 1}",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        translationX = offsetXAnim.value
                        translationY = offsetYAnim.value
                        clip = scaleAnim.value > 1.05f
                    }
            )

            // Indicador de Marcador Salvo na página
            if (isBookmarked) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Marcado",
                        tint = AccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Marcador",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Card minimalista ao fim do capítulo para transição fácil e rápida para o próximo
 */
@Composable
private fun EndOfChapterCard(
    onNextChapter: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Fim do Capítulo 🎉",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Você concluiu todas as páginas deste capítulo.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onBack() },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                ) {
                    Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                        Text("Voltar", color = Color.White, fontSize = 13.sp)
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1.5f)
                        .clickable { onNextChapter() },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = CrimsonRed)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Próximo Capítulo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.NavigateNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
