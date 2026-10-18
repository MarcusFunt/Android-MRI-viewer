package com.marcusfunt.neuroview

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import com.marcusfunt.neuroview.ui.operations.OperationId
import com.marcusfunt.neuroview.ui.operations.OperationKind
import com.marcusfunt.neuroview.ui.operations.OperationProgress
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStage
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.OperationUnit
import com.marcusfunt.neuroview.ui.operations.SystemMonotonicClock
import com.marcusfunt.neuroview.ui.screens.BrainSuiteOperationCard
import com.marcusfunt.neuroview.ui.screens.ConversionProgressCard
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConversionAndBrainSuiteProgressTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun conversionCardShowsOutputStageSliceCountAndCancelAction() {
        val now = SystemMonotonicClock.nowMs()
        val operation = OperationSnapshot(
            id = OperationId(1),
            kind = OperationKind.DICOM_CONVERSION,
            status = OperationStatus.RUNNING,
            stage = OperationStage.PACKAGING,
            progress = OperationProgress(2, 3, OperationUnit.SLICES),
            startedAtElapsedMs = now - 4_000,
            stageStartedAtElapsedMs = now,
            lastWorkAdvanceElapsedMs = now,
            lastHeartbeatElapsedMs = null,
        )
        var canceled = false
        compose.setContent {
            NeuroViewTheme {
                ConversionProgressCard(operation) { canceled = true }
            }
        }

        compose.onNodeWithText("Converting MRI data").assertExists()
        compose.onNodeWithText("Packaging results").assertExists()
        compose.onNodeWithText("2 of 3 slices").assertExists()
        compose.onNodeWithText("CANCEL").performClick()
        assertTrue(canceled)
    }

    @Test
    fun brainSuiteTransferShowsExactBytesWhileRemoteWaitHasNoCancelOrPercent() {
        val now = SystemMonotonicClock.nowMs()
        val upload = OperationSnapshot(
            id = OperationId(2),
            kind = OperationKind.BRAIN_SUITE,
            status = OperationStatus.RUNNING,
            stage = OperationStage.UPLOADING,
            progress = OperationProgress(65_536, 150_000, OperationUnit.BYTES),
            startedAtElapsedMs = now - 2_000,
            stageStartedAtElapsedMs = now,
            lastWorkAdvanceElapsedMs = now,
            lastHeartbeatElapsedMs = null,
        )
        val waiting = upload.copy(
            id = OperationId(3),
            stage = OperationStage.WAITING_FOR_REMOTE,
            progress = null,
        )
        val showWaiting = mutableStateOf(false)
        var canceled = false
        compose.setContent {
            NeuroViewTheme {
                if (showWaiting.value) {
                    BrainSuiteOperationCard(waiting, canCancelTransfer = false, onCancel = {})
                } else {
                    BrainSuiteOperationCard(upload, canCancelTransfer = true) { canceled = true }
                }
            }
        }
        compose.onNodeWithText("Uploading to processing host").assertExists()
        compose.onNodeWithText("65,536 of 150,000 bytes").assertExists()
        compose.onNodeWithText("CANCEL").performClick()
        assertTrue(canceled)

        compose.runOnIdle { showWaiting.value = true }
        compose.onNodeWithText("Waiting for processing host").assertExists()
        compose.onNodeWithText("CANCEL").assertDoesNotExist()
        compose.onNodeWithText("0%").assertDoesNotExist()
    }
}
