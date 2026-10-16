package com.marcusfunt.neuroview.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.marcusfunt.neuroview.brain.BrainGlSurfaceView

@Composable
internal fun BrainPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val supportsOpenGl = remember(context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.deviceConfigurationInfo.reqGlEsVersion >= 0x20000
    }

    Column(
        modifier = modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("BRAIN PREVIEW", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("3D · OFFLINE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (supportsOpenGl) {
            InteractiveBrain(
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "This device does not support OpenGL ES 2.0. The 3D preview is unavailable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            "Stylized illustration · Not a patient MRI or diagnostic model",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun InteractiveBrain(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(context) { BrainGlSurfaceView(context) }
    var autoRotation by rememberSaveable { mutableStateOf(true) }
    var userDragging by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }

    val animator = remember(view) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            var lastFrameMillis = 0L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    lastFrameMillis = 0L
                }
            })
            addUpdateListener {
                val now = SystemClock.uptimeMillis()
                if (lastFrameMillis == 0L) lastFrameMillis = now
                val elapsed = now - lastFrameMillis
                if (elapsed >= 32L) {
                    // Approximately 30 FPS: avoid rendering at 60/120 Hz unnecessarily.
                    view.rotateBy(elapsed.coerceAtMost(60L) * (360f / 24000f), 0f)
                    lastFrameMillis = now
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

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { autoRotation = !autoRotation }) {
                Text(if (autoRotation) "Pause rotation" else "Resume rotation")
            }
            OutlinedButton(onClick = { view.resetView() }) {
                Text("Reset view")
            }
        }
        Text(
            "Drag the brain to rotate",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}
