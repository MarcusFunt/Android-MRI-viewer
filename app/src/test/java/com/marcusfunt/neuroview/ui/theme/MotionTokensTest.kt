package com.marcusfunt.neuroview.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokensTest {
    @Test
    fun preferenceScalesSharedMotionDurations() {
        assertEquals(180, MotionToken.STANDARD.durationMs(MotionPreference.SYSTEM))
        assertEquals(90, MotionToken.STANDARD.durationMs(MotionPreference.REDUCED))
        assertEquals(0, MotionToken.STANDARD.durationMs(MotionPreference.OFF))
    }

    @Test
    fun motionTokensStayWithinTheDocumentedTimingRanges() {
        assertTrue(MotionToken.INSTANT.durationMs(MotionPreference.SYSTEM) in 0..80)
        assertTrue(MotionToken.QUICK.durationMs(MotionPreference.SYSTEM) in 100..150)
        assertTrue(MotionToken.STANDARD.durationMs(MotionPreference.SYSTEM) in 160..220)
        assertTrue(MotionToken.NAVIGATION.durationMs(MotionPreference.SYSTEM) in 220..300)
        assertTrue(MotionToken.CELEBRATION.durationMs(MotionPreference.SYSTEM) in 250..400)
    }

    @Test
    fun reducedAndOffModesDisableContinuousDecorativeMotion() {
        assertTrue(MotionPreference.SYSTEM.allowsDecorativeMotion)
        assertFalse(MotionPreference.REDUCED.allowsDecorativeMotion)
        assertFalse(MotionPreference.OFF.allowsDecorativeMotion)
    }
}
