package com.marcusfunt.neuroview

import android.content.Context
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.mri.SpatialAffine
import com.marcusfunt.neuroview.ui.VolumeLoadState
import com.marcusfunt.neuroview.ui.screens.ADAPTIVE_RENDERING_KEY
import com.marcusfunt.neuroview.ui.screens.RENDER_QUALITY_MODE_KEY
import com.marcusfunt.neuroview.ui.screens.VIEWER_DISPLAY_PREFERENCES
import com.marcusfunt.neuroview.ui.screens.ViewerScreen
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Rule
import org.junit.Test

class ViewerMemoryLifecycleTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun returningFromBackgroundRendersTheCurrentSliceAgain() {
        showViewer(volume(32, 32, 12), "resume scan")
        compose.waitUntil(15_000L) {
            compose.onAllNodesWithContentDescription("Real Axial MRI slice")
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        compose.waitUntil(15_000L) {
            compose.onAllNodesWithContentDescription("Real Axial MRI slice")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Real Axial MRI slice").assertExists()
    }

    @Test
    fun runningCriticalTrimSelectsAndExplainsTheSafeStillResolution() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(VIEWER_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
        val originalSettings = preferences.all
        preferences.edit()
            .clear()
            .putString(RENDER_QUALITY_MODE_KEY, "BALANCED")
            .putBoolean(ADAPTIVE_RENDERING_KEY, false)
            .commit()

        try {
            showViewer(volume(384, 256, 8), "memory pressure scan")
            compose.waitUntil(15_000L) {
                compose.onAllNodesWithContentDescription("Real Axial MRI slice")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            sendRunningCriticalTrim()

            compose.onNodeWithContentDescription("Viewer settings").performClick()
            compose.onNodeWithText("Rendering priority").performScrollTo()
            compose.waitUntil(10_000L) {
                compose.onAllNodesWithText("effective side: 192 px", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("reduced for memory headroom", substring = true).assertExists()
        } finally {
            preferences.edit().clear().commit()
            originalSettings.forEach { (key, value) ->
                val editor = preferences.edit()
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

    @Test
    fun repeatedCriticalTrimRendersSmallScansAgainAtTheSameQualityCap() {
        showViewer(volume(32, 32, 8), "small memory pressure scan")
        compose.waitUntil(15_000L) {
            compose.onAllNodesWithContentDescription("Real Axial MRI slice")
                .fetchSemanticsNodes().isNotEmpty()
        }

        repeat(2) {
            sendRunningCriticalTrim()
            compose.waitUntil(15_000L) {
                compose.onAllNodesWithContentDescription("Real Axial MRI slice")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun memoryCallbacksAfterPlaneChangeRestartTheCurrentDescriptorRender() {
        showViewer(volume(64, 64, 12), "plane memory pressure scan")
        compose.waitUntil(15_000L) {
            compose.onAllNodesWithContentDescription("Real Axial MRI slice")
                .fetchSemanticsNodes().isNotEmpty()
        }
        sendRunningCriticalTrim()
        compose.waitUntil(15_000L) {
            compose.onAllNodesWithContentDescription("Real Axial MRI slice")
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Coronal").performClick()
        compose.waitUntil(15_000L) {
            compose.onAllNodesWithContentDescription("Real Coronal MRI slice")
                .fetchSemanticsNodes().isNotEmpty()
        }
        sendRunningCriticalTrim()

        compose.waitUntil(15_000L) {
            compose.onAllNodesWithContentDescription("Real Coronal MRI slice")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun sendRunningCriticalTrim() {
        compose.activityRule.scenario.onActivity { activity ->
            @Suppress("DEPRECATION")
            activity.application.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        }
    }

    private fun showViewer(
        volume: NiftiVolume,
        displayName: String,
    ) {
        compose.activityRule.scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0) as ComposeView
            content.setContent {
                NeuroViewTheme {
                    ViewerScreen(
                        volumeState = VolumeLoadState.Ready(displayName, volume),
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
    }

    private fun volume(nx: Int, ny: Int, nz: Int) = NiftiVolume(
        nx = nx,
        ny = ny,
        nz = nz,
        values = FloatArray(nx * ny * nz) { index -> (index % 256).toFloat() },
        affine = SpatialAffine.axisAligned(1.0, 1.0, 1.0),
        percentileLow = 0f,
        percentileHigh = 255f,
        transformSource = "sform (RAS+)",
        timepoints = 1,
    )
}
