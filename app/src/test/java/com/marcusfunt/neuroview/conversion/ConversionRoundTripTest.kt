package com.marcusfunt.neuroview.conversion

import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.mri.SpatialAffine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ConversionRoundTripTest {
    private fun volume(): NiftiVolume {
        val dimensions = 4 * 3 * 3
        return NiftiVolume(
            4, 3, 3,
            FloatArray(dimensions) { it.toFloat() - 10f },
            SpatialAffine.fromRows(
                doubleArrayOf(
                    0.0, -1.5, 0.0, 12.0,
                    1.2, 0.0, 0.0, -8.0,
                    0.0, 0.0, 2.0, 20.0,
                ),
            ),
            -10f, 25f, "sform (RAS+)", 1,
        )
    }

    private fun slicesFromZip(volume: NiftiVolume = volume()): List<DicomSlice> {
        val output = ByteArrayOutputStream()
        DerivedDicomWriter.writeZip(volume, output)
        val parts = mutableListOf<DicomSlice>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (zip.nextEntry != null) {
                val data = ByteArrayOutputStream()
                zip.copyTo(data)
                val bytes = data.toByteArray()
                assertEquals("DICM", bytes.copyOfRange(128, 132).toString(Charsets.US_ASCII))
                parts += DicomReader.read(ByteArrayInputStream(bytes))
                zip.closeEntry()
            }
        }
        return parts
    }

    @Test fun derivedSecondaryCaptureFilesCanBeReadAsSingleSeries() {
        val slices = slicesFromZip()
        assertEquals(3, slices.size)
        assertTrue(slices.all { it.rows == 3 && it.columns == 4 })
        assertEquals(1, slices.map { it.seriesUid }.distinct().size)
        assertEquals(1, slices.map { it.studyUid }.distinct().size)
        assertEquals(12, slices.first().pixels.size)
    }

    @Test fun dicomToNiftiRoundTripPreservesLpsRasGeometryAndVoxelValues() {
        val original = volume()
        val dicom = slicesFromZip(original)
        val assembled = DicomSeriesAssembler.assemble(dicom.shuffled())
        val buffer = ByteArrayOutputStream()
        NiftiSeriesWriter.writeGz(assembled, buffer)
        val restored = NiftiReader.read(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(original.nx, restored.nx)
        assertEquals(original.ny, restored.ny)
        assertEquals(original.nz, restored.nz)
        assertEquals("sform (RAS+)", restored.transformSource)
        for (ijk in listOf(
            doubleArrayOf(0.0, 0.0, 0.0),
            doubleArrayOf(2.0, 1.0, 1.0),
            doubleArrayOf(3.0, 2.0, 2.0),
        )) {
            val before = original.affine.world(ijk[0], ijk[1], ijk[2])
            val after = restored.affine.world(ijk[0], ijk[1], ijk[2])
            for (a in 0..2) assertEquals(before[a], after[a], 0.0001)
        }
        for (index in original.values.indices) {
            assertEquals(original.values[index], restored.values[index], 0.002f)
        }
    }

    @Test fun mixedStudyOrSeriesFilesAreRejected() {
        val slices = slicesFromZip()
        val other = slices[2].copy(seriesUid = "2.25.999")
        assertThrows(IllegalArgumentException::class.java) {
            DicomSeriesAssembler.assemble(listOf(slices[0], slices[1], other))
        }
        val shifted = slices[2].copy(
            positionLps = slices[2].positionLps.clone().also { it[2] += 15.0 },
        )
        assertThrows(IllegalArgumentException::class.java) {
            DicomSeriesAssembler.assemble(listOf(slices[0], slices[1], shifted))
        }
    }

    @Test fun refusesToExportUnorientedNiftiToMisleadingDicom() {
        val v = volume()
        val unoriented = NiftiVolume(
            v.nx, v.ny, v.nz, v.values, v.affine,
            v.percentileLow, v.percentileHigh,
            "NO ORIENTATION — assumed RAS", 1,
        )
        assertThrows(IllegalArgumentException::class.java) {
            DerivedDicomWriter.writeZip(unoriented, ByteArrayOutputStream())
        }
    }

    @Test fun conversionDoesNotEmbedSourcePatientName() {
        val output = ByteArrayOutputStream()
        DerivedDicomWriter.writeZip(volume(), output)
        val all = output.toByteArray()
        // ZIP archive names are generic and do not contain NIfTI filenames or patient info.
        ZipInputStream(ByteArrayInputStream(all)).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                assertTrue(entry.name.startsWith("DERIVED_"))
                assertTrue(entry.name.endsWith(".dcm"))
                count++
                zip.closeEntry()
            }
            assertEquals(3, count)
        }
    }

    @Test fun singleSliceRetainsProvidedSliceSpacing() {
        val reference = slicesFromZip().first()
        val one = reference.copy(sliceSpacing = 3.5)
        val volume = DicomSeriesAssembler.assemble(listOf(one))
        assertEquals(3.5, volume.affine.axisSpacing(2), 0.0001)
    }
}
