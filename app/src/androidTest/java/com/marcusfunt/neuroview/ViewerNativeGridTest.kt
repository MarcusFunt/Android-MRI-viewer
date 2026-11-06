package com.marcusfunt.neuroview

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.mri.SpatialAffine
import com.marcusfunt.neuroview.ui.VolumeLoadState
import com.marcusfunt.neuroview.ui.screens.OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY
import com.marcusfunt.neuroview.ui.screens.VIEWER_DISPLAY_PREFERENCES
import com.marcusfunt.neuroview.ui.screens.ViewerScreen
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class ViewerNativeGridTest {
    @get:Rule val compose = createComposeRule()

    private fun volume(affine: SpatialAffine) = NiftiVolume(
        nx = 16,
        ny = 12,
        nz = 8,
        values = FloatArray(16 * 12 * 8) { index -> (index % 255).toFloat() },
        affine = affine,
        percentileLow = 0f,
        percentileHigh = 255f,
        transformSource = "sform (RAS+)",
        timepoints = 1,
    )

    @Test
    fun obliqueNativeGridStartsExplicitlyAndCanReturnFromReconstruction() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = targetContext.getSharedPreferences(VIEWER_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
        val wasPresent = preferences.contains(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY)
        val previous = preferences.getBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, true)
        val angle = Math.toRadians(15.0)
        val oblique = SpatialAffine.fromRows(doubleArrayOf(
            1.0, 0.0, 0.0, 0.0,
            0.0, 2.0 * kotlin.math.cos(angle), -2.5 * kotlin.math.sin(angle), 0.0,
            0.0, 2.0 * kotlin.math.sin(angle), 2.5 * kotlin.math.cos(angle), 0.0,
        ))
        val obliqueVolume = volume(oblique)
        preferences.edit().putBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, true).commit()
        val documentUri = "content://native-grid-test/oblique-${System.nanoTime()}"
        try {
            compose.setContent {
                NeuroViewTheme {
                    ViewerScreen(
                        volumeState = VolumeLoadState.Ready("oblique", obliqueVolume),
                        selectedDocumentUri = documentUri,
                        onFirstSliceBitmapReady = {},
                        onFirstSliceDisplayed = {},
                        onFirstSliceRenderStarted = { _, _, _ -> },
                        onFirstSliceRenderFinished = { _, _, _ -> },
                        onCancelLoad = {}, onRetryLoad = {}, onReturnToLibrary = {},
                        onImport = {}, onNavigate = {},
                    )
                }
            }
            compose.waitUntil(15_000L) {
                compose.onAllNodesWithContentDescription("Native · oblique MRI slice")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Native · oblique").assertIsDisplayed()
            compose.onNodeWithText("Return to Axial").assertIsDisplayed()
            compose.onNodeWithText("Coronal").performClick()
            compose.waitUntil(10_000L) {
                compose.onAllNodesWithContentDescription("Real Coronal MRI slice")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Native · oblique").assertIsDisplayed().performClick()
            compose.waitUntil(10_000L) {
                compose.onAllNodesWithContentDescription("Native · oblique MRI slice")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Axial").assertExists()
        } finally {
            if (wasPresent) preferences.edit().putBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, previous).commit()
            else preferences.edit().remove(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY).commit()
        }
    }

    @Test
    fun alignedAnisotropicGridOpensUnderItsAnatomicalTabWithAMarker() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = targetContext.getSharedPreferences(VIEWER_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
        val wasPresent = preferences.contains(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY)
        val previous = preferences.getBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, true)
        preferences.edit().putBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, true).commit()
        val documentUri = "content://native-grid-test/aligned-${System.nanoTime()}"
        val alignedVolume = volume(SpatialAffine.axisAligned(1.0, 1.0, 2.5))
        try {
            compose.setContent {
                NeuroViewTheme {
                    ViewerScreen(
                        volumeState = VolumeLoadState.Ready(
                            "axial native", alignedVolume,
                        ),
                        selectedDocumentUri = documentUri,
                        onFirstSliceBitmapReady = {},
                        onFirstSliceDisplayed = {},
                        onFirstSliceRenderStarted = { _, _, _ -> },
                        onFirstSliceRenderFinished = { _, _, _ -> },
                        onCancelLoad = {}, onRetryLoad = {}, onReturnToLibrary = {},
                        onImport = {}, onNavigate = {},
                    )
                }
            }
            compose.waitUntil(15_000L) {
                compose.onAllNodesWithContentDescription("Native grid MRI slice")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Axial").assertIsDisplayed()
            compose.onNode(isSelectable() and hasText("Native grid", substring = true))
                .assertExists()
        } finally {
            if (wasPresent) preferences.edit().putBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, previous).commit()
            else preferences.edit().remove(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY).commit()
        }
    }

    @Test
    fun reopeningAnObliqueNativeGridNoteRestoresItsStoredAxisAndSlice() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = targetContext.getSharedPreferences(VIEWER_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
        val wasPresent = preferences.contains(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY)
        val previous = preferences.getBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, true)
        preferences.edit().putBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, false).commit()
        val angle = Math.toRadians(15.0)
        val oblique = SpatialAffine.fromRows(doubleArrayOf(
            1.0, 0.0, 0.0, 0.0,
            0.0, 2.0 * kotlin.math.cos(angle), -2.5 * kotlin.math.sin(angle), 0.0,
            0.0, 2.0 * kotlin.math.sin(angle), 2.5 * kotlin.math.cos(angle), 0.0,
        ))
        try {
            compose.setContent {
                NeuroViewTheme {
                    ViewerScreen(
                        volumeState = VolumeLoadState.Ready("oblique note", volume(oblique)),
                        selectedDocumentUri = "content://native-grid-test/note-${System.nanoTime()}",
                        initialNoteId = "oblique-note",
                        initialNotePlane = "AXIAL",
                        initialNoteSlice = 3,
                        initialNoteNativeStackAxis = 2,
                        onFirstSliceBitmapReady = {},
                        onFirstSliceDisplayed = {},
                        onFirstSliceRenderStarted = { _, _, _ -> },
                        onFirstSliceRenderFinished = { _, _, _ -> },
                        onCancelLoad = {}, onRetryLoad = {}, onReturnToLibrary = {},
                        onImport = {}, onNavigate = {},
                    )
                }
            }
            compose.waitUntil(15_000L) {
                compose.onAllNodesWithContentDescription("Native · oblique MRI slice")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("4 / 8").assertExists()
        } finally {
            if (wasPresent) preferences.edit().putBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, previous).commit()
            else preferences.edit().remove(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY).commit()
        }
    }
}
