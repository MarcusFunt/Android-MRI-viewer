package com.marcusfunt.neuroview.mri

import kotlin.math.acos
import kotlin.math.roundToInt

/** Describes the displayed plane and how it relates to the stored voxel grid. */
data class ViewPlaneDescriptor(
    val isNativeGrid: Boolean,
    val nearestAnatomicalPlane: MriPlane?,
    val stackAxis: Int?,
    val horizontalAxis: Int,
    val verticalAxis: Int,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val flipStack: Boolean,
    val obliquityDegrees: Double,
    val label: String,
) {
    val isObliqueNative: Boolean
        get() = isNativeGrid && obliquityDegrees > NativeGridOrientation.ALIGNED_TOLERANCE_DEGREES

    companion object {
        fun worldAligned(plane: MriPlane): ViewPlaneDescriptor = when (plane) {
            MriPlane.AXIAL -> ViewPlaneDescriptor(
                false, plane, 2, 0, 1, true, true, false, 0.0, plane.label,
            )
            MriPlane.CORONAL -> ViewPlaneDescriptor(
                false, plane, 1, 0, 2, true, true, false, 0.0, plane.label,
            )
            MriPlane.SAGITTAL -> ViewPlaneDescriptor(
                false, plane, 0, 1, 2, true, true, false, 0.0, plane.label,
            )
        }

        fun nativeGrid(volume: NiftiVolume, stackAxis: Int): ViewPlaneDescriptor {
            require(stackAxis in 0..2)
            // Under a sheared sform the stack-axis column is not perpendicular to
            // the native slice. The inverse-affine row is the true fixed-index plane normal.
            val direction = volume.affine.voxelPlaneNormalDirectionRas(stackAxis)
            val normalOptions = listOf(
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0),
            )
            val normalIndex = normalOptions.indices.maxBy { absDot(direction, normalOptions[it]) }
            val obliquity = Math.toDegrees(acos(absDot(direction, normalOptions[normalIndex]).coerceIn(-1.0, 1.0)))
            val nearestPlane = when (normalIndex) {
                0 -> MriPlane.SAGITTAL
                1 -> MriPlane.CORONAL
                else -> MriPlane.AXIAL
            }
            val horizontalTarget = if (nearestPlane == MriPlane.SAGITTAL) {
                doubleArrayOf(0.0, 1.0, 0.0) // RAS+ anterior at screen-left
            } else {
                doubleArrayOf(1.0, 0.0, 0.0) // RAS+ patient-right at screen-left
            }
            val verticalTarget = if (nearestPlane == MriPlane.AXIAL) {
                doubleArrayOf(0.0, 1.0, 0.0)
            } else {
                doubleArrayOf(0.0, 0.0, 1.0)
            }
            val remaining = (0..2).filter { it != stackAxis }
            val horizontal = remaining.maxBy { absDot(volume.affine.voxelAxisDirectionRas(it), horizontalTarget) }
            val vertical = remaining.first { it != horizontal }
            val horizontalDirection = volume.affine.voxelAxisDirectionRas(horizontal)
            val verticalDirection = volume.affine.voxelAxisDirectionRas(vertical)
            val stackDirectionIsPositive = direction[normalIndex] >= 0.0
            return ViewPlaneDescriptor(
                isNativeGrid = true,
                nearestAnatomicalPlane = nearestPlane,
                stackAxis = stackAxis,
                horizontalAxis = horizontal,
                verticalAxis = vertical,
                flipHorizontal = dot(horizontalDirection, horizontalTarget) > 0.0,
                flipVertical = dot(verticalDirection, verticalTarget) > 0.0,
                flipStack = !stackDirectionIsPositive,
                obliquityDegrees = obliquity,
                label = if (obliquity <= NativeGridOrientation.ALIGNED_TOLERANCE_DEGREES) {
                    "Native grid"
                } else {
                    "Native · oblique"
                },
            )
        }
    }
}

data class NativeGridAssessment(
    val preferredStackAxis: Int,
    val nearestAnatomicalPlane: MriPlane,
    val obliquityDegrees: Double,
    val likely2DLike: Boolean,
    val orientationKnown: Boolean,
    val nearIsotropic: Boolean,
    val useNativeForStartup: Boolean,
    val sourceAxisSpacingMm: List<Double>,
)

