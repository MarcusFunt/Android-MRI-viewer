package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import kotlin.math.abs
import kotlin.math.roundToInt

/** Single pointer owner: upward precision changes cannot compete with a Material Slider. */
@Composable
internal fun PrecisionSliceSlider(
    selectedSlice: Int,
    totalSlices: Int,
    positionLabel: String,
    onSliceChange: (Int) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastSlice = (totalSlices - 1).coerceAtLeast(0)
    val currentSlice by rememberUpdatedState(selectedSlice)
    val currentChange by rememberUpdatedState(onSliceChange)
    val currentScrubbing by rememberUpdatedState(onScrubbingChanged)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current.density
    var sensitivity by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    fun adjust(value: Int) {
        currentChange(value.coerceIn(0, lastSlice))
        currentScrubbing(false)
    }
    Box(
        modifier.height(56.dp).semantics {
            contentDescription = "Slice position"
            stateDescription = "Slice ${selectedSlice + 1} of $totalSlices, $positionLabel"
            progressBarRangeInfo = ProgressBarRangeInfo(selectedSlice.toFloat(), 0f..lastSlice.coerceAtLeast(1).toFloat(), (lastSlice - 1).coerceAtLeast(0))
            setProgress { value -> adjust(value.roundToInt()); true }
            customActions = listOf(
                CustomAccessibilityAction("Previous slice") { adjust(currentSlice - 1); true },
                CustomAccessibilityAction("Next slice") { adjust(currentSlice + 1); true },
            )
            if (lastSlice == 0) disabled()
        }.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || lastSlice == 0) false else when (event.key) {
                Key.DirectionRight -> { adjust(currentSlice + if (rtl) -1 else 1); true }
                Key.DirectionLeft -> { adjust(currentSlice + if (rtl) 1 else -1); true }
                Key.MoveHome -> { adjust(0); true }
                Key.MoveEnd -> { adjust(lastSlice); true }
                else -> false
            }
        }.focusable(enabled = lastSlice > 0).pointerInput(lastSlice, rtl, density) {
            if (lastSlice == 0) return@pointerInput
            val inset = 12.dp.toPx()
            val thumbHitRadius = 24.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                val width = (size.width - inset * 2).coerceAtLeast(1f)
                val currentFraction = currentSlice.toFloat() / lastSlice
                val thumbX = inset + width * if (rtl) 1f - currentFraction else currentFraction
                val onThumb = abs(down.position.x - thumbX) <= thumbHitRadius
                val start = if (onThumb) currentSlice else {
                    val fraction = ((down.position.x - inset) / width).coerceIn(0f, 1f)
                    ((if (rtl) 1f - fraction else fraction) * lastSlice).roundToInt()
                }
                val scrub = PrecisionScrubAccumulator(start.toFloat(), lastSlice)
                currentScrubbing(true)
                currentChange(start)
                dragging = true
                down.consume()
                var previous = down.position
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        val upward = ((size.height / 2f - change.position.y) / density).coerceAtLeast(0f)
                        sensitivity = precisionSensitivity(upward)
                        currentChange(scrub.move(change.position.x - previous.x, upward, width, rtl))
                        previous = change.position
                        change.consume()
                        if (!change.pressed) break
                    }
                } finally {
                    dragging = false
                    sensitivity = 1f
                    currentScrubbing(false)
                }
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 12.dp.toPx()
            val width = (size.width - inset * 2).coerceAtLeast(1f)
            val fraction = selectedSlice.toFloat() / lastSlice.coerceAtLeast(1)
            val x = inset + width * if (rtl) 1f - fraction else fraction
            val y = size.height / 2f
            drawLine(NeuroChrome.stroke, Offset(inset, y), Offset(size.width - inset, y), 4.dp.toPx())
            drawLine(NeuroChrome.accent, Offset(if (rtl) size.width - inset else inset, y), Offset(x, y), 4.dp.toPx())
            drawCircle(NeuroChrome.accent, 9.dp.toPx(), Offset(x, y))
        }
        if (dragging && sensitivity < 0.98f) {
            Text("Precision · 1/${(1f / sensitivity).roundToInt()}×", color = NeuroChrome.accent,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 4.dp))
        }
    }
}
