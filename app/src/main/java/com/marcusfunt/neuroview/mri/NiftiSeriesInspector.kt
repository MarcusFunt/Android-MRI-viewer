package com.marcusfunt.neuroview.mri

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

/**
 * Reads ONLY the NIfTI-1 header for fast catalog indexing. No voxel data is allocated.
 * A catalog entry is not considered a validated volume until NiftiReader.read succeeds.
 */
data class NiftiSeriesMetadata(
    val dimensions: List<Int>,
    val spacingMm: List<Double>,
    val timepoints: Int,
    val dataType: String,
    val orientation: String,
    val description: String,
)

object NiftiSeriesInspector {
    private val dataTypes = mapOf(
        2 to ("uint8" to 8),
        256 to ("int8" to 8),
        4 to ("int16" to 16),
        512 to ("uint16" to 16),
        8 to ("int32" to 32),
        768 to ("uint32" to 32),
        16 to ("float32" to 32),
        64 to ("float64" to 64),
        1024 to ("int64" to 64),
    )

    fun inspect(input: InputStream, cancellationCheck: () -> Unit = {}): NiftiSeriesMetadata {
        val source = BufferedInputStream(input, 8192)
        source.mark(2)
        val gzip = source.read() == 0x1f && source.read() == 0x8b
        source.reset()
        val raw = if (gzip) GZIPInputStream(source, 8192) else source

        DataInputStream(raw).use { stream ->
            cancellationCheck()
            val bytes = ByteArray(348)
            var offset = 0
            while (offset < bytes.size) {
                cancellationCheck()
                val count = stream.read(bytes, offset, bytes.size - offset)
                if (count < 0) throw java.io.EOFException()
                if (count == 0) continue
                offset += count
            }
            val little = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val order = when {
                little.getInt(0) == 348 -> ByteOrder.LITTLE_ENDIAN
                little.order(ByteOrder.BIG_ENDIAN).getInt(0) == 348 -> ByteOrder.BIG_ENDIAN
                else -> throw IllegalArgumentException("Not a supported NIfTI-1 file")
            }
            val buffer = ByteBuffer.wrap(bytes).order(order)
            require(
                bytes.copyOfRange(344, 348).contentEquals(
                    byteArrayOf(110, 43, 49, 0),
                ),
            ) { "NIfTI single-file magic missing" }
            val dimensionality = buffer.getShort(40).toInt()
            require(dimensionality in 3..7) { "Not a 3D/4D NIfTI volume" }
            val dims = (1..3).map { buffer.getShort(40 + it * 2).toInt() }
            require(dims.all { it in 1..4096 }) { "Invalid image dimensions" }
            // Cataloging is header-only: large scans need no voxel heap allocation.
            // Disk space is checked when a volume is opened in the viewer.
            require((5..7).all { buffer.getShort(40 + it * 2).toInt() <= 1 }) {
                "Unsupported multi-channel NIfTI"
            }
            val frames = if (dimensionality >= 4) buffer.getShort(48).toInt() else 1
            require(frames in 1..100000) { "Invalid temporal dimension" }
            val dt = buffer.getShort(70).toInt()
            val spec = dataTypes[dt] ?: throw IllegalArgumentException(
                "Unsupported NIfTI datatype: $dt",
            )
            require(buffer.getShort(72).toInt() == spec.second) {
                "Invalid datatype/bitpix combination"
            }
            val voxOffset = buffer.getFloat(108)
            require(voxOffset.isFinite() && voxOffset >= 352f && voxOffset <= 64f * 1024 * 1024) {
                "Invalid NIfTI voxel offset"
            }
            val spacing = (1..3).map { dimension ->
                buffer.getFloat(76 + dimension * 4).toDouble()
            }
            val orientation = when {
                buffer.getShort(254).toInt() > 0 -> "sform"
                buffer.getShort(252).toInt() > 0 -> "qform"
                else -> "unknown"
            }
            // NIfTI's descriptive text is free-form; store locally, never send to logs/server.
            val description = bytes.copyOfRange(148, 228)
                .takeWhile { it != 0.toByte() }
                .toByteArray()
                .toString(Charsets.US_ASCII)
                .trim()
                .take(80)
            return NiftiSeriesMetadata(
                dimensions = dims,
                spacingMm = spacing,
                timepoints = frames,
                dataType = spec.first,
                orientation = orientation,
                description = description,
            )
        }
    }
}
