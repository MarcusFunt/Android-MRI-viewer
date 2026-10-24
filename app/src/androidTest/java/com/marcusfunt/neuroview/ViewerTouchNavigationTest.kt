package com.marcusfunt.neuroview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.mri.*
import com.marcusfunt.neuroview.ui.screens.MriContent
import com.marcusfunt.neuroview.ui.screens.PrecisionSliceSlider
import com.marcusfunt.neuroview.ui.screens.createViewerBitmap
import com.marcusfunt.neuroview.ui.screens.ViewerScreen
import com.marcusfunt.neuroview.ui.VolumeLoadState
import androidx.test.platform.app.InstrumentationRegistry
import com.marcusfunt.neuroview.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic only. Run on the explicitly selected disposable emulator, then a real touchscreen. */
class ViewerTouchNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val volume = NiftiVolume(16, 16, 256, FloatArray(16 * 16 * 256) { (it % 256).toFloat() },
        SpatialAffine.axisAligned(1.0, 1.0, 1.0), 0f, 255f, "sform (synthetic RAS+)", 1)

    @Test fun heldImageDragSurvivesRepeatedBitmapAndMotionChanges() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(frame(0))
        val motion = mutableStateOf(MotionPreference.SYSTEM)
        val scrubbing = mutableStateOf(false)
        val rerenderCount = mutableIntStateOf(0)
        val observedSlices = mutableListOf<Int>()
        compose.setContent {
            Harness(
                slice,
                bitmap,
                motion.value,
                scrubbing = scrubbing,
                onSliceChange = { nextSlice ->
                    slice.intValue = nextSlice
                    observedSlices += nextSlice
                    bitmap.value = frame(++rerenderCount.intValue)
                    motion.value = MotionPreference.entries[rerenderCount.intValue % MotionPreference.entries.size]
                },
            )
        }
        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        image.performTouchInput {
            // Use the touch scope's untransformed node-local size. boundsInRoot includes
            // the image's 1.35x zoom layer and gives coordinates outside the pointer box.
            swipe(
                start = Offset(centerX, height * 0.85f),
                end = Offset(centerX, height * 0.15f),
                durationMillis = 1_600,
            )
        }
        compose.runOnIdle {
            assertTrue("Drag did not commit render frames", rerenderCount.intValue > 0)
            assertTrue(
                "Long drag truncated: ${slice.intValue}; rerenders=${rerenderCount.intValue}; observed=$observedSlices",
                slice.intValue >= 140,
            )
            assertFalse("Scrubbing remained active after release", scrubbing.value)
        }
    }

    @Test fun manualDragAndAccessibleSliderAdjustmentAreIdenticalInAllMotionModes() {
        val slice = mutableIntStateOf(128)
        val motion = mutableStateOf(MotionPreference.SYSTEM)
        val bitmap = mutableStateOf(frame(0))
        compose.setContent { Harness(slice, bitmap, motion.value) }
        val results = mutableListOf<Int>()
        MotionPreference.entries.forEach { preference ->
            compose.runOnIdle { slice.intValue = 128; motion.value = preference }
            compose.onNodeWithContentDescription("Real Axial MRI slice").performTouchInput {
                swipe(Offset(centerX, height * 0.8f), Offset(centerX, height * 0.3f), durationMillis = 1_200)
            }
            compose.runOnIdle { results += slice.intValue }
            compose.onNodeWithContentDescription("Slice position").performSemanticsAction(SemanticsActions.SetProgress) { it(201f) }
            compose.runOnIdle { assertEquals(201, slice.intValue) }
        }
        assertTrue(results.first() > 128)
        assertEquals(listOf(results.first(), results.first(), results.first()), results)
    }

    @Test fun precisionThumbMovesUpWithoutJumpAndReleaseKeepsSelection() {
        val slice = mutableIntStateOf(128)
        val scrubbing = mutableStateOf(false)
        compose.setContent {
            NeuroViewTheme {
                PrecisionSliceSlider(slice.intValue, 256, "synthetic", { slice.intValue = it },
                    { scrubbing.value = it }, Modifier.width(320.dp))
            }
        }
        val slider = compose.onNodeWithContentDescription("Slice position")
        slider.performTouchInput { down(center); moveBy(Offset(0f, -180f), delayMillis = 100) }
        compose.runOnIdle { assertEquals(128, slice.intValue) }
        slider.performTouchInput { moveBy(Offset(40f, 0f), delayMillis = 100) }
        val exact = slice.intValue
        slider.performTouchInput { moveBy(Offset(0f, 180f), delayMillis = 100); up() }
        compose.runOnIdle { assertEquals(exact, slice.intValue); assertFalse(scrubbing.value) }
    }

    @Test fun focusPreservesZoomAndSelectedSlice() {
        val slice = mutableIntStateOf(137)
        val focus = mutableStateOf(false)
        val bitmap = mutableStateOf(frame(0))
        compose.setContent { Harness(slice, bitmap, focus = focus) }
        compose.onNodeWithText("Scroll").performClick()
        compose.onNodeWithText("135% · drag to pan · pinch to zoom").assertExists()
        compose.onNodeWithContentDescription("More viewer actions").performClick()
        compose.onNodeWithText("Focus mode").performClick()
        compose.onNodeWithText("Exit focus").performClick()
        compose.onNodeWithText("135% · drag to pan · pinch to zoom").assertExists()
        compose.onNodeWithText("138 / 256").assertExists()
        compose.runOnIdle { assertEquals(137, slice.intValue) }
    }

    @Test fun commonViewerActionsStayVisibleAndProcessingRemainsInOverflow() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(frame(0))
        compose.setContent { Harness(slice, bitmap) }
        compose.onNodeWithContentDescription("Gesture mode").assertExists()
        compose.onNodeWithContentDescription("Display adjustments").assertExists()
        compose.onNodeWithContentDescription("More viewer actions").performClick()
        compose.onNodeWithText("Focus mode").assertExists()
        compose.onNodeWithText("Process with BrainSuite").performClick()
        compose.onNodeWithText("MRI processing").assertExists()
    }

    @Test fun viewerSettingsOpenAtViewerControlsWithoutManualScrolling() {
        compose.setContent {
            NeuroViewTheme {
                ViewerScreen(
                    VolumeLoadState.Ready("synthetic", volume),
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
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Real Axial MRI slice").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("Viewer settings").performClick()

        compose.onNodeWithText("MRI viewer").assertIsDisplayed()
        compose.onNodeWithText("Show previous/next slice buttons").assertIsDisplayed()
    }

    @Test fun displayedDarkGrayscaleRampIsOpaqueAndUnchanged() {
        val ramp = IntArray(33) { intensity -> 0xff000000.toInt() or (intensity * 0x010101) }
        val bitmap = createViewerBitmap(MprImage(33, 1, ramp))
        ramp.indices.forEach { x -> assertEquals("Intensity $x", ramp[x], bitmap.getPixel(x, 0)) }
    }

    @Test fun stepButtonsDefaultOffAndSettingUpdatesImmediatelyWithoutMovingSlice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("neuroview_display_v1", android.content.Context.MODE_PRIVATE)
        val original = preferences.getBoolean("show_slice_buttons", false)
        preferences.edit().putBoolean("show_slice_buttons", false).commit()
        try {
            compose.setContent {
                NeuroViewTheme {
                    ViewerScreen(VolumeLoadState.Ready("synthetic", volume), onFirstSliceBitmapReady = {},
                        onFirstSliceDisplayed = {}, onFirstSliceRenderStarted = { _, _, _ -> },
                        onFirstSliceRenderFinished = { _, _, _ -> }, onCancelLoad = {}, onRetryLoad = {},
                        onReturnToLibrary = {}, onImport = {}, onNavigate = {})
                }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Real Axial MRI slice").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Previous slice").assertDoesNotExist()
            compose.onNodeWithContentDescription("Next slice").assertDoesNotExist()
            compose.onNodeWithContentDescription("Slice position").performSemanticsAction(SemanticsActions.SetProgress) { it(137f) }
            compose.onNodeWithContentDescription("Viewer settings").performClick()
            compose.onNodeWithText("Show previous/next slice buttons").performScrollTo().performClick()
            compose.onNodeWithText("Done").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Previous slice").assertExists()
            compose.onNodeWithContentDescription("Next slice").assertExists()
            compose.onNodeWithText("138 / 256").assertExists()
            assertTrue(preferences.getBoolean("show_slice_buttons", false))
        } finally {
            preferences.edit().putBoolean("show_slice_buttons", original).commit()
        }
    }

    @Composable private fun Harness(
        slice: MutableIntState,
        bitmap: MutableState<Bitmap>,
        motion: MotionPreference = MotionPreference.SYSTEM,
        focus: MutableState<Boolean> = remember { mutableStateOf(false) },
        scrubbing: MutableState<Boolean> = remember { mutableStateOf(false) },
        onSliceChange: ((Int) -> Unit)? = null,
    ) {
        NeuroViewTheme {
            CompositionLocalProvider(LocalNeuroViewMotion provides motion) {
                    MriContent(Modifier.fillMaxSize(), focus.value, false, false, { focus.value = !focus.value },
                        false, false, {}, 3f, volume, MriPlane.AXIAL, bitmap.value, false, {},
                        slice.intValue, 256, onSliceChange ?: { slice.intValue = it }, { scrubbing.value = it }, {},
                        null, "synthetic", null, {}, 0.4f, {}, 0.8f, {})
            }
        }
    }

    private fun frame(value: Int): Bitmap = Bitmap.createBitmap(
        intArrayOf(0xff000000.toInt() or (value.coerceIn(0, 255) * 0x010101)), 1, 1, Bitmap.Config.ARGB_8888)
}
