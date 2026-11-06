package com.marcusfunt.neuroview

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.ComposeView
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import com.marcusfunt.neuroview.mri.MriPlane
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.mri.SpatialAffine
import com.marcusfunt.neuroview.notes.ScanNote
import com.marcusfunt.neuroview.notes.ScanNoteStore
import com.marcusfunt.neuroview.ui.VolumeLoadState
import com.marcusfunt.neuroview.ui.DrawOrderDiagnostics
import com.marcusfunt.neuroview.ui.screens.ViewerScreen
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme

/** Emulator smoke tests: app starts and high-value navigation is functional. */
class NeuroViewNavigationSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeExposesPrimaryActionsWithoutOpeningAnotherScreen() {
        compose.onNodeWithText("NeuroView").assertExists()
        compose.onNodeWithText("Browse scans").assertExists()
        compose.onNodeWithText("Import MRI").assertExists()
        compose.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun brainPreviewSignalsItsFirstDrawWithoutAnArbitraryDelay() {
        val marker = "brain-preview-first-frame-drawn"
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(marker).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(marker).assertExists()
    }

    @Test
    fun firstBrainFrameIsVisibleInTheWindowWithoutTouch() {
        val marker = "brain-preview-first-frame-drawn"
        compose.onNodeWithText("Browse scans").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(marker).fetchSemanticsNodes().isNotEmpty()
        }

