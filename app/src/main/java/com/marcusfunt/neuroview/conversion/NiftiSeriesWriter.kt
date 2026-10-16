package com.marcusfunt.neuroview.conversion

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream

/**
 * Converts assembled DICOM slices to a scalar float32, single-file NIfTI-1 .nii.gz.
 * Pixel data is streamed from the sorted series; it does not allocate another 3D volume.
 * All original DICOM patient identity/acquisition fields are deliberately omitted.
 */
object NiftiSeriesWriter {
    fun writeGz(series: DicomSeries, destination: OutputStream) {
        val header = ByteBuffer.allocate(352).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0, 348)
        header.putShort(40, 3)                        // 3 spatial dimensions
        header.putShort(42, series.nx.toShort())
        header.putShort(44, series.ny.toShort())
        header.putShort(46, series.nz.toShort())
        header.putShort(48, 1)
        header.putShort(50, 1)
        header.putShort(70, 16)                       // float32
        header.putShort(72, 32)
        header.putFloat(76, 1f)
        for (i in 0..2) {
            header.putFloat(80 + i * 4, series.affine.axisSpacing(i).toFloat())
        }
        header.putFloat(108, 352f)
        header.putShort(254, 1)                      // sform in RAS+
        header.put(123, 2)                           // mm units
        val description = "NeuroView DICOM-derived NIfTI"
            .toByteArray(Charsets.US_ASCII)
        description.copyInto(header.array(), destinationOffset = 148)
        val o = series.affine.world(0.0, 0.0, 0.0)
        val i = series.affine.world(1.0, 0.0, 0.0)
        val j = series.affine.world(0.0, 1.0, 0.0)
        val k = series.affine.world(0.0, 0.0, 1.0)
        for (row in 0..2) {
            header.putFloat(280 + row * 16, (i[row] - o[row]).toFloat())
            header.putFloat(284 + row * 16, (j[row] - o[row]).toFloat())
            header.putFloat(288 + row * 16, (k[row] - o[row]).toFloat())
            header.putFloat(292 + row * 16, o[row].toFloat())
        }
        header.put(344, 'n'.code.toByte())
        header.put(345, '+'.code.toByte())
        header.put(346, '1'.code.toByte())
        header.put(347, 0)

        GZIPOutputStream(BufferedOutputStream(destination, 64 * 1024), 64 * 1024).use { out ->
            out.write(header.array())
            val bytes = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
            for (slice in series.slices) {
                for (pixel in slice.pixels) {
                    if (bytes.remaining() < 4) {
                        out.write(bytes.array(), 0, bytes.position())
                        bytes.clear()
                    }
                    bytes.putFloat(pixel)
                }
            }
            if (bytes.position() > 0) out.write(bytes.array(), 0, bytes.position())
            out.finish()
        }
    }
}
