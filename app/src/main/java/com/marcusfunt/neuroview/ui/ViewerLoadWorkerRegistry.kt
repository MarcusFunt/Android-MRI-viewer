package com.marcusfunt.neuroview.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.marcusfunt.neuroview.ui.operations.OperationCoordinator

/** Process-lifetime owner so a stalled provider cannot disappear from accounting on rotation. */
internal object ViewerLoadWorkerOwner {
    val registry = ViewerLoadWorkerRegistry()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val operationCoordinator = OperationCoordinator()
}

/** Tracks one visible reader and, at most, one canceled reader still inside provider I/O. */
internal class ViewerLoadWorkerRegistry {
    private var currentReader: Job? = null
    private var detachedReader: Job? = null
    private val mutableDetachedReaderPending = MutableStateFlow(false)
    private val mutablePendingReaderCount = MutableStateFlow(0)

    val detachedReaderPending: StateFlow<Boolean> = mutableDetachedReaderPending.asStateFlow()
    val pendingReaderCount: StateFlow<Int> = mutablePendingReaderCount.asStateFlow()

    @Synchronized
    fun canStartCurrentReader(): Boolean {
        clearCompletedReaders()
        return currentReader == null
    }

    @Synchronized
    fun registerCurrentReader(job: Job): Boolean {
        clearCompletedReaders()
        if (currentReader != null || job.isCompleted) return false
        currentReader = job
        mutablePendingReaderCount.value += 1
        job.invokeOnCompletion { releaseCurrentReader(job) }
        return true
    }

    /** Detach only after the caller observes a blocked read inside a document provider. */
    @Synchronized
    fun detachCurrentProviderReader(job: Job, insideProviderIo: Boolean): Boolean {
        clearCompletedReaders()
        if (!insideProviderIo || currentReader !== job || job.isCompleted || detachedReader != null) {
            return false
        }
        currentReader = null
        detachedReader = job
        mutableDetachedReaderPending.value = true
        job.invokeOnCompletion { releaseDetachedReader(job) }
        return true
    }

    @Synchronized
    private fun releaseCurrentReader(job: Job) {
        if (currentReader === job) {
            currentReader = null
            mutablePendingReaderCount.value -= 1
        }
    }

    @Synchronized
    private fun releaseDetachedReader(job: Job) {
        if (detachedReader === job) {
            detachedReader = null
            mutableDetachedReaderPending.value = false
            mutablePendingReaderCount.value -= 1
        }
    }

    private fun clearCompletedReaders() {
        if (currentReader?.isCompleted == true) {
            currentReader = null
            mutablePendingReaderCount.value -= 1
        }
        if (detachedReader?.isCompleted == true) {
            detachedReader = null
            mutableDetachedReaderPending.value = false
            mutablePendingReaderCount.value -= 1
        }
    }
}
