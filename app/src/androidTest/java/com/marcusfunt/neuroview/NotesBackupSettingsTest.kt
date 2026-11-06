package com.marcusfunt.neuroview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.marcusfunt.neuroview.ui.screens.SettingsScreen
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotesBackupSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun exportExplainsSensitiveContentsBeforeOpeningTheDestinationPicker() {
        var exportRequests = 0
        compose.setContent {
            NeuroViewTheme {
                SettingsScreen(
                    responsiveScrollEnabled = true,
                    onResponsiveScrollChange = {},
                    scrollPreviewQuality = 0.5f,
                    onScrollPreviewQualityChange = {},
                    autoRotate = true,
                    onAutoRotateChange = {},
                    glowEnabled = true,
                    onGlowEnabledChange = {},
                    onResetOrientation = {},
                    onClearCache = { 0 },
                    onExportNotesBackup = { exportRequests++ },
                    onImportNotesBackup = {},
                    noteArchiveBusy = false,
                    noteArchiveStatus = null,
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Export notes").performScrollTo().performClick()
        compose.onNodeWithText("Export notes backup").assertIsDisplayed()
        compose.onNodeWithText(
            "Note backups may contain sensitive personal or medical information.",
            substring = true,
        )
            .assertIsDisplayed()
        compose.onNodeWithText("Choose backup location").performClick()

        compose.runOnIdle { assertEquals(1, exportRequests) }
    }

    @Test
    fun importExplainsMergeAndManualRelinkingBeforeOpeningTheFilePicker() {
        var importRequests = 0
        compose.setContent {
            NeuroViewTheme {
                SettingsScreen(
                    responsiveScrollEnabled = true,
                    onResponsiveScrollChange = {},
                    scrollPreviewQuality = 0.5f,
                    onScrollPreviewQualityChange = {},
                    autoRotate = true,
                    onAutoRotateChange = {},
                    glowEnabled = true,
                    onGlowEnabledChange = {},
                    onResetOrientation = {},
                    onClearCache = { 0 },
                    onExportNotesBackup = {},
                    onImportNotesBackup = { importRequests++ },
                    noteArchiveBusy = false,
                    noteArchiveStatus = null,
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Import notes backup").performScrollTo().performClick()
        compose.onNodeWithText(
            "Import merges notes without deleting existing entries.",
            substring = true,
        ).assertIsDisplayed()
        compose.onNodeWithText("Choose backup file").performClick()

        compose.runOnIdle { assertEquals(1, importRequests) }
    }
}
