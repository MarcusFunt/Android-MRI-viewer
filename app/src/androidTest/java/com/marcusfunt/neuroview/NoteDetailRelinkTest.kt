package com.marcusfunt.neuroview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import com.marcusfunt.neuroview.mri.NiftiSpatialUnits
import com.marcusfunt.neuroview.notes.ScanNote
import com.marcusfunt.neuroview.notes.ScanNoteArchiveCodec
import com.marcusfunt.neuroview.ui.screens.NoteDetailScreen
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NoteDetailRelinkTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun unavailableNoteCanBeManuallyRelinkedAfterMetadataConfirmation() {
        val scan = scan("restored-scan", "T1.nii")
        val note = note().copy(
            sourceFingerprint = ScanNoteArchiveCodec.fingerprint(scan),
            sourceDisplayName = "T1.nii",
            archiveSourceScanId = "old-scan-id",
        )
        var relinkedScanId: String? = null
        compose.setContent {
            NeuroViewTheme {
                NoteDetailScreen(
                    note = note,
                    available = false,
                    availableScans = listOf(scan),
                    onRelink = { relinkedScanId = it.id },
                    onBack = {},
                    onOpenSavedSlice = {},
                )
            }
        }

        compose.onNodeWithText("Relink to scan").performClick()
        compose.onNodeWithText("T1.nii · 128 × 128 × 64", substring = true).performClick()
        compose.onNodeWithText("Confirm relink").assertIsDisplayed()
        compose.onNodeWithText("Relink note").performClick()

        compose.runOnIdle { assertEquals(scan.id, relinkedScanId) }
    }

    @Test
    fun mismatchedSourceRequiresExplicitWarningBeforeRelinking() {
        val scan = scan("different-scan", "Other.nii")
        var relinked = false
        compose.setContent {
            NeuroViewTheme {
                NoteDetailScreen(
                    note = note().copy(sourceFingerprint = "sha256:" + "0".repeat(64)),
                    available = false,
                    availableScans = listOf(scan),
                    onRelink = { relinked = true },
                    onBack = {},
                    onOpenSavedSlice = {},
                )
            }
        }

        compose.onNodeWithText("Relink to scan").performClick()
        compose.onNodeWithText("Other.nii · 128 × 128 × 64", substring = true).performClick()
        compose.onNodeWithText("Metadata differs", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        compose.runOnIdle { assertEquals(false, relinked) }
    }

    @Test
    fun relinkStorageFailureIsVisibleAndAnnouncedOnTheNote() {
        compose.setContent {
            NeuroViewTheme {
                NoteDetailScreen(
                    note = note(),
                    available = false,
                    availableScans = emptyList(),
                    relinkStatus = "Could not relink note: storage error.",
                    onRelink = {},
                    onBack = {},
                    onOpenSavedSlice = {},
                )
            }
        }

        compose.onNodeWithText("Could not relink note: storage error.").assertIsDisplayed()
    }

    private fun note() = ScanNote(
        id = "restored-note",
        scanId = "archive-unlinked:old-scan-id",
        collectionLabel = "Imported set",
        seriesLabel = "T1 axial",
        planeName = "AXIAL",
        sliceIndex = 23,
        sliceCount = 64,
        createdAtMillis = 1_797_000_000_000L,
        body = "Imported observation",
        spatialTransformPresent = true,
    )

    private fun scan(id: String, name: String) = ScanRecord(
        id = id,
        collectionId = "collection-a",
        documentUri = "content://provider/$id",
        displayName = name,
        metadata = NiftiSeriesMetadata(
            dimensions = listOf(128, 128, 64),
            spacingHeaderUnits = listOf(0.5, 0.5, 3.0),
            timepoints = 1,
            dataType = "float32",
            orientation = "sform",
            description = "synthetic",
            spatialUnits = NiftiSpatialUnits.MILLIMETER,
            sformCode = 1,
            transformHeaderFingerprint = "sha256:" + "a".repeat(64),
        ),
    )
}
