package com.marcusfunt.neuroview.brain

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Strict reader for the bundled NVB1 mesh.
 *
 * All vertices are position.xyz + normal.xyz (six little-endian float32s),
 * followed by unsigned 16-bit triangle indices. Native GLES2 draws these
 * directly without a glTF runtime, texture decoding or GPU-side conversion.
 */
internal object BrainAssetLoader {
    private const val HEADER_BYTES = 28
    private const val MAX_ASSET_BYTES = 8 * 1024 * 1024
    const val ASSET_NAME = "landing_brain.nvbm"

    fun load(source: InputStream): BrainMesh {
        val bytes = source.use { it.readBytes() }
        require(bytes.size in HEADER_BYTES..MAX_ASSET_BYTES) { "Invalid brain asset length" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.get().toInt() == 'N'.code &&
            buffer.get().toInt() == 'V'.code &&
            buffer.get().toInt() == 'B'.code &&
            buffer.get().toInt() == '1'.code) { "Invalid brain asset magic" }
        require(buffer.int == 1) { "Unsupported NVBM version" }
        val verticesCount = buffer.int
        val indexCount = buffer.int
        require(verticesCount in 3..65_535) { "Unsupported OpenGL ES 2.0 vertex count" }
        require(indexCount in 3..600_000 && indexCount % 3 == 0) {
            "Invalid triangle index count"
        }
        val expected = HEADER_BYTES.toLong() + verticesCount * 24L + indexCount * 2L
        require(expected == bytes.size.toLong()) { "Truncated or oversized brain asset" }
        val color = FloatArray(3) { buffer.float }
        require(color.all { it.isFinite() && it in 0f..1f }) { "Invalid material color" }
        val points = FloatArray(verticesCount * 6) {
            buffer.float.also { value ->
                require(value.isFinite()) { "Invalid vertex coordinate or normal" }
            }
        }
        val triangles = ShortArray(indexCount) {
            buffer.short.also { value ->
                require((value.toInt() and 0xffff) < verticesCount) { "Index out of range" }
            }
        }
        softenSurface(points, triangles)
        return BrainMesh(points, triangles, color)
    }

    /** Applies one very light Laplacian pass to tame tiny mesh chatter while preserving folds. */
    private fun softenSurface(vertices: FloatArray, indices: ShortArray) {
        val vertexCount = vertices.size / 6
        val neighborCounts = IntArray(vertexCount)
        val positionSums = FloatArray(vertexCount * 3)
        val normalSums = FloatArray(vertexCount * 3)

        fun addNeighbor(from: Int, to: Int) {
            val fromOffset = from * 3
            val toOffset = to * 6
            positionSums[fromOffset] += vertices[toOffset]
            positionSums[fromOffset + 1] += vertices[toOffset + 1]
            positionSums[fromOffset + 2] += vertices[toOffset + 2]
            normalSums[fromOffset] += vertices[toOffset + 3]
            normalSums[fromOffset + 1] += vertices[toOffset + 4]
            normalSums[fromOffset + 2] += vertices[toOffset + 5]
            neighborCounts[from]++
        }

        for (offset in indices.indices step 3) {
            val a = indices[offset].toInt() and 0xffff
            val b = indices[offset + 1].toInt() and 0xffff
            val c = indices[offset + 2].toInt() and 0xffff
            addNeighbor(a, b); addNeighbor(a, c)
            addNeighbor(b, a); addNeighbor(b, c)
            addNeighbor(c, a); addNeighbor(c, b)
        }

        for (vertex in 0 until vertexCount) {
            val count = neighborCounts[vertex]
            if (count == 0) continue
            val offset = vertex * 6
            val sumsOffset = vertex * 3
            for (axis in 0..2) {
                val average = positionSums[sumsOffset + axis] / count
                vertices[offset + axis] += (average - vertices[offset + axis]) * 0.025f
            }

            var nx = vertices[offset + 3] * 0.84f +
                normalSums[sumsOffset] / count * 0.16f
            var ny = vertices[offset + 4] * 0.84f +
                normalSums[sumsOffset + 1] / count * 0.16f
            var nz = vertices[offset + 5] * 0.84f +
                normalSums[sumsOffset + 2] / count * 0.16f
            val length = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.000001f)
            nx /= length; ny /= length; nz /= length
            vertices[offset + 3] = nx
            vertices[offset + 4] = ny
            vertices[offset + 5] = nz
        }
    }
}
