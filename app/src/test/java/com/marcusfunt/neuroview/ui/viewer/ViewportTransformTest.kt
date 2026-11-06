package com.marcusfunt.neuroview.ui.viewer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportTransformTest {
    @Test
    fun fittedAspectRatioMapsBothImageCornersToTheirVisibleBounds() {
        val transform = ViewportTransform(
            viewportWidthPx = 400,
            viewportHeightPx = 200,
            sourceWidthPx = 100,
            sourceHeightPx = 100,
        )

        assertEquals(Offset(100f, 0f), transform.screenPointForImageFraction(Offset.Zero))
        assertEquals(Offset(300f, 200f), transform.screenPointForImageFraction(Offset(1f, 1f)))
        assertEquals(Offset.Zero, transform.imageFractionForScreenPoint(Offset(100f, 0f)))
        assertEquals(Offset(1f, 1f), transform.imageFractionForScreenPoint(Offset(300f, 200f)))
    }

    @Test
    fun zoomKeepsTheImagePointUnderTheGestureCentroidFixed() {
        val transform = ViewportTransform(400, 200, 100, 100)
        val pivot = Offset(140f, 70f)
        val imageFraction = transform.imageFractionForScreenPoint(pivot)
        assertNotNull(imageFraction)

        val zoomed = transform.transformed(centroid = pivot, panDelta = Offset.Zero, zoomFactor = 4f)
        val movedPivot = requireNotNull(zoomed.screenPointForImageFraction(requireNotNull(imageFraction)))
        assertTrue(kotlin.math.abs(movedPivot.x - pivot.x) < 0.01f)
        assertTrue(kotlin.math.abs(movedPivot.y - pivot.y) < 0.01f)
    }

    @Test
    fun panIsClampedToTheTransformedImageBoundsAndFitRecenters() {
        val fitted = ViewportTransform(400, 200, 100, 100)
        assertEquals(Offset.Zero,
            fitted.transformed(Offset(200f, 100f), Offset(900f, -900f), zoomFactor = 1f).panOffset)

        val zoomed = fitted.transformed(Offset(200f, 100f), Offset(900f, -900f), zoomFactor = 3f)
        assertEquals(Offset(100f, -200f), zoomed.panOffset)
        assertEquals(Offset.Zero, zoomed.fit().panOffset)
        assertEquals(1f, zoomed.fit().scale)
    }
}
