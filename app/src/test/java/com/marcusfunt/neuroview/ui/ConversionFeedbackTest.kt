package com.marcusfunt.neuroview.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionFeedbackTest {
    @Test
    fun savedNiftiFeedbackNamesTheFileAndFolder() {
        val feedback = savedNiftiFeedback(
            sliceSummary = "Saved 5 DICOM slices as .nii.gz.",
            fileName = "NeuroView_DICOM_derived.nii.gz",
            folderPath = "Download/MRI study",
        )

        assertTrue(feedback.contains("NeuroView_DICOM_derived.nii.gz"))
        assertTrue(feedback.contains("Download/MRI study"))
        assertTrue(feedback.contains("Import NIfTI"))
    }

    @Test
    fun savedNiftiFeedbackExplainsWhereToLookWhenFolderIsUnavailable() {
        val feedback = savedNiftiFeedback(
            sliceSummary = "Saved 1 DICOM slice as .nii.gz.",
            fileName = "scan.nii.gz",
            folderPath = null,
        )

        assertTrue(feedback.contains("scan.nii.gz"))
        assertTrue(feedback.contains("Search Files for scan.nii.gz"))
    }

    @Test
    fun externalStorageDocumentIdIdentifiesItsContainingFolder() {
        assertEquals(
            "Download/MRI study",
            externalStorageFolderPath("primary:Download/MRI study/scan.nii.gz"),
        )
    }

    @Test
    fun externalStorageDocumentInRootHasReadableLocation() {
        assertEquals("Internal storage", externalStorageFolderPath("primary:scan.nii.gz"))
    }

    @Test
    fun suggestedConversionNameKeepsNiftiExtensionAfterUniqueSuffix() {
        assertEquals(
            "NeuroView_DICOM_derived_20261105_110700_123.nii.gz",
            suggestedNiftiFileName("20261105_110700_123"),
        )
    }
}
