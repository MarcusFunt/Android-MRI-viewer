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
            stillMaxSide = 900,
            refineDelayMs = 500,
        ).validated()

        assertEquals(96, settings.previewMaxSide)
        assertEquals(768, settings.stillMaxSide)
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

        assertEquals(RenderQualityProfile(160, 384, 72L), profile)
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

        assertEquals(RenderQualityProfile(256, 512, 110L), profile)
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

        assertEquals(RenderQualityProfile(256, 256, 72L), profile)
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

        assertEquals(RenderQualityProfile(128, 320, 72L), profile)
    }

    @Test
    fun adaptiveQualityRequiresRepeatedSlowWindowsAndSeveralFastWindowsToRestore() {
        val controller = AdaptiveRenderQualityController()

        repeat(7) { controller.recordRenderDuration(70L) }
        assertEquals(AdaptiveQualityLevel.STANDARD, controller.level)
        controller.recordRenderDuration(70L)
        assertEquals(AdaptiveQualityLevel.LOW, controller.level)

        repeat(11) { controller.recordRenderDuration(12L) }
        assertEquals(AdaptiveQualityLevel.LOW, controller.level)
        controller.recordRenderDuration(12L)
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
}
