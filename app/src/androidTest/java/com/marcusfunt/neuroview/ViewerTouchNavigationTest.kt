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
        compose.setContent { Harness(slice, bitmap, motion.value, scrubbing = scrubbing) }
        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        val height = image.fetchSemanticsNode().boundsInRoot.height
        image.performTouchInput { down(Offset(centerX, height * 0.95f)) }
        repeat(24) { step ->
            image.performTouchInput { moveBy(Offset(0f, -height * 0.033f), delayMillis = 24) }
            compose.runOnIdle {
                bitmap.value = frame(step + 1) // Deliberately commit frames with pointer still DOWN.
                motion.value = MotionPreference.entries[step % 3]
            }
        }
        compose.runOnIdle {
            assertTrue("Drag canceled by recomposition", scrubbing.value)
            assertTrue("Long drag truncated: ${slice.intValue}", slice.intValue >= 140)
        }
        image.performTouchInput { up() }
        compose.runOnIdle { assertFalse(scrubbing.value) }
    }

    @Test fun manualDragAndAccessibleSliderAdjustmentAreIdenticalInAllMotionModes() {
        val slice = mutableIntStateOf(128)
        val motion = mutableStateOf(MotionPreference.SYSTEM)
        compose.setContent { Harness(slice, mutableStateOf(frame(0)), motion.value) }
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
        compose.setContent { Harness(slice, mutableStateOf(frame(0)), focus = focus) }
        compose.onNodeWithText("Zoom", useUnmergedTree = true).performClick()
        compose.onNodeWithText("135% · drag to pan · pinch to zoom").assertExists()
        compose.onNodeWithText("Focus").performClick()
        compose.onNodeWithText("Exit focus").performClick()
        compose.onNodeWithText("135% · drag to pan · pinch to zoom").assertExists()
        compose.onNodeWithText("138 / 256").assertExists()
        compose.runOnIdle { assertEquals(137, slice.intValue) }
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
    ) {
        NeuroViewTheme {
            CompositionLocalProvider(LocalNeuroViewMotion provides motion) {
                    MriContent(Modifier.fillMaxSize(), focus.value, false, false, { focus.value = !focus.value },
                        false, false, {}, 3f, volume, MriPlane.AXIAL, bitmap.value, false, {},
                        slice.intValue, 256, { slice.intValue = it }, { scrubbing.value = it }, {},
                        null, "synthetic", null, {}, 0.4f, {}, 0.8f, {})
            }
        }
    }

    private fun frame(value: Int): Bitmap = Bitmap.createBitmap(
        intArrayOf(0xff000000.toInt() or (value.coerceIn(0, 255) * 0x010101)), 1, 1, Bitmap.Config.ARGB_8888)
}