        // GL submission and SurfaceTexture updates can precede the window screenshot by a frame.
        // Require actual rendered brain pixels to appear without a user touch, not only a GL marker.
        var visiblePixels = 0
        compose.waitUntil(timeoutMillis = 10_000) {
            val screenshot = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().uiAutomation.takeScreenshot()
            visiblePixels = countVisibleBrainPixels(screenshot)
            visiblePixels >= 1_000
        }
        assertTrue(
            "The Home brain never became visible without user interaction (pixels=$visiblePixels)",
            visiblePixels >= 1_000,
        )
    }

    @Test
    fun homeCompositorDiagnosticsAreActuallyPaintedOnScreen() {
        DrawOrderDiagnostics.enabledForTests = true
        try {
        val marker = "brain-preview-first-frame-drawn"
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(marker).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-preview-overlay-diagnostic").assertExists()
        compose.onNodeWithTag("home-preview-behind-diagnostic").assertExists()

        val screenshot = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().uiAutomation.takeScreenshot()
        assertTrue(
            "The Compose overlay above the brain was not painted into the screen",
            countDiagnosticPixels(screenshot) { red, green, blue ->
                red > 200 && green < 80 && blue > 200
            } >= 100,
        )
        assertTrue(
            "The Compose rectangle behind the brain was not painted into the screen",
            countDiagnosticPixels(screenshot) { red, green, blue ->
                red < 80 && green > 150 && blue > 150
            } >= 100,
        )
        } finally {
            DrawOrderDiagnostics.enabledForTests = false
        }
    }

    @Test
    fun homeDrawOrderMarkersAreAbsentInAnOrdinaryDebugBuild() {
        DrawOrderDiagnostics.enabledForTests = false
        val marker = "brain-preview-first-frame-drawn"
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(marker).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-preview-overlay-diagnostic").assertDoesNotExist()
        compose.onNodeWithTag("home-preview-behind-diagnostic").assertDoesNotExist()
    }

    @Test
    fun homeCanOpenUnifiedLibrary() {
        compose.onNodeWithText("NeuroView").assertExists()
        compose.onNodeWithText("Browse scans").performClick()
        compose.onNodeWithText("MRI Library").assertExists()
        compose.onNodeWithText("Import MRI volumes").assertExists()
    }

    @Test
    fun settingsControlRotationAndGlowAndReturnHome() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Automatically rotate brain").assertExists()
        compose.onNodeWithText("Ambient background glow").assertExists()
        compose.onNodeWithText("Reset brain orientation").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Browse scans").assertExists()
    }

    @Test
    fun settingsExposesResponsiveScrollControls() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Responsive scrolling").assertExists()
        compose.onNodeWithText("Scrolling image quality").assertExists()
        compose.onNodeWithText("Full image quality returns when you stop moving.").assertExists()
    }

    @Test
    fun homeCanOpenNotesAndRecoverToLibraryWhenEmpty() {
        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithText("No notes yet").assertExists()
        compose.onNodeWithText("Add a note while viewing a scan. It will be saved on this device with its series and slice location.")
            .assertExists()
        compose.onNodeWithText("Browse scans").performClick()
        compose.onNodeWithText("MRI Library").assertExists()
    }

    @Test
    fun notesRemainReadableWhenTheirScanIsUnavailable() {
        val store = ScanNoteStore(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
        )
        val note = ScanNote(
            id = "smoke-unavailable-note",
            scanId = "scan-removed-from-library",
            collectionLabel = "Test import",
            seriesLabel = "T1 test scan",
            planeName = "AXIAL",
            sliceIndex = 4,
            sliceCount = 10,
            createdAtMillis = 1_797_000_000_000L,
            body = "Keep this observation available for reference.\nSecond line.\nThird line.\nFourth line remains readable.",
        )
        store.save(note)
        try {
            compose.onNodeWithText("Notes").performClick()
            compose.onNodeWithText(note.body).assertExists()
            compose.onNodeWithText("Unavailable · scan removed from library").assertExists()
            compose.onNodeWithText(note.body).performClick()
            compose.onNodeWithText("Note").assertExists()
            compose.onNodeWithText(note.body).assertExists()
            compose.onNodeWithText("orientation unverified", substring = true).assertExists()
            compose.onNodeWithText("Open saved slice").assertDoesNotExist()
        } finally {
            store.delete(note.id)
        }
    }

    @Test
    fun viewerCanSaveAnObservationAtTheCurrentSlice() {
        var saved: Triple<MriPlane, Int, Boolean>? = null
        val volume = NiftiVolume(
            5, 5, 5,
            FloatArray(125) { it.toFloat() },
            SpatialAffine.axisAligned(1.0, 1.0, 1.0),
            0f, 124f, "test fixture", 1,
        )
        compose.activityRule.scenario.onActivity { activity ->
            val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                .getChildAt(0) as ComposeView
            content.setContent {
                NeuroViewTheme {
                    ViewerScreen(
                        volumeState = VolumeLoadState.Ready("Synthetic viewer fixture", volume),
                        onFirstSliceBitmapReady = {},
                        onFirstSliceDisplayed = {},
                        onFirstSliceRenderStarted = { _, _, _ -> },
                        onFirstSliceRenderFinished = { _, _, _ -> },
                        onCancelLoad = {},
                        onRetryLoad = {},
                        onReturnToLibrary = {},
                        onSaveNote = { plane, slice, _, _, orientationVerified, body ->
                            if (body == "Check this slice") saved = Triple(plane, slice, orientationVerified)
                        },
                        onImport = {},
                        onNavigate = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Add note").performClick()
        compose.onNodeWithText("Observation").performTextInput("Check this slice")
        compose.onNodeWithText("Save note").performClick()
        compose.onNodeWithText("Note saved on this device.").assertExists()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(Triple(MriPlane.AXIAL, 2, true), saved)
        }
    }

    @Test
    fun bundledBrainAssetLoadsOnAndroid() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val mesh = com.marcusfunt.neuroview.brain.BrainAssetLoader.load(
            context.assets.open("landing_brain.nvbm"),
        )
        org.junit.Assert.assertEquals(43_872, mesh.vertexCount)
        org.junit.Assert.assertEquals(268_584, mesh.indices.size)
    }

    @Test
    fun libraryShowsConversionAndManagementActions() {
        compose.onNodeWithText("Browse scans").performClick()
        compose.onNodeWithText("MRI Library").assertExists()
        compose.onNodeWithText("Collections").performClick()
        compose.onNodeWithText("+ New collection").assertExists()
        compose.onNodeWithText("Conversion tools").performClick()
        compose.onNodeWithText("Select DICOM folder").assertExists()
        compose.onNodeWithText("Select DICOM files instead").assertExists()
        compose.onNodeWithText("Import MRI volumes").assertExists()
    }

    @Test
    fun expandedConversionHelpKeepsEmptyLibraryStateVisible() {
        compose.onNodeWithText("Browse scans").performClick()
        compose.onNodeWithText("Convert DICOM").performClick()
        compose.onNodeWithText("Compatibility and export help").performClick()

        compose.onNodeWithText("Your library is empty").assertIsDisplayed()
    }

    private fun countVisibleBrainPixels(bitmap: Bitmap): Int {
        val left = bitmap.width * 25 / 100
        val right = bitmap.width * 75 / 100
        val top = bitmap.height * 15 / 100
        val bottom = bitmap.height * 65 / 100
        var count = 0
        for (y in top until bottom step 2) {
            for (x in left until right step 2) {
                val pixel = bitmap.getPixel(x, y)
                val red = Color.red(pixel)
                if (red > Color.green(pixel) + 35 && red > Color.blue(pixel) + 45 && red > 75) {
                    count++
                }
            }
        }
        return count
    }

    private fun countDiagnosticPixels(
        bitmap: Bitmap,
        isDiagnosticColor: (red: Int, green: Int, blue: Int) -> Boolean,
    ): Int {
        var count = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (isDiagnosticColor(Color.red(pixel), Color.green(pixel), Color.blue(pixel))) {
                    count++
                }
            }
        }
        return count
    }
}
