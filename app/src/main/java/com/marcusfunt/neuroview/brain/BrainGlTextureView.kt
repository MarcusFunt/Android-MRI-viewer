package com.marcusfunt.neuroview.brain

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.TextureView
import com.marcusfunt.neuroview.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/** TextureView-backed GLES2 preview so Compose content can layer above and below the brain. */
internal class BrainGlTextureView(context: Context) : TextureView(context) {
    @Volatile var onFirstFrameDrawn: () -> Unit = {}
    @Volatile var onRenderFailure: (String) -> Unit = {}
    @Volatile var onTouchDragChanged: (Boolean) -> Unit = {}
    @Volatile internal var presentedFrameCountForTests: Long = 0L

    private val renderLock = Object()
    private val frameSubmitted = AtomicBoolean(false)
    private val firstTextureUpdateLogged = AtomicBoolean(false)
    private val firstVisibleFrameNotified = AtomicBoolean(false)
    private val brainRenderer = BrainRenderer(context.applicationContext) {
        frameSubmitted.set(true)
    }

    private var activeSurfaceTexture: SurfaceTexture? = null
    private var renderThread: Thread? = null
    private var renderWidth = 0
    private var renderHeight = 0
    private var resumed = false
    private var renderRequested = false
    private var shutdownRequested = false
    private var previousX = 0f
    private var previousY = 0f

