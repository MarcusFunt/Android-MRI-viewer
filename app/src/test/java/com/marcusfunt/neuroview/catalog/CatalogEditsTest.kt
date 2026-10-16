package com.marcusfunt.neuroview.catalog

import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogEditsTest {
    private val metadata = NiftiSeriesMetadata(
        dimensions = listOf(100, 90, 80),
        spacingMm = listOf(1.0, 1.0, 1.2),
        timepoints = 1,
        dataType = "int16",
        orientation = "sform",
        description = "test",
    )

    private fun initial() = ScanCatalogSnapshot(
        collections = listOf(
            ImportCollection("a", "Batch A", 1L),
            ImportCollection("b", "Batch B", 2L),
        ),
        scans = listOf(
            ScanRecord("s1", "a", "content://nifti/1", "first.nii", metadata),
            ScanRecord("s2", "a", "content://nifti/2", "second.nii", metadata),
            ScanRecord("s3", "b", "content://nifti/3", "third.nii", metadata),
        ),
    )

    @Test fun renameChangesCatalogTitleNotOriginalFilenameOrUri() {
        val updated = CatalogEdits.renameSeries(initial(), "s1", "  T1 MPRAGE  ")
        val scan = updated.scans.first()
        assertEquals("T1 MPRAGE", scan.label)
        assertEquals("first.nii", scan.displayName)
        assertEquals("content://nifti/1", scan.documentUri)
        assertEquals("first.nii", CatalogEdits.resetSeriesName(updated, "s1").scans.first().label)
    }

    @Test fun movingSeriesKeepsStableIdAndOriginalUri() {
        val updated = CatalogEdits.moveSeries(initial(), "s1", "b")
        assertEquals(2, updated.inCollection("b").size)
        assertEquals(1, updated.inCollection("a").size)
        assertEquals("content://nifti/1", updated.scans.first().documentUri)
        assertEquals("s1", updated.scans.first().id)
    }

    @Test fun removingSingleSeriesDoesNotRemoveOthersOrTheirCollection() {
        val updated = CatalogEdits.removeSeries(initial(), "s1")
        assertEquals(2, updated.scans.size)
        assertEquals(2, updated.collections.size)
        assertTrue(updated.scans.none { it.id == "s1" })
    }

    @Test fun removingCollectionCascadesOnlyReferencesInThatCollection() {
        val updated = CatalogEdits.removeCollection(initial(), "a")
        assertEquals(listOf("b"), updated.collections.map { it.id })
        assertEquals(listOf("s3"), updated.scans.map { it.id })
    }

    @Test fun createRenameCollectionAndPersistenceFriendlyImmutableOutputs() {
        val original = initial()
        val updated = CatalogEdits.createCollection(original, "c", " Personal ", 5L)
        assertEquals("Personal", updated.collections.first().label)
        assertEquals("Batch A", original.collections.first().label)
        val renamed = CatalogEdits.renameCollection(updated, "c", "Study review")
        assertEquals("Study review", renamed.collections.first().label)
        assertEquals(3, renamed.scans.size)
    }

    @Test fun invalidLabelsAndUnknownTargetsAreRejected() {
        for (bad in listOf(" ", "\n", "x".repeat(101), "name\u0000bad")) {
            assertThrows(IllegalArgumentException::class.java) {
                CatalogEdits.checkedLabel(bad)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogEdits.moveSeries(initial(), "s1", "missing")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogEdits.renameSeries(initial(), "missing", "some")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogEdits.removeCollection(initial(), "missing")
        }
    }

    @Test fun emptyCollectionsRemainAvailableToMoveScansInto() {
        val after = CatalogEdits.removeSeries(CatalogEdits.removeSeries(initial(), "s1"), "s2")
        assertEquals(0, after.inCollection("a").size)
        assertTrue(after.collections.any { it.id == "a" })
    }
}
