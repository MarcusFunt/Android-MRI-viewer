package com.marcusfunt.neuroview.ui.viewer

/** One state machine owns a touch stream from its first down until every pointer is released. */
internal enum class ViewerGestureState {
    TAP_CANDIDATE,
    SLICE_DRAG,
    PINCH_PAN,
    HANDLE_DRAG,
    TOOL_DRAG,
}

internal class ViewerGestureController {
    var state: ViewerGestureState = ViewerGestureState.TAP_CANDIDATE
        private set

    fun onPointerCount(count: Int) {
        if (count > 1) state = ViewerGestureState.PINCH_PAN
    }

    fun onDragStarted(isToolMode: Boolean, measurementHandleIndex: Int?) {
        if (state == ViewerGestureState.PINCH_PAN) return
        state = when {
            isToolMode && measurementHandleIndex != null -> ViewerGestureState.HANDLE_DRAG
            isToolMode -> ViewerGestureState.TOOL_DRAG
            else -> ViewerGestureState.SLICE_DRAG
        }
    }
}
