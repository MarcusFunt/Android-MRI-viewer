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
        compose.onNodeWithText("Browse scans").assertExists()
        compose.onNodeWithText("Import MRI").assertExists()
        compose.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun homeCanOpenUnifiedLibrary() {
        compose.onNodeWithText("NeuroView").assertExists()
        compose.onNodeWithText("Browse scans").performClick()
        compose.onNodeWithText("MRI Library").assertExists()
        compose.onNodeWithText("Import MRI volumes").assertExists()
    }

    @Test
    fun settingsControlRotationAndGlowWithoutLeavingHome() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Automatically rotate brain").assertExists()
        compose.onNodeWithText("Ambient background glow").assertExists()
        compose.onNodeWithText("Reset brain orientation").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Browse scans").assertExists()
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
}
