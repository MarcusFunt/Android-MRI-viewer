package com.marcusfunt.neuroview

import androidx.test.platform.app.InstrumentationRegistry
import com.marcusfunt.neuroview.conversion.DerivedDicomWriter
import com.marcusfunt.neuroview.conversion.DicomReader
import com.marcusfunt.neuroview.conversion.DicomSlice
import com.marcusfunt.neuroview.mri.NiftiReadOptions
import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.mri.NiftiVolume
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappedDicomExportTest {
    private fun smallFloatNifti(): ByteArray {
        val nx = 4
        val ny = 3
        val nz = 3
        val data = ByteBuffer.allocate(352 + nx * ny * nz * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.putInt(0, 348)
        data.putShort(40, 3)
        data.putShort(42, nx.toShort())
        data.putShort(44, ny.toShort())
        data.putShort(46, nz.toShort())
        data.putShort(70, 16) // float32
        data.putShort(72, 32)
        data.putFloat(108, 352f)
        data.putShort(254, 1) // sform
        data.putFloat(280, 1f)
        data.putFloat(300, 1f)
        data.putFloat(320, 1f)
        data.put(344, 'n'.code.toByte())
        data.put(345, '+'.code.toByte())
        data.put(346, '1'.code.toByte())
        for (index in 0 until nx * ny * nz) data.putFloat(352 + index * 4, index - 10f)
        return data.array()
    }

    private fun exportSlices(volume: NiftiVolume): List<DicomSlice> {
        val zipBytes = ByteArrayOutputStream()
        DerivedDicomWriter.writeZip(volume, zipBytes)
        val slices = mutableListOf<DicomSlice>()
        ZipInputStream(ByteArrayInputStream(zipBytes.toByteArray())).use { zip ->
            while (zip.nextEntry != null) {
                val dicomBytes = ByteArrayOutputStream()
                zip.copyTo(dicomBytes)
                slices += DicomReader.read(ByteArrayInputStream(dicomBytes.toByteArray()))
                zip.closeEntry()
            }
        }
        return slices
    }

    @Test
    fun diskBackedNiftiExportsSameDicomPixelsAndGeometryAndReleasesBacking() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backingDirectory = File(context.cacheDir, "nifti_export_test_${UUID.randomUUID()}")
        assertTrue(backingDirectory.mkdir())
        val nifti = smallFloatNifti()
        val inMemory = NiftiReader.read(ByteArrayInputStream(nifti))
        val mapped = NiftiReader.read(
            ByteArrayInputStream(nifti),
            NiftiReadOptions(maxVoxels = 1, backingDirectory = backingDirectory),
        )
        val expectedPixels = listOf(
            floatArrayOf(-10f, -9f, -8f, -7f, -6f, -5f, -4f, -3f, -2f, -1f, 0f, 1f),
            floatArrayOf(2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f),
            floatArrayOf(14f, 15f, 16f, 17f, 18f, 19f, 20f, 21f, 22f, 23f, 24f, 25f),
        )
        var backingFileReleased = false
        var backingDirectoryDeleted = false
        try {
            assertFalse(inMemory.isDiskBacked)
            assertTrue(mapped.isDiskBacked)
            assertTrue(mapped.values.isEmpty())
            assertTrue(backingDirectory.listFiles().orEmpty().isNotEmpty())

            val expected = exportSlices(inMemory)
            val actual = exportSlices(mapped)
            assertEquals(3, actual.size)
            assertEquals(expected.size, actual.size)
            expected.zip(actual).forEachIndexed { sliceIndex, (reference, exported) ->
                assertEquals(reference.rows, exported.rows)
                assertEquals(reference.columns, exported.columns)
                assertEquals(reference.positionLps.toList(), exported.positionLps.toList())
                assertEquals(reference.orientationLps.toList(), exported.orientationLps.toList())
                assertEquals(reference.pixelSpacing.toList(), exported.pixelSpacing.toList())
                assertEquals(reference.pixels.size, exported.pixels.size)
                reference.pixels.indices.forEach { index ->
                    assertEquals(reference.pixels[index], exported.pixels[index], 0.001f)
                    assertEquals(expectedPixels[sliceIndex][index], exported.pixels[index], 0.001f)
                }
                assertEquals(listOf(-0.0, -0.0, sliceIndex.toDouble()), exported.positionLps.toList())
                assertEquals(listOf(-1.0, -0.0, 0.0, -0.0, -1.0, 0.0), exported.orientationLps.toList())
                assertEquals(listOf(1.0, 1.0), exported.pixelSpacing.toList())
            }
        } finally {
            try {
                mapped.close()
            } finally {
                inMemory.close()
                backingFileReleased = backingDirectory.listFiles().orEmpty().isEmpty()
                backingDirectoryDeleted = backingDirectory.delete()
            }
        }

        assertTrue("mapped NIfTI backing file should be released", backingFileReleased)
        assertTrue("test backing directory should be removed", backingDirectoryDeleted)
    }
}
