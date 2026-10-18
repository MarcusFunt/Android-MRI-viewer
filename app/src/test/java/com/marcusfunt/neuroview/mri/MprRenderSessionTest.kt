package com.marcusfunt.neuroview.mri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import java.util.concurrent.CancellationException

class MprRenderSessionTest {
    private fun volume(): NiftiVolume {
        val nx = 30
        val ny = 26
        val nz = 22
        val data = FloatArray(nx * ny * nz) { position ->
            val i = position % nx
            val j = (position / nx) % ny
            val k = position / (nx * ny)
            (i * 3 + j * 1.5 + k * 2.2).toFloat()
        }
        val affine = SpatialAffine.fromRows(
            doubleArrayOf(
                0.82, -0.09, 0.05, -16.0,
                0.15, 0.94, -0.07, 12.0,
                0.04, 0.12, 1.28, -7.0,
            ),
        )
        return NiftiVolume(nx, ny, nz, data, affine, 1f, 135f, "sform (RAS+)", 1)
    }

    @Test fun matchesOriginalMprForAllPlanesAndWindowSettings() {
        val volume = volume()
        for (plane in MriPlane.entries) {
            val renderer = MprRenderSession(volume, plane, 120)
            val count = MprRenderer.sliceCount(volume, plane)
            for (slice in listOf(0, count / 3, count / 2, count - 1)) {
                for ((center, width) in listOf(0.5f to 1f, 0.17f to 0.55f, 0.9f to 1.7f)) {
                    val expected = MprRenderer.render(volume, plane, slice, center, width, 120)
                    val actual = renderer.render(slice, center, width)
                    assertEquals(expected.width, actual.width)
                    assertEquals(expected.height, actual.height)
                    for (i in expected.pixels.indices) {
                        val first = expected.pixels[i] and 0xff
                        val second = actual.pixels[i] and 0xff
                        assertTrue(
                            "${plane.label} slice $slice pixel $i: expected=$first actual=$second",
                            abs(first - second) <= 1,
                        )
                    }
                }
            }
        }
    }

    @Test fun cachesVoxelSamplingAcrossWindowChangesAndRecentSlices() {
        val volume = volume()
        val render = MprRenderSession(volume, MriPlane.AXIAL, 160, 3)
        val original = render.sample(4)
        assertSame(original, render.sample(4))
        render.render(4, 0.12f, 0.25f)
        assertSame(original, render.sample(4))
        render.sample(5)
        render.sample(6)
        assertSame(original, render.sample(4))
        render.sample(7)
        assertSame(original, render.sample(4))
        render.sample(8)
        render.sample(9)
        render.sample(10)
        assertTrue(render.sample(4) !== original)
    }

    @Test fun cancellationHappensAtRowBoundaries() {
        val render = MprRenderSession(volume(), MriPlane.CORONAL, 80)
        var rows = 0
        try {
            render.sample(3) {
                rows++
                if (rows == 4) throw IllegalStateException("Cancelled")
            }
            throw AssertionError("Expected cancellation")
        } catch (expected: IllegalStateException) {
            assertEquals("Cancelled", expected.message)
            assertEquals(4, rows)
        }
        // Cancelled or half-written frames never enter the cache.
        assertEquals(render.width * render.height, render.sample(3).values.size)
    }

    @Test fun sessionGridConstructionCanBeCanceledBetweenRows() {
        var checkedRows = 0

        assertThrows(CancellationException::class.java) {
            MprRenderSession(
                volume(), MriPlane.AXIAL, 128,
                cancellationCheck = {
                    checkedRows++
                    if (checkedRows == 3) throw CancellationException("Cancel stale grid construction")
                },
            )
        }

        assertEquals(3, checkedRows)
    }
}
