package com.marcusfunt.neuroview.ui.screens

import com.marcusfunt.neuroview.catalog.ImportCollection
import com.marcusfunt.neuroview.catalog.ImportResult
import com.marcusfunt.neuroview.catalog.ScanCatalogSnapshot
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import com.marcusfunt.neuroview.ui.components.toCatalogQuantity
import com.marcusfunt.neuroview.ui.operations.OperationUnit
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LibraryPresentationTest {
    private val metadata = NiftiSeriesMetadata(listOf(8, 9, 10), listOf(1.0, 1.0, 1.0), 1, "int16", "sform", "T1")
    private fun scan(id: Int, frames: Int = 1) = ScanRecord("$id", if (id % 2 == 0) "new" else "old",
        "content://scan/$id", "source-$id.nii.gz", metadata.copy(timepoints = frames), "Alias $id")

    // Exercise the shipped plural text without requiring an Android runtime in JVM tests.
    private val resources = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/values/strings.xml"))
    private fun quantity(key: CatalogQuantity, count: Int): String {
        return resourceQuantity(key.resourceName, count)
    }

    private fun resourceQuantity(resourceName: String, count: Int, formattedCount: String? = null): String {
        val plurals = resources.getElementsByTagName("plurals")
        val plural = (0 until plurals.length).map { plurals.item(it) }
            .single { it.attributes.getNamedItem("name").nodeValue == resourceName }
        val children = plural.childNodes
        val text = (0 until children.length).map { children.item(it) }
            .single { it.nodeName == "item" && it.attributes.getNamedItem("quantity").nodeValue == if (count == 1) "one" else "other" }.textContent
        return if (formattedCount == null) String.format(text, count) else String.format(text, formattedCount)
    }

    @Test fun zeroOneManyCountsUseShippedPluralResources() {
        assertEquals("0 scans · 0 collections", librarySummary(0, 0, ::quantity))
        assertEquals("1 scan · 1 collection", librarySummary(1, 1, ::quantity))
        assertEquals("2 scans · 2 collections", librarySummary(2, 2, ::quantity))
        assertEquals("1 matching scan", quantity(CatalogQuantity.MATCHING_SCANS, 1))
        assertEquals("0 matching scans", quantity(CatalogQuantity.MATCHING_SCANS, 0))
        assertEquals("1 MRI library entry", quantity(CatalogQuantity.MRI_ENTRIES, 1))
        assertEquals("2 MRI library entries", quantity(CatalogQuantity.MRI_ENTRIES, 2))
    }

    @Test fun conversionCacheSeriesAndProgressCountsHaveCorrectSingularAndPluralCopy() {
        assertEquals("0 volumes", resourceQuantity("series_volumes", 0))
        assertEquals("1 volume", resourceQuantity("series_volumes", 1))
        assertEquals("2 volumes", resourceQuantity("series_volumes", 2))
        assertEquals("Found 1 DICOM slice. Choose where to save the NIfTI.",
            resourceQuantity("conversion_dicom_slices_found", 1))
        assertEquals("Found 2 DICOM slices. Choose where to save the NIfTI.",
            resourceQuantity("conversion_dicom_slices_found", 2))
        assertEquals("Found 0 DICOM slices. Choose where to save the NIfTI.",
            resourceQuantity("conversion_dicom_slices_found", 0))
        assertEquals("Saved 0 DICOM slices as .nii.gz.", resourceQuantity("conversion_dicom_slices_saved", 0))
        assertEquals("Saved 1 DICOM slice as .nii.gz.", resourceQuantity("conversion_dicom_slices_saved", 1))
        assertEquals("Saved 2 DICOM slices as .nii.gz.", resourceQuantity("conversion_dicom_slices_saved", 2))
        assertEquals("Skipped 0 non-DICOM files.", resourceQuantity("conversion_non_dicom_files_skipped", 0))
        assertEquals("Skipped 1 non-DICOM file.", resourceQuantity("conversion_non_dicom_files_skipped", 1))
        assertEquals("Skipped 2 non-DICOM files.", resourceQuantity("conversion_non_dicom_files_skipped", 2))
        assertEquals("Saved 0 anonymized DERIVED Secondary Capture DICOM slices inside a ZIP archive.",
            resourceQuantity("conversion_derived_dicom_slices_saved", 0))
        assertEquals("Saved 1 anonymized DERIVED Secondary Capture DICOM slice inside a ZIP archive.",
            resourceQuantity("conversion_derived_dicom_slices_saved", 1))
        assertEquals("Saved 2 anonymized DERIVED Secondary Capture DICOM slices inside a ZIP archive.",
            resourceQuantity("conversion_derived_dicom_slices_saved", 2))
        assertEquals("Cleared 1 cached thumbnail", resourceQuantity("cache_thumbnails_cleared", 1))
        assertEquals("Cleared 2 cached thumbnails", resourceQuantity("cache_thumbnails_cleared", 2))
        assertEquals("Cleared 0 cached thumbnails", resourceQuantity("cache_thumbnails_cleared", 0))
        assertEquals("Cleared 0 generated MRI previews", resourceQuantity("cache_mri_previews_cleared", 0))
        assertEquals("Cleared 1 generated MRI preview", resourceQuantity("cache_mri_previews_cleared", 1))
        assertEquals("Cleared 2 generated MRI previews", resourceQuantity("cache_mri_previews_cleared", 2))
        assertEquals("0 bytes", resourceQuantity("operation_bytes", 0))
        assertEquals("1 byte", resourceQuantity("operation_bytes", 1))
        assertEquals("2 bytes", resourceQuantity("operation_bytes", 2))
        assertEquals("0 slices", resourceQuantity("operation_slices", 0))
        assertEquals("1 slice", resourceQuantity("operation_slices", 1))
        assertEquals("2 slices", resourceQuantity("operation_slices", 2))
        assertEquals("0 files", resourceQuantity("operation_files", 0))
        assertEquals("1 file", resourceQuantity("operation_files", 1))
        assertEquals("2 files", resourceQuantity("operation_files", 2))
        assertEquals("65,536 bytes", resourceQuantity("operation_bytes", 65_536, "65,536"))
        assertEquals("150,000 bytes", resourceQuantity("operation_bytes", 150_000, "150,000"))
        for (name in listOf("operation_voxels", "operation_pixels", "operation_items")) {
            assertEquals("0 ${name.substringAfter('_')}", resourceQuantity(name, 0))
            assertEquals("1 ${name.substringAfter('_').dropLast(1)}", resourceQuantity(name, 1))
            assertEquals("2 ${name.substringAfter('_')}", resourceQuantity(name, 2))
        }
    }

    @Test fun operationProgressUsesPluralResourcesForEveryUnit() {
        val expected = mapOf(
            OperationUnit.BYTES to CatalogQuantity.OPERATION_BYTES,
            OperationUnit.VOXELS to CatalogQuantity.OPERATION_VOXELS,
            OperationUnit.FILES to CatalogQuantity.OPERATION_FILES,
            OperationUnit.SLICES to CatalogQuantity.OPERATION_SLICES,
            OperationUnit.PIXELS to CatalogQuantity.OPERATION_PIXELS,
            OperationUnit.ITEMS to CatalogQuantity.OPERATION_ITEMS,
        )
        expected.forEach { (unit, label) ->
            assertEquals("$unit", label, unit.toCatalogQuantity())
            assertEquals("1 singular $unit", quantity(label, 1), resourceQuantity(label.resourceName, 1))
            assertEquals("2 plural $unit", quantity(label, 2), resourceQuantity(label.resourceName, 2))
        }
    }

    @Test fun importSummaryKeepsFailureDetailsAndOmitsIrrelevantZeroDetails() {
        assertEquals("0 MRIs added", importResultSummary(ImportResult(0, 0, emptyList()), ::quantity))
        assertEquals("1 MRI added", importResultSummary(ImportResult(1, 0, emptyList()), ::quantity))
        assertEquals("2 MRIs added · 1 skipped · 2 failed\nUnsupported syntax",
            importResultSummary(ImportResult(2, 1, listOf("Unsupported syntax"), 2), ::quantity))
    }

    @Test fun cancellationKeepsAcceptedCountsAndSpecificFailuresVisible() {
        for (count in listOf(0, 1, 2)) {
            val kept = if (count == 1) "1 accepted MRI kept" else "$count accepted MRIs kept"
            assertEquals("Import canceled · file checks: 2 / 3\n$kept · 1 skipped · 1 failed\nCannot read file",
                importCanceledSummary(ImportResult(count, 1, listOf("Cannot read file"), 1, true), 2, 3, ::quantity))
        }
    }

    @Test fun searchMatchesAliasesOriginalNamesDescriptionsAndCollectionsAtEveryLibrarySize() {
        for (size in listOf(1, 10, 120)) {
            val snapshot = ScanCatalogSnapshot(listOf(ImportCollection("old", "Earlier import", 1), ImportCollection("new", "Latest import", 2)),
                (1..size).map { scan(it, if (it % 2 == 0) 4 else 1) })
            assertEquals(listOf("1"), libraryScans(snapshot, "source-1.nii.gz", "Recent", "All").map { it.id })
            assertEquals(size, libraryScans(snapshot, "T1", "Recent", "All").size)
            assertTrue(libraryScans(snapshot, "missing", "Recent", "All").isEmpty())
            assertTrue(libraryScans(snapshot, "Earlier import", "Name", "All").all { it.collectionId == "old" })
            assertTrue(libraryScans(snapshot, "", "Name", "4D").all { it.metadata.timepoints > 1 })
            assertTrue(libraryScans(snapshot, "", "Recent", "3D").all { it.metadata.timepoints == 1 })
        }
    }

    @Test fun aliasesSortIndependentlyOfSourceNamesAndNewestUsesImportTime() {
        val snapshot = ScanCatalogSnapshot(listOf(ImportCollection("old", "Old", 1), ImportCollection("new", "New", 2)),
            listOf(scan(1).copy(customName = "Z".repeat(100)), scan(2).copy(customName = "a")))
        assertEquals(listOf("2", "1"), libraryScans(snapshot, "", "Name", "All").map { it.id })
        assertEquals(listOf("2", "1"), libraryScans(snapshot, "", "Recent", "All").map { it.id })
        assertEquals("source-1.nii.gz", snapshot.scans.first().displayName)
    }

    @Test fun homePreviewRequiresVisibleBoundsAndResumedLifecycle() {
        assertTrue(shouldShowHomePreview(true, true))
        assertFalse(shouldShowHomePreview(false, true))
        assertFalse(shouldShowHomePreview(true, false))
        assertFalse(shouldShowHomePreview(false, false))
    }
}
