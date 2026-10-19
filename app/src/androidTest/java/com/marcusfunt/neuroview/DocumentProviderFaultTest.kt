package com.marcusfunt.neuroview

import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.marcusfunt.neuroview.mri.FaultInjectionProvider
import com.marcusfunt.neuroview.mri.NiftiReadOptions
import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.ui.operations.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
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
        try { body() } finally {
            test.resume.countDown()
            if (!test.failOnOpen) assertTrue("Provider writer released", test.finished.await(10, TimeUnit.SECONDS))
            FaultInjectionProvider.scenario = null
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
                log("ignored-cancel: cleanup pending -> released; stale callback rejected")
            }
        } finally { executor.shutdownNow() }
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
