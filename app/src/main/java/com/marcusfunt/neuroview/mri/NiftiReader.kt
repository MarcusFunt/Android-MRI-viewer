package com.marcusfunt.neuroview.mri

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/** One NIfTI-1 scalar 3D frame in physical RAS+ coordinates. */
class NiftiVolume(
    val nx: Int,
    val ny: Int,
    val nz: Int,
    val values: FloatArray,
    val affine: SpatialAffine,
    val percentileLow: Float,
    val percentileHigh: Float,
    val transformSource: String,
    val timepoints: Int,
) {
    init {
        require(values.size.toLong() == nx.toLong() * ny * nz)
    }

    fun nearest(i: Int, j: Int, k: Int): Float =
        values[(k * ny + j) * nx + i]

    /** Trilinear interpolation at fractional voxel coordinates; exterior is black. */
    fun sample(i: Double, j: Double, k: Double): Float {
        if (i < -0.0001 || j < -0.0001 || k < -0.0001 ||
            i > nx - 1 + 0.0001 || j > ny - 1 + 0.0001 || k > nz - 1 + 0.0001
        ) return 0f
        val x = i.coerceIn(0.0, (nx - 1).toDouble())
        val y = j.coerceIn(0.0, (ny - 1).toDouble())
        val z = k.coerceIn(0.0, (nz - 1).toDouble())
        val x0 = x.toInt(); val y0 = y.toInt(); val z0 = z.toInt()
        val x1 = (x0 + 1).coerceAtMost(nx - 1)
        val y1 = (y0 + 1).coerceAtMost(ny - 1)
        val z1 = (z0 + 1).coerceAtMost(nz - 1)
        val fx = (x - x0).toFloat(); val fy = (y - y0).toFloat(); val fz = (z - z0).toFloat()
        fun line(a: Float, b: Float, f: Float) = a * (1f - f) + b * f
        return line(
            line(
                line(nearest(x0, y0, z0), nearest(x1, y0, z0), fx),
                line(nearest(x0, y1, z0), nearest(x1, y1, z0), fx), fy,
            ),
            line(
                line(nearest(x0, y0, z1), nearest(x1, y0, z1), fx),
                line(nearest(x0, y1, z1), nearest(x1, y1, z1), fx), fy,
            ), fz,
        )
    }
}

/**
 * Streamed .nii / .nii.gz NIfTI-1 single-file loader. Rejects unsupported formats,
 * invalid spatial transforms, excessive allocations and truncated payloads.
 * No external native libraries or network access.
 */
object NiftiReader {
    const val MAX_VOXELS = 48_000_000L // ~183 MiB float volume, intentionally bounded

