package com.marcusfunt.neuroview.conversion

import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.mri.SpatialAffine
import kotlin.math.abs
import kotlin.math.sqrt

data class DicomSeries(
    val slices: List<DicomSlice>,
    val affine: SpatialAffine,
) {
    val nx: Int get() = slices.first().columns
    val ny: Int get() = slices.first().rows
    val nz: Int get() = slices.size
}

/** Group and sort one DICOM series; never combine studies or inconsistent acquisitions. */
object DicomSeriesAssembler {
    fun assemble(files: List<DicomSlice>): DicomSeries {
        require(files.isNotEmpty() && files.size <= 1024) { "Select 1–1024 DICOM slices" }
        val first = files.first()
        val pixels = first.rows.toLong() * first.columns * files.size
        require(pixels in 1..NiftiReader.MAX_VOXELS) { "DICOM series too large for this device" }
        val a = first.orientationLps
        val iVector = a.copyOfRange(0, 3)
        val jVector = a.copyOfRange(3, 6)
        val normal = cross(iVector, jVector)
        fun projected(position: DoubleArray) = position.indices.sumOf { position[it] * normal[it] }
        for (slice in files) {
            require(slice.studyUid == first.studyUid && slice.seriesUid == first.seriesUid) {
                "Selected DICOM files belong to different studies or series"
            }
            require(slice.rows == first.rows && slice.columns == first.columns) {
                "DICOM rows or columns differ across slices"
            }
            require(slice.pixelSpacing.indices.all {
                abs(slice.pixelSpacing[it] - first.pixelSpacing[it]) <= 1e-4
            }) { "Inconsistent DICOM pixel spacing" }
            require((0..5).all {
                abs(slice.orientationLps[it] - a[it]) <= 0.001
            }) { "Inconsistent DICOM image orientation" }
        }
        val sorted = files.sortedBy { projected(it.positionLps) }
        val start = sorted.first().positionLps
        val step = if (sorted.size == 1) normal.map {
            it * first.sliceSpacing
        }.toDoubleArray() else DoubleArray(3) { index ->
            (sorted.last().positionLps[index] - start[index]) / (sorted.size - 1)
        }
        val stepProjection = dot(step, normal)
        require(stepProjection > 1e-4) { "Duplicate or unordered DICOM slice positions" }
        val tolerance = maxOf(0.20, stepProjection * 0.05)
        sorted.forEachIndexed { index, slice ->
            val expected = DoubleArray(3) { axis -> start[axis] + index * step[axis] }
            val deviation = sqrt((0..2).sumOf {
                val delta = slice.positionLps[it] - expected[it]
                delta * delta
            })
            require(deviation <= tolerance) {
                "Irregular or missing DICOM slices; separate this acquisition before converting"
            }
        }
        // DICOM patient LPS (x left, y posterior, z superior) -> NIfTI RAS+
        fun ras(v: DoubleArray) = doubleArrayOf(-v[0], -v[1], v[2])
        val origin = ras(start)
        val x = ras(iVector.map { it * first.pixelSpacing[1] }.toDoubleArray())
        val y = ras(jVector.map { it * first.pixelSpacing[0] }.toDoubleArray())
        val z = ras(step)
        val affine = SpatialAffine.fromRows(
            doubleArrayOf(
                x[0], y[0], z[0], origin[0],
                x[1], y[1], z[1], origin[1],
                x[2], y[2], z[2], origin[2],
            ),
        )
        return DicomSeries(sorted, affine)
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = (0..2).sumOf { a[it] * b[it] }

    private fun cross(a: DoubleArray, b: DoubleArray): DoubleArray = doubleArrayOf(
        a[1]*b[2] - a[2]*b[1],
        a[2]*b[0] - a[0]*b[2],
        a[0]*b[1] - a[1]*b[0],
    )
}
