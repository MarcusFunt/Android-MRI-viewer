package com.marcusfunt.neuroview.mri

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class MprRendererTest {
    private fun sampleVolume(affine: SpatialAffine = SpatialAffine.axisAligned(1.0, 1.0, 1.0)): NiftiVolume {
        val data = FloatArray(5 * 5 * 5)
        for (z in 0..4) for (y in 0..4) for (x in 0..4) {
            data[(z * 5 + y) * 5 + x] = (x * 20 + y * 3 + z).toFloat()
        }
        return NiftiVolume(5, 5, 5, data, affine, 0f, 100f, "test", 1)
    }

    @Test fun allPlanesHaveValidPhysicalSliceCounts() {
        val v = sampleVolume()
        for (plane in MriPlane.entries) {
            assertEquals(5, MprRenderer.sliceCount(v, plane))
            assertEquals(2.0, MprRenderer.slicePosition(v, plane, 2), 1e-6)
            val image = MprRenderer.render(v, plane, 2, maxSide = 16)
            assertEquals(5, image.width)
            assertEquals(5, image.height)
            assertEquals(25, image.pixels.size)
            assertTrue(image.pixels.all { (it ushr 24) == 255 })
        }
    }

    @Test fun anisotropicVolumesUseAPlaneSpecificWorldAxisSamplingStep() {
        val volume = NiftiVolume(
            17, 13, 9, FloatArray(17 * 13 * 9),
            SpatialAffine.axisAligned(0.7, 0.7, 2.5),
            0f, 1f, "sform (RAS+)", 1,
        )

        assertEquals(9, MprRenderer.sliceCount(volume, MriPlane.AXIAL))
        assertEquals(13, MprRenderer.sliceCount(volume, MriPlane.CORONAL))
        assertEquals(17, MprRenderer.sliceCount(volume, MriPlane.SAGITTAL))
        assertEquals(2.5, MprRenderer.sliceStepWorld(volume, MriPlane.AXIAL), 1e-9)
        assertEquals(0.7, MprRenderer.sliceStepWorld(volume, MriPlane.CORONAL), 1e-9)
        assertEquals(0.7, MprRenderer.sliceStepWorld(volume, MriPlane.SAGITTAL), 1e-9)
    }

    @Test fun largeWorldPlaneStacksReportTheIndependentQualityCap() {
        val volume = NiftiVolume(
            900, 2, 2, FloatArray(900 * 2 * 2),
            SpatialAffine.axisAligned(0.01, 1.0, 1.0),
            0f, 1f, "sform (RAS+)", 1,
        )
        val info = MprRenderer.sliceSamplingInfo(volume, MriPlane.SAGITTAL)
        assertEquals(701, info.renderedCount)
        assertEquals(900, info.estimatedCount)
        assertTrue(info.limitedByQualityCap)
        assertEquals(8.99 / 700, info.renderedStepMm, 1e-9)
    }

    @Test fun axialAndCoronalShowPatientRightOnScreenLeftForRasPositiveXDirection() {
        val v = sampleVolume()
        for (plane in listOf(MriPlane.AXIAL, MriPlane.CORONAL)) {
            val image = MprRenderer.render(v, plane, 2, maxSide = 16)
            val left = image.pixels[2 * image.width] and 255
            val right = image.pixels[2 * image.width + image.width - 1] and 255
            assertTrue("Screen-left patient-right should be higher RAS X", left > right)
        }
    }

    @Test fun sagittalHasAnteriorOnScreenLeftAndSuperiorAtTop() {
        val v = sampleVolume()
        val image = MprRenderer.render(v, MriPlane.SAGITTAL, 2, maxSide = 16)
        assertTrue((image.pixels[2 * image.width] and 255) > (image.pixels[2 * image.width + image.width - 1] and 255))
        assertTrue((image.pixels[0] and 255) > (image.pixels[(image.height - 1) * image.width] and 255))
    }

    @Test fun inverseAffineRoundTripsRotatedShearedAndTranslatedPositions() {
        val affine = SpatialAffine.fromRows(
            doubleArrayOf(
                0.0, -0.5, 0.2, 16.0,
                1.5, 0.0, 0.0, -23.0,
                0.0, 0.2, 2.0, 7.0,
            ),
        )
        val pos = affine.world(4.0, 3.0, 2.0)
        val reconstructed = affine.voxel(pos[0], pos[1], pos[2])
        assertTrue(abs(reconstructed[0] - 4.0) < 1e-8)
        assertTrue(abs(reconstructed[1] - 3.0) < 1e-8)
        assertTrue(abs(reconstructed[2] - 2.0) < 1e-8)
        val image = MprRenderer.render(sampleVolume(affine), MriPlane.AXIAL, 2, maxSide = 32)
        assertTrue(image.pixels.any { (it and 255) > 0 })
    }

    @Test fun voxelInterpolationIsTrilinearAndOutOfBoundsIsBlack() {
        val v = sampleVolume()
        val expected = (v.nearest(0, 0, 0) + v.nearest(1, 0, 0)) / 2f
        assertEquals(expected, v.sample(0.5, 0.0, 0.0))
        assertEquals(0f, v.sample(-2.0, 0.0, 0.0))
        assertEquals(0f, v.sample(8.0, 0.0, 0.0))
    }

    @Test fun previewRenderChecksCancellationAtRowBoundaries() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            MprRenderer.render(
                sampleVolume(), MriPlane.AXIAL, 2, maxSide = 32,
                cancellationCheck = {
                    checks++
                    if (checks == 3) throw CancellationException("Cancel thumbnail rendering")
                },
            )
        }
        assertEquals(3, checks)
    }
}
