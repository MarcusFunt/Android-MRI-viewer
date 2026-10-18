package com.marcusfunt.neuroview.processing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BrainSuiteTransferTest {
    @Test
    fun knownSizeTransferCopiesBytesAndReportsGroundedTotals() {
        val source = ByteArray(150_000) { (it % 251).toByte() }
        val destination = ByteArrayOutputStream()
        val progress = mutableListOf<Pair<Long, Long?>>()

        val copied = copyStreamWithProgress(
            ByteArrayInputStream(source), destination, source.size.toLong(),
            onProgress = { complete, total -> progress += complete to total },
        )

        assertEquals(source.size.toLong(), copied)
        assertArrayEquals(source, destination.toByteArray())
        assertEquals(
            listOf(65_536L to 150_000L, 131_072L to 150_000L, 150_000L to 150_000L),
            progress,
        )
    }

    @Test
    fun unknownSizeTransferReportsBytesWithoutInventingATotal() {
        val destination = ByteArrayOutputStream()
        val progress = mutableListOf<Pair<Long, Long?>>()

        copyStreamWithProgress(
            ByteArrayInputStream(ByteArray(70_000)), destination, null,
            onProgress = { complete, total -> progress += complete to total },
        )

        assertEquals(70_000, destination.size())
        assertEquals(2, progress.size)
        assertEquals(65_536L, progress[0].first)
        assertNull(progress[0].second)
        assertEquals(70_000L, progress[1].first)
        assertNull(progress[1].second)
    }

    @Test
    fun transferCancellationStopsBeforeReadingTheNextChunk() {
        val destination = ByteArrayOutputStream()
        var checks = 0

        assertThrows(CancellationException::class.java) {
            copyStreamWithProgress(
                ByteArrayInputStream(ByteArray(150_000)), destination, 150_000,
                cancellationCheck = {
                    checks++
                    if (checks == 2) throw CancellationException("transfer canceled")
                },
            )
        }

        assertEquals(65_536, destination.size())
    }

    @Test
    fun remoteFailureMessagesDistinguishTokenNetworkAndBusyServer() {
        assertEquals(
            "The access token was rejected. Check it and try again.",
            brainSuiteFailureMessage(BrainSuiteServiceException(401)),
        )
        assertEquals(
            "The BrainSuite server is busy. Check status again shortly.",
            brainSuiteFailureMessage(BrainSuiteServiceException(429)),
        )
        assertEquals(
            "The BrainSuite server is busy. Check status again shortly.",
            brainSuiteFailureMessage(BrainSuiteServiceException(503)),
        )
        assertEquals(
            "The BrainSuite server returned an error. Check status again later.",
            brainSuiteFailureMessage(BrainSuiteServiceException(500)),
        )
        assertEquals(
            "Could not reach the BrainSuite server. Check the connection and try again.",
            brainSuiteFailureMessage(IOException("network unreachable")),
        )
    }
}
