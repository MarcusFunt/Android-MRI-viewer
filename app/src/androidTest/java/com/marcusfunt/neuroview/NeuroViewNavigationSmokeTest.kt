package com.marcusfunt.neuroview

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** Emulator smoke tests: app starts and high-value navigation is functional. */
class NeuroViewNavigationSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeCanOpenSeriesBrowser() {
        compose.onNodeWithText("NEUROVIEW").assertExists()
        compose.onNodeWithText("View MRI series").performClick()
        compose.onNodeWithText("Series browser").assertExists()
        compose.onNodeWithText("Import NIfTI volumes").assertExists()
    }

    @Test
    fun libraryShowsConversionAndManagementActions() {
        compose.onNodeWithText("Library & conversion").performClick()
        compose.onNodeWithText("MRI Library").assertExists()
        compose.onNodeWithText("New collection").assertExists()
        compose.onNodeWithText("Choose DICOM folder").assertExists()
        compose.onNodeWithText("Select individual files instead").assertExists()
        compose.onNodeWithText("Import NIfTI volumes").assertExists()
    }
}
