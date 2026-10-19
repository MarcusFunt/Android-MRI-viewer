package com.marcusfunt.neuroview.mri

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class SettledRenderDetailTest {
    @Test fun settledFramesRecoverMoreSharpFixtureDetailThanPreviewForEveryQualityMode() {
        val side = 513
        val values = FloatArray(side * side * 2) { index ->
            val x = index % side
            if ((x / 3) % 2 == 0) 255f else 0f
        }
        val volume = NiftiVolume(side, side, 2, values, SpatialAffine.axisAligned(1.0, 1.0, 1.0), 0f, 255f, "synthetic", 1)
        val environment = RenderEnvironment(768L * 1024 * 1024, viewportMaxSidePx = 1200,
            powerSave = false, thermalStatus = ThermalStatus.NOMINAL)
        RenderQualityMode.entries.forEach { mode ->
            val settings = RenderQualitySettings(mode = mode, adaptive = false, previewMaxSide = 160, stillMaxSide = 576)
            val profile = RenderQualityPolicy.resolve(settings, environment)
            val preview = MprRenderSession(volume, MriPlane.AXIAL, profile.previewMaxSide).render(0, 0.5f, 1f)
            val still = MprRenderSession(volume, MriPlane.AXIAL, profile.stillMaxSide).render(0, 0.5f, 1f)
            val previewError = reconstructionError(preview)
            val stillError = reconstructionError(still)
            println("Sharp synthetic fixture $mode: source 513, preview ${preview.width}, settled ${still.width}, grayscale MAE $previewError -> $stillError (JVM; not a device measurement)")
            assertTrue("$mode settled frame must recover useful detail", stillError < previewError)
        }
    }

    @Test fun lowDisplayRampRetainsAllOpaqueAchromaticLevels() {
        val values = FloatArray(33 * 8 * 2) { (it % 33).toFloat() }
        val volume = NiftiVolume(33, 8, 2, values, SpatialAffine.axisAligned(1.0, 1.0, 1.0), 0f, 255f, "synthetic", 1)
        val image = MprRenderSession(volume, MriPlane.AXIAL, 33).render(0, 0.5f, 1f)
        for (intensity in 0..32) {
            assertEquals(0xff000000.toInt() or (intensity * 0x010101), image.pixels[32 - intensity])
        }
    }

    private fun reconstructionError(image: MprImage): Double {
        // Compare a linearly displayed output row with known source voxel centres.
        return (0..512).sumOf { col ->
            val sourceX = 512 - col
            val expected = if ((sourceX / 3) % 2 == 0) 255 else 0
            val coordinate = col.toDouble() / 512 * (image.width - 1)
            val left = coordinate.toInt()
            val right = (left + 1).coerceAtMost(image.width - 1)
            val fraction = coordinate - left
            val actual = (image.pixels[left] and 255) * (1 - fraction) + (image.pixels[right] and 255) * fraction
            abs(actual - expected)
        } / 513
    }
}