/** Heuristics are explicitly identified as heuristics; NIfTI alone cannot prove acquisition type. */
object NativeGridOrientation {
    const val ALIGNED_TOLERANCE_DEGREES = 5.0
    private const val TWO_D_SPACING_RATIO = 1.75
    private const val ISOTROPIC_RATIO = 1.20

    fun analyze(volume: NiftiVolume): NativeGridAssessment {
        val spacing = (0..2).map(volume.affine::voxelPlaneSpacing)
        val minSpacing = spacing.minOrNull() ?: 1.0
        val maxSpacing = spacing.maxOrNull() ?: 1.0
        val ratio = maxSpacing / minSpacing.coerceAtLeast(1e-9)
        val preferredAxis = if (ratio >= TWO_D_SPACING_RATIO) spacing.indices.maxBy { spacing[it] } else 2
        val descriptor = ViewPlaneDescriptor.nativeGrid(volume, preferredAxis)
        val known = !volume.transformSource.startsWith("NO ORIENTATION")
        val likely2D = ratio >= TWO_D_SPACING_RATIO
        val isotropic = ratio <= ISOTROPIC_RATIO
        return NativeGridAssessment(
            preferredStackAxis = preferredAxis,
            nearestAnatomicalPlane = requireNotNull(descriptor.nearestAnatomicalPlane),
            obliquityDegrees = descriptor.obliquityDegrees,
            likely2DLike = likely2D,
            orientationKnown = known,
            nearIsotropic = isotropic,
            useNativeForStartup = known && likely2D && !isotropic,
            sourceAxisSpacingMm = spacing,
        )
    }

    fun initialDescriptor(volume: NiftiVolume, openNativeWhenAppropriate: Boolean): ViewPlaneDescriptor {
        val assessment = analyze(volume)
        return if (openNativeWhenAppropriate && assessment.useNativeForStartup) {
            ViewPlaneDescriptor.nativeGrid(volume, assessment.preferredStackAxis)
        } else {
            ViewPlaneDescriptor.worldAligned(MriPlane.AXIAL)
        }
    }
}

data class PlaneFractions(val horizontal: Double, val vertical: Double)

private data class NativePlaneBasis(
    val horizontal: DoubleArray,
    val vertical: DoubleArray,
    val normal: DoubleArray,
    val horizontalMin: Double,
    val horizontalMax: Double,
    val verticalMin: Double,
    val verticalMax: Double,
)

