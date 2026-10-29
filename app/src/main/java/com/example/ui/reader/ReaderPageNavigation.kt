package com.example.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPageNavigation(index: Int, count: Int, previousEnabled: Boolean, nextEnabled: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onSelected: (Int) -> Unit, compact: Boolean) {
    val bubbleOffset = with(LocalDensity.current) { (-36).dp.roundToPx() }
    val source = remember { MutableInteractionSource() }
    val dragging by source.collectIsDraggedAsState()
    var dragValue by remember(index) { mutableFloatStateOf(index.toFloat()) }
    Row(Modifier.fillMaxWidth().height(if (compact) 48.dp else 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = previousEnabled, onClick = onPrevious, modifier = Modifier.size(40.dp).testTag("nav_prev_chapter")) {
            Icon(Icons.Default.SkipPrevious, "Capítulo anterior", Modifier.size(22.dp))
        }
        Box(Modifier.weight(1f)) {
            val color = MaterialTheme.colorScheme.primary
            Slider(value = if (dragging) dragValue else index.toFloat(),
                onValueChange = { dragValue = it; onSelected(it.roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))) },
                enabled = count > 1, valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(), interactionSource = source,
                thumb = { Box(Modifier.size(10.dp).background(color, CircleShape)) },
                track = { state ->
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        drawLine(Color.White.copy(alpha = .3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height)
                        val fraction = state.value / (count - 1).coerceAtLeast(1)
                        drawLine(color, Offset(0f, size.height / 2), Offset(size.width * fraction, size.height / 2), size.height)
                    }
                }, modifier = Modifier.testTag("reader_page_slider"))
            if (dragging) Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, bubbleOffset), properties = PopupProperties(focusable = false)) {
                Surface(color = Color.Black.copy(alpha = .85f), shape = MaterialTheme.shapes.small, contentColor = Color.White) {
                    Text("Página ${dragValue.roundToInt() + 1}", Modifier.padding(horizontal = 10.dp, vertical = 6.dp).testTag("reader_slider_bubble"))
                }
            }
        }
        Text("${if (count == 0) 0 else index + 1}/$count", Modifier.padding(horizontal = 6.dp).testTag("reader_position"),
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
        IconButton(enabled = nextEnabled, onClick = onNext, modifier = Modifier.size(40.dp).testTag("nav_next_chapter")) {
            Icon(Icons.Default.SkipNext, "Próximo capítulo", Modifier.size(22.dp))
        }
    }
}
