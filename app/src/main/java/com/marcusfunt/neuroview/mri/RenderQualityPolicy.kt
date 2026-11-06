package com.marcusfunt.neuroview.mri

import kotlin.math.roundToInt

enum class RenderQualityMode(val label: String) {
    AUTO("Auto"),
    SMOOTH("Smooth"),
    BALANCED("Balanced"),
    HIGH_DETAIL("High detail"),
    MANUAL("Manual"),
}

enum class AdaptiveQualityLevel {
    LOW,
    STANDARD,
    HIGH,
}

enum class ThermalStatus {
    NOMINAL,
    MODERATE,
    SEVERE,
}

data class RenderQualitySettings(
    val mode: RenderQualityMode = RenderQualityMode.AUTO,
    val adaptive: Boolean = true,
    val previewMaxSide: Int = 160,
    val stillMaxSide: Int = 768,
    val refineDelayMs: Long = 72L,
) {
    fun validated(): RenderQualitySettings {
        val preview = previewMaxSide.coerceIn(MIN_PREVIEW_SIDE, MAX_PREVIEW_SIDE)
        val still = stillMaxSide.coerceIn(MIN_STILL_SIDE, MAX_STILL_SIDE).coerceAtLeast(preview)
        return copy(
            previewMaxSide = preview,
            stillMaxSide = still,
            refineDelayMs = refineDelayMs.coerceIn(MIN_REFINE_DELAY_MS, MAX_REFINE_DELAY_MS),
        )
    }

    companion object {
        const val MIN_PREVIEW_SIDE = 96
        const val MAX_PREVIEW_SIDE = 320
        const val MIN_STILL_SIDE = 192
        const val MAX_STILL_SIDE = 1536
        const val MIN_REFINE_DELAY_MS = 40L
        const val MAX_REFINE_DELAY_MS = 250L
    }
}

data class RenderEnvironmentSnapshot(
    val maxHeapBytes: Long,
    val availableHeapBytes: Long = maxHeapBytes,
    val viewportMaxSidePx: Int,
    val powerSave: Boolean,
    val thermalStatus: ThermalStatus,
    val zoomScale: Float = 1f,
    val sourceMaxSide: Int = RenderQualitySettings.MAX_STILL_SIDE,
    val usedHeapBytes: Long = (maxHeapBytes - availableHeapBytes).coerceAtLeast(0L),
    val nativeHeapAllocatedBytes: Long = 0L,
    val processPssBytes: Long = 0L,
    val bitmapBytes: Long = 0L,
    val cachedRenderBytes: Long = 0L,
    val systemAvailableBytes: Long = Long.MAX_VALUE,
    val lowMemory: Boolean = false,
    val trimMemoryLevel: Int = 0,
    val memorySafetyCapOverride: Int? = null,
) {
    /** Java headroom minus measured image/cache allocations and a conservative native reserve. */
    val effectiveAvailableHeapBytes: Long
        get() = (availableHeapBytes.coerceAtLeast(0L) - bitmapBytes.coerceAtLeast(0L) -
            cachedRenderBytes.coerceAtLeast(0L) -
            nativeHeapAllocatedBytes.coerceAtLeast(0L).coerceAtMost(64L * MEBIBYTE) / 4L)
            .coerceAtLeast(0L)

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}

/** Source-compatible name for callers that only need the snapshot inputs. */
typealias RenderEnvironment = RenderEnvironmentSnapshot

data class RenderQualityProfile(
    val previewMaxSide: Int,
    val stillMaxSide: Int,
    val refineDelayMs: Long,
    val requestedPreviewMaxSide: Int = previewMaxSide,
    val requestedStillMaxSide: Int = stillMaxSide,
    val constraintReason: String? = null,
)

