package com.marcusfunt.neuroview.mri

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NiftiSeriesInspectorTest {
    private fun niftiHeader(
        order: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        dimensions: Triple<Int, Int, Int> = Triple(11, 9, 7),
        orientation: Int = 1,
        datatype: Int = 16,
        bitpix: Int = 32,
    ): ByteArray {
        val header = ByteBuffer.allocate(352).order(order)
        header.putInt(0, 348)
        header.putShort(40, 3)
        header.putShort(42, dimensions.first.toShort())
        header.putShort(44, dimensions.second.toShort())
        header.putShort(46, dimensions.third.toShort())
        header.putShort(48, 1)
        header.putShort(70, datatype.toShort())
        header.putShort(72, bitpix.toShort())
        header.putFloat(80, 0.8f)
        header.putFloat(84, 0.8f)
        header.putFloat(88, 1.3f)
        header.putFloat(108, 352f)
        header.putShort(254, orientation.toShort())
        "Synthetic only".toByteArray().forEachIndexed { i, b ->
            header.put(148 + i, b)
        }
        header.put(344, 110)
        header.put(345, 43)
        header.put(346, 49)
        header.put(347, 0)
        return header.array()
    }

    @Test
    fun indexesHeaderWithoutRequiringPixelData() {
        val data = NiftiSeriesInspector.inspect(ByteArrayInputStream(niftiHeader()))
        assertEquals(listOf(11, 9, 7), data.dimensions)
        assertEquals(listOf(0.8, 0.8, 1.3), data.spacingMm, 0.00001)
        assertEquals("float32", data.dataType)
        assertEquals("sform", data.orientation)
        assertEquals("Synthetic only", data.description)
    }

    @Test
    fun supportsGzipAndBigEndianHeaders() {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use {
            it.write(niftiHeader(order = ByteOrder.BIG_ENDIAN))
        }
        val data = NiftiSeriesInspector.inspect(
            ByteArrayInputStream(output.toByteArray()),
        )
        assertEquals(listOf(11, 9, 7), data.dimensions)
        assertEquals(1, data.timepoints)
    }

    @Test
    fun doesNotMislabelMissingOrientation() {
        val data = NiftiSeriesInspector.inspect(
            ByteArrayInputStream(niftiHeader(orientation = 0)),
        )
        assertEquals("unknown", data.orientation)
    }

    @Test
    fun rejectsNonNiftiHeadersAndInvalidDatatypes() {
        assertThrows(IllegalArgumentException::class.java) {
            NiftiSeriesInspector.inspect(ByteArrayInputStream(ByteArray(352)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NiftiSeriesInspector.inspect(ByteArrayInputStream(niftiHeader(datatype = 32)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NiftiSeriesInspector.inspect(
                ByteArrayInputStream(niftiHeader(datatype = 16, bitpix = 16)),
            )
        }
    }

    @Test
    fun refusesOversizedVolumesBeforeReadingPixels() {
        val header = niftiHeader(dimensions = Triple(4096, 4096, 4096))
        assertThrows(IllegalArgumentException::class.java) {
            NiftiSeriesInspector.inspect(ByteArrayInputStream(header))
        }
    }

    @Test
    fun doesNotRequireFullVoxelPayload() {
        val header = niftiHeader()
        val data = NiftiSeriesInspector.inspect(ByteArrayInputStream(header.copyOf(348)))
        assertTrue(data.dimensions[0] > 0)
    }
}
