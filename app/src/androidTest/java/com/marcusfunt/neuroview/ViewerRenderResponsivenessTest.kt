package com.marcusfunt.neuroview

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.ComposeView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.mri.SpatialAffine
import com.marcusfunt.neuroview.ui.VolumeLoadState
import com.marcusfunt.neuroview.ui.screens.ViewerScreen
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class ViewerRenderResponsivenessTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun planeChangeDropsOldBitmapUntilCorrectlyLabeledReplacementIsReady() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val displayPreferences = targetContext.getSharedPreferences(
            "neuroview_display_v1", android.content.Context.MODE_PRIVATE,
        )
        val originalSettings = displayPreferences.all
        val values = FloatArray(16 * 16 * 16) { index -> (index % 256).toFloat() }
        val volume = NiftiVolume(
            nx = 16,
            ny = 16,
            nz = 16,
            values = values,
            affine = SpatialAffine.axisAligned(1.0, 1.0, 1.0),
            percentileLow = 0f,
            percentileHigh = 255f,
            transformSource = "sform (RAS+)",
            timepoints = 1,
        )

        try {
            compose.activityRule.scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    .getChildAt(0) as ComposeView
                content.setContent {
                    NeuroViewTheme {
                        ViewerScreen(
                            volumeState = VolumeLoadState.Ready("synthetic MRI", volume),
                            onFirstSliceBitmapReady = {},
                            onFirstSliceDisplayed = {},
                            onFirstSliceRenderStarted = { _, _, _ -> },
                            onFirstSliceRenderFinished = { _, _, _ -> },
                            onCancelLoad = {},
                            onRetryLoad = {},
                            onReturnToLibrary = {},
                            onImport = {},
                            onNavigate = {},
                        )
                    }
                }
            }

            compose.waitUntil(timeoutMillis = 15_000L) {
                compose.onAllNodesWithContentDescription("Real Axial MRI slice").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Axial").assertIsDisplayed()
            compose.onNodeWithText("Coronal").assertIsDisplayed()
            compose.onNodeWithText("Sagittal").assertIsDisplayed()
            val sliceBitmap = compose.onNodeWithContentDescription("Real Axial MRI slice")
                .captureToImage().asAndroidBitmap()
            assertTrue(
                "The clipped Viewer viewport did not retain visible grayscale slice pixels",
                countIntermediateGrayPixels(sliceBitmap) >= 1_000,
            )
            val markerNode = compose.onNodeWithTag("viewer-tabs-overlay-diagnostic")
            markerNode.assertIsDisplayed()
            val markerBitmap = markerNode
                .captureToImage().asAndroidBitmap()
            val capturedMarkerPixels = countMagentaPixels(markerBitmap)
            val centerPixel = markerBitmap.getPixel(markerBitmap.width / 2, markerBitmap.height / 2)
            assertTrue(
                "Compose did not rasterize its draw-order marker over the plane tabs " +
                    "(${markerBitmap.width}x${markerBitmap.height}, $capturedMarkerPixels magenta samples, " +
                    "center=#${Integer.toHexString(centerPixel)})",
                capturedMarkerPixels >= 25,
            )
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            assertTrue(
                "The magenta Compose draw-order marker over the plane tabs was not visible on screen",
                countMagentaPixels(screenshot) >= 25,
            )
            compose.onNodeWithText("Sagittal").performClick()
            compose.waitUntil(timeoutMillis = 15_000L) {
                compose.onAllNodesWithContentDescription("Real Sagittal MRI slice").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Real Axial MRI slice").assertDoesNotExist()
        } finally {
            displayPreferences.edit().clear().commit()
            originalSettings.forEach { (key, value) ->
                val editor = displayPreferences.edit()
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
                editor.commit()
            }
        }
    }

    private fun countMagentaPixels(bitmap: android.graphics.Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 200 && Color.green(pixel) < 80 && Color.blue(pixel) > 200) {
                    count++
                }
            }
        }
        return count
    }

    private fun countIntermediateGrayPixels(bitmap: android.graphics.Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                val red = Color.red(pixel)
                if (red in 8..247 &&
                    kotlin.math.abs(red - Color.green(pixel)) <= 2 &&
                    kotlin.math.abs(red - Color.blue(pixel)) <= 2
                ) {
                    count++
                }
            }
        }
        return count
    }
}