    init {
        isOpaque = false
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                firstVisibleFrameNotified.set(false)
                firstTextureUpdateLogged.set(false)
                frameSubmitted.set(false)
                trace("SurfaceTexture available ${width}x$height")
                updateSurface(surface, width, height)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                trace("SurfaceTexture size changed ${width}x$height")
                updateSurface(surface, width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                trace("SurfaceTexture destroyed")
                synchronized(renderLock) {
                    if (activeSurfaceTexture === surface) activeSurfaceTexture = null
                    shutdownRequested = true
                    renderLock.notifyAll()
                }
                stopRenderThread()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                if (firstTextureUpdateLogged.compareAndSet(false, true)) {
                    trace("first SurfaceTexture frame updated")
                }
                // A completed GL swap is not yet a frame displayed by the TextureView.
                // Wait for the view's compositor update before marking Home as ready.
                if (frameSubmitted.get() && firstVisibleFrameNotified.compareAndSet(false, true)) {
                    frameSubmitted.set(false)
                    trace("first compositor-updated brain frame is ready")
                    onFirstFrameDrawn()
                }
            }
        }
        contentDescription = "Rotating 3D brain model from the Brainchop demonstration dataset. Drag to rotate."
        isClickable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        trace("View attached")
        if (isAvailable) surfaceTexture?.let { updateSurface(it, width, height) }
    }

    override fun onDetachedFromWindow() {
        trace("View detached")
        synchronized(renderLock) {
            shutdownRequested = true
            renderLock.notifyAll()
        }
        stopRenderThread()
        super.onDetachedFromWindow()
    }

    fun onResume() {
        synchronized(renderLock) {
            resumed = true
            renderRequested = true
            startRenderThreadIfReadyLocked()
            renderLock.notifyAll()
        }
        trace("onResume requested initial render")
    }

    fun onPause() {
        synchronized(renderLock) {
            resumed = false
            renderLock.notifyAll()
        }
        trace("onPause")
    }

    fun requestRender() {
        synchronized(renderLock) {
            renderRequested = true
            startRenderThreadIfReadyLocked()
            renderLock.notifyAll()
        }
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

    private fun updateSurface(surface: SurfaceTexture, width: Int, height: Int) {
        synchronized(renderLock) {
            activeSurfaceTexture = surface
            renderWidth = width
            renderHeight = height
            renderRequested = true
            startRenderThreadIfReadyLocked()
            renderLock.notifyAll()
        }
    }

    private fun startRenderThreadIfReadyLocked() {
        if (!isAttachedToWindow || !resumed || activeSurfaceTexture == null || renderThread?.isAlive == true) return
        shutdownRequested = false
        val surface = requireNotNull(activeSurfaceTexture)
        renderThread = Thread({ renderLoop(surface) }, "NeuroViewBrainEgl").apply {
            isDaemon = true
            start()
        }
        trace("started EGL render thread")
    }

    private fun stopRenderThread() {
        val thread = synchronized(renderLock) {
            val running = renderThread
            if (running != null) {
                shutdownRequested = true
                renderLock.notifyAll()
            }
            running
        }
        val stopped = stopRenderThreadBounded(thread)
        synchronized(renderLock) {
            if (stopped && renderThread === thread) renderThread = null
            if (stopped && renderThread == null) shutdownRequested = false
        }
    }

    private fun renderLoop(surfaceTexture: SurfaceTexture) {
        var egl: EglSession? = null
        var lastWidth = 0
        var lastHeight = 0
        try {
            egl = runCatching { EglSession.create(surfaceTexture) }
                .onFailure { fail("Could not create the GLES preview surface", it) }
                .getOrNull() ?: return
            brainRenderer.onSurfaceCreated(null, null)
            while (true) {
                val size = synchronized(renderLock) {
                    while (!shutdownRequested && (!resumed || !renderRequested)) {
                        try {
                            renderLock.wait()
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return
                        }
                    }
                    if (shutdownRequested) return
                    renderRequested = false
                    renderWidth.coerceAtLeast(1) to renderHeight.coerceAtLeast(1)
                }

                if (size.first != lastWidth || size.second != lastHeight) {
                    brainRenderer.onSurfaceChanged(null, size.first, size.second)
                    lastWidth = size.first
                    lastHeight = size.second
                }
                brainRenderer.onDrawFrame(null)
                if (egl?.swapBuffers() != true) {
                    fail("Could not present the GLES preview frame", null)
                    return
                }
                presentedFrameCountForTests += 1L
                // Readiness is delivered by onSurfaceTextureUpdated on the UI thread.
                brainRenderer.firstFrameGlError?.let { error ->
                    fail("GLES preview failed to draw (error $error)", null)
                    return
                }
            }
        } catch (failure: Throwable) {
            fail("GLES preview stopped unexpectedly", failure)
        } finally {
            egl?.release()
            synchronized(renderLock) {
                val wasRequestedShutdown = shutdownRequested
                if (renderThread === Thread.currentThread()) renderThread = null
                shutdownRequested = false
                if (wasRequestedShutdown && resumed && isAttachedToWindow && activeSurfaceTexture != null) {
                    renderRequested = true
                    startRenderThreadIfReadyLocked()
                }
                renderLock.notifyAll()
            }
            trace("EGL render thread stopped")
        }
    }

    private fun fail(message: String, failure: Throwable?) {
        if (failure == null) Log.e("NeuroViewDraw", "t=${SystemClock.uptimeMillis()} $message")
        else Log.e("NeuroViewDraw", "t=${SystemClock.uptimeMillis()} $message", failure)
        post { onRenderFailure(message) }
    }

    private fun trace(event: String) {
        if (BuildConfig.DEBUG) {
            Log.i("NeuroViewDraw", "t=${SystemClock.uptimeMillis()} BrainGlTextureView $event")
        }
    }

    private class EglSession private constructor(
        private val display: EGLDisplay,
        private val context: EGLContext,
        private val surface: EGLSurface,
    ) {
        fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(display, surface)

        fun release() {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }

        companion object {
            fun create(texture: SurfaceTexture): EglSession {
                val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
                var initialized = false
                var context = EGL14.EGL_NO_CONTEXT
                var surface = EGL14.EGL_NO_SURFACE
                try {
                    val version = IntArray(2)
                    check(EGL14.eglInitialize(display, version, 0, version, 1)) {
                        "eglInitialize failed"
                    }
                    initialized = true

                    val attributes = intArrayOf(
                        EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_DEPTH_SIZE, 16,
                        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                        EGL14.EGL_NONE,
                    )
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val configCount = IntArray(1)
                    check(
                        EGL14.eglChooseConfig(
                            display, attributes, 0, configs, 0, configs.size, configCount, 0,
                        ) && configCount[0] > 0,
                    ) { "eglChooseConfig failed" }
                    val config = requireNotNull(configs[0])
                    val contextAttributes = intArrayOf(
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE,
                    )
                    context = EGL14.eglCreateContext(
                        display, config, EGL14.EGL_NO_CONTEXT, contextAttributes, 0,
                    )
                    check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
                    surface = EGL14.eglCreateWindowSurface(
                        display, config, texture, intArrayOf(EGL14.EGL_NONE), 0,
                    )
                    check(surface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
                    check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
                        "eglMakeCurrent failed"
                    }
                    return EglSession(display, context, surface)
                } catch (failure: Throwable) {
                    if (surface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(display, surface)
                    }
                    if (context != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(display, context)
                    }
                    if (initialized) EGL14.eglTerminate(display)
                    throw failure
                }
            }
        }
    }
}

/** Bounded grace period, followed by interruption and one bounded final wait. */
internal fun stopRenderThreadBounded(
    thread: Thread?,
    gracefulWaitMillis: Long = 250L,
    interruptWaitMillis: Long = 100L,
): Boolean {
    if (thread == null || !thread.isAlive) return true
    if (thread === Thread.currentThread()) return false
    try {
        thread.join(gracefulWaitMillis.coerceAtLeast(1L))
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        return false
    }
    if (thread.isAlive) {
        thread.interrupt()
        try {
            thread.join(interruptWaitMillis.coerceAtLeast(1L))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
    }
    return !thread.isAlive
}
