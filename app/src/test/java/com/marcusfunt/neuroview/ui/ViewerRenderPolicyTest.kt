package com.marcusfunt.neuroview.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerRenderPolicyTest {
    @Test
    fun clampsQualityToSupportedRange() {
        assertEquals(96, ViewerRenderPolicy.previewResolution(384, 0.01f))
        assertEquals(288, ViewerRenderPolicy.previewResolution(384, 0.99f))
    }

    @Test
    fun previewResolutionScalesFullRender() {
        assertEquals(154, ViewerRenderPolicy.previewResolution(384, 0.40f))
    }
}
