package com.marcusfunt.neuroview.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Consistent decorative gradients. None of these brushes are used on MRI pixels;
 * they belong exclusively to navigation chrome and surrounding surfaces.
 */
object NeuroGradients {
    val midnight = Color(0xFF07121F)
    val tealInk = Color(0xFF0C2632)
    val indigoInk = Color(0xFF1B1D3E)
    val cyan = Color(0xFF90F6EC)
    val mint = Color(0xFF67DACA)
    val primary = Brush.horizontalGradient(
        listOf(Color(0xFF8FF3E8), Color(0xFF61C9DE), Color(0xFF96ACF2)),
    )
    val hero = Brush.linearGradient(
        listOf(Color(0xFF183A49), Color(0xFF122C40), Color(0xFF252446)),
    )
    val secondary = Brush.linearGradient(
        listOf(Color(0xFF1C3648), Color(0xFF1B263B), Color(0xFF292741)),
    )
    val trim = Brush.linearGradient(
        listOf(Color(0xFF69EBDD), Color(0xFF4B8DC4), Color(0xFF9C87E5)),
    )
    val backdrop = Brush.verticalGradient(
        listOf(midnight, tealInk, Color(0xFF0D1E33), indigoInk),
    )
}

/** Static bloom: cached with the layout size, with no animation/frame overhead. */
fun Modifier.neuroGradientBackdrop(): Modifier = this
    .background(NeuroGradients.backdrop)
    .drawWithCache {
        val glow = Brush.radialGradient(
            colors = listOf(
                Color(0xFF3B8F9D).copy(alpha = 0.31f),
                Color(0xFF224D68).copy(alpha = 0.14f),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.73f, size.height * 0.23f),
            radius = size.width.coerceAtLeast(1f) * 1.18f,
        )
        val lowerGlow = Brush.radialGradient(
            colors = listOf(Color(0xFF6660A3).copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.95f),
            radius = size.width.coerceAtLeast(1f) * 1.04f,
        )
        onDrawBehind {
            drawRect(glow)
            drawRect(lowerGlow)
        }
    }
