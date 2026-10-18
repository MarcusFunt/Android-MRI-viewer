package com.marcusfunt.neuroview.mri

import kotlin.math.max
import kotlin.math.roundToInt

/** Float intensities are cached independently from brightness/contrast settings.
 * NaN marks geometrically outside voxels; source NIfTI nonfinite voxels were
 * already normalized by NiftiReader.
 */
data class MprScalarSlice(val width: Int, val height: Int, val values: FloatArray)

/**
 * Reusable world-to-voxel sampling grid for one plane and output resolution.
 * The expensive affine transformation is calculated once per output pixel,
 * not again for every scroll frame. The first-axis shift is handled as a
 * constant voxel-space delta for every slice.
 *
 * Not thread safe: one render coordinator owns one session.
 */
class MprRenderSession(
    private val volume: NiftiVolume,
    private val plane: MriPlane,
    maxSide: Int,
    private val cacheEntries: Int = 8,
    private val cancellationCheck: () -> Unit = {},
) {
    init {
        require(maxSide in 8..1024)
        require(cacheEntries in 0..32)
    }

    private val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
    private val horizontalSpan = (bounds[1][plane.horizontalAxis] -
        bounds[0][plane.horizontalAxis]).coerceAtLeast(0.001)
    private val verticalSpan = (bounds[1][plane.verticalAxis] -
        bounds[0][plane.verticalAxis]).coerceAtLeast(0.001)
    private val aspect = horizontalSpan / verticalSpan
    val width: Int = if (aspect >= 1) maxSide else (maxSide * aspect).roundToInt().coerceAtLeast(8)
    val height: Int = if (aspect < 1) maxSide else (maxSide / aspect).roundToInt().coerceAtLeast(8)

    private val baseI = DoubleArray(width * height)
    private val baseJ = DoubleArray(width * height)
    private val baseK = DoubleArray(width * height)
    private val shiftI: Double
    private val shiftJ: Double
    private val shiftK: Double

    private val cache = object : LinkedHashMap<Int, MprScalarSlice>(cacheEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MprScalarSlice>): Boolean =
            size > cacheEntries
    }

    init {
        val stepWorld = (bounds[1][plane.firstAxis] - bounds[0][plane.firstAxis]) /
            max(1, MprRenderer.sliceCount(volume, plane) - 1)
        val at = bounds[0].clone()
        val from = DoubleArray(3)
        val to = DoubleArray(3)
        volume.affine.voxelInto(at[0], at[1], at[2], from)
        at[plane.firstAxis] += stepWorld
        volume.affine.voxelInto(at[0], at[1], at[2], to)
        shiftI = to[0] - from[0]
        shiftJ = to[1] - from[1]
        shiftK = to[2] - from[2]

        at[plane.firstAxis] = bounds[0][plane.firstAxis]
        val voxel = DoubleArray(3)
        for (row in 0 until height) {
            cancellationCheck()
            at[plane.verticalAxis] = bounds[1][plane.verticalAxis] -
                row.toDouble() / max(1, height - 1) * verticalSpan
            for (col in 0 until width) {
                at[plane.horizontalAxis] = bounds[1][plane.horizontalAxis] -
                    col.toDouble() / max(1, width - 1) * horizontalSpan
                volume.affine.voxelInto(at[0], at[1], at[2], voxel)
                val index = row * width + col
                baseI[index] = voxel[0]
                baseJ[index] = voxel[1]
                baseK[index] = voxel[2]
            }
        }
    }

    fun sample(slice: Int, cancellationCheck: () -> Unit = {}): MprScalarSlice {
        cache[slice]?.let { return it }
        val values = FloatArray(width * height)
        val di = shiftI * slice
        val dj = shiftJ * slice
        val dk = shiftK * slice
        val nx = volume.nx - 1.0
        val ny = volume.ny - 1.0
        val nz = volume.nz - 1.0
        for (row in 0 until height) {
            cancellationCheck()
            for (col in 0 until width) {
                val index = row * width + col
                val i = baseI[index] + di
                val j = baseJ[index] + dj
                val k = baseK[index] + dk
                values[index] = if (i < -0.0001 || j < -0.0001 || k < -0.0001 ||
                    i > nx + 0.0001 || j > ny + 0.0001 || k > nz + 0.0001
                ) Float.NaN else volume.sample(i, j, k)
            }
        }
        val result = MprScalarSlice(width, height, values)
        if (cacheEntries > 0) cache[slice] = result
        return result
    }

    fun render(
        slice: Int,
        windowCenter: Float,
        windowWidth: Float,
        cancellationCheck: () -> Unit = {},
    ): MprImage {
        val scalar = sample(slice, cancellationCheck)
        val min = volume.percentileLow.toDouble()
        val span = (volume.percentileHigh - volume.percentileLow).toDouble().coerceAtLeast(0.0001)
        val center = min + windowCenter.toDouble() * span
        val window = windowWidth.toDouble().coerceAtLeast(0.02) * span
        val lower = center - window * 0.5
        val pixels = IntArray(scalar.values.size)
        for (row in 0 until height) {
            cancellationCheck()
            for (col in 0 until width) {
                val index = row * width + col
                val value = scalar.values[index]
                val intensity = if (value.isNaN()) 0 else
                    (((value.toDouble() - lower) / window) * 255.0)
                        .roundToInt().coerceIn(0, 255)
                pixels[index] = (255 shl 24) or (intensity shl 16) or
                    (intensity shl 8) or intensity
            }
        }
        return MprImage(width, height, pixels)
    }
}
