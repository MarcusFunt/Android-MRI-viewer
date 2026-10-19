package com.marcusfunt.neuroview.ui.screens

import org.junit.Assert.*
import org.junit.Test

class SliceNavigationTest {
    @Test fun uninterruptedDragRetainsFractionalDistanceAcrossFrameCommits() {
        val drag = SliceDragAccumulator(128f, 18f, 255)
        repeat(180) { drag.move(-3f) } // Render commits must never replace this gesture state.
        assertEquals(158, drag.slice)
        repeat(180) { drag.move(3f) }
        assertEquals(128, drag.slice)
    }

    @Test fun precisionChangesDoNotMoveSelectionAndRetainFractions() {
        val scrub = PrecisionScrubAccumulator(128f, 255)
        assertEquals(128, scrub.move(0f, 160f, 300f, false))
        repeat(16) { scrub.move(1f, 160f, 255f, false) }
        assertEquals(129, scrub.slice)
        assertEquals(129, scrub.move(0f, 0f, 255f, false))
        assertEquals(145, scrub.move(16f, 0f, 255f, false))
    }

    @Test fun scrubSensitivityIsContinuousAndRtlReversesDirection() {
        assertEquals(precisionSensitivity(70f), precisionSensitivity(70.001f), 0.001f)
        val normal = PrecisionScrubAccumulator(128f, 255)
        val precise = PrecisionScrubAccumulator(128f, 255)
        assertTrue(normal.move(60f, 0f, 255f, false) > precise.move(60f, 100f, 255f, false))
        assertEquals(68, PrecisionScrubAccumulator(128f, 255).move(60f, 0f, 255f, true))
        assertEquals(255, normal.move(9999f, 0f, 255f, false))
        assertEquals(254, normal.move(-1f, 0f, 255f, false))
    }

    @Test fun inertiaDependsOnNavigationSettingAndPlatformReduction() {
        assertTrue(allowsSliceInertia(true, false))
        assertFalse(allowsSliceInertia(false, false))
        assertFalse(allowsSliceInertia(true, true))
    }
}
