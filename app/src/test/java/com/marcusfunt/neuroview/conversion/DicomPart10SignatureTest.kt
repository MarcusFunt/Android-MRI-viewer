package com.marcusfunt.neuroview.conversion

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DicomPart10SignatureTest {
    private fun part10() = ByteArray(132).also {
        "DICM".toByteArray(Charsets.US_ASCII).copyInto(it, 128)
    }

    @Test fun detectsDicomWithoutAFileExtension() {
        assertTrue(DicomPart10Signature.matches(ByteArrayInputStream(part10())))
    }

    @Test fun detectsDicomEvenWhenStreamReturnsTinyChunks() {
        val slow = object : FilterInputStream(ByteArrayInputStream(part10())) {
            override fun read(bytes: ByteArray, off: Int, len: Int): Int =
                super.read(bytes, off, minOf(len, 3))
        }
        assertTrue(DicomPart10Signature.matches(slow))
    }

    @Test fun rejectsShortFilesAndNonDicomSidecars() {
        assertFalse(DicomPart10Signature.matches(ByteArrayInputStream(ByteArray(131))))
        assertFalse(DicomPart10Signature.matches(ByteArrayInputStream(ByteArray(256))))
    }

    @Test fun warnsAboutNamedDicomFilesWithoutHeaders() {
        assertTrue(DicomPart10Signature.hasDicomExtension("IMG001.DCM"))
        assertTrue(DicomPart10Signature.hasDicomExtension("IMG001.IMA"))
        assertTrue(DicomPart10Signature.hasDicomExtension("abc.dicom"))
        assertFalse(DicomPart10Signature.hasDicomExtension("IMG001"))
        assertFalse(DicomPart10Signature.hasDicomExtension("notes.xml"))
    }
}
