package com.marcusfunt.neuroview.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerGestureControllerTest {
    @Test
    fun addingAndRemovingPointersKeepsTheWholeStreamOwnedByPinchPan() {
        val controller = ViewerGestureController()
        assertEquals(ViewerGestureState.TAP_CANDIDATE, controller.state)
        controller.onPointerCount(2)
        assertEquals(ViewerGestureState.PINCH_PAN, controller.state)
        controller.onPointerCount(1)
        controller.onDragStarted(isToolMode = false, measurementHandleIndex = null)
        assertEquals(ViewerGestureState.PINCH_PAN, controller.state)
        controller.onPointerCount(3)
        controller.onPointerCount(1)
        assertEquals(ViewerGestureState.PINCH_PAN, controller.state)
    }

    @Test
    fun oneFingerDragRoutesToSliceOrMeasurementHandle() {
        val browse = ViewerGestureController()
        browse.onDragStarted(isToolMode = false, measurementHandleIndex = null)
        assertEquals(ViewerGestureState.SLICE_DRAG, browse.state)

        val measure = ViewerGestureController()
        measure.onDragStarted(isToolMode = true, measurementHandleIndex = 1)
        assertEquals(ViewerGestureState.HANDLE_DRAG, measure.state)

        val toolBackgroundDrag = ViewerGestureController()
        toolBackgroundDrag.onDragStarted(isToolMode = true, measurementHandleIndex = null)
        assertEquals(ViewerGestureState.TOOL_DRAG, toolBackgroundDrag.state)
    }
}
