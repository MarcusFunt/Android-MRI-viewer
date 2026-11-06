package com.marcusfunt.neuroview.ui.screens

import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import com.marcusfunt.neuroview.mri.NiftiSpatialUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanMetadataPresentationTest {
    private fun metadata(spacing: List<Double>) = NiftiSeriesMetadata(
        dimensions = listOf(256, 256, 80),
        spacingHeaderUnits = spacing,
        timepoints = 1,
        dataType = "float32",
        orientation = "sform",
        description = "",
        spatialUnits = NiftiSpatialUnits.MILLIMETER,
        sformCode = 1,
    )

    @Test fun anisotropicSpacingIsReportedAsAnInference() {
        val result = scanSamplingInference(metadata(listOf(0.5, 0.5, 3.0)))

        assertTrue(result.contains("2D-like"))
        assertTrue(result.contains("inferred"))
        assertTrue(result.contains("unconfirmed"))
    }

    @Test fun nearIsotropicSpacingIsNotClaimedAsConfirmedThreeDimensionalAcquisition() {
        val result = scanSamplingInference(metadata(listOf(1.0, 1.0, 1.1)))

        assertTrue(result.contains("3D-like"))
        assertTrue(result.contains("inferred"))
        assertTrue(result.contains("unconfirmed"))
    }

    @Test fun inconclusiveSpacingRemainsUnknownAndAffineSourceIncludesItsCode() {
        val scan = metadata(listOf(0.8, 1.0, 1.4))

        assertTrue(scanSamplingInference(scan).startsWith("Unknown"))
        assertEquals("sform (code 1)", scanAffineProvenance(scan))
    }

    @Test fun missingUnitsNeverProduceMillimeterSpacing() {
        val scan = metadata(listOf(0.5, 0.5, 3.0)).copy(spatialUnits = NiftiSpatialUnits.UNKNOWN)

        assertEquals(null, scan.spacingMm)
        assertTrue(scanSpacingDetails(scan).contains("do not interpret as mm"))
        assertTrue(requireNotNull(scanSpatialUnitWarning(scan)).contains("unavailable"))
    }
}
