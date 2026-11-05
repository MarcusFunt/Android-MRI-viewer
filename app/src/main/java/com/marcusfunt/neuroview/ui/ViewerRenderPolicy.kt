package com.marcusfunt.neuroview.ui

import kotlin.math.roundToInt

/** Quality controls affect only the temporary frame shown during slice movement. */
object ViewerRenderPolicy {
    const val MIN_PREVIEW_QUALITY = 0.25f
    const val MAX_PREVIEW_QUALITY = 0.75f
    const val DEFAULT_PREVIEW_QUALITY = 0.42f
    const val MIN_PREVIEW_RESOLUTION = 96

    fun previewResolution(fullResolution: Int, quality: Float): Int {
        require(fullResolution > MIN_PREVIEW_RESOLUTION) {
            "Full render resolution must exceed the minimum preview resolution"
        }
        return (fullResolution * quality.coerceIn(
            MIN_PREVIEW_QUALITY,
            MAX_PREVIEW_QUALITY,
        )).roundToInt().coerceIn(MIN_PREVIEW_RESOLUTION, fullResolution - 1)
    }
}
