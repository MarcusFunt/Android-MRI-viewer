package com.marcusfunt.neuroview

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import com.marcusfunt.neuroview.mri.NiftiSpatialUnits
import com.marcusfunt.neuroview.notes.ScanNote
import com.marcusfunt.neuroview.notes.ScanNoteArchiveCodec
import com.marcusfunt.neuroview.notes.ScanNoteStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanNoteArchiveStoreTest {
    @Test
    fun archiveMergeAndManualRelinkPersistWithoutRemovingOtherNotes() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedName = "notes_archive_test_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("${isolatedName}_$name", mode)
        }
        val preferences = context.getSharedPreferences("scan_notes_v1", Context.MODE_PRIVATE)

        try {
            val store = ScanNoteStore(context)
            val existing = note("retained", "scan-still-here", "Keep this note")
            val archived = note("restored", "old-scan-id", "Restore this note")
            store.save(existing)
            val archive = ScanNoteArchiveCodec.decode(
                ScanNoteArchiveCodec.encode(listOf(archived), emptyList(), exportedAtMillis = 42L),
            )

            val result = store.mergeArchive(archive, emptyList())

            assertEquals(1, result.imported)
            assertEquals(1, result.awaitingRelink)
            assertEquals(setOf("Keep this note", "Restore this note"), store.load().map { it.body }.toSet())
            val restored = store.load().single { it.id == "restored" }
            val selectedScan = scan()
            assertTrue(store.relink(restored.id, selectedScan))

            val reloaded = ScanNoteStore(context).load()
            assertEquals(2, reloaded.size)
            assertEquals("Keep this note", reloaded.single { it.id == "retained" }.body)
            assertEquals(selectedScan.id, reloaded.single { it.id == "restored" }.scanId)
            assertEquals(ScanNoteArchiveCodec.fingerprint(selectedScan),
                reloaded.single { it.id == "restored" }.sourceFingerprint)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private fun note(id: String, scanId: String, body: String) = ScanNote(
        id = id,
        scanId = scanId,
        collectionLabel = "Test collection",
        seriesLabel = "Synthetic T1",
        planeName = "AXIAL",
        sliceIndex = 4,
        sliceCount = 8,
        createdAtMillis = 42L,
        body = body,
    )

    private fun scan() = ScanRecord(
        id = "restored-scan",
        collectionId = "collection",
        documentUri = "content://provider/private",
        displayName = "T1.nii.gz",
        metadata = NiftiSeriesMetadata(
            dimensions = listOf(8, 8, 8),
            spacingHeaderUnits = listOf(1.0, 1.0, 1.0),
            timepoints = 1,
            dataType = "int16",
            orientation = "sform",
            description = "synthetic test",
            spatialUnits = NiftiSpatialUnits.MILLIMETER,
            sformCode = 1,
            transformHeaderFingerprint = "sha256:" + "a".repeat(64),
        ),
    )
}
