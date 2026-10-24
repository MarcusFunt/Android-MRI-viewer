package com.marcusfunt.neuroview

import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.marcusfunt.neuroview.mri.FaultInjectionProvider
import com.marcusfunt.neuroview.mri.NiftiReadOptions
import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.ui.operations.*
import com.marcusfunt.neuroview.ui.ViewerLoadWorkerRegistry
import com.marcusfunt.neuroview.processing.copyStreamWithProgress
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Pass instrumentation argument providerLongRun=true for ~3 minutes of delivery and a real 61s pause. */
class DocumentProviderFaultTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val startedAt = SystemClock.elapsedRealtime()
    private fun log(message: String) {
        Log.i("ProviderFaultTest", "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}; $message")
    }
    private val uri = Uri.parse("content://${context.packageName}.fault-provider/synthetic.nii.gz")
    private val longRun get() = InstrumentationRegistry.getArguments().getString("providerLongRun") == "true"

    /** Holds a source read in flight across cancellation to emulate a provider that never returns. */
    private class IgnoringCancellationReadGate(
        input: InputStream,
        private val pauseAfterBytes: Long,
    ) : FilterInputStream(input) {
        val paused = CountDownLatch(1)
        val resume = CountDownLatch(1)
        private val bytesRead = AtomicLong()
        private val hasPaused = AtomicBoolean()

        private fun pauseIfNeeded() {
            if (bytesRead.get() < pauseAfterBytes || !hasPaused.compareAndSet(false, true)) return
            paused.countDown()
            while (true) {
                try {
                    resume.await()
                    return
                } catch (_: InterruptedException) {
                    // This gate models a provider read that ignores interruption and cancellation.
                }
            }
        }

        override fun read(): Int {
            pauseIfNeeded()
            val value = super.read()
            if (value >= 0) bytesRead.incrementAndGet()
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            pauseIfNeeded()
            val count = super.read(buffer, offset, length)
            if (count > 0) bytesRead.addAndGet(count.toLong())
            return count
        }
    }

    private fun gzip(): ByteArray {
        val bytes = ByteArray(352 + 100_000)
        Random(26).nextBytes(bytes)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0, 348)
        header.putShort(40, 3); header.putShort(42, 100); header.putShort(44, 100); header.putShort(46, 10)
        for (index in 4..7) header.putShort(40 + index * 2, 1)
        header.putShort(70, 2); header.putShort(72, 8); header.putFloat(108, 352f)
        for (index in 0..3) header.putFloat(76 + index * 4, 1f)
        header.putFloat(112, 0f); header.putShort(252, 0); header.putShort(254, 0)
        header.put(123, 2)
        bytes[344] = 110; bytes[345] = 43; bytes[346] = 49; bytes[347] = 0
        return ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
    }

    private fun withScenario(test: FaultInjectionProvider.Scenario, body: () -> Unit) {
        FaultInjectionProvider.scenario = test
        var failure: Throwable? = null
        try { body() } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            test.resume.countDown()
            FaultInjectionProvider.scenario = null
            if (!test.failOnOpen && !test.finished.await(10, TimeUnit.SECONDS)) {
                val cleanup = AssertionError("Provider writer released")
                if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
            }
        }
    }

    @Test fun progressingGzipSourceIsSlowWithoutBecomingStuck() {
        val clock = AtomicLong()
        val coordinator = OperationCoordinator(MonotonicClock(clock::get))
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        coordinator.reportProgress(id, OperationStage.DECODING_VOLUME,
            OperationProgress(0, 100_000, OperationUnit.VOXELS))
        val statuses = mutableSetOf<OperationStatus>()
        var lastBytes = 0L
        val test = FaultInjectionProvider.Scenario(gzip(), chunkDelayMs = if (longRun) 1_000 else 0)
        withScenario(test) {
            context.contentResolver.openAssetFileDescriptor(uri, "r", CancellationSignal())!!.use { asset ->
                NiftiReader.read(asset.createInputStream(), NiftiReadOptions(sourceProgressSink = { received ->
                    assertTrue(received > lastBytes)
                    lastBytes = received
                    clock.addAndGet(1_000)
                    coordinator.observeWork(id)
                    statuses += coordinator.classifyStall(id)
                })).close()
            }
            assertEquals(test.payload.size.toLong(), lastBytes)
            assertFalse(statuses.contains(OperationStatus.SUSPECTED_STALL))
            log("progressing-source: completed; bytes=$lastBytes; longRun=$longRun")
        }
    }

    @Test fun pausedProviderResumesAndClearsStallWithoutFakePercentage() {
        val clock = AtomicLong()
        val coordinator = OperationCoordinator(MonotonicClock(clock::get))
        val id = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.DECODING_VOLUME)
        val received = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val test = FaultInjectionProvider.Scenario(gzip(), pauseAt = 8_192)
        try {
            withScenario(test) {
                val reader = executor.submit {
                    context.contentResolver.openAssetFileDescriptor(uri, "r", CancellationSignal())!!.use { asset ->
                        NiftiReader.read(asset.createInputStream(), NiftiReadOptions(sourceProgressSink = {
                            coordinator.observeWork(id)
                            received.countDown()
                        })).close()
                    }
                }
                assertTrue(received.await(10, TimeUnit.SECONDS))
                assertTrue(test.paused.await(10, TimeUnit.SECONDS))
                if (longRun) Thread.sleep(61_000)
                clock.set(61_000)
                assertEquals(OperationStatus.SUSPECTED_STALL, coordinator.classifyStall(id))
                assertNull(coordinator.state(id)?.progress?.percent)
                test.resume.countDown()
                reader.get(20, TimeUnit.SECONDS)
                assertEquals(OperationStatus.RUNNING, coordinator.state(id)?.status)
                log("paused-source: suspected-stall -> resumed; longRun=$longRun")
            }
        } finally { executor.shutdownNow() }
    }

    @Test fun ignoredCancellationStaysPendingUntilReaderAndResourcesFinish() {
        val coordinator = OperationCoordinator()
        val old = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.OPENING_SOURCE)
        val signal = CancellationSignal()
        val canceled = AtomicBoolean()
        val executor = Executors.newSingleThreadExecutor()
        val test = FaultInjectionProvider.Scenario(gzip(), pauseAt = 8_192, ignoreCancellation = true)
        try {
            withScenario(test) {
                val reader = executor.submit {
                    try {
                        context.contentResolver.openAssetFileDescriptor(uri, "r", signal)!!.use { asset ->
                            NiftiReader.read(asset.createInputStream(), NiftiReadOptions(cancellationCheck = {
                                if (canceled.get()) throw CancellationException("Injected user cancellation")
                            })).close()
                        }
                        fail("Read should cancel")
                    } catch (_: CancellationException) { }
                }
                assertTrue(test.paused.await(10, TimeUnit.SECONDS))
                coordinator.requestCancel(old)
                signal.cancel()
                // Android detaches the resolver's cancellation transport after open.
                // Inject a provider-side request too, so ignored cancellation remains
                // reproducible even when a late client signal cannot reach the provider.
                val lateTransportRequests = test.cancellationRequests.get()
                test.cancelProvider()
                assertEquals(1, test.cancellationRequests.get())
                assertFalse(reader.isDone)
                assertEquals(OperationStatus.CANCELING, coordinator.state(old)?.status)
                canceled.set(true)
                val newer = coordinator.begin(OperationKind.VIEWER_LOAD, OperationStage.OPENING_SOURCE)
                assertFalse(coordinator.observeWork(old))
                test.resume.countDown()
                reader.get(10, TimeUnit.SECONDS)
                assertFalse(coordinator.confirmCanceled(old))
                assertEquals(OperationStatus.RUNNING, coordinator.state(newer)?.status)
                log("ignored-cancel: cleanup pending -> released; stale callback rejected; " +
                    "lateTransportRequests=$lateTransportRequests")
            }
        } finally { executor.shutdownNow() }
    }

    @Test fun replacementProviderFinishesWhilePriorReaderWaitsAndEnforcesSecondStallCap() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val registry = ViewerLoadWorkerRegistry()
        val signalA = CancellationSignal()
        val signalB = CancellationSignal()
        val blockedA = FaultInjectionProvider.Scenario(
            gzip(), pauseAt = 8_192, ignoreCancellation = true,
        )
        val readGateA = AtomicReference<IgnoringCancellationReadGate?>()
        val readGateAReady = CountDownLatch(1)
        val readGateC = AtomicReference<IgnoringCancellationReadGate?>()
        val readGateCReady = CountDownLatch(1)
        var blockedB: FaultInjectionProvider.Scenario? = null
        try {
            withScenario(blockedA) {
                val readerA = scope.async(start = CoroutineStart.LAZY) {
                    val workerContext = currentCoroutineContext()
                    context.contentResolver.openAssetFileDescriptor(uri, "r", signalA)!!.use { asset ->
                        val gate = IgnoringCancellationReadGate(asset.createInputStream(), 8_192)
                        readGateA.set(gate)
                        readGateAReady.countDown()
                        NiftiReader.read(
                            gate,
                            NiftiReadOptions(cancellationCheck = { workerContext.ensureActive() }),
                        ).close()
                    }
                }
                assertTrue(registry.registerCurrentReader(readerA))
                readerA.start()
                assertTrue(readGateAReady.await(10, TimeUnit.SECONDS))
                assertTrue(blockedA.paused.await(10, TimeUnit.SECONDS))
                assertTrue(requireNotNull(readGateA.get()).paused.await(10, TimeUnit.SECONDS))
                readerA.cancel()
                signalA.cancel()
                assertTrue(registry.detachCurrentProviderReader(readerA, insideProviderIo = true))
                assertTrue(registry.detachedReaderPending.value)

                val completedB = FaultInjectionProvider.Scenario(gzip())
                FaultInjectionProvider.scenario = completedB
                val readerB = scope.async(start = CoroutineStart.LAZY) {
                    val workerContext = currentCoroutineContext()
                    context.contentResolver.openAssetFileDescriptor(uri, "r", CancellationSignal())!!.use { asset ->
                        NiftiReader.read(
                            asset.createInputStream(),
                            NiftiReadOptions(cancellationCheck = { workerContext.ensureActive() }),
                        ).close()
                    }
                    true
                }
                assertTrue(registry.registerCurrentReader(readerB))
                readerB.start()
                assertTrue(runBlocking { withTimeout(10_000) { readerB.await() } })
                assertTrue(completedB.finished.await(10, TimeUnit.SECONDS))
                assertFalse(readerA.isCompleted)
                assertTrue(registry.detachedReaderPending.value)

                val secondStall = FaultInjectionProvider.Scenario(
                    gzip(), pauseAt = 8_192, ignoreCancellation = true,
                )
                blockedB = secondStall
                FaultInjectionProvider.scenario = secondStall
                val readerC = scope.async(start = CoroutineStart.LAZY) {
                    val workerContext = currentCoroutineContext()
                    context.contentResolver.openAssetFileDescriptor(uri, "r", signalB)!!.use { asset ->
                        val gate = IgnoringCancellationReadGate(asset.createInputStream(), 8_192)
                        readGateC.set(gate)
                        readGateCReady.countDown()
                        NiftiReader.read(
                            gate,
                            NiftiReadOptions(cancellationCheck = { workerContext.ensureActive() }),
                        ).close()
                    }
                }
                assertTrue(registry.registerCurrentReader(readerC))
                readerC.start()
                assertTrue(readGateCReady.await(10, TimeUnit.SECONDS))
                assertTrue(secondStall.paused.await(10, TimeUnit.SECONDS))
                assertTrue(requireNotNull(readGateC.get()).paused.await(10, TimeUnit.SECONDS))
                readerC.cancel()
                signalB.cancel()
                assertFalse(registry.detachCurrentProviderReader(readerC, insideProviderIo = true))
                assertFalse(registry.canStartCurrentReader())

                requireNotNull(readGateA.get()).resume.countDown()
                blockedA.resume.countDown()
                runBlocking { withTimeout(10_000) { readerA.join() } }
                assertFalse(registry.detachedReaderPending.value)
                assertFalse(registry.canStartCurrentReader()) // C still occupies the current slot.

                requireNotNull(readGateC.get()).resume.countDown()
                secondStall.resume.countDown()
                runBlocking { withTimeout(10_000) { readerC.join() } }
                assertTrue(secondStall.finished.await(10, TimeUnit.SECONDS))
                assertTrue(registry.canStartCurrentReader())
                log("replacement-read: B completed while A remained detached; a second stalled reader held the current slot")
            }
        } finally {
            readGateA.get()?.resume?.countDown()
            readGateC.get()?.resume?.countDown()
            blockedA.resume.countDown()
            blockedB?.resume?.countDown()
            signalA.cancel()
            signalB.cancel()
            scope.cancel()
        }
    }

    @Test fun advertisedProviderSizeCanBlockOnThePostPayloadIntegrityProbe() {
        val payload = gzip()
        val test = FaultInjectionProvider.Scenario(payload, pauseAfterPayload = true)
        val copied = AtomicLong(-1L)
        val copiedAllBytes = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            withScenario(test) {
                val advertised = context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.SIZE), null, null, null,
                )!!.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getLong(0)
                }
                assertEquals(payload.size.toLong(), advertised)
                val transfer = executor.submit<Long> {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        copyStreamWithProgress(
                            input,
                            ByteArrayOutputStream(),
                            advertised,
                            onProgress = { complete, _ ->
                                copied.set(complete)
                                if (complete == advertised) copiedAllBytes.countDown()
                            },
                        )
                    }
                }
                assertTrue(test.paused.await(10, TimeUnit.SECONDS))
                assertTrue(copiedAllBytes.await(10, TimeUnit.SECONDS))
                assertEquals(advertised, copied.get())
                assertThrows(TimeoutException::class.java) { transfer.get(250, TimeUnit.MILLISECONDS) }
                test.resume.countDown()
                assertEquals(advertised, transfer.get(10, TimeUnit.SECONDS))
                log("length-probe: Android fault provider withheld EOF after advertised bytes; probe resumed on close")
            }
        } finally {
            test.resume.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun eofPermissionAndBrokenPipeFailWithoutLeavingDiskBacking() {
        val payload = gzip()
        val directory = File(context.cacheDir, "provider-fault-test").apply { mkdirs() }
        val cases = listOf(
            FaultInjectionProvider.Scenario(payload.copyOf(20)),
            FaultInjectionProvider.Scenario(payload.copyOf(payload.size - 4)),
            FaultInjectionProvider.Scenario(payload.copyOf(8_192), failAfterPayload = true),
            FaultInjectionProvider.Scenario(payload, failOnOpen = true),
        )
        try {
            cases.forEach { test ->
                withScenario(test) {
                    assertThrows(Exception::class.java) {
                        context.contentResolver.openAssetFileDescriptor(uri, "r", CancellationSignal())!!.use { asset ->
                            NiftiReader.read(asset.createInputStream(), NiftiReadOptions(
                                maxVoxels = 2, backingDirectory = directory,
                            )).close()
                        }
                    }
                    assertEquals(0, directory.listFiles()!!.size)
                }
            }
            log("eof/truncated-gzip/broken-pipe/permission: failed; no partial backing")
        } finally { directory.deleteRecursively() }
    }
}
