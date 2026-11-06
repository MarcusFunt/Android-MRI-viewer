package com.marcusfunt.neuroview.mri

import kotlin.math.max
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Convention: radiological display for axial/coronal (patient's right on screen-left). */
enum class MriPlane(val label: String, val firstAxis: Int, val horizontalAxis: Int, val verticalAxis: Int) {
    AXIAL("Axial", 2, 0, 1),
    CORONAL("Coronal", 1, 0, 2),
    SAGITTAL("Sagittal", 0, 1, 2),
}

/** Pure Kotlin pixel buffer; Android Bitmap ownership remains in the UI layer. */
data class MprImage(val width: Int, val height: Int, val pixels: IntArray)

/** Rendered world-axis sampling metadata, explicitly separate from acquired voxel spacing. */
data class SliceSamplingInfo(
    val densityStepMm: Double,
    val renderedStepMm: Double,
    val estimatedCount: Int,
    val renderedCount: Int,
    val limitedByQualityCap: Boolean,
)

/** Reformat arbitrary oblique NIfTI orientations into orthogonal RAS+ world planes. */
object MprRenderer {
    const val MAX_IMAGE_SIDE = 384
    const val MAX_QUALITY_SIDE = 1536
    const val MAX_SLICE_COUNT = 701

    fun sliceStepWorld(volume: NiftiVolume, plane: MriPlane): Double =
        volume.affine.worldAxisSamplingStep(plane.firstAxis)

    fun sliceSamplingInfo(volume: NiftiVolume, plane: MriPlane): SliceSamplingInfo {
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        val span = bounds[1][plane.firstAxis] - bounds[0][plane.firstAxis]
        val densityStep = sliceStepWorld(volume, plane)
        val densityIntervals = span / densityStep
        val intervals = ceil(densityIntervals - 1e-9 * max(1.0, densityIntervals)).coerceAtLeast(0.0)
        val estimate = if (intervals >= Int.MAX_VALUE - 1.0) Int.MAX_VALUE else intervals.toInt() + 1
        val count = estimate.coerceIn(1, MAX_SLICE_COUNT)
        val renderedStep = if (count > 1) span / (count - 1) else densityStep
        return SliceSamplingInfo(
            densityStepMm = densityStep,
            renderedStepMm = renderedStep,
            estimatedCount = estimate,
            renderedCount = count,
            limitedByQualityCap = estimate > MAX_SLICE_COUNT,
        )
    }

    fun sliceCount(volume: NiftiVolume, plane: MriPlane): Int =
        sliceSamplingInfo(volume, plane).renderedCount

    fun sliceCount(volume: NiftiVolume, descriptor: ViewPlaneDescriptor): Int =
        PlaneGeometry.create(volume, descriptor, maxSide = 8).sliceCount

    fun slicePosition(volume: NiftiVolume, plane: MriPlane, sliceIndex: Int): Double {
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        val n = sliceCount(volume, plane)
        return bounds[0][plane.firstAxis] +
            (sliceIndex.coerceIn(0, n - 1).toDouble() / max(1, n - 1)) *
            (bounds[1][plane.firstAxis] - bounds[0][plane.firstAxis])
    }

    fun slicePosition(volume: NiftiVolume, descriptor: ViewPlaneDescriptor, sliceIndex: Int): Double? =
        PlaneGeometry.create(volume, descriptor, maxSide = 8).slicePositionMm(sliceIndex)

    /**
     * windowCenter and windowWidth are relative to 2nd–98th percentile data span;
     * samples outside the source volume are black.
     */
    fun render(
        volume: NiftiVolume,
        plane: MriPlane,
        sliceIndex: Int,
        windowCenter: Float = 0.5f,
        windowWidth: Float = 1f,
        maxSide: Int = MAX_IMAGE_SIDE,
        cancellationCheck: () -> Unit = {},
    ): MprImage {
        return render(volume, ViewPlaneDescriptor.worldAligned(plane), sliceIndex, windowCenter,
            windowWidth, maxSide, cancellationCheck)
    }

    fun render(
        volume: NiftiVolume,
        descriptor: ViewPlaneDescriptor,
        sliceIndex: Int,
        windowCenter: Float = 0.5f,
        windowWidth: Float = 1f,
        maxSide: Int = MAX_IMAGE_SIDE,
        cancellationCheck: () -> Unit = {},
    ): MprImage = MprRenderSession(
        volume = volume,
        descriptor = descriptor,
        maxSide = maxSide,
        cacheEntries = 0,
    ).render(sliceIndex, windowCenter, windowWidth, cancellationCheck)

    fun displayOrientation(plane: MriPlane) = when (plane) {
        MriPlane.AXIAL -> "R ←  A ↑  → L"
        MriPlane.CORONAL -> "R ←  S ↑  → L"
        MriPlane.SAGITTAL -> "A ←  S ↑  → P"
    }
}
