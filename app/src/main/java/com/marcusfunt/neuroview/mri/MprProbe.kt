package com.marcusfunt.neuroview.mri

import kotlin.math.roundToInt

/** Reversible geometry between a radiological MPR image and its RAS+ world coordinates. */
object MprProbe {
    data class Sample(
        val worldRasMm: DoubleArray,
        val voxelIJK: DoubleArray,
        val intensity: Float,
    )

    /**
     * Horizontal and vertical fractions are measured from the top-left of the
     * actual fitted image (not its letterboxed Android canvas).
     */
    fun sample(
        volume: NiftiVolume,
        plane: MriPlane,
        slice: Int,
        horizontalFraction: Double,
        verticalFraction: Double,
    ): Sample? {
        if (!horizontalFraction.isFinite() || !verticalFraction.isFinite() ||
            horizontalFraction !in 0.0..1.0 || verticalFraction !in 0.0..1.0) return null
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        val world = DoubleArray(3)
        world[plane.firstAxis] = MprRenderer.slicePosition(volume, plane, slice)
        for ((axis, fraction) in listOf(
            plane.horizontalAxis to horizontalFraction,
            plane.verticalAxis to verticalFraction,
        )) {
            world[axis] = bounds[1][axis] - fraction * (bounds[1][axis] - bounds[0][axis])
        }
        val voxel = DoubleArray(3)
        volume.affine.voxelInto(world[0], world[1], world[2], voxel)
        if (voxel[0] !in 0.0..(volume.nx - 1).toDouble() ||
            voxel[1] !in 0.0..(volume.ny - 1).toDouble() ||
            voxel[2] !in 0.0..(volume.nz - 1).toDouble()) return null
        return Sample(world, voxel, volume.sample(voxel[0], voxel[1], voxel[2]))
    }

    fun sliceForWorld(volume: NiftiVolume, plane: MriPlane, world: DoubleArray): Int {
        require(world.size == 3 && world.all { it.isFinite() })
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        val lower = bounds[0][plane.firstAxis]
        val span = (bounds[1][plane.firstAxis] - lower).coerceAtLeast(1e-9)
        return (((world[plane.firstAxis] - lower) / span) *
            (MprRenderer.sliceCount(volume, plane) - 1))
            .roundToInt().coerceIn(0, MprRenderer.sliceCount(volume, plane) - 1)
    }

    fun fractionsForWorld(volume: NiftiVolume, plane: MriPlane, world: DoubleArray): Pair<Double, Double> {
        require(world.size == 3)
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        fun fraction(axis: Int): Double {
            val span = (bounds[1][axis] - bounds[0][axis]).coerceAtLeast(1e-9)
            return (bounds[1][axis] - world[axis]) / span
        }
        return fraction(plane.horizontalAxis) to fraction(plane.verticalAxis)
    }
}
