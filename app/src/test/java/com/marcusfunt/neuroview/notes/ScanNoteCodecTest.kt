package com.marcusfunt.neuroview.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Base64

class ScanNoteCodecTest {
    @Test
    fun roundTripsFieldsWithUnicodeAndNewlines() {
        val note = createNote("midline note | check\nSecond line — cerveau 🧠").copy(nativeStackAxis = 2)
        val encoded = ScanNoteCodec.encode(listOf(note))
        val restored = ScanNoteCodec.decode(encoded)

        assertEquals(listOf(note), restored)
    }

    @Test
    fun skipsMalformedRecordsButKeepsValidNotes() {
        val note = createNote("Valid observation")
        val encoded = ScanNoteCodec.encode(listOf(note))
        val restored = ScanNoteCodec.decode("$encoded\nbroken|record")

        assertEquals(listOf(note), restored)
    }

    @Test
    fun oldRecordsKeepUnknownOrientationAfterMigration() {
        fun text(value: String) = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        val v1 = listOf(
            "v1", text("old-note"), text("scan-2"), text("Set"), text("T1"), text("AXIAL"),
            "4", "10", "1797000000000", text("Older observation"),
        ).joinToString("|")

        val restored = ScanNoteCodec.decode(v1).single()
        assertEquals("Older observation", restored.body)
        assertFalse(restored.spatialTransformPresent)
    }

    @Test
    fun versionTwoRecordsRemainReadableWithoutNativeGridLocation() {
        fun text(value: String) = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        val v2 = listOf(
            "v2", text("old-note"), text("scan-2"), text("Set"), text("T1"), text("AXIAL"),
            "4", "10", "1797000000000", "true", text("Older observation"),
        ).joinToString("|")

        val restored = ScanNoteCodec.decode(v2).single()
        assertEquals("Older observation", restored.body)
        assertEquals(null, restored.nativeStackAxis)
        assertEquals(true, restored.spatialTransformPresent)
    }

    @Test
    fun notesKeepTheNativeStackAxisWhenSaved() {
        val note = createNote("Oblique source slice").copy(nativeStackAxis = 1)

        assertEquals(note, ScanNoteCodec.decode(ScanNoteCodec.encode(listOf(note))).single())
    }

    private fun createNote(body: String) = ScanNote(
        id = "note-1",
        scanId = "scan-2",
        collectionLabel = "Imported set",
        seriesLabel = "T1 axial",
        planeName = "AXIAL",
        sliceIndex = 23,
        sliceCount = 82,
        createdAtMillis = 1_797_000_000_000L,
        body = body,
    )
}
