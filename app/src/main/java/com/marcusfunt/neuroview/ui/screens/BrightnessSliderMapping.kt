package com.marcusfunt.neuroview.ui.screens

/**
 * The NIfTI window-center parameter darkens the display as it increases.
 * Present the user-facing brightness control in the intuitive opposite
 * direction: dragging right increases image brightness.
 *
 * Does not alter the image data or the renderer's window-center convention.
 */
internal object BrightnessSliderMapping {
    fun sliderFromWindowCenter(center: Float): Float = 1f - center
    fun windowCenterFromSlider(position: Float): Float = 1f - position
}
