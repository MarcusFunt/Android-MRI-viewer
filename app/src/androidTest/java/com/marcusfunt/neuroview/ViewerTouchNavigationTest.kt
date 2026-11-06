package com.marcusfunt.neuroview

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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

    @Test fun defaultViewerFitShowsAllFourSourceCorners() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(cornerMarkerFrame())
        compose.setContent { Harness(slice, bitmap) }

        val displayed = compose.onNodeWithContentDescription("Real Axial MRI slice")
            .captureToImage().asAndroidBitmap()

        listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEach { expectedColor ->
            assertTrue("The default fit cropped source corner #${Integer.toHexString(expectedColor)}",
                countExactPixels(displayed, expectedColor) > 0)
        }
    }

    @Test fun viewerDoesNotRequireZoomOrScrollModeSelection() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(frame(0))
        compose.setContent { Harness(slice, bitmap) }

        compose.onNodeWithContentDescription("Gesture mode").assertDoesNotExist()
        compose.onNodeWithText("Scroll").assertDoesNotExist()
        compose.onNodeWithText("Zoom").assertDoesNotExist()
    }

    @Test fun twoFingerPinchZoomsInBrowseModeWithoutChangingSlice() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(panPattern())
        compose.setContent { Harness(slice, bitmap) }
        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        val beforePinch = image.captureToImage().asAndroidBitmap()

        image.performTouchInput {
            down(0, Offset(centerX - 40f, centerY))
            down(1, Offset(centerX + 40f, centerY))
            updatePointerTo(0, Offset(centerX - 80f, centerY))
            updatePointerTo(1, Offset(centerX + 80f, centerY))
            move()
            up(1)
            up(0)
        }

        val afterPinch = image.captureToImage().asAndroidBitmap()
        assertTrue("A two-finger pinch in browse mode must zoom the MRI image.",
            differingPixels(beforePinch, afterPinch) > 0)
        compose.runOnIdle { assertEquals("Pinch must not navigate slices.", 128, slice.intValue) }
    }

    @Test fun addingAndRemovingPointersDuringPinchNeverFallsBackToSliceScrolling() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(panPattern())
        compose.setContent { Harness(slice, bitmap) }
        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        image.performTouchInput {
            down(0, Offset(centerX - 40f, centerY))
            down(1, Offset(centerX + 40f, centerY))
            updatePointerTo(0, Offset(centerX - 80f, centerY))
            updatePointerTo(1, Offset(centerX + 80f, centerY))
            move()
            up(1)
            moveBy(Offset(0f, 220f))
            down(1, Offset(centerX + 20f, centerY))
            down(2, Offset(centerX - 20f, centerY))
            moveBy(Offset(0f, 80f))
            up(2)
            up(1)
            moveBy(Offset(0f, -180f))
            up(0)
        }
        compose.runOnIdle { assertEquals("A pinch stream must stay owned through pointer changes.", 128, slice.intValue) }
    }

    @Test fun doubleTapZoomsFromFitAndTheNextDoubleTapReturnsToFit() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(cornerMarkerFrame())
        compose.setContent { Harness(slice, bitmap) }
        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        val cornersBefore = image.captureToImage().asAndroidBitmap()
        assertTrue(listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
            .all { countExactPixels(cornersBefore, it) > 0 })

        image.performTouchInput { doubleClick(center) }
        val zoomed = image.captureToImage().asAndroidBitmap()
        assertTrue("Double tap must zoom the image from Fit.",
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
                .any { countExactPixels(zoomed, it) == 0 })

        image.performTouchInput { doubleClick(center) }
        val fitted = image.captureToImage().asAndroidBitmap()
        assertTrue("The next double tap must restore the full source field of view.",
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
                .all { countExactPixels(fitted, it) > 0 })
    }

    @Test fun fitActionRestoresTheEntireSourceFieldOfView() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(cornerMarkerFrame())
        compose.setContent { Harness(slice, bitmap) }
        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        pinch(image)
        compose.onNodeWithText("Fit").performClick()
        val fitted = image.captureToImage().asAndroidBitmap()
        assertTrue(listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
            .all { countExactPixels(fitted, it) > 0 })
        compose.onNodeWithText("200% · pinch to zoom").assertDoesNotExist()
    }

    @Test fun sliceScrubbingPreservesZoomAndPan() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(panPattern())
        compose.setContent { Harness(slice, bitmap) }
        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        pinch(image)
        twoFingerPan(image, Offset(40f, 0f))
        val beforeScroll = image.captureToImage().asAndroidBitmap()

        image.performTouchInput {
            swipe(Offset(centerX, height * 0.8f), Offset(centerX, height * 0.2f), durationMillis = 600)
        }

        compose.onNodeWithText("200% · pinch to zoom").assertIsDisplayed()
        compose.runOnIdle { assertNotEquals("The test drag should scrub slices.", 128, slice.intValue) }
        assertEquals("Scrubbing must preserve image zoom and pan.", 0,
            differingPixels(beforeScroll, image.captureToImage().asAndroidBitmap()))
    }

    @Test fun twoFingerPinchRemainsAvailableWhileMeasuring() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(panPattern())
        compose.setContent { Harness(slice, bitmap) }
        compose.onNodeWithContentDescription("More viewer actions").performClick()
        compose.onNodeWithText("Measure distance").performClick()
        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        val beforePinch = image.captureToImage().asAndroidBitmap()

        image.performTouchInput {
            down(0, Offset(centerX - 40f, centerY))
            down(1, Offset(centerX + 40f, centerY))
            updatePointerTo(0, Offset(centerX - 80f, centerY))
            updatePointerTo(1, Offset(centerX + 80f, centerY))
            move()
            up(1)
            up(0)
        }

        val afterPinch = image.captureToImage().asAndroidBitmap()
        assertTrue("Measurement mode must keep two-finger zoom enabled.",
            differingPixels(beforePinch, afterPinch) > 0)
        compose.onNodeWithText("Tap two points on this slice.").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Pinch must not navigate slices while measuring.", 128, slice.intValue) }
    }

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
        pinch(compose.onNodeWithContentDescription("Real Axial MRI slice"))
        compose.onNodeWithText("200% · pinch to zoom").assertExists()
        compose.onNodeWithContentDescription("More viewer actions").performClick()
        compose.onNodeWithText("Focus mode").performClick()
        compose.onNodeWithText("Exit focus").performClick()
        compose.onNodeWithText("200% · pinch to zoom").assertExists()
        compose.onNodeWithText("138 / 256").assertExists()
        compose.runOnIdle { assertEquals(137, slice.intValue) }
    }

    @Test fun commonViewerActionsStayVisibleAndProcessingRemainsInOverflow() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(frame(0))
        compose.setContent { Harness(slice, bitmap) }
        compose.onNodeWithContentDescription("Gesture mode").assertDoesNotExist()
        compose.onNodeWithContentDescription("Display adjustments").assertExists()
        compose.onNodeWithContentDescription("More viewer actions").performClick()
        compose.onNodeWithText("Focus mode").assertExists()
        compose.onNodeWithText("Process with BrainSuite").performClick()
        compose.onNodeWithText("MRI processing").assertExists()
    }

    @Test fun measureToolReportsDistanceAfterTwoTapsAndCanBeCleared() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(panPattern())
        compose.setContent { Harness(slice, bitmap) }

        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        pinch(image)
        compose.onNodeWithText("200% · pinch to zoom").assertIsDisplayed()
        val imageAtCenter = image.captureToImage().asAndroidBitmap()
        twoFingerPan(image, Offset(40f, 0f))
        val imageAfterPan = image.captureToImage().asAndroidBitmap()
        assertTrue("The pan gesture must visibly move the test image.",
            differingPixels(imageAtCenter, imageAfterPan) > 0)
        compose.onNodeWithContentDescription("More viewer actions").performClick()
        compose.onNodeWithText("Measure distance").performClick()
        compose.onNodeWithText("Tap two points on this slice.").assertIsDisplayed()

        image.performTouchInput { click(Offset(width * 0.3f, height * 0.5f)) }
        compose.onNodeWithText("First point set · tap the second point.").assertIsDisplayed()
        image.performTouchInput { click(Offset(width * 0.7f, height * 0.5f)) }
        compose.onNodeWithText("Distance:", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Tap two points on this slice.").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Toolbar taps must not leak into slice navigation.", 128, slice.intValue) }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("200% · pinch to zoom").assertIsDisplayed()
        val imageAfterMeasurement = image.captureToImage().asAndroidBitmap()
        assertEquals("Measurement must preserve the panned image position.",
            0, differingPixels(imageAfterPan, imageAfterMeasurement))
        compose.onNodeWithText("Measure distance").assertDoesNotExist()
    }

    @Test fun measurementIsUnavailableWhenNiftiSpatialUnitsAreUnknown() {
        val unknownUnits = NiftiVolume(
            16, 16, 256,
            FloatArray(16 * 16 * 256) { (it % 256).toFloat() },
            SpatialAffine.axisAligned(1.0, 1.0, 1.0),
            0f, 255f, "sform (synthetic RAS+)", 1,
            hasKnownSpatialUnits = false,
        )
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(frame(0))
        compose.setContent { Harness(slice, bitmap, volumeForTest = unknownUnits) }

        compose.onNodeWithContentDescription("More viewer actions").performClick()
        compose.onNodeWithText("Measure distance").performClick()
        compose.onNodeWithText("Distance measurement unavailable").assertIsDisplayed()
        compose.onNodeWithText("cannot verify a calibrated distance in millimetres", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Tap two points on this slice.").assertDoesNotExist()
    }

    @Test fun draggingMeasurementHandleMovesEndpointWithoutChangingSlice() {
        val slice = mutableIntStateOf(128)
        val bitmap = mutableStateOf(panPattern())
        compose.setContent { Harness(slice, bitmap) }
        compose.onNodeWithContentDescription("More viewer actions").performClick()
        compose.onNodeWithText("Measure distance").performClick()
        val image = compose.onNodeWithContentDescription("Real Axial MRI slice")
        val pointOffset = 0.2f * minOf(image.fetchSemanticsNode().boundsInRoot.width,
            image.fetchSemanticsNode().boundsInRoot.height)
        val firstPoint = Offset(image.fetchSemanticsNode().boundsInRoot.width / 2f - pointOffset,
            image.fetchSemanticsNode().boundsInRoot.height / 2f)
        val secondPoint = Offset(image.fetchSemanticsNode().boundsInRoot.width / 2f + pointOffset,
            image.fetchSemanticsNode().boundsInRoot.height / 2f)
        image.performTouchInput { click(firstPoint) }
        image.performTouchInput { click(secondPoint) }
        val beforeDrag = measurementDistanceText()

        image.performTouchInput {
            down(firstPoint)
            moveBy(Offset(180f, 90f), delayMillis = 100)
            up()
        }

        val afterDrag = measurementDistanceText()
        assertNotEquals("Dragging a measurement handle must update its distance.", beforeDrag, afterDrag)
        compose.runOnIdle { assertEquals("Measurement handle drag must not change slices.", 128, slice.intValue) }
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
        volumeForTest: NiftiVolume = volume,
    ) {
        NeuroViewTheme {
            CompositionLocalProvider(LocalNeuroViewMotion provides motion) {
                    MriContent(
                        modifier = Modifier.fillMaxSize(),
                        focusMode = focus.value,
                        showSliceButtons = false,
                        inertialScrolling = false,
                        onFocusToggle = { focus.value = !focus.value },
                        showOrientationMarkers = false,
                        probeCrosshairEnabled = false,
                        onProbeCrosshairChange = {},
                        crosshairThicknessDp = 3f,
                        onZoomScaleChanged = {},
                        volume = volumeForTest,
                        plane = MriPlane.AXIAL,
                        descriptor = ViewPlaneDescriptor.worldAligned(MriPlane.AXIAL),
                        bitmap = bitmap.value,
                        renderStatusVisible = false,
                        onImageViewportSizeChanged = {},
                        selectedSlice = slice.intValue,
                        totalSlices = 256,
                        onSliceChange = onSliceChange ?: { slice.intValue = it },
                        onScrubbingChanged = { scrubbing.value = it },
                        onPlaneSelect = {},
                        onNativeGridSelect = {},
                        onStartupViewChange = {},
                        onResetStartupView = {},
                        selectedDocumentUri = null,
                        originalDocumentName = "synthetic",
                        referenceWorld = null,
                        onReferenceWorldChange = {},
                        windowCenter = 0.4f,
                        onWindowCenter = {},
                        windowWidth = 0.8f,
                        onWindowWidth = {},
                    )
            }
        }
    }

    private fun frame(value: Int): Bitmap = Bitmap.createBitmap(
        intArrayOf(0xff000000.toInt() or (value.coerceIn(0, 255) * 0x010101)), 1, 1, Bitmap.Config.ARGB_8888)

    private fun cornerMarkerFrame(): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) {
            val color = when {
                x < 8 && y < 8 -> Color.RED
                x >= width - 8 && y < 8 -> Color.GREEN
                x < 8 && y >= height - 8 -> Color.BLUE
                x >= width - 8 && y >= height - 8 -> Color.YELLOW
                else -> Color.WHITE
            }
            setPixel(x, y, color)
        }
    }

    private fun countExactPixels(bitmap: Bitmap, color: Int): Int {
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) == color) count++
        }
        return count
    }

    private fun panPattern(): Bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) {
            setPixel(x, y, if (x < width / 2) 0xffff3030.toInt() else 0xff3060ff.toInt())
        }
    }

    private fun differingPixels(first: Bitmap, second: Bitmap): Int {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        var differing = 0
        for (y in 0 until first.height) for (x in 0 until first.width) {
            if (first.getPixel(x, y) != second.getPixel(x, y)) differing++
        }
        return differing
    }

    private fun pinch(image: SemanticsNodeInteraction) {
        image.performTouchInput {
            down(0, Offset(centerX - 40f, centerY))
            down(1, Offset(centerX + 40f, centerY))
            updatePointerTo(0, Offset(centerX - 80f, centerY))
            updatePointerTo(1, Offset(centerX + 80f, centerY))
            move()
            up(1)
            up(0)
        }
    }

    private fun twoFingerPan(image: SemanticsNodeInteraction, delta: Offset) {
        image.performTouchInput {
            down(0, Offset(centerX - 40f, centerY))
            down(1, Offset(centerX + 40f, centerY))
            updatePointerTo(0, Offset(centerX - 40f + delta.x, centerY + delta.y))
            updatePointerTo(1, Offset(centerX + 40f + delta.x, centerY + delta.y))
            move()
            up(1)
            up(0)
        }
    }

    private fun measurementDistanceText(): String = compose
        .onNodeWithText("Distance:", substring = true)
        .fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .joinToString(separator = "") { it.text }
}
