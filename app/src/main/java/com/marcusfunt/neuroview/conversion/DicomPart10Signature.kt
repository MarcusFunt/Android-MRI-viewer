package com.marcusfunt.neuroview.conversion

import java.io.InputStream

/** A small signature check so folders can contain sidecar files and extensionless DICOMs. */
object DicomPart10Signature {
    fun matches(input: InputStream): Boolean {
        val header = ByteArray(132)
        var count = 0
        while (count < header.size) {
            val read = input.read(header, count, header.size - count)
            if (read < 0) return false
            if (read == 0) {
                val byte = input.read()
                if (byte < 0) return false
                header[count++] = byte.toByte()
            } else {
                count += read
            }
        }
        return header[128] == 'D'.code.toByte() &&
            header[129] == 'I'.code.toByte() &&
            header[130] == 'C'.code.toByte() &&
            header[131] == 'M'.code.toByte()
    }

    /** Known image extensions without a Part 10 preamble should fail explicitly. */
    fun hasDicomExtension(filename: String): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return extension == "dcm" || extension == "dicom" || extension == "ima"
    }
}
