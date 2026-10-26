package com.example.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A unified, responsive gesture detector for reading, zoom, 2D free pan, and tap/double-tap. */
@Composable
fun Modifier.readerGestures(
    key: Any,
    webtoon: Boolean,
    controlsVisible: () -> Boolean = { false },
    transform: () -> PageTransform,
    limitX: () -> Float,
    swipeThreshold: Float,
    onTransform: (zoom: Float, focus: Offset, movement: Offset) -> Unit,
    onVerticalPan: (Float) -> Unit = {},
    onEdgeSwipe: (Float) -> Unit = {},
    onInteraction: (Boolean) -> Unit = {},
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit
): Modifier {
    val scope = rememberCoroutineScope()
    val latestTransform = rememberUpdatedState(transform)
    val latestLimit = rememberUpdatedState(limitX)
    val latestControlsVisible = rememberUpdatedState(controlsVisible)
    val transformCallback = rememberUpdatedState(onTransform)
    val verticalCallback = rememberUpdatedState(onVerticalPan)
    val swipeCallback = rememberUpdatedState(onEdgeSwipe)
    val interactionCallback = rememberUpdatedState(onInteraction)
    val tapCallback = rememberUpdatedState(onTap)
    val doubleTapCallback = rememberUpdatedState(onDoubleTap)

    var lastTapTime by remember(key) { mutableLongStateOf(0L) }
    var lastTapPos by remember(key) { mutableStateOf(Offset.Zero) }
    var singleTapJob by remember(key) { mutableStateOf<Job?>(null) }

    return this.pointerInput(key, webtoon) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            interactionCallback.value(true)
            val downTime = System.currentTimeMillis()
            val downPos = down.position
            val start = latestTransform.value()
            val startLimit = latestLimit.value()

            var travel = Offset.Zero
            var isDrag = false
            var isPinch = false

            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val activePointers = event.changes.filter { it.pressed }
                    val pointerCount = activePointers.size

                    if (pointerCount >= 2) {
                        isPinch = true
                        singleTapJob?.cancel()
                        val zoom = event.calculateZoom()
                        val centroid = event.calculateCentroid(useCurrent = false)
                        val pan = event.calculatePan()
                        transformCallback.value(zoom, centroid, pan)
                        event.changes.forEach { it.consume() }
                    } else if (pointerCount == 1 && !isPinch) {
                        val change = activePointers.first()
                        val delta = change.position - change.previousPosition
                        travel += delta

                        if (!isDrag && travel.getDistance() > viewConfiguration.touchSlop) {
                            isDrag = true
                            singleTapJob?.cancel()
                        }

                        // When zoomed: pan freely in all directions without any directional lock
                        if (isDrag && latestTransform.value().scale > 1.01f) {
                            transformCallback.value(1f, change.position, delta)
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })

                // Edge swipe check in paged mode when zoomed
                if (!webtoon && !isPinch && start.scale > 1.01f && abs(travel.x) >= swipeThreshold &&
                    canSwipeFromEdge(start.pan.x, startLimit, travel.x)
                ) {
                    swipeCallback.value(travel.x)
                }

                // Tap & Double-Tap detection:
                // If it wasn't a pinch and didn't exceed touch slop, it's a tap!
                if (!isPinch && !isDrag && travel.getDistance() <= viewConfiguration.touchSlop) {
                    val upTime = System.currentTimeMillis()
                    if (upTime - downTime < 400L) {
                        val now = upTime
                        val isDoubleTap = (now - lastTapTime < 350L) && (downPos - lastTapPos).getDistance() < 120f
                        if (isDoubleTap) {
                            lastTapTime = 0L
                            singleTapJob?.cancel()
                            doubleTapCallback.value(downPos)
                        } else {
                            lastTapTime = now
                            lastTapPos = downPos
                            if (latestControlsVisible.value()) {
                                // Controls are open: dismiss IMMEDIATELY on the spot!
                                lastTapTime = 0L
                                tapCallback.value(downPos)
                            } else {
                                // Controls are hidden: wait for potential second tap
                                singleTapJob?.cancel()
                                singleTapJob = scope.launch {
                                    delay(220L)
                                    tapCallback.value(downPos)
                                }
                            }
                        }
                    }
                }
            } finally {
                interactionCallback.value(false)
            }
        }
    }
}

/** Observe controls without consuming their buttons, sliders or accessibility gestures. */
fun Modifier.readerControlInteraction(onInteraction: (Boolean) -> Unit): Modifier = pointerInput(onInteraction) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onInteraction(true)
        try {
            do { val event = awaitPointerEvent(PointerEventPass.Initial) }
            while (event.changes.any { it.pressed })
        } finally { onInteraction(false) }
    }
}
