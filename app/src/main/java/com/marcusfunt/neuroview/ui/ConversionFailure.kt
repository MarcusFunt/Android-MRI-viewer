package com.marcusfunt.neuroview.ui

import com.marcusfunt.neuroview.ui.operations.OperationFailure
import com.marcusfunt.neuroview.ui.operations.OperationFailureKind
import com.marcusfunt.neuroview.ui.operations.RecoveryAction
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException

/** Known compatibility failures get practical guidance without echoing provider or DICOM text. */
internal fun conversionFailure(error: Throwable): OperationFailure {
    val detail = error.message.orEmpty()
    fun contains(vararg parts: String) = parts.any { detail.contains(it, ignoreCase = true) }
    fun invalid(message: String, unsupported: Boolean = false) = OperationFailure(
        if (unsupported) OperationFailureKind.UNSUPPORTED_DATA else OperationFailureKind.INVALID_DATA,
        message,
        retryable = false,
        actions = setOf(RecoveryAction.RECONNECT_SOURCE),
    )
    return when {
        error is OutOfMemoryError -> OperationFailure(
            OperationFailureKind.OUT_OF_MEMORY,
            "This device is low on memory. Try a smaller DICOM series or MRI volume.", true,
            setOf(RecoveryAction.RETRY, RecoveryAction.CLEAR_DERIVED_CACHE),
        )
        contains("Insufficient storage") -> OperationFailure(
            OperationFailureKind.INSUFFICIENT_STORAGE,
            "There is not enough storage to finish this conversion.", true,
            setOf(RecoveryAction.CLEAR_DERIVED_CACHE, RecoveryAction.RETRY),
        )
        error is FileNotFoundException || error is SecurityException -> OperationFailure(
            OperationFailureKind.SOURCE_UNAVAILABLE,
            "A selected MRI document is no longer accessible. Select it again.", true,
            setOf(RecoveryAction.RECONNECT_SOURCE, RecoveryAction.RETRY),
        )
        error is EOFException || error is ZipException -> invalid(
            "The source is incomplete or its compressed data is damaged. Copy or export the complete " +
                "scan again, then select that copy.",
        )
        error is IllegalArgumentException && contains("transfer syntax", "undefined", "encapsulated") -> invalid(
            "This DICOM encoding is not supported. Convert it on a computer to NIfTI, or export " +
                "uncompressed single-frame DICOM using Implicit or Explicit VR Little Endian, then try again.", true,
        )
        error is IllegalArgumentException && contains("different studies or series") -> invalid(
            "The selection contains more than one DICOM study or series. Select files from one " +
                "acquisition only, or separate the series on a computer before converting.",
        )
        error is IllegalArgumentException && contains(
            "rows or columns differ", "pixel spacing", "image orientation", "slice positions",
            "Irregular or missing DICOM", "direction cosines", "PixelSpacing", "orientation vectors",
        ) -> invalid(
            "The DICOM slices have incompatible geometry, duplicate positions, or missing slices. " +
                "Select one complete acquisition. If this persists, convert the series to NIfTI on a computer.",
        )
        error is IllegalArgumentException && contains(
            "multiframe", "monochrome", "MONOCHROME2", "pixel packing", "NIfTI datatype",
            "single-file NIfTI", "NIfTI-1 header", "Multi-channel",
        ) -> invalid(
            "This image format is not supported by the built-in converter. Use a computer to export " +
                "a scalar NIfTI-1 (.nii or .nii.gz), or uncompressed single-frame monochrome DICOM.", true,
        )
        error is IllegalArgumentException -> invalid(
            "The selected MRI data is incomplete or incompatible. Select a complete scan from one " +
                "series, or convert it to NIfTI on a computer before importing.",
        )
        error is IOException -> OperationFailure(
            OperationFailureKind.PROVIDER_ERROR,
            "The file provider could not supply or save the MRI data. Check access and free space, " +
                "or copy the source to local storage and try again.", true,
            setOf(RecoveryAction.RECONNECT_SOURCE, RecoveryAction.RETRY),
        )
        else -> OperationFailure(
            OperationFailureKind.INTERNAL_ERROR,
            "NeuroView could not finish this conversion. Check source and destination access.", true,
            setOf(RecoveryAction.RETRY, RecoveryAction.RECONNECT_SOURCE),
        )
    }
}
