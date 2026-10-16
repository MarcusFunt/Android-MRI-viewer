package com.marcusfunt.neuroview.mri

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Row-major 3x4 affine mapping voxel (i,j,k) to NIfTI RAS+ millimetres. */
class SpatialAffine private constructor(
    private val forward: DoubleArray,
    private val inverse: DoubleArray,
) {
    fun world(i: Double, j: Double, k: Double): DoubleArray = transform(forward, i, j, k)
    fun voxel(x: Double, y: Double, z: Double): DoubleArray = transform(inverse, x, y, z)

    fun axisSpacing(axis: Int): Double = sqrt(
        forward[axis] * forward[axis] +
            forward[4 + axis] * forward[4 + axis] +
            forward[8 + axis] * forward[8 + axis],
    )

    fun bounds(nx: Int, ny: Int, nz: Int): Array<DoubleArray> {
        val min = doubleArrayOf(
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
        )
        val max = doubleArrayOf(
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
        )
        for (i in listOf(0.0, (nx - 1).toDouble())) {
            for (j in listOf(0.0, (ny - 1).toDouble())) {
                for (k in listOf(0.0, (nz - 1).toDouble())) {
                    val pos = world(i, j, k)
                    for (axis in 0..2) {
                        min[axis] = kotlin.math.min(min[axis], pos[axis])
                        max[axis] = kotlin.math.max(max[axis], pos[axis])
                    }
                }
            }
        }
        return arrayOf(min, max)
    }

    companion object {
        fun fromRows(rows: DoubleArray): SpatialAffine {
            require(rows.size == 12 && rows.all { it.isFinite() }) { "Invalid NIfTI affine" }
            val a = rows[0]; val b = rows[1]; val c = rows[2]
            val d = rows[4]; val e = rows[5]; val f = rows[6]
            val g = rows[8]; val h = rows[9]; val i = rows[10]
            val determinant = a * (e * i - f * h) -
                b * (d * i - f * g) + c * (d * h - e * g)
            require(determinant.isFinite() && abs(determinant) > 1e-9) {
                "Invalid or singular NIfTI orientation matrix"
            }
            val inv = doubleArrayOf(
                (e * i - f * h) / determinant,
                (c * h - b * i) / determinant,
                (b * f - c * e) / determinant,
                0.0,
                (f * g - d * i) / determinant,
                (a * i - c * g) / determinant,
                (c * d - a * f) / determinant,
                0.0,
                (d * h - e * g) / determinant,
                (b * g - a * h) / determinant,
                (a * e - b * d) / determinant,
                0.0,
            )
            for (axis in 0..2) {
                inv[axis * 4 + 3] = -(
                    inv[axis * 4] * rows[3] +
                    inv[axis * 4 + 1] * rows[7] +
                    inv[axis * 4 + 2] * rows[11]
                )
            }
            require((0..2).all { index ->
                val spacing = sqrt(
                    rows[index] * rows[index] +
                    rows[4 + index] * rows[4 + index] +
                    rows[8 + index] * rows[8 + index],
                )
                spacing.isFinite() && spacing > 1e-8
            }) { "Missing NIfTI voxel spacing" }
            return SpatialAffine(rows.clone(), inv)
        }

        fun axisAligned(dx: Double, dy: Double, dz: Double) = fromRows(
            doubleArrayOf(dx, 0.0, 0.0, 0.0, 0.0, dy, 0.0, 0.0, 0.0, 0.0, dz, 0.0),
        )

        fun fromQuaternion(
            bInput: Double, cInput: Double, dInput: Double,
            dx: Double, dy: Double, dz: Double, qfac: Double,
            tx: Double, ty: Double, tz: Double,
        ): SpatialAffine {
            var b = bInput; var c = cInput; var d = dInput
            var a = 1.0 - (b * b + c * c + d * d)
            if (a < 1e-7) {
                val scale = 1.0 / sqrt(max(1e-15, b * b + c * c + d * d))
                b *= scale; c *= scale; d *= scale; a = 0.0
            } else {
                a = sqrt(a)
            }
            val sx = dx.coerceAtLeast(1e-6)
            val sy = dy.coerceAtLeast(1e-6)
            val sz = dz.coerceAtLeast(1e-6) * (if (qfac < 0.0) -1.0 else 1.0)
            return fromRows(
                doubleArrayOf(
                    (a*a+b*b-c*c-d*d)*sx, 2.0*(b*c-a*d)*sy, 2.0*(b*d+a*c)*sz, tx,
                    2.0*(b*c+a*d)*sx, (a*a+c*c-b*b-d*d)*sy, 2.0*(c*d-a*b)*sz, ty,
                    2.0*(b*d-a*c)*sx, 2.0*(c*d+a*b)*sy, (a*a+d*d-c*c-b*b)*sz, tz,
                ),
            )
        }

        private fun transform(matrix: DoubleArray, x: Double, y: Double, z: Double) =
            doubleArrayOf(
                matrix[0]*x + matrix[1]*y + matrix[2]*z + matrix[3],
                matrix[4]*x + matrix[5]*y + matrix[6]*z + matrix[7],
                matrix[8]*x + matrix[9]*y + matrix[10]*z + matrix[11],
            )
    }
}
