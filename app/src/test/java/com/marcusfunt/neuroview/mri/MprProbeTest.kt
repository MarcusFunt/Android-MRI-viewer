package com.marcusfunt.neuroview.mri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test fun tappingOutsideImageIsNotSampled() {
        val v = volume()
        assertNull(MprProbe.sample(v, MriPlane.AXIAL, 5, -0.1, 0.5))
        assertNull(MprProbe.sample(v, MriPlane.AXIAL, 5, 0.5, 1.1))
    }
}
