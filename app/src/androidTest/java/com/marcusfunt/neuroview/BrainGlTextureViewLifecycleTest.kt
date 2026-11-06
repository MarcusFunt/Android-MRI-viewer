package com.marcusfunt.neuroview

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.marcusfunt.neuroview.brain.BrainGlTextureView
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrainGlTextureViewLifecycleTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun renderThreadProducesFramesAfterPauseAndResume() {
        val view = attachPreview()
        waitForFrame(view, after = 0L)

        val frameCountAtResume = longArrayOf(0L)
        compose.activityRule.scenario.onActivity {
            view.onPause()
            frameCountAtResume[0] = view.presentedFrameCountForTests
            view.onResume()
        }

        waitForFrame(view, after = frameCountAtResume[0])
    }

    @Test
    fun renderThreadRecoversAfterTextureViewDetachAndReattach() {
        val view = attachPreview()
        waitForFrame(view, after = 0L)
        val frameCountBeforeReattach = view.presentedFrameCountForTests

        compose.activityRule.scenario.onActivity { activity ->
            val host = activity.findViewById<FrameLayout>(TEST_HOST_ID)
            host.removeView(view)
            host.addView(
                view,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            view.onResume()
        }

        waitForFrame(view, after = frameCountBeforeReattach)
    }

    private fun attachPreview(): BrainGlTextureView {
        val reference = AtomicReference<BrainGlTextureView>()
        compose.activityRule.scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            content.removeAllViews()
            val host = FrameLayout(activity).apply { id = TEST_HOST_ID }
            content.addView(host, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val view = BrainGlTextureView(activity)
            host.addView(
                view,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            reference.set(view)
            view.onResume()
        }
        return requireNotNull(reference.get())
    }

    private fun waitForFrame(view: BrainGlTextureView, after: Long) {
        compose.waitUntil(timeoutMillis = 10_000L) {
            view.presentedFrameCountForTests > after
        }
        assertTrue(view.presentedFrameCountForTests > after)
    }

    private companion object {
        const val TEST_HOST_ID = 0x7f0b10a1
    }
}
