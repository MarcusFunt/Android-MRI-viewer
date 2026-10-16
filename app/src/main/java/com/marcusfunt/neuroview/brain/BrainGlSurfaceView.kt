package com.marcusfunt.neuroview.brain

import android.content.Context
import android.opengl.GLSurfaceView
import android.graphics.PixelFormat
import android.view.MotionEvent

/**
 * Interactive render-on-demand brain display.
 * Touch drag rotates the model; automatic rotation is controlled by the Compose host.
 */
internal class BrainGlSurfaceView(context: Context) : GLSurfaceView(context) {
    private val brainRenderer = BrainRenderer(context.applicationContext)
    private var previousX = 0f
    private var previousY = 0f
    var onTouchDragChanged: (Boolean) -> Unit = {}

    init {
        // Transparent brain SurfaceView lets the gradient and subtle glow sit
        // behind the model instead of trapping it in a rectangular canvas.
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(brainRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = "Rotating 3D brain model from the Brainchop demonstration dataset. Drag to rotate."
        isClickable = true
    }

    fun rotateBy(yawDelta: Float, pitchDelta: Float) {
        brainRenderer.yawDegrees = (brainRenderer.yawDegrees + yawDelta) % 360f
        brainRenderer.pitchDegrees =
            (brainRenderer.pitchDegrees + pitchDelta).coerceIn(-62f, 62f)
        requestRender()
    }

    fun resetView() {
        brainRenderer.yawDegrees = -85f
        brainRenderer.pitchDegrees = -12f
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                onTouchDragChanged(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                rotateBy(
                    yawDelta = (event.x - previousX) * 0.40f,
                    pitchDelta = (event.y - previousY) * 0.28f,
                )
                previousX = event.x
                previousY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                onTouchDragChanged(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onTouchDragChanged(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
