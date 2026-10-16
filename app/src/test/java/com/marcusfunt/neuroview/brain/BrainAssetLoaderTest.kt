package com.marcusfunt.neuroview.brain

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BrainAssetLoaderTest {
    private fun triangle(): ByteArray {
        val buffer = ByteBuffer.allocate(28 + 3 * 6 * 4 + 3 * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("NVB1".toByteArray(Charsets.US_ASCII))
        buffer.putInt(1)
        buffer.putInt(3)
        buffer.putInt(3)
        floatArrayOf(.79f, .81f, .83f).forEach { buffer.putFloat(it) }
        floatArrayOf(
            -1f, 0f, 0f, 0f, 0f, 1f,
             1f, 0f, 0f, 0f, 0f, 1f,
             0f, 1f, 0f, 0f, 0f, 1f,
        ).forEach { buffer.putFloat(it) }
        shortArrayOf(0, 1, 2).forEach { buffer.putShort(it) }
        return buffer.array()
    }

    @Test fun parsesGeometryNormalsColorAndIndices() {
        val mesh = BrainAssetLoader.load(ByteArrayInputStream(triangle()))
        assertEquals(3, mesh.vertexCount)
        assertEquals(3, mesh.indices.size)
        assertEquals(.79f, mesh.color[0], 0.0001f)
        assertArrayEquals(shortArrayOf(0, 1, 2), mesh.indices)
        assertEquals(1f, mesh.vertices[6], 0f)
    }

    @Test fun rejectsTruncatedOrCorruptedAssets() {
        val data = triangle()
        assertThrows(IllegalArgumentException::class.java) {
            BrainAssetLoader.load(ByteArrayInputStream(data.dropLast(2).toByteArray()))
        }
        data[0] = 'X'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            BrainAssetLoader.load(ByteArrayInputStream(data))
        }
    }

    @Test fun rejectsIndicesOutsideVertexRange() {
        val data = triangle()
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(data.size - 2, 3)
        assertThrows(IllegalArgumentException::class.java) {
            BrainAssetLoader.load(ByteArrayInputStream(data))
        }
    }
}
