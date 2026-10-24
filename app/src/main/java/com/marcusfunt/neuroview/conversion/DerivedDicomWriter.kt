package com.marcusfunt.neuroview.conversion

import com.marcusfunt.neuroview.mri.NiftiVolume
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Exports DERIVED Secondary Capture (SC) DICOM Part 10 slices in a ZIP.
 * NOT an original MR DICOM reconstruction: scanner/patient/acquisition information
 * absent from NIfTI cannot be restored. Data is linearly mapped to unsigned 16-bit.
 * Output metadata is regenerated and some patient fields are blank; this is not an
 * anonymization guarantee, since identifiable information may remain in image pixels.
 */
object DerivedDicomWriter {
    private const val SC_UID = "1.2.840.10008.5.1.4.1.1.7"
    private const val EXPLICIT_LE_UID = "1.2.840.10008.1.2.1"
    private const val IMPLEMENTATION_UID = "2.25.180848462823764534621857403973065946951"
    private val longVr = setOf("OB", "OD", "OF", "OL", "OV", "OW", "SQ", "UC", "UR", "UT", "UN", "SV", "UV")

    fun writeZip(
        volume: NiftiVolume,
        destination: OutputStream,
        onProgress: (completedSlices: Int, totalSlices: Int) -> Unit = { _, _ -> },
        cancellationCheck: () -> Unit = {},
    ) {
        cancellationCheck()
        require(!volume.transformSource.startsWith("NO ORIENTATION")) {
            "Cannot write spatial DICOM when the NIfTI orientation is unknown"
        }
        require(volume.nx in 1..4096 && volume.ny in 1..4096 && volume.nz in 1..2048) {
            "Unsupported volume dimensions for derived DICOM"
        }

        val affine = volume.affine
        val origin = affine.world(0.0, 0.0, 0.0)
        val alongI = affine.world(1.0, 0.0, 0.0).zip(origin) { x, o -> x - o }.toDoubleArray()
        val alongJ = affine.world(0.0, 1.0, 0.0).zip(origin) { x, o -> x - o }.toDoubleArray()
        val spacingCol = sqrt(alongI.sumOf { it * it })
        val spacingRow = sqrt(alongJ.sumOf { it * it })
        val alignment = alongI.indices.sumOf { alongI[it] * alongJ[it] } /
            (spacingCol * spacingRow)
        require(spacingCol > 0 && spacingRow > 0 && abs(alignment) < 0.001) {
            "In-plane NIfTI affine is sheared; cannot encode as orthogonal DICOM orientation"
        }
        // RAS+ -> LPS for patient coordinates and direction cosines.
        fun lps(vector: DoubleArray) = doubleArrayOf(-vector[0], -vector[1], vector[2])
        val rowDirection = lps(alongI.map { it / spacingCol }.toDoubleArray())
        val colDirection = lps(alongJ.map { it / spacingRow }.toDoubleArray())
        val orientation = rowDirection + colDirection
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        val voxelCount = volume.nx.toLong() * volume.ny * volume.nz
        for (index in 0 until voxelCount) {
            if (index % 65_536L == 0L) cancellationCheck()
            val v = volume.valueAt(index)
            if (v.isFinite()) {
                if (v < min) min = v
                if (v > max) max = v
            }
        }
        require(min.isFinite() && max.isFinite()) { "No finite voxel intensities" }
        val range = (max.toDouble() - min.toDouble()).coerceAtLeast(1e-12)
        val slope = range / 65535.0
        val study = newUid()
        val series = newUid()
        val frame = newUid()
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val time = SimpleDateFormat("HHmmss", Locale.US).format(Date())
        val rowSpacing = decimal(spacingRow)
        val colSpacing = decimal(spacingCol)
        val sequence = ZipOutputStream(destination.buffered(65536))
        sequence.use { zip ->
            for (k in 0 until volume.nz) {
                cancellationCheck()
                val sop = newUid()
                zip.putNextEntry(ZipEntry("DERIVED_${(k + 1).toString().padStart(4, '0')}.dcm"))
                val point = lps(affine.world(0.0, 0.0, k.toDouble()))
                writePart10(
                    zip = zip, volume = volume, slice = k, sop = sop, study = study,
                    series = series, frame = frame,
                    date = date, time = time, orientation = orientation,
                    position = point, rowSpacing = rowSpacing, colSpacing = colSpacing,
                    minimum = min.toDouble(), slope = slope, cancellationCheck = cancellationCheck,
                )
                zip.closeEntry()
                zip.flush()
                onProgress(k + 1, volume.nz)
            }
            zip.finish()
        }
    }

