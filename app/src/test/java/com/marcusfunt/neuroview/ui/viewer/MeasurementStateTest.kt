package com.marcusfunt.neuroview.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementStateTest {
    private fun point(x: Double, y: Double, z: Double) = MeasurementPoint(
        fractionX = x,
        fractionY = y,
        worldRasMm = doubleArrayOf(x, y, z),
    )

    @Test fun validEndpointsProduceCalibratedDistanceAndCanBeFinished() {
        val first = point(0.0, 0.0, 0.0)
        val second = point(3.0, 4.0, 0.0)
        val state = MeasurementState().add(first).add(second)

        assertEquals(2, state.points.size)
        assertEquals("Distance: 5.0 mm", state.statusText())
        assertTrue(state.finish().isFinished)
    }

    @Test fun rejectedTapAndHandleMoveKeepTheExistingEndpoints() {
        val state = MeasurementState().add(point(0.0, 0.0, 0.0)).add(point(1.0, 0.0, 0.0))
        val finished = state.finish()

        assertSame(finished, finished.add(null))
        assertSame(finished, finished.move(index = 4, point = point(2.0, 0.0, 0.0)))
        assertSame(finished, finished.move(index = 1, point = null))
    }

    @Test fun clearRemovesTemporaryResultAndThirdTapStartsANewRuler() {
        val original = MeasurementState().add(point(0.0, 0.0, 0.0)).add(point(1.0, 0.0, 0.0)).finish()
        val next = original.add(point(2.0, 0.0, 0.0))

        assertEquals(1, next.points.size)
        assertEquals(false, next.isFinished)
        assertEquals(0, next.clear().points.size)
    }
}
