package com.example.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** A single owner for pinch + pan. Vertical webtoon drags remain owned by LazyColumn. */
@Composable
fun Modifier.readerGestures(
    key: Any,
    webtoon: Boolean,
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
    val latestTransform = rememberUpdatedState(transform)
    val latestLimit = rememberUpdatedState(limitX)
    val transformCallback = rememberUpdatedState(onTransform)
    val verticalCallback = rememberUpdatedState(onVerticalPan)
    val swipeCallback = rememberUpdatedState(onEdgeSwipe)
    val interactionCallback = rememberUpdatedState(onInteraction)
    val tapCallback = rememberUpdatedState(onTap)
    val doubleTapCallback = rememberUpdatedState(onDoubleTap)
    return this
        .pointerInput(key, webtoon) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                interactionCallback.value(true)
                val start = latestTransform.value()
                val startLimit = latestLimit.value()
                var travel = Offset.Zero
                var dragging = false
                var horizontal = false
                var transformed = false
                var completed = false
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointers = event.changes.count { it.pressed }
                        if (pointers >= 2) {
                            transformed = true
                            transformCallback.value(event.calculateZoom(), event.calculateCentroid(useCurrent = false), event.calculatePan())
                            event.changes.forEach { it.consume() }
                        } else if (pointers == 1) {
                            val change = event.changes.first { it.pressed }
                            val delta = change.position - change.previousPosition
                            travel += delta
                            if (!dragging && travel.getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                horizontal = abs(travel.x) > abs(travel.y)
                            }
                            if ((dragging || transformed) && latestTransform.value().scale > 1.01f && (!webtoon || horizontal || transformed)) {
                                transformCallback.value(1f, change.position, if (webtoon) Offset(delta.x, 0f) else delta)
                                if (webtoon) verticalCallback.value(delta.y)
                                change.consume()
                            }
                        } else {
                            // A platform cancellation also releases pointers; never navigate on it.
                            completed = event.changes.none { it.isConsumed }
                        }
                    } while (event.changes.any { it.pressed })
                    if (completed && !webtoon && !transformed && start.scale > 1.01f && horizontal &&
                        abs(travel.x) >= swipeThreshold && canSwipeFromEdge(start.pan.x, startLimit, travel.x)) {
                        swipeCallback.value(travel.x)
                    }
                } finally {
                    interactionCallback.value(false)
                }
            }
        }
        .pointerInput(key) {
            detectTapGestures(onTap = { tapCallback.value(it) }, onDoubleTap = { doubleTapCallback.value(it) })
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
