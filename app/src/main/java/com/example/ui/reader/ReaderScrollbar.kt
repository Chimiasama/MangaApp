package com.example.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ReaderScrollbar(index: Int, count: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (count < 2) return
    var height by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val selected by rememberUpdatedState(onSelected)
    val thumbHeight = with(LocalDensity.current) { 40.dp.toPx() }
    fun select(y: Float) {
        dragPosition = y.coerceIn(0f, height)
        selected((dragPosition / height * (count - 1)).roundToInt().coerceIn(0, count - 1))
    }
    val progress = if (dragging) dragPosition / height else index.toFloat() / (count - 1)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (dragging) Text("${index + 1} / $count", color = Color.White,
            modifier = Modifier.background(Color.Black.copy(alpha = .8f), RoundedCornerShape(8.dp)).padding(8.dp))
        Box(Modifier.width(48.dp).height(260.dp).testTag("reader_lateral_scrollbar")
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = "Navegar páginas"
                progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..(count - 1).toFloat(), count - 2)
                setProgress { selected(it.roundToInt().coerceIn(0, count - 1)); true }
            }
            .pointerInput(count) { detectTapGestures { select(it.y) } }
            .pointerInput(count) {
                detectVerticalDragGestures(onDragStart = { dragging = true; select(it.y) },
                    onDragEnd = { dragging = false }, onDragCancel = { dragging = false }) { change, delta ->
                    change.consume(); select(dragPosition + delta)
                }
            }, contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(Color.White.copy(alpha = .35f), RoundedCornerShape(4.dp)))
            Box(Modifier.offset { IntOffset(0, (progress * (height - thumbHeight).coerceAtLeast(0f)).roundToInt()) }
                .width(if (dragging) 8.dp else 5.dp).height(40.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
        }
    }
}
