package com.marcusfunt.neuroview

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.marcusfunt.neuroview.ui.NeuroViewApp
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        traceLifecycle("onCreate")
        // NeuroView is dark-first even when Android is configured for light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            NeuroViewTheme {
                NeuroViewApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        traceLifecycle("onStart")
    }

    override fun onResume() {
        super.onResume()
        traceLifecycle("onResume")
    }

    override fun onPause() {
        traceLifecycle("onPause")
        super.onPause()
    }

    override fun onStop() {
        traceLifecycle("onStop")
        super.onStop()
    }

    override fun onDestroy() {
        traceLifecycle("onDestroy")
        super.onDestroy()
    }

    private fun traceLifecycle(event: String) {
        if (BuildConfig.DEBUG) {
            Log.i("NeuroViewDraw", "t=${SystemClock.uptimeMillis()} Activity $event")
        }
    }
}
