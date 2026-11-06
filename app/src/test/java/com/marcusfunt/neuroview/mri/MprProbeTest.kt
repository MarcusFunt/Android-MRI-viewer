package com.marcusfunt.neuroview.mri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class MprProbeTest {
    private fun volume(): NiftiVolume {
        val data = FloatArray(11 * 11 * 11) { index ->
            val x = index % 11
            val y = index / 11 % 11
            val z = index / 121
            (x + 100 * y + 10_000 * z).toFloat()
        }
        return NiftiVolume(
            11, 11, 11, data, SpatialAffine.axisAligned(1.0, 1.0, 1.0),
            0f, 101010f, "sform", 1,
        )
    }

    @Test fun centerPointIsConsistentAcrossThreePlanes() {
        val v = volume()
        val samples = MriPlane.entries.map { plane ->
            val slice = MprRenderer.sliceCount(v, plane) / 2
            requireNotNull(MprProbe.sample(v, plane, slice, 0.5, 0.5))
        }
        samples.forEach { probe ->
            assertEquals(5.0, probe.worldRasMm[0], 1e-5)
            assertEquals(5.0, probe.worldRasMm[1], 1e-5)
            assertEquals(5.0, probe.worldRasMm[2], 1e-5)
            assertEquals(50505f, probe.intensity, 1e-2f)
        }
    }

    @Test fun projectedPointReturnsCorrespondingSlices() {
        val v = volume()
        val world = doubleArrayOf(2.0, 7.0, 4.0)
        for (plane in MriPlane.entries) {
            val slice = MprProbe.sliceForWorld(v, plane, world)
            assertEquals(world[plane.firstAxis], MprRenderer.slicePosition(v, plane, slice), 1.0)
            val (x, y) = MprProbe.fractionsForWorld(v, plane, world)
            val sample = requireNotNull(MprProbe.sample(v, plane, slice, x, y))
            assertTrue(sample.worldRasMm.all { it.isFinite() })
        }
    }

    @Test fun flippedAffineStillShowsPatientRightOnScreenLeft() {
        val data = FloatArray(11 * 11 * 11) { index -> (index % 11).toFloat() }
        val affine = SpatialAffine.fromRows(
            doubleArrayOf(
                -1.0, 0.0, 0.0, 10.0,
                 0.0, 1.0, 0.0, 0.0,
                 0.0, 0.0, 1.0, 0.0,
            ),
        )
        val v = NiftiVolume(11, 11, 11, data, affine, 0f, 10f, "sform", 1)
        val leftSideOfScreen = requireNotNull(MprProbe.sample(v, MriPlane.AXIAL, 5, 0.0, 0.5))
        val rightSideOfScreen = requireNotNull(MprProbe.sample(v, MriPlane.AXIAL, 5, 1.0, 0.5))
        assertEquals(0f, leftSideOfScreen.intensity, 0.01f)
        assertEquals(10f, rightSideOfScreen.intensity, 0.01f)
        assertTrue(leftSideOfScreen.worldRasMm[0] > rightSideOfScreen.worldRasMm[0])
    }

    @Test fun tappingOutsideImageIsNotSampled() {
        val v = volume()
        assertNull(MprProbe.sample(v, MriPlane.AXIAL, 5, -0.1, 0.5))
        assertNull(MprProbe.sample(v, MriPlane.AXIAL, 5, 0.5, 1.1))
    }

    @Test fun anisotropicRulerDistanceUsesCalibratedMillimeters() {
        val v = NiftiVolume(
            11, 11, 11, FloatArray(11 * 11 * 11),
            SpatialAffine.axisAligned(0.5, 0.5, 3.0), 0f, 0f, "sform", 1,
        )
        val first = requireNotNull(MprProbe.sample(v, MriPlane.AXIAL, 4, 0.1, 0.5))
        val second = requireNotNull(MprProbe.sample(v, MriPlane.AXIAL, 4, 0.9, 0.5))

        assertEquals(4.0, MprProbe.distanceMm(first.worldRasMm, second.worldRasMm), 0.001)
        assertEquals(first.worldRasMm[2], second.worldRasMm[2], 0.001)
    }

    @Test fun nativeObliqueAndReformattedSamplesStayOnTheirOwnPhysicalPlanes() {
        val affine = SpatialAffine.fromRows(
            doubleArrayOf(
                0.5, 0.1, 0.4, 10.0,
                0.0, 0.6, 0.3, -4.0,
                0.0, 0.0, 2.0, 7.0,
            ),
        )
        val v = NiftiVolume(
            11, 11, 11, FloatArray(11 * 11 * 11), affine, 0f, 0f, "sform", 1,
        )
        val native = ViewPlaneDescriptor.nativeGrid(v, stackAxis = 2)
        val nativeLeft = requireNotNull(MprProbe.sample(v, native, 4, 0.45, 0.5))
        val nativeRight = requireNotNull(MprProbe.sample(v, native, 4, 0.55, 0.5))
        val axialLeft = requireNotNull(MprProbe.sample(v, ViewPlaneDescriptor.worldAligned(MriPlane.AXIAL), 4, 0.45, 0.5))
        val axialRight = requireNotNull(MprProbe.sample(v, ViewPlaneDescriptor.worldAligned(MriPlane.AXIAL), 4, 0.55, 0.5))

        assertEquals(4.0, nativeLeft.voxelIJK[2], 1e-6)
        assertEquals(4.0, nativeRight.voxelIJK[2], 1e-6)
        assertEquals(axialLeft.worldRasMm[2], axialRight.worldRasMm[2], 1e-3)
        assertTrue(MprProbe.distanceMm(nativeLeft.worldRasMm, nativeRight.worldRasMm) > 0.0)
        assertTrue(MprProbe.distanceMm(axialLeft.worldRasMm, axialRight.worldRasMm) > 0.0)
    }

    @Test fun optionalEndpointSnapUsesAStableOutputGridWithinTheActivePlane() {
        val v = volume()
        val geometry = PlaneGeometry.create(v, ViewPlaneDescriptor.worldAligned(MriPlane.AXIAL), 768)
        val snapped = geometry.snapFractionsToOutputGrid(0.333, 0.712)

        assertEquals(
            (snapped.horizontal * (geometry.width - 1)).roundToInt().toDouble(),
            snapped.horizontal * (geometry.width - 1),
            1e-8,
        )
        assertEquals(
            (snapped.vertical * (geometry.height - 1)).roundToInt().toDouble(),
            snapped.vertical * (geometry.height - 1),
            1e-8,
        )
        assertTrue(snapped.horizontal in 0.0..1.0 && snapped.vertical in 0.0..1.0)
    }
}
