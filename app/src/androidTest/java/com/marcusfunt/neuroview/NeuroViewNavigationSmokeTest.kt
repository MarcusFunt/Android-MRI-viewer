package com.marcusfunt.neuroview

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** Emulator smoke tests: app starts and high-value navigation is functional. */
class NeuroViewNavigationSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeExposesPrimaryActionsWithoutOpeningAnotherScreen() {
        compose.onNodeWithText("NeuroView").assertExists()
        compose.onNodeWithText("Open viewer").assertExists()
        compose.onNodeWithText("Import & Library").assertExists()
        compose.onNodeWithContentDescription("Landing page settings").assertExists()
    }

    @Test
    fun homeCanOpenSeriesBrowser() {
        compose.onNodeWithText("NeuroView").assertExists()
        compose.onNodeWithText("Open viewer").performClick()
        compose.onNodeWithText("Series browser").assertExists()
        compose.onNodeWithText("Import NIfTI volumes").assertExists()
    }

    @Test
    fun settingsControlRotationAndGlowWithoutLeavingHome() {
        compose.onNodeWithContentDescription("Landing page settings").performClick()
        compose.onNodeWithText("Automatically rotate brain").assertExists()
        compose.onNodeWithText("Ambient background glow").assertExists()
        compose.onNodeWithText("Reset brain orientation").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Open viewer").assertExists()
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
        compose.onNodeWithText("Import & Library").performClick()
        compose.onNodeWithText("MRI Library").assertExists()
        compose.onNodeWithText("New collection").assertExists()
        compose.onNodeWithText("Choose DICOM folder").assertExists()
        compose.onNodeWithText("Select individual files instead").assertExists()
        compose.onNodeWithText("Import NIfTI volumes").assertExists()
    }
}
