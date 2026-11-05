package com.marcusfunt.neuroview.ui.viewer

import androidx.compose.ui.geometry.Offset
import kotlin.math.min

/** Screen-space fit, zoom, pan and fraction conversion for one rendered image. */
internal data class ViewportTransform(
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val scale: Float = 1f,
    val panOffset: Offset = Offset.Zero,
) {
    private val centerX get() = viewportWidthPx / 2f
    private val centerY get() = viewportHeightPx / 2f
    private val fitScale: Float
        get() = if (viewportWidthPx <= 0 || viewportHeightPx <= 0 ||
            sourceWidthPx <= 0 || sourceHeightPx <= 0
        ) 0f else min(
            viewportWidthPx.toFloat() / sourceWidthPx,
            viewportHeightPx.toFloat() / sourceHeightPx,
        )
    private val fittedWidth get() = sourceWidthPx * fitScale
    private val fittedHeight get() = sourceHeightPx * fitScale

    fun imageFractionForScreenPoint(point: Offset): Offset? {
        if (!isUsable() || scale <= 0f) return null
        val imageX = (point.x - centerX - panOffset.x) / scale + centerX
        val imageY = (point.y - centerY - panOffset.y) / scale + centerY
        val fractionX = (imageX - (viewportWidthPx - fittedWidth) / 2f) / fittedWidth
        val fractionY = (imageY - (viewportHeightPx - fittedHeight) / 2f) / fittedHeight
        return Offset(fractionX, fractionY).takeIf { it.x.isFinite() && it.y.isFinite() }
    }

    fun screenPointForImageFraction(fraction: Offset): Offset? {
        if (!isUsable() || scale <= 0f || !fraction.x.isFinite() || !fraction.y.isFinite()) return null
        val imageX = (viewportWidthPx - fittedWidth) / 2f + fraction.x * fittedWidth
        val imageY = (viewportHeightPx - fittedHeight) / 2f + fraction.y * fittedHeight
        val x = (imageX - centerX) * scale + centerX + panOffset.x
        val y = (imageY - centerY) * scale + centerY + panOffset.y
        return Offset(x, y).takeIf { it.x.isFinite() && it.y.isFinite() }
    }

    /** Applies a centered pinch plus centroid translation while keeping the pivot anchored. */
    fun transformed(centroid: Offset, panDelta: Offset, zoomFactor: Float): ViewportTransform {
        if (!isUsable()) return this
        val safeZoom = zoomFactor.takeIf { it.isFinite() && it > 0f } ?: 1f
        val nextScale = (scale * safeZoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = nextScale / scale.coerceAtLeast(MIN_SCALE)
        val nextPan = if (nextScale <= MIN_SCALE) {
            Offset.Zero
        } else {
            Offset(
                centroid.x - centerX + (centerX + panOffset.x - centroid.x) * ratio + panDelta.x,
                centroid.y - centerY + (centerY + panOffset.y - centroid.y) * ratio + panDelta.y,
            )
        }
        return copy(scale = nextScale, panOffset = nextPan).clamped()
    }

    fun zoomAt(point: Offset, zoomFactor: Float): ViewportTransform =
        transformed(centroid = point, panDelta = Offset.Zero, zoomFactor = zoomFactor)

    fun fit(): ViewportTransform = copy(scale = MIN_SCALE, panOffset = Offset.Zero)

    fun clamped(): ViewportTransform {
        if (!isUsable()) return copy(scale = MIN_SCALE, panOffset = Offset.Zero)
        val nextScale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (nextScale <= MIN_SCALE) return copy(scale = MIN_SCALE, panOffset = Offset.Zero)
        val limitX = ((fittedWidth * nextScale - viewportWidthPx) / 2f).coerceAtLeast(0f)
        val limitY = ((fittedHeight * nextScale - viewportHeightPx) / 2f).coerceAtLeast(0f)
        return copy(
            scale = nextScale,
            panOffset = Offset(
                panOffset.x.coerceIn(-limitX, limitX),
                panOffset.y.coerceIn(-limitY, limitY),
            ),
        )
    }

    private fun isUsable(): Boolean = fitScale > 0f && fitScale.isFinite()

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 6f
    }
}
