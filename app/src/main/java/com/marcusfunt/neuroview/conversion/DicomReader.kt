package com.marcusfunt.neuroview.conversion

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Bounded DICOM Part 10 single-frame grayscale reader. Supports ONLY native
 * Implicit VR LE and Explicit VR LE transfer syntaxes. Encapsulated/compressed
 * pixel data, sequences with undefined length, multiframe and color are rejected.
 * Does not retain PHI: patient names/IDs are never parsed or copied into output.
 */
data class DicomSlice(
    val studyUid: String,
    val seriesUid: String,
    val rows: Int,
    val columns: Int,
    val positionLps: DoubleArray,
    /** Direction cosines: first triplet = increasing column, second = increasing row. */
    val orientationLps: DoubleArray,
    /** [row spacing, column spacing] in mm. */
    val pixelSpacing: DoubleArray,
    val sliceSpacing: Double,
    val pixels: FloatArray,
)

object DicomReader {
    private const val TS_IMPLICIT_LE = "1.2.840.10008.1.2"
    private const val TS_EXPLICIT_LE = "1.2.840.10008.1.2.1"
    private const val TAG_PIXEL_DATA = 0x7FE00010
    private const val MAX_ELEMENT = 128 * 1024 * 1024L
    private val longVr = setOf("OB", "OD", "OF", "OL", "OV", "OW", "SQ", "UC", "UR", "UT", "UN", "SV", "UV")
    private val textTags = setOf(
        0x00080016, 0x00080060, 0x0020000D, 0x0020000E,
        0x00200032, 0x00200037, 0x00280004, 0x00280008,
        0x00280030, 0x00180050, 0x00180088, 0x00281052, 0x00281053,
    )
    private val numericTags = setOf(
        0x00280002, 0x00280010, 0x00280011,
        0x00280100, 0x00280101, 0x00280102, 0x00280103,
    )

