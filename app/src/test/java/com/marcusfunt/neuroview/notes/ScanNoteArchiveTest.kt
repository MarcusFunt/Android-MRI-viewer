package com.marcusfunt.neuroview.notes

import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import com.marcusfunt.neuroview.mri.NiftiSpatialUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanNoteArchiveTest {
    @Test
    fun archiveRoundTripsNoteTextAndFingerprintWithoutExportingDocumentUri() {
        val source = scan("scan-original", "T1.nii.gz")
        val note = note("midline | finding\n第二行 🧠", source.id).copy(
            sourceFingerprint = ScanNoteArchiveCodec.fingerprint(source),
            sourceDisplayName = source.displayName,
        )

        val encoded = ScanNoteArchiveCodec.encode(listOf(note), listOf(source), exportedAtMillis = 42L)
        val decoded = ScanNoteArchiveCodec.decode(encoded)

        assertTrue(encoded.contains("\"formatVersion\":1"))
        assertFalse("A note archive must never include provider URIs.", encoded.contains(source.documentUri))
        assertFalse("A note archive must never include MRI voxel data.", encoded.contains("voxelData"))
        assertEquals(note.body, decoded.notes.single().body)
        assertEquals(ScanNoteArchiveCodec.fingerprint(source), decoded.notes.single().sourceFingerprint)
        assertEquals("T1.nii.gz", decoded.notes.single().sourceDisplayName)
    }

    @Test
    fun archiveDoesNotInventAFingerprintForLegacyNotesWithoutSavedSourceMetadata() {
        val source = scan("scan-original", "T1.nii.gz")
        val legacyNote = note("Older observation", source.id).copy(sourceFingerprint = null)

        val archived = ScanNoteArchiveCodec.decode(
            ScanNoteArchiveCodec.encode(listOf(legacyNote), listOf(source), exportedAtMillis = 42L),
        ).notes.single()

        assertNull(archived.sourceFingerprint)
        assertEquals(source.displayName, archived.sourceDisplayName)
    }

    @Test
    fun unsupportedVersionsAndInvalidNoteLocationsAreRejectedBeforeImport() {
        val json = ScanNoteArchiveCodec.encode(listOf(note("Observation", "scan-a")), emptyList(), 42L)
        val wrongVersion = json.replace("\"formatVersion\":1", "\"formatVersion\":99")
        val invalidLocation = json.replace("\"sliceIndex\":23", "\"sliceIndex\":999")

        assertTrue(runCatching { ScanNoteArchiveCodec.decode(wrongVersion) }.isFailure)
        assertTrue(runCatching { ScanNoteArchiveCodec.decode(invalidLocation) }.isFailure)
    }

    @Test
    fun exportRejectsUtf8PayloadsThatExceedTheImportByteLimit() {
        val notes = (0 until 66).map { index ->
            note("é".repeat(65_000), "scan-$index").copy(id = "note-$index")
        }

        assertTrue(runCatching { ScanNoteArchiveCodec.encode(notes, emptyList(), 42L) }.isFailure)
    }

    @Test
    fun importSkipsExactDuplicatesAndPreservesIdConflictsWithNewIds() {
        val source = scan("scan-a", "T1.nii")
        val original = note("Existing observation", source.id)
        val conflict = original.copy(body = "Different imported observation")
        val archive = ScanNoteArchiveCodec.decode(
            ScanNoteArchiveCodec.encode(listOf(original, conflict), listOf(source), 42L),
        )

        val result = ScanNoteArchiveCodec.merge(
            existing = listOf(original),
            archive = archive,
            currentScans = listOf(source),
            idFactory = { "conflict-copy" },
        )

        assertEquals(1, result.duplicatesSkipped)
        assertEquals(1, result.conflictsPreserved)
        assertEquals(1, result.imported)
        assertEquals(setOf("Existing observation", "Different imported observation"),
            result.notes.map { it.body }.toSet())
        assertNotEquals(original.id, result.notes.single { it.body == conflict.body }.id)
    }

    @Test
    fun duplicateBackupIsSkippedWhenItsOriginalScanIsNoLongerInTheCatalog() {
        val original = note("Existing observation", "removed-scan")
        val archive = ScanNoteArchiveCodec.decode(
            ScanNoteArchiveCodec.encode(listOf(original), emptyList(), 42L),
        )

        val result = ScanNoteArchiveCodec.merge(
            existing = listOf(original),
            archive = archive,
            currentScans = emptyList(),
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.duplicatesSkipped)
        assertEquals(listOf(original), result.notes)
    }

    @Test
    fun aMatchingMetadataFingerprintDoesNotRelinkAcrossCatalogIds() {
        val originalScan = scan("old-catalog-id", "T1.nii")
        val restoredScan = scan("new-catalog-id", "T1.nii")
        val note = note("Imported note", originalScan.id).copy(
            sourceFingerprint = ScanNoteArchiveCodec.fingerprint(originalScan),
            sourceDisplayName = originalScan.displayName,
        )
        val archive = ScanNoteArchiveCodec.decode(
            ScanNoteArchiveCodec.encode(listOf(note), listOf(originalScan), 42L),
        )

        val result = ScanNoteArchiveCodec.merge(
            existing = emptyList(),
            archive = archive,
            currentScans = listOf(restoredScan),
            idFactory = { "restored-note" },
        )

        assertEquals(1, result.awaitingRelink)
        assertFalse(result.notes.single().scanId in setOf(restoredScan.id))
        assertEquals(originalScan.id, result.notes.single().archiveSourceScanId)
        assertEquals(ScanNoteArchiveCodec.fingerprint(originalScan), result.notes.single().sourceFingerprint)
    }

    @Test
    fun localCatalogIdAndFingerprintTogetherAllowSameInstallRestore() {
        val source = scan("scan-a", "T1.nii")
        val note = note("Observation", source.id).copy(
            sourceFingerprint = ScanNoteArchiveCodec.fingerprint(source),
            sourceDisplayName = source.displayName,
        )
        val archive = ScanNoteArchiveCodec.decode(
            ScanNoteArchiveCodec.encode(listOf(note), listOf(source), 42L),
        )

        val result = ScanNoteArchiveCodec.merge(emptyList(), archive, listOf(source))

        assertEquals(0, result.awaitingRelink)
        assertEquals(source.id, result.notes.single().scanId)
    }

    @Test
    fun missingRawTransformFingerprintNeverAutoRelinksEvenWhenCatalogIdMatches() {
        val sourceWithTransform = scan("scan-a", "T1.nii")
        val source = sourceWithTransform.copy(
            metadata = sourceWithTransform.metadata.copy(transformHeaderFingerprint = null),
        )
        val archive = ScanNoteArchiveCodec.decode(
            ScanNoteArchiveCodec.encode(listOf(note("Observation", source.id)), listOf(source), 42L),
        )

        val result = ScanNoteArchiveCodec.merge(emptyList(), archive, listOf(source))

        assertEquals(1, result.awaitingRelink)
        assertNull(result.notes.single().sourceFingerprint)
    }

    private fun scan(id: String, name: String) = ScanRecord(
        id = id,
        collectionId = "collection-a",
        documentUri = "content://private-provider/$id",
        displayName = name,
        metadata = NiftiSeriesMetadata(
            dimensions = listOf(128, 128, 64),
            spacingHeaderUnits = listOf(0.5, 0.5, 3.0),
            timepoints = 1,
            dataType = "float32",
            orientation = "sform",
            description = "synthetic test",
            spatialUnits = NiftiSpatialUnits.MILLIMETER,
            sformCode = 1,
            transformHeaderFingerprint = "sha256:" + "a".repeat(64),
        ),
    )

    private fun note(body: String, scanId: String) = ScanNote(
        id = "note-a",
        scanId = scanId,
        collectionLabel = "Imported set",
        seriesLabel = "T1 axial",
        planeName = "AXIAL",
        sliceIndex = 23,
        sliceCount = 64,
        createdAtMillis = 1_797_000_000_000L,
        body = body,
        spatialTransformPresent = true,
    )
}
