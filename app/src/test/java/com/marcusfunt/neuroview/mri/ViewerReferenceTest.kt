package com.marcusfunt.neuroview.mri

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Goldens are produced by scripts/qa/viewer_reference.py, not the Android renderer. */
class ViewerReferenceTest {
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/viewer-reference/$name"))
    private fun fixture(): NiftiVolume {
        val loaded = resource("asymmetric-oblique-4d.nii").use { NiftiReader.read(it) }
        return NiftiVolume(loaded.nx, loaded.ny, loaded.nz, loaded.values, loaded.affine,
            -50f, 72.5f, loaded.transformSource, loaded.timepoints)
    }

    @Test fun threePlanesMatchIndependentObliqueFlippedAnisotropicReference() {
        val volume = fixture()
        resource("planes.csv").bufferedReader().readLines().forEach { line ->
            val fields = line.split(',')
            val plane = MriPlane.valueOf(fields[0])
            val slice = fields[1].toInt()
            assertEquals(fields[2].toInt(), MprRenderer.sliceCount(volume, plane))
            assertEquals(fields[3].toDouble(), MprRenderer.slicePosition(volume, plane, slice), 0.00001)
            val golden = resource("${plane.name.lowercase()}.pgm").bufferedReader().readText().trim().split(Regex("\\s+"))
            val width = golden[1].toInt()
            val height = golden[2].toInt()
            val expected = golden.drop(4).map(String::toInt)
            val direct = MprRenderer.render(volume, plane, slice, maxSide = 96)
            val cached = MprRenderSession(volume, plane, 96).render(slice, 0.5f, 1f)
            listOf(direct, cached).forEach { image ->
                assertEquals(width, image.width)
                assertEquals(height, image.height)
                image.pixels.forEachIndexed { index, pixel ->
                    assertEquals(255, pixel ushr 24)
                    assertEquals(pixel and 255, pixel shr 8 and 255)
                    assertEquals(pixel and 255, pixel shr 16 and 255)
                    assertTrue("$plane pixel $index expected ${expected[index]} got ${pixel and 255}",
                        abs((pixel and 255) - expected[index]) <= 1)
                }
            }
        }
    }

    @Test fun independentWorldLandmarkHasCorrectIjkAndProbeIntensity() {
        val volume = fixture()
        // Exact landmark (i,j,k)=(2,3,1), evaluated outside production affine code.
        val world = doubleArrayOf(7.7, -5.6, 22.3)
        val voxel = volume.affine.voxel(world[0], world[1], world[2])
        doubleArrayOf(2.0, 3.0, 1.0).forEachIndexed { axis, expected -> assertEquals(expected, voxel[axis], 0.00001) }
        assertEquals(-4.75f, volume.sample(voxel[0], voxel[1], voxel[2]), 0.0001f)
        MriPlane.entries.forEach { plane ->
            val slice = MprProbe.sliceForWorld(volume, plane, world)
            val position = MprRenderer.slicePosition(volume, plane, slice)
            val (x, y) = MprProbe.fractionsForWorld(volume, plane, world)
            val sample = requireNotNull(MprProbe.sample(volume, plane, slice, x, y))
            assertEquals(position, sample.worldRasMm[plane.firstAxis], 0.00001)
            // Verify intensity independently at the actual nearest reconstructed plane.
            val r = sample.worldRasMm[0]; val a = sample.worldRasMm[1]; val s = sample.worldRasMm[2]
            val i = (a + 8) / 1.2
            val j = (2 * (r - 12) - 0.2 * (s - 20)) / -3.02
            val k = (-0.1 * (r - 12) - 1.5 * (s - 20)) / -3.02
            assertEquals((17 * i + 3.5 * j + 0.75 * k - 50).toFloat(), sample.intensity, 0.0001f)
        }
    }

    @Test fun fourDimensionalFixtureUsesOnlyFirstFrame() {
        val volume = fixture()
        assertEquals(2, volume.timepoints)
        assertEquals(7 * 6 * 5, volume.values.size)
        assertEquals(-50f, volume.nearest(0, 0, 0), 0f)
        assertEquals(72.5f, volume.nearest(6, 5, 4), 0f)
    }
}
