package com.marcusfunt.neuroview.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainPreviewMotionTest {
    @Test
    fun decorativeRotationRequiresAReadyVisibleIdlePreviewAndEnabledSystemMotion() {
        assertFalse(
            shouldRotateBrain(autoRotation = true, firstFrameDrawn = false, resumed = true,
                userDragging = false, animatorsEnabled = true),
        )
        assertTrue(
            shouldRotateBrain(autoRotation = true, firstFrameDrawn = true, resumed = true,
                userDragging = false, animatorsEnabled = true),
        )
        assertFalse(
            shouldRotateBrain(autoRotation = true, firstFrameDrawn = true, resumed = false,
                userDragging = false, animatorsEnabled = true),
        )
        assertFalse(
            shouldRotateBrain(autoRotation = true, firstFrameDrawn = true, resumed = true,
                userDragging = true, animatorsEnabled = true),
        )
        assertFalse(
            shouldRotateBrain(autoRotation = true, firstFrameDrawn = true, resumed = true,
                userDragging = false, animatorsEnabled = false),
        )
    }
}