object RenderQualityPolicy {
    fun resolve(
        settings: RenderQualitySettings,
        environment: RenderEnvironment,
        adaptiveLevel: AdaptiveQualityLevel = AdaptiveQualityLevel.STANDARD,
    ): RenderQualityProfile {
        val selected = settings.validated()
        val (basePreview, baseStill) = when (selected.mode) {
            RenderQualityMode.AUTO,
            RenderQualityMode.BALANCED -> 160 to 768
            RenderQualityMode.SMOOTH -> 112 to 512
            RenderQualityMode.HIGH_DETAIL -> 224 to 1024
            RenderQualityMode.MANUAL -> selected.previewMaxSide to selected.stillMaxSide
        }
        val requestedPreview = basePreview
        val requestedStill = baseStill

        var level = adaptiveLevel
        if (selected.adaptive) {
            if (environment.maxHeapBytes < 192L * MEBIBYTE) level = level.lower()
            if (environment.powerSave || environment.thermalStatus >= ThermalStatus.MODERATE) {
                level = level.lower()
            }
        } else {
            level = AdaptiveQualityLevel.STANDARD
        }

        val (preview, still) = when (level) {
            AdaptiveQualityLevel.LOW -> basePreview * 4 / 5 to baseStill * 5 / 6
            AdaptiveQualityLevel.STANDARD -> basePreview to baseStill
            AdaptiveQualityLevel.HIGH -> basePreview * 6 / 5 to baseStill * 7 / 6
        }
        val measuredMemoryCap = memorySafeMaximumSide(
            environment.maxHeapBytes,
            environment.effectiveAvailableHeapBytes,
        )
        // The resource controller has already applied immediate pressure rules and hysteresis.
        // Once present, its cap is authoritative so sample-to-sample heap noise cannot rebuild a
        // render session. Direct policy callers still receive the stateless measured cap.
        val memoryCap = environment.memorySafetyCapOverride
            ?.coerceIn(192, RenderQualitySettings.MAX_STILL_SIDE)
            ?: measuredMemoryCap
        val zoomAdjustedViewportCap = (environment.viewportMaxSidePx.coerceAtLeast(1) *
            environment.zoomScale.coerceIn(1f, 4f)).roundToInt()
        val sourceCap = environment.sourceMaxSide.coerceIn(8, RenderQualitySettings.MAX_STILL_SIDE)
        val safeCap = minOf(memoryCap, zoomAdjustedViewportCap, sourceCap)
        val safePreview = minOf(preview, safeCap).coerceAtLeast(minOf(96, safeCap))
        val safeStill = minOf(still, safeCap).coerceAtLeast(safePreview)

        val reasons = buildList {
            if (selected.adaptive && (level != AdaptiveQualityLevel.STANDARD || environment.powerSave ||
                    environment.thermalStatus >= ThermalStatus.MODERATE)
            ) {
                add(when {
                    environment.powerSave -> "adaptive quality for battery saver"
                    environment.thermalStatus >= ThermalStatus.MODERATE -> "adaptive quality for device temperature"
                    else -> "adaptive quality for render performance"
                })
            }
            if (safeStill < still) {
                add(when {
                    memoryCap <= minOf(zoomAdjustedViewportCap, sourceCap) -> "reduced for memory headroom"
                    zoomAdjustedViewportCap <= sourceCap -> "limited by viewport size"
                    else -> "limited by scan grid"
                })
            }
        }

        return RenderQualityProfile(
            previewMaxSide = safePreview,
            stillMaxSide = safeStill,
            refineDelayMs = selected.refineDelayMs,
            requestedPreviewMaxSide = requestedPreview,
            requestedStillMaxSide = requestedStill,
            constraintReason = reasons.distinct().joinToString("; ").ifEmpty { null },
        )
    }

    fun memorySafeMaximumSide(maxHeapBytes: Long, availableHeapBytes: Long = maxHeapBytes): Int {
        val heapClassCap = when {
            maxHeapBytes < 192L * MEBIBYTE -> 320
            maxHeapBytes < 384L * MEBIBYTE -> 512
            else -> RenderQualitySettings.MAX_STILL_SIDE
        }
        val currentlyAvailableCap = when {
            availableHeapBytes < 16L * MEBIBYTE -> 192
            availableHeapBytes < 32L * MEBIBYTE -> 256
            availableHeapBytes < 64L * MEBIBYTE -> 384
            availableHeapBytes < 96L * MEBIBYTE -> 512
            else -> RenderQualitySettings.MAX_STILL_SIDE
        }
        return minOf(heapClassCap, currentlyAvailableCap)
    }

