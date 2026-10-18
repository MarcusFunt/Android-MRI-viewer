package com.marcusfunt.neuroview

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.marcusfunt.neuroview.ui.screens.NeuroViewSettingsSheet
import com.marcusfunt.neuroview.ui.theme.LocalNeuroViewMotion
import com.marcusfunt.neuroview.ui.theme.MOTION_PREFERENCE_KEY
import com.marcusfunt.neuroview.ui.theme.MOTION_PREFERENCES
import com.marcusfunt.neuroview.ui.theme.MotionPreference
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import com.marcusfunt.neuroview.ui.theme.rememberMotionPreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MotionPreferenceSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reducedMotionChoicePersistsAndUpdatesTheActivePreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(MOTION_PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        try {
            compose.setContent {
                val preference = rememberMotionPreference()
                NeuroViewTheme {
                    CompositionLocalProvider(LocalNeuroViewMotion provides preference) {
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
            }

            compose.onNodeWithText("Motion").performScrollTo()
            compose.onNodeWithText(MotionPreference.SYSTEM.label).performClick()
            compose.onNodeWithText(MotionPreference.REDUCED.label).performClick()
            compose.waitForIdle()

            assertEquals(
                MotionPreference.REDUCED.name,
                preferences.getString(MOTION_PREFERENCE_KEY, null),
            )
            compose.onNodeWithText(MotionPreference.REDUCED.description).assertExists()
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
