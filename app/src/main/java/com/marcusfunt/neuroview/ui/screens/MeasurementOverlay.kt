package com.marcusfunt.neuroview.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.viewer.MeasurementState
import com.marcusfunt.neuroview.ui.viewer.ViewportTransform
import kotlin.math.roundToInt

@Composable
internal fun MeasurementOverlay(
    state: MeasurementState,
    image: Bitmap?,
    viewport: IntSize,
    scale: Float,
    panOffset: Offset,
) {
    if (image == null || viewport.width <= 0 || viewport.height <= 0 || state.points.isEmpty()) return
    val transform = ViewportTransform(
        viewport.width, viewport.height, image.width, image.height, scale, panOffset,
    )
    val screenPoints = state.points.mapNotNull { point ->
        transform.screenPointForImageFraction(Offset(point.fractionX.toFloat(), point.fractionY.toFloat()))
    }
    if (screenPoints.isEmpty()) return
    Canvas(
        Modifier.fillMaxSize().semantics {
            contentDescription = state.statusText()
        },
    ) {
        if (screenPoints.size == 2) {
            drawLine(
                color = NeuroChrome.accent,
                start = screenPoints[0],
                end = screenPoints[1],
                strokeWidth = 3.dp.toPx(),
            )
        }
        screenPoints.forEach { point ->
            drawCircle(Color.Black, radius = 10.dp.toPx(), center = point)
            drawCircle(NeuroChrome.accent, radius = 7.dp.toPx(), center = point)
            drawCircle(Color.White, radius = 7.dp.toPx(), center = point, style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
internal fun MeasurementStatusOverlay(
    state: MeasurementState,
    active: Boolean,
    snapEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    onNudgePoint: (index: Int, horizontalDelta: Double, verticalDelta: Double) -> Unit,
) {
    if (!active && !state.isFinished) return
    var adjustMenuExpanded by remember(state.points.size) { mutableStateOf(false) }
    Surface(
        modifier = modifier.semantics {
            contentDescription = if (active) "Measurement controls" else "Finished distance measurement"
        },
        color = NeuroChrome.topBar.copy(alpha = 0.96f),
        contentColor = NeuroChrome.ink,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                if (active) "Measure distance" else "Distance measurement",
                color = NeuroChrome.accent,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                state.statusText(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
            )
            if (snapEnabled) {
                Text(
                    "Snaps to the fixed view grid; it does not add source detail.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeuroChrome.muted,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.points.isNotEmpty()) {
                    Box {
                        TextButton(
                            onClick = { adjustMenuExpanded = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                        ) { Text("Adjust points", style = MaterialTheme.typography.labelMedium) }
                        DropdownMenu(
                            expanded = adjustMenuExpanded,
                            onDismissRequest = { adjustMenuExpanded = false },
                        ) {
                            val directions = listOf(
                                "left" to (-1.0 to 0.0),
                                "right" to (1.0 to 0.0),
                                "up" to (0.0 to -1.0),
                                "down" to (0.0 to 1.0),
                            )
                            state.points.indices.forEach { index ->
                                val label = if (index == 0) "Point A" else "Point B"
                                directions.forEach { (direction, delta) ->
                                    DropdownMenuItem(
                                        text = { Text("$label · move $direction") },
                                        onClick = {
                                            adjustMenuExpanded = false
                                            onNudgePoint(index, delta.first * 0.005, delta.second * 0.005)
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    ) { Text("Clear", style = MaterialTheme.typography.labelMedium) }
                }
                if (active) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    ) { Text("Cancel", style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = onFinish,
                        enabled = state.points.size == 2,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    ) { Text("Finish", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
