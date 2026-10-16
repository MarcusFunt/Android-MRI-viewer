package com.marcusfunt.neuroview.qa

import com.marcusfunt.neuroview.conversion.DerivedDicomWriter
import com.marcusfunt.neuroview.conversion.DicomReader
import com.marcusfunt.neuroview.conversion.DicomSeriesAssembler
import com.marcusfunt.neuroview.conversion.NiftiSeriesWriter
import com.marcusfunt.neuroview.mri.NiftiReader
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Interoperability bridge for Python nibabel/pydicom, not a self-referential
 * Android reader-writer round trip. No patient data in these deterministic fixtures.
 */
class ReferenceInteropTest {
    @Test
    fun convertIndependentNiftiAndDicomFixturesForExternalVerification() {
        val target = System.getenv("NEUROVIEW_REFERENCE_FIXTURE_DIR") ?: return
        val folder = File(target)
        assertTrue("Missing reference fixtures; run Python generate step", folder.isDirectory)
        val nibabelFile = File(folder, "reference_oblique.nii.gz")
        val dicomFolder = File(folder, "dicom_input")
        assertTrue("nibabel NIfTI fixture missing", nibabelFile.isFile)
        assertTrue("pydicom DICOM fixture missing", dicomFolder.isDirectory)

        val volume = nibabelFile.inputStream().use { NiftiReader.read(it) }
        assertEquals(7, volume.nx)
        assertEquals(6, volume.ny)
        assertEquals(5, volume.nz)
        val origin = volume.affine.world(0.0, 0.0, 0.0)
        assertEquals(12.0, origin[0], 1e-6)
        assertEquals(-8.0, origin[1], 1e-6)
        assertEquals(20.0, origin[2], 1e-6)
        for (z in 0 until 5) for (y in 0 until 6) for (x in 0 until 7) {
            val expected = x * 17f + y * 3.5f + z * 0.75f - 50f
            assertEquals(expected, volume.nearest(x, y, z), 0.0001f)
        }
        FileOutputStream(File(folder, "android_from_reference_nifti.zip")).use {
            DerivedDicomWriter.writeZip(volume, it)
        }

        val slices = dicomFolder.listFiles()?.filter { it.extension.equals("dcm", true) }
            ?.sortedByDescending { it.name } ?: emptyList()
        assertEquals(5, slices.size)
        val parsed = slices.map { file ->
            file.inputStream().use { DicomReader.read(it) }
        }
        val dicom = DicomSeriesAssembler.assemble(parsed)
        assertEquals(7, dicom.nx)
        assertEquals(6, dicom.ny)
        assertEquals(5, dicom.nz)
        FileOutputStream(File(folder, "android_from_reference_dicom.nii.gz")).use {
            NiftiSeriesWriter.writeGz(dicom, it)
        }
    }
}
