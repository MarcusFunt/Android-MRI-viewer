package com.marcusfunt.neuroview

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marcusfunt.neuroview.ui.VolumeLoadState
import com.marcusfunt.neuroview.ui.operations.OperationId
import com.marcusfunt.neuroview.ui.operations.OperationKind
import com.marcusfunt.neuroview.ui.operations.OperationProgress
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStage
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.OperationUnit
import com.marcusfunt.neuroview.ui.operations.SystemMonotonicClock
import com.marcusfunt.neuroview.ui.screens.ViewerScreen
import com.marcusfunt.neuroview.ui.theme.NeuroViewTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ViewerLoadingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingScreenShowsActualStageProgressElapsedTimeAndCancel() {
        val now = SystemMonotonicClock.nowMs()
        val snapshot = OperationSnapshot(
            id = OperationId(1),
            kind = OperationKind.VIEWER_LOAD,
            status = OperationStatus.RUNNING,
            stage = OperationStage.DECODING_VOLUME,
            progress = OperationProgress(25_000, 50_000, OperationUnit.VOXELS),
            startedAtElapsedMs = now - 12_000,
            stageStartedAtElapsedMs = now,
            lastWorkAdvanceElapsedMs = now,
            lastHeartbeatElapsedMs = now,
        )
        var canceled = false

        compose.setContent {
            NeuroViewTheme {
                ViewerScreen(
                    volumeState = VolumeLoadState.Loading(snapshot, "example.nii.gz"),
                    onFirstSliceBitmapReady = {},
                    onFirstSliceDisplayed = {},
                    onFirstSliceRenderStarted = { _, _, _ -> },
                    onFirstSliceRenderFinished = { _, _, _ -> },
                    onCancelLoad = { canceled = true },
                    onRetryLoad = {},
                    onReturnToLibrary = {},
                    onImport = {},
                    onNavigate = {},
                )
            }
        }

        compose.onNodeWithText("Decoding MRI volume").assertExists()
        compose.onNodeWithText("50%").assertExists()
        compose.onNodeWithText("Elapsed", substring = true).assertExists()
        compose.onNodeWithText("CANCEL").performClick()
        assertTrue(canceled)
    }

    @Test
    fun delayedProviderCancellationOffersLibraryReturnWithoutClaimingStopped() {
        val now = SystemMonotonicClock.nowMs()
        val snapshot = OperationSnapshot(
            id = OperationId(2),
            kind = OperationKind.VIEWER_LOAD,
            status = OperationStatus.CANCELING,
            stage = OperationStage.OPENING_SOURCE,
            progress = null,
            startedAtElapsedMs = now - 9_000,
            stageStartedAtElapsedMs = now - 9_000,
            lastWorkAdvanceElapsedMs = now - 9_000,
            lastHeartbeatElapsedMs = now,
        )
        var returnedToLibrary = false

        compose.setContent {
            NeuroViewTheme {
                ViewerScreen(
                    volumeState = VolumeLoadState.Loading(
                        snapshot,
                        cancelRequestedAtElapsedMs = now - 9_000,
                    ),
                    onFirstSliceBitmapReady = {},
                    onFirstSliceDisplayed = {},
                    onFirstSliceRenderStarted = { _, _, _ -> },
                    onFirstSliceRenderFinished = { _, _, _ -> },
                    onCancelLoad = {},
                    onRetryLoad = {},
                    onReturnToLibrary = { returnedToLibrary = true },
                    onImport = {},
                    onNavigate = {},
                )
            }
        }

        compose.onNodeWithText("The selected file provider is still responding slowly. You can return to the Library.")
            .assertExists()
        compose.onNodeWithText("CANCEL").assertDoesNotExist()
        compose.onNodeWithText("RETURN TO LIBRARY").performClick()
        assertTrue(returnedToLibrary)
    }
}
