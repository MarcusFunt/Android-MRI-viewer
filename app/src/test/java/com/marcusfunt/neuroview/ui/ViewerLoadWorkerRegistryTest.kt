package com.marcusfunt.neuroview.ui

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerLoadWorkerRegistryTest {
    @Test fun permitsOneDetachedReaderAndOneCurrentLoadThenEnforcesRecoveryCap() = runBlocking {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + Dispatchers.IO)
        val registry = ViewerLoadWorkerRegistry()
        val releaseA = CountDownLatch(1)
        val releaseB = CountDownLatch(1)
        val startedA = CountDownLatch(1)
        val startedB = CountDownLatch(1)
        val readerA = scope.launch(start = CoroutineStart.LAZY) {
            startedA.countDown()
            releaseA.await()
        }
        val readerB = scope.launch(start = CoroutineStart.LAZY) {
            startedB.countDown()
            releaseB.await()
        }

        try {
            assertTrue(registry.registerCurrentReader(readerA))
            readerA.start()
            assertTrue(startedA.await(5, TimeUnit.SECONDS))
            readerA.cancel()
            assertTrue(registry.detachCurrentProviderReader(readerA, insideProviderIo = true))
            assertTrue(registry.detachedReaderPending.value)
            assertEquals(1, registry.pendingReaderCount.value)
            assertTrue(registry.canStartCurrentReader())

            assertTrue(registry.registerCurrentReader(readerB))
            assertEquals(2, registry.pendingReaderCount.value)
            readerB.start()
            assertTrue(startedB.await(5, TimeUnit.SECONDS))
            readerB.cancel()
            assertFalse(registry.detachCurrentProviderReader(readerB, insideProviderIo = true))
            assertFalse(registry.canStartCurrentReader())

            releaseA.countDown()
            withTimeout(5_000) { readerA.join() }
            assertFalse(registry.detachedReaderPending.value)
            assertEquals(1, registry.pendingReaderCount.value)
            assertFalse(registry.canStartCurrentReader()) // B still owns the current slot.

            assertTrue(registry.detachCurrentProviderReader(readerB, insideProviderIo = true))
            assertTrue(registry.canStartCurrentReader())
            releaseB.countDown()
            withTimeout(5_000) { readerB.join() }
            assertFalse(registry.detachedReaderPending.value)
            assertEquals(0, registry.pendingReaderCount.value)
            assertTrue(registry.canStartCurrentReader())
        } finally {
            releaseA.countDown()
            releaseB.countDown()
            scope.cancel()
        }
    }

    @Test fun doesNotDetachAReaderWhenItIsNotInsideProviderIo() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val registry = ViewerLoadWorkerRegistry()
        val reader = scope.launch(start = CoroutineStart.LAZY) {
            started.countDown()
            release.await()
        }
        try {
            assertTrue(registry.registerCurrentReader(reader))
            reader.start()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            reader.cancel()
            // A non-provider worker is never eligible for detached-reader ownership.
            assertFalse(registry.detachCurrentProviderReader(reader, insideProviderIo = false))
            assertFalse(registry.canStartCurrentReader())
            assertEquals(1, registry.pendingReaderCount.value)
        } finally {
            release.countDown()
            withTimeout(5_000) { reader.join() }
            scope.cancel()
        }
    }
}
