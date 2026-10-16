package com.marcusfunt.neuroview.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Unit checks for generated render assets: no Android/GPU dependency. */
class BrainGeometryTest {
    @Test
    fun generatesFiveSeparateIndexedSurfaces() {
        val meshes = BrainGeometry.build()
        assertEquals(5, meshes.size)
        assertTrue(meshes.all { it.vertexCount > 200 })
        assertTrue(meshes.all { it.indices.isNotEmpty() })
    }

    @Test
    fun allIndicesFitUnsignedGles2IndexRange() {
        BrainGeometry.build().forEach { mesh ->
            assertTrue(mesh.vertexCount <= 65535)
            assertEquals(0, mesh.vertices.size % 6)
            assertEquals(0, mesh.indices.size % 3)
            mesh.indices.forEach { index ->
                val unsigned = index.toInt() and 0xffff
                assertTrue("Bad vertex index $unsigned / ${mesh.vertexCount}",
                    unsigned < mesh.vertexCount)
            }
        }
    }

    @Test
    fun verticesAndNormalsAreFiniteAndNormalsAreNormalized() {
        BrainGeometry.build().forEach { mesh ->
            val values = mesh.vertices
            var i = 0
            while (i < values.size) {
                for (offset in 0..5) {
                    assertTrue("Non-finite vertex component", values[i + offset].isFinite())
                }
                val nx = values[i + 3]
                val ny = values[i + 4]
                val nz = values[i + 5]
                val length = sqrt(nx * nx + ny * ny + nz * nz)
                assertTrue("Unit normal expected", abs(length - 1f) < 0.005f)
                i += 6
            }
        }
    }

    @Test
    fun brainRemainsWithinExpectedPreviewBounds() {
        val meshes = BrainGeometry.build()
        for (mesh in meshes) {
            for (index in mesh.vertices.indices step 6) {
                val x = mesh.vertices[index]
                val y = mesh.vertices[index + 1]
                val z = mesh.vertices[index + 2]
                assertTrue("X outside camera framing", abs(x) < 1.2f)
                assertTrue("Y outside camera framing", y in -1.4f..1.1f)
                assertTrue("Z outside camera framing", abs(z) < 1.3f)
            }
        }
    }

    @Test
    fun surfacesHaveEnoughTrianglesForSmoothAppearance() {
        val mesh = BrainGeometry.build()
        val hemisphereVertices = mesh[0].vertexCount + mesh[1].vertexCount
        assertTrue(hemisphereVertices > 9000)
        assertTrue(mesh.sumOf { it.indices.size / 3 } > 16000)
    }
}
