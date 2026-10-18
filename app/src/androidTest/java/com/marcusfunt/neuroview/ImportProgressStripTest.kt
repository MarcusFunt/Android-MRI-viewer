package com.marcusfunt.neuroview

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import com.marcusfunt.neuroview.catalog.ImportBatchProgress
import com.marcusfunt.neuroview.ui.operations.OperationId
import com.marcusfunt.neuroview.ui.operations.OperationKind
import com.marcusfunt.neuroview.ui.operations.OperationProgress
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStage
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.OperationUnit
import com.marcusfunt.neuroview.ui.operations.SystemMonotonicClock
import com.marcusfunt.neuroview.ui.screens.ImportProgressStrip
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImportProgressStripTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsCurrentFileExactBatchCountsAndCancelAction() {
        val now = SystemMonotonicClock.nowMs()
        val operation = OperationSnapshot(
            id = OperationId(1),
            kind = OperationKind.IMPORT_BATCH,
            status = OperationStatus.RUNNING,
            stage = OperationStage.IMPORTING_FILE,
            progress = OperationProgress(1, 3, OperationUnit.FILES),
            startedAtElapsedMs = now - 15_000,
            stageStartedAtElapsedMs = now,
            lastWorkAdvanceElapsedMs = now,
            lastHeartbeatElapsedMs = now,
        )
        var canceled = false

        compose.setContent {
            NeuroViewTheme {
                ImportProgressStrip(
                    progress = ImportBatchProgress(
                        filesProcessed = 1,
                        totalFiles = 3,
                        currentFileName = "scan-two.nii.gz",
                        added = 1,
                        skipped = 1,
                        failed = 0,
                    ),
                    operation = operation,
                    cancelRequestedAtElapsedMs = null,
                    onCancel = { canceled = true },
                )
            }
        }

        compose.onNodeWithText("Checking scan-two.nii.gz · file 2 of 3").assertExists()
        compose.onNodeWithText("Checking scan-two.nii.gz · file 2 of 3").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
        compose.onNodeWithText("1 added · 1 skipped · 0 failed").assertExists()
        compose.onNodeWithText("CANCEL").performClick()
        assertTrue(canceled)
    }

    @Test
    fun cancelingCopyKeepsPartialImportVisibleWithoutOfferingAnotherCancel() {
        val now = SystemMonotonicClock.nowMs()
        val operation = OperationSnapshot(
            id = OperationId(2),
            kind = OperationKind.IMPORT_BATCH,
            status = OperationStatus.CANCELING,
            stage = OperationStage.IMPORTING_FILE,
            progress = OperationProgress(1, 3, OperationUnit.FILES),
            startedAtElapsedMs = now - 9_000,
            stageStartedAtElapsedMs = now - 9_000,
            lastWorkAdvanceElapsedMs = now - 9_000,
            lastHeartbeatElapsedMs = now,
        )

        compose.setContent {
            NeuroViewTheme {
                ImportProgressStrip(
                    progress = ImportBatchProgress(1, 3, "slow-provider.nii", 1, 0, 0),
                    operation = operation,
                    cancelRequestedAtElapsedMs = now - 9_000,
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithText("The file provider has not confirmed cancellation. The Library remains available.")
            .assertExists()
        compose.onNodeWithText("CANCEL").assertDoesNotExist()
        compose.onNodeWithText("1 added · 0 skipped · 0 failed").assertExists()
    }
}
