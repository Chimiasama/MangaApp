package com.example.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.example.source.ChapterModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val MIN_READER_SCALE = 1f
const val MAX_READER_SCALE = 4.5f

data class ReaderOptions(
    val steppedDoubleTap: Boolean = false,
    val controlsTimeoutSeconds: Int = 3,
    val preferredScale: Float = 1f,
    val barSize: String = "compact",
    val sliderPosition: String = "bottom",
    val showExtraInfo: Boolean = false
)

/** Fractions survive image resizing, rotation and a process restart; Room keeps legacy progress. */
data class ReaderViewport(
    val chapterId: String,
    val pageIndex: Int = 0,
    val offsetFraction: Float = 0f,
    val scale: Float = 1f,
    val panXFraction: Float = 0f,
    val panYFraction: Float = 0f,
    val mode: String = "WEBTOON"
)

data class PageTransform(val scale: Float = 1f, val pan: Offset = Offset.Zero)

fun fittedImageSize(image: Size, viewport: Size, fitWidth: Boolean = false): Size {
    if (image.width <= 0 || image.height <= 0 || viewport.width <= 0 || viewport.height <= 0) return Size.Zero
    val factor = if (fitWidth) viewport.width / image.width
        else min(viewport.width / image.width, viewport.height / image.height)
    return Size(image.width * factor, image.height * factor)
}

fun panLimits(image: Size, viewport: Size, scale: Float): Offset = Offset(
    max(0f, (image.width * scale - viewport.width) / 2f),
    max(0f, (image.height * scale - viewport.height) / 2f)
)

fun clampPan(pan: Offset, limits: Offset): Offset = Offset(
    if (limits.x == 0f) 0f else pan.x.coerceIn(-limits.x, limits.x),
    if (limits.y == 0f) 0f else pan.y.coerceIn(-limits.y, limits.y)
)

/** Keep the image coordinate under the fingers fixed while zooming and translating. */
fun transformAround(
    current: PageTransform,
    zoom: Float,
    centroid: Offset,
    movement: Offset,
    image: Size,
    viewport: Size
): PageTransform {
    val scale = (current.scale * zoom).coerceIn(MIN_READER_SCALE, MAX_READER_SCALE)
    val ratio = scale / current.scale
    val focus = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
    val pan = focus - (focus - current.pan) * ratio + movement
    return PageTransform(scale, clampPan(pan, panLimits(image, viewport, scale)))
}

fun anchoredScrollOffset(offset: Int, focusY: Float, oldScale: Float, newScale: Float): Int =
    (((offset + focusY) * newScale / oldScale) - focusY).toInt()

fun nextDoubleTapScale(scale: Float, stepped: Boolean): Float = if (!stepped) {
    if (scale > 1.05f) 1f else 2.5f
} else when {
    scale < 1.4f -> 1.75f
    scale < 2.3f -> 2.75f
    scale < 3.6f -> 4f
    else -> 1f
}

fun adjacentChapter(chapters: List<ChapterModel>, currentId: String?, next: Boolean): ChapterModel? {
    val ordered = chapters.distinctBy { it.id }.sortedBy { it.chapterNumber }
    val index = ordered.indexOfFirst { it.id == currentId }
    return if (index < 0) null else ordered.getOrNull(index + if (next) 1 else -1)
}

/** Positive is previous, negative is next. Reversing the drag disarms the action. */
fun accumulateChapterPull(current: Float, delta: Float, hasPrevious: Boolean, hasNext: Boolean, threshold: Float): Float {
    val candidate = current + delta
    return when {
        candidate > 0 && hasPrevious -> candidate.coerceAtMost(threshold * 1.5f)
        candidate < 0 && hasNext -> candidate.coerceAtLeast(-threshold * 1.5f)
        else -> 0f
    }
}

fun releasedChapterPull(distance: Float, threshold: Float): Boolean? =
    if (abs(distance) >= threshold) distance < 0 else null

/** Edge navigation is allowed only if this gesture STARTED at that image edge. */
fun canSwipeFromEdge(startPanX: Float, limit: Float, dragX: Float): Boolean =
    if (dragX < 0) startPanX <= -limit + 1f else startPanX >= limit - 1f
