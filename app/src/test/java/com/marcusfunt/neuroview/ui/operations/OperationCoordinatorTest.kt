package com.marcusfunt.neuroview.ui.operations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationCoordinatorTest {
    @Test fun sourceWorkClearsStallWithoutInventingDecodedVoxels() {
        val clock = FakeClock()
        val coordinator = OperationCoordinator(clock)
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        coordinator.reportProgress(id, OperationStage.DECODING_VOLUME, progress(0, 100))
        clock.now = 61_000
        assertEquals(OperationStatus.SUSPECTED_STALL, coordinator.classifyStall(id))
        assertTrue(coordinator.observeWork(id))
        assertEquals(OperationStatus.RUNNING, coordinator.state(id)?.status)
        assertEquals(0L, coordinator.state(id)?.progress?.completedUnits)
        assertEquals(61_000L, coordinator.state(id)?.lastWorkAdvanceElapsedMs)
        coordinator.requestCancel(id)
        assertFalse(coordinator.observeWork(id))
        coordinator.confirmCanceled(id)
        assertFalse(coordinator.observeWork(id))
        coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.OPENING_SOURCE)
        assertFalse(coordinator.observeWork(id))
    }

    private class FakeClock(var now: Long = 0L) : MonotonicClock {
        override fun nowMs(): Long = now
    }

    @Test
    fun unknownProgressTotalHasNoFractionOrPercentage() {
        val progress = OperationProgress(
            completedUnits = 35,
            totalUnits = null,
            unit = OperationUnit.BYTES,
        )

        assertNull(progress.fraction)
        assertNull(progress.percent)
    }

    @Test
    fun rejectsNegativeAndImpossibleProgressCounters() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationProgress(-1, 10, OperationUnit.VOXELS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationProgress(11, 10, OperationUnit.VOXELS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationProgress(0, 0, OperationUnit.VOXELS)
        }
    }

    @Test
    fun monotonicClockMayHaveANegativeOrigin() {
        val clock = FakeClock(-5_000)
        val coordinator = OperationCoordinator(clock)
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        clock.now = -4_000

        coordinator.reportProgress(id, OperationStage.DECODING_VOLUME, progress(1, 10))

        assertEquals(-5_000L, coordinator.state(id)?.startedAtElapsedMs)
        assertEquals(-4_000L, coordinator.state(id)?.lastWorkAdvanceElapsedMs)
    }

    @Test
    fun terminalOperationsCannotTransitionAgain() {
        val coordinator = OperationCoordinator(FakeClock())
        val id = coordinator.begin(OperationKind.IMPORT_BATCH, OperationStage.IMPORTING_FILE)
        coordinator.requestCancel(id)
        coordinator.confirmCanceled(id)

        assertThrows(IllegalStateException::class.java) {
            coordinator.fail(
                id,
                OperationFailure(
                    kind = OperationFailureKind.INTERNAL_ERROR,
                    safeMessage = "The import could not be completed",
                    retryable = false,
                ),
            )
        }
        assertEquals(OperationStatus.CANCELED, coordinator.state(id)?.status)
    }

    @Test
    fun progressCannotMoveBackwardWithinOneStage() {
        val clock = FakeClock()
        val coordinator = OperationCoordinator(clock)
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        coordinator.reportProgress(id, OperationStage.DECODING_VOLUME, progress(8, 20))
        clock.now = 125

        assertThrows(IllegalArgumentException::class.java) {
            coordinator.reportProgress(id, OperationStage.DECODING_VOLUME, progress(7, 20))
        }
    }

    @Test
    fun aNewStageMayRestartItsOwnProgressCounter() {
        val clock = FakeClock()
        val coordinator = OperationCoordinator(clock)
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.READING_HEADER)
        coordinator.reportProgress(id, OperationStage.READING_HEADER, progress(300, 300, OperationUnit.BYTES))

        val accepted = coordinator.reportProgress(
            id,
            OperationStage.DECODING_VOLUME,
            progress(0, 100, OperationUnit.VOXELS),
        )

        assertTrue(accepted)
        assertEquals(OperationStage.DECODING_VOLUME, coordinator.state(id)?.stage)
        assertEquals(0L, coordinator.state(id)?.progress?.completedUnits)
    }

    @Test
    fun viewerLoadCannotSucceedBeforeFirstSliceIsReady() {
        val coordinator = OperationCoordinator(FakeClock())
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)

        assertThrows(IllegalStateException::class.java) { coordinator.succeed(id, "volume-1") }
        coordinator.markFirstSliceReady(id)
        coordinator.succeed(id, "volume-1")

        assertEquals(OperationStatus.SUCCEEDED, coordinator.state(id)?.status)
    }

    @Test
    fun heartbeatsDoNotResetTheWorkStallClock() {
        val clock = FakeClock()
        val coordinator = OperationCoordinator(clock)
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        clock.now = 1
        coordinator.reportProgress(id, OperationStage.DECODING_VOLUME, progress(5, 100))
        clock.now = 20_000
        coordinator.heartbeat(id)
        clock.now = 21_001

        assertEquals(
            OperationStatus.SUSPECTED_STALL,
            coordinator.classifyStall(
                id,
                StallThresholds(slowAfterMs = 8_000, suspectedStallAfterMs = 20_000),
            ),
        )
        assertEquals(1L, coordinator.state(id)?.lastWorkAdvanceElapsedMs)
        assertEquals(20_000L, coordinator.state(id)?.lastHeartbeatElapsedMs)
    }

    @Test
    fun reportingAnInitialZeroDoesNotResetTheWorkStallClock() {
        val clock = FakeClock()
        val coordinator = OperationCoordinator(clock)
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        clock.now = 3_000

        coordinator.reportProgress(id, OperationStage.DECODING_VOLUME, progress(0, 100))

        assertEquals(0L, coordinator.state(id)?.lastWorkAdvanceElapsedMs)
    }

    @Test
    fun activeProviderWaitIsNotCalledStalledUntilItsLongerThreshold() {
        val clock = FakeClock()
        val coordinator = OperationCoordinator(clock)
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.OPENING_SOURCE)
        clock.now = 30_000

        assertEquals(
            OperationStatus.WAITING_FOR_PROVIDER,
            coordinator.classifyStall(id, waitingForProvider = true),
        )
        clock.now = 60_001
        assertEquals(
            OperationStatus.SUSPECTED_STALL,
            coordinator.classifyStall(id, waitingForProvider = true),
        )
    }

    @Test
    fun newProgressClearsSlowStatusAndUnknownOperationsCannotReplaceLatestProgress() {
        val clock = FakeClock()
        val coordinator = OperationCoordinator(clock)
        val old = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        clock.now = 1
        coordinator.reportProgress(old, OperationStage.DECODING_VOLUME, progress(5, 100))
        clock.now = 9_000
        assertEquals(OperationStatus.SLOW, coordinator.classifyStall(old))
        clock.now = 9_100
        coordinator.reportProgress(old, OperationStage.DECODING_VOLUME, progress(6, 100))
        assertEquals(OperationStatus.RUNNING, coordinator.state(old)?.status)

        val current = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        assertFalse(coordinator.reportProgress(old, OperationStage.DECODING_VOLUME, progress(7, 100)))
        assertNull(coordinator.state(current)?.progress)
    }

    @Test
    fun stateFlowThrottlesUiProgressButCoordinatorKeepsExactCounters() {
        val clock = FakeClock()
        val coordinator = OperationCoordinator(clock, progressPublishIntervalMs = 100)
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        clock.now = 100
        coordinator.reportProgress(id, OperationStage.DECODING_VOLUME, progress(10, 100))
        clock.now = 150
        coordinator.reportProgress(id, OperationStage.DECODING_VOLUME, progress(20, 100))

        assertEquals(10L, coordinator.states.value.single().progress?.completedUnits)
        assertEquals(20L, coordinator.state(id)?.progress?.completedUnits)

        clock.now = 200
        coordinator.reportProgress(id, OperationStage.DECODING_VOLUME, progress(30, 100))
        assertEquals(30L, coordinator.states.value.single().progress?.completedUnits)
    }

    private fun progress(
        completed: Long,
        total: Long?,
        unit: OperationUnit = OperationUnit.VOXELS,
    ) = OperationProgress(completed, total, unit)
}
