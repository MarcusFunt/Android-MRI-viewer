package com.marcusfunt.neuroview

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.marcusfunt.neuroview.ui.components.OperationProgressIndicator
import com.marcusfunt.neuroview.ui.operations.OperationId
import com.marcusfunt.neuroview.ui.operations.OperationKind
import com.marcusfunt.neuroview.ui.operations.OperationProgress
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStage
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.OperationUnit
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Rule
import org.junit.Test

class OperationProgressIndicatorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unknownTotalShowsWorkCountWithoutInventingPercentage() {
        val snapshot = OperationSnapshot(
            id = OperationId(1),
            kind = OperationKind.VIEWER_LOAD,
            status = OperationStatus.RUNNING,
            stage = OperationStage.DECODING_VOLUME,
            progress = OperationProgress(35, null, OperationUnit.BYTES),
            startedAtElapsedMs = 0,
            stageStartedAtElapsedMs = 0,
            lastWorkAdvanceElapsedMs = 0,
            lastHeartbeatElapsedMs = null,
        )

        compose.setContent {
            NeuroViewTheme {
                OperationProgressIndicator(snapshot = snapshot, elapsedMs = 12_000)
            }
        }

        compose.onNodeWithText("Decoding MRI volume").assertExists()
        compose.onNodeWithText("35 bytes").assertExists()
        compose.onNodeWithText("35%").assertDoesNotExist()
    }
}
