package com.marcusfunt.neuroview.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import com.marcusfunt.neuroview.mri.NiftiSpatialUnits
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Rule
import org.junit.Test

class ScanDetailsAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun detailsWarnWhenHeaderSpacingHasNoUnitsAndLabelAcquisitionInference() {
        val scan = ScanRecord(
            id = "scan-details-test",
            collectionId = "collection",
            documentUri = "content://scan-details-test",
            displayName = "synthetic.nii",
            metadata = NiftiSeriesMetadata(
                dimensions = listOf(256, 256, 80),
                spacingHeaderUnits = listOf(0.5, 0.5, 3.0),
                timepoints = 1,
                dataType = "float32",
                orientation = "sform",
                description = "",
                spatialUnits = NiftiSpatialUnits.UNKNOWN,
            ),
        )
        compose.setContent {
            NeuroViewTheme {
                ScanDetailsDialog(scan = scan, importedAt = null, onDismiss = {})
            }
        }

        compose.onNodeWithText("2D-like", substring = true).assertIsDisplayed()
        compose.onNodeWithText("inferred", substring = true).assertIsDisplayed()
        compose.onNodeWithText("sform", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Spatial units unknown", substring = true).assertIsDisplayed()
        compose.onNodeWithText("do not interpret as mm", substring = true).assertIsDisplayed()
    }
}
