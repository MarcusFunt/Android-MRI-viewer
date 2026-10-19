package com.marcusfunt.neuroview.mri

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Debug APK only. Each test owns and releases its pipe writer, including ignored cancellation. */
class FaultInjectionProvider : ContentProvider() {
    class Scenario(
        val payload: ByteArray,
        val chunkBytes: Int = 512,
        val chunkDelayMs: Long = 0,
        val pauseAt: Int? = null,
        val ignoreCancellation: Boolean = false,
        val failOnOpen: Boolean = false,
        val failAfterPayload: Boolean = false,
    ) {
        val paused = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val cancellationRequests = AtomicInteger()
        internal val providerCancellation = AtomicReference<CancellationSignal?>()

        /** Explicit injection after open: the resolver only forwards cancellation during the open call. */
        fun cancelProvider() = requireNotNull(providerCancellation.get()).cancel()
    }

    // Read-only resolver opens use the typed path. Its platform default calls
    // the legacy two-argument openAssetFile, bypassing the signal overload.
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor =
        openAssetFile(uri, mode, null)

    override fun openTypedAssetFile(
        uri: Uri, mimeTypeFilter: String, opts: Bundle?, signal: CancellationSignal?,
    ): AssetFileDescriptor = openAssetFile(uri, "r", signal)

    override fun openAssetFile(uri: Uri, mode: String, signal: CancellationSignal?): AssetFileDescriptor {
        val test = requireNotNull(scenario) { "Install a test scenario first" }
        if (test.failOnOpen) throw SecurityException("Injected permission failure")
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val providerSignal = signal ?: CancellationSignal()
        test.providerCancellation.set(providerSignal)
        providerSignal.setOnCancelListener {
            test.cancellationRequests.incrementAndGet()
            if (!test.ignoreCancellation) {
                test.resume.countDown()
                runCatching { pipe[1].closeWithError("Canceled") }
            }
        }
        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    var offset = 0
                    while (offset < test.payload.size) {
                        if (test.pauseAt != null && offset >= test.pauseAt) {
                            test.paused.countDown()
                            check(test.resume.await(10, TimeUnit.MINUTES)) { "Test did not release provider" }
                        }
                        if (!test.ignoreCancellation) providerSignal.throwIfCanceled()
                        val count = minOf(test.chunkBytes, test.payload.size - offset)
                        output.write(test.payload, offset, count)
                        offset += count
                        if (test.chunkDelayMs > 0) Thread.sleep(test.chunkDelayMs)
                    }
                    if (test.failAfterPayload) pipe[1].closeWithError("Injected read failure")
                }
            } catch (_: Exception) {
                runCatching { pipe[1].closeWithError("Injected provider closed") }
            } finally {
                test.finished.countDown()
            }
        }, "fault-provider-writer").apply { isDaemon = true }.start()
        return AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/gzip"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        @Volatile var scenario: Scenario? = null
    }
}
