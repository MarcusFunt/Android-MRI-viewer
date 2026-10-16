package com.marcusfunt.neuroview.mri

import kotlin.math.max
import kotlin.math.roundToInt

/** Convention: radiological display for axial/coronal (patient's right on screen-left). */
enum class MriPlane(val label: String, val firstAxis: Int, val horizontalAxis: Int, val verticalAxis: Int) {
    AXIAL("Axial", 2, 0, 1),
    CORONAL("Coronal", 1, 0, 2),
    SAGITTAL("Sagittal", 0, 1, 2),
}

/** Pure Kotlin pixel buffer; Android Bitmap ownership remains in the UI layer. */
data class MprImage(val width: Int, val height: Int, val pixels: IntArray)

/** Reformat arbitrary oblique NIfTI orientations into orthogonal RAS+ world planes. */
object MprRenderer {
    const val MAX_IMAGE_SIDE = 384
    const val MAX_SLICE_COUNT = 701

    fun sliceCount(volume: NiftiVolume, plane: MriPlane): Int {
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        val span = bounds[1][plane.firstAxis] - bounds[0][plane.firstAxis]
        val resolution = (0..2).minOf { volume.affine.axisSpacing(it) }
        return (span / resolution).roundToInt().coerceIn(0, MAX_SLICE_COUNT - 1) + 1
    }

    fun slicePosition(volume: NiftiVolume, plane: MriPlane, sliceIndex: Int): Double {
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        val n = sliceCount(volume, plane)
        return bounds[0][plane.firstAxis] +
            (sliceIndex.coerceIn(0, n - 1).toDouble() / max(1, n - 1)) *
            (bounds[1][plane.firstAxis] - bounds[0][plane.firstAxis])
    }

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
    ): MprImage {
        require(maxSide in 8..1024)
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        val horizontalSpan = (bounds[1][plane.horizontalAxis] -
            bounds[0][plane.horizontalAxis]).coerceAtLeast(0.001)
        val verticalSpan = (bounds[1][plane.verticalAxis] -
            bounds[0][plane.verticalAxis]).coerceAtLeast(0.001)
        val aspect = horizontalSpan / verticalSpan
        val width = if (aspect >= 1) maxSide else (maxSide * aspect).roundToInt().coerceAtLeast(8)
        val height = if (aspect < 1) maxSide else (maxSide / aspect).roundToInt().coerceAtLeast(8)
        val slicePosition = slicePosition(volume, plane, sliceIndex)
        val min = volume.percentileLow.toDouble()
        val span = (volume.percentileHigh - volume.percentileLow).toDouble().coerceAtLeast(0.0001)
        val center = min + windowCenter.toDouble() * span
        val widthWindow = windowWidth.toDouble().coerceAtLeast(0.02) * span
        val lower = center - widthWindow * 0.5
        val image = IntArray(width * height)
        val world = DoubleArray(3)
        world[plane.firstAxis] = slicePosition

        for (row in 0 until height) {
            // Image-top means +anterior for axial, +superior for coronal/sagittal.
            val verticalFraction = row.toDouble() / max(1, height - 1)
            world[plane.verticalAxis] = bounds[1][plane.verticalAxis] -
                verticalFraction * verticalSpan
            for (col in 0 until width) {
                // Image-left means patient-right (axial/coronal) or anterior (sagittal).
                val horizontalFraction = col.toDouble() / max(1, width - 1)
                world[plane.horizontalAxis] = bounds[1][plane.horizontalAxis] -
                    horizontalFraction * horizontalSpan
                val voxel = volume.affine.voxel(world[0], world[1], world[2])
                val value = volume.sample(voxel[0], voxel[1], voxel[2]).toDouble()
                val intensity = (((value - lower) / widthWindow) * 255.0)
                    .roundToInt().coerceIn(0, 255)
                image[row * width + col] =
                    (255 shl 24) or (intensity shl 16) or (intensity shl 8) or intensity
            }
        }
        return MprImage(width, height, image)
    }

    fun displayOrientation(plane: MriPlane) = when (plane) {
        MriPlane.AXIAL -> "R ←  A ↑  → L"
        MriPlane.CORONAL -> "R ←  S ↑  → L"
        MriPlane.SAGITTAL -> "A ←  S ↑  → P"
    }
}
