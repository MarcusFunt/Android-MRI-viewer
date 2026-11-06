package com.marcusfunt.neuroview.brain

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainGlTextureViewShutdownTest {
    @Test
    fun stubbornRenderThreadCannotMakeShutdownWaitUnboundedly() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val thread = Thread {
            started.countDown()
            while (release.count > 0) {
                try {
                    release.await()
                } catch (_: InterruptedException) {
                    // Simulates an EGL call that does not immediately respond to interruption.
                }
            }
        }
        thread.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))

        val beganAt = System.nanoTime()
        try {
            assertFalse(stopRenderThreadBounded(thread, gracefulWaitMillis = 40, interruptWaitMillis = 40))
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beganAt)
            assertTrue("Shutdown waited $elapsedMillis ms despite its 80 ms bound", elapsedMillis < 300)
        } finally {
            release.countDown()
            thread.join(1_000)
        }
        assertFalse(thread.isAlive)
    }

    @Test
    fun alreadyStoppedThreadReturnsImmediately() {
        val thread = Thread {}
        thread.start()
        thread.join(1_000)
        assertTrue(stopRenderThreadBounded(thread))
    }
}
