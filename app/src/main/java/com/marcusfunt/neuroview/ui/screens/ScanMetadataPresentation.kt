package com.marcusfunt.neuroview.ui.screens

import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import java.util.Locale

internal fun scanSpacingSummary(metadata: NiftiSeriesMetadata): String {
    val millimeters = metadata.spacingMm
    return if (millimeters != null) {
        millimeters.joinToString(" × ") { String.format(Locale.US, "%.2f", it) } + " mm"
    } else {
        "Spatial units unknown"
    }
}

internal fun scanSpacingDetails(metadata: NiftiSeriesMetadata): String {
    val millimeters = metadata.spacingMm
    if (millimeters != null) {
        return millimeters.joinToString(" × ") { String.format(Locale.US, "%.3f", it) } + " mm"
    }
    val headerValues = metadata.spacingHeaderUnits.joinToString(" × ") {
        String.format(Locale.US, "%.3f", it)
    }
    return "Header pixdim: $headerValues · spatial units unknown; do not interpret as mm"
}

internal fun scanSpatialUnitWarning(metadata: NiftiSeriesMetadata): String? =
    if (metadata.spacingMm == null) {
        "Spatial units unknown. Calibrated millimetre measurements are unavailable."
    } else {
        null
    }

internal fun scanSamplingInference(metadata: NiftiSeriesMetadata): String {
    val spacing = metadata.spacingHeaderUnits
    if (spacing.size != 3 || spacing.any { !it.isFinite() || it <= 0.0 }) {
        return "Unknown (header spacing is incomplete or invalid; acquisition type unconfirmed)"
    }
    val ratio = (spacing.maxOrNull() ?: 1.0) / (spacing.minOrNull() ?: 1.0)
    return when {
        ratio >= 1.75 ->
            "2D-like (inferred from anisotropic header spacing; acquisition type unconfirmed)"
        ratio <= 1.20 ->
            "3D-like (inferred from near-isotropic header spacing; acquisition type unconfirmed)"
        else ->
            "Unknown (spacing pattern is inconclusive; acquisition type unconfirmed)"
    }
}

internal fun scanAffineProvenance(metadata: NiftiSeriesMetadata): String = when {
    metadata.sformCode > 0 -> "sform (code ${metadata.sformCode})"
    metadata.qformCode > 0 -> "qform (code ${metadata.qformCode})"
    metadata.orientation.equals("sform", ignoreCase = true) -> "sform (code unavailable in saved catalog entry)"
    metadata.orientation.equals("qform", ignoreCase = true) -> "qform (code unavailable in saved catalog entry)"
    else -> "Unknown; anatomical labels may not match the image"
}
