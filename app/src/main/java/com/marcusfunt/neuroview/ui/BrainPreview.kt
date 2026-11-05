package com.marcusfunt.neuroview.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.marcusfunt.neuroview.brain.BrainGlTextureView
import com.marcusfunt.neuroview.BuildConfig

private const val FIRST_FRAME_TEST_TAG = "brain-preview-first-frame-drawn"

internal fun shouldRotateBrain(
    autoRotation: Boolean,
    firstFrameDrawn: Boolean,
    resumed: Boolean,
    userDragging: Boolean,
    animatorsEnabled: Boolean,
): Boolean = autoRotation && firstFrameDrawn && resumed && !userDragging && animatorsEnabled

/**
 * Pure 3D preview without chrome or descriptive text. Home owns the settings.
 * Paused off-screen and while the user drags; EGL frames render only on request.
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
    var firstFrameDrawn by remember { mutableStateOf(false) }
    var renderFailure by remember { mutableStateOf<String?>(null) }
    val readinessModifier = if (firstFrameDrawn) {
        modifier.testTag(FIRST_FRAME_TEST_TAG)
    } else {
        modifier
    }
    InteractiveBrain(
        readinessModifier,
        autoRotation,
        resetRequest,
        firstFrameDrawn,
        onFirstFrameDrawn = { firstFrameDrawn = true },
        renderFailure = renderFailure,
        onRenderFailure = { renderFailure = it },
    )
}

@Composable
private fun InteractiveBrain(
    modifier: Modifier,
    autoRotation: Boolean,
    resetRequest: Int,
    firstFrameDrawn: Boolean,
    onFirstFrameDrawn: () -> Unit,
    renderFailure: String?,
    onRenderFailure: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(context) { BrainGlTextureView(context) }
    val currentOnFirstFrameDrawn by rememberUpdatedState(onFirstFrameDrawn)
    val currentOnRenderFailure by rememberUpdatedState(onRenderFailure)
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
                    view.rotateBy(elapsed.coerceAtMost(66L) * (360f / 44000f), 0f)
                    lastMillis = now
                }
            }
        }
    }

    DisposableEffect(view, lifecycleOwner) {
        traceBrainPreview("DisposableEffect attached view=${System.identityHashCode(view)}")
        view.onTouchDragChanged = { userDragging = it }
        view.onFirstFrameDrawn = {
            traceBrainPreview("first-frame callback view=${System.identityHashCode(view)}")
            currentOnFirstFrameDrawn()
        }
        view.onRenderFailure = { failure -> currentOnRenderFailure(failure) }
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    traceBrainPreview("lifecycle ON_RESUME")
                    view.onResume()
                    resumed = true
                    view.requestRender()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    traceBrainPreview("lifecycle ON_PAUSE")
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
            traceBrainPreview("DisposableEffect disposed view=${System.identityHashCode(view)}")
            animator.cancel()
            resumed = false
            view.onPause()
            view.onTouchDragChanged = {}
            view.onFirstFrameDrawn = {}
            view.onRenderFailure = {}
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(autoRotation, firstFrameDrawn, userDragging, resumed) {
        if (shouldRotateBrain(
                autoRotation = autoRotation,
                firstFrameDrawn = firstFrameDrawn,
                resumed = resumed,
                userDragging = userDragging,
                animatorsEnabled = ValueAnimator.areAnimatorsEnabled(),
            )
        ) {
            animator.start()
        } else {
            animator.cancel()
        }
    }
    LaunchedEffect(resetRequest) {
        if (resetRequest != 0) view.resetView()
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        if (!firstFrameDrawn) {
            if (renderFailure == null) {
                Image(
                    painter = painterResource(com.marcusfunt.neuroview.R.drawable.neuroview_logo),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).alpha(0.28f),
                )
            } else {
                Text(
                    "3D preview unavailable",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun traceBrainPreview(event: String) {
    if (BuildConfig.DEBUG) {
        Log.i("NeuroViewDraw", "t=${SystemClock.uptimeMillis()} BrainPreview $event")
    }
}
