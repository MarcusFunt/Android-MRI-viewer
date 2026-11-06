package com.marcusfunt.neuroview.mri

import kotlin.math.max
import kotlin.math.roundToInt

/** Float intensities are cached independently from brightness/contrast settings. NaN means outside. */
data class MprScalarSlice(val width: Int, val height: Int, val values: FloatArray)

/**
 * Reusable sampling for one plane and resolution. Four 3-element vectors replace the old three
 * width×height coordinate maps. Each output coordinate is an affine increment from the origin.
 * The access-ordered scalar cache is bounded by both entry count and bytes.
 */
class MprRenderSession(
    private val volume: NiftiVolume,
    val descriptor: ViewPlaneDescriptor,
    maxSide: Int,
    private val cacheEntries: Int = 8,
    private val cacheBudgetBytes: Long = DEFAULT_CACHE_BUDGET_BYTES,
) {
    constructor(
        volume: NiftiVolume,
        plane: MriPlane,
        maxSide: Int,
        cacheEntries: Int = 8,
        cacheBudgetBytes: Long = DEFAULT_CACHE_BUDGET_BYTES,
    ) : this(
        volume, ViewPlaneDescriptor.worldAligned(plane), maxSide, cacheEntries,
        cacheBudgetBytes,
    )

    init {
        require(maxSide in 8..MprRenderer.MAX_QUALITY_SIDE)
        require(cacheEntries in 0..32)
        require(cacheBudgetBytes in 0L..64L * MEBIBYTE)
    }

    private val geometry = PlaneGeometry.create(volume, descriptor, maxSide)
    val width: Int = geometry.width
    val height: Int = geometry.height

    private val origin = geometry.voxelAtFractionRaw(0.0, 0.0, 0)
    private val columnStep = vectorDifference(
        geometry.voxelAtFractionRaw(1.0 / max(1, width - 1), 0.0, 0), origin,
    )
    private val rowStep = vectorDifference(
        geometry.voxelAtFractionRaw(0.0, 1.0 / max(1, height - 1), 0), origin,
    )
    private val sliceStep = if (geometry.sliceCount > 1) {
        vectorDifference(geometry.voxelAtFractionRaw(0.0, 0.0, 1), origin)
    } else doubleArrayOf(0.0, 0.0, 0.0)

    private val cache = LinkedHashMap<Int, MprScalarSlice>(cacheEntries + 1, 0.75f, true)
    private var cachedBytes = 0L

    fun sample(slice: Int, cancellationCheck: () -> Unit = {}): MprScalarSlice {
        val validSlice = slice.coerceIn(0, geometry.sliceCount - 1)
        cache[validSlice]?.let { return it }
        val values = FloatArray(width * height)
        val sliceI = origin[0] + sliceStep[0] * validSlice
        val sliceJ = origin[1] + sliceStep[1] * validSlice
        val sliceK = origin[2] + sliceStep[2] * validSlice
        val nx = volume.nx - 1.0
        val ny = volume.ny - 1.0
        val nz = volume.nz - 1.0
        for (row in 0 until height) {
            cancellationCheck()
            val rowI = sliceI + rowStep[0] * row
            val rowJ = sliceJ + rowStep[1] * row
            val rowK = sliceK + rowStep[2] * row
            var i = rowI
            var j = rowJ
            var k = rowK
            val rowOffset = row * width
            for (col in 0 until width) {
                values[rowOffset + col] = if (i < -0.0001 || j < -0.0001 || k < -0.0001 ||
                    i > nx + 0.0001 || j > ny + 0.0001 || k > nz + 0.0001
                ) Float.NaN else {
                    val exactNativeVoxel = descriptor.isNativeGrid &&
                        kotlin.math.abs(i - i.roundToInt()) < 1e-8 &&
                        kotlin.math.abs(j - j.roundToInt()) < 1e-8 &&
                        kotlin.math.abs(k - k.roundToInt()) < 1e-8
                    if (exactNativeVoxel) {
                        volume.nearest(i.roundToInt(), j.roundToInt(), k.roundToInt())
                    } else {
                        volume.sample(i, j, k)
                    }
                }
                i += columnStep[0]
                j += columnStep[1]
                k += columnStep[2]
            }
        }
        val result = MprScalarSlice(width, height, values)
        if (cacheEntries > 0 && cacheBudgetBytes > 0L) {
            cache[validSlice] = result
            cachedBytes += values.size.toLong() * Float.SIZE_BYTES
            trimCache()
        }
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
                    (((value.toDouble() - lower) / window) * 255.0).roundToInt().coerceIn(0, 255)
                pixels[index] = (255 shl 24) or (intensity shl 16) or (intensity shl 8) or intensity
            }
        }
        return MprImage(width, height, pixels)
    }

    private fun trimCache() {
        while (cache.isNotEmpty() &&
            (cache.size > cacheEntries || cachedBytes > cacheBudgetBytes)
        ) {
            val eldest = cache.entries.iterator()
            val entry = eldest.next()
            cachedBytes -= entry.value.values.size.toLong() * Float.SIZE_BYTES
            eldest.remove()
        }
    }

    private fun vectorDifference(first: DoubleArray, second: DoubleArray) =
        doubleArrayOf(first[0] - second[0], first[1] - second[1], first[2] - second[2])

    companion object {
        const val DEFAULT_CACHE_BUDGET_BYTES = 8L * 1024L * 1024L
        private const val MEBIBYTE = 1024L * 1024L
    }
}