    private fun AdaptiveQualityLevel.lower() = when (this) {
        AdaptiveQualityLevel.HIGH -> AdaptiveQualityLevel.STANDARD
        AdaptiveQualityLevel.STANDARD,
        AdaptiveQualityLevel.LOW -> AdaptiveQualityLevel.LOW
    }

    private const val MEBIBYTE = 1024L * 1024L
}

data class RenderResourceDecision(
    val maximumSide: Int,
    val reason: String? = null,
)

/**
 * Dampens ordinary memory-driven quality changes while honoring Android's explicit low-memory
 * signal immediately. All timing is injected so its behavior is deterministic in JVM tests.
 */
class RenderResourceQualityController(
    private val downshiftSamples: Int = 2,
    private val recoverySamples: Int = 5,
    private val recoveryCooldownMs: Long = 15_000L,
) {
    init {
        require(downshiftSamples >= 1)
        require(recoverySamples >= 1)
        require(recoveryCooldownMs >= 0L)
    }

    private var currentCap: Int? = null
    private var pendingCap: Int? = null
    private var pendingSamples = 0
    private var pendingSinceMs = 0L

    fun update(snapshot: RenderEnvironmentSnapshot, nowMillis: Long): RenderResourceDecision {
        val candidate = if (snapshot.lowMemory) 192 else RenderQualityPolicy.memorySafeMaximumSide(
            snapshot.maxHeapBytes,
            snapshot.effectiveAvailableHeapBytes,
        )
        val current = currentCap
        if (current == null) {
            currentCap = candidate
            clearPending()
        } else if (candidate < current && snapshot.lowMemory) {
            currentCap = minOf(current, candidate)
            clearPending()
        } else if (candidate != current) {
            if (pendingCap != candidate) {
                pendingCap = candidate
                pendingSamples = 1
                pendingSinceMs = nowMillis
            } else {
                pendingSamples++
            }
            val shouldApply = if (candidate < current) {
                pendingSamples >= downshiftSamples
            } else {
                pendingSamples >= recoverySamples &&
                    nowMillis - pendingSinceMs >= recoveryCooldownMs
            }
            if (shouldApply) {
                currentCap = candidate
                clearPending()
            }
        } else {
            clearPending()
        }

        val cap = requireNotNull(currentCap)
        return RenderResourceDecision(
            maximumSide = cap,
            reason = when {
                snapshot.lowMemory -> "Android reported low memory"
                cap < RenderQualitySettings.MAX_STILL_SIDE -> "reduced for memory headroom"
                else -> null
            },
        )
    }

    private fun clearPending() {
        pendingCap = null
        pendingSamples = 0
        pendingSinceMs = 0L
    }
}

