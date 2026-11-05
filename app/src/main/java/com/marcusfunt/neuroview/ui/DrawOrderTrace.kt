package com.marcusfunt.neuroview.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.marcusfunt.neuroview.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/** Test-controlled overlay gate for draw-order screenshots; normal debug builds keep it off. */
internal object DrawOrderDiagnostics {
    @Volatile var enabledForTests: Boolean = false

    fun isEnabled(): Boolean = BuildConfig.DEBUG && enabledForTests
}

/** Logs the first Compose composition, layout, and draw for a named surface in debug builds. */
@Composable
internal fun Modifier.traceComposeFirstDraw(name: String): Modifier {
    if (!BuildConfig.DEBUG) return this

    val compositionLogged = remember(name) { AtomicBoolean(false) }
    val layoutLogged = remember(name) { AtomicBoolean(false) }
    val drawLogged = remember(name) { AtomicBoolean(false) }
    SideEffect {
        if (compositionLogged.compareAndSet(false, true)) traceComposeEvent("composition $name")
    }
    return this
        .onGloballyPositioned { coordinates ->
            if (layoutLogged.compareAndSet(false, true)) {
                val bounds = coordinates.boundsInWindow()
                traceComposeEvent(
                    "layout $name bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
                )
            }
        }
        .drawWithContent {
            if (drawLogged.compareAndSet(false, true)) traceComposeEvent("draw $name")
            drawContent()
        }
}

private fun traceComposeEvent(event: String) {
    if (BuildConfig.DEBUG) {
        Log.i("NeuroViewDraw", "t=${SystemClock.uptimeMillis()} Compose $event")
    }
}
