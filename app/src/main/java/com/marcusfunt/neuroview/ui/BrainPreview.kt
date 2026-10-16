package com.marcusfunt.neuroview.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.marcusfunt.neuroview.brain.BrainGlSurfaceView

/**
 * Pure 3D preview without chrome or descriptive text. Home owns the settings.
 * Paused off-screen and while the user drags; drawing uses RENDERMODE_WHEN_DIRTY.
 */
@Composable
internal fun BrainPreview(
    modifier: Modifier = Modifier,
    autoRotation: Boolean = true,
    resetRequest: Int = 0,
) {
    val context = LocalContext.current
    val supportsOpenGl = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo.reqGlEsVersion >= 0x20000
    }
    if (!supportsOpenGl) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "3D preview requires OpenGL ES 2.0",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    InteractiveBrain(modifier, autoRotation, resetRequest)
}

@Composable
private fun InteractiveBrain(
    modifier: Modifier,
    autoRotation: Boolean,
    resetRequest: Int,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(context) { BrainGlSurfaceView(context) }
    var userDragging by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }

    val animator = remember(view) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            var lastMillis = 0L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) { lastMillis = 0L }
            })
            addUpdateListener {
                val now = SystemClock.uptimeMillis()
                if (lastMillis == 0L) lastMillis = now
                val elapsed = now - lastMillis
                if (elapsed >= 33L) {
                    view.rotateBy(elapsed.coerceAtMost(66L) * (360f / 50000f), 0f)
                    lastMillis = now
                }
            }
        }
    }

    DisposableEffect(view, lifecycleOwner) {
        view.onTouchDragChanged = { userDragging = it }
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    view.onResume()
                    resumed = true
                    view.requestRender()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    view.onPause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            view.onResume()
            resumed = true
        }
        onDispose {
            animator.cancel()
            resumed = false
            view.onPause()
            view.onTouchDragChanged = {}
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(autoRotation, userDragging, resumed) {
        if (resumed && autoRotation && !userDragging && ValueAnimator.areAnimatorsEnabled()) {
            animator.start()
        } else {
            animator.cancel()
        }
    }
    LaunchedEffect(resetRequest) {
        if (resetRequest != 0) view.resetView()
    }
    AndroidView(factory = { view }, modifier = modifier.fillMaxSize())
}
