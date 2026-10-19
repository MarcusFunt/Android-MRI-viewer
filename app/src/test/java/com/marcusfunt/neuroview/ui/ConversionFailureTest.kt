package com.marcusfunt.neuroview.ui

import com.marcusfunt.neuroview.ui.operations.OperationFailureKind
import java.io.EOFException
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ConversionFailureTest {
    @Test fun encodingRequiresExternalConversionWithoutEchoingUntrustedSyntax() {
        val failure = conversionFailure(IllegalArgumentException(
            "Unsupported or compressed transfer syntax: sensitive-provider-text",
        ))
        assertEquals(OperationFailureKind.UNSUPPORTED_DATA, failure.kind)
        assertTrue(failure.safeMessage.contains("on a computer"))
        assertFalse(failure.safeMessage.contains("sensitive-provider-text"))
        assertFalse(failure.retryable)
    }

    @Test fun mixedSeriesAndGeometryHaveDifferentRecoveryInstructions() {
        val mixed = conversionFailure(IllegalArgumentException("Selected DICOM files belong to different studies or series"))
        val geometry = conversionFailure(IllegalArgumentException("Inconsistent DICOM image orientation"))
        assertTrue(mixed.safeMessage.contains("more than one"))
        assertTrue(geometry.safeMessage.contains("incompatible geometry"))
        assertFalse(mixed.retryable)
        assertFalse(geometry.retryable)
    }

    @Test fun truncationIsInvalidDataAndProviderFailureCanBeRetried() {
        assertEquals(OperationFailureKind.INVALID_DATA, conversionFailure(EOFException()).kind)
        val provider = conversionFailure(IOException("private-document-name"))
        assertTrue(provider.retryable)
        assertFalse(provider.safeMessage.contains("private-document-name"))
        assertEquals(OperationFailureKind.SOURCE_UNAVAILABLE, conversionFailure(SecurityException()).kind)
    }
}
