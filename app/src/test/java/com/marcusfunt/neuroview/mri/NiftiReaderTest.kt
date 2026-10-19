package com.marcusfunt.neuroview.mri

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NiftiReaderTest {
    @Test fun sourceProgressCountsActualBytesBeforeDecoderProgress() {
        val source = synthetic()
        val counts = mutableListOf<Long>()
        val input = object : ByteArrayInputStream(source) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                super.read(bytes, offset, minOf(length, 7))
        }
        NiftiReader.read(input, NiftiReadOptions(sourceProgressSink = counts::add)).close()
        assertTrue(counts.size > 10)
        assertEquals(source.size.toLong(), counts.last())
        assertTrue(counts.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun rejectsTruncatedGzipTrailerEvenWhenVoxelsAreComplete() {
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(synthetic()) }
        }.toByteArray()
        assertThrows(java.io.EOFException::class.java) {
            NiftiReader.read(ByteArrayInputStream(compressed.copyOf(compressed.size - 4)))
        }
    }

    @Test fun cancellationAtDiskPreparationRemovesNewTemporaryFile() {
        val directory = Files.createTempDirectory("nifti-before-disk-write").toFile()
        try {
            assertThrows(CancellationException::class.java) {
                NiftiReader.read(ByteArrayInputStream(synthetic()), NiftiReadOptions(
                    maxVoxels = 2,
                    backingDirectory = directory,
                    progressSink = {
                        if (it.stage == NiftiReadStage.PREPARING_DISK_BACKING) {
                            throw CancellationException("Injected before opening output")
                        }
                    },
                ))
            }
            assertEquals(0, directory.listFiles()!!.size)
        } finally {
            directory.listFiles()?.forEach(File::delete)
            directory.delete()
        }
    }

    private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

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

    @Test fun reportsExactMonotonicCountsForLittleEndianInMemoryRead() {
        val events = mutableListOf<NiftiReadProgress>()

        NiftiReader.read(
            ByteArrayInputStream(synthetic()),
            NiftiReadOptions(progressSink = events::add),
        )

        val decode = events.filter { it.stage == NiftiReadStage.DECODING_VOLUME }
        assertEquals(listOf(0L, 12L), decode.map { it.completedUnits })
        assertEquals(listOf(12L, 12L), decode.map { it.totalUnits })
        val sampling = events.filter { it.stage == NiftiReadStage.SAMPLING_INTENSITY }
        assertEquals(12L, sampling.last().completedUnits)
        assertEquals(12L, sampling.last().totalUnits)
    }

    @Test fun publishesMonotonicChunkCountsForADeepInMemoryVolume() {
        val voxelCount = 100_000L
        val events = mutableListOf<NiftiReadProgress>()

        NiftiReader.read(
            ByteArrayInputStream(synthetic(nx = 500, ny = 200, nz = 1, datatype = 16)),
            NiftiReadOptions(progressSink = events::add),
        )

        val decode = events.filter { it.stage == NiftiReadStage.DECODING_VOLUME }
        assertTrue(decode.size > 2)
        assertEquals(0L, decode.first().completedUnits)
        assertEquals(voxelCount, decode.last().completedUnits)
        assertEquals(NiftiReadUnit.VOXELS, decode.last().unit)
        assertTrue(decode.zipWithNext().all { (before, after) ->
            requireNotNull(after.completedUnits) >= requireNotNull(before.completedUnits)
        })
        assertTrue(decode.all { it.totalUnits == voxelCount })
    }

    @Test fun reportsDecodedVoxelsForBigEndianGzipWithoutUsingCompressedLength() {
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use {
                it.write(synthetic(nx = 500, ny = 200, nz = 1, datatype = 4, order = ByteOrder.BIG_ENDIAN))
            }
        }.toByteArray()
        val events = mutableListOf<NiftiReadProgress>()

        NiftiReader.read(
            ByteArrayInputStream(compressed),
            NiftiReadOptions(progressSink = events::add),
        )

        val decode = events.filter { it.stage == NiftiReadStage.DECODING_VOLUME }
        assertEquals(100_000L, decode.last().completedUnits)
        assertEquals(100_000L, decode.last().totalUnits)
        assertEquals(NiftiReadUnit.VOXELS, decode.last().unit)
        assertTrue(decode.zipWithNext().all { (before, after) ->
            requireNotNull(after.completedUnits) >= requireNotNull(before.completedUnits)
        })
    }

    @Test fun reportsWrittenBytesAndSampleCountsForMappedRead() {
        val dir = Files.createTempDirectory("nifti-progress-disk-test").toFile()
        val events = mutableListOf<NiftiReadProgress>()
        try {
            val volume = NiftiReader.read(
                ByteArrayInputStream(synthetic(
                    nx = 250, ny = 200, nz = 1, datatype = 4,
                    order = ByteOrder.BIG_ENDIAN,
                )),
                NiftiReadOptions(maxVoxels = 2, backingDirectory = dir, progressSink = events::add),
            )

            val backing = events.filter { it.stage == NiftiReadStage.PREPARING_DISK_BACKING }
            assertTrue(backing.size > 2)
            assertEquals(100_000L, backing.last().completedUnits)
            assertEquals(100_000L, backing.last().totalUnits)
            assertEquals(NiftiReadUnit.BYTES, backing.last().unit)
            assertTrue(backing.zipWithNext().all { (before, after) ->
                requireNotNull(after.completedUnits) >= requireNotNull(before.completedUnits)
            })
            val sampling = events.filter { it.stage == NiftiReadStage.SAMPLING_INTENSITY }
            assertEquals(48_000L, sampling.last().completedUnits)
            assertEquals(48_000L, sampling.last().totalUnits)
            assertTrue(sampling.zipWithNext().all { (before, after) ->
                requireNotNull(after.completedUnits) >= requireNotNull(before.completedUnits)
            })
            volume.close()
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test fun cancellationDuringDecodeClosesTheInputStream() {
        val input = CloseTrackingInputStream(synthetic(nx = 500, ny = 200, nz = 1))
        var cancelRequested = false
        val options = NiftiReadOptions(
            progressSink = { event ->
                if (event.stage == NiftiReadStage.DECODING_VOLUME &&
                    (event.completedUnits ?: 0L) > 0L) cancelRequested = true
            },
            cancellationCheck = {
                if (cancelRequested) throw CancellationException("Test cancellation")
            },
        )

        assertThrows(CancellationException::class.java) { NiftiReader.read(input, options) }
        assertTrue(input.closed)
    }

    @Test fun cancellationDuringDiskBackingDeletesPartialFileAndClosesInput() {
        val input = CloseTrackingInputStream(synthetic(nx = 500, ny = 200, nz = 1))
        val dir = Files.createTempDirectory("nifti-cancel-disk-test").toFile()
        var cancelRequested = false
        val options = NiftiReadOptions(
            maxVoxels = 2,
            backingDirectory = dir,
            progressSink = { event ->
                if (event.stage == NiftiReadStage.PREPARING_DISK_BACKING &&
                    (event.completedUnits ?: 0L) > 0L) cancelRequested = true
            },
            cancellationCheck = {
                if (cancelRequested) throw CancellationException("Test cancellation")
            },
        )

        try {
            assertThrows(CancellationException::class.java) { NiftiReader.read(input, options) }
            assertTrue(input.closed)
            assertEquals(0, dir.listFiles()?.size ?: -1)
        } finally {
            dir.listFiles()?.forEach(File::delete)
            dir.delete()
        }
    }
}
