package com.marcusfunt.neuroview.ui.operations

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns immutable operation snapshots. It never starts worker jobs: callers keep
 * cancellation in their structured coroutine scope and report confirmed cleanup.
 */
class OperationCoordinator(
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val progressPublishIntervalMs: Long = 125L,
) {
    private data class Record(
        var snapshot: OperationSnapshot,
        var lastObservedAtMs: Long,
        var lastPublishedAtMs: Long,
    )

    private val sequence = AtomicLong()
    private val records = linkedMapOf<OperationId, Record>()
    private val latestByKind = mutableMapOf<OperationKind, OperationId>()
    private val mutableStates = MutableStateFlow<List<OperationSnapshot>>(emptyList())

    val states: StateFlow<List<OperationSnapshot>> = mutableStates.asStateFlow()

    init {
        require(progressPublishIntervalMs >= 0L)
    }

    @Synchronized
    fun begin(kind: OperationKind, stage: OperationStage): OperationId {
        val now = clock.nowMs()
        val id = OperationId(sequence.incrementAndGet())
        val snapshot = OperationSnapshot(
            id = id,
            kind = kind,
            status = OperationStatus.RUNNING,
            stage = stage,
            progress = null,
            startedAtElapsedMs = now,
            stageStartedAtElapsedMs = now,
            lastWorkAdvanceElapsedMs = now,
            lastHeartbeatElapsedMs = null,
        )
        records[id] = Record(snapshot, now, now)
        latestByKind[kind] = id
        publishAll()
        return id
    }

    /** Exact worker state, including progress that has not yet been published to the UI. */
    @Synchronized
    fun state(id: OperationId): OperationSnapshot? = records[id]?.snapshot

    /** Latest generation for a kind. Older callbacks must not replace it. */
    @Synchronized
    fun latest(kind: OperationKind): OperationSnapshot? =
        latestByKind[kind]?.let { records[it]?.snapshot }

    @Synchronized
    fun isCurrent(id: OperationId): Boolean =
        records[id]?.let { latestByKind[it.snapshot.kind] == id } == true

    /**
     * Reports exact work completed. Updates to a new stage may restart the count;
     * updates inside one stage must be monotonic and keep the same units and total.
     * Returns false for missing, stale, or terminal operations.
     */
    @Synchronized
    fun reportProgress(
        id: OperationId,
        stage: OperationStage,
        progress: OperationProgress?,
    ): Boolean {
        val record = records[id] ?: return false
        val previous = record.snapshot
        if (latestByKind[previous.kind] != id || !previous.status.canReceiveProgress) return false

        val stageChanged = stage != previous.stage
        if (!stageChanged) validateMonotonic(previous.progress, progress)

        val now = observeTime(record)
        val workAdvanced = stageChanged || progressAdvanced(previous.progress, progress)
        val nextStatus = if (workAdvanced && previous.status != OperationStatus.RUNNING) {
            OperationStatus.RUNNING
        } else {
            previous.status
        }
        record.snapshot = previous.copy(
            status = nextStatus,
            stage = stage,
            progress = progress,
            stageStartedAtElapsedMs = if (stageChanged) now else previous.stageStartedAtElapsedMs,
            lastWorkAdvanceElapsedMs = if (workAdvanced) now else previous.lastWorkAdvanceElapsedMs,
            failure = null,
        )

        val stageComplete = progress?.let {
            it.completedUnits != null && it.totalUnits != null && it.completedUnits == it.totalUnits
        } == true
        if (stageChanged || nextStatus != previous.status ||
            now - record.lastPublishedAtMs >= progressPublishIntervalMs || stageComplete) {
            publish(record, now)
        }
        return true
    }

    /** A heartbeat means the worker is responsive; it does not advance the work clock. */
    @Synchronized
    fun heartbeat(id: OperationId): Boolean {
        val record = records[id] ?: return false
        val previous = record.snapshot
        if (latestByKind[previous.kind] != id || !previous.status.canReceiveProgress) return false
        val now = observeTime(record)
        record.snapshot = previous.copy(lastHeartbeatElapsedMs = now)
        if (now - record.lastPublishedAtMs >= progressPublishIntervalMs) publish(record, now)
        return true
    }

    /** Measurable work below a stage counter, e.g. gzip source bytes before a voxel batch completes. */
    @Synchronized
    fun observeWork(id: OperationId): Boolean {
        val record = records[id] ?: return false
        val previous = record.snapshot
        if (latestByKind[previous.kind] != id || !previous.status.canReceiveProgress) return false
        val now = observeTime(record)
        record.snapshot = previous.copy(
            status = OperationStatus.RUNNING,
            lastWorkAdvanceElapsedMs = now,
        )
        if (previous.status != OperationStatus.RUNNING ||
            now - record.lastPublishedAtMs >= progressPublishIntervalMs) publish(record, now)
        return true
    }

    /** Classifies staleness using the stage's work clock, never the heartbeat clock. */
    @Synchronized
    fun classifyStall(
        id: OperationId,
        thresholds: StallThresholds? = null,
        waitingForProvider: Boolean = false,
    ): OperationStatus {
        val record = records[id] ?: return OperationStatus.CANCELED
        val snapshot = record.snapshot
        if (latestByKind[snapshot.kind] != id || !snapshot.status.canReceiveProgress) {
            return snapshot.status
        }
        val now = observeTime(record)
        val policy = thresholds ?: thresholdsFor(snapshot.stage)
        val withoutWorkFor = now - snapshot.lastWorkAdvanceElapsedMs
        val stageAge = now - snapshot.stageStartedAtElapsedMs
        val next = when {
            waitingForProvider && withoutWorkFor >= policy.providerSuspectedStallAfterMs ->
                OperationStatus.SUSPECTED_STALL
            waitingForProvider -> OperationStatus.WAITING_FOR_PROVIDER
            withoutWorkFor >= policy.suspectedStallAfterMs -> OperationStatus.SUSPECTED_STALL
            stageAge >= policy.slowAfterMs && snapshot.progress?.completedUnits != null ->
                OperationStatus.SLOW
            else -> OperationStatus.RUNNING
        }
        if (next != snapshot.status) transitionRecord(record, next, now)
        return next
    }

    @Synchronized
    fun markFirstSliceReady(id: OperationId): Boolean {
        val snapshot = records[id]?.snapshot ?: return false
        require(snapshot.kind == OperationKind.VIEWER_LOAD) {
            "Only a viewer load can report a first slice"
        }
        return reportProgress(id, OperationStage.FIRST_SLICE_READY, null)
    }

    @Synchronized
    fun requestCancel(id: OperationId): Boolean = transition(id, OperationStatus.CANCELING)

    /** Call only after the worker has stopped and released resources. */
    @Synchronized
    fun confirmCanceled(id: OperationId): Boolean = transition(id, OperationStatus.CANCELED)

    @Synchronized
    fun fail(id: OperationId, failure: OperationFailure): Boolean =
        transition(id, OperationStatus.FAILED, failure = failure)

    @Synchronized
    fun succeed(id: OperationId, resultId: String? = null): Boolean {
        val snapshot = records[id]?.snapshot ?: return false
        check(snapshot.kind != OperationKind.VIEWER_LOAD ||
            snapshot.stage == OperationStage.FIRST_SLICE_READY) {
            "A viewer load succeeds only after its first usable slice is ready"
        }
        return transition(id, OperationStatus.SUCCEEDED, resultId = resultId)
    }

    @Synchronized
    fun transition(
        id: OperationId,
        status: OperationStatus,
        failure: OperationFailure? = null,
        resultId: String? = null,
    ): Boolean {
        val record = records[id] ?: return false
        if (latestByKind[record.snapshot.kind] != id) return false
        val now = observeTime(record)
        transitionRecord(record, status, now, failure, resultId)
        return true
    }

    private fun transitionRecord(
        record: Record,
        status: OperationStatus,
        now: Long,
        failure: OperationFailure? = null,
        resultId: String? = null,
    ) {
        val previous = record.snapshot
        check(status in allowedTransitions(previous.status)) {
            "Illegal operation transition: ${previous.status} → $status"
        }
        require((status == OperationStatus.FAILED) == (failure != null)) {
            "Failed operations need a typed failure; other states must not carry one"
        }
        if (status == OperationStatus.SUCCEEDED) {
            check(previous.kind != OperationKind.VIEWER_LOAD ||
                previous.stage == OperationStage.FIRST_SLICE_READY) {
                "A viewer load succeeds only after its first usable slice is ready"
            }
        }
        record.snapshot = previous.copy(
            status = status,
            failure = failure,
            resultId = resultId,
        )
        publish(record, now)
    }

    private fun allowedTransitions(from: OperationStatus): Set<OperationStatus> = when (from) {
        OperationStatus.QUEUED -> setOf(
            OperationStatus.RUNNING,
            OperationStatus.CANCELING,
            OperationStatus.CANCELED,
            OperationStatus.FAILED,
        )
        OperationStatus.RUNNING -> setOf(
            OperationStatus.WAITING_FOR_PROVIDER,
            OperationStatus.SLOW,
            OperationStatus.SUSPECTED_STALL,
            OperationStatus.CANCELING,
            OperationStatus.FAILED,
            OperationStatus.SUCCEEDED,
        )
        OperationStatus.WAITING_FOR_PROVIDER -> setOf(
            OperationStatus.RUNNING,
            OperationStatus.SLOW,
            OperationStatus.SUSPECTED_STALL,
            OperationStatus.CANCELING,
            OperationStatus.FAILED,
        )
        OperationStatus.SLOW -> setOf(
            OperationStatus.RUNNING,
            OperationStatus.WAITING_FOR_PROVIDER,
            OperationStatus.SUSPECTED_STALL,
            OperationStatus.CANCELING,
            OperationStatus.FAILED,
            OperationStatus.SUCCEEDED,
        )
        OperationStatus.SUSPECTED_STALL -> setOf(
            OperationStatus.RUNNING,
            OperationStatus.WAITING_FOR_PROVIDER,
            OperationStatus.CANCELING,
            OperationStatus.FAILED,
            OperationStatus.SUCCEEDED,
        )
        OperationStatus.CANCELING -> setOf(
            OperationStatus.CANCELED,
            OperationStatus.FAILED,
        )
        OperationStatus.FAILED,
        OperationStatus.CANCELED,
        OperationStatus.SUCCEEDED -> emptySet()
    }

    private fun validateMonotonic(
        previous: OperationProgress?,
        next: OperationProgress?,
    ) {
        if (previous == null || next == null) return
        require(previous.unit == null || next.unit == previous.unit) {
            "Progress units cannot change inside one stage"
        }
        require(previous.totalUnits == null || next.totalUnits == previous.totalUnits) {
            "A stage's known total cannot change"
        }
        require(previous.completedUnits == null || next.completedUnits != null) {
            "A stage's completed-work count cannot become unknown"
        }
        require(previous.completedUnits == null ||
            next.completedUnits!! >= previous.completedUnits) {
            "Progress cannot move backward inside one stage"
        }
    }

    private fun progressAdvanced(
        previous: OperationProgress?,
        next: OperationProgress?,
    ): Boolean {
        val completed = next?.completedUnits ?: return false
        val previousCompleted = previous?.completedUnits ?: return completed > 0L
        return completed > previousCompleted
    }

    private fun thresholdsFor(stage: OperationStage): StallThresholds = when (stage) {
        OperationStage.OPENING_SOURCE,
        OperationStage.UPLOADING,
        OperationStage.DOWNLOADING,
        OperationStage.WAITING_FOR_REMOTE -> StallThresholds(
            slowAfterMs = 8_000L,
            suspectedStallAfterMs = 45_000L,
            providerSuspectedStallAfterMs = 60_000L,
        )
        else -> StallThresholds()
    }

    private fun observeTime(record: Record): Long {
        val now = clock.nowMs()
        require(now >= record.lastObservedAtMs) { "Operation clock must be monotonic" }
        record.lastObservedAtMs = now
        return now
    }

    private fun publish(record: Record, now: Long) {
        record.lastPublishedAtMs = now
        publishAll()
    }

    private fun publishAll() {
        mutableStates.value = records.values.map { it.snapshot }
    }
}

private val OperationStatus.canReceiveProgress: Boolean
    get() = this == OperationStatus.RUNNING ||
        this == OperationStatus.WAITING_FOR_PROVIDER ||
        this == OperationStatus.SLOW ||
        this == OperationStatus.SUSPECTED_STALL
