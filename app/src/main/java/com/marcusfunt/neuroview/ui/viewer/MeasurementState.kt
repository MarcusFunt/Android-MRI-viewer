package com.marcusfunt.neuroview.ui.viewer

import com.marcusfunt.neuroview.mri.MprProbe
import java.util.Locale

/** One temporary two-point ruler in the active physical image plane. */
data class MeasurementPoint(
    val fractionX: Double,
    val fractionY: Double,
    val worldRasMm: DoubleArray,
)

data class MeasurementState(
    val points: List<MeasurementPoint> = emptyList(),
    val isFinished: Boolean = false,
) {
    fun add(point: MeasurementPoint?): MeasurementState {
        if (point == null) return this
        val updated = if (points.size < 2) points + point else listOf(point)
        return copy(points = updated, isFinished = false)
    }

    fun move(index: Int, point: MeasurementPoint?): MeasurementState {
        if (point == null || index !in points.indices) return this
        return copy(points = points.toMutableList().also { it[index] = point }, isFinished = false)
    }

    fun clear(): MeasurementState = MeasurementState()

    fun finish(): MeasurementState =
        if (points.size == 2) copy(isFinished = true) else this

    fun distanceMm(): Double? = if (points.size == 2) {
        MprProbe.distanceMm(points[0].worldRasMm, points[1].worldRasMm)
    } else {
        null
    }

    fun statusText(): String = when (points.size) {
        0 -> "Tap two points on this slice."
        1 -> "First point set · tap the second point."
        else -> "Distance: ${String.format(Locale.US, "%.1f mm", requireNotNull(distanceMm()))}"
    }
}
