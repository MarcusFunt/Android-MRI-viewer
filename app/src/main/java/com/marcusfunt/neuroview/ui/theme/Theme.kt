package com.marcusfunt.neuroview.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF91CED2),
    onPrimary = Color(0xFF07343B),
    secondary = Color(0xFFB4C7D2),
    background = Color(0xFF091117),
    onBackground = Color(0xFFE5F0F2),
    surface = Color(0xFF111E27),
    onSurface = Color(0xFFE5F0F2),
    surfaceVariant = Color(0xFF1C303A),
    onSurfaceVariant = Color(0xFFC2D2DA),
    outline = Color(0xFF68808A),
)

@Composable
fun NeuroViewTheme(content: @Composable () -> Unit) {
    // Dark-first visual identity; light mode can be introduced as an option later.
    MaterialTheme(
        colorScheme = DarkPalette,
        typography = Typography(),
        content = content,
    )
}
