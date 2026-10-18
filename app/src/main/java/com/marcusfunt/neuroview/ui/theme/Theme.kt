package com.marcusfunt.neuroview.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Calm, high-contrast dark surfaces keep grayscale medical images visually neutral.
private val DarkPalette = darkColorScheme(
    primary = Color(0xFF79D6E6),
    onPrimary = Color(0xFF003638),
    primaryContainer = Color(0xFF19464D),
    onPrimaryContainer = Color(0xFFB4FAF5),
    secondary = Color(0xFFABC1CD),
    onSecondary = Color(0xFF20343D),
    background = Color(0xFF09121D),
    onBackground = Color(0xFFF1F6F8),
    surface = Color(0xFF121F2D),
    onSurface = Color(0xFFF1F6F8),
    surfaceVariant = Color(0xFF20303F),
    onSurfaceVariant = Color(0xFFB0C3CE),
    outline = Color(0xFF617687),
    outlineVariant = Color(0xFF304555),
    error = Color(0xFFFFB4AB),
)

private val NeuroTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Typography().headlineMedium.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val NeuroShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun NeuroViewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkPalette,
        typography = NeuroTypography,
        shapes = NeuroShapes,
        content = content,
    )
}
