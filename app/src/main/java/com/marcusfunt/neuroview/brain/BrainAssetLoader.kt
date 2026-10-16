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
        return BrainMesh(points, triangles, color)
    }
}
