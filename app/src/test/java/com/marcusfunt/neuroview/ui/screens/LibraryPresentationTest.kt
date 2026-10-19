package com.marcusfunt.neuroview.ui.screens

import com.marcusfunt.neuroview.catalog.ImportCollection
import com.marcusfunt.neuroview.catalog.ImportResult
import com.marcusfunt.neuroview.catalog.ScanCatalogSnapshot
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
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
        val plurals = resources.getElementsByTagName("plurals")
        val plural = (0 until plurals.length).map { plurals.item(it) }
            .single { it.attributes.getNamedItem("name").nodeValue == key.resourceName }
        val children = plural.childNodes
        val text = (0 until children.length).map { children.item(it) }
            .single { it.nodeName == "item" && it.attributes.getNamedItem("quantity").nodeValue == if (count == 1) "one" else "other" }.textContent
        return String.format(text, count)
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
