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
        compose.onNodeWithText("VIEW MRI SERIES").performClick()
        compose.onNodeWithText("MRI series").assertExists()
        compose.onNodeWithText("ADD NIFTI SERIES").assertExists()
    }

    @Test
    fun libraryExposesBothConversionDirectionsAndManagement() {
        compose.onNodeWithText("LIBRARY").performClick()
        compose.onNodeWithText("MRI Library").assertExists()
        compose.onNodeWithText("CREATE COLLECTION").assertExists()
        compose.onNodeWithText("CONVERT DICOM SERIES → NIFTI").assertExists()
        compose.onNodeWithText("IMPORT NIFTI FILES").assertExists()
    }
}
