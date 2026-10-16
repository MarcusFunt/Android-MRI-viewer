package com.marcusfunt.neuroview.qa

import com.marcusfunt.neuroview.mri.MprRenderer
import com.marcusfunt.neuroview.mri.MriPlane
import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.mri.SpatialAffine
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Regression checks for visual orientation, physical units and windowing behavior. */
class MprQualityTest {
    private fun signedVolume(affine: SpatialAffine): NiftiVolume {
        val nx = 21
        val ny = 17
        val nz = 13
        val values = FloatArray(nx * ny * nz)
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) {
            values[(k * ny + j) * nx + i] = -120f + i * 4 + j * 7 + k * 3
        }
        return NiftiVolume(nx, ny, nz, values, affine, -120f, 120f, "sform (RAS+)", 1)
    }

    @Test fun radiologicalLeftScreenHasPatientRightForAxialCoronal() {
        val v = signedVolume(SpatialAffine.axisAligned(1.0, 1.2, 2.0))
        for (plane in listOf(MriPlane.AXIAL, MriPlane.CORONAL)) {
            val i = MprRenderer.render(v, plane, MprRenderer.sliceCount(v, plane) / 2,
                maxSide = 120)
            val middle = i.height / 2
            val left = i.pixels[middle * i.width + 1] and 255
            val right = i.pixels[middle * i.width + i.width - 2] and 255
            assertTrue("Positive RAS-X patient-right must appear on screen-left", left > right)
        }
    }

    @Test fun obliqueOutsideVolumeIsBlackEvenWhenWindowIncludesZero() {
        val affine = SpatialAffine.fromRows(doubleArrayOf(
            0.0, -1.0, 0.0, 5.0,
            1.0, 0.0, 0.0, 6.0,
            0.0, 0.0, 2.0, 7.0
        ))
        val v = signedVolume(affine)
        val rendered = MprRenderer.render(v, MriPlane.AXIAL, 5, 0.6f, 1.6f, 96)
        assertEquals(0xff000000.toInt(), rendered.pixels[0])
        assertTrue(rendered.pixels.any { it != 0xff000000.toInt() })
    }

    @Test fun mirroredCoordinateBasisIsInvertibleAndSliceImageIsFinite() {
        val mirrored = SpatialAffine.fromRows(doubleArrayOf(
            -0.95, 0.0, 0.0, 15.0,
            0.0, 1.4, 0.0, -2.0,
            0.0, 0.0, -2.0, 25.0
        ))
        val v = signedVolume(mirrored)
        for (plane in MriPlane.entries) {
            val image = MprRenderer.render(v, plane, MprRenderer.sliceCount(v, plane) / 2,
                maxSide = 88)
            assertTrue(image.pixels.any { it != 0xff000000.toInt() })
            assertTrue(image.width in 8..88 && image.height in 8..88)
        }
        val world = mirrored.world(3.0, 6.0, 9.0)
        val voxel = mirrored.voxel(world[0], world[1], world[2])
        for (axis in 0..2) assertEquals(listOf(3.0, 6.0, 9.0)[axis], voxel[axis], 1e-8)
    }

    @Test fun niftiSpatialUnitsAreAlwaysConvertedToMillimetres() {
        fun fixture(units: Int, scale: Float): ByteArray {
            val header = ByteBuffer.allocate(352 + 8).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(0, 348)
            header.putShort(40, 3)
            header.putShort(42, 2)
            header.putShort(44, 2)
            header.putShort(46, 2)
            header.putShort(70, 2)
            header.putShort(72, 8)
            header.putFloat(108, 352f)
            header.putShort(254, 1)
            header.put(123, units.toByte())
            header.putFloat(280, scale)
            header.putFloat(300, scale)
            header.putFloat(320, scale)
            "n+1".toByteArray().copyInto(header.array(), 344)
            for (a in 0..7) header.put(352 + a, (a + 1).toByte())
            return header.array()
        }
        val fromMetres = NiftiReader.read(ByteArrayInputStream(fixture(1, 0.001f)))
        val fromMillimetres = NiftiReader.read(ByteArrayInputStream(fixture(2, 1f)))
        val fromMicrons = NiftiReader.read(ByteArrayInputStream(fixture(3, 1000f)))
        for (v in listOf(fromMetres, fromMillimetres, fromMicrons)) {
            for (i in 0..2) assertEquals(1.0, v.affine.axisSpacing(i), 1e-6)
            assertEquals(8f, v.nearest(1, 1, 1), 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NiftiReader.read(ByteArrayInputStream(fixture(7, 1f)))
        }
    }

    @Test fun largeSyntheticVolumeReslicesWithoutHugeIntermediateOutputs() {
        val nx = 192; val ny = 192; val nz = 96
        val data = FloatArray(nx * ny * nz) {
            ((it * 1664525L + 1013904223L) % 4096).toFloat()
        }
        val volume = NiftiVolume(nx, ny, nz, data,
            SpatialAffine.axisAligned(0.8, 0.8, 1.6),
            0f, 4095f, "sform (RAS+)", 1)
        val measurements = mutableListOf<Long>()
        for (plane in MriPlane.entries) {
            repeat(3) {
                val start = System.nanoTime()
                val image = MprRenderer.render(volume, plane,
                    MprRenderer.sliceCount(volume, plane) / 2, maxSide = 384)
                val millis = (System.nanoTime() - start) / 1_000_000
                measurements.add(millis)
                assertTrue(image.width in 8..384 && image.height in 8..384)
                assertTrue(image.pixels.size <= 384 * 384)
                assertTrue(image.pixels.any { it != 0 })
            }
        }
        println("QA desktop JVM MPR timings (ms), NOT device benchmarks: $measurements")
        assertTrue("Severe JVM slice-render regression", measurements.sum() < 60_000L)
    }
}
