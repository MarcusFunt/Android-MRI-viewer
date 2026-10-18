package com.marcusfunt.neuroview

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.marcusfunt.neuroview.ui.screens.ADAPTIVE_RENDERING_KEY
import com.marcusfunt.neuroview.ui.screens.PROBE_CROSSHAIR_ENABLED_KEY
import com.marcusfunt.neuroview.ui.screens.RENDER_QUALITY_MODE_KEY
import com.marcusfunt.neuroview.ui.screens.VIEWER_DISPLAY_PREFERENCES
import com.marcusfunt.neuroview.ui.screens.NeuroViewSettingsSheet
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RenderQualitySettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun manualQualityControlsAppearAndSaveWithoutChangingCrosshairPreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(VIEWER_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit()
            .clear()
            .putBoolean(PROBE_CROSSHAIR_ENABLED_KEY, true)
            .commit()

        try {
            compose.setContent {
                NeuroViewTheme {
                    NeuroViewSettingsSheet(
                        autoRotate = false,
                        onAutoRotateChange = {},
                        glowEnabled = false,
                        onGlowEnabledChange = {},
                        onResetBrainOrientation = null,
                        onClearCache = { 0 },
                        scrollToViewerSettings = false,
                        onDismiss = {},
                    )
                }
            }

            compose.onNodeWithText("Rendering priority").performScrollTo()
            compose.onNodeWithText("Auto").performClick()
            compose.onNodeWithText("Manual").performClick()
            compose.onNodeWithContentDescription("Scrolling preview maximum side").assertExists()
            compose.onNodeWithText("Refine after scrolling", substring = true).assertExists()

            assertEquals("MANUAL", preferences.getString(RENDER_QUALITY_MODE_KEY, null))
            assertTrue(preferences.getBoolean(PROBE_CROSSHAIR_ENABLED_KEY, false))
            assertTrue(preferences.getBoolean(ADAPTIVE_RENDERING_KEY, true))
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
