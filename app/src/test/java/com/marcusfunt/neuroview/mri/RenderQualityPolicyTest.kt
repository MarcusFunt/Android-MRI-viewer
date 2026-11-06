package com.marcusfunt.neuroview.mri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderQualityPolicyTest {
    @Test
    fun manualSettingsAreClampedToSupportedBounds() {
        val settings = RenderQualitySettings(
            mode = RenderQualityMode.MANUAL,
            adaptive = true,
            previewMaxSide = 40,
            stillMaxSide = 1800,
            refineDelayMs = 500,
        ).validated()

        assertEquals(96, settings.previewMaxSide)
        assertEquals(1536, settings.stillMaxSide)
        assertEquals(250L, settings.refineDelayMs)
    }

    @Test
    fun stillResolutionCannotFallBelowScrollingPreviewResolution() {
        val settings = RenderQualitySettings(
            mode = RenderQualityMode.MANUAL,
            previewMaxSide = 320,
            stillMaxSide = 192,
        ).validated()

        assertEquals(320, settings.stillMaxSide)
    }

    @Test
    fun balancedModeKeepsBaselineResolutionWhenAdaptiveRenderingIsOff() {
        val profile = RenderQualityPolicy.resolve(
            RenderQualitySettings(mode = RenderQualityMode.BALANCED, adaptive = false),
            RenderEnvironment(
                maxHeapBytes = 512L * 1024 * 1024,
                viewportMaxSidePx = 1200,
                powerSave = false,
                thermalStatus = ThermalStatus.NOMINAL,
            ),
            AdaptiveQualityLevel.LOW,
        )

        assertEquals(RenderQualityProfile(160, 768, 72L), profile)
    }

    @Test
    fun manualModeUsesSelectedResolutionWithinMemoryCap() {
        val profile = RenderQualityPolicy.resolve(
            RenderQualitySettings(
                mode = RenderQualityMode.MANUAL,
                adaptive = false,
                previewMaxSide = 256,
                stillMaxSide = 720,
                refineDelayMs = 110,
            ),
            RenderEnvironment(
                maxHeapBytes = 256L * 1024 * 1024,
                viewportMaxSidePx = 1000,
                powerSave = false,
                thermalStatus = ThermalStatus.NOMINAL,
            ),
            AdaptiveQualityLevel.HIGH,
        )

        assertEquals(
            RenderQualityProfile(256, 512, 110L, requestedPreviewMaxSide = 256,
                requestedStillMaxSide = 720, constraintReason = "reduced for memory headroom"),
            profile,
        )
    }

    @Test
    fun availableHeapCapsManualQualityEvenWhenAdaptiveRenderingIsOff() {
        val profile = RenderQualityPolicy.resolve(
            RenderQualitySettings(
                mode = RenderQualityMode.MANUAL,
                adaptive = false,
                previewMaxSide = 320,
                stillMaxSide = 768,
            ),
            RenderEnvironment(
                maxHeapBytes = 512L * 1024 * 1024,
                availableHeapBytes = 30L * 1024 * 1024,
                viewportMaxSidePx = 1200,
                powerSave = false,
                thermalStatus = ThermalStatus.NOMINAL,
            ),
            AdaptiveQualityLevel.HIGH,
        )

        assertEquals(
            RenderQualityProfile(256, 256, 72L, requestedPreviewMaxSide = 320,
                requestedStillMaxSide = 768, constraintReason = "reduced for memory headroom"),
            profile,
        )
    }

    @Test
    fun autoModeAdaptsResolutionForPowerAndThermalConstraints() {
        val profile = RenderQualityPolicy.resolve(
            RenderQualitySettings(mode = RenderQualityMode.AUTO, adaptive = true),
            RenderEnvironment(
                maxHeapBytes = 512L * 1024 * 1024,
                viewportMaxSidePx = 1200,
                powerSave = true,
                thermalStatus = ThermalStatus.MODERATE,
            ),
            AdaptiveQualityLevel.STANDARD,
        )

        assertEquals(
            RenderQualityProfile(128, 640, 72L, requestedPreviewMaxSide = 160,
                requestedStillMaxSide = 768, constraintReason = "adaptive quality for battery saver"),
            profile,
        )
    }

    @Test
    fun settledResolutionFollowsZoomAndDoesNotExceedSourceGridInformation() {
        val zoomed = RenderQualityPolicy.resolve(
            RenderQualitySettings(mode = RenderQualityMode.HIGH_DETAIL, adaptive = false),
            RenderEnvironment(
                maxHeapBytes = 1024L * 1024 * 1024,
                viewportMaxSidePx = 400,
                powerSave = false,
                thermalStatus = ThermalStatus.NOMINAL,
                zoomScale = 2f,
                sourceMaxSide = 1200,
            ),
        )
        assertEquals(800, zoomed.stillMaxSide)

        val sourceLimited = RenderQualityPolicy.resolve(
            RenderQualitySettings(mode = RenderQualityMode.HIGH_DETAIL, adaptive = false),
            RenderEnvironment(
                maxHeapBytes = 1024L * 1024 * 1024,
                viewportMaxSidePx = 1600,
                powerSave = false,
                thermalStatus = ThermalStatus.NOMINAL,
                zoomScale = 3f,
                sourceMaxSide = 512,
            ),
        )
        assertEquals(512, sourceLimited.stillMaxSide)
    }

    @Test
    fun adaptiveQualityRequiresRepeatedSlowWindowsAndSeveralFastWindowsToRestore() {
        val controller = AdaptiveRenderQualityController(recoveryCooldownMs = 10_000)

        repeat(7) { controller.recordRenderDuration(70L, nowMillis = it * 100L) }
        assertEquals(AdaptiveQualityLevel.STANDARD, controller.level)
        controller.recordRenderDuration(70L, nowMillis = 700L)
        assertEquals(AdaptiveQualityLevel.LOW, controller.level)

        repeat(12) { controller.recordRenderDuration(12L, nowMillis = 800L + it * 100L) }
        assertEquals(AdaptiveQualityLevel.LOW, controller.level)
        repeat(4) { controller.recordRenderDuration(12L, nowMillis = 11_000L + it * 100L) }
        assertEquals(AdaptiveQualityLevel.STANDARD, controller.level)
    }

    @Test
    fun staleSliceGenerationCannotCommitOverANewerRequest() {
        val gate = RenderRequestGate()

        val earlier = gate.begin()
        val latest = gate.begin()

        assertFalse(gate.isCurrent(earlier))
        assertTrue(gate.isCurrent(latest))
    }

    @Test
    fun renderMetricsReportTimingPercentilesSupersededWorkAndPeakHeap() {
        val monitor = RenderPerformanceMonitor(sampleLimit = 8)

        monitor.recordRender(durationMs = 10L, committed = true, heapUsedBytes = 100L)
        monitor.recordRender(durationMs = 30L, committed = true, heapUsedBytes = 200L)
        val snapshot = monitor.recordRender(
            durationMs = 20L,
            committed = false,
            heapUsedBytes = 250L,
        )

        assertEquals(20L, snapshot.p50Ms)
        assertEquals(30L, snapshot.p95Ms)
        assertEquals(30L, snapshot.p99Ms)
        assertEquals(33, snapshot.supersededPercent)
        assertEquals(250L, snapshot.peakHeapUsedBytes)
        assertEquals(100L, snapshot.maxObservedHeapIncreaseBytes)
    }

    @Test
    fun composeCommitLatencyPercentilesCountEachRenderGenerationOnce() {
        val monitor = RenderPerformanceMonitor(sampleLimit = 8)

        monitor.recordComposeCommit(generation = 1L, latencyMs = 20L)
        monitor.recordComposeCommit(generation = 2L, latencyMs = 40L)
        monitor.recordComposeCommit(generation = 2L, latencyMs = 900L)
        val snapshot = monitor.recordComposeCommit(generation = 3L, latencyMs = 30L)

        assertEquals(3, snapshot.displayedFrameCount)
        assertEquals(30L, snapshot.gestureToCommitP50Ms)
        assertEquals(40L, snapshot.gestureToCommitP95Ms)
        assertEquals(40L, snapshot.gestureToCommitP99Ms)
    }

    @Test
    fun reportsRequestedAndEffectiveQualityWhenMemorySafetyReducesResolution() {
        val profile = RenderQualityPolicy.resolve(
            RenderQualitySettings(
                mode = RenderQualityMode.MANUAL,
                adaptive = false,
                previewMaxSide = 256,
                stillMaxSide = 768,
            ),
            RenderEnvironmentSnapshot(
                maxHeapBytes = 512L * 1024 * 1024,
                availableHeapBytes = 30L * 1024 * 1024,
                viewportMaxSidePx = 1200,
                powerSave = false,
                thermalStatus = ThermalStatus.NOMINAL,
            ),
        )

        assertEquals(256, profile.requestedPreviewMaxSide)
        assertEquals(768, profile.requestedStillMaxSide)
        assertEquals(256, profile.previewMaxSide)
        assertEquals(256, profile.stillMaxSide)
        assertTrue(profile.constraintReason.orEmpty().contains("memory", ignoreCase = true))
    }

    @Test
    fun graphicsAndSessionMemoryAreIncludedInEffectiveHeadroom() {
        val snapshot = RenderEnvironmentSnapshot(
            maxHeapBytes = 512L * 1024 * 1024,
            availableHeapBytes = 80L * 1024 * 1024,
            nativeHeapAllocatedBytes = 48L * 1024 * 1024,
            processPssBytes = 180L * 1024 * 1024,
            bitmapBytes = 20L * 1024 * 1024,
            cachedRenderBytes = 8L * 1024 * 1024,
            viewportMaxSidePx = 1200,
            powerSave = false,
            thermalStatus = ThermalStatus.NOMINAL,
        )

        assertTrue(snapshot.effectiveAvailableHeapBytes < snapshot.availableHeapBytes)
        assertTrue(
            RenderQualityPolicy.resolve(
                RenderQualitySettings(mode = RenderQualityMode.MANUAL, adaptive = false),
                snapshot,
            ).stillMaxSide <= 512,
        )
    }

    @Test
    fun resourceCapDownshiftsAfterRepeatedPressureAndRecoversAfterStableCooldown() {
        val controller = RenderResourceQualityController(
            downshiftSamples = 2,
            recoverySamples = 3,
            recoveryCooldownMs = 10_000,
        )
        fun snapshot(availableMiB: Int) = RenderEnvironmentSnapshot(
            maxHeapBytes = 512L * 1024 * 1024,
            availableHeapBytes = availableMiB * 1024L * 1024,
            viewportMaxSidePx = 1200,
            powerSave = false,
            thermalStatus = ThermalStatus.NOMINAL,
        )

        assertEquals(1536, controller.update(snapshot(120), nowMillis = 0).maximumSide)
        assertEquals(1536, controller.update(snapshot(30), nowMillis = 1_000).maximumSide)
        assertEquals(256, controller.update(snapshot(30), nowMillis = 4_000).maximumSide)
        assertEquals(256, controller.update(snapshot(120), nowMillis = 5_000).maximumSide)
        assertEquals(256, controller.update(snapshot(120), nowMillis = 10_000).maximumSide)
        assertEquals(1536, controller.update(snapshot(120), nowMillis = 15_000).maximumSide)
    }

    @Test
    fun oneNoisyMemorySampleDoesNotChangeTheEffectiveRenderSessionSize() {
        val controller = RenderResourceQualityController()
        val healthy = RenderEnvironmentSnapshot(
            maxHeapBytes = 512L * 1024 * 1024,
            availableHeapBytes = 120L * 1024 * 1024,
            viewportMaxSidePx = 1200,
            powerSave = false,
            thermalStatus = ThermalStatus.NOMINAL,
        )
        val healthyDecision = controller.update(healthy, nowMillis = 0)
        val noisy = healthy.copy(availableHeapBytes = 30L * 1024 * 1024)
        val noisyDecision = controller.update(noisy, nowMillis = 1_000)
        val profile = RenderQualityPolicy.resolve(
            RenderQualitySettings(mode = RenderQualityMode.MANUAL, adaptive = false),
            noisy.copy(memorySafetyCapOverride = noisyDecision.maximumSide),
        )

        assertEquals(1536, healthyDecision.maximumSide)
        assertEquals(1536, noisyDecision.maximumSide)
        assertEquals(768, profile.stillMaxSide)
    }

    @Test
    fun androidLowMemorySignalForcesImmediateSafeCap() {
        val controller = RenderResourceQualityController()
        val snapshot = RenderEnvironmentSnapshot(
            maxHeapBytes = 1024L * 1024 * 1024,
            availableHeapBytes = 700L * 1024 * 1024,
            lowMemory = true,
            viewportMaxSidePx = 1200,
            powerSave = false,
            thermalStatus = ThermalStatus.NOMINAL,
        )

        assertEquals(192, controller.update(snapshot, nowMillis = 0).maximumSide)
    }

    @Test
    fun adaptiveRenderTimingReductionIsExplainedSeparatelyFromUserRequest() {
        val profile = RenderQualityPolicy.resolve(
            RenderQualitySettings(mode = RenderQualityMode.BALANCED, adaptive = true),
            RenderEnvironment(
                maxHeapBytes = 512L * 1024 * 1024,
                viewportMaxSidePx = 1200,
                powerSave = false,
                thermalStatus = ThermalStatus.NOMINAL,
            ),
            adaptiveLevel = AdaptiveQualityLevel.LOW,
        )

        assertEquals(768, profile.requestedStillMaxSide)
        assertEquals(640, profile.stillMaxSide)
        assertTrue(profile.constraintReason.orEmpty().contains("render performance"))
    }

    @Test
    fun renderMetricsTrackNativePssAndCanceledFrames() {
        val monitor = RenderPerformanceMonitor()

        val snapshot = monitor.recordRender(
            durationMs = 18,
            committed = false,
            heapUsedBytes = 100,
            nativeHeapBytes = 40,
            processPssBytes = 160,
            bitmapBytes = 24,
            canceled = true,
        )

        assertEquals(1, snapshot.canceledFrameCount)
        assertEquals(40, snapshot.peakNativeHeapBytes)
        assertEquals(160, snapshot.peakProcessPssBytes)
        assertEquals(24, snapshot.peakBitmapBytes)
    }
}
