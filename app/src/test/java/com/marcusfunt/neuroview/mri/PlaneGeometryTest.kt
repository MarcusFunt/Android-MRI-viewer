package com.marcusfunt.neuroview.mri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaneGeometryTest {
    @Test
    fun nativeGridUsesStoredSamplesAndMapsFlippedAxesToPatientSpace() {
        val volume = volume(
            nx = 3, ny = 4, nz = 2,
            affine = SpatialAffine.fromRows(doubleArrayOf(
                -2.0, 0.0, 0.0, 10.0,
                0.0, 1.0, 0.0, -5.0,
                0.0, 0.0, 3.0, 7.0,
            )),
        )
        val descriptor = ViewPlaneDescriptor.nativeGrid(volume, stackAxis = 2)
        val geometry = PlaneGeometry.create(volume, descriptor, maxSide = 64)

        assertTrue(descriptor.isNativeGrid)
        assertEquals(MriPlane.AXIAL, descriptor.nearestAnatomicalPlane)
        assertEquals(0, descriptor.horizontalAxis)
        assertEquals(1, descriptor.verticalAxis)
        assertFalse(descriptor.flipHorizontal)
        assertTrue(descriptor.flipVertical)
        assertEquals(2, geometry.sliceCount)

        val topLeftVoxel = geometry.voxelAtFraction(0.0, 0.0, slice = 0)
        val bottomRightVoxel = geometry.voxelAtFraction(1.0, 1.0, slice = 0)
        assertEquals(listOf(0.0, 3.0, 0.0), topLeftVoxel.toList())
        assertEquals(listOf(2.0, 0.0, 0.0), bottomRightVoxel.toList())
        assertEquals(2.0, geometry.sourcePixelSpacingHorizontalMm, 1e-9)
        assertEquals(1.0, geometry.sourcePixelSpacingVerticalMm, 1e-9)

        val fraction = PlaneFractions(0.25, 0.75)
        val world = geometry.worldAtFraction(fraction.horizontal, fraction.vertical, slice = 1)
        val roundTrip = geometry.fractionsForWorld(world, slice = 1)
        assertEquals(fraction.horizontal, roundTrip.horizontal, 1e-9)
        assertEquals(fraction.vertical, roundTrip.vertical, 1e-9)
        assertEquals(volume.nearest(0, 3, 1), geometry.sample(volume, 1, 0.0, 0.0)?.intensity)
    }

    @Test
    fun planeOrientationClassifiesFlipsSwapsAndObliquityWithoutGuessingScannerProvenance() {
        val flipped = volume(8, 7, 6, SpatialAffine.axisAligned(-1.0, 1.5, 2.0))
        val flippedAssessment = NativeGridOrientation.analyze(flipped)
        assertEquals(2, flippedAssessment.preferredStackAxis)
        assertEquals(MriPlane.AXIAL, flippedAssessment.nearestAnatomicalPlane)
        assertEquals(0.0, flippedAssessment.obliquityDegrees, 1e-6)
        assertTrue(flippedAssessment.likely2DLike)
        assertTrue(flippedAssessment.orientationKnown)

        val swappedAffine = SpatialAffine.fromRows(doubleArrayOf(
            0.0, 0.0, 1.0, 0.0,
            0.0, 2.0, 0.0, 0.0,
            1.0, 0.0, 0.0, 0.0,
        ))
        val swappedVolume = volume(8, 7, 6, swappedAffine)
        val swapped = ViewPlaneDescriptor.nativeGrid(swappedVolume, stackAxis = 2)
        assertEquals(MriPlane.SAGITTAL, swapped.nearestAnatomicalPlane)
        assertEquals(0.0, swapped.obliquityDegrees, 1e-6)

        val angle = Math.toRadians(15.0)
        val obliqueAffine = SpatialAffine.fromRows(doubleArrayOf(
            1.0, 0.0, 0.0, 0.0,
            0.0, kotlin.math.cos(angle), -kotlin.math.sin(angle), 0.0,
            0.0, kotlin.math.sin(angle), kotlin.math.cos(angle), 0.0,
        ))
        val oblique = NativeGridOrientation.analyze(volume(8, 7, 6, obliqueAffine))
        assertEquals(MriPlane.AXIAL, oblique.nearestAnatomicalPlane)
        assertEquals(15.0, oblique.obliquityDegrees, 1e-6)
        assertTrue(ViewPlaneDescriptor.nativeGrid(volume(8, 7, 6, obliqueAffine), 2).isObliqueNative)
    }

    @Test
    fun isotropicUnknownProvenanceDefaultsToNeutralAxialAndNoMmClaim() {
        val volume = volume(
            8, 7, 6, SpatialAffine.axisAligned(1.0, 1.1, 1.05),
            knownUnits = false,
            transformSource = "NO ORIENTATION — assumed RAS; labels are not reliable",
        )
        val assessment = NativeGridOrientation.analyze(volume)
        val initial = NativeGridOrientation.initialDescriptor(volume, openNativeWhenAppropriate = true)
        val geometry = PlaneGeometry.create(volume, initial, maxSide = 64)

        assertTrue(assessment.nearIsotropic)
        assertFalse(assessment.orientationKnown)
        assertFalse(assessment.useNativeForStartup)
        assertFalse(initial.isNativeGrid)
        assertFalse(geometry.hasKnownSpatialUnits)
        assertEquals(null, geometry.slicePositionMm(3))
    }

    @Test
    fun nonIsotropicStartupIsClearlyLabeledAsNativeGridAndCanBeDisabled() {
        val volume = volume(8, 7, 6, SpatialAffine.axisAligned(1.0, 1.0, 2.5))
        val native = NativeGridOrientation.initialDescriptor(volume, openNativeWhenAppropriate = true)
        val axial = NativeGridOrientation.initialDescriptor(volume, openNativeWhenAppropriate = false)

        assertTrue(NativeGridOrientation.analyze(volume).useNativeForStartup)
        assertTrue(native.isNativeGrid)
        assertEquals("Native grid", native.label)
        assertFalse(axial.isNativeGrid)
        assertEquals("Axial", axial.label)
    }

    @Test
    fun negativeQfacKeepsNativeStackInIncreasingPatientPositionOrder() {
        val volume = volume(
            3, 4, 5,
            SpatialAffine.fromQuaternion(
                bInput = 0.0, cInput = 0.0, dInput = 0.0,
                dx = 1.0, dy = 1.0, dz = 2.0, qfac = -1.0,
                tx = 0.0, ty = 0.0, tz = 10.0,
            ),
        )
        val descriptor = ViewPlaneDescriptor.nativeGrid(volume, stackAxis = 2)
        val geometry = PlaneGeometry.create(volume, descriptor, maxSide = 64)
        assertTrue(descriptor.flipStack)
        assertEquals(4.0, geometry.voxelAtFraction(0.5, 0.5, 0)[2], 0.0)
        assertTrue(requireNotNull(geometry.slicePositionMm(4)) > requireNotNull(geometry.slicePositionMm(0)))
        assertEquals(volume.nearest(2, 3, 4), geometry.sample(volume, 0, 0.0, 0.0)?.intensity)
    }

    @Test
    fun shearIsReportedWithoutChangingTheStoredVoxelGrid() {
        val affine = SpatialAffine.fromRows(doubleArrayOf(
            1.0, 0.25, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 2.0, 0.0,
        ))
        val volume = volume(7, 6, 5, affine)
        assertTrue(affine.hasShear())
        val descriptor = ViewPlaneDescriptor.nativeGrid(volume, stackAxis = 2)
        val geometry = PlaneGeometry.create(volume, descriptor, 64)
        val sample = geometry.sample(volume, 2, 2.0 / 6.0, 0.4)
        assertTrue(sample?.voxelIJK?.all { kotlin.math.abs(it - kotlin.math.round(it)) < 1e-8 } == true)
    }

    @Test
    fun shearedStackAxisIsClassifiedByTheActualNativeSlicePlaneNormal() {
        // The k-axis column leans 26.6° in RAS X, but a fixed-k slice is still axial.
        val affine = SpatialAffine.fromRows(doubleArrayOf(
            1.0, 0.0, 1.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 2.0, 0.0,
        ))
        val volume = volume(7, 6, 5, affine)
        val descriptor = ViewPlaneDescriptor.nativeGrid(volume, stackAxis = 2)

        assertTrue(affine.hasShear())
        assertEquals(MriPlane.AXIAL, descriptor.nearestAnatomicalPlane)
        assertEquals(0.0, descriptor.obliquityDegrees, 1e-6)
        assertFalse(descriptor.isObliqueNative)
        val assessment = NativeGridOrientation.analyze(volume)
        assertEquals(2, assessment.preferredStackAxis)
        assertEquals(2.0, assessment.sourceAxisSpacingMm[2], 1e-9)
        assertEquals(kotlin.math.sqrt(5.0), affine.axisSpacing(2), 1e-9)
        assertEquals(2.0, PlaneGeometry.create(volume, descriptor, 64).renderedSliceSpacingMm()!!, 1e-9)
    }

    private fun volume(
        nx: Int,
        ny: Int,
        nz: Int,
        affine: SpatialAffine,
        knownUnits: Boolean = true,
        transformSource: String = "sform (RAS+)",
    ): NiftiVolume = NiftiVolume(
        nx, ny, nz,
        FloatArray(nx * ny * nz) { it.toFloat() },
        affine, 0f, (nx * ny * nz).toFloat(), transformSource, 1,
        hasKnownSpatialUnits = knownUnits,
    )
}