/** Moving timing windows with hysteresis avoid changing resolution on a single slow frame. */
class AdaptiveRenderQualityController(
    private val windowSize: Int = 4,
    private val historySize: Int = 16,
    private val slowThresholdMs: Long = 45L,
    private val fastThresholdMs: Long = 20L,
    private val downshiftCooldownMs: Long = 1_500L,
    private val recoveryCooldownMs: Long = 15_000L,
) {
    init {
        require(windowSize >= 4)
        require(historySize >= windowSize)
        require(slowThresholdMs > fastThresholdMs && fastThresholdMs >= 0L)
        require(downshiftCooldownMs >= 0L && recoveryCooldownMs >= 0L)
    }

    private val history = ArrayDeque<Long>()
    private var samplesSinceEvaluation = 0
    private var consecutiveSlowWindows = 0
    private var consecutiveFastWindows = 0
    private var lastQualityChangeAtMs: Long? = null

    var level: AdaptiveQualityLevel = AdaptiveQualityLevel.STANDARD
        private set

    fun recordRenderDuration(
        durationMs: Long,
        nowMillis: Long = System.nanoTime() / 1_000_000L,
    ): AdaptiveQualityLevel {
        history.addLast(durationMs.coerceAtLeast(0L))
        while (history.size > historySize) history.removeFirst()
        samplesSinceEvaluation++
        if (samplesSinceEvaluation < windowSize || history.size < windowSize) return level
        samplesSinceEvaluation = 0

        val ordered = history.sorted()
        val p75 = ordered[((ordered.size - 1) * 3.0 / 4.0).roundToInt()]
        when {
            p75 > slowThresholdMs -> {
                consecutiveSlowWindows++
                consecutiveFastWindows = 0
            }
            p75 < fastThresholdMs -> {
                consecutiveFastWindows++
                consecutiveSlowWindows = 0
            }
            else -> {
                consecutiveSlowWindows = 0
                consecutiveFastWindows = 0
            }
        }

        if (consecutiveSlowWindows >= 2 && level != AdaptiveQualityLevel.LOW &&
            cooldownElapsed(nowMillis, downshiftCooldownMs)
        ) {
            level = if (level == AdaptiveQualityLevel.HIGH) {
                AdaptiveQualityLevel.STANDARD
            } else {
                AdaptiveQualityLevel.LOW
            }
            lastQualityChangeAtMs = nowMillis
            resetHysteresis()
        } else if (consecutiveFastWindows >= 3 && level != AdaptiveQualityLevel.HIGH) {
            if (cooldownElapsed(nowMillis, recoveryCooldownMs)) {
                level = if (level == AdaptiveQualityLevel.LOW) {
                    AdaptiveQualityLevel.STANDARD
                } else {
                    AdaptiveQualityLevel.HIGH
                }
                lastQualityChangeAtMs = nowMillis
                resetHysteresis()
            }
        }
        return level
    }

    private fun cooldownElapsed(nowMillis: Long, durationMs: Long): Boolean =
        lastQualityChangeAtMs?.let { nowMillis - it >= durationMs } ?: true

    private fun resetHysteresis() {
        history.clear()
        samplesSinceEvaluation = 0
        consecutiveSlowWindows = 0
        consecutiveFastWindows = 0
    }
}

/** A render may commit only while its generation remains the newest request. */
class RenderRequestGate {
    private var generation = 0L

    fun begin(): Long {
        generation += 1L
        return generation
    }

    fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation
}

data class RenderPerformanceSnapshot(
    val sampleCount: Int = 0,
    val p50Ms: Long? = null,
    val p95Ms: Long? = null,
    val p99Ms: Long? = null,
    val supersededPercent: Int = 0,
    val peakHeapUsedBytes: Long = 0L,
    val maxObservedHeapIncreaseBytes: Long = 0L,
    val displayedFrameCount: Int = 0,
    val gestureToCommitP50Ms: Long? = null,
    val gestureToCommitP95Ms: Long? = null,
    val gestureToCommitP99Ms: Long? = null,
    val canceledFrameCount: Int = 0,
    val peakNativeHeapBytes: Long = 0L,
    val peakProcessPssBytes: Long = 0L,
    val peakBitmapBytes: Long = 0L,
)

/** Bounded render samples for local diagnostics; it never stores scan identity or image data. */
class RenderPerformanceMonitor(private val sampleLimit: Int = 64) {
    init {
        require(sampleLimit in 1..512)
    }

    private data class Sample(
        val durationMs: Long,
        val committed: Boolean,
        val heapUsedBytes: Long,
        val nativeHeapBytes: Long,
        val processPssBytes: Long,
        val bitmapBytes: Long,
        val canceled: Boolean,
    )

