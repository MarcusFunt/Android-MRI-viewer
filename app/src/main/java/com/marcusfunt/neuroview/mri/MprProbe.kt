package com.marcusfunt.neuroview.mri

import kotlin.math.roundToInt
import kotlin.math.hypot

/** Reversible geometry between a radiological MPR image and its RAS+ world coordinates. */
object MprProbe {
    data class Sample(
        val worldRasMm: DoubleArray,
        val voxelIJK: DoubleArray,
        val intensity: Float,
    )

    /** Euclidean distance between two points in NIfTI RAS+ millimetres. */
    fun distanceMm(firstWorldRasMm: DoubleArray, secondWorldRasMm: DoubleArray): Double {
        require(firstWorldRasMm.size == 3 && secondWorldRasMm.size == 3)
        require(firstWorldRasMm.all { it.isFinite() } && secondWorldRasMm.all { it.isFinite() })
        return hypot(
            hypot(firstWorldRasMm[0] - secondWorldRasMm[0], firstWorldRasMm[1] - secondWorldRasMm[1]),
            firstWorldRasMm[2] - secondWorldRasMm[2],
        )
    }

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
        return sample(volume, ViewPlaneDescriptor.worldAligned(plane), slice, horizontalFraction, verticalFraction)
    }

    fun sample(
        volume: NiftiVolume,
        descriptor: ViewPlaneDescriptor,
        slice: Int,
        horizontalFraction: Double,
        verticalFraction: Double,
    ): Sample? = PlaneGeometry.create(volume, descriptor, maxOf(8, MprRenderer.MAX_IMAGE_SIDE))
        .sample(volume, slice, horizontalFraction, verticalFraction)

    fun sliceForWorld(volume: NiftiVolume, plane: MriPlane, world: DoubleArray): Int {
        require(world.size == 3 && world.all { it.isFinite() })
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        val lower = bounds[0][plane.firstAxis]
        val span = (bounds[1][plane.firstAxis] - lower).coerceAtLeast(1e-9)
        return (((world[plane.firstAxis] - lower) / span) *
            (MprRenderer.sliceCount(volume, plane) - 1))
            .roundToInt().coerceIn(0, MprRenderer.sliceCount(volume, plane) - 1)
    }

    fun sliceForWorld(volume: NiftiVolume, descriptor: ViewPlaneDescriptor, world: DoubleArray): Int =
        PlaneGeometry.create(volume, descriptor, maxOf(8, MprRenderer.MAX_IMAGE_SIDE)).sliceForWorld(world)

    fun fractionsForWorld(volume: NiftiVolume, plane: MriPlane, world: DoubleArray): Pair<Double, Double> {
        require(world.size == 3)
        val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
        fun fraction(axis: Int): Double {
            val span = (bounds[1][axis] - bounds[0][axis]).coerceAtLeast(1e-9)
            return (bounds[1][axis] - world[axis]) / span
        }
        return fraction(plane.horizontalAxis) to fraction(plane.verticalAxis)
    }

    fun fractionsForWorld(
        volume: NiftiVolume,
        descriptor: ViewPlaneDescriptor,
        world: DoubleArray,
    ): Pair<Double, Double> {
        require(world.size == 3)
        val fractions = PlaneGeometry.create(volume, descriptor, maxOf(8, MprRenderer.MAX_IMAGE_SIDE))
            .fractionsForWorld(world, slice = 0)
        return fractions.horizontal to fractions.vertical
    }
}
