package com.marcusfunt.neuroview.ui

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.OpenableColumns
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.marcusfunt.neuroview.catalog.ScanCatalog
import com.marcusfunt.neuroview.catalog.ScanCatalogSnapshot
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.catalog.ImportBatchProgress
import com.marcusfunt.neuroview.mri.MriMemoryBudget
import com.marcusfunt.neuroview.mri.NiftiReadOptions
import com.marcusfunt.neuroview.mri.NiftiReadProgress
import com.marcusfunt.neuroview.mri.NiftiReadStage
import com.marcusfunt.neuroview.mri.NiftiReadUnit
import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.navigation.Destination
import com.marcusfunt.neuroview.navigation.NavigationActions
import com.marcusfunt.neuroview.ui.screens.HomeScreen
import com.marcusfunt.neuroview.ui.screens.LibraryScreen
import com.marcusfunt.neuroview.ui.screens.CatalogQuantity
import com.marcusfunt.neuroview.ui.screens.importResultSummary
import com.marcusfunt.neuroview.ui.screens.importCanceledSummary
import com.marcusfunt.neuroview.ui.screens.catalogQuantityText
import com.marcusfunt.neuroview.ui.screens.SeriesScreen
import com.marcusfunt.neuroview.ui.screens.ViewerScreen
import com.marcusfunt.neuroview.ui.operations.OperationFailure
import com.marcusfunt.neuroview.ui.operations.OperationFailureKind
import com.marcusfunt.neuroview.ui.operations.OperationId
import com.marcusfunt.neuroview.ui.operations.OperationKind
import com.marcusfunt.neuroview.ui.operations.OperationProgress
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStage
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.OperationUnit
import com.marcusfunt.neuroview.ui.operations.RecoveryAction
import com.marcusfunt.neuroview.ui.operations.SystemMonotonicClock
import com.marcusfunt.neuroview.ui.theme.LocalNeuroViewMotion
import com.marcusfunt.neuroview.ui.theme.MotionToken
import com.marcusfunt.neuroview.ui.theme.rememberMotionPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FilterInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.EOFException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface VolumeLoadState {
    data object Empty : VolumeLoadState
    data class Loading(
        val operation: OperationSnapshot,
        val displayName: String? = null,
        val decodedVolume: NiftiVolume? = null,
        val cancelRequestedAtElapsedMs: Long? = null,
        val recoveryMessage: String? = null,
    ) : VolumeLoadState
    data class FirstSliceReady(
        val operation: OperationSnapshot,
        val displayName: String,
        val volume: NiftiVolume,
    ) : VolumeLoadState
    data class Ready(val displayName: String, val volume: NiftiVolume) : VolumeLoadState
    data class Failed(val operation: OperationSnapshot, val explanation: String) : VolumeLoadState
    data class Canceled(val operation: OperationSnapshot) : VolumeLoadState
}

private class ViewerLoadSession(
    val operationId: OperationId,
    val firstSliceReady: CompletableDeferred<Unit>,
    val job: Job,
    val cancellationSignal: AtomicReference<CancellationSignal>,
    val providerIoActive: AtomicBoolean,
    val workerJob: AtomicReference<Job?> = AtomicReference(null),
    val renderJob: AtomicReference<Job?> = AtomicReference(null),
) {
    @Volatile
    var userRequestedCancel: Boolean = false

    fun cancelReader() {
        userRequestedCancel = true
        cancellationSignal.get().cancel()
        workerJob.get()?.cancel(CancellationException("MRI load canceled by user"))
        renderJob.get()?.cancel(CancellationException("First-slice render canceled by user"))
        firstSliceReady.cancel(CancellationException("MRI load canceled by user"))
    }

    suspend fun stopAndJoinOwnedWork(registry: ViewerLoadWorkerRegistry): ViewerWorkerCleanup =
        withContext(NonCancellable) {
            val worker = workerJob.get()
            worker?.cancel(CancellationException("Viewer load ended"))
            val workerFinished = worker == null || withTimeoutOrNull(VIEWER_READER_CANCEL_GRACE_MS) {
                worker.join()
                true
            } == true
            renderJob.get()?.cancelAndJoin()
            if (workerFinished) {
                ViewerWorkerCleanup.FINISHED
            } else if (registry.detachCurrentProviderReader(
                    worker!!,
                    insideProviderIo = providerIoActive.get(),
                )
            ) {
                ViewerWorkerCleanup.DETACHED_PROVIDER_READ
            } else {
                ViewerWorkerCleanup.CAPACITY_BLOCKED
            }
        }

    private companion object {
        const val VIEWER_READER_CANCEL_GRACE_MS = 1_500L
    }
}

private const val VIEWER_READER_CAPACITY_RECOVERY =
    "Another file provider is still pending. Return to the Library, retry after it returns, or restart NeuroView if it never does."

private enum class ViewerWorkerCleanup { FINISHED, DETACHED_PROVIDER_READ, CAPACITY_BLOCKED }

