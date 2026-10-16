package com.marcusfunt.neuroview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.marcusfunt.neuroview.ui.NeuroViewApp
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroViewTheme {
                NeuroViewApp()
            }
        }
    }
}
