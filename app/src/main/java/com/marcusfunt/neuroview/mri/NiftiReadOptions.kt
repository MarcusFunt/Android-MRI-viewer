package com.marcusfunt.neuroview.mri

import java.io.File

/** Decoder stages reported from completed reader work, independent of Compose and Android UI. */
enum class NiftiReadStage {
    READING_HEADER,
    VALIDATING_VOLUME,
    SKIPPING_EXTENSIONS,
    DECODING_VOLUME,
    PREPARING_DISK_BACKING,
    SAMPLING_INTENSITY,
}

enum class NiftiReadUnit {
    BYTES,
    VOXELS,
    SAMPLES,
}

data class NiftiReadProgress(
    val stage: NiftiReadStage,
    val completedUnits: Long? = null,
    val totalUnits: Long? = null,
    val unit: NiftiReadUnit? = null,
)

/** Optional hooks for responsive reads. The default path preserves the synchronous API. */
data class NiftiReadOptions(
    val maxVoxels: Long = NiftiReader.MAX_VOXELS,
    val backingDirectory: File? = null,
    val cancellationCheck: () -> Unit = {},
    val progressSink: (NiftiReadProgress) -> Unit = {},
)