private inline fun <T> duringProviderIo(active: AtomicBoolean, block: () -> T): T {
    active.set(true)
    return try { block() } finally { active.set(false) }
}

private class ProviderIoTrackingInputStream(
    input: InputStream,
    private val active: AtomicBoolean,
) : FilterInputStream(input) {
    override fun read(): Int = duringProviderIo(active) { super.read() }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        duringProviderIo(active) { super.read(bytes, offset, length) }
}

private data class LoadedViewerVolume(val displayName: String, val volume: NiftiVolume)

private fun NiftiReadStage.toOperationStage(): OperationStage = when (this) {
    NiftiReadStage.READING_HEADER -> OperationStage.READING_HEADER
    NiftiReadStage.VALIDATING_VOLUME -> OperationStage.VALIDATING_VOLUME
    NiftiReadStage.SKIPPING_EXTENSIONS -> OperationStage.SKIPPING_EXTENSIONS
    NiftiReadStage.DECODING_VOLUME -> OperationStage.DECODING_VOLUME
    NiftiReadStage.PREPARING_DISK_BACKING -> OperationStage.PREPARING_DISK_BACKING
    NiftiReadStage.SAMPLING_INTENSITY -> OperationStage.SAMPLING_INTENSITY
}

private fun NiftiReadProgress.toOperationProgress(): OperationProgress? {
    if (completedUnits == null && totalUnits == null) return null
    val operationUnit = when (unit) {
        NiftiReadUnit.BYTES -> OperationUnit.BYTES
        NiftiReadUnit.VOXELS -> OperationUnit.VOXELS
        NiftiReadUnit.SAMPLES -> OperationUnit.ITEMS
        null -> return null
    }
    return OperationProgress(completedUnits, totalUnits, operationUnit)
}

private fun Throwable.toOperationFailure(): OperationFailure {
    val detail = message.orEmpty()
    return when {
        this is OutOfMemoryError -> OperationFailure(
            kind = OperationFailureKind.OUT_OF_MEMORY,
            safeMessage = "This device is low on memory. Try a smaller MRI volume, then try again.",
            retryable = true,
            actions = setOf(RecoveryAction.RETRY, RecoveryAction.CLEAR_DERIVED_CACHE),
        )
        detail.contains("Insufficient storage", ignoreCase = true) -> OperationFailure(
            kind = OperationFailureKind.INSUFFICIENT_STORAGE,
            safeMessage = "There is not enough free storage to prepare this MRI on this device.",
            retryable = true,
            actions = setOf(RecoveryAction.CLEAR_DERIVED_CACHE, RecoveryAction.RETRY),
        )
        this is FileNotFoundException || this is SecurityException -> OperationFailure(
            kind = OperationFailureKind.SOURCE_UNAVAILABLE,
            safeMessage = "This MRI is no longer accessible. Select its file again.",
            retryable = true,
            actions = setOf(RecoveryAction.RECONNECT_SOURCE, RecoveryAction.RETRY),
        )
        detail.contains("Unsupported NIfTI datatype", ignoreCase = true) ||
            detail.contains("NIfTI-2", ignoreCase = true) ||
            detail.contains("Analyze", ignoreCase = true) ||
            detail.contains("not supported yet", ignoreCase = true) -> OperationFailure(
            kind = OperationFailureKind.UNSUPPORTED_DATA,
            safeMessage = "This NIfTI format is not supported yet. Choose another MRI file.",
            retryable = false,
            actions = setOf(RecoveryAction.RECONNECT_SOURCE),
        )
        this is EOFException || this is IllegalArgumentException -> OperationFailure(
            kind = OperationFailureKind.INVALID_DATA,
            safeMessage = "The MRI header or voxel data is incomplete or invalid.",
            retryable = false,
            actions = setOf(RecoveryAction.RECONNECT_SOURCE),
        )
        this is IOException || this is OperationCanceledException -> OperationFailure(
            kind = OperationFailureKind.PROVIDER_ERROR,
            safeMessage = "The selected file provider could not supply the MRI data. Check access and try again.",
            retryable = true,
            actions = setOf(RecoveryAction.RECONNECT_SOURCE, RecoveryAction.RETRY),
        )
        else -> OperationFailure(
            kind = OperationFailureKind.INTERNAL_ERROR,
            safeMessage = "NeuroView could not open this MRI. Try selecting the file again.",
            retryable = true,
            actions = setOf(RecoveryAction.RETRY, RecoveryAction.RECONNECT_SOURCE),
        )
    }
}

