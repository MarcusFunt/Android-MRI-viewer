package com.marcusfunt.neuroview.mri

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NiftiReaderTest {
    private fun synthetic(
        nx: Int = 3,
        ny: Int = 2,
        nz: Int = 2,
        datatype: Int = 2,
        order: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        slope: Float = 0f,
        intercept: Float = 0f,
        sform: Boolean = true,
        qform: Boolean = false,
        qfac: Float = 1f,
    ): ByteArray {
        val voxelCount = nx * ny * nz
        val header = ByteBuffer.allocate(352).order(order)
        header.putInt(0, 348)
        header.putShort(40, 3)
        header.putShort(42, nx.toShort())
        header.putShort(44, ny.toShort())
        header.putShort(46, nz.toShort())
        header.putShort(48, 1)
        header.putShort(70, datatype.toShort())
        val bytes = when (datatype) {
            2 -> 1
            4, 512 -> 2
            16 -> 4
            else -> error("Bad test datatype")
        }
        header.putShort(72, (8 * bytes).toShort())
        for (i in 1..3) header.putFloat(76 + 4 * i, 1f)
        header.putFloat(76, qfac)
        header.putFloat(108, 352f)
        header.putFloat(112, slope)
        header.putFloat(116, intercept)
        header.putShort(252, if (qform) 1 else 0)
        header.putShort(254, if (sform) 1 else 0)
        if (sform) {
            header.putFloat(280, 1f)
            header.putFloat(300, 1f)
            header.putFloat(320, 1f)
        }
        header.put(344, 'n'.code.toByte())
        header.put(345, '+'.code.toByte())
        header.put(346, '1'.code.toByte())
        header.put(347, 0)

        val output = ByteBuffer.allocate(352 + bytes * voxelCount).order(order)
        output.put(header.array())
        for (index in 0 until voxelCount) {
            when (datatype) {
                2 -> output.put(index.toByte())
                4, 512 -> output.putShort(index.toShort())
                16 -> output.putFloat(index.toFloat())
            }
        }
        return output.array()
    }

    @Test fun loadsUncompressedAndIndicesFollowNiftiXFastest() {
        val volume = NiftiReader.read(ByteArrayInputStream(synthetic()))
        assertEquals(3, volume.nx)
        assertEquals(2, volume.ny)
        assertEquals(2, volume.nz)
        assertEquals(2f, volume.nearest(2, 0, 0))
        assertEquals(3f, volume.nearest(0, 1, 0))
        assertEquals(6f, volume.nearest(0, 0, 1))
        assertEquals("sform (RAS+)", volume.transformSource)
    }

    @Test fun handlesGzipAndBigEndianSignedSamplesWithScaling() {
        val bytes = synthetic(
            datatype = 4, order = ByteOrder.BIG_ENDIAN, slope = 2f, intercept = 10f,
        )
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { it.write(bytes) }
        val volume = NiftiReader.read(ByteArrayInputStream(compressed.toByteArray()))
        assertEquals(10f, volume.nearest(0, 0, 0))
        assertEquals(32f, volume.nearest(2, 1, 1))
    }

    @Test fun ignoresInterceptWhenSlopeIsZeroPerNiftiSpec() {
        val v = NiftiReader.read(
            ByteArrayInputStream(synthetic(slope = 0f, intercept = 200f)),
        )
        assertEquals(1f, v.nearest(1, 0, 0))
    }

    @Test fun qformHonorsNegativeQfac() {
        val v = NiftiReader.read(
            ByteArrayInputStream(synthetic(sform = false, qform = true, qfac = -1f)),
        )
        assertEquals("qform (RAS+)", v.transformSource)
        assertEquals(-1.0, v.affine.world(0.0, 0.0, 1.0)[2], 0.0001)
    }

    @Test fun rejectsUnsupportedAndOversizedDatasets() {
        val incorrectMagic = synthetic().apply { this[344] = 0 }
        assertThrows(IllegalArgumentException::class.java) {
            NiftiReader.read(ByteArrayInputStream(incorrectMagic))
        }
        val tooLarge = synthetic().apply {
            ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(42, 4000).putShort(44, 4000).putShort(46, 4000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NiftiReader.read(ByteArrayInputStream(tooLarge))
        }
        val truncated = synthetic().copyOf(355)
        assertThrows(java.io.EOFException::class.java) {
            NiftiReader.read(ByteArrayInputStream(truncated))
        }
    }

    @Test fun missingOrientationIsFlaggedRatherThanAssertedAnatomical() {
        val volume = NiftiReader.read(
            ByteArrayInputStream(synthetic(sform = false, qform = false)),
        )
        assertTrue(volume.transformSource.startsWith("NO ORIENTATION"))
    }

    @Test fun diskBackedMatchesInMemoryForBigEndianGzipAndCleansCache() {
        val source = synthetic(
            nx = 7, ny = 4, nz = 3, datatype = 4,
            order = ByteOrder.BIG_ENDIAN, slope = 2.5f, intercept = -7f,
        )
        val compressed = ByteArrayOutputStream().also { dst ->
            GZIPOutputStream(dst).use { it.write(source) }
        }.toByteArray()
        val expected = NiftiReader.read(ByteArrayInputStream(compressed))
        val dir = Files.createTempDirectory("nifti-disk-test").toFile()
        try {
            val large = NiftiReader.read(
                ByteArrayInputStream(compressed), maxVoxels = 2, backingDirectory = dir,
            )
            assertTrue(large.isDiskBacked)
            assertEquals(0, large.values.size)
            for (k in 0 until 3) for (j in 0 until 4) for (i in 0 until 7) {
                assertEquals(expected.nearest(i, j, k), large.nearest(i, j, k), 0.0001f)
            }
            assertEquals(expected.percentileLow, large.percentileLow, 0.0001f)
            assertEquals(expected.percentileHigh, large.percentileHigh, 0.0001f)
            large.close()
            // Windows locks active mapped views until JVM GC; Android/Linux can unlink immediately.
            if (System.getProperty("os.name")?.startsWith("Windows") != true) {
                assertEquals(0, dir.listFiles()?.size ?: -1)
            }
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }
}