    private val samples = ArrayDeque<Sample>()
    private val displayLatencies = ArrayDeque<Long>()
    private val committedGenerations = LinkedHashSet<Long>()
    private var peakHeapUsedBytes = 0L
    private var previousHeapUsedBytes: Long? = null
    private var maxObservedHeapIncreaseBytes = 0L
    private var peakNativeHeapBytes = 0L
    private var peakProcessPssBytes = 0L
    private var peakBitmapBytes = 0L

    fun recordRender(
        durationMs: Long,
        committed: Boolean,
        heapUsedBytes: Long,
        nativeHeapBytes: Long = 0L,
        processPssBytes: Long = 0L,
        bitmapBytes: Long = 0L,
        canceled: Boolean = false,
    ): RenderPerformanceSnapshot {
        val sample = Sample(
            durationMs = durationMs.coerceAtLeast(0L),
            committed = committed,
            heapUsedBytes = heapUsedBytes.coerceAtLeast(0L),
            nativeHeapBytes = nativeHeapBytes.coerceAtLeast(0L),
            processPssBytes = processPssBytes.coerceAtLeast(0L),
            bitmapBytes = bitmapBytes.coerceAtLeast(0L),
            canceled = canceled,
        )
        samples.addLast(sample)
        while (samples.size > sampleLimit) samples.removeFirst()
        peakHeapUsedBytes = maxOf(peakHeapUsedBytes, sample.heapUsedBytes)
        previousHeapUsedBytes?.let { previous ->
            maxObservedHeapIncreaseBytes = maxOf(
                maxObservedHeapIncreaseBytes,
                (sample.heapUsedBytes - previous).coerceAtLeast(0L),
            )
        }
        previousHeapUsedBytes = sample.heapUsedBytes
        peakNativeHeapBytes = maxOf(peakNativeHeapBytes, sample.nativeHeapBytes)
        peakProcessPssBytes = maxOf(peakProcessPssBytes, sample.processPssBytes)
        peakBitmapBytes = maxOf(peakBitmapBytes, sample.bitmapBytes)
        return snapshot()
    }

    fun recordComposeCommit(generation: Long, latencyMs: Long): RenderPerformanceSnapshot {
        if (committedGenerations.add(generation)) {
            while (committedGenerations.size > sampleLimit * 2) {
                committedGenerations.remove(committedGenerations.first())
            }
            displayLatencies.addLast(latencyMs.coerceAtLeast(0L))
            while (displayLatencies.size > sampleLimit) displayLatencies.removeFirst()
        }
        return snapshot()
    }

    private fun snapshot(): RenderPerformanceSnapshot {
        val renderTimes = samples.map { it.durationMs }
        val commitTimes = displayLatencies.toList()
        val superseded = samples.count { !it.committed }
        return RenderPerformanceSnapshot(
            sampleCount = samples.size,
            p50Ms = renderTimes.percentile(0.50),
            p95Ms = renderTimes.percentile(0.95),
            p99Ms = renderTimes.percentile(0.99),
            supersededPercent = if (samples.isEmpty()) 0 else {
                (superseded * 100.0 / samples.size).roundToInt()
            },
            peakHeapUsedBytes = peakHeapUsedBytes,
            maxObservedHeapIncreaseBytes = maxObservedHeapIncreaseBytes,
            displayedFrameCount = displayLatencies.size,
            gestureToCommitP50Ms = commitTimes.percentile(0.50),
            gestureToCommitP95Ms = commitTimes.percentile(0.95),
            gestureToCommitP99Ms = commitTimes.percentile(0.99),
            canceledFrameCount = samples.count { it.canceled },
            peakNativeHeapBytes = peakNativeHeapBytes,
            peakProcessPssBytes = peakProcessPssBytes,
            peakBitmapBytes = peakBitmapBytes,
        )
    }

    private fun List<Long>.percentile(percent: Double): Long? {
        if (isEmpty()) return null
        val ordered = sorted()
        val index = kotlin.math.ceil(percent * ordered.size).toInt().coerceAtLeast(1) - 1
        return ordered[index.coerceAtMost(ordered.lastIndex)]
    }
}
