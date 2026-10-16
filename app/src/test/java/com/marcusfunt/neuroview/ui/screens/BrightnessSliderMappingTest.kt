package com.marcusfunt.neuroview.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessSliderMappingTest {
    @Test fun rightSideLowersWindowCenterAndBrightensImage() {
        assertEquals(1f, BrightnessSliderMapping.windowCenterFromSlider(0f), 0f)
        assertEquals(0f, BrightnessSliderMapping.windowCenterFromSlider(1f), 0f)
        assertEquals(0.5f, BrightnessSliderMapping.windowCenterFromSlider(0.5f), 0f)
    }

    @Test fun sliderAndWindowCenterMappingsAreInverses() {
        for (step in 0..20) {
            val x = step / 20f
            assertEquals(
                x,
                BrightnessSliderMapping.sliderFromWindowCenter(
                    BrightnessSliderMapping.windowCenterFromSlider(x),
                ),
                0.00001f,
            )
        }
    }
}
