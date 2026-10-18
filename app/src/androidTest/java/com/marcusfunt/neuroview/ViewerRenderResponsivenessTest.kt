package com.marcusfunt.neuroview

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.mri.SpatialAffine
import com.marcusfunt.neuroview.ui.VolumeLoadState
import com.marcusfunt.neuroview.ui.screens.ViewerScreen
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Rule
import org.junit.Test

class ViewerRenderResponsivenessTest {
    @get:Rule
    val compose = createComposeRule()

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
            compose.setContent {
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

            compose.waitUntil(timeoutMillis = 15_000L) {
                compose.onAllNodesWithContentDescription("Real Axial MRI slice").fetchSemanticsNodes().isNotEmpty()
            }
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
}