/** Phase 4 separates a persisted read-only series catalog from one active in-memory volume. */
@Composable
fun NeuroViewApp() {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val motionPreference = rememberMotionPreference()
    val navigationMotionMs = MotionToken.NAVIGATION.durationMs(motionPreference)
    val scope = rememberCoroutineScope()
    val conversions = rememberConversionControls()
    val catalog = remember(context.applicationContext) {
        NiftiReader.cleanStaleBackings(File(context.cacheDir, "nifti_mapped"))
        ScanCatalog(context.applicationContext)
    }
    var snapshot by remember { mutableStateOf(catalog.load()) }
    var importBusy by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf<ImportBatchProgress?>(null) }
    var importOperation by remember { mutableStateOf<OperationSnapshot?>(null) }
    var importCancellationSignal by remember { mutableStateOf<CancellationSignal?>(null) }
    var importCancelRequestedAt by remember { mutableStateOf<Long?>(null) }
    var editBusy by remember { mutableStateOf(false) }
    var libraryMessage by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedScanId by rememberSaveable { mutableStateOf<String?>(null) }
    // The last-opened MRI ID is a tiny local preference, not a copy of scan data.
    val navigationPrefs = remember(context.applicationContext) {
        context.getSharedPreferences("neuroview_navigation_v1", Context.MODE_PRIVATE)
    }
    var lastOpenedScanId by rememberSaveable {
        mutableStateOf(navigationPrefs.getString("last_opened_id", null))
    }

    fun updateCatalog(description: String, action: suspend () -> ScanCatalogSnapshot) {
        if (importBusy || editBusy || conversions.busy) return
        editBusy = true
        libraryMessage = null
        scope.launch {
            try {
                snapshot = action()
                libraryMessage = description
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                libraryMessage = "Library operation failed: ${error.message ?: "Unknown error"}"
            } finally {
                editBusy = false
            }
        }
    }


    val backStack = rememberNavBackStack(Destination.Home)
    val navigate: (Destination) -> Unit = { NavigationActions.navigate(backStack, it) }

    val operationCoordinator = ViewerLoadWorkerOwner.operationCoordinator
    val viewerLoadWorkers = ViewerLoadWorkerOwner.registry
    val pendingDetachedProviderCleanup by viewerLoadWorkers.detachedReaderPending.collectAsState()
    val pendingViewerReaderCount by viewerLoadWorkers.pendingReaderCount.collectAsState()
    val viewerRenderJobs = remember { ConcurrentHashMap<Job, NiftiVolume>() }
    var volumeState by remember { mutableStateOf<VolumeLoadState>(VolumeLoadState.Empty) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var activeLoadSession by remember { mutableStateOf<ViewerLoadSession?>(null) }
    val viewerVisible = backStack.lastOrNull() is Destination.Viewer

    DisposableEffect(catalog, viewerVisible) {
        catalog.setViewerPriority(viewerVisible)
        onDispose {
            if (viewerVisible) catalog.setViewerPriority(false)
        }
    }

    LaunchedEffect(importBusy, importOperation?.id) {
        val operationId = importOperation?.id ?: return@LaunchedEffect
        while (isActive) {
            delay(250L)
            val current = operationCoordinator.state(operationId) ?: break
            if (current.status.isTerminal) break
            operationCoordinator.classifyStall(
                operationId,
                waitingForProvider = current.status == OperationStatus.WAITING_FOR_PROVIDER,
            )
            importOperation = operationCoordinator.state(operationId)
        }
    }

    LaunchedEffect(pendingViewerReaderCount, activeLoadSession?.operationId) {
        val session = activeLoadSession ?: return@LaunchedEffect
        if (!session.job.isCompleted || session.workerJob.get()?.isCompleted == false ||
            session.renderJob.get()?.isCompleted == false
        ) return@LaunchedEffect
        val current = operationCoordinator.state(session.operationId)
        if (operationCoordinator.isCurrent(session.operationId) &&
            current?.status == OperationStatus.CANCELING &&
            operationCoordinator.confirmCanceled(session.operationId)
        ) {
            operationCoordinator.state(session.operationId)?.let { canceled ->
                volumeState = VolumeLoadState.Canceled(canceled)
            }
            if (activeLoadSession == session) activeLoadSession = null
        }
    }

    LaunchedEffect(selectedUri, viewerVisible, loadAttempt) {
        val source = selectedUri
        val attemptAtStart = loadAttempt
        if (source == null || !viewerVisible) {
            volumeState = VolumeLoadState.Empty
            return@LaunchedEffect
        }

        if (!viewerLoadWorkers.canStartCurrentReader()) {
            val pendingSession = activeLoadSession
            val pendingOperation = pendingSession?.let { operationCoordinator.state(it.operationId) }
                ?: operationCoordinator.latest(OperationKind.VIEWER_LOAD)
            if (pendingOperation != null) {
                val existingLoading = (volumeState as? VolumeLoadState.Loading)
                    ?.takeIf { it.operation.id == pendingOperation.id }
                volumeState = existingLoading?.copy(
                    operation = pendingOperation,
                    recoveryMessage = VIEWER_READER_CAPACITY_RECOVERY,
                ) ?: VolumeLoadState.Loading(
                    operation = pendingOperation,
                    cancelRequestedAtElapsedMs = if (pendingOperation.status == OperationStatus.CANCELING) {
                        SystemMonotonicClock.nowMs()
                    } else null,
                    recoveryMessage = VIEWER_READER_CAPACITY_RECOVERY,
                )
            }
            return@LaunchedEffect
        }
        if (source != selectedUri || backStack.lastOrNull() !is Destination.Viewer) {
            return@LaunchedEffect
        }

        val operationId = operationCoordinator.begin(
            OperationKind.VIEWER_LOAD,
            OperationStage.OPENING_SOURCE,
        )
        val signal = CancellationSignal()
        val session = ViewerLoadSession(
            operationId = operationId,
            firstSliceReady = CompletableDeferred(),
            job = currentCoroutineContext()[Job] ?: error("Viewer load needs a coroutine job"),
            cancellationSignal = AtomicReference(signal),
            providerIoActive = AtomicBoolean(false),
        )
        activeLoadSession = session
        var volumeTransferredToUi = false
        val decodedVolume = AtomicReference<NiftiVolume?>(null)
        volumeState = VolumeLoadState.Loading(
            requireNotNull(operationCoordinator.state(operationId)),
        )

        val progressCollector = launch {
            operationCoordinator.states.collect { operations ->
                val current = volumeState as? VolumeLoadState.Loading ?: return@collect
                val updated = operations.firstOrNull { it.id == operationId } ?: return@collect
                if (operationCoordinator.isCurrent(operationId)) {
                    volumeState = current.copy(operation = updated)
                }
            }
        }
        val watchdog = launch {
            while (isActive) {
                delay(250)
                if (operationCoordinator.isCurrent(operationId)) {
                    val current = operationCoordinator.state(operationId)
                    operationCoordinator.classifyStall(
                        operationId,
                        waitingForProvider = current?.status == OperationStatus.WAITING_FOR_PROVIDER,
                    )
                }
            }
        }

        try {
            operationCoordinator.transition(operationId, OperationStatus.WAITING_FOR_PROVIDER)
            val worker = ViewerLoadWorkerOwner.scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val workerContext = currentCoroutineContext()
                val cancellationBridge = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        signal.cancel()
                    }
                }
                try {
                    val uri = Uri.parse(source)
                    val displayName = runCatching {
                        duringProviderIo(session.providerIoActive) {
                            applicationContext.contentResolver.query(
                                uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME),
                                null,
                                null,
                                null,
                                signal,
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                        }
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Selected MRI"
                    workerContext.ensureActive()
                    val descriptor = duringProviderIo(session.providerIoActive) {
                        applicationContext.contentResolver.openAssetFileDescriptor(uri, "r", signal)
                    } ?: throw FileNotFoundException("Selected MRI is unavailable")
                    val volume = descriptor.use { asset ->
                        val sourceStream = duringProviderIo(session.providerIoActive) {
                            asset.createInputStream()
                        }
                        ProviderIoTrackingInputStream(sourceStream, session.providerIoActive).use { input ->
                            NiftiReader.read(
                                input,
                                NiftiReadOptions(
                                    maxVoxels = MriMemoryBudget.maxVoxels(Runtime.getRuntime().maxMemory()),
                                    backingDirectory = File(applicationContext.cacheDir, "nifti_mapped"),
                                    cancellationCheck = { workerContext.ensureActive() },
                                    progressSink = { progress ->
                                        operationCoordinator.reportProgress(
                                            operationId,
                                            progress.stage.toOperationStage(),
                                            progress.toOperationProgress(),
                                        )
                                    },
                                    sourceProgressSink = { operationCoordinator.observeWork(operationId) },
                                ),
                            )
                        }
                    }
                    try {
                        workerContext.ensureActive()
                        decodedVolume.set(volume)
                        operationCoordinator.reportProgress(
                            operationId,
                            OperationStage.RENDERING_FIRST_SLICE,
                            null,
                        )
                        workerContext.ensureActive()
                        LoadedViewerVolume(displayName, volume)
                    } catch (error: Throwable) {
                        decodedVolume.compareAndSet(volume, null)
                        volume.close()
                        throw error
                    }
                } finally {
                    cancellationBridge.cancel()
                }
            }
            if (!viewerLoadWorkers.registerCurrentReader(worker)) {
                worker.cancel()
                throw IllegalStateException(
                    "A file provider is still pending. Return to the Library and retry after it responds.",
                )
            }
            session.workerJob.set(worker)
            worker.invokeOnCompletion { failure ->
                if (failure != null) {
                    decodedVolume.getAndSet(null)?.close()
                    operationCoordinator.confirmStaleCanceledAfterCleanup(operationId)
                }
            }
            worker.start()
            val loaded = worker.await()
            if (!operationCoordinator.isCurrent(operationId)) {
                throw CancellationException("A newer viewer load replaced this request")
            }
            val rendering = operationCoordinator.state(operationId)
                ?: throw CancellationException("Viewer load state was cleared")
            volumeState = VolumeLoadState.Loading(rendering, loaded.displayName, loaded.volume)
            volumeTransferredToUi = true

            session.firstSliceReady.await()
            val completed = operationCoordinator.state(operationId)
            if (operationCoordinator.isCurrent(operationId) &&
                completed?.status == OperationStatus.SUCCEEDED
            ) {
                volumeState = VolumeLoadState.Ready(loaded.displayName, loaded.volume)
            }
        } catch (canceled: CancellationException) {
            if (!session.job.isActive) throw canceled
            if (!session.userRequestedCancel) {
                val failure = OperationFailure(
                    kind = OperationFailureKind.PROVIDER_ERROR,
                    safeMessage = "The selected file provider stopped responding. Try opening the file again.",
                    retryable = true,
                    actions = setOf(RecoveryAction.RETRY, RecoveryAction.RECONNECT_SOURCE),
                )
                if (operationCoordinator.isCurrent(operationId)) {
                    operationCoordinator.fail(operationId, failure)
                    volumeState = VolumeLoadState.Failed(
                        requireNotNull(operationCoordinator.state(operationId)),
                        failure.safeMessage,
                    )
                }
            }
        } catch (canceled: OperationCanceledException) {
            if (!session.job.isActive) throw CancellationException("Viewer load canceled", canceled)
            if (!session.userRequestedCancel) {
                val failure = canceled.toOperationFailure()
                if (operationCoordinator.isCurrent(operationId)) {
                    operationCoordinator.fail(operationId, failure)
                    volumeState = VolumeLoadState.Failed(
                        requireNotNull(operationCoordinator.state(operationId)),
                        failure.safeMessage,
                    )
                }
            }
        } catch (error: OutOfMemoryError) {
            val failure = error.toOperationFailure()
            if (operationCoordinator.isCurrent(operationId)) {
                operationCoordinator.fail(operationId, failure)
                volumeState = VolumeLoadState.Failed(
                    requireNotNull(operationCoordinator.state(operationId)),
                    failure.safeMessage,
                )
            }
        } catch (error: Exception) {
            val failure = error.toOperationFailure()
            if (operationCoordinator.isCurrent(operationId)) {
                operationCoordinator.fail(operationId, failure)
                volumeState = VolumeLoadState.Failed(
                    requireNotNull(operationCoordinator.state(operationId)),
                    failure.safeMessage,
                )
            }
        } finally {
            signal.cancel()
            progressCollector.cancel()
            watchdog.cancel()
            val succeeded = operationCoordinator.state(operationId)?.status == OperationStatus.SUCCEEDED
            if (!succeeded && operationCoordinator.isCurrent(operationId)) {
                val operation = operationCoordinator.state(operationId)
                if (operation != null && !operation.status.isTerminal) {
                    if (operation.status != OperationStatus.CANCELING) {
                        operationCoordinator.requestCancel(operationId)
                    }
                    val stopping = operationCoordinator.state(operationId)
                    val currentLoad = volumeState as? VolumeLoadState.Loading
                    if (stopping != null && currentLoad?.operation?.id == operationId) {
                        volumeState = currentLoad.copy(
                            operation = stopping,
                            cancelRequestedAtElapsedMs = currentLoad.cancelRequestedAtElapsedMs
                                ?: SystemMonotonicClock.nowMs(),
                        )
                    }
                }
            }
            val cleanup = if (!succeeded) {
                session.stopAndJoinOwnedWork(viewerLoadWorkers)
            } else ViewerWorkerCleanup.FINISHED
            if (!volumeTransferredToUi) decodedVolume.getAndSet(null)?.close()
            val currentOperation = operationCoordinator.state(operationId)
            if (operationCoordinator.isCurrent(operationId) &&
                currentOperation?.status == OperationStatus.CANCELING
            ) {
                if (session.workerJob.get()?.isCompleted != false &&
                    session.renderJob.get()?.isCompleted != false
                ) {
                    if (operationCoordinator.confirmCanceled(operationId)) {
                        operationCoordinator.state(operationId)?.let { canceled ->
                            volumeState = VolumeLoadState.Canceled(canceled)
                        }
                    }
                } else if (cleanup == ViewerWorkerCleanup.CAPACITY_BLOCKED) {
                    val stopping = operationCoordinator.state(operationId)
                    val currentLoad = volumeState as? VolumeLoadState.Loading
                    if (stopping != null && currentLoad?.operation?.id == operationId) {
                        volumeState = currentLoad.copy(
                            operation = stopping,
                            recoveryMessage = VIEWER_READER_CAPACITY_RECOVERY,
                        )
                    }
                }
            }
            val currentLoad = volumeState as? VolumeLoadState.Loading
            if (currentLoad?.operation?.id == operationId &&
                !session.userRequestedCancel &&
                (selectedUri != source || backStack.lastOrNull() !is Destination.Viewer || loadAttempt != attemptAtStart)
            ) {
                volumeState = VolumeLoadState.Empty
            }
            if (activeLoadSession == session &&
                (succeeded || (session.workerJob.get()?.isCompleted != false &&
                    session.renderJob.get()?.isCompleted != false))
            ) activeLoadSession = null
        }
    }

    // A volume remains owned by this screen state across progress updates and is closed on replacement.
    val activeVolume = when (val state = volumeState) {
        is VolumeLoadState.Loading -> state.decodedVolume
        is VolumeLoadState.FirstSliceReady -> state.volume
        is VolumeLoadState.Ready -> state.volume
        else -> null
    }
    DisposableEffect(activeVolume) {
        onDispose {
            val jobsToStop = viewerRenderJobs.entries
                .filter { it.value === activeVolume }
                .mapNotNull { entry ->
                    if (viewerRenderJobs.remove(entry.key, activeVolume)) entry.key else null
                }
            if (jobsToStop.isEmpty()) {
                activeVolume?.close()
            } else {
                jobsToStop.forEach { it.cancel(CancellationException("Viewer volume was released")) }
                scope.launch {
                    jobsToStop.forEach { it.join() }
                    activeVolume?.close()
                }
            }
        }
    }

    fun cancelViewerLoad() {
        val session = activeLoadSession ?: return
        val loading = volumeState as? VolumeLoadState.Loading ?: return
        if (loading.operation.id != session.operationId) return
        if (operationCoordinator.requestCancel(session.operationId)) {
            volumeState = loading.copy(
                operation = requireNotNull(operationCoordinator.state(session.operationId)),
                cancelRequestedAtElapsedMs = SystemMonotonicClock.nowMs(),
            )
            session.cancelReader()
        }
    }

    fun retryViewerLoad() {
        loadAttempt += 1
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { documents ->
        if (documents.isNotEmpty() && !importBusy) {
            importBusy = true
            importMessage = null
            val operationId = operationCoordinator.begin(
                OperationKind.IMPORT_BATCH,
                OperationStage.IMPORTING_FILE,
            )
            val totalFiles = documents.distinctBy { it.toString() }.take(64).size
            val signal = CancellationSignal()
            val initialProgress = ImportBatchProgress(
                filesProcessed = 0,
                totalFiles = totalFiles,
                currentFileName = null,
                added = 0,
                skipped = 0,
                failed = 0,
            )
            if (totalFiles > 0) {
                operationCoordinator.reportProgress(
                    operationId,
                    OperationStage.IMPORTING_FILE,
                    OperationProgress(0, totalFiles.toLong(), OperationUnit.FILES),
                )
            }
            importProgress = initialProgress
            importOperation = requireNotNull(operationCoordinator.state(operationId))
            importCancellationSignal = signal
            importCancelRequestedAt = null
            if (backStack.lastOrNull() !is Destination.Library) navigate(Destination.Library)
            scope.launch {
                val cancellationBridge = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        signal.cancel()
                    }
                }
                try {
                    val result = catalog.importDocuments(
                        documents = documents,
                        cancellationSignal = signal,
                        onProgress = { progress ->
                            withContext(Dispatchers.Main) {
                                importProgress = progress
                                if (progress.waitingForProvider) {
                                    val state = operationCoordinator.state(operationId)
                                    if (state != null && !state.status.isTerminal &&
                                        state.status != OperationStatus.WAITING_FOR_PROVIDER
                                    ) {
                                        operationCoordinator.transition(
                                            operationId,
                                            OperationStatus.WAITING_FOR_PROVIDER,
                                        )
                                    }
                                } else if (progress.totalFiles > 0) {
                                    operationCoordinator.reportProgress(
                                        operationId,
                                        OperationStage.IMPORTING_FILE,
                                        OperationProgress(
                                            progress.filesProcessed.toLong(),
                                            progress.totalFiles.toLong(),
                                            OperationUnit.FILES,
                                        ),
                                    )
                                }
                                importOperation = operationCoordinator.state(operationId)
                            }
                        },
                        onCatalogChanged = { updated ->
                            withContext(Dispatchers.Main) { snapshot = updated }
                        },
                    )
                    snapshot = catalog.load()
                    if (result.canceled || signal.isCanceled) {
                        val state = operationCoordinator.state(operationId)
                        if (state?.status != OperationStatus.CANCELING) {
                            operationCoordinator.requestCancel(operationId)
                        }
                        operationCoordinator.confirmCanceled(operationId)
                        importMessage = importCanceledSummary(result, importProgress?.filesProcessed ?: 0,
                            totalFiles, context.resources::catalogQuantityText)
                    } else {
                        operationCoordinator.succeed(operationId)
                        importMessage = importResultSummary(result, context.resources::catalogQuantityText)
                    }
                } catch (canceled: CancellationException) {
                    if (operationCoordinator.isCurrent(operationId)) {
                        val state = operationCoordinator.state(operationId)
                        if (state != null && !state.status.isTerminal) {
                            if (state.status != OperationStatus.CANCELING) {
                                operationCoordinator.requestCancel(operationId)
                            }
                            operationCoordinator.confirmCanceled(operationId)
                        }
                    }
                    throw canceled
                } catch (exception: Exception) {
                    if (operationCoordinator.isCurrent(operationId)) {
                        val failure = OperationFailure(
                            kind = OperationFailureKind.PROVIDER_ERROR,
                            safeMessage = "NeuroView could not finish checking the selected files. Check access and try again.",
                            retryable = true,
                            actions = setOf(RecoveryAction.RETRY, RecoveryAction.RECONNECT_SOURCE),
                        )
                        operationCoordinator.fail(operationId, failure)
                    }
                    importMessage = "Import failed. Check access to the selected files and try again."
                } finally {
                    cancellationBridge.cancel()
                    importBusy = false
                    importProgress = null
                    importOperation = null
                    importCancellationSignal = null
                    importCancelRequestedAt = null
                }
            }
        }
    }
    val chooseDocuments: () -> Unit = {
        if (!importBusy && !editBusy && !conversions.busy) picker.launch(arrayOf("*/*"))
    }

    fun cancelImport() {
        val operation = importOperation ?: return
        val progress = importProgress ?: return
        if (progress.currentFileName == null && progress.filesProcessed >= progress.totalFiles) return
        if (operation.status != OperationStatus.CANCELING &&
            operationCoordinator.requestCancel(operation.id)
        ) {
            importCancelRequestedAt = SystemMonotonicClock.nowMs()
            importOperation = operationCoordinator.state(operation.id)
            importCancellationSignal?.cancel()
        }
    }

    val openScan: (ScanRecord) -> Unit = { scan ->
        selectedUri = scan.documentUri
        selectedScanId = scan.id
        lastOpenedScanId = scan.id
        navigationPrefs.edit().putString("last_opened_id", scan.id).apply()
        navigate(Destination.Viewer(scan.id))
    }

    CompositionLocalProvider(LocalNeuroViewMotion provides motionPreference) {
    Surface(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { NavigationActions.back(backStack) },
            transitionSpec = {
                fadeIn(tween(navigationMotionMs)) + slideInHorizontally(
                    animationSpec = tween(navigationMotionMs),
                    initialOffsetX = { fullWidth -> fullWidth / 12 },
                ) togetherWith fadeOut(tween(navigationMotionMs)) + slideOutHorizontally(
                    animationSpec = tween(navigationMotionMs),
                    targetOffsetX = { fullWidth -> -fullWidth / 12 },
                )
            },
            popTransitionSpec = {
                fadeIn(tween(navigationMotionMs)) + slideInHorizontally(
                    animationSpec = tween(navigationMotionMs),
                    initialOffsetX = { fullWidth -> -fullWidth / 12 },
                ) togetherWith fadeOut(tween(navigationMotionMs)) + slideOutHorizontally(
                    animationSpec = tween(navigationMotionMs),
                    targetOffsetX = { fullWidth -> fullWidth / 12 },
                )
            },
            predictivePopTransitionSpec = { _ ->
                fadeIn(tween(navigationMotionMs)) + slideInHorizontally(
                    animationSpec = tween(navigationMotionMs),
                    initialOffsetX = { fullWidth -> -fullWidth / 12 },
                ) togetherWith fadeOut(tween(navigationMotionMs)) + slideOutHorizontally(
                    animationSpec = tween(navigationMotionMs),
                    targetOffsetX = { fullWidth -> fullWidth / 12 },
                )
            },
            entryProvider = entryProvider {
                entry<Destination.Home> {
                    HomeScreen(
                        onBrowse = { navigate(Destination.Library) },
                        onImport = chooseDocuments,
                        lastScanName = snapshot.scans.firstOrNull { it.id == lastOpenedScanId }?.label,
                        lastScan = snapshot.scans.firstOrNull { it.id == lastOpenedScanId },
                        lastScanImportedAt = snapshot.scans.firstOrNull { it.id == lastOpenedScanId }?.let { scan ->
                            snapshot.collections.firstOrNull { it.id == scan.collectionId }?.importedAt
                        },
                        onResumeScan = {
                            snapshot.scans.firstOrNull { it.id == lastOpenedScanId }?.let(openScan)
                        },
                        onClearCache = { catalog.clearThumbnailCache() },
                    )
                }
                entry<Destination.Series> {
                    SeriesScreen(
                        snapshot = snapshot,
                        catalog = catalog,
                        isImporting = importBusy,
                        importMessage = importMessage,
                        onImport = chooseDocuments,
                        onBack = { NavigationActions.back(backStack) },
                        onSelectSeries = openScan,
                    )
                }
                entry<Destination.Library> {
                    LibraryScreen(
                        snapshot = snapshot,
                        catalog = catalog,
                        isBusy = editBusy || conversions.busy,
                        isImporting = importBusy,
                        importProgress = importProgress,
                        importOperation = importOperation,
                        importCancelRequestedAtElapsedMs = importCancelRequestedAt,
                        onCancelImport = ::cancelImport,
                        conversionOperation = conversions.operation,
                        onCancelConversion = conversions.cancel,
                        activityLabel = when {
                            editBusy -> "Updating library…"
                            else -> null
                        },
                        feedback = conversions.message ?: libraryMessage ?: importMessage,
                        onConvertDicom = conversions.selectDicomFolder,
                        onConvertDicomFiles = conversions.selectDicomSeries,
                        onExportDicom = conversions.exportNiftiAsDicom,
                        onImport = chooseDocuments,
                        onBrowse = { navigate(Destination.Series) },
                        onOpenScan = openScan,
                        onCreateCollection = { label ->
                            updateCatalog("Collection created") { catalog.createCollection(label) }
                        },
                        onRenameCollection = { id, name ->
                            updateCatalog("Collection renamed") { catalog.renameCollection(id, name) }
                        },
                        onRemoveCollection = { id ->
                            val removed = snapshot.inCollection(id)
                            updateCatalog("Collection and references removed; original files untouched") {
                                val updated = catalog.removeCollection(id)
                                if (removed.any { it.id == selectedScanId }) {
                                    selectedScanId = null
                                    selectedUri = null
                                }
                                updated
                            }
                        },
                        onRenameScan = { id, name ->
                            updateCatalog("MRI label updated") { catalog.renameSeries(id, name) }
                        },
                        onResetScanName = { id ->
                            updateCatalog("Original filename label restored") {
                                catalog.resetSeriesName(id)
                            }
                        },
                        onMoveScan = { id, collectionId ->
                            updateCatalog("MRI moved to another collection") {
                                catalog.moveSeries(id, collectionId)
                            }
                        },
                        onRemoveScan = { id ->
                            updateCatalog("MRI reference removed; source file untouched") {
                                val updated = catalog.removeSeries(id)
                                if (selectedScanId == id) {
                                    selectedScanId = null
                                    selectedUri = null
                                }
                                updated
                            }
                        },
                        onClearThumbnails = {
                            if (!importBusy && !editBusy) {
                                editBusy = true
                                scope.launch {
                                    try {
                                        val count = catalog.clearThumbnailCache()
                                        libraryMessage = context.resources.catalogQuantityText(
                                            CatalogQuantity.THUMBNAILS_CLEARED,
                                            count,
                                        )
                                    } catch (error: Exception) {
                                        libraryMessage = error.message ?: "Cache cleanup failed"
                                    } finally {
                                        editBusy = false
                                    }
                                }
                            }
                        },
                        onBack = { NavigationActions.back(backStack) },
                    )
                }
                entry<Destination.Viewer> { destination ->
                    ViewerScreen(
                        volumeState = volumeState,
                        pendingProviderCleanup = pendingDetachedProviderCleanup,
                        selectedDocumentUri = selectedUri,
                        displayNameOverride = snapshot.scans.firstOrNull {
                            it.id == destination.seriesId
                        }?.label,
                        onFirstSliceBitmapReady = { operationId ->
                            val session = activeLoadSession
                            val loading = volumeState as? VolumeLoadState.Loading
                            if (session?.operationId == operationId &&
                                loading?.decodedVolume != null &&
                                operationCoordinator.isCurrent(operationId) &&
                                operationCoordinator.markFirstSliceReady(operationId)
                            ) {
                                volumeState = VolumeLoadState.FirstSliceReady(
                                    operation = requireNotNull(operationCoordinator.state(operationId)),
                                    displayName = loading.displayName ?: "Selected MRI",
                                    volume = loading.decodedVolume,
                                )
                            }
                        },
                        onFirstSliceDisplayed = { operationId ->
                            val session = activeLoadSession
                            val ready = volumeState as? VolumeLoadState.FirstSliceReady
                            if (session?.operationId == operationId &&
                                ready?.operation?.id == operationId &&
                                operationCoordinator.isCurrent(operationId) &&
                                operationCoordinator.succeed(operationId, operationId.value.toString())
                            ) {
                                session.firstSliceReady.complete(Unit)
                            }
                        },
                        onFirstSliceRenderStarted = { operationId, volume, renderJob ->
                            viewerRenderJobs[renderJob] = volume
                            activeLoadSession
                                ?.takeIf { it.operationId == operationId }
                                ?.renderJob
                                ?.set(renderJob)
                        },
                        onFirstSliceRenderFinished = { operationId, volume, renderJob ->
                            viewerRenderJobs.remove(renderJob, volume)
                            activeLoadSession
                                ?.takeIf { it.operationId == operationId }
                                ?.renderJob
                                ?.compareAndSet(renderJob, null)
                        },
                        onCancelLoad = ::cancelViewerLoad,
                        onRetryLoad = ::retryViewerLoad,
                        onReturnToLibrary = { navigate(Destination.Library) },
                        onImport = chooseDocuments,
                        onNavigate = navigate,
                        onClearCache = { catalog.clearThumbnailCache() },
                    )
                }
            },
        )
    }
    }
}
