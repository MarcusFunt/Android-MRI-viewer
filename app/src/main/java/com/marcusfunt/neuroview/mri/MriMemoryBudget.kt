package com.marcusfunt.neuroview.mri

/**
 * Approximate conservative Android heap allocation guard for voxel-heavy tasks.
 *
 * One loaded float32 voxel already takes 4 bytes. Allowing 12 heap bytes per voxel
 * leaves headroom for parser buffers, temporary DICOM slice arrays, pixels, and UI.
 * This is not a substitute for OS low-memory handling or a real-device benchmark.
 */
object MriMemoryBudget {
    fun maxVoxels(heapMaxBytes: Long): Long {
        require(heapMaxBytes > 0) { "Invalid device heap budget" }
        return minOf(NiftiReader.MAX_VOXELS, heapMaxBytes / 12L)
            .coerceAtLeast(1L)
    }
}