    private fun writePart10(
        zip: OutputStream,
        volume: NiftiVolume, slice: Int,
        sop: String, study: String, series: String, frame: String,
        date: String, time: String,
        orientation: DoubleArray, position: DoubleArray,
        rowSpacing: String, colSpacing: String,
        minimum: Double, slope: Double,
        cancellationCheck: () -> Unit,
    ) {
        zip.write(ByteArray(128))
        zip.write("DICM".toByteArray(Charsets.US_ASCII))
        val meta = ByteArrayOutputStream()
        write(meta, 0x00020001, "OB", byteArrayOf(0, 1))
        write(meta, 0x00020002, "UI", SC_UID)
        write(meta, 0x00020003, "UI", sop)
        write(meta, 0x00020010, "UI", EXPLICIT_LE_UID)
        write(meta, 0x00020012, "UI", IMPLEMENTATION_UID)
        write(meta, 0x00020013, "SH", "NEUROVIEW_06")
        write(zip, 0x00020000, "UL", littleInt(meta.size()))
        zip.write(meta.toByteArray())

        write(zip, 0x00080008, "CS", "DERIVED\\SECONDARY")
        write(zip, 0x00080016, "UI", SC_UID)
        write(zip, 0x00080018, "UI", sop)
        write(zip, 0x00080020, "DA", date)
        write(zip, 0x00080021, "DA", date)
        write(zip, 0x00080023, "DA", date)
        write(zip, 0x00080030, "TM", time)
        write(zip, 0x00080031, "TM", time)
        write(zip, 0x00080033, "TM", time)
        write(zip, 0x00080050, "SH", "")
        write(zip, 0x00080060, "CS", "OT")
        write(zip, 0x00080064, "CS", "WSD") // workstation conversion
        write(zip, 0x00080070, "LO", "NeuroView")
        write(zip, 0x00081030, "LO", "NIfTI-derived images")
        write(zip, 0x0008103E, "LO", "DERIVED SECONDARY CAPTURE")
        write(zip, 0x00082111, "ST", "Converted from NIfTI. Not original acquisition DICOM.")
        write(zip, 0x00100010, "PN", "") // No identifying patient information
        write(zip, 0x00100020, "LO", "")
        write(zip, 0x00100030, "DA", "")
        write(zip, 0x00100040, "CS", "")
        write(zip, 0x00181020, "LO", "NeuroView 0.6")
        write(zip, 0x0020000D, "UI", study)
        write(zip, 0x0020000E, "UI", series)
        write(zip, 0x00200011, "IS", "1")
        write(zip, 0x00200013, "IS", (slice + 1).toString())
        write(zip, 0x00200032, "DS", position.joinToString("\\") { decimal(it) })
        write(zip, 0x00200037, "DS", orientation.joinToString("\\") { decimal(it) })
        write(zip, 0x00200052, "UI", frame)
        write(zip, 0x00280002, "US", littleShort(1))
        write(zip, 0x00280004, "CS", "MONOCHROME2")
        write(zip, 0x00280010, "US", littleShort(volume.ny))
        write(zip, 0x00280011, "US", littleShort(volume.nx))
        write(zip, 0x00280030, "DS", "$rowSpacing\\$colSpacing")
        write(zip, 0x00280100, "US", littleShort(16))
        write(zip, 0x00280101, "US", littleShort(16))
        write(zip, 0x00280102, "US", littleShort(15))
        write(zip, 0x00280103, "US", littleShort(0))
        write(zip, 0x00281052, "DS", decimal(minimum))
        write(zip, 0x00281053, "DS", decimal(slope))
        // Raw pixel values use column-fastest NIfTI indexing in the original voxel plane.
        val packed = ByteBuffer.allocate(volume.nx * volume.ny * 2).order(ByteOrder.LITTLE_ENDIAN)
        val offset = slice.toLong() * volume.nx * volume.ny
        for (pixel in 0 until volume.nx * volume.ny) {
            if (pixel % 8192 == 0) cancellationCheck()
            val raw = volume.valueAt(offset + pixel)
            val normalized = if (!raw.isFinite()) 0 else {
                ((raw.toDouble() - minimum) / slope).roundToInt().coerceIn(0, 65535)
            }
            packed.putShort(normalized.toShort())
        }
        write(zip, 0x7FE00010, "OW", packed.array())
    }

    private fun newUid(): String {
        val uuid = UUID.randomUUID()
        val bits = ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits).array()
        return "2.25." + BigInteger(1, bits).toString()
    }

    private fun decimal(value: Double): String {
        require(value.isFinite()) { "Invalid DICOM decimal value" }
        // DICOM DS value representation: at most 16 ASCII bytes.
        return String.format(Locale.US, "%.8g", value).take(16)
    }
    private fun littleShort(value: Int) =
        byteArrayOf(value.toByte(), (value ushr 8).toByte())
    private fun littleInt(value: Int) =
        byteArrayOf(value.toByte(), (value ushr 8).toByte(),
            (value ushr 16).toByte(), (value ushr 24).toByte())

    private fun write(output: OutputStream, tag: Int, vr: String, text: String) =
        write(output, tag, vr, text.toByteArray(Charsets.US_ASCII))

    private fun write(output: OutputStream, tag: Int, vr: String, source: ByteArray) {
        val padded = if (source.size % 2 == 0) source else source +
            byteArrayOf(if (vr == "UI" || vr == "OB" || vr == "OW") 0 else 32)
        require(padded.size <= Int.MAX_VALUE - 8)
        output.write(littleShort(tag ushr 16))
        output.write(littleShort(tag and 65535))
        output.write(vr.toByteArray(Charsets.US_ASCII))
        if (vr in longVr) {
            output.write(byteArrayOf(0, 0))
            output.write(littleInt(padded.size))
        } else {
            require(padded.size <= 65535) { "DICOM VR too long" }
            output.write(littleShort(padded.size))
        }
        output.write(padded)
    }
}