/** Reversible mapping between a raster plane, voxel samples and RAS+ millimetres. */
class PlaneGeometry private constructor(
    val descriptor: ViewPlaneDescriptor,
    private val volume: NiftiVolume,
    val width: Int,
    val height: Int,
    val sliceCount: Int,
    val sourcePixelSpacingHorizontalMm: Double,
    val sourcePixelSpacingVerticalMm: Double,
    val hasKnownSpatialUnits: Boolean,
    private val bounds: Array<DoubleArray>,
    private val horizontalSpan: Double,
    private val verticalSpan: Double,
    private val nativeBasis: NativePlaneBasis?,
) {
    val renderedPixelSpacingHorizontalMm: Double
        get() = horizontalSpan / (width - 1).coerceAtLeast(1)
    val renderedPixelSpacingVerticalMm: Double
        get() = verticalSpan / (height - 1).coerceAtLeast(1)

    fun voxelAtFraction(horizontal: Double, vertical: Double, slice: Int): DoubleArray =
        voxelAtFractionRaw(horizontal, vertical, slice)

    fun worldAtFraction(horizontal: Double, vertical: Double, slice: Int): DoubleArray {
        val voxel = voxelAtFractionRaw(horizontal, vertical, slice)
        return volume.affine.world(voxel[0], voxel[1], voxel[2])
    }

    fun fractionsForWorld(worldRas: DoubleArray, slice: Int): PlaneFractions {
        require(worldRas.size == 3 && worldRas.all(Double::isFinite))
        if (descriptor.isNativeGrid) {
            val basis = requireNotNull(nativeBasis)
            return PlaneFractions(
                fractionForCoordinate(
                    dot(worldRas, basis.horizontal), basis.horizontalMin, basis.horizontalMax, flip = false,
                ),
                fractionForCoordinate(
                    dot(worldRas, basis.vertical), basis.verticalMin, basis.verticalMax, flip = false,
                ),
            )
        }
        val hAxis = descriptor.horizontalAxis
        val vAxis = descriptor.verticalAxis
        return PlaneFractions(
            fractionForCoordinate(worldRas[hAxis], bounds[0][hAxis], bounds[1][hAxis], descriptor.flipHorizontal),
            fractionForCoordinate(worldRas[vAxis], bounds[0][vAxis], bounds[1][vAxis], descriptor.flipVertical),
        )
    }

    /** Quantizes normalized image coordinates to this geometry's fixed output sample grid. */
    fun snapFractionsToOutputGrid(horizontal: Double, vertical: Double): PlaneFractions {
        require(horizontal.isFinite() && vertical.isFinite())
        require(horizontal in 0.0..1.0 && vertical in 0.0..1.0)
        fun snap(fraction: Double, sampleCount: Int): Double {
            val intervals = (sampleCount - 1).coerceAtLeast(1)
            val index = (fraction * intervals).roundToInt().coerceIn(0, intervals)
            return index.toDouble() / intervals
        }
        return PlaneFractions(snap(horizontal, width), snap(vertical, height))
    }

    fun sample(volume: NiftiVolume, slice: Int, horizontal: Double, vertical: Double): MprProbe.Sample? {
        if (volume !== this.volume) return null
        if (!horizontal.isFinite() || !vertical.isFinite() || horizontal !in 0.0..1.0 || vertical !in 0.0..1.0) return null
        val voxel = voxelAtFractionRaw(horizontal, vertical, slice)
        if (voxel[0] !in 0.0..(volume.nx - 1).toDouble() ||
            voxel[1] !in 0.0..(volume.ny - 1).toDouble() ||
            voxel[2] !in 0.0..(volume.nz - 1).toDouble()
        ) return null
        val world = volume.affine.world(voxel[0], voxel[1], voxel[2])
        val exactNative = descriptor.isNativeGrid && voxel.all { kotlin.math.abs(it - it.roundToInt()) < 1e-8 }
        val intensity = if (exactNative) {
            volume.nearest(voxel[0].roundToInt(), voxel[1].roundToInt(), voxel[2].roundToInt())
        } else {
            volume.sample(voxel[0], voxel[1], voxel[2])
        }
        return MprProbe.Sample(world, voxel, intensity)
    }

    fun slicePositionMm(slice: Int): Double? {
        if (!hasKnownSpatialUnits) return null
        val planeAxis = descriptor.stackAxis ?: return null
        val centerVoxel = voxelAtFractionRaw(0.5, 0.5, slice)
        val centerWorld = volume.affine.world(centerVoxel[0], centerVoxel[1], centerVoxel[2])
        val referenceAxis = descriptor.nearestAnatomicalPlane?.firstAxis ?: planeAxis
        return centerWorld[referenceAxis]
    }

    fun renderedSliceSpacingMm(): Double? {
        if (!hasKnownSpatialUnits || sliceCount < 2) return null
        if (descriptor.isNativeGrid) {
            val stackAxis = descriptor.stackAxis ?: return null
            // Adjacent sheared native slices have in-plane center drift. Report
            // their perpendicular separation, which is the distance between planes.
            return volume.affine.voxelPlaneSpacing(stackAxis)
        }
        val first = worldAtFraction(0.5, 0.5, 0)
        val second = worldAtFraction(0.5, 0.5, 1)
        return MprProbe.distanceMm(first, second)
    }

    fun sliceForWorld(worldRas: DoubleArray): Int {
        require(worldRas.size == 3 && worldRas.all(Double::isFinite))
        if (!descriptor.isNativeGrid) {
            val plane = requireNotNull(descriptor.nearestAnatomicalPlane)
            val axis = plane.firstAxis
            val span = (bounds[1][axis] - bounds[0][axis]).coerceAtLeast(1e-9)
            val fraction = ((worldRas[axis] - bounds[0][axis]) / span).coerceIn(0.0, 1.0)
            return (fraction * (sliceCount - 1)).roundToInt().coerceIn(0, sliceCount - 1)
        }
        val stackAxis = descriptor.stackAxis ?: return 0
        val voxel = volume.affine.voxel(worldRas[0], worldRas[1], worldRas[2])
        val index = if (descriptor.flipStack) dimension(stackAxis) - 1.0 - voxel[stackAxis] else voxel[stackAxis]
        return index.roundToInt().coerceIn(0, sliceCount - 1)
    }

    internal fun voxelAtFractionRaw(horizontal: Double, vertical: Double, slice: Int): DoubleArray {
        val h = horizontal.coerceIn(0.0, 1.0)
        val v = vertical.coerceIn(0.0, 1.0)
        if (descriptor.isNativeGrid) {
            val basis = requireNotNull(nativeBasis)
            val sAxis = requireNotNull(descriptor.stackAxis)
            val stackIndex = if (descriptor.flipStack) dimension(sAxis) - 1.0 - slice.coerceIn(0, sliceCount - 1)
                else slice.coerceIn(0, sliceCount - 1).toDouble()
            val stackVoxel = DoubleArray(3).also { it[sAxis] = stackIndex }
            val planePoint = volume.affine.world(stackVoxel[0], stackVoxel[1], stackVoxel[2])
            val horizontalPosition = coordinateAtFraction(
                h, basis.horizontalMin, basis.horizontalMax, flip = false,
            )
            val verticalPosition = coordinateAtFraction(
                v, basis.verticalMin, basis.verticalMax, flip = false,
            )
            val normalPosition = dot(planePoint, basis.normal)
            val world = DoubleArray(3) { axis ->
                basis.horizontal[axis] * horizontalPosition +
                    basis.vertical[axis] * verticalPosition +
                    basis.normal[axis] * normalPosition
            }
            return volume.affine.voxel(world[0], world[1], world[2])
        }
        val plane = requireNotNull(descriptor.nearestAnatomicalPlane)
        val world = DoubleArray(3)
        val pos = MprRenderer.slicePosition(volume, plane, slice)
        world[plane.firstAxis] = pos
        val hAxis = descriptor.horizontalAxis
        val vAxis = descriptor.verticalAxis
        world[hAxis] = coordinateAtFraction(h, bounds[0][hAxis], bounds[1][hAxis], descriptor.flipHorizontal)
        world[vAxis] = coordinateAtFraction(v, bounds[0][vAxis], bounds[1][vAxis], descriptor.flipVertical)
        return volume.affine.voxel(world[0], world[1], world[2])
    }

    private fun dimension(axis: Int) = when (axis) {
        0 -> volume.nx
        1 -> volume.ny
        else -> volume.nz
    }

    companion object {
        fun create(volume: NiftiVolume, descriptor: ViewPlaneDescriptor, maxSide: Int): PlaneGeometry {
            require(maxSide in 8..1536)
            val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
            val plane = descriptor.nearestAnatomicalPlane ?: MriPlane.AXIAL
            val count = if (descriptor.isNativeGrid) {
                val axis = requireNotNull(descriptor.stackAxis)
                when (axis) { 0 -> volume.nx; 1 -> volume.ny; else -> volume.nz }
            } else MprRenderer.sliceCount(volume, plane)
            val horizontalSpan: Double
            val verticalSpan: Double
            val horizontalSpacing: Double
            val verticalSpacing: Double
            val width: Int
            val height: Int
            val nativeBasis: NativePlaneBasis?
            if (descriptor.isNativeGrid) {
                nativeBasis = createNativeBasis(volume, descriptor)
                horizontalSpan = (nativeBasis.horizontalMax - nativeBasis.horizontalMin).coerceAtLeast(0.001)
                verticalSpan = (nativeBasis.verticalMax - nativeBasis.verticalMin).coerceAtLeast(0.001)
                horizontalSpacing = volume.affine.worldDirectionSamplingStep(nativeBasis.horizontal)
                verticalSpacing = volume.affine.worldDirectionSamplingStep(nativeBasis.vertical)
            } else {
                nativeBasis = null
                val hAxis = descriptor.horizontalAxis
                val vAxis = descriptor.verticalAxis
                horizontalSpan = (bounds[1][hAxis] - bounds[0][hAxis]).coerceAtLeast(0.001)
                verticalSpan = (bounds[1][vAxis] - bounds[0][vAxis]).coerceAtLeast(0.001)
                horizontalSpacing = volume.affine.worldAxisSamplingStep(hAxis)
                verticalSpacing = volume.affine.worldAxisSamplingStep(vAxis)
            }
            // Preserve detail from the finer in-plane source direction within the
            // independent render-side budget. A coarse perpendicular axis must not
            // downsample the higher-resolution axis.
            val pixelStep = maxOf(
                minOf(horizontalSpacing, verticalSpacing),
                maxOf(horizontalSpan, verticalSpan) / (maxSide - 1).coerceAtLeast(1),
            )
            width = (horizontalSpan / pixelStep).roundToInt().coerceAtLeast(1) + 1
            height = (verticalSpan / pixelStep).roundToInt().coerceAtLeast(1) + 1
            return PlaneGeometry(
                descriptor, volume, width, height, count, horizontalSpacing, verticalSpacing,
                volume.hasKnownSpatialUnits, bounds, horizontalSpan, verticalSpan, nativeBasis,
            )
        }

        private fun createNativeBasis(
            volume: NiftiVolume,
            descriptor: ViewPlaneDescriptor,
        ): NativePlaneBasis {
            val stackAxis = requireNotNull(descriptor.stackAxis)
            val normal = volume.affine.voxelPlaneNormalDirectionRas(stackAxis)
            val horizontalSign = if (descriptor.flipHorizontal) -1.0 else 1.0
            val verticalSign = if (descriptor.flipVertical) -1.0 else 1.0
            val rawHorizontal = volume.affine.voxelAxisDirectionRas(descriptor.horizontalAxis)
            val horizontalProjected = subtract(rawHorizontal, scale(normal, dot(rawHorizontal, normal)))
            val horizontal = normalize(scale(horizontalProjected, horizontalSign))

            val rawVertical = volume.affine.voxelAxisDirectionRas(descriptor.verticalAxis)
            var verticalProjected = subtract(rawVertical, scale(normal, dot(rawVertical, normal)))
            verticalProjected = subtract(verticalProjected, scale(horizontal, dot(verticalProjected, horizontal)))
            val vertical = normalize(scale(verticalProjected, verticalSign))

            var horizontalMin = Double.POSITIVE_INFINITY
            var horizontalMax = Double.NEGATIVE_INFINITY
            var verticalMin = Double.POSITIVE_INFINITY
            var verticalMax = Double.NEGATIVE_INFINITY
            for (i in listOf(0.0, (volume.nx - 1).toDouble())) {
                for (j in listOf(0.0, (volume.ny - 1).toDouble())) {
                    for (k in listOf(0.0, (volume.nz - 1).toDouble())) {
                        val world = volume.affine.world(i, j, k)
                        val horizontalPosition = dot(world, horizontal)
                        val verticalPosition = dot(world, vertical)
                        horizontalMin = minOf(horizontalMin, horizontalPosition)
                        horizontalMax = maxOf(horizontalMax, horizontalPosition)
                        verticalMin = minOf(verticalMin, verticalPosition)
                        verticalMax = maxOf(verticalMax, verticalPosition)
                    }
                }
            }
            return NativePlaneBasis(
                horizontal, vertical, normal,
                horizontalMin, horizontalMax, verticalMin, verticalMax,
            )
        }

        private fun scale(vector: DoubleArray, factor: Double) = DoubleArray(3) { vector[it] * factor }

        private fun subtract(first: DoubleArray, second: DoubleArray) =
            DoubleArray(3) { first[it] - second[it] }

        private fun normalize(vector: DoubleArray): DoubleArray {
            val length = kotlin.math.sqrt(dot(vector, vector)).coerceAtLeast(1e-12)
            return scale(vector, 1.0 / length)
        }

        private fun indexAtFraction(fraction: Double, dimension: Int, flip: Boolean): Double {
            val position = fraction.coerceIn(0.0, 1.0) * (dimension - 1).coerceAtLeast(0)
            return if (flip) dimension - 1.0 - position else position
        }

        private fun coordinateAtFraction(fraction: Double, lower: Double, upper: Double, flip: Boolean) =
            if (flip) upper - fraction * (upper - lower) else lower + fraction * (upper - lower)

        private fun fractionForCoordinate(value: Double, lower: Double, upper: Double, flip: Boolean): Double {
            val fraction = (value - lower) / (upper - lower).coerceAtLeast(1e-9)
            return if (flip) 1.0 - fraction else fraction
        }
    }
}

private fun dot(first: DoubleArray, second: DoubleArray): Double =
    first[0] * second[0] + first[1] * second[1] + first[2] * second[2]

private fun absDot(first: DoubleArray, second: DoubleArray): Double = kotlin.math.abs(dot(first, second))
