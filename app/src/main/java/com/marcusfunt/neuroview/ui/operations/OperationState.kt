package com.marcusfunt.neuroview.ui.operations

import kotlin.math.roundToInt

@JvmInline
value class OperationId(val value: Long) {
    init {
        require(value > 0L) { "Operation IDs must be positive" }
    }
}

enum class OperationKind {
    VIEWER_LOAD,
    IMPORT_BATCH,
    THUMBNAIL,
    DICOM_CONVERSION,
    BRAIN_SUITE,
}

enum class OperationStage(val label: String) {
    OPENING_SOURCE("Opening selected file"),
    READING_HEADER("Reading MRI header"),
    VALIDATING_VOLUME("Checking volume dimensions"),
    SKIPPING_EXTENSIONS("Reading NIfTI extensions"),
    DECODING_VOLUME("Decoding MRI volume"),
    PREPARING_DISK_BACKING("Preparing local volume storage"),
    SAMPLING_INTENSITY("Sampling MRI intensity range"),
    RENDERING_FIRST_SLICE("Rendering first slice"),
    FIRST_SLICE_READY("Displaying first slice"),
    IMPORTING_FILE("Checking selected MRI files"),
    GENERATING_THUMBNAIL("Creating scan preview"),
    SCANNING_DICOM("Scanning DICOM files"),
    ASSEMBLING_SERIES("Building DICOM series"),
    WRITING_NIFTI("Writing NIfTI output"),
    UPLOADING("Uploading to processing host"),
    WAITING_FOR_REMOTE("Waiting for processing host"),
    DOWNLOADING("Downloading processed data"),
    PACKAGING("Packaging results"),
}

enum class OperationStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_PROVIDER,
    SLOW,
    SUSPECTED_STALL,
    FAILED,
    CANCELING,
    CANCELED,
    SUCCEEDED,
    ;

    val isTerminal: Boolean
        get() = this == FAILED || this == CANCELED || this == SUCCEEDED
}

enum class OperationUnit {
    BYTES,
    VOXELS,
    FILES,
    SLICES,
    PIXELS,
    ITEMS,
}

enum class OperationFailureKind {
    SOURCE_UNAVAILABLE,
    INVALID_DATA,
    UNSUPPORTED_DATA,
    INSUFFICIENT_STORAGE,
    OUT_OF_MEMORY,
    PROVIDER_ERROR,
    NETWORK_ERROR,
    REMOTE_ERROR,
    INTERNAL_ERROR,
}

enum class RecoveryAction {
    WAIT,
    CANCEL,
    RETRY,
    RECONNECT_SOURCE,
    CLEAR_DERIVED_CACHE,
    COPY_SAFE_DIAGNOSTICS,
}

data class OperationProgress(
    val completedUnits: Long? = null,
    val totalUnits: Long? = null,
    val unit: OperationUnit? = null,
) {
    init {
        require(completedUnits == null || completedUnits >= 0L) {
            "Completed work cannot be negative"
        }
        require(totalUnits == null || totalUnits > 0L) {
            "A known total must be positive"
        }
        require(totalUnits == null || completedUnits != null) {
            "A known total requires a completed-work count"
        }
        require((completedUnits == null && totalUnits == null) || unit != null) {
            "Progress counters require a unit"
        }
        require(totalUnits == null || completedUnits!! <= totalUnits) {
            "Completed work cannot exceed its total"
        }
    }

    val fraction: Float?
        get() = if (completedUnits != null && totalUnits != null) {
            (completedUnits.toDouble() / totalUnits.toDouble()).toFloat()
        } else {
            null
        }

    val percent: Int?
        get() = fraction?.times(100f)?.roundToInt()
}

data class OperationFailure(
    val kind: OperationFailureKind,
    val safeMessage: String,
    val retryable: Boolean,
    val actions: Set<RecoveryAction> = emptySet(),
) {
    init {
        require(safeMessage.isNotBlank()) { "A failure needs a user-facing safe message" }
    }
}

data class OperationSnapshot(
    val id: OperationId,
    val kind: OperationKind,
    val status: OperationStatus,
    val stage: OperationStage,
    val progress: OperationProgress?,
    val startedAtElapsedMs: Long,
    val stageStartedAtElapsedMs: Long,
    val lastWorkAdvanceElapsedMs: Long,
    val lastHeartbeatElapsedMs: Long?,
    val failure: OperationFailure? = null,
    val resultId: String? = null,
)

data class StallThresholds(
    val slowAfterMs: Long = 8_000L,
    val suspectedStallAfterMs: Long = 20_000L,
    val providerSuspectedStallAfterMs: Long = 60_000L,
) {
    init {
        require(slowAfterMs >= 0L)
        require(suspectedStallAfterMs > slowAfterMs)
        require(providerSuspectedStallAfterMs >= suspectedStallAfterMs)
    }
}

fun interface MonotonicClock {
    fun nowMs(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowMs(): Long = System.nanoTime() / 1_000_000L
}