    fun read(input: InputStream): DicomSlice {
        val stream = DataInputStream(BufferedInputStream(input, 64 * 1024))
        val preamble = ByteArray(132)
        stream.readFully(preamble)
        require(preamble.copyOfRange(128, 132).contentEquals("DICM".toByteArray(Charsets.US_ASCII))) {
            "Expected a DICOM Part 10 file with DICM preamble"
        }
        var transferSyntax = ""
        val data = mutableMapOf<Int, ByteArray>()
        var firstDatasetTag: Int? = null
        while (true) {
            val tag = readTag(stream) ?: break
            if (tag ushr 16 != 0x0002) {
                firstDatasetTag = tag
                break
            }
            val bytes = readElement(stream, true, tag, keep = tag == 0x00020010)
            if (tag == 0x00020010 && bytes != null) transferSyntax = asText(bytes)
        }
        require(transferSyntax == TS_IMPLICIT_LE || transferSyntax == TS_EXPLICIT_LE) {
            "Unsupported or compressed transfer syntax: ${transferSyntax.ifBlank { "missing" }}"
        }
        val explicit = transferSyntax == TS_EXPLICIT_LE
        var nextTag = firstDatasetTag
        while (nextTag != null) {
            val tag = nextTag
            if (tag == TAG_PIXEL_DATA || tag in textTags || tag in numericTags) {
                val element = readElement(stream, explicit, tag, keep = true)
                if (element != null) data[tag] = element
                if (tag == TAG_PIXEL_DATA) break
            } else {
                readElement(stream, explicit, tag, keep = false)
            }
            nextTag = readTag(stream)
        }

        fun requiredString(tag: Int, label: String): String =
            data[tag]?.let(::asText)?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("DICOM missing $label")
        fun numbers(tag: Int, expected: Int, label: String): DoubleArray {
            val array = requiredString(tag, label).split('\\').map {
                it.trim().toDoubleOrNull() ?: throw IllegalArgumentException("Invalid $label")
            }.toDoubleArray()
            require(array.size == expected && array.all { it.isFinite() }) { "Invalid $label" }
            return array
        }
        fun us(tag: Int, name: String): Int {
            val bytes = data[tag] ?: throw IllegalArgumentException("DICOM missing $name")
            require(bytes.size == 2) { "Invalid $name" }
            return (bytes[0].toInt() and 255) or ((bytes[1].toInt() and 255) shl 8)
        }
        val rows = us(0x00280010, "Rows")
        val columns = us(0x00280011, "Columns")
        require(rows in 1..4096 && columns in 1..4096) { "Invalid DICOM image size" }
        require(us(0x00280002, "SamplesPerPixel") == 1) { "Only monochrome DICOM supported" }
        require(requiredString(0x00280004, "PhotometricInterpretation") == "MONOCHROME2") {
            "Only MONOCHROME2 DICOM supported"
        }
        require(data[0x00280008]?.let(::asText)?.toIntOrNull() ?: 1 == 1) {
            "Multiframe DICOM is not supported"
        }
        val bits = us(0x00280100, "BitsAllocated")
        val stored = us(0x00280101, "BitsStored")
        val highBit = us(0x00280102, "HighBit")
        val signed = us(0x00280103, "PixelRepresentation")
        require(bits in listOf(8, 16) && stored in 1..bits && highBit == stored - 1 &&
            signed in 0..1) { "Unsupported DICOM pixel packing" }
        val pixel = data[TAG_PIXEL_DATA] ?: throw IllegalArgumentException("Missing DICOM PixelData")
        val needed = rows.toLong() * columns * (bits / 8)
        require(needed <= MAX_ELEMENT && pixel.size.toLong() in needed..(needed + 1)) {
            "DICOM PixelData does not match image dimensions"
        }
        val slope = data[0x00281053]?.let(::asText)?.toDoubleOrNull() ?: 1.0
        val intercept = data[0x00281052]?.let(::asText)?.toDoubleOrNull() ?: 0.0
        require(slope.isFinite() && slope != 0.0 && intercept.isFinite()) {
            "Invalid DICOM rescale values"
        }
        val mask = (1 shl stored) - 1
        val signBit = 1 shl (stored - 1)
        val values = FloatArray(rows * columns)
        for (idx in values.indices) {
            val raw = if (bits == 8) pixel[idx].toInt() and 255 else {
                val offset = idx * 2
                (pixel[offset].toInt() and 255) or ((pixel[offset + 1].toInt() and 255) shl 8)
            } and mask
            val number = if (signed == 1 && raw and signBit != 0) raw - (1 shl stored) else raw
            val scaled = number.toDouble() * slope + intercept
            require(scaled.isFinite() && abs(scaled) <= Float.MAX_VALUE) {
                "Unrepresentable DICOM intensity"
            }
            values[idx] = scaled.toFloat()
        }

        val spacing = numbers(0x00280030, 2, "PixelSpacing")
        require(spacing.all { it > 0.0 }) { "Invalid PixelSpacing" }
        val orientation = numbers(0x00200037, 6, "ImageOrientationPatient")
        val position = numbers(0x00200032, 3, "ImagePositionPatient")
        fun length(from: Int) = kotlin.math.sqrt((0..2).sumOf { axis ->
            orientation[from + axis] * orientation[from + axis]
        })
        val dot = (0..2).sumOf { orientation[it] * orientation[3 + it] }
        require(abs(length(0) - 1.0) < 0.001 && abs(length(3) - 1.0) < 0.001 &&
            abs(dot) < 0.001) { "Invalid DICOM orientation vectors" }
        val spacingZ = listOf(0x00180088, 0x00180050).firstNotNullOfOrNull {
            data[it]?.let(::asText)?.toDoubleOrNull()?.takeIf { n -> n > 0 && n.isFinite() }
        } ?: 1.0

        return DicomSlice(
            studyUid = requiredString(0x0020000D, "StudyInstanceUID"),
            seriesUid = requiredString(0x0020000E, "SeriesInstanceUID"),
            rows = rows, columns = columns,
            positionLps = position, orientationLps = orientation,
            pixelSpacing = spacing, sliceSpacing = spacingZ, pixels = values,
        )
    }

    private fun asText(bytes: ByteArray) =
        bytes.toString(Charsets.US_ASCII).trim(' ', '\u0000', '\r', '\n')

    private fun readTag(stream: DataInputStream): Int? {
        val first = stream.read()
        if (first < 0) return null
        val second = stream.readUnsignedByte()
        val third = stream.readUnsignedByte()
        val fourth = stream.readUnsignedByte()
        val group = first or (second shl 8)
        val element = third or (fourth shl 8)
        return (group shl 16) or element
    }

    private fun u16(stream: DataInputStream): Int =
        stream.readUnsignedByte() or (stream.readUnsignedByte() shl 8)
    private fun u32(stream: DataInputStream): Long =
        u16(stream).toLong() or (u16(stream).toLong() shl 16)

    private fun readElement(
        stream: DataInputStream, explicit: Boolean, tag: Int, keep: Boolean,
    ): ByteArray? {
        val length = if (explicit) {
            val a = stream.readUnsignedByte().toChar()
            val b = stream.readUnsignedByte().toChar()
            val vr = "$a$b"
            if (vr in longVr) {
                u16(stream) // reserved
                u32(stream)
            } else u16(stream).toLong()
        } else u32(stream)
        require(length != 0xffffffffL && length <= MAX_ELEMENT) {
            "Unsupported DICOM element length or sequence at ${tag.toUInt().toString(16)}"
        }
        if (keep) {
            val bytes = ByteArray(length.toInt())
            stream.readFully(bytes)
            return bytes
        }
        var remaining = length
        val skip = ByteArray(8192)
        while (remaining > 0L) {
            val n = minOf(remaining, skip.size.toLong()).toInt()
            stream.readFully(skip, 0, n)
            remaining -= n
        }
        return null
    }
}
