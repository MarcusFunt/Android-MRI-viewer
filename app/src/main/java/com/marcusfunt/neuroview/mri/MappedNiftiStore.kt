package com.marcusfunt.neuroview.mri

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Read-only, uncompressed voxel payload backed by the app's cache, not the Java heap.
 * The NIfTI payload is split into aligned 64 MiB memory maps to support files > 2 GiB.
 * Access is absolute (thread-safe), and scaling is performed on demand.
 */
class MappedNiftiStore(
    private val file: File,
    private val voxelCount: Long,
    private val bytesPerVoxel: Int,
    private val datatype: Int,
    private val order: ByteOrder,
    private val slope: Double,
    private val intercept: Double,
) : Closeable {
    private val channel = RandomAccessFile(file, "r").channel
    private val windowBytes = 64L * 1024 * 1024
    private val windows: List<MappedByteBuffer>

    init {
        try {
            val expected = Math.multiplyExact(voxelCount, bytesPerVoxel.toLong())
            require(channel.size() == expected) { "Incomplete on-disk NIfTI payload" }
            val windowCount = (expected + windowBytes - 1) / windowBytes
            require(windowCount <= Int.MAX_VALUE) { "Too many mapped NIfTI windows" }
            windows = List(windowCount.toInt()) { index ->
                val start = index.toLong() * windowBytes
                channel.map(
                    FileChannel.MapMode.READ_ONLY, start, minOf(windowBytes, expected - start),
                ).also { it.order(order) }
            }
        } catch (error: Throwable) {
            channel.close()
            throw error
        }
    }

    fun value(index: Long): Float {
        require(index in 0 until voxelCount)
        val byteIndex = index * bytesPerVoxel
        val buffer = windows[(byteIndex / windowBytes).toInt()]
        val offset = (byteIndex % windowBytes).toInt()
        val raw = when (datatype) {
            2 -> (buffer.get(offset).toInt() and 255).toDouble()
            256 -> buffer.get(offset).toDouble()
            4 -> buffer.getShort(offset).toDouble()
            512 -> (buffer.getShort(offset).toInt() and 65535).toDouble()
            8 -> buffer.getInt(offset).toDouble()
            16 -> buffer.getFloat(offset).toDouble()
            768 -> (buffer.getInt(offset).toLong() and 0xffffffffL).toDouble()
            64 -> buffer.getDouble(offset)
            1024 -> buffer.getLong(offset).toDouble()
            else -> error("Unsupported datatype")
        }
        val scaled = raw * slope + intercept
        return if (scaled.isFinite()) scaled.toFloat() else 0f
    }

    override fun close() {
        channel.close()
        // Android/Linux permits unlinking an active mapping; the mapping stays alive
        // until the volume is discarded. A failed unlink is cleaned on next launch.
        file.delete()
    }
}
