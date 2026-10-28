package com.example.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.motionEventSpy
import android.view.MotionEvent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.source.ChapterModel
import com.example.ui.reader.*
import com.example.ui.theme.CrimsonRed
import com.example.ui.viewmodel.ReaderUiState
import com.example.util.PagePreloaderManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Kept for callers/tests of the original progressive zoom setting.
fun getNextZoomScale(currentScale: Float): Float = nextDoubleTapScale(currentScale, stepped = true)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
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
    onScrollPositionChanged: (Int, Int) -> Unit = { page, _ -> onPageSelected(page) },
    onFlushScrollPosition: () -> Unit = {},
    onNavigateChapter: (Boolean) -> Unit,
    onToggleBookmark: (Int) -> Unit = {},
    onReaderModeChanged: (String) -> Unit = {},
    onReaderBackgroundColorChanged: (String) -> Unit = {},
    onReaderInvertColorsChanged: (Boolean) -> Unit = {},
    batterySaverEnabled: Boolean = false,
    batterySaverDimPercent: Int = 0,
    onBatterySaverToggled: (Boolean) -> Unit = {},
    onBatterySaverDimPercentChanged: (Int) -> Unit = {},
    options: ReaderOptions = ReaderOptions(),
    onOptionsChanged: (ReaderOptions) -> Unit = {},
    onViewportChanged: (ReaderViewport) -> Unit = {},
    onChapterSelected: (ChapterModel) -> Unit = {},
    onRetryChapter: () -> Unit = {},
    onReaderOrientationChanged: (String) -> Unit = {},
    onChapterCompleted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var fullscreen by remember { mutableStateOf(defaultFullscreen) }
    var mode by remember { mutableStateOf(readerMode) }
    val webtoon = mode == "WEBTOON"
    val rtl = mode == "PAGED_RTL"
    var controls by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var orientationMenu by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf(false) }
    var chapterQuery by remember { mutableStateOf("") }
    var interacting by remember { mutableStateOf(false) }
    var controlsInteracting by remember { mutableStateOf(false) }
    var interactionVersion by remember { mutableLongStateOf(0) }
    var pinching by remember { mutableStateOf(false) }
    var releasedNormally by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panXFraction by remember { mutableFloatStateOf(0f) }
    var panYFraction by remember { mutableFloatStateOf(0f) }
    var pull by remember { mutableFloatStateOf(0f) }
    var animation by remember { mutableStateOf<Job?>(null) }
    var anchorJob by remember { mutableStateOf<Job?>(null) }
    var pendingAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var restoring by remember { mutableStateOf(true) }
    var restoredVersion by remember { mutableLongStateOf(-1L) }
    var liveViewport by remember { mutableStateOf<ReaderViewport?>(null) }
    var restoredWidth by remember { mutableIntStateOf(0) }
    val imageSizes = remember(state.contentVersion) { mutableStateMapOf<String, Size>() }
    val list = rememberLazyListState()
    val pager = rememberPagerState(pageCount = { state.pages.size })
    val viewportSize = Size(size.width.toFloat(), size.height.toFloat())
    val pageIndex = if (webtoon) list.firstVisibleItemIndex else pager.currentPage
    val currentUrl = state.pages.getOrNull(pageIndex)
    val intrinsicSize = imageSizes[currentUrl] ?: Size(1000f, 1400f)
    val fitted = if (webtoon) viewportSize else fittedImageSize(intrinsicSize, viewportSize)
    val limits = panLimits(fitted, viewportSize, scale)
    val transform = PageTransform(scale, Offset(panXFraction * limits.x, if (webtoon) 0f else panYFraction * limits.y))
    val threshold = with(density) { 72.dp.toPx() }
    val previous = adjacentChapter(state.chaptersList, state.currentChapter?.id, false)
    val next = adjacentChapter(state.chaptersList, state.currentChapter?.id, true)
    val latestState by rememberUpdatedState(state)
    val latestNavigate by rememberUpdatedState(onNavigateChapter)
    val latestCompleted by rememberUpdatedState(onChapterCompleted)
    val latestFlush by rememberUpdatedState(onFlushScrollPosition)
    val latestScroll by rememberUpdatedState(onScrollPositionChanged)
    val latestViewport by rememberUpdatedState(onViewportChanged)
    val latestFitted by rememberUpdatedState(fitted)
    val latestSize by rememberUpdatedState(viewportSize)
    val latestPrevious by rememberUpdatedState(previous)
    val latestNext by rememberUpdatedState(next)

    fun touch() { interactionVersion++ }
    val controlInteraction: (Boolean) -> Unit = remember { { active -> controlsInteracting = active; interactionVersion++ } }
    val timeoutMs = if (options.controlsTimeoutSeconds == 0) 0L else (options.controlsTimeoutSeconds * 1000L).coerceAtMost(2000L)
    LaunchedEffect(controls, interactionVersion, controlsInteracting, settings, chapters, orientationMenu, options.controlsTimeoutSeconds) {
        if (controls && !settings && !chapters && !orientationMenu && timeoutMs > 0L) {
            if (!controlsInteracting) {
                delay(timeoutMs)
                controls = false
            }
        }
    }
    LaunchedEffect(readerMode) { mode = readerMode }

    DisposableEffect(fullscreen, readerOrientation) {
        var ctx = context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        val activity = ctx as? Activity
        val oldOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = when (readerOrientation) {
            "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            oldOrientation?.let { activity?.requestedOrientation = it }
            activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) latestFlush() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); latestFlush() }
    }
    BackHandler { when { settings -> settings = false; chapters -> chapters = false; else -> { latestFlush(); onBack() } } }

    // Restore once per loaded chapter, mode or viewport width, never on each progress emission.
    LaunchedEffect(state.contentVersion, state.pages, mode, size.width) {
        if (state.pages.isEmpty() || size.width == 0) return@LaunchedEffect
        restoring = true
        // Old gesture jobs must never reposition a newly loaded chapter or a resized layout.
        animation?.cancel()
        anchorJob?.cancel()
        pendingAnchor = null
        val initial = restoredVersion != state.contentVersion
        val saved = if (initial) state.viewport else liveViewport
        val target = (saved?.pageIndex ?: state.currentPageIndex).coerceIn(state.pages.indices)
        if (initial) {
            scale = saved?.scale ?: options.preferredScale
            panXFraction = saved?.panXFraction ?: 0f
            panYFraction = saved?.panYFraction ?: 0f
        }
        pull = 0f
        if (webtoon) {
            list.scrollToItem(target)
            withFrameNanos { }
            val height = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }?.size ?: 0
            val offset = if (saved != null && saved.mode == mode && saved.offsetFraction > 0) (height * saved.offsetFraction).roundToInt()
                else if (initial) state.scrollOffset else 0
            list.scrollToItem(target, offset)
        } else pager.scrollToPage(target)
        restoredVersion = state.contentVersion
        restoredWidth = size.width
        restoring = false
    }
    LaunchedEffect(state.contentVersion, mode, state.isLoading, restoring) {
        if (restoring || state.isLoading || state.pages.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val index = if (webtoon) list.firstVisibleItemIndex else pager.currentPage
            val offset = if (webtoon) list.firstVisibleItemScrollOffset else 0
            val height = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 1
            ReaderViewport(state.currentChapter?.id.orEmpty(), index,
                if (webtoon) (offset.toFloat() / height.coerceAtLeast(1)).coerceIn(0f, 1f) else 0f,
                scale, panXFraction, panYFraction, mode) to offset
        }.distinctUntilChanged().collect { (snapshot, offset) ->
            if (size.width != restoredWidth) return@collect
            liveViewport = snapshot
            latestScroll(snapshot.pageIndex, offset)
            latestViewport(snapshot)
        }
    }
    LaunchedEffect(state.contentVersion, mode, restoring, state.isLoading) {
        if (restoring || state.isLoading || state.pages.isEmpty()) return@LaunchedEffect
        val chapterId = state.currentChapter?.id ?: return@LaunchedEffect
        snapshotFlow {
            if (webtoon) {
                !list.canScrollForward && list.layoutInfo.visibleItemsInfo.any { it.index == state.pages.lastIndex } &&
                    list.layoutInfo.visibleItemsInfo.all { item -> state.pages.getOrNull(item.index) in imageSizes }
            } else pager.settledPage == state.pages.lastIndex && state.pages.last() in imageSizes
        }.distinctUntilChanged().collect { completed ->
            if (completed && size.width == restoredWidth) latestCompleted(chapterId)
        }
    }
    var lastPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager.settledPage) {
        if (!webtoon && pager.settledPage != lastPage && !restoring) { panXFraction = 0f; panYFraction = 0f }
        lastPage = pager.settledPage
    }
    DisposableEffect(state.pages, pageIndex, pagePreloadEnabled, batterySaverEnabled, state.nextChapterFirstPages) {
        val job = if (pagePreloadEnabled) PagePreloaderManager.preloadPages(context, scope, state.pages, pageIndex,
            if (batterySaverEnabled) 1 else 3, if (batterySaverEnabled) 0 else 1,
            if (batterySaverEnabled) emptyList() else state.nextChapterFirstPages) else null
        onDispose { job?.cancel() }
    }

    fun currentTransform(): PageTransform {
        val bounds = panLimits(latestFitted, latestSize, scale)
        return PageTransform(scale, Offset(panXFraction * bounds.x, if (webtoon) 0f else panYFraction * bounds.y))
    }
    fun applyZoom(zoom: Float, focus: Offset, movement: Offset) {
        // Pointer events can arrive before recomposition: accumulate against the live state.
        val before = currentTransform()
        val result = transformAround(before, zoom, focus, if (webtoon) Offset(movement.x, 0f) else movement,
            latestFitted, latestSize)
        if (webtoon && (abs(result.scale - before.scale) > 0.0001f || pendingAnchor != null)) {
            val (index, oldOffset) = pendingAnchor ?: (list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
            val offset = anchoredScrollOffset(oldOffset, focus.y, before.scale, result.scale) - movement.y.roundToInt()
            scale = result.scale
            pendingAnchor = index to offset
            anchorJob?.cancel()
            anchorJob = scope.launch {
                withFrameNanos { }
                list.scrollToItem(index, offset)
                pendingAnchor = null
            }
        } else {
            scale = result.scale
            if (webtoon && movement.y != 0f) list.dispatchRawDelta(-movement.y)
        }
        val bounds = panLimits(latestFitted, latestSize, result.scale)
        panXFraction = if (bounds.x > 0) result.pan.x / bounds.x else 0f
        panYFraction = if (!webtoon && bounds.y > 0) result.pan.y / bounds.y else 0f
    }
    fun doubleTap(focus: Offset) {
        animation?.cancel()
        val start = scale
        val target = nextDoubleTapScale(start, options.steppedDoubleTap)
        animation = scope.launch {
            Animatable(start).animateTo(target, tween(180)) { applyZoom(value / scale, focus, Offset.Zero) }
        }
    }
    fun navigatePage(physicalDelta: Float) {
        if (latestState.isLoading) return
        val forward = if (rtl) physicalDelta > 0 else physicalDelta < 0
        val target = pager.currentPage + if (forward) 1 else -1
        if (target in latestState.pages.indices) scope.launch { pager.animateScrollToPage(target) }
        else latestNavigate(forward)
    }
    val nested = remember(mode, state.contentVersion) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || latestState.isLoading || pinching) return Offset.Zero
                val delta = if (webtoon) available.y else available.x * if (rtl) -1 else 1
                val contentMoved = if (webtoon) consumed.y else consumed.x
                if (contentMoved != 0f && abs(delta) < 0.1f) {
                    pull = 0f
                    return Offset.Zero
                }
                val atStart = if (webtoon) !list.canScrollBackward else pager.currentPage == 0
                val atEnd = if (webtoon) !list.canScrollForward else pager.currentPage == latestState.pages.lastIndex
                val oldReady = abs(pull) >= threshold
                pull = accumulateChapterPull(pull, delta, atStart && latestPrevious != null,
                    atEnd && latestNext != null, threshold)
                val ready = abs(pull) >= threshold
                if (!oldReady && ready) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                return if (pull == 0f) Offset.Zero else if (webtoon) Offset(0f, available.y) else Offset(available.x, 0f)
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val direction = if (releasedNormally && !pinching && !latestState.isLoading) releasedChapterPull(pull, threshold) else null
                pull = 0f
                releasedNormally = false
                direction?.let { latestNavigate(it) }
                return Velocity.Zero
            }
        }
    }
    val clock by produceState("") {
        while (true) {
            val battery = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val percent = battery?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            value = if (percent in 0..100) "$time  •  $percent%" else time
            delay(60_000)
        }
    }
    val background = if (batterySaverEnabled) Color.Black else when (readerBackgroundColor) {
        "LIGHT" -> Color(0xFFF8F9FA); "SEPIA" -> Color(0xFFF4ECD8); "DARK" -> Color(0xFF141416); else -> Color.Black
    }
    val filter = remember(readerInvertColors) {
        if (readerInvertColors) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))) else null
    }
    fun imageLoaded(url: String, image: Size) {
        if (imageSizes[url] == image) return
        val first = list.firstVisibleItemIndex
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == first }
        val saved = if (restoredVersion != state.contentVersion) state.viewport else liveViewport
        val fraction = saved?.takeIf { it.pageIndex == first && it.mode == mode }?.offsetFraction
            ?: if (item != null) list.firstVisibleItemScrollOffset.toFloat() / item.size.coerceAtLeast(1) else 0f
        imageSizes[url] = image
        if (webtoon && url == state.pages.getOrNull(first) && fraction > 0) scope.launch {
            withFrameNanos { }
            val height = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == first }?.size ?: return@launch
            list.scrollToItem(first, (fraction * height).roundToInt())
        }
    }

    Box(Modifier.fillMaxSize().background(background).onSizeChanged { size = it }
        .motionEventSpy { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL -> { releasedNormally = false; pull = 0f }
                MotionEvent.ACTION_UP -> releasedNormally = true
            }
        }
        .testTag("reader_screen").semantics { stateDescription = "zoom=$scale;page=$pageIndex" }
        .nestedScroll(nested)) {
        if (state.pages.isNotEmpty()) {
            val gestureModifier = Modifier.fillMaxSize().clipToBounds().readerGestures(
                key = state.contentVersion to mode,
                webtoon = webtoon,
                controlsVisible = { controls },
                transform = { currentTransform() },
                limitX = { panLimits(latestFitted, latestSize, scale).x },
                swipeThreshold = threshold,
                onTransform = { zoom, focus, movement ->
                    if (zoom != 1f) { pinching = true; pull = 0f }
                    applyZoom(zoom, focus, movement)
                },
                onVerticalPan = { list.dispatchRawDelta(-it) },
                onEdgeSwipe = { if (releasedNormally) navigatePage(it) },
                onInteraction = { active ->
                    interacting = active; touch()
                    if (active) animation?.cancel() else pinching = false
                },
                onTap = { position ->
                    if (controls) {
                        controls = false
                    } else {
                        if (webtoon || scale > 1.01f || position.x in (size.width * 0.28f)..(size.width * 0.72f)) {
                            controls = true; touch()
                        } else navigatePage(if (position.x < size.width / 2) 1f else -1f)
                    }
                },
                onDoubleTap = { doubleTap(it) }
            )
            Box(gestureModifier) {
                if (webtoon) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("reader_webtoon")) {
                        itemsIndexed(state.pages, key = { index, url -> "${state.currentChapter?.id}:$index:$url" }) { index, url ->
                            val image = imageSizes[url] ?: Size(1000f, 1400f)
                            val width = with(density) { (size.width * scale).toDp() }
                            val height = width * (image.height / image.width)
                            Box(Modifier.fillMaxWidth().height(height).testTag("webtoon_page_$index"), contentAlignment = Alignment.TopCenter) {
                                ReaderPageImage(url, index, filter, ContentScale.FillWidth,
                                    Modifier.requiredWidth(width).fillMaxHeight().graphicsLayer { translationX = transform.pan.x },
                                    onLoaded = { imageLoaded(url, it) })
                            }
                        }
                    }
                } else {
                    HorizontalPager(state = pager, reverseLayout = rtl, userScrollEnabled = scale <= 1.01f,
                        modifier = Modifier.fillMaxSize().testTag("reader_pager")) { index ->
                        val url = state.pages[index]
                        ReaderPageImage(url, index, filter, ContentScale.Fit,
                            Modifier.fillMaxSize().testTag("reader_page_$index").graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = if (index == pager.currentPage) transform.pan.x else 0f
                                translationY = if (index == pager.currentPage) transform.pan.y else 0f
                            }, onLoaded = { imageLoaded(url, it) })
                    }
                }
            }
        }
        if (batterySaverDimPercent > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = batterySaverDimPercent / 100f)))
        if (abs(pull) > 0) {
            val target = if (pull < 0) next else previous
            val ready = abs(pull) >= threshold
            Surface(modifier = Modifier.align(if (pull < 0) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(24.dp).testTag("chapter_pull_indicator"), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (ready) "Solte para abrir: ${target?.title}" else "Puxe para abrir: ${target?.title}")
                    LinearProgressIndicator(progress = { (abs(pull) / threshold).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("Recue o dedo para cancelar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        AnimatedVisibility(controls && state.pages.size > 1, modifier = Modifier.align(Alignment.CenterEnd)
            .readerControlInteraction(controlInteraction)) {
            ReaderScrollbar(pageIndex, state.pages.size, onSelected = { target ->
                touch(); scope.launch { if (webtoon) list.scrollToItem(target) else pager.scrollToPage(target) }
            })
        }
        AnimatedVisibility(controls, modifier = Modifier.align(Alignment.TopCenter).testTag("reader_controls")) {
            Surface(color = Color.Black.copy(alpha = 0.94f), contentColor = Color.White,
                modifier = Modifier.fillMaxWidth().readerControlInteraction(controlInteraction)) {
                Column(Modifier.statusBarsPadding().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { latestFlush(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Sair do leitor") }
                        Column(Modifier.weight(1f)) {
                            Text(state.mangaTitle, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                            Text(state.currentChapter?.title ?: "Leitor", maxLines = 1)
                        }
                        Box {
                            IconButton(onClick = { orientationMenu = true; touch() }, modifier = Modifier.testTag("reader_orientation")) {
                                Icon(when (readerOrientation) {
                                    "PORTRAIT" -> Icons.Default.ScreenLockPortrait
                                    "LANDSCAPE" -> Icons.Default.ScreenLockLandscape
                                    else -> Icons.Default.ScreenRotation
                                }, when (readerOrientation) {
                                    "PORTRAIT" -> "Orientação: retrato travado"
                                    "LANDSCAPE" -> "Orientação: paisagem travada"
                                    else -> "Orientação: rotação livre"
                                })
                            }
                            DropdownMenu(expanded = orientationMenu, onDismissRequest = { orientationMenu = false; touch() }) {
                                listOf("PORTRAIT" to "Travar em retrato", "LANDSCAPE" to "Travar em paisagem", "SENSOR" to "Liberar rotação").forEach { (value, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        latestFlush(); onReaderOrientationChanged(value); orientationMenu = false; touch()
                                    }, modifier = Modifier.testTag("reader_orientation_$value"))
                                }
                            }
                        }
                        IconButton(onClick = { settings = true; touch() }) { Icon(Icons.Default.Tune, "Opções do leitor") }
                        IconButton(onClick = { onToggleBookmark(pageIndex); touch() }) {
                            Icon(if (pageIndex in state.bookmarkedPages) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Marcar página")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(enabled = previous != null && !state.isLoading, onClick = { latestNavigate(false); touch() }, modifier = Modifier.testTag("nav_prev_chapter")) { Text("Anterior") }
                        TextButton(enabled = state.pages.isNotEmpty(), onClick = {
                            scope.launch { if (webtoon) list.scrollToItem(0) else pager.scrollToPage(0) }; touch()
                        }) { Text("Início") }
                        TextButton(onClick = { chapters = true; touch() }, modifier = Modifier.testTag("nav_chapters")) { Text("Capítulos") }
                        TextButton(enabled = next != null && !state.isLoading, onClick = { latestNavigate(true); touch() }, modifier = Modifier.testTag("nav_next_chapter")) { Text("Próximo") }
                    }
                    if (state.pages.isNotEmpty()) Text(
                        "${pageIndex + 1} / ${state.pages.size}  •  ${"%.2f".format(scale)}×  •  $clock",
                        modifier = Modifier.testTag("reader_position").padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (state.pages.size > 1) Slider(value = pageIndex.toFloat(), valueRange = 0f..state.pages.lastIndex.toFloat(),
                        onValueChange = { value ->
                            touch(); scope.launch { if (webtoon) list.scrollToItem(value.roundToInt()) else pager.scrollToPage(value.roundToInt()) }
                        }, modifier = Modifier.testTag("reader_page_slider"))
                }
            }
        }
        if (state.isLoading) Surface(color = Color.Black.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.Center)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Carregando ${state.pendingChapter?.title ?: "capítulo"}…")
            }
        }
        if (state.errorMessage != null) Surface(modifier = Modifier.align(Alignment.Center).padding(24.dp), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp)) {
                Text(state.errorMessage)
                Button(onClick = onRetryChapter, modifier = Modifier.testTag("retry_chapter")) { Text("Tentar novamente") }
                if (state.pages.isNotEmpty()) TextButton(onClick = { chapters = true }) { Text("Escolher outro capítulo") }
            }
        }
    }
    if (chapters) ModalBottomSheet(onDismissRequest = { chapters = false; touch() }) {
        OutlinedTextField(chapterQuery, onValueChange = { chapterQuery = it }, label = { Text("Buscar capítulo") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("reader_chapter_search"))
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f).testTag("reader_chapter_list")) {
            itemsIndexed(state.chaptersList.distinctBy { it.id }.sortedBy { it.chapterNumber }.filter {
                chapterQuery.isBlank() || it.title.contains(chapterQuery, true) || it.chapterNumber.toString().contains(chapterQuery)
            }, key = { _, chapter -> chapter.id }) { _, chapter ->
                ListItem(headlineContent = { Text(chapter.title) }, supportingContent = {
                    if (chapter.id == state.currentChapter?.id) Text("Lendo agora")
                }, modifier = Modifier.clickable(enabled = !state.isLoading) {
                    onChapterSelected(chapter); chapters = false; touch()
                }.testTag("reader_chapter_${chapter.id}"))
            }
        }
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false; touch() }) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("reader_options")) {
            item {
                Text("Leitura", style = MaterialTheme.typography.titleLarge)
                listOf("WEBTOON" to "Webtoon contínuo", "PAGED_RTL" to "Mangá: direita → esquerda", "PAGED_LTR" to "Páginas: esquerda → direita").forEach { (key, title) ->
                    Row(Modifier.fillMaxWidth().clickable { mode = key; onReaderModeChanged(key) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == key, onClick = { mode = key; onReaderModeChanged(key) }); Text(title)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Duplo toque com zoom em etapas", Modifier.weight(1f))
                    Switch(options.steppedDoubleTap, { onOptionsChanged(options.copy(steppedDoubleTap = it, preferredScale = scale)) })
                }
                Text(if (options.controlsTimeoutSeconds == 0) "Controles: manter visíveis" else "Esconder controles após ${options.controlsTimeoutSeconds}s")
                Slider(options.controlsTimeoutSeconds.toFloat(), onValueChange = {
                    onOptionsChanged(options.copy(controlsTimeoutSeconds = it.roundToInt(), preferredScale = scale))
                }, valueRange = 0f..15f, steps = 14, modifier = Modifier.testTag("reader_timeout_slider"))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tela cheia", Modifier.weight(1f)); Switch(fullscreen, { fullscreen = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Inverter cores", Modifier.weight(1f)); Switch(readerInvertColors, onReaderInvertColorsChanged)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("OLED" to "Preto", "DARK" to "Escuro", "SEPIA" to "Sépia", "LIGHT" to "Claro").forEach { (key, label) ->
                        TextButton(onClick = { onReaderBackgroundColorChanged(key) }) { Text(label) }
                    }
                }
                Text("Escurecimento da tela: $batterySaverDimPercent%")
                Slider(batterySaverDimPercent.toFloat(), onValueChange = { onBatterySaverDimPercentChanged(it.roundToInt()) },
                    valueRange = 0f..60f, modifier = Modifier.testTag("reader_dimming_slider"))
                TextButton(onClick = { applyZoom(1f / scale, Offset(size.width / 2f, size.height / 2f), Offset.Zero); touch() },
                    modifier = Modifier.testTag("reader_reset_zoom")) { Text("Restaurar zoom e enquadramento") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ReaderPageImage(
    url: String, index: Int, filter: ColorFilter?, contentScale: ContentScale,
    modifier: Modifier, onLoaded: (Size) -> Unit
) {
    val context = LocalContext.current
    val retryScope = rememberCoroutineScope()
    var effectiveUrl by remember(url) { mutableStateOf(url) }
    var retrying by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }
    var loaded by remember(url, retry) { mutableStateOf(false) }
    val request = remember(url, effectiveUrl, retry) {
        ImageRequest.Builder(context).data(effectiveUrl).memoryCacheKey(url).diskCacheKey(url)
            .memoryCachePolicy(if (retry > 0) coil.request.CachePolicy.WRITE_ONLY else coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(if (retry > 0) coil.request.CachePolicy.WRITE_ONLY else coil.request.CachePolicy.ENABLED)
            .apply { if (retry > 0) setHeader("Cache-Control", "no-cache") }.build()
    }
    key(url, retry) {
        SubcomposeAsyncImage(model = request, contentDescription = "Página ${index + 1}",
            modifier = modifier.testTag("reader_image_$index").semantics { stateDescription = if (loaded) "loaded" else "loading" },
            contentScale = contentScale, colorFilter = filter,
            onSuccess = {
                loaded = true
                val drawable = it.result.drawable
                if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0)
                    onLoaded(Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat()))
            }, loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
            error = { errorState ->
                Box(Modifier.fillMaxSize().background(Color(0xFF18181C)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Erro ao carregar página ${index + 1}", color = Color.White)
                        Button(enabled = !retrying, onClick = {
                            retryScope.launch {
                                retrying = true
                                try {
                                    val error = errorState.result.throwable
                                    val code = (error as? coil.network.HttpException)?.response?.code
                                    val certificateFailure = generateSequence(error) { it.cause }.take(16).any {
                                        it is java.security.cert.CertificateException || it is javax.net.ssl.SSLPeerUnverifiedException
                                    }
                                    val registry = com.example.data.network.MangaDexPages.registry
                                    if (registry.contains(url) && code !in listOf(403, 429) && !certificateFailure) {
                                        registry.invalidate(url)
                                        val alternatives = try { registry.alternatives(url) }
                                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                        catch (_: Exception) { emptyList() }
                                        effectiveUrl = alternatives.firstOrNull { it != effectiveUrl } ?: url
                                    }
                                    retry++
                                } finally { retrying = false }
                            }
                        }, modifier = Modifier.testTag("retry_page_$index")) { Text(if (retrying) "Atualizando página…" else "Tentar novamente") }
                    }
                }
            })
    }
}
