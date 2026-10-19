package com.marcusfunt.neuroview.ui.screens

import kotlin.math.roundToInt

/** A gesture owns this coordinate until release; render frames never replace it. */
internal class SliceDragAccumulator(startSlice: Float, private val pixelsPerSlice: Float, private val lastSlice: Int) {
    private var coordinate = startSlice
    val slice: Int get() = coordinate.roundToInt().coerceIn(0, lastSlice)
    fun move(verticalPixels: Float): Int {
        coordinate = (coordinate - verticalPixels / pixelsPerSlice).coerceIn(0f, lastSlice.toFloat())
        return slice
    }
    val position: Float get() = coordinate
}

/** Piecewise linear interpolation keeps every sensitivity boundary continuous. */
internal fun precisionSensitivity(upwardDp: Float): Float {
    val height = upwardDp.coerceAtLeast(0f)
    return when {
        height <= 30f -> 1f
        height <= 70f -> 1f + (1f / 3f - 1f) * (height - 30f) / 40f
        height <= 120f -> 1f / 3f + (1f / 8f - 1f / 3f) * (height - 70f) / 50f
        height <= 160f -> 1f / 8f + (1f / 16f - 1f / 8f) * (height - 120f) / 40f
        else -> 1f / 16f
    }
}

internal class PrecisionScrubAccumulator(startSlice: Float, private val lastSlice: Int) {
    private var coordinate = startSlice
    val slice: Int get() = coordinate.roundToInt().coerceIn(0, lastSlice)
    fun move(horizontalPixels: Float, upwardDp: Float, trackWidthPixels: Float, rtl: Boolean): Int {
        val direction = if (rtl) -1f else 1f
        coordinate = (coordinate + direction * horizontalPixels / trackWidthPixels.coerceAtLeast(1f) *
            lastSlice * precisionSensitivity(upwardDp)).coerceIn(0f, lastSlice.toFloat())
        return slice
    }
}

/** Decorative Motion choice never changes navigation. Android's no-animation request wins. */
internal fun allowsSliceInertia(enabled: Boolean, systemMotionReduced: Boolean): Boolean =
    enabled && !systemMotionReduced
