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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlin.math.roundToInt
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
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.example.util.PagePreloaderManager
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
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
    readerBackgroundColor: String = "OLED",
    readerInvertColors: Boolean = false,
    pagePreloadEnabled: Boolean = true,
    onBack: () -> Unit,
    onPageSelected: (Int) -> Unit = {},
    onScrollPositionChanged: (pageIndex: Int, scrollOffset: Int) -> Unit = { page, _ -> onPageSelected(page) },
    onFlushScrollPosition: () -> Unit = {},
    onNavigateChapter: (Boolean) -> Unit,
    onToggleBookmark: (Int) -> Unit = {},
    onReaderModeChanged: (String) -> Unit = {},
    onReaderBackgroundColorChanged: (String) -> Unit = {},
    onReaderInvertColorsChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(defaultFullscreen) }
    var currentMode by remember { mutableStateOf(readerMode) }
    var showControls by remember { mutableStateOf(false) } // Iniciar limpo para foco na leitura
    var showModeDropdown by remember { mutableStateOf(false) }
    var showLongPressOverlay by remember { mutableStateOf(false) }
    var longPressTrigger by remember { mutableStateOf(0L) }

    // Auto-dismiss do overlay de navegação por toque longo após 6 segundos (sutil e dá tempo de escolher)
    LaunchedEffect(showLongPressOverlay, longPressTrigger) {
        if (showLongPressOverlay) {
            kotlinx.coroutines.delay(6000)
            showLongPressOverlay = false
        }
    }

    // Cor de Fundo do Leitor (OLED, DARK, SEPIA, LIGHT)
    val readerBgColor = when (readerBackgroundColor) {
        "OLED" -> Color.Black
        "DARK" -> Color(0xFF141416)
        "SEPIA" -> Color(0xFFF4ECD8)
        "LIGHT" -> Color(0xFFF8F9FA)
        else -> Color.Black
    }

    // Filtro Noturno Invertido via ColorMatrix (Fase 1.4)
    val invertColorFilter = remember(readerInvertColors) {
        if (readerInvertColors) {
            val matrix = ColorMatrix(
                floatArrayOf(
                    -1f,  0f,  0f, 0f, 255f,
                     0f, -1f,  0f, 0f, 255f,
                     0f,  0f, -1f, 0f, 255f,
                     0f,  0f,  0f, 1f,   0f
                )
            )
            ColorFilter.colorMatrix(matrix)
        } else null
    }

    // Pré-carregamento Inteligente de Páginas (Fase 1.1)
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(state.currentPageIndex, state.pages, pagePreloadEnabled) {
        if (pagePreloadEnabled && state.pages.isNotEmpty()) {
            PagePreloaderManager.preloadPages(
                context = context,
                scope = coroutineScope,
                pages = state.pages,
                currentIndex = state.currentPageIndex,
                forwardCount = 3,
                backwardCount = 1
            )
        }
    }

    // Indicadores de Bateria e Relógio para leitura imersiva (Fase 1.4)
    val batteryPercent = remember(context) {
        try {
            val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        } catch (_: Exception) {
            100
        }
    }
    val currentTime = remember {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    }

    // Zoom state persistente global para todas as páginas (rolagem mantém mesmo zoom e pan)
    var currentScale by remember { mutableFloatStateOf(1f) }
    var globalPanX by remember { mutableFloatStateOf(0f) }
    var fitMode by remember { mutableStateOf(ContentScale.FillWidth) }

    val animatedScale by animateFloatAsState(
        targetValue = currentScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "globalReaderScale"
    )
    val animatedPanX by animateFloatAsState(
        targetValue = if (currentScale > 1.05f) globalPanX else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "globalReaderPanX"
    )

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

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.currentPageIndex,
        initialFirstVisibleItemScrollOffset = state.scrollOffset
    )
    val pagerState = rememberPagerState(
        initialPage = state.currentPageIndex.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0)),
        pageCount = { state.pages.size }
    )

    // Controle rigoroso de troca de capítulo: sempre abrir no topo (página 0, offset 0)
    var lastLoadedChapterId by remember { mutableStateOf<String?>(state.currentChapter?.id) }
    var isSwitchingChapter by remember { mutableStateOf(false) }
    var isOverscrollTriggered by remember { mutableStateOf(false) }
    var showResumedBadge by remember { mutableStateOf(false) }

    // Quando o ID do capítulo mudar, força imediatamente a volta para o topo (página 0, offset 0)
    LaunchedEffect(state.currentChapter?.id) {
        val chapterId = state.currentChapter?.id
        if (chapterId != null && chapterId != lastLoadedChapterId) {
            lastLoadedChapterId = chapterId
            isSwitchingChapter = true
            isOverscrollTriggered = false
            globalPanX = 0f
            listState.scrollToItem(0, 0)
            pagerState.scrollToPage(0)
            isSwitchingChapter = false
        }
    }

    // Aplica a posição correta quando as páginas do capítulo são carregadas
    LaunchedEffect(state.pages, state.currentChapter?.id) {
        if (state.pages.isNotEmpty()) {
            val isNewChapter = lastLoadedChapterId != state.currentChapter?.id
            if (isNewChapter) {
                lastLoadedChapterId = state.currentChapter?.id
                isSwitchingChapter = true
                isOverscrollTriggered = false
                globalPanX = 0f
                listState.scrollToItem(0, 0)
                pagerState.scrollToPage(0)
                isSwitchingChapter = false
            } else {
                val targetPage = state.currentPageIndex.coerceIn(0, state.pages.lastIndex)
                val targetOffset = state.scrollOffset
                if (currentMode == "WEBTOON") {
                    listState.scrollToItem(targetPage, targetOffset)
                } else {
                    pagerState.scrollToPage(targetPage)
                }
                if (targetPage > 0 || targetOffset > 0) {
                    showResumedBadge = true
                }
            }
        }
    }

    LaunchedEffect(showResumedBadge) {
        if (showResumedBadge) {
            kotlinx.coroutines.delay(2600)
            showResumedBadge = false
        }
    }

    // Sincroniza continuamente a posição exata de rolagem com o Room (ignora emissões durante troca de capítulo)
    LaunchedEffect(listState, currentMode, isSwitchingChapter) {
        if (currentMode == "WEBTOON") {
            snapshotFlow {
                Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }.collect { (index, offset) ->
                if (!isSwitchingChapter && !state.isLoading && index in state.pages.indices) {
                    onScrollPositionChanged(index, offset)
                }
            }
        }
    }

    // Salva o progresso no modo paginado horizontal
    LaunchedEffect(pagerState, currentMode, isSwitchingChapter) {
        if (currentMode != "WEBTOON") {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                if (!isSwitchingChapter && !state.isLoading && page in state.pages.indices) {
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

    // NestedScroll Connection para navegação por puxar/overscroll nas extremidades do capítulo
    val nestedScrollConnection = remember(currentMode, state.pages.size, isOverscrollTriggered, isSwitchingChapter) {
        object : NestedScrollConnection {
            var totalOverscrollY = 0f
            var totalOverscrollX = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && !isOverscrollTriggered && !isSwitchingChapter) {
                    val totalItems = state.pages.size
                    if (totalItems > 0) {
                        if (currentMode == "WEBTOON") {
                            val isAtFirstPage = listState.firstVisibleItemIndex == 0 &&
                                    listState.firstVisibleItemScrollOffset == 0
                            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val isAtLastPage = lastVisibleIndex >= totalItems - 1

                            // Puxar no topo (disponível Y > 0) -> capítulo anterior
                            if (isAtFirstPage && available.y > 0) {
                                totalOverscrollY += available.y
                                if (totalOverscrollY > 120f) {
                                    totalOverscrollY = 0f
                                    isOverscrollTriggered = true
                                    onNavigateChapter(false)
                                }
                            }
                            // Puxar no fim (disponível Y < 0) -> próximo capítulo
                            else if (isAtLastPage && available.y < 0) {
                                totalOverscrollY += available.y
                                if (totalOverscrollY < -120f) {
                                    totalOverscrollY = 0f
                                    isOverscrollTriggered = true
                                    onNavigateChapter(true)
                                }
                            }
                        } else {
                            val isRtl = currentMode == "PAGED_RTL"
                            val isAtFirstPage = pagerState.currentPage == 0
                            val isAtLastPage = pagerState.currentPage == totalItems - 1

                            if (isRtl) {
                                if (isAtLastPage && available.x < 0) {
                                    totalOverscrollX += available.x
                                    if (totalOverscrollX < -120f) {
                                        totalOverscrollX = 0f
                                        isOverscrollTriggered = true
                                        onNavigateChapter(true)
                                    }
                                } else if (isAtFirstPage && available.x > 0) {
                                    totalOverscrollX += available.x
                                    if (totalOverscrollX > 120f) {
                                        totalOverscrollX = 0f
                                        isOverscrollTriggered = true
                                        onNavigateChapter(false)
                                    }
                                }
                            } else {
                                if (isAtLastPage && available.x < 0) {
                                    totalOverscrollX += available.x
                                    if (totalOverscrollX < -120f) {
                                        totalOverscrollX = 0f
                                        isOverscrollTriggered = true
                                        onNavigateChapter(true)
                                    }
                                } else if (isAtFirstPage && available.x > 0) {
                                    totalOverscrollX += available.x
                                    if (totalOverscrollX > 120f) {
                                        totalOverscrollX = 0f
                                        isOverscrollTriggered = true
                                        onNavigateChapter(false)
                                    }
                                }
                            }
                        }
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                totalOverscrollY = 0f
                totalOverscrollX = 0f
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("reader_screen")
            .nestedScroll(nestedScrollConnection)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(readerBgColor)
            ) {
                if (currentMode == "WEBTOON") {
                    // Modo Cascata Contínua (Webtoon Vertical Fluido e Ininterrupto)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(readerBgColor),
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
                                colorFilter = invertColorFilter,
                                scale = animatedScale,
                                panX = animatedPanX,
                                onScaleChanged = { newScale ->
                                    currentScale = newScale
                                    if (newScale <= 1.05f) globalPanX = 0f
                                },
                                onPanXChanged = { newPan ->
                                    globalPanX = newPan
                                },
                                onCenterTap = { showControls = !showControls },
                                onLongPress = {
                                    showLongPressOverlay = true
                                    longPressTrigger = System.currentTimeMillis()
                                }
                            )
                        }
                    }
                } else {
                    // Modos Paginados Horizontais (PAGED_RTL - Mangá autêntico / PAGED_LTR)
                    val isRtl = currentMode == "PAGED_RTL"
                    HorizontalPager(
                        state = pagerState,
                        reverseLayout = isRtl,
                        userScrollEnabled = animatedScale <= 1.05f,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(readerBgColor)
                    ) { pageIndex ->
                        val isBookmarked = pageIndex in state.bookmarkedPages
                        ZoomableMangaPage(
                            pageUrl = state.pages[pageIndex],
                            pageIndex = pageIndex,
                            isBookmarked = isBookmarked,
                            contentScale = fitMode,
                            colorFilter = invertColorFilter,
                            scale = animatedScale,
                            panX = animatedPanX,
                            onScaleChanged = { newScale ->
                                currentScale = newScale
                                if (newScale <= 1.05f) globalPanX = 0f
                            },
                            onPanXChanged = { newPan ->
                                globalPanX = newPan
                            },
                            onCenterTap = { showControls = !showControls },
                            onLongPress = {
                                showLongPressOverlay = true
                                longPressTrigger = System.currentTimeMillis()
                            },
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
                        // Modo de leitura ativo discreto + Relógio e Bateria (Fase 1.4)
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentTime,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.BatteryStd,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$batteryPercent%",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 9.sp
                            )
                        }

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

        // Barra de rolagem lateral bem suave (visível apenas ao segurar na tela para abrir os controles e some junto com eles)
        AnimatedVisibility(
            visible = showLongPressOverlay && state.pages.size > 1,
            enter = fadeIn(animationSpec = tween(250)) + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut(animationSpec = tween(250)) + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .zIndex(20f)
                .testTag("smooth_lateral_scrollbar")
        ) {
            SmoothLateralScrollbar(
                currentPage = state.currentPageIndex,
                totalPages = state.pages.size,
                onPageSelected = { targetPage ->
                    onPageSelected(targetPage)
                    coroutineScope.launch {
                        if (currentMode == "WEBTOON") {
                            listState.scrollToItem(targetPage, 0)
                        } else {
                            pagerState.scrollToPage(targetPage)
                        }
                    }
                },
                onInteracting = {
                    longPressTrigger = System.currentTimeMillis()
                }
            )
        }

        // Subtle Top Navigation Bar triggered by Long Press
        AnimatedVisibility(
            visible = showLongPressOverlay,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp, start = 16.dp, end = 16.dp)
                .zIndex(25f)
                .testTag("long_press_navigation_bar")
        ) {
            Surface(
                color = Color(0xEE121218),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voltar um capítulo
                    IconButton(
                        onClick = {
                            showLongPressOverlay = false
                            onNavigateChapter(false)
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("long_press_prev_chapter")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateBefore,
                                contentDescription = "Capítulo Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Anterior", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Ir pro início do capítulo
                    IconButton(
                        onClick = {
                            showLongPressOverlay = false
                            coroutineScope.launch {
                                onPageSelected(0)
                                if (currentMode == "WEBTOON") {
                                    listState.scrollToItem(0, 0)
                                } else {
                                    pagerState.scrollToPage(0)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("long_press_chapter_start")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerticalAlignTop,
                                contentDescription = "Início do Capítulo",
                                tint = AccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Início", color = AccentGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Voltar pra tela de capítulos (detalhes/listagem)
                    IconButton(
                        onClick = {
                            showLongPressOverlay = false
                            onBack()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("long_press_back_to_list")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatListNumbered,
                                contentDescription = "Tela de Capítulos",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Capítulos", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Ir para o próximo capítulo
                    IconButton(
                        onClick = {
                            showLongPressOverlay = false
                            onNavigateChapter(true)
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("long_press_next_chapter")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateNext,
                                contentDescription = "Próximo Capítulo",
                                tint = CrimsonRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Próximo", color = CrimsonRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
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
    colorFilter: ColorFilter? = null,
    scale: Float = 1.0f,
    panX: Float = 0f,
    onScaleChanged: (Float) -> Unit = {},
    onPanXChanged: (Float) -> Unit = {},
    onCenterTap: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val isZoomed = scale > 1.05f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (pageIndex > 0) Modifier.offset(y = (-1).dp) else Modifier)
            .zIndex(if (isZoomed) 2f else 0f)
            .onSizeChanged { pageSize = it }
            .testTag("webtoon_page_$pageIndex")
            .pointerInput(isZoomed, pageSize, panX, scale) {
                if (isZoomed) {
                    val width = pageSize.width.toFloat().coerceAtLeast(1f)
                    val maxOffsetX = ((scale - 1f) * width) / 2f
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val newPan = (panX + dragAmount).coerceIn(-maxOffsetX, maxOffsetX)
                        onPanXChanged(newPan)
                    }
                }
            }
            .pointerInput(scale) {
                detectPinchZoomOnly { zoomChange ->
                    val newScale = (scale * zoomChange).coerceIn(1.0f, 4.5f)
                    onScaleChanged(newScale)
                    if (newScale <= 1.05f) {
                        onPanXChanged(0f)
                    }
                }
            }
            .pointerInput(scale, pageSize) {
                detectTapGestures(
                    onLongPress = {
                        onLongPress()
                    },
                    onDoubleTap = { tapOffset ->
                        val nextScale = getNextZoomScale(scale)
                        onScaleChanged(nextScale)
                        if (nextScale <= 1.05f) {
                            onPanXChanged(0f)
                        } else {
                            val width = pageSize.width.toFloat().coerceAtLeast(1f)
                            val maxOffsetX = ((nextScale - 1f) * width) / 2f
                            val centerX = width / 2f
                            val desiredX = (centerX - tapOffset.x) * (nextScale - 1f)
                            onPanXChanged(desiredX.coerceIn(-maxOffsetX, maxOffsetX))
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
            colorFilter = colorFilter,
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
                    scaleX = scale
                    scaleY = scale
                    translationX = panX
                    clip = scale > 1.05f
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
 * Visualizador de Página com Zoom Avançado (Pinch + Pan livre horizontal)
 * - Zoom persistente global compartilhado entre todas as páginas
 * - Tap Zones invisíveis (Esquerda = voltar, Direita = avançar, Centro = controles)
 * - Toque longo aciona menu de navegação sutil
 */
@Composable
fun ZoomableMangaPage(
    pageUrl: String,
    pageIndex: Int,
    isBookmarked: Boolean,
    contentScale: ContentScale = ContentScale.FillWidth,
    colorFilter: ColorFilter? = null,
    scale: Float = 1.0f,
    panX: Float = 0f,
    onScaleChanged: (Float) -> Unit = {},
    onPanXChanged: (Float) -> Unit = {},
    onCenterTap: () -> Unit,
    onLongPress: () -> Unit = {},
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isZoomed = scale > 1.05f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .testTag("reader_page_$pageIndex")
    ) {
        val containerWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isZoomed, containerWidth, panX, scale) {
                    if (isZoomed) {
                        val maxOffsetX = ((scale - 1f) * containerWidth) / 2f
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val newPan = (panX + dragAmount).coerceIn(-maxOffsetX, maxOffsetX)
                            onPanXChanged(newPan)
                        }
                    }
                }
                .pointerInput(scale) {
                    detectPinchZoomOnly { zoomChange ->
                        val newScale = (scale * zoomChange).coerceIn(1.0f, 4.5f)
                        onScaleChanged(newScale)
                        if (newScale <= 1.05f) {
                            onPanXChanged(0f)
                        }
                    }
                }
                .pointerInput(scale, containerWidth) {
                    detectTapGestures(
                        onLongPress = {
                            onLongPress()
                        },
                        onDoubleTap = { tapOffset ->
                            val nextScale = getNextZoomScale(scale)
                            onScaleChanged(nextScale)
                            if (nextScale <= 1.05f) {
                                onPanXChanged(0f)
                            } else {
                                val maxOffsetX = ((nextScale - 1f) * containerWidth) / 2f
                                val centerX = containerWidth / 2f
                                val desiredX = (centerX - tapOffset.x) * (nextScale - 1f)
                                onPanXChanged(desiredX.coerceIn(-maxOffsetX, maxOffsetX))
                            }
                        },
                        onTap = { tapOffset ->
                            if (scale <= 1.1f) {
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
                colorFilter = colorFilter,
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
                        scaleX = scale
                        scaleY = scale
                        translationX = panX
                        clip = scale > 1.05f
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
 * Barra de Rolagem Lateral Suave e Elegante
 * - Substitui a antiga barra inferior com slider de bolinhas.
 * - Fica visível apenas ao segurar na tela para abrir os controles e some junto com eles.
 * - Permite arrastar verticalmente de forma contínua e suave para navegar pelas páginas do capítulo.
 * - Exibe uma badge flutuante sutil ao lado do polegar com o número da página atual.
 */
@Composable
fun SmoothLateralScrollbar(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit,
    onInteracting: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalPages <= 1) return

    val density = androidx.compose.ui.platform.LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Sincroniza progresso visual com a página atual quando não está arrastando
    val targetProgress = if (totalPages > 1) {
        (currentPage.toFloat() / (totalPages - 1).toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveProgress = if (isDragging) dragProgress else targetProgress
    val animatedProgress by animateFloatAsState(
        targetValue = effectiveProgress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "lateralScrollProgress"
    )

    val displayedPage = if (isDragging) {
        (dragProgress * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
    } else currentPage

    Row(
        modifier = modifier
            .padding(end = 4.dp)
            .pointerInput(totalPages, trackHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onInteracting()
                        val newProgress = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                        dragProgress = newProgress
                        val target = (newProgress * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
                        onPageSelected(target)
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onInteracting()
                        val deltaProgress = dragAmount / trackHeightPx
                        dragProgress = (dragProgress + deltaProgress).coerceIn(0f, 1f)
                        val target = (dragProgress * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
                        onPageSelected(target)
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // Badge flutuante sutil com o número da página durante arrasto ou interação
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(tween(150)) + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = fadeOut(tween(150)) + slideOutHorizontally(targetOffsetX = { it / 2 }),
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.6f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pág. ${displayedPage + 1} / $totalPages",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Pista da barra de rolagem (Track) e indicador deslizante (Thumb)
        Box(
            modifier = Modifier
                .width(32.dp) // Área de toque confortável para o polegar
                .height(260.dp)
                .onSizeChanged { size ->
                    trackHeightPx = size.height.toFloat().coerceAtLeast(1f)
                },
            contentAlignment = Alignment.Center
        ) {
            // Linha suave de fundo
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.20f), CircleShape)
            )

            // Thumb deslizante suave
            val thumbHeightDp = 38.dp
            val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
            val availableTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
            val thumbOffsetY = with(density) { (animatedProgress * availableTravelPx).toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbOffsetY)
                    .width(if (isDragging) 8.dp else 5.dp)
                    .height(thumbHeightDp)
                    .background(
                        if (isDragging) CrimsonRed else Color.White.copy(alpha = 0.85f),
                        CircleShape
                    )
            )
        }
    }
}
