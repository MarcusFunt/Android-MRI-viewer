package com.marcusfunt.neuroview.brain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deliberately stylized brain *illustration*, generated locally with no model download.
 * This is not anatomical/diagnostic geometry and is never derived from patient data.
 *
 * Layout per vertex: xyz position, xyz unit normal. Indices are unsigned 16-bit.
 * Each surface stays under the GLES2 65,535-vertex index limit.
 */
internal data class BrainMesh(
    val vertices: FloatArray,
    val indices: ShortArray,
    val color: FloatArray,
) {
    val vertexCount: Int get() = vertices.size / 6
}

internal object BrainGeometry {
    fun build(): List<BrainMesh> = listOf(
        hemisphere(-1f),
        hemisphere(1f),
        ellipsoid(cx = -0.25f, cy = -0.63f, cz = -0.58f,
            rx = 0.31f, ry = 0.29f, rz = 0.43f, tone = floatArrayOf(0.45f, 0.69f, 0.73f)),
        ellipsoid(cx = 0.25f, cy = -0.63f, cz = -0.58f,
            rx = 0.31f, ry = 0.29f, rz = 0.43f, tone = floatArrayOf(0.45f, 0.69f, 0.73f)),
        ellipsoid(cx = 0f, cy = -0.89f, cz = -0.26f,
            rx = 0.13f, ry = 0.36f, rz = 0.16f, tone = floatArrayOf(0.42f, 0.63f, 0.67f)),
    )

    private fun hemisphere(side: Float): BrainMesh = surface(
        latitudeSteps = 56,
        longitudeSteps = 96,
        color = floatArrayOf(0.69f, 0.84f, 0.85f),
        center = floatArrayOf(side * 0.42f, 0.06f, 0f),
    ) { latitude, longitude ->
        val polar = latitude.toDouble()
        val around = longitude.toDouble()
        val s = sin(polar)
        val foldA = 13.0 * polar + 2.4 * sin(4.0 * around) + side * 0.4
        val foldB = 17.0 * around + 2.0 * sin(4.0 * polar)
        // Grooves are narrow depressions with gentle gyri between them.
        val sulcusA = (1.0 - abs(sin(foldA))).coerceAtLeast(0.0).pow(9.0)
        val sulcusB = (1.0 - abs(sin(foldB))).coerceAtLeast(0.0).pow(11.0)
        val fold = 1.0 + 0.018 * sin(foldA) + 0.015 * sin(foldB) -
            0.066 * sulcusA - 0.036 * sulcusB
        floatArrayOf(
            (side * (0.42 + 0.40 * s * cos(around) * fold)).toFloat(),
            (0.06 + 0.83 * cos(polar) * fold).toFloat(),
            (0.98 * s * sin(around) * fold).toFloat(),
        )
    }

    private fun ellipsoid(
        cx: Float, cy: Float, cz: Float,
        rx: Float, ry: Float, rz: Float,
        tone: FloatArray,
    ): BrainMesh = surface(
        latitudeSteps = 28,
        longitudeSteps = 52,
        color = tone,
        center = floatArrayOf(cx, cy, cz),
    ) { theta, phi ->
        val polar = theta.toDouble()
        val around = phi.toDouble()
        val ripple = 1.0 + 0.024 * cos(22 * polar + 1.5 * sin(5 * around))
        floatArrayOf(
            (cx + rx * sin(polar) * cos(around) * ripple).toFloat(),
            (cy + ry * cos(polar) * ripple).toFloat(),
            (cz + rz * sin(polar) * sin(around) * ripple).toFloat(),
        )
    }

    private fun surface(
        latitudeSteps: Int,
        longitudeSteps: Int,
        color: FloatArray,
        center: FloatArray,
        position: (Float, Float) -> FloatArray,
    ): BrainMesh {
        val columns = longitudeSteps + 1
        val vertices = FloatArray((latitudeSteps + 1) * columns * 6)
        val indices = ShortArray(latitudeSteps * longitudeSteps * 6)
        val pi = PI.toFloat()
        val tangentStep = 0.002f

        for (latitudeIndex in 0..latitudeSteps) {
            val theta = (latitudeIndex.toFloat() / latitudeSteps * pi)
                .coerceIn(0.0001f, pi - 0.0001f)
            for (longitudeIndex in 0..longitudeSteps) {
                val phi = longitudeIndex.toFloat() / longitudeSteps * (2f * pi)
                val p = position(theta, phi)
                val tBefore = position(max(0.0001f, theta - tangentStep), phi)
                val tAfter = position(min(pi - 0.0001f, theta + tangentStep), phi)
                val pBefore = position(theta, phi - tangentStep)
                val pAfter = position(theta, phi + tangentStep)

                val t = floatArrayOf(
                    tAfter[0] - tBefore[0], tAfter[1] - tBefore[1], tAfter[2] - tBefore[2],
                )
                val a = floatArrayOf(
                    pAfter[0] - pBefore[0], pAfter[1] - pBefore[1], pAfter[2] - pBefore[2],
                )
                var nx = t[1] * a[2] - t[2] * a[1]
                var ny = t[2] * a[0] - t[0] * a[2]
                var nz = t[0] * a[1] - t[1] * a[0]
                val radialX = p[0] - center[0]
                val radialY = p[1] - center[1]
                val radialZ = p[2] - center[2]
                if (nx * radialX + ny * radialY + nz * radialZ < 0f) {
                    nx = -nx; ny = -ny; nz = -nz
                }
                val magnitude = sqrt(nx * nx + ny * ny + nz * nz)
                if (magnitude < 0.00000001f) {
                    // Stable polar fallback, avoiding zero-length lighting normals.
                    val radialLength = sqrt(
                        radialX * radialX + radialY * radialY + radialZ * radialZ,
                    ).coerceAtLeast(0.000001f)
                    nx = radialX / radialLength
                    ny = radialY / radialLength
                    nz = radialZ / radialLength
                } else {
                    nx /= magnitude; ny /= magnitude; nz /= magnitude
                }

                val offset = (latitudeIndex * columns + longitudeIndex) * 6
                vertices[offset] = p[0]
                vertices[offset + 1] = p[1]
                vertices[offset + 2] = p[2]
                vertices[offset + 3] = nx
                vertices[offset + 4] = ny
                vertices[offset + 5] = nz
            }
        }

        var next = 0
        for (row in 0 until latitudeSteps) {
            for (column in 0 until longitudeSteps) {
                val a = row * columns + column
                val b = a + columns
                indices[next++] = a.toShort()
                indices[next++] = b.toShort()
                indices[next++] = (a + 1).toShort()
                indices[next++] = (a + 1).toShort()
                indices[next++] = b.toShort()
                indices[next++] = (b + 1).toShort()
            }
        }
        return BrainMesh(vertices, indices, color)
    }
}
