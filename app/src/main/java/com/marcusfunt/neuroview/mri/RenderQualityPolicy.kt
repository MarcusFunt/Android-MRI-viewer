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
    val stillMaxSide: Int = 384,
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
        const val MAX_STILL_SIDE = 768
        const val MIN_REFINE_DELAY_MS = 40L
        const val MAX_REFINE_DELAY_MS = 250L
    }
}

data class RenderEnvironment(
    val maxHeapBytes: Long,
    val availableHeapBytes: Long = maxHeapBytes,
    val viewportMaxSidePx: Int,
    val powerSave: Boolean,
    val thermalStatus: ThermalStatus,
)

data class RenderQualityProfile(
    val previewMaxSide: Int,
    val stillMaxSide: Int,
    val refineDelayMs: Long,
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
            RenderQualityMode.BALANCED -> 160 to 384
            RenderQualityMode.SMOOTH -> 112 to 256
            RenderQualityMode.HIGH_DETAIL -> 224 to 576
            RenderQualityMode.MANUAL -> selected.previewMaxSide to selected.stillMaxSide
        }

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
        val memoryCap = memorySafeMaximumSide(
            environment.maxHeapBytes,
            environment.availableHeapBytes,
        )
        val viewportCap = environment.viewportMaxSidePx.coerceAtLeast(96)
        val safeCap = minOf(memoryCap, viewportCap)
        val safePreview = preview.coerceIn(96, safeCap)
        val safeStill = still.coerceIn(safePreview, safeCap)

        return RenderQualityProfile(
            previewMaxSide = safePreview,
            stillMaxSide = safeStill,
            refineDelayMs = selected.refineDelayMs,
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

/** Moving timing windows with hysteresis avoid changing resolution on a single slow frame. */
class AdaptiveRenderQualityController(
    private val windowSize: Int = 4,
    private val historySize: Int = 16,
    private val slowThresholdMs: Long = 45L,
    private val fastThresholdMs: Long = 20L,
) {
    init {
        require(windowSize >= 4)
        require(historySize >= windowSize)
        require(slowThresholdMs > fastThresholdMs && fastThresholdMs >= 0L)
    }

    private val history = ArrayDeque<Long>()
    private var samplesSinceEvaluation = 0
    private var consecutiveSlowWindows = 0
    private var consecutiveFastWindows = 0

    var level: AdaptiveQualityLevel = AdaptiveQualityLevel.STANDARD
        private set

    fun recordRenderDuration(durationMs: Long): AdaptiveQualityLevel {
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

        if (consecutiveSlowWindows >= 2 && level != AdaptiveQualityLevel.LOW) {
            level = if (level == AdaptiveQualityLevel.HIGH) {
                AdaptiveQualityLevel.STANDARD
            } else {
                AdaptiveQualityLevel.LOW
            }
            resetHysteresis()
        } else if (consecutiveFastWindows >= 3 && level != AdaptiveQualityLevel.HIGH) {
            level = if (level == AdaptiveQualityLevel.LOW) {
                AdaptiveQualityLevel.STANDARD
            } else {
                AdaptiveQualityLevel.HIGH
            }
            resetHysteresis()
        }
        return level
    }

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
)

/** Bounded render samples for local diagnostics; it never stores scan identity or image data. */
class RenderPerformanceMonitor(private val sampleLimit: Int = 64) {
    init {
        require(sampleLimit in 1..512)
    }

    private data class Sample(val durationMs: Long, val committed: Boolean, val heapUsedBytes: Long)

    private val samples = ArrayDeque<Sample>()
    private val displayLatencies = ArrayDeque<Long>()
    private val committedGenerations = LinkedHashSet<Long>()
    private var peakHeapUsedBytes = 0L
    private var previousHeapUsedBytes: Long? = null
    private var maxObservedHeapIncreaseBytes = 0L

    fun recordRender(
        durationMs: Long,
        committed: Boolean,
        heapUsedBytes: Long,
    ): RenderPerformanceSnapshot {
        val sample = Sample(
            durationMs = durationMs.coerceAtLeast(0L),
            committed = committed,
            heapUsedBytes = heapUsedBytes.coerceAtLeast(0L),
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
        )
    }

    private fun List<Long>.percentile(percent: Double): Long? {
        if (isEmpty()) return null
        val ordered = sorted()
        val index = kotlin.math.ceil(percent * ordered.size).toInt().coerceAtLeast(1) - 1
        return ordered[index.coerceAtMost(ordered.lastIndex)]
    }
}
