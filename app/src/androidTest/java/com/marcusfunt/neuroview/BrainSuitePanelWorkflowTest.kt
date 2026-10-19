package com.marcusfunt.neuroview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.marcusfunt.neuroview.ui.screens.BrainSuitePanel
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Rule
import org.junit.Test

class BrainSuitePanelWorkflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun jobsAndLimitationsAreCollapsedAndRetrievalCanOpenWithoutUpload() {
        compose.setContent {
            NeuroViewTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BrainSuitePanel(null, null, "", {})
                }
            }
        }
        compose.onNodeWithText("Job identifier (paste to resume)").assertDoesNotExist()
        compose.onNodeWithText("Send T1w scan for processing").assertIsNotEnabled()
        compose.onNodeWithText("Open a T1w NIfTI to enable sending.").assertExists()
        compose.onNodeWithText("Previous jobs and results").performScrollTo().performClick()
        compose.onNodeWithText("Job identifier (paste to resume)").assertExists()
        compose.onNodeWithText("Refresh status").assertIsNotEnabled()
    }

    @Test fun sendRequiresConfiguredHttpsAndConsentForCurrentSelection() {
        val token = mutableStateOf("")
        val source = mutableStateOf("content://synthetic/one")
        compose.setContent {
            NeuroViewTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BrainSuitePanel(source.value, "Synthetic T1w", token.value, { token.value = it })
                }
            }
        }
        compose.onNodeWithText("Secure HTTPS address").performTextInput("https://processing.example.test")
        compose.onNodeWithText("Access token (not saved)").performTextInput("synthetic-test-token-0000000000")
        compose.onNodeWithText("Use this computer").performScrollTo().performClick()
        compose.onNodeWithText("Secure HTTPS address").assertDoesNotExist()
        compose.onNodeWithText("Send T1w scan for processing").assertIsNotEnabled()
        compose.onNodeWithText("Confirm the upload consent above to enable sending.").assertExists()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Send T1w scan for processing").assertIsEnabled()
        compose.runOnIdle { source.value = "content://synthetic/two" }
        compose.onNodeWithText("Send T1w scan for processing").assertIsNotEnabled()
    }
}
