package com.marcusfunt.neuroview.mri

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises an actual stream larger than the original 48-million-voxel cutoff. */
class LargeVolumeStreamingTest {
    private class ZeroStream(var remaining: Long) : InputStream() {
        override fun read(): Int = if (remaining-- > 0L) 0 else -1

        override fun read(dst: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0L) return -1
            if (length == 0) return 0
            val amount = minOf(remaining, length.toLong()).toInt()
            java.util.Arrays.fill(dst, offset, offset + amount, 0.toByte())
            remaining -= amount
            return amount
        }
    }

    @Test fun loadsMoreThanOldGlobalVoxelCapWithoutAllocatingVoxelArray() {
        val nx = 400
        val ny = 400
        val nz = 310
        val total = nx.toLong() * ny * nz // 49,600,000 > 48,000,000
        val header = ByteBuffer.allocate(352).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0, 348)
        header.putShort(40, 3)
        header.putShort(42, nx.toShort())
        header.putShort(44, ny.toShort())
        header.putShort(46, nz.toShort())
        header.putShort(70, 2) // uint8
        header.putShort(72, 8)
        header.putFloat(80, 1f)
        header.putFloat(84, 1f)
        header.putFloat(88, 1f)
        header.putFloat(108, 352f)
        header.putShort(254, 1)
        header.putFloat(280, 1f)
        header.putFloat(300, 1f)
        header.putFloat(320, 1f)
        header.put(344, 'n'.code.toByte())
        header.put(345, '+'.code.toByte())
        header.put(346, '1'.code.toByte())
        val dir = Files.createTempDirectory("nifti-large-test").toFile()
        try {
            val input = SequenceInputStream(
                ByteArrayInputStream(header.array()), ZeroStream(total),
            )
            val volume = NiftiReader.read(
                input,
                maxVoxels = NiftiReader.MAX_VOXELS,
                backingDirectory = dir,
            )
            assertTrue(volume.isDiskBacked)
            assertTrue(volume.values.isEmpty())
            assertEquals(0f, volume.nearest(nx - 1, ny - 1, nz - 1))
            assertEquals(0f, volume.nearest(0, 0, 0))
            volume.close()
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }
}
