package com.marcusfunt.neuroview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.marcusfunt.neuroview.ui.NeuroViewApp
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
