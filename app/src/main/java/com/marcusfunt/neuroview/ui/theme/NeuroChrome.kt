package com.marcusfunt.neuroview.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Restrained, reusable navigation chrome. Never applied to the MRI image itself.
 * Keep static gradients outside scroll-recomposed cards and do not animate them.
 */
object NeuroChrome {
    val background = Color(0xFF06111F)
    val topBar = Color(0xFF091726)
    val panel = Color(0xFF112335)
    val raised = Color(0xFF152B3E)
    val stroke = Color(0xFF294357)
    val accent = Color(0xFF79D6E6)
    val ink = Color(0xFFF1F6FA)
    val muted = Color(0xFFA5B9CC)
    val blue = Color(0xFF1882DF)
    val primaryGradient = Brush.horizontalGradient(
        listOf(Color(0xFF12547C), Color(0xFF1B5F9B)),
    )
    val backgroundGradient = Brush.verticalGradient(
        listOf(Color(0xFF081725), Color(0xFF0A1D2E), Color(0xFF07121F)),
    )
}
fun Modifier.neuroWorkspaceBackground(): Modifier =
    this.background(NeuroChrome.backgroundGradient)