    fun read(input: InputStream): NiftiVolume {
        val buffered = BufferedInputStream(input, 64 * 1024)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        val source = if (first == 0x1f && second == 0x8b) {
            GZIPInputStream(buffered, 64 * 1024)
        } else buffered
        DataInputStream(source).use { stream ->
            val headerBytes = ByteArray(348)
            stream.readFully(headerBytes)
            val initial = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val order = when {
                initial.getInt(0) == 348 -> ByteOrder.LITTLE_ENDIAN
                initial.order(ByteOrder.BIG_ENDIAN).getInt(0) == 348 -> ByteOrder.BIG_ENDIAN
                else -> throw IllegalArgumentException("Not a NIfTI-1 header (NIfTI-2 and Analyze unsupported)")
            }
            val header = ByteBuffer.wrap(headerBytes).order(order)
            require(headerBytes.copyOfRange(344, 348).contentEquals(byteArrayOf(110, 43, 49, 0))) {
                "Only single-file NIfTI-1 (.nii/.nii.gz) is supported"
            }
            val dimensions = (0..7).map { header.getShort(40 + it * 2).toInt() }
            require(dimensions[0] in 3..7) { "Expected a 3D or 4D NIfTI volume" }
            val nx = dimensions[1]
            val ny = dimensions[2]
            val nz = dimensions[3]
            require(nx in 1..4096 && ny in 1..4096 && nz in 1..4096) {
                "Invalid NIfTI dimensions"
            }
            // Supports the first frame of 4D; higher dimensions must be singleton.
            require((5..7).all { dimensions[it] <= 1 }) {
                "Multi-channel / 5D NIfTI is not supported yet"
            }
            val timepoints = if (dimensions[0] >= 4) dimensions[4] else 1
            require(timepoints in 1..100000) { "Invalid NIfTI temporal dimension" }
            val count = nx.toLong() * ny * nz
            require(count in 1..MAX_VOXELS) {
                "Volume too large for mobile preview (${count} voxels; limit $MAX_VOXELS)"
            }

            val datatype = header.getShort(70).toInt()
            val bitpix = header.getShort(72).toInt()
            val bytesPerVoxel = when (datatype) {
                2, 256 -> 1                   // uint8, int8
                4, 512 -> 2                   // int16, uint16
                8, 16, 768 -> 4               // int32, float32, uint32
                64, 1024 -> 8                 // float64, int64
                else -> throw IllegalArgumentException("Unsupported NIfTI datatype: $datatype")
            }
            require(bitpix == bytesPerVoxel * 8) { "Datatype/bitpix mismatch" }
            val offset = header.getFloat(108).toDouble()
            require(offset.isFinite() && offset >= 352.0 && offset <= 64.0 * 1024 * 1024) {
                "Invalid or excessive NIfTI data offset"
            }
            val intOffset = offset.toInt()
            require(offset == intOffset.toDouble()) { "Invalid noninteger NIfTI data offset" }

            val pixdim = (0..3).map { header.getFloat(76 + 4 * it).toDouble() }
            // NIfTI xyzt_units lower 3 bits: metres=1, millimetres=2, microns=3.
            // All in-app world coordinates are millimetres (also for sform offsets).
            val spatialUnits = header.get(123).toInt() and 0x07
            val millimetres = when (spatialUnits) {
                1 -> 1000.0
                2, 0 -> 1.0 // unspecified is commonly interpreted as millimetres
                3 -> 0.001
                else -> throw IllegalArgumentException("Unsupported NIfTI spatial units")
            }
            val dx = (pixdim[1].takeIf { it.isFinite() && it > 0.0 } ?: 1.0) * millimetres
            val dy = (pixdim[2].takeIf { it.isFinite() && it > 0.0 } ?: 1.0) * millimetres
            val dz = (pixdim[3].takeIf { it.isFinite() && it > 0.0 } ?: 1.0) * millimetres
            val sform = header.getShort(254).toInt()
            val qform = header.getShort(252).toInt()
            val affine: SpatialAffine
            val transformSource: String
            if (sform > 0) {
                val rows = DoubleArray(12) { i ->
                    header.getFloat(280 + 4 * i).toDouble() * millimetres
                }
                affine = SpatialAffine.fromRows(rows)
                transformSource = "sform (RAS+)"
            } else if (qform > 0) {
                affine = SpatialAffine.fromQuaternion(
                    bInput = header.getFloat(256).toDouble(),
                    cInput = header.getFloat(260).toDouble(),
                    dInput = header.getFloat(264).toDouble(),
                    dx = dx, dy = dy, dz = dz, qfac = pixdim[0],
                    tx = header.getFloat(268).toDouble() * millimetres,
                    ty = header.getFloat(272).toDouble() * millimetres,
                    tz = header.getFloat(276).toDouble() * millimetres,
                )
                transformSource = "qform (RAS+)"
            } else {
                affine = SpatialAffine.axisAligned(dx, dy, dz)
                transformSource = "NO ORIENTATION — assumed RAS; labels are not reliable"
            }

            val slopeField = header.getFloat(112).toDouble()
            val slope = if (slopeField.isFinite() && slopeField != 0.0) slopeField else 1.0
            val interceptField = header.getFloat(116).toDouble()
            val intercept = if (slopeField.isFinite() && slopeField != 0.0 && interceptField.isFinite()) {
                interceptField
            } else 0.0
            // header was 348 bytes; the remaining data offset includes extension bytes.
            var remaining = intOffset - 348L
            val discard = ByteArray(16 * 1024)
            while (remaining > 0) {
                val n = stream.read(discard, 0, minOf(remaining, discard.size.toLong()).toInt())
                require(n > 0) { "Truncated NIfTI header/extension" }
                remaining -= n
            }

            val data = FloatArray(count.toInt())
            val chunk = ByteArray(64 * 1024)
            val voxelsPerChunk = chunk.size / bytesPerVoxel
            var index = 0
            while (index < data.size) {
                val batch = minOf(voxelsPerChunk, data.size - index)
                stream.readFully(chunk, 0, batch * bytesPerVoxel)
                val bytes = ByteBuffer.wrap(chunk, 0, batch * bytesPerVoxel).order(order)
                for (p in 0 until batch) {
                    val number = when (datatype) {
                        2 -> (bytes.get().toInt() and 255).toDouble()
                        256 -> bytes.get().toDouble()
                        4 -> bytes.short.toDouble()
                        512 -> (bytes.short.toInt() and 65535).toDouble()
                        8 -> bytes.int.toDouble()
                        16 -> bytes.float.toDouble()
                        768 -> (bytes.int.toLong() and 0xffffffffL).toDouble()
                        64 -> bytes.double
                        else -> bytes.long.toDouble()
                    }
                    val result = number * slope + intercept
                    data[index++] = if (result.isFinite()) result.toFloat() else 0f
                }
            }
            val samples = FloatArray(minOf(48_000, data.size))
            val stride = max(1, data.size / samples.size)
            for (i in samples.indices) samples[i] = data[(i * stride).coerceAtMost(data.lastIndex)]
            samples.sort()
            val low = samples[(samples.lastIndex * 0.02).toInt()]
            val high = samples[(samples.lastIndex * 0.98).toInt()]
            val upper = if (high > low) high else low + 1f
            return NiftiVolume(nx, ny, nz, data, affine, low, upper, transformSource, timepoints)
        }
    }
}
